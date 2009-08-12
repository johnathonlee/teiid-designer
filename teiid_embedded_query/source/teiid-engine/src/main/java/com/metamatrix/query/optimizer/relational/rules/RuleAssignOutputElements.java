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

package com.metamatrix.query.optimizer.relational.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.query.QueryMetadataException;
import com.metamatrix.api.exception.query.QueryPlannerException;
import com.metamatrix.query.analysis.AnalysisRecord;
import com.metamatrix.query.metadata.QueryMetadataInterface;
import com.metamatrix.query.metadata.SupportConstants;
import com.metamatrix.query.optimizer.capabilities.CapabilitiesFinder;
import com.metamatrix.query.optimizer.relational.OptimizerRule;
import com.metamatrix.query.optimizer.relational.RuleStack;
import com.metamatrix.query.optimizer.relational.plantree.NodeConstants;
import com.metamatrix.query.optimizer.relational.plantree.NodeEditor;
import com.metamatrix.query.optimizer.relational.plantree.PlanNode;
import com.metamatrix.query.resolver.util.ResolverUtil;
import com.metamatrix.query.sql.lang.Command;
import com.metamatrix.query.sql.lang.Criteria;
import com.metamatrix.query.sql.lang.StoredProcedure;
import com.metamatrix.query.sql.symbol.AggregateSymbol;
import com.metamatrix.query.sql.symbol.AliasSymbol;
import com.metamatrix.query.sql.symbol.ElementSymbol;
import com.metamatrix.query.sql.symbol.Expression;
import com.metamatrix.query.sql.symbol.ExpressionSymbol;
import com.metamatrix.query.sql.symbol.GroupSymbol;
import com.metamatrix.query.sql.symbol.SingleElementSymbol;
import com.metamatrix.query.sql.util.SymbolMap;
import com.metamatrix.query.sql.visitor.AggregateSymbolCollectorVisitor;
import com.metamatrix.query.sql.visitor.GroupsUsedByElementsVisitor;
import com.metamatrix.query.util.CommandContext;

/**
 * <p>This rule is responsible for assigning the output elements to every node in the
 * plan.  The output elements define the columns that are returned from every node.
 * This is generally done by figuring out top-down all the elements required to
 * execute the operation at each node and making sure those elements are selected
 * from the children nodes.  </p>
 */
public final class RuleAssignOutputElements implements OptimizerRule {

    /**
     * Execute the rule.  This rule is executed exactly once during every planning
     * call.  The plan is modified in place - only properties are manipulated, structure
     * is unchanged.
     * @param plan The plan to execute rule on
     * @param metadata The metadata interface
     * @param rules The rule stack, not modified
     * @return The updated plan
     */
	public PlanNode execute(PlanNode plan, QueryMetadataInterface metadata, CapabilitiesFinder capFinder, RuleStack rules, AnalysisRecord analysisRecord, CommandContext context)
		throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {

		// Record project node output columns in top node
		PlanNode projectNode = NodeEditor.findNodePreOrder(plan, NodeConstants.Types.PROJECT);

        if(projectNode == null) {
            return plan;
        }

		List<SingleElementSymbol> projectCols = (List<SingleElementSymbol>)projectNode.getProperty(NodeConstants.Info.PROJECT_COLS);

		assignOutputElements(plan, projectCols, metadata, capFinder, rules, analysisRecord, context);

		return plan;
	}

    /**
     * <p>Assign the output elements at a particular node and recurse the tree.  The
     * outputElements needed from above the node have been collected in
     * outputElements.</p>
     *
     * <p>SOURCE nodes:  If we find a SOURCE node, this must define the top
     * of a virtual group.  Physical groups can be identified by ACCESS nodes
     * at this point in the planning stage.  So, we filter the virtual elements
     * in the virtual source based on the required output elements.</p>
     *
     * <p>SET_OP nodes:  If we hit a SET_OP node, this must be a union.  Unions
     * require a lot of special care.  Unions have many branches and the projected
     * elements in each branch are "equivalent" in terms of nodes above the union.
     * This means that any filtering must occur in an identical way in all branches
     * of a union.</p>
     *
     * @param root Node to assign
     * @param outputElements Output elements needed for this node
     * @param metadata Metadata implementation
     */
	private void assignOutputElements(PlanNode root, List<SingleElementSymbol> outputElements, QueryMetadataInterface metadata, CapabilitiesFinder capFinder, RuleStack rules, AnalysisRecord analysisRecord, CommandContext context)
		throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {

	    int nodeType = root.getType();
        
        //fix no output elements if possible
        if(outputElements.isEmpty() && (nodeType == NodeConstants.Types.ACCESS || nodeType == NodeConstants.Types.SOURCE)) {
            PlanNode groupSource = FrameUtil.findJoinSourceNode(root);
            ElementSymbol symbol = selectOutputElement(groupSource.getGroups(), metadata);
            if (symbol != null) {//can be null for procedures
                outputElements.add(symbol);
            }
        }
        
		// Update this node's output columns based on parent's columns
		root.setProperty(NodeConstants.Info.OUTPUT_COLS, outputElements);
        
		if (root.getChildCount() == 0) {
            return;
        }

        switch (nodeType) {
		    case NodeConstants.Types.ACCESS:
		        Command command = FrameUtil.getNonQueryCommand(root);
	            if (command instanceof StoredProcedure) {
	                //if the access node represents a stored procedure, then we can't actually change the output symbols
	                root.setProperty(NodeConstants.Info.OUTPUT_COLS, command.getProjectedSymbols());
	            }
		    case NodeConstants.Types.TUPLE_LIMIT:
		    case NodeConstants.Types.DUP_REMOVE:
		    case NodeConstants.Types.SORT:
		    	if (root.hasBooleanProperty(NodeConstants.Info.UNRELATED_SORT)) {
		    		//add missing sort columns
			    	List<SingleElementSymbol> elements = (List<SingleElementSymbol>) root.getProperty(NodeConstants.Info.SORT_ORDER);
			    	outputElements = new ArrayList<SingleElementSymbol>(outputElements);
			    	for (SingleElementSymbol singleElementSymbol : elements) {
						if (!outputElements.contains(singleElementSymbol)) {
							outputElements.add(singleElementSymbol);
						}
					}
		    	}
		        assignOutputElements(root.getLastChild(), outputElements, metadata, capFinder, rules, analysisRecord, context);
		        break;
		    case NodeConstants.Types.SOURCE: {
		        outputElements = (List<SingleElementSymbol>)determineSourceOutput(root, outputElements);
	            root.setProperty(NodeConstants.Info.OUTPUT_COLS, outputElements);
	            assignOutputElements(root.getFirstChild(), filterVirtualElements(root, outputElements, metadata), metadata, capFinder, rules, analysisRecord, context);
		        break;
		    }
		    case NodeConstants.Types.SET_OP: {
		        for (PlanNode childNode : root.getChildren()) {
		            PlanNode projectNode = NodeEditor.findNodePreOrder(childNode, NodeConstants.Types.PROJECT);
	                List<SingleElementSymbol> projectCols = (List<SingleElementSymbol>)projectNode.getProperty(NodeConstants.Info.PROJECT_COLS);
	                assignOutputElements(childNode, projectCols, metadata, capFinder, rules, analysisRecord, context);
	            }
	            break;
		    }
		    default: {
	            GroupSymbol intoGroup = (GroupSymbol)root.getProperty(NodeConstants.Info.INTO_GROUP);
	            if (intoGroup != null) { //if this is a project into, treat the nodes under the source as a new plan root
	                PlanNode intoRoot = NodeEditor.findNodePreOrder(root, NodeConstants.Types.SOURCE);
	                execute(intoRoot.getFirstChild(), metadata, capFinder, rules, analysisRecord, context);
	                return;
	            }
	            
	            List<SingleElementSymbol> requiredInput = collectRequiredInputSymbols(root);
	            
	            // Call children recursively
	            if(root.getChildCount() == 1) {
	                assignOutputElements(root.getLastChild(), requiredInput, metadata, capFinder, rules, analysisRecord, context);
	            } else {
	                //determine which elements go to each side of the join
	                for (PlanNode childNode : root.getChildren()) {
	                    Set<GroupSymbol> filterGroups = FrameUtil.findJoinSourceNode(childNode).getGroups();
	                    List<SingleElementSymbol> filteredElements = filterElements(requiredInput, filterGroups);

	                    // Call child recursively
	                    assignOutputElements(childNode, filteredElements, metadata, capFinder, rules, analysisRecord, context);
	                }
	            }
		    }
		}
	}

    private List<SingleElementSymbol> filterElements(Collection<? extends SingleElementSymbol> requiredInput, Set<GroupSymbol> filterGroups) {
        List<SingleElementSymbol> filteredElements = new ArrayList<SingleElementSymbol>();
        for (SingleElementSymbol element : requiredInput) {
            if(filterGroups.containsAll(GroupsUsedByElementsVisitor.getGroups(element))) {
                filteredElements.add(element);
            }
        }
        return filteredElements;
    }

    /** 
     * A special case to consider is when the virtual group is defined by a
     * UNION (no ALL) or a SELECT DISTINCT.  In this case, the dup removal means 
     * that all columns need to be used to determine duplicates.  So, filtering the
     * columns at all will alter the number of rows flowing through the frame.
     * So, in this case filtering should not occur.  In fact the output columns
     * that were set on root above are filtered, but we actually want all the
     * virtual elements - so just reset it and proceed as before
     */
    static List<? extends SingleElementSymbol> determineSourceOutput(PlanNode root,
                                           List<SingleElementSymbol> outputElements) {
        PlanNode virtualRoot = root.getLastChild();
        
        if(hasDupRemoval(virtualRoot)) {
            // Reset the outputColumns for this source node to be all columns for the virtual group
            SymbolMap symbolMap = (SymbolMap) root.getProperty(NodeConstants.Info.SYMBOL_MAP);
            return symbolMap.getKeys();
        } 
    	PlanNode limit = NodeEditor.findNodePreOrder(root, NodeConstants.Types.TUPLE_LIMIT, NodeConstants.Types.PROJECT);
		if (limit == null) {
			return outputElements;
		}
        //reset the output elements to be the output columns + what's required by the sort
		PlanNode sort = NodeEditor.findNodePreOrder(limit, NodeConstants.Types.SORT, NodeConstants.Types.PROJECT);
        if (sort == null) {
        	return outputElements;
        }
        List sortOrder = (List)sort.getProperty(NodeConstants.Info.SORT_ORDER);
        List<SingleElementSymbol> topCols = FrameUtil.findTopCols(sort);
        
        SymbolMap symbolMap = (SymbolMap)root.getProperty(NodeConstants.Info.SYMBOL_MAP);
        
        List<ElementSymbol> symbolOrder = symbolMap.getKeys();
        
        for (final Iterator iterator = sortOrder.iterator(); iterator.hasNext();) {
            final Expression expr = (Expression)iterator.next();
            int index = topCols.indexOf(expr);
            ElementSymbol symbol = symbolOrder.get(index);
            if (!outputElements.contains(symbol)) {
                outputElements.add(symbol);
            }
        }
        return outputElements;
    }
    
    /**
     * Find a selectable element in the specified groups.  This is a helper for fixing
     * the "no elements" case.
     *
     * @param groups Bunch of groups
     * @param metadata Metadata implementation
     * @throws QueryPlannerException
     */
    private ElementSymbol selectOutputElement(Collection<GroupSymbol> groups, QueryMetadataInterface metadata)
        throws QueryMetadataException, MetaMatrixComponentException {

        // Find a group with selectable elements and pick the first one
        for (GroupSymbol group : groups) {
            List<ElementSymbol> elements = (List<ElementSymbol>)ResolverUtil.resolveElementsInGroup(group, metadata);
            
            for (ElementSymbol element : elements) {
                if(metadata.elementSupports(element.getMetadataID(), SupportConstants.Element.SELECT)) {
                    element = (ElementSymbol)element.clone();
                    element.setGroupSymbol(group);
                    return element;
                }
            }
        }
        
        return null;
    }

    /**
     * <p>This method looks at a source node, which defines a virtual group, and filters the
     * virtual elements defined by the group down into just the output elements needed
     * by that source node.  This means, for instance, that the PROJECT node at the top
     * of the virtual group might need to have some elements removed from the project as
     * those elements are no longer needed.  </p>
     *
     * <p>One special case that is handled here is when a virtual group is defined by
     * a UNION ALL.  In this case, the various branches of the union have elements defined
     * and filtering must occur identically in all branches of the union.  </p>
     *
     * @param sourceNode Node to filter
     * @param metadata Metadata implementation
     * @return The filtered list of columns for this node (used in recursing tree)
     */
	static List<SingleElementSymbol> filterVirtualElements(PlanNode sourceNode, List<SingleElementSymbol> outputColumns, QueryMetadataInterface metadata) {

		PlanNode virtualRoot = sourceNode.getLastChild();

		// Update project cols - typically there is exactly one and that node can
	    // just get the filteredCols determined above.  In the case of one or more
	    // nested set operations (UNION, INTERSECT, EXCEPT) there will be 2 or more
	    // projects.  
	    List<PlanNode> allProjects = NodeEditor.findAllNodes(virtualRoot, NodeConstants.Types.PROJECT, NodeConstants.Types.PROJECT);

        int[] filteredIndex = new int[outputColumns.size()];
        Arrays.fill(filteredIndex, -1);
        
        SymbolMap symbolMap = (SymbolMap)sourceNode.getProperty(NodeConstants.Info.SYMBOL_MAP);
        List originalOrder = symbolMap.getKeys();
        
        for (int i = 0; i < outputColumns.size(); i++) {
            Expression expr = outputColumns.get(i);
            filteredIndex[i] = originalOrder.indexOf(expr);
        }
        
        List<SingleElementSymbol> newCols = null;
        for(int i=allProjects.size()-1; i>=0; i--) {
            PlanNode projectNode = allProjects.get(i);
            List<SingleElementSymbol> projectCols = (List<SingleElementSymbol>) projectNode.getProperty(NodeConstants.Info.PROJECT_COLS);

            newCols = new ArrayList<SingleElementSymbol>();
            for(int j=0; j<filteredIndex.length; j++) {
                newCols.add(projectCols.get(filteredIndex[j]));
            }
            
            projectNode.setProperty(NodeConstants.Info.PROJECT_COLS, newCols);
        }

		// Create output columns for virtual group project
		return newCols;
	}

    /** 
     * Check all branches for either a dup removal or a non all union.
     *
     * @param node Root of virtual group (node below source node)
     * @return True if the virtual group at this source node does dup removal
     */
	static boolean hasDupRemoval(PlanNode node) {
        
        List<PlanNode> nodes = NodeEditor.findAllNodes(node, NodeConstants.Types.DUP_REMOVE|NodeConstants.Types.SET_OP, NodeConstants.Types.DUP_REMOVE|NodeConstants.Types.PROJECT);
        
        for (PlanNode planNode : nodes) {
            if (planNode.getType() == NodeConstants.Types.DUP_REMOVE
                || (planNode.getType() == NodeConstants.Types.SET_OP && Boolean.FALSE.equals(planNode.getProperty(NodeConstants.Info.USE_ALL)))) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Collect all required input symbols for a given node.  Input symbols
     * are any symbols that are required in the processing of this node,
     * for instance to create a new element symbol or sort on it, etc.
     * @param node Node to collect for
     * @param requiredSymbols Collection<SingleElementSymbol> Place to collect required symbols
     * @param createdSymbols Set<SingleElementSymbol> Place to collect any symbols created by this node
     */
	private List<SingleElementSymbol> collectRequiredInputSymbols(PlanNode node) {

        Set<SingleElementSymbol> requiredSymbols = new LinkedHashSet<SingleElementSymbol>();
        Set<SingleElementSymbol> createdSymbols = new HashSet<SingleElementSymbol>();
        
        List<SingleElementSymbol> outputCols = (List<SingleElementSymbol>) node.getProperty(NodeConstants.Info.OUTPUT_COLS);

		switch(node.getType()) {
			case NodeConstants.Types.PROJECT:
            {
                List<SingleElementSymbol> projectCols = (List<SingleElementSymbol>) node.getProperty(NodeConstants.Info.PROJECT_COLS);
                for (SingleElementSymbol ss : projectCols) {
                    if(ss instanceof AliasSymbol) {
                        createdSymbols.add(ss);
                        
                        ss = ((AliasSymbol)ss).getSymbol();
                    }
                    
                    if (ss instanceof ExpressionSymbol && !(ss instanceof AggregateSymbol)) {
                        ExpressionSymbol exprSymbol = (ExpressionSymbol)ss;
                        
                        if (!exprSymbol.isDerivedExpression()) {
                            createdSymbols.add(ss);
                        } 
                    }
                    AggregateSymbolCollectorVisitor.getAggregates(ss, requiredSymbols, requiredSymbols);                        
                }
				break;
            }
			case NodeConstants.Types.SELECT:
				Criteria selectCriteria = (Criteria) node.getProperty(NodeConstants.Info.SELECT_CRITERIA);
                AggregateSymbolCollectorVisitor.getAggregates(selectCriteria, requiredSymbols, requiredSymbols);
				break;
			case NodeConstants.Types.JOIN:
				List crits = (List) node.getProperty(NodeConstants.Info.JOIN_CRITERIA);
				if(crits != null) {
					Iterator critIter = crits.iterator();
					while(critIter.hasNext()) {
						Criteria joinCriteria = (Criteria) critIter.next();
						AggregateSymbolCollectorVisitor.getAggregates(joinCriteria, requiredSymbols, requiredSymbols);
					}
				}
				break;
			case NodeConstants.Types.GROUP:
				List<SingleElementSymbol> groupCols = (List<SingleElementSymbol>) node.getProperty(NodeConstants.Info.GROUP_COLS);
				if(groupCols != null) {
				    for (SingleElementSymbol expression : groupCols) {
                        if(expression instanceof ElementSymbol || expression instanceof AggregateSymbol) {
                            requiredSymbols.add(expression);
                        } else {    
                            ExpressionSymbol exprSymbol = (ExpressionSymbol) expression;
                            Expression expr = exprSymbol.getExpression();
                            AggregateSymbolCollectorVisitor.getAggregates(expr, requiredSymbols, requiredSymbols);
                            createdSymbols.add(exprSymbol);
                        }
                    }
				}

				// Take credit for creating any aggregates that are needed above
				for (SingleElementSymbol outputSymbol : outputCols) {
					if(outputSymbol instanceof AggregateSymbol) {
					    AggregateSymbol agg = (AggregateSymbol)outputSymbol;
					    createdSymbols.add(outputSymbol);
					    
	                    Expression aggExpr = agg.getExpression();
	                    if(aggExpr != null) {
	                        AggregateSymbolCollectorVisitor.getAggregates(aggExpr, requiredSymbols, requiredSymbols);
	                    }
					}
				}

				break;
		}

        // Gather elements from correlated subquery references;
        // currently only for SELECT or PROJECT nodes
		for (SymbolMap refs : node.getCorrelatedReferences()) {
        	for (Expression expr : refs.asMap().values()) {
                AggregateSymbolCollectorVisitor.getAggregates(expr, requiredSymbols, requiredSymbols);
            }
        }
        
/*        Set<SingleElementSymbol> tempRequired = requiredSymbols;
        requiredSymbols = new LinkedHashSet<SingleElementSymbol>(outputCols);
        requiredSymbols.removeAll(createdSymbols);
        requiredSymbols.addAll(tempRequired);
*/        
        // Add any columns to required that are in this node's output but were not created here
        for (SingleElementSymbol currentOutputSymbol : outputCols) {
            if(!(createdSymbols.contains(currentOutputSymbol)) ) {
                requiredSymbols.add(currentOutputSymbol);
            }
        }
        
        //further minimize the required symbols based upon underlying expression (accounts for aliasing)
        //TODO: this should depend upon whether the expressions are deterministic
        if (node.getType() == NodeConstants.Types.PROJECT) {
            Set<Expression> expressions = new HashSet<Expression>();
            for (Iterator<SingleElementSymbol> iterator = requiredSymbols.iterator(); iterator.hasNext();) {
                SingleElementSymbol ses = iterator.next();
                if (!expressions.add(SymbolMap.getExpression(ses))) {
                    iterator.remove();
                }
            }
        }
        
        return new ArrayList<SingleElementSymbol>(requiredSymbols);
	}

    /**
     * Get name of the rule
     * @return Name of the rule
     */
	public String toString() {
		return "AssignOutputElements"; //$NON-NLS-1$
	}

}
