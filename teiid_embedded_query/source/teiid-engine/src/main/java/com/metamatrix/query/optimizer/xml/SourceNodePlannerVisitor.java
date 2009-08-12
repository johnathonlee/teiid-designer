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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.query.QueryMetadataException;
import com.metamatrix.api.exception.query.QueryPlannerException;
import com.metamatrix.api.exception.query.QueryResolverException;
import com.metamatrix.core.MetaMatrixRuntimeException;
import com.metamatrix.query.mapping.relational.QueryNode;
import com.metamatrix.query.mapping.xml.MappingDocument;
import com.metamatrix.query.mapping.xml.MappingNode;
import com.metamatrix.query.mapping.xml.MappingSourceNode;
import com.metamatrix.query.mapping.xml.MappingVisitor;
import com.metamatrix.query.mapping.xml.Navigator;
import com.metamatrix.query.mapping.xml.ResultSetInfo;
import com.metamatrix.query.metadata.TempMetadataAdapter;
import com.metamatrix.query.metadata.TempMetadataStore;
import com.metamatrix.query.resolver.util.ResolverUtil;
import com.metamatrix.query.sql.lang.Command;
import com.metamatrix.query.sql.lang.Criteria;
import com.metamatrix.query.sql.lang.Query;
import com.metamatrix.query.sql.lang.SubqueryFromClause;
import com.metamatrix.query.sql.lang.UnaryFromClause;
import com.metamatrix.query.sql.symbol.AliasSymbol;
import com.metamatrix.query.sql.symbol.ElementSymbol;
import com.metamatrix.query.sql.symbol.GroupSymbol;
import com.metamatrix.query.sql.symbol.SingleElementSymbol;
import com.metamatrix.query.sql.visitor.ElementCollectorVisitor;
import com.metamatrix.query.sql.visitor.ExpressionMappingVisitor;
import com.metamatrix.query.sql.visitor.ReferenceCollectorVisitor;
import com.metamatrix.query.sql.visitor.SQLStringVisitor;


/** 
 * This visitor will take source node's QueryNode, move the the inputset criteria 
 * specified on the  QueryNode on to the Source Node's query.
 */
public class SourceNodePlannerVisitor extends MappingVisitor {
    private XMLPlannerEnvironment planEnv;
    
    public SourceNodePlannerVisitor(XMLPlannerEnvironment planEnv) {
        this.planEnv = planEnv;
    }

    public void visit(MappingSourceNode sourceNode) {

        try {
            // create a basic query for the mapping class.
            String groupName = sourceNode.getResultName();
            GroupSymbol groupSymbol = null;
            GroupSymbol newGroupSymbol = null;
            
            try {
                groupSymbol = QueryUtil.createResolvedGroup(groupName, planEnv.getGlobalMetadata());
                newGroupSymbol = createAlternateGroup(groupSymbol, sourceNode);
            } catch (QueryMetadataException e) {
                /*
                 * JIRA JBEDSP-531 if a node is excluded and has no transformation, then it matches the
                 * designer notion of an incomplete document.  we'll allow this by removing everything
                 * starting at this point
                 */
                MappingNode current = sourceNode;
                boolean isExcluded = false;
                while (current != null) {
                    if (current.isExcluded()) {
                        isExcluded = true;
                        break;
                    }
                    current = current.getParent();
                }
                if (!isExcluded) {
                    throw e;
                }
                //cut me and everything below me from the tree
                sourceNode.getParent().getChildren().remove(sourceNode);
                sourceNode.getChildren().clear();
                return;
            }
            String newGroup = newGroupSymbol.getName();
                        
            ResultSetInfo rsInfo = sourceNode.getResultSetInfo();
            //create the command off of the unresolved group symbol
            Query baseQuery = QueryUtil.wrapQuery(new UnaryFromClause(new GroupSymbol(newGroup)), newGroup);
            baseQuery.getSelect().clearSymbols();
            for (Iterator i = ResolverUtil.resolveElementsInGroup(groupSymbol, planEnv.getGlobalMetadata()).iterator(); i.hasNext();) {
                SingleElementSymbol ses = (SingleElementSymbol)i.next();
                baseQuery.getSelect().addSymbol(new ElementSymbol(newGroup + SingleElementSymbol.SEPARATOR + ses.getShortName()));
            }
            
            rsInfo.setCommand(baseQuery);
            
            QueryNode modifiedNode = QueryUtil.getQueryNode(newGroup, planEnv.getGlobalMetadata());
            Command command = QueryUtil.getQuery(modifiedNode);
                        
            MappingSourceNode parent = sourceNode.getParentSourceNode();
            
            // root source nodes do not have any inputset criteria on them; so there is no use in
            // going through the raising the criteria.
            // if the original query is not a select.. we are out of luck. we can expand on this later
            // versions. make ure bindings are only to parent.
            if (parent == null || !canRaiseInputset(command) || !areBindingsOnlyToNode(modifiedNode, parent)) {
                return;
            }
            
            // now get the criteria set at the design time; and walk and remove any inputset 
            // criteria.
            Query transformationQuery = (Query)command;
            
            QueryUtil.resolveQuery(transformationQuery, planEnv.getGlobalMetadata());
            
            Criteria criteria = transformationQuery.getCriteria();
            Criteria nonInputsetCriteria = null;
            Criteria inputSetCriteria = null;
            
            for (Iterator i = Criteria.separateCriteriaByAnd(criteria).iterator(); i.hasNext();) {
                Criteria conjunct = (Criteria) i.next();

                // collect references in the criteria; if there are references; then this is
                // set by inputset criteria
                Collection references = ReferenceCollectorVisitor.getReferences(conjunct);
                if (references.isEmpty()) {
                    nonInputsetCriteria = Criteria.combineCriteria(nonInputsetCriteria, conjunct);
                }
                else {
                    inputSetCriteria = Criteria.combineCriteria(inputSetCriteria, conjunct);
                }
            }
            
            // Keep the criteria which is not reference based.
            transformationQuery.setCriteria(nonInputsetCriteria);

            // check and map/convert the inputset criteria elements to groupName, so that 
            // this criteria mapped on the baseQuery; 

            boolean addedProjectedSymbol = convertCriteria(newGroupSymbol, transformationQuery, inputSetCriteria, planEnv.getGlobalMetadata(), sourceNode.getSymbolMap());
            if (addedProjectedSymbol && transformationQuery.getSelect().isDistinct()) {
                transformationQuery.getSelect().setDistinct(false);
                baseQuery.getSelect().setDistinct(true);
            }
            
            String inlineViewName = planEnv.getAliasName(newGroup);
            transformationQuery = QueryUtil.wrapQuery(new SubqueryFromClause(inlineViewName, transformationQuery), inlineViewName);
                        
            // Now that we have the modified Query Node for the group name
            // we need to update the metadata.
            QueryNode relationalNode = new QueryNode(newGroup, SQLStringVisitor.getSQLString(transformationQuery));
            planEnv.addQueryNodeToMetadata(newGroupSymbol.getMetadataID(), relationalNode);
            
            // this is the simple form of the planned query preplan stage. set the command
            // with newly found criteria.
            QueryUtil.handleBindings(inputSetCriteria, modifiedNode, planEnv);
            
            baseQuery.setCriteria(inputSetCriteria);
            rsInfo.setCriteriaRaised(true);
        } catch (Exception e) {
            throw new MetaMatrixRuntimeException(e);
        } 
    }

    /**
     * check to make sure that given query nodes bindings are only to the node provided,
     * if they are returns true; false otherwise
     */
    private boolean areBindingsOnlyToNode(QueryNode modifiedNode, MappingSourceNode sourceNode) 
        throws MetaMatrixComponentException {
        
        List bindings = QueryUtil.parseBindings(modifiedNode, planEnv);

        String nodeStr = (sourceNode.getActualResultSetName() + ElementSymbol.SEPARATOR).toUpperCase();
        
        for (Iterator i = bindings.iterator(); i.hasNext();) {
            ElementSymbol binding = (ElementSymbol)i.next();
            
            if (!binding.getCanonicalName().startsWith(nodeStr)) {
                return false;
            }
        }
        
        return true;
    }

    static String getNewName(String groupName, TempMetadataStore store) {
        int index = 1;
        String newGroup = null;
        while (true) {
            newGroup = (groupName + "_" + index++).toUpperCase(); //$NON-NLS-1$
            if (!store.getData().containsKey(newGroup)) {
                break;
            }
        }
        return newGroup;
    }
    
    /**
     * In mapping document, sometimes sibiling nodes might share the mapping class defination, 
     * however during the runtime depending upon where they are used their criteria may be 
     * different on different path of execution (choice nodes), so here we alias all the result
     * set names in a mapping document.
     */
    private GroupSymbol createAlternateGroup(GroupSymbol oldSymbol, MappingSourceNode sourceNode) 
        throws QueryMetadataException, MetaMatrixComponentException, QueryResolverException, QueryPlannerException {
        
        // get elements in the old group
        List elements = ResolverUtil.resolveElementsInGroup(oldSymbol, planEnv.getGlobalMetadata());
        
        TempMetadataStore store = planEnv.getGlobalMetadata().getMetadataStore();
        
        // create a new group name and to the temp store
        String newGroup = getNewName(oldSymbol.getName(), store);
        GroupSymbol newGroupSymbol = new GroupSymbol(newGroup);
        newGroupSymbol.setMetadataID(store.addTempGroup(newGroup, elements));
        
        // create a symbol map; so that all the others who refer by the old name can use this map 
        // to convert to new group.
        sourceNode.setSymbolMap(QueryUtil.createSymbolMap(oldSymbol, newGroup, elements));        
                
        // now that we created a new group; now define the query node for this new group based
        // on the old one.
        QueryNode oldQueryNode = QueryUtil.getQueryNode(oldSymbol.getName(), planEnv.getGlobalMetadata());

        // move the query and its bindings
        QueryNode modifiedNode = new QueryNode(newGroup, oldQueryNode.getQuery());
        mapBindings(sourceNode, oldQueryNode, modifiedNode);
        
        // add the query node for the new group into metadata.
        planEnv.addQueryNodeToMetadata(newGroupSymbol.getMetadataID(), modifiedNode);        
                
        return newGroupSymbol;
    }

    static void mapBindings(MappingSourceNode sourceNode,
                             QueryNode oldQueryNode,
                             QueryNode modifiedNode) {
        if (oldQueryNode.getBindings() != null) {
            List bindings = new ArrayList();
            for (Iterator i = oldQueryNode.getBindings().iterator(); i.hasNext();) {
                String binding = (String)i.next();
                bindings.add(sourceNode.getMappedSymbol(new ElementSymbol(binding)).getName());
            }
            modifiedNode.setBindings(bindings);
        }
    }
    
    private boolean canRaiseInputset(Command command) {
        // check to see if this is query.
        if (!(command instanceof Query)) {
            return false;
        }
        
        Query query = (Query)command; 
        Criteria crit = query.getCriteria();
        
        if (crit != null && (query.getGroupBy() != null || query.getHaving() != null || query.getLimit() != null)) {
            return false;
        }
        //temporarily remove the criteria
        query.setCriteria(null);
        //just throw away order by
        query.setOrderBy(null);
        
        Collection references = ReferenceCollectorVisitor.getReferences(query);

        query.setCriteria(crit);
        
        //if there are any input set bindings in the rest of the command, don't convert
        return references.isEmpty();
    }
    
    /** 
     * Convert the critria group elements from its native group to supplied group.
     * @param newGroupSymbol - group to which the criterial elements to be modified
     * @param criteria - criteria on which the elements need to modified
     * @return true if converted; false otherwise
     */
    private boolean convertCriteria(GroupSymbol newGroupSymbol, Query transformationQuery, Criteria criteria, TempMetadataAdapter metadata, Map symbolMap) 
        throws QueryMetadataException, MetaMatrixComponentException {
        
        String groupName = newGroupSymbol.getName();
        Collection elementsInCriteria = ElementCollectorVisitor.getElements(criteria, true);
        Map mappedElements = new HashMap();

        List projectedSymbols = transformationQuery.getProjectedSymbols();
        
        boolean addedProjectedSymbol = false;
        
        for (Iterator i = elementsInCriteria.iterator(); i.hasNext();) {
            
            final ElementSymbol symbol = (ElementSymbol)i.next();
            
            if (projectedSymbols.contains(symbol)) {
                mappedElements.put(symbol, new ElementSymbol(groupName + ElementSymbol.SEPARATOR + symbol.getShortName()));
                continue;
            }
            AliasSymbol alias = getMachingAlias(projectedSymbols, symbol);
            if (alias != null) {
                mappedElements.put(symbol, new ElementSymbol(groupName + ElementSymbol.SEPARATOR + alias.getShortName()));
                continue;
            }
            // this means that the criteria symbol, is not projected, so add the element symbol
            // to query node, so that it is projected.
            
            String name = getNewSymbolName(newGroupSymbol.getName(), symbol, symbolMap);
            
            AliasSymbol selectSymbol = new AliasSymbol(name, symbol);
            
            transformationQuery.getSelect().addSymbol(selectSymbol);
            addedProjectedSymbol = true;

            // also add to the projected elements on the temp group.
            metadata.getMetadataStore().addElementSymbolToTempGroup(newGroupSymbol.getName(), selectSymbol);
            
            ElementSymbol upperSymbol = new ElementSymbol(groupName + ElementSymbol.SEPARATOR + selectSymbol.getShortName());
            mappedElements.put(symbol, upperSymbol);
            
            //add to the symbol map.  the base symbol is not to the original group, since it doesn't really project this element 
            symbolMap.put(upperSymbol, upperSymbol); 
        }      
        
        // now that we have resolved criteria elements; map them correctly
        ExpressionMappingVisitor.mapExpressions(criteria, mappedElements);
        return addedProjectedSymbol;
    }    
    
    static String getNewSymbolName(String newGroupName,
                                           ElementSymbol elementSymbol,
                                           Map symbolMap) {

        int index = 1;

        String newSymbolName = elementSymbol.getShortName();

        while (symbolMap.values().contains(new ElementSymbol(newGroupName + ElementSymbol.SEPARATOR + newSymbolName))) {
            newSymbolName = elementSymbol.getShortName() + "_" + index++; //$NON-NLS-1$
        }

        return newSymbolName;
    }
       
    /**
     * If the element has alias wrapping, then return the matching alias element.
     * @return matched alias symbol; null otherwise.
     */
    private AliasSymbol getMachingAlias(List elementsInGroup, ElementSymbol symbol) {
        
        for(Iterator i = elementsInGroup.iterator(); i.hasNext();) {
            final SingleElementSymbol element = (SingleElementSymbol)i.next();
            if (element instanceof AliasSymbol) {
                AliasSymbol alias = (AliasSymbol)element;
                if (alias.getSymbol().equals(symbol)) {
                    return alias;
                }
            }
        }
        return null;
    }
    
    /**
     * try to split the criteria based on if that is inputset criteria or not.
     * @param doc
     * @param planEnv
     * @return
     */
    public static MappingDocument raiseInputSet(MappingDocument doc, XMLPlannerEnvironment planEnv) 
        throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {
        SourceNodePlannerVisitor real = new SourceNodePlannerVisitor(planEnv);
        planWalk(doc, real);
        return doc;
    }
       
    private static void planWalk(MappingDocument doc, MappingVisitor visitor) 
        throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {
    
        try {
            Navigator walker = new Navigator(true, visitor) {
                
                /*
                 * Special walking of children so that we can safely remove nodes
                 */
                @Override
                protected void walkChildNodes(MappingNode element) {
                    List children = new ArrayList(element.getNodeChildren());
                    for(Iterator i=children.iterator(); i.hasNext();) {
                        
                        if (shouldAbort()) {
                            break;
                        }
                        
                        MappingNode node = (MappingNode)i.next();            
                        node.acceptVisitor(this);
                    }
                }
            };

            doc.acceptVisitor(walker);
        } catch (MetaMatrixRuntimeException e) {
            if (e.getCause() instanceof QueryPlannerException) {
                throw (QueryPlannerException)e.getCause();
            }
            else if (e.getCause() instanceof QueryMetadataException) {
                throw (QueryMetadataException)e.getCause();
            }
            else if (e.getCause() instanceof MetaMatrixComponentException) {
                throw (MetaMatrixComponentException)e.getCause();
            }
            else {
                throw e;
            }
        }
    }  
}
