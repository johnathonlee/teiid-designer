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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.query.QueryMetadataException;
import com.metamatrix.api.exception.query.QueryPlannerException;
import com.metamatrix.api.exception.query.QueryResolverException;
import com.metamatrix.core.MetaMatrixRuntimeException;
import com.metamatrix.query.execution.QueryExecPlugin;
import com.metamatrix.query.mapping.relational.QueryNode;
import com.metamatrix.query.mapping.xml.MappingBaseNode;
import com.metamatrix.query.mapping.xml.MappingDocument;
import com.metamatrix.query.mapping.xml.MappingSourceNode;
import com.metamatrix.query.mapping.xml.MappingVisitor;
import com.metamatrix.query.mapping.xml.Navigator;
import com.metamatrix.query.mapping.xml.ResultSetInfo;
import com.metamatrix.query.metadata.QueryMetadataInterface;
import com.metamatrix.query.metadata.TempMetadataAdapter;
import com.metamatrix.query.metadata.TempMetadataID;
import com.metamatrix.query.optimizer.QueryOptimizer;
import com.metamatrix.query.optimizer.relational.rules.NewCalculateCostUtil;
import com.metamatrix.query.processor.ProcessorPlan;
import com.metamatrix.query.processor.relational.RelationalNode;
import com.metamatrix.query.processor.relational.RelationalPlan;
import com.metamatrix.query.resolver.util.ResolverUtil;
import com.metamatrix.query.rewriter.QueryRewriter;
import com.metamatrix.query.sql.lang.Command;
import com.metamatrix.query.sql.lang.Criteria;
import com.metamatrix.query.sql.lang.Drop;
import com.metamatrix.query.sql.lang.ExistsCriteria;
import com.metamatrix.query.sql.lang.FromClause;
import com.metamatrix.query.sql.lang.GroupContext;
import com.metamatrix.query.sql.lang.Into;
import com.metamatrix.query.sql.lang.JoinPredicate;
import com.metamatrix.query.sql.lang.JoinType;
import com.metamatrix.query.sql.lang.Limit;
import com.metamatrix.query.sql.lang.Query;
import com.metamatrix.query.sql.lang.QueryCommand;
import com.metamatrix.query.sql.lang.SubqueryFromClause;
import com.metamatrix.query.sql.lang.UnaryFromClause;
import com.metamatrix.query.sql.symbol.Constant;
import com.metamatrix.query.sql.symbol.GroupSymbol;
import com.metamatrix.query.sql.symbol.Reference;
import com.metamatrix.query.sql.visitor.ReferenceCollectorVisitor;
import com.metamatrix.query.sql.visitor.StaticSymbolMappingVisitor;

public class XMLQueryPlanner {

    static void prePlanQueries(MappingDocument doc, final XMLPlannerEnvironment planEnv) 
        throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {
        
        MappingVisitor queryPlanVisitor = new MappingVisitor() {
            
            public void visit(MappingBaseNode baseNode) {
                try {
                    // first if there are any explicit staging tables plan them first 
                    List stagingTables = baseNode.getStagingTables();
                    for (final Iterator i = stagingTables.iterator(); i.hasNext();) {
                        
                        final String tableName = (String)i.next();
                        planStagingTable(tableName, planEnv);    
                    }
                    
                    // now if this is of they source node plan; all other nodes
                    // do not need query planning.
                    if (baseNode instanceof MappingSourceNode) {
                        planQueries((MappingSourceNode)baseNode, planEnv);
                    }
                    
                } catch (Exception e) {
                    throw new MetaMatrixRuntimeException(e);
                } 
            }     
            
        };        
        planWalk(doc, queryPlanVisitor);
    }
    
    static void optimizeQueries(MappingDocument doc, final XMLPlannerEnvironment planEnv) 
        throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {

        MappingVisitor queryPlanVisitor = new MappingVisitor() {
            public void visit(MappingSourceNode sourceNode) {
                try {
                    ResultSetInfo rsInfo = sourceNode.getResultSetInfo();
                
                    if (rsInfo.isJoinedWithParent()) {
                        return;
                    }
                    
                    Query command = (Query)rsInfo.getCommand();
                    
                    prepareQuery(sourceNode, planEnv, command);
                    
                    QueryUtil.rewriteQuery(command, planEnv.getGlobalMetadata(), planEnv.context);
                    
                    List references = QueryUtil.getReferences(command, false);
                    rsInfo.setReferences(references);    
                    
                    // Plan the result set.
                    ProcessorPlan queryPlan = optimizePlan(command, planEnv);
                    rsInfo.setPlan(queryPlan);                    
                } catch (Exception e) {
                    throw new MetaMatrixRuntimeException(e);
                }
            }
        };
        planWalk(doc, queryPlanVisitor);
    }    
    
    private static void planWalk(MappingDocument doc, MappingVisitor visitor) 
        throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {
        
        try {
            Navigator walker = new Navigator(true, visitor);
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
    
    static void planQueries(MappingSourceNode sourceNode, XMLPlannerEnvironment planEnv) 
        throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {

        ResultSetInfo rsInfo = sourceNode.getResultSetInfo();
        
        // Create sql:  SELECT * FROM rsName            
        Query rsQuery = (Query)rsInfo.getCommand();
        
        // add user order by to base query
        rsQuery.setOrderBy(rsInfo.getOrderBy());

        // add user criteria to base query from model
        Criteria crit = rsInfo.getCriteria();
        try {
            if(crit != null) {
                planQueryWithCriteria(sourceNode, planEnv);
            }
        } catch (QueryResolverException e) {
            throw new MetaMatrixComponentException(e);
        }

        if (rsInfo.getUserRowLimit() != -1) {
            int limit = rsInfo.getUserRowLimit();
            if (rsInfo.exceptionOnRowlimit()) {
                limit++;
            }
            rsQuery.setLimit(new Limit(null, new Constant(new Integer(limit))));
        }
        
        //prepareQuery(sourceNode, planEnv, rsQuery);
        
        // this query is not eligible for staging; proceed normally.
        rsInfo.setCommand(rsQuery);            
    }
      
    static ProcessorPlan optimizePlan(Command query, XMLPlannerEnvironment planEnv)
        throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {

        TempMetadataAdapter metadata = planEnv.getGlobalMetadata();
        ProcessorPlan plan = QueryOptimizer.optimizePlan(query, metadata, planEnv.idGenerator, planEnv.capFinder, planEnv.analysisRecord, planEnv.context);
    
        return plan;
    }

    static void prepareQuery(MappingSourceNode sourceNode, XMLPlannerEnvironment planEnv, QueryCommand rsQuery) 
        throws MetaMatrixComponentException, QueryPlannerException {
        
        Collection externalGroups = getExternalGroups(sourceNode);
        
        rsQuery.setExternalGroupContexts(new GroupContext(null, externalGroups));
        
        QueryUtil.resolveQuery(rsQuery, planEnv.getGlobalMetadata());
    }
    
    private static Collection getExternalGroups(MappingSourceNode sourceNode) {
        Collection externalGroups = new HashSet();

        MappingSourceNode parentSource = sourceNode.getParentSourceNode();
        while (parentSource != null) {
            externalGroups.add(new GroupSymbol(parentSource.getActualResultSetName()));
            parentSource = parentSource.getParentSourceNode();
        }
        return externalGroups;
    }
    
       
    /**
     * The Criteria Source nodes are source nodes underneath the context Node.  
     */
    private static boolean getResultSets(MappingSourceNode contextNode, Set criteriaSourceNodes, LinkedHashSet allResultSets)  {
        
        boolean singleParentage = true;

        for (Iterator i = criteriaSourceNodes.iterator(); i.hasNext();) {
            MappingSourceNode node = (MappingSourceNode)i.next();

            List rsStack = getResultSetStack(contextNode, node);
            
            if (allResultSets.containsAll(rsStack)) {
                continue;
            }
            if (!rsStack.containsAll(allResultSets)) {
                singleParentage = false;
            }
            allResultSets.addAll(rsStack);
        }
        
        return singleParentage;
    }
    
    private static LinkedList getResultSetStack(MappingSourceNode contextNode, MappingBaseNode node) {
        LinkedList rsStack = new LinkedList();
        
        while (node != null && node != contextNode) {
            if (node instanceof MappingSourceNode) {
                rsStack.add(0, node);
            }
            node = node.getParentNode();
        }
        return rsStack;
    }
    
    private static void planQueryWithCriteria(MappingSourceNode contextNode, XMLPlannerEnvironment planEnv) 
        throws QueryPlannerException, MetaMatrixComponentException, QueryMetadataException, QueryResolverException {
        
        Map symbolMap = new HashMap();
        
        ResultSetInfo rsInfo = contextNode.getResultSetInfo();
        
        // this list of all the source nodes below the context, which are directly ro indirectly 
        // involved in the criteria
        LinkedHashSet resultSets = new LinkedHashSet();
        
        boolean singleParentage = getResultSets(contextNode, rsInfo.getCriteriaResultSets(), resultSets);
        
        Query contextQuery = null;
        
        if (rsInfo.isCriteriaRaised()) {
            contextQuery = (Query)QueryUtil.getQueryFromQueryNode(rsInfo.getResultSetName(), planEnv);
            String inlineViewName = planEnv.getAliasName(rsInfo.getResultSetName());
            updateSymbolMap(symbolMap, rsInfo.getResultSetName(), inlineViewName, planEnv.getGlobalMetadata());
        } else {
            contextQuery = (Query)rsInfo.getCommand();
        }
        
        Query currentQuery = contextQuery;
        
        for (Iterator i = resultSets.iterator(); i.hasNext();) {
            MappingSourceNode rsNode = (MappingSourceNode)i.next();
            
            ResultSetInfo childRsInfo = rsNode.getResultSetInfo();
            
            QueryNode planNode = QueryUtil.getQueryNode(childRsInfo.getResultSetName(), planEnv.getGlobalMetadata());    
            Command command = QueryUtil.getQuery(planNode);
            
            String inlineViewName = planEnv.getAliasName(childRsInfo.getResultSetName());
            
            updateSymbolMap(symbolMap, childRsInfo.getResultSetName(), inlineViewName, planEnv.getGlobalMetadata());
            
            // check if the criteria has been raised, if it is then we can update this as a join.
            if (childRsInfo.isCriteriaRaised()) {
                Query transformationQuery = (Query) command;
                SubqueryFromClause sfc = (SubqueryFromClause)transformationQuery.getFrom().getClauses().get(0);
                
                Criteria joinCriteria = ((Query)childRsInfo.getCommand()).getCriteria();
                
                if (joinCriteria == null) {
                    joinCriteria = QueryRewriter.TRUE_CRITERIA;
                }
                
                joinCriteria = (Criteria)joinCriteria.clone();
                
                //update the from clause
                FromClause clause = (FromClause)currentQuery.getFrom().getClauses().remove(0);
                
                JoinPredicate join = null;
                
                if (clause instanceof JoinPredicate) {
                    join = (JoinPredicate)clause;
                    
                    FromClause right = join.getRightClause();
                    
                    JoinPredicate newRight = new JoinPredicate(right, sfc, JoinType.JOIN_LEFT_OUTER, Criteria.separateCriteriaByAnd(joinCriteria));
                    
                    join.setRightClause(newRight);
                } else {
                    join = new JoinPredicate(clause, sfc, JoinType.JOIN_LEFT_OUTER, Criteria.separateCriteriaByAnd(joinCriteria));
                }
                
                currentQuery.getFrom().addClause(join);
                
                currentQuery.getSelect().setDistinct(true);
                
                continue;
            }
            
            if (!singleParentage) {
                throw new QueryPlannerException(QueryExecPlugin.Util.getString("XMLQueryPlanner.cannot_plan", rsInfo.getCriteria())); //$NON-NLS-1$
            }
            
            QueryUtil.handleBindings(command, planNode, planEnv);
            
            Query subQuery = QueryUtil.wrapQuery(new SubqueryFromClause(inlineViewName, command), inlineViewName);

            currentQuery.setCriteria(Criteria.combineCriteria(currentQuery.getCriteria(), new ExistsCriteria(subQuery)));
            
            currentQuery = subQuery; 
        }
        
        Criteria userCrit = (Criteria)rsInfo.getCriteria().clone();
        
        currentQuery.setCriteria(Criteria.combineCriteria(currentQuery.getCriteria(), userCrit));
        
        StaticSymbolMappingVisitor.mapSymbols(contextQuery, symbolMap);
        
        if (rsInfo.isCriteriaRaised()) {
            //if allowing ancestor bindings, we need to update the bindings for the query node...
            prepareQuery(contextNode, planEnv, contextQuery);
            QueryUtil.rewriteQuery(contextQuery, planEnv.getGlobalMetadata(), planEnv.context);

            //selectively replace correlated references with their actual element symbols
            List bindings = QueryUtil.getReferences(contextQuery, true);
            
            QueryNode modifiedNode = new QueryNode(rsInfo.getResultSetName(), null);
            modifiedNode.setCommand(contextQuery);
            
            for (Iterator i = bindings.iterator(); i.hasNext();) {
                Reference ref = (Reference)i.next();
                modifiedNode.addBinding(ref.getExpression().toString());
            }
            
            GroupSymbol groupSymbol = QueryUtil.createResolvedGroup(rsInfo.getResultSetName(), planEnv.getGlobalMetadata());
            planEnv.addQueryNodeToMetadata(groupSymbol.getMetadataID(), modifiedNode);
        } 
    }

    private static void updateSymbolMap(Map symbolMap, String oldGroup, final String newGroup, QueryMetadataInterface metadata) 
        throws QueryResolverException,QueryMetadataException,MetaMatrixComponentException {
        
        GroupSymbol oldGroupSymbol = new GroupSymbol(oldGroup);
        ResolverUtil.resolveGroup(oldGroupSymbol, metadata);
        
        HashSet projectedElements = new HashSet(ResolverUtil.resolveElementsInGroup(oldGroupSymbol, metadata));
        
        symbolMap.putAll(QueryUtil.createSymbolMap(oldGroupSymbol, newGroup, projectedElements));
    }
    
    /**
     * Currently any virtual/physical table can be planned as a staged table. A Staged
     * table only means that is has been preped to load the data into a temp table; when the other
     * transformations use this staged table, they will be redirected to use the temp table instead.
     * however note that it is still up to the plan to make sure the temp table is loaded.
     * @param groupName
     * @param planEnv
     * @return {@link GroupSymbol} the temptable which has been planned.
     */
    static void planStagingTable(String groupName, XMLPlannerEnvironment planEnv) 
        throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {

        ResultSetInfo rsInfo = planEnv.getStagingTableResultsInfo(groupName);
        
        FromClause fromClause = new UnaryFromClause(new GroupSymbol(groupName));
        Query query = QueryUtil.wrapQuery(fromClause, groupName);
        if (rsInfo.getCriteria() != null) {
            query.setCriteria(rsInfo.getCriteria());
        }
        planStagaingQuery(false, groupName, groupName, query, planEnv);
    }
    /**
     * This method takes given query and adds the "into" symbol to query and resoves it
     * and registers it with planner env as the staging table. Also, builds a unload query
     * to unload the staging table.
     */
    static boolean planStagaingQuery(boolean implicit, String srcGroupName, String stageGroupName, Query query, XMLPlannerEnvironment planEnv) 
        throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {

        GroupSymbol srcGroup = QueryUtil.createResolvedGroup(srcGroupName, planEnv.getGlobalMetadata());
        
        String intoGroupName =  "#"+stageGroupName.replace('.', '_'); //$NON-NLS-1$
        GroupSymbol intoGroupSymbol = new GroupSymbol(intoGroupName); 
                
        query.setInto(new Into(intoGroupSymbol));
        
        QueryUtil.resolveQuery(query, planEnv.getGlobalMetadata());
        
        if (!implicit) {
	        UnaryFromClause ufc = (UnaryFromClause)query.getFrom().getClauses().get(0);
	        if (!ReferenceCollectorVisitor.getReferences(ufc.getExpandedCommand()).isEmpty()) {
	        	throw new QueryPlannerException(QueryExecPlugin.Util.getString("XMLQueryPlanner.staging_table_has_input_set", stageGroupName)); //$NON-NLS-1$
	        }
        }
        
        QueryUtil.rewriteQuery(query, planEnv.getGlobalMetadata(), planEnv.context);
                
        ProcessorPlan plan = null;
        
        boolean debug = planEnv.analysisRecord.recordDebug();
        
        if (debug) {
            planEnv.analysisRecord.println("Attempting to create plan for staging table " + srcGroupName); //$NON-NLS-1$
        }
        
        try {
            // register with env
            plan = optimizePlan(query, planEnv);
        } catch (QueryPlannerException e) {
            if (implicit) {
                if (debug) {
                    planEnv.analysisRecord.println("Failed to create plan for staging table " + srcGroupName + " due to " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
                }
                return false;
            } 
            throw e;
        }
        
        int cardinality = QueryMetadataInterface.UNKNOWN_CARDINALITY;
        
        if (plan instanceof RelationalPlan) {
            RelationalPlan relationalPlan = (RelationalPlan)plan;
            RelationalNode root = relationalPlan.getRootNode();
            //since the root will be a project into node, get the cost from its child
            if (root.getChildren()[0] != null) {
                root = root.getChildren()[0];
            }
            Number planCardinality = root.getEstimateNodeCardinality();
            
            if (planCardinality == null || planCardinality.floatValue() == NewCalculateCostUtil.UNKNOWN_VALUE) {
                //don't stage unknown cost without criteria
                if (implicit && query.getCriteria() == null) {
                    return false;
                }
            } else if (planCardinality.floatValue() < planEnv.context.getProcessorBatchSize()) {
                //the staging table seems small    
                cardinality = planCardinality.intValue();
            } else if (implicit) {
                return false;
            }
        }
        
        // since this was staging table; this adds some temp metadata to the query node; extract
        // that metadata and inject into global metadata store for rest of the queries to use.
        Map tempMetadata = query.getTemporaryMetadata();
        if (tempMetadata != null && !tempMetadata.isEmpty()) {
            planEnv.addToGlobalMetadata(tempMetadata);
        }
        
        ResultSetInfo rsInfo = planEnv.getStagingTableResultsInfo(stageGroupName);
        rsInfo.setCommand(query);
        rsInfo.setPlan(plan);
        
        //set the carinality on the temp group.
        TempMetadataID intoGroupID = (TempMetadataID)intoGroupSymbol.getMetadataID();
        intoGroupID.setCardinality(cardinality);
        
        // add the meterialization hook for the staged table to original one.
        //GroupSymbol groupSymbol = (GroupSymbol)query.getFrom().getGroups().get(0);
        planEnv.addStagingTable(srcGroup.getMetadataID(), intoGroupID);
        
        // plan the unload of the staging table
        String unloadName = planEnv.unLoadResultName(stageGroupName);
        ResultSetInfo rsUnloadInfo = planEnv.getStagingTableResultsInfo(unloadName);
        Command command = wrapStagingTableUnloadQuery(intoGroupSymbol);
        QueryUtil.resolveQuery(command, planEnv.getGlobalMetadata());
        QueryUtil.rewriteQuery(command, planEnv.getGlobalMetadata(), planEnv.context);
        
        plan = optimizePlan(command, planEnv);
        rsUnloadInfo.setCommand(command);
        rsUnloadInfo.setPlan(plan);
        
        return true;
    }
        
    /**
     * This builds a command in the following form; If staging table name is "FOO"
     * the command built is "Delete FROM #FOO"
     */    
    private static Command wrapStagingTableUnloadQuery(GroupSymbol intoGroupSymbol) {
        Drop drop = new Drop();
        drop.setTable(intoGroupSymbol);
        return drop;
    }    
}
