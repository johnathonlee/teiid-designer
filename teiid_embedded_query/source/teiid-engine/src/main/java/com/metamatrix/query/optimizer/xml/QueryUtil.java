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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.query.QueryMetadataException;
import com.metamatrix.api.exception.query.QueryParserException;
import com.metamatrix.api.exception.query.QueryPlannerException;
import com.metamatrix.api.exception.query.QueryResolverException;
import com.metamatrix.api.exception.query.QueryValidatorException;
import com.metamatrix.query.analysis.AnalysisRecord;
import com.metamatrix.query.execution.QueryExecPlugin;
import com.metamatrix.query.mapping.relational.QueryNode;
import com.metamatrix.query.metadata.QueryMetadataInterface;
import com.metamatrix.query.metadata.TempMetadataAdapter;
import com.metamatrix.query.parser.QueryParser;
import com.metamatrix.query.resolver.QueryResolver;
import com.metamatrix.query.resolver.util.ResolverUtil;
import com.metamatrix.query.rewriter.QueryRewriter;
import com.metamatrix.query.sql.LanguageObject;
import com.metamatrix.query.sql.lang.Command;
import com.metamatrix.query.sql.lang.From;
import com.metamatrix.query.sql.lang.FromClause;
import com.metamatrix.query.sql.lang.Query;
import com.metamatrix.query.sql.lang.Select;
import com.metamatrix.query.sql.symbol.AllInGroupSymbol;
import com.metamatrix.query.sql.symbol.ElementSymbol;
import com.metamatrix.query.sql.symbol.Expression;
import com.metamatrix.query.sql.symbol.GroupSymbol;
import com.metamatrix.query.sql.symbol.Reference;
import com.metamatrix.query.sql.visitor.CommandCollectorVisitor;
import com.metamatrix.query.sql.visitor.ReferenceCollectorVisitor;
import com.metamatrix.query.util.CommandContext;
import com.metamatrix.query.util.ErrorMessageKeys;


/** 
 * Helper methods for dealing with relational queries while performing XML planning.
 *  
 * @since 4.3
 */
public class QueryUtil {

    /** Parse a query from a query node and return a Command object.
     * 
     * @param queryNode The query node which contains a query
     * @param planEnv The planner environment
     * @return New Command object
     * @throws QueryPlannerException If an error occurred
     * @since 4.3
     */
    static Command getQuery(QueryNode queryNode) throws QueryPlannerException {
        Command query = queryNode.getCommand();
        
        if (query == null) {
            try {
                query = QueryParser.getQueryParser().parseCommand(queryNode.getQuery());            
            } catch (QueryParserException e) {
                throw new QueryPlannerException(e, QueryExecPlugin.Util.getString(ErrorMessageKeys.OPTIMIZER_0054, new Object[]{queryNode.getGroupName(), queryNode.getQuery()}));
            }
        } 
        return query;
    }

    static Command parseQuery(String queryStr) throws QueryPlannerException {
        Command query = null;
        try {
            query = QueryParser.getQueryParser().parseCommand(queryStr);            
        } catch (QueryParserException e) {
            throw new QueryPlannerException(e, QueryExecPlugin.Util.getString(ErrorMessageKeys.OPTIMIZER_0054, new Object[]{queryStr}));
        }
        return query;
    }
    
    /** 
     * Resolve a command using the metadata in the planner environment.
     * @param query The query to resolve
     * @param planEnv The planner environment
     * @throws MetaMatrixComponentException
     * @throws QueryPlannerException
     * @since 4.3
     */
    static void resolveQuery(Command query, TempMetadataAdapter metadata) 
        throws MetaMatrixComponentException, QueryPlannerException {
        // Run resolver
        try {
            QueryResolver.resolveCommand(query, Collections.EMPTY_MAP, true, metadata, AnalysisRecord.createNonRecordingRecord());
        } catch(QueryResolverException e) {
            throw new QueryPlannerException(e, e.getMessage());
        }
    }

    /** 
     * Rewrite a command using the metadata in the planner environment.
     * @param query The query to rewrite
     * @param planEnv The planner environment
     * @throws QueryPlannerException
     * @since 4.3
     */
    static void rewriteQuery(Command query, QueryMetadataInterface metadata, CommandContext context) 
        throws QueryPlannerException {
        try {
            QueryRewriter.rewrite(query, null, metadata, context);
        } catch(QueryValidatorException e) {
            throw new QueryPlannerException(e, e.getMessage());
        }
    }

    /**
     * Returns the query node (object holding SQL query transformation) for the
     * indicated table
     * @param groupName String name of a temporary group (a.k.a. temp table)
     * @param metadata QueryMetadataInterface source of metadata
     * @return QueryNode defining the query transformation of the temp table
     * @throws QueryPlannerException for any logical exception detected
     * @throws QueryMetadataException if metadata encounters exception
     * @throws MetaMatrixComponentException unexpected exception
     */
    static QueryNode getQueryNode(String groupName, QueryMetadataInterface metadata)
        throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException{

        QueryNode queryNode = null;
        try {
            GroupSymbol gs = new GroupSymbol(groupName);
            ResolverUtil.resolveGroup(gs, metadata);
            queryNode = metadata.getVirtualPlan(gs.getMetadataID());
        } catch (QueryResolverException e) {
            throw new QueryPlannerException(e, ErrorMessageKeys.OPTIMIZER_0029, QueryExecPlugin.Util.getString(ErrorMessageKeys.OPTIMIZER_0029, groupName));
        }
        return queryNode;
    }    
    
    static Query wrapQuery(FromClause fromClause, String groupName) {
        Select select = new Select();
        select.addSymbol(new AllInGroupSymbol(groupName + ".*")); //$NON-NLS-1$
        Query query = new Query();
        query.setSelect(select);
        From from = new From();
        from.addClause(fromClause);
        query.setFrom(from);        
        return query;
    }

    public static GroupSymbol createResolvedGroup(String groupName, QueryMetadataInterface metadata) 
        throws MetaMatrixComponentException {
        GroupSymbol group = new GroupSymbol(groupName);
        return createResolvedGroup(group, metadata);
    }
    
    public static GroupSymbol createResolvedGroup(GroupSymbol group, QueryMetadataInterface metadata) 
        throws MetaMatrixComponentException {
        try {
            ResolverUtil.resolveGroup(group, metadata);
            return group;
        } catch (QueryResolverException e) {
            throw new MetaMatrixComponentException(e);
        }
    }
        
    static Command getQueryFromQueryNode(String groupName, XMLPlannerEnvironment planEnv) 
        throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {
        
        QueryNode queryNode = QueryUtil.getQueryNode(groupName, planEnv.getGlobalMetadata());
        Command command = QueryUtil.getQuery(queryNode);
        return command;
    }     
    
    static void handleBindings(LanguageObject object, QueryNode planNode, XMLPlannerEnvironment planEnv) 
        throws QueryResolverException, QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {

        List parsedBindings = parseBindings(planNode, planEnv);
    
        if (!parsedBindings.isEmpty()) {
            //use ReferenceBindingReplacer Visitor
            ReferenceBindingReplacerVisitor.replaceReferences(object, parsedBindings);
        }
    }    
    
    static List parseBindings(QueryNode planNode, XMLPlannerEnvironment planEnv) throws MetaMatrixComponentException {
        Collection bindingsCol = planNode.getBindings();
        if (bindingsCol == null) {
            return Collections.EMPTY_LIST;
        }
        
        List parsedBindings = new ArrayList(bindingsCol.size());
        for (Iterator bindings=bindingsCol.iterator(); bindings.hasNext();) {
            try {
                ElementSymbol binding = (ElementSymbol)QueryParser.getQueryParser().parseExpression((String)bindings.next());
                parsedBindings.add(binding);
            } catch (QueryParserException err) {
                throw new MetaMatrixComponentException(err);
            }
        }
        return parsedBindings;
    }

    static Map createSymbolMap(GroupSymbol oldGroup, final String newGroup, Collection projectedElements) {
        HashMap symbolMap = new HashMap();
        symbolMap.put(oldGroup, new GroupSymbol(newGroup));

        for (Iterator i = projectedElements.iterator(); i.hasNext();) {
            ElementSymbol element = (ElementSymbol)i.next();

            symbolMap.put(element, new ElementSymbol(newGroup + ElementSymbol.SEPARATOR + element.getShortName()));
        }
        return symbolMap;
    }

    
    static List getReferences(Command query, boolean embeddedOnly) {
        List boundList = new ArrayList();
        
        Collection commands = null;
        
        if (embeddedOnly) {
            commands = new LinkedList();
        } else {
            commands = new LinkedList(CommandCollectorVisitor.getNonEmbeddedCommands(query));
        }
        commands.add(query);
        
        for (Iterator cmds = commands.iterator(); cmds.hasNext();) {
            Command command = (Command)cmds.next();
            for (Iterator refs = ReferenceCollectorVisitor.getReferences(command).iterator(); refs.hasNext();) {
                Reference ref = (Reference) refs.next();
                Expression expr = ref.getExpression();
                if (!(expr instanceof ElementSymbol)){
                    continue;
                }
                ElementSymbol elem = (ElementSymbol)expr;
                
                if (!query.getExternalGroupContexts().getGroups().contains(elem.getGroupSymbol())) {
                    continue;
                }                
                boundList.add(ref);
            }
        }
        return boundList;
    }     
}
