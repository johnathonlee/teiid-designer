/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package com.metamatrix.query.optimizer.xml;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.query.QueryMetadataException;
import com.metamatrix.api.exception.query.QueryPlannerException;
import com.metamatrix.query.execution.QueryExecPlugin;
import com.metamatrix.query.function.FunctionLibrary;
import com.metamatrix.query.mapping.xml.MappingDocument;
import com.metamatrix.query.mapping.xml.MappingNode;
import com.metamatrix.query.mapping.xml.MappingSourceNode;
import com.metamatrix.query.mapping.xml.ResultSetInfo;
import com.metamatrix.query.metadata.QueryMetadataInterface;
import com.metamatrix.query.sql.lang.CompareCriteria;
import com.metamatrix.query.sql.lang.Criteria;
import com.metamatrix.query.sql.symbol.Constant;
import com.metamatrix.query.sql.symbol.ElementSymbol;
import com.metamatrix.query.sql.symbol.Function;
import com.metamatrix.query.sql.symbol.GroupSymbol;
import com.metamatrix.query.sql.visitor.ElementCollectorVisitor;
import com.metamatrix.query.util.ErrorMessageKeys;

public class CriteriaPlanner {
    
    /**
     * Take the criteria from the user's command and break it into pieces applicable to each
     * mapping class result set in the mapping document
     * 
     * Assumes that criteria is in CNF where each conjunct applies to only a single context.
     * 
     * The post condition of this method is that each result set with 
     * 
     * @param criteria Criteria from user's command
     * @throws QueryPlannerException for any logical exception detected during planning
     * @throws QueryMetadataException if metadata encounters exception
     * @throws MetaMatrixComponentException unexpected exception
     */
    static void placeUserCriteria(Criteria criteria, XMLPlannerEnvironment planEnv)
        throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {
        
        for (Iterator conjunctIter = Criteria.separateCriteriaByAnd(criteria).iterator(); conjunctIter.hasNext();) {
        
            Criteria conjunct = (Criteria) conjunctIter.next();
            
            if (planStagingTableCriteria(conjunct, planEnv)) {
                continue;
            }

            //this is a gross hack, these should not be criteria
            if (planRowLimitFunction(conjunct, criteria, planEnv)) {
                continue;
            }
            
            MappingNode context = null;
            
            Collection contextFunctions = ContextReplacerVisitor.replaceContextFunctions(conjunct);
            if (!contextFunctions.isEmpty()) {
                //ensure that every part of the conjunct is to the same context
                for (Iterator i = contextFunctions.iterator(); i.hasNext();) {
                    Function contextFunction = (Function)i.next();
                    MappingNode otherContext = getContext(planEnv, contextFunction);
                    if (context == null) {
                        context = otherContext;
                    } else if (context != otherContext){
                        throw new QueryPlannerException(ErrorMessageKeys.OPTIMIZER_0068, QueryExecPlugin.Util.getString(ErrorMessageKeys.OPTIMIZER_0068, criteria));
                    }
                }
                
                //search up to find the source node
                MappingNode contextRsNode = context.getSourceNode();
                if (contextRsNode != null) {
                    context = contextRsNode;
                }
                
            } else {
                context = planEnv.mappingDoc;
            }
            
            Set sourceNodes = collectSourceNodesInConjunct(conjunct, context, planEnv.mappingDoc);

            //TODO: this can be replaced with method on the source node?
            MappingSourceNode criteriaRs = findRootResultSetNode(context, sourceNodes, criteria);
            
            Criteria convertedCrit = XMLNodeMappingVisitor.convertCriteria(conjunct, planEnv.mappingDoc, planEnv.getGlobalMetadata());
            
            ResultSetInfo rs = criteriaRs.getResultSetInfo();
            rs.setCriteria(Criteria.combineCriteria(rs.getCriteria(), convertedCrit));
            rs.addToCriteriaResultSets(sourceNodes);
        }
    }

    /** 
     * This method collects all the MappingSourceNode(s) at or below the context given.
     */
    private static Set collectSourceNodesInConjunct(Criteria conjunct, MappingNode context, MappingDocument mappingDoc)
        throws QueryPlannerException {
        
        Collection elements = ElementCollectorVisitor.getElements(conjunct, true);
        Set resultSets = new HashSet();
        
        String contextFullName = context.getFullyQualifiedName().toUpperCase();
        
        //validate that each element's group is under the current context or is in the direct parentage
        for (Iterator i = elements.iterator(); i.hasNext();) {
            ElementSymbol elementSymbol = (ElementSymbol)i.next();
            
            String elementFullName = elementSymbol.getCanonicalName();
            
            MappingNode node = MappingNode.findNode(mappingDoc, elementFullName);
            
            MappingNode elementRsNode = node.getSourceNode(); 
            if (elementRsNode == null) {
                throw new QueryPlannerException(QueryExecPlugin.Util.getString("CriteriaPlanner.invalid_element", elementSymbol)); //$NON-NLS-1$
            }
            
            String elementRsFullName = elementRsNode.getFullyQualifiedName().toUpperCase();
            
            //check for a match at or below the context
            if (contextFullName.equals(elementRsFullName) || 
                            elementRsFullName.startsWith(contextFullName + ElementSymbol.SEPARATOR)) {
                resultSets.add(elementRsNode);
                continue;
            }
            
            //check for match above the context
            if (contextFullName.startsWith(elementRsFullName + ElementSymbol.SEPARATOR)) {
                continue;
            }
            
            throw new QueryPlannerException(QueryExecPlugin.Util.getString("CriteriaPlanner.invalid_context", elementSymbol, context.getFullyQualifiedName())); //$NON-NLS-1$
        }
        return resultSets;
    }

    private static MappingSourceNode findRootResultSetNode(MappingNode context, Set resultSets, Criteria criteria) 
        throws QueryPlannerException {
        
        if (context instanceof MappingSourceNode) {
            return (MappingSourceNode)context;
        }

        Set criteriaResultSets = new HashSet();
        // if the context node is not the root node then we need to find the root source node from list.
        for (Iterator i = resultSets.iterator(); i.hasNext();) {

            // these are actually source nodes.
            MappingNode node = (MappingNode)i.next();
   
            MappingNode root = node;
            
            while (node != null) {
                if (node instanceof MappingSourceNode) {
                    root = node;
                }
                node = node.getParent();
            }
            criteriaResultSets.add(root);
        }
   
        if (criteriaResultSets.size() != 1) {
            //TODO: this assumption could be relaxed if we allow context to be from a document perspective, rather than from a result set
            throw new QueryPlannerException(QueryExecPlugin.Util.getString("CriteriaPlanner.no_context", criteria)); //$NON-NLS-1$
        }
        return (MappingSourceNode)criteriaResultSets.iterator().next();
    }
    
    /**
     * Removes non-inferred staging table criteria.  Places it directly onto the contextCriteria  
     */
    static boolean planStagingTableCriteria(Criteria criteria, XMLPlannerEnvironment planEnv) throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {
        
        String rootTempGroupName = getStagingTableForConjunct(criteria, planEnv.getGlobalMetadata());
        if (rootTempGroupName == null){
            return false;
        }

        // Defect 13172 - be careful to check for previously found root staging table
        // conjuncts and combine that with current conjunct
        ResultSetInfo rs = planEnv.getStagingTableResultsInfo(rootTempGroupName);
        
        rs.setCriteria(Criteria.combineCriteria(rs.getCriteria(), criteria));
        
        return true;
    }
    
    static boolean planRowLimitFunction(Criteria conjunct, Criteria wholeCrit, XMLPlannerEnvironment planEnv) 
    throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {
        
        // Check for "rowlimit" or "rowlimitexception" pseudo-function:
        // Restrictions
        //  -Single arg must be any xml doc node that is within the scope of a mapping class
        //  -Can't have conflicting row limits on the same mapping class
        // (Query Validator enforces additional restrictions.)

        Function rowLimitFunction = null;
        Constant rowLimitConstant = null;
        boolean exceptionOnRowLimit = false;
        
        if (conjunct instanceof CompareCriteria) {
            CompareCriteria crit = (CompareCriteria)conjunct;
            if (crit.getLeftExpression() instanceof Function) {
                Function function = (Function)crit.getLeftExpression();
                if (function.getName().equalsIgnoreCase(FunctionLibrary.ROWLIMIT)) {
                    rowLimitFunction = function;
                    rowLimitConstant = (Constant)crit.getRightExpression();
                } else if (function.getName().equalsIgnoreCase(FunctionLibrary.ROWLIMITEXCEPTION)) {
                    rowLimitFunction = function;
                    rowLimitConstant = (Constant)crit.getRightExpression();
                    exceptionOnRowLimit = true;
                }
            }
            if (rowLimitFunction == null && crit.getRightExpression() instanceof Function) {
                Function function = (Function)crit.getRightExpression();
                if (function.getName().equalsIgnoreCase(FunctionLibrary.ROWLIMIT)) {
                    rowLimitFunction = function;
                    rowLimitConstant = (Constant)crit.getLeftExpression();
                } else if (function.getName().equalsIgnoreCase(FunctionLibrary.ROWLIMITEXCEPTION)) {
                    rowLimitFunction = function;
                    rowLimitConstant = (Constant)crit.getLeftExpression();
                    exceptionOnRowLimit = true;
                }
            }
        }
        
        if (rowLimitFunction == null) {
            return false;
        }
        int rowLimit = ((Integer)rowLimitConstant.getValue()).intValue();
        
        String fullyQualifiedNodeName = planEnv.getGlobalMetadata().getFullName(((ElementSymbol)rowLimitFunction.getArg(0)).getMetadataID());
        
        MappingNode node = MappingNode.findNode(planEnv.mappingDoc, fullyQualifiedNodeName.toUpperCase());
        MappingSourceNode sourceNode = node.getSourceNode();
        if (sourceNode == null) {
            String msg = QueryExecPlugin.Util.getString("XMLPlanner.The_rowlimit_parameter_{0}_is_not_in_the_scope_of_any_mapping_class", fullyQualifiedNodeName); //$NON-NLS-1$
            throw new QueryPlannerException(msg);
        }
        
        ResultSetInfo criteriaRsInfo = sourceNode.getResultSetInfo();
        
        // Check for conflicting row limits on the same mapping class
        int existingLimit = criteriaRsInfo.getUserRowLimit();
        if (existingLimit > 0 && existingLimit != rowLimit) {
            String msg = QueryExecPlugin.Util.getString("XMLPlanner.Criteria_{0}_contains_conflicting_row_limits", wholeCrit); //$NON-NLS-1$
            throw new QueryPlannerException(msg);
        }
        
        criteriaRsInfo.setUserRowLimit(rowLimit, exceptionOnRowLimit);
        
        // No further processing on this conjunct
        return true;
    }
    
    /**
     * Validate that all elements within a conjunct are either referring to a temp table
     * or NOT referring to a temp table.  Can't mix element types within a conjunct.
     * @param conjunct Conjunct to validate
     * @return String name of temporary result set, if conjunct is for the root temp table;
     * or return null if the conjunct is for XML document node(s)
     * @throws QueryPlannerException if conjunct has mixed types
     */
    static String getStagingTableForConjunct(Criteria conjunct, QueryMetadataInterface metadata)
        throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {

        Collection elements = ElementCollectorVisitor.getElements(conjunct, true);

        boolean first = true;
        String resultSet = null;
        
        // Check each remaining element to make sure it matches
        for(Iterator elemIter = elements.iterator(); elemIter.hasNext();) {
            ElementSymbol element = (ElementSymbol) elemIter.next();
            GroupSymbol group = element.getGroupSymbol();
            //assumes that all non-xml group elements are temp elements
            boolean hasTempElement = !metadata.isXMLGroup(group.getMetadataID());
            if(!first && hasTempElement && resultSet == null) {
                throw new QueryPlannerException(ErrorMessageKeys.OPTIMIZER_0035, QueryExecPlugin.Util.getString(ErrorMessageKeys.OPTIMIZER_0035, conjunct));
            }

            if (hasTempElement) {
                String currentResultSet = metadata.getFullName(element.getGroupSymbol().getMetadataID());
                if (resultSet != null && !resultSet.equalsIgnoreCase(currentResultSet)) {
                    throw new QueryPlannerException(QueryExecPlugin.Util.getString("CriteriaPlanner.multiple_staging", conjunct)); //$NON-NLS-1$
                } 
                resultSet = currentResultSet;
            }
            first = false;
        }
        
        if (resultSet != null) {
            Collection functions = ContextReplacerVisitor.replaceContextFunctions(conjunct);
            if (!functions.isEmpty()) {
                throw new QueryPlannerException(QueryExecPlugin.Util.getString("CriteriaPlanner.staging_context")); //$NON-NLS-1$
            }
            
            //should also throw an exception if it contains a row limit function
        }
        
        return resultSet;
    }
    
    /** 
     * Returns the context for a given context function
     */
    static MappingNode getContext(XMLPlannerEnvironment planEnv, Function contextFunction) 
        throws QueryPlannerException {
        
        ElementSymbol targetContext = (ElementSymbol)contextFunction.getArg(0);

        MappingNode contextNode = MappingNode.findNode(planEnv.mappingDoc, targetContext.getCanonicalName());
        if (contextNode == null){
            throw new QueryPlannerException(ErrorMessageKeys.OPTIMIZER_0037, QueryExecPlugin.Util.getString(ErrorMessageKeys.OPTIMIZER_0037, targetContext));
        }
        return contextNode;
    }

}
