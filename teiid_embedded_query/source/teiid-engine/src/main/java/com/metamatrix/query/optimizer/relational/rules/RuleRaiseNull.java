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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.query.QueryMetadataException;
import com.metamatrix.api.exception.query.QueryPlannerException;
import com.metamatrix.core.util.Assertion;
import com.metamatrix.query.analysis.AnalysisRecord;
import com.metamatrix.query.metadata.QueryMetadataInterface;
import com.metamatrix.query.optimizer.capabilities.CapabilitiesFinder;
import com.metamatrix.query.optimizer.relational.OptimizerRule;
import com.metamatrix.query.optimizer.relational.RuleStack;
import com.metamatrix.query.optimizer.relational.plantree.NodeConstants;
import com.metamatrix.query.optimizer.relational.plantree.NodeEditor;
import com.metamatrix.query.optimizer.relational.plantree.NodeFactory;
import com.metamatrix.query.optimizer.relational.plantree.PlanNode;
import com.metamatrix.query.sql.lang.JoinType;
import com.metamatrix.query.sql.lang.SetQuery;
import com.metamatrix.query.sql.symbol.AliasSymbol;
import com.metamatrix.query.sql.symbol.ExpressionSymbol;
import com.metamatrix.query.sql.symbol.GroupSymbol;
import com.metamatrix.query.sql.symbol.SingleElementSymbol;
import com.metamatrix.query.sql.util.SymbolMap;
import com.metamatrix.query.util.CommandContext;

/**
 * Will attempt to raise null nodes to their highest points 
 */
public final class RuleRaiseNull implements OptimizerRule {

	public PlanNode execute(PlanNode plan, QueryMetadataInterface metadata, CapabilitiesFinder capFinder, RuleStack rules, AnalysisRecord analysisRecord, CommandContext context)
		throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {

        List<PlanNode> nodes = NodeEditor.findAllNodes(plan, NodeConstants.Types.NULL);
        
        //create a new list to iterate over since the original will be modified
        for (PlanNode nullNode : new LinkedList<PlanNode>(nodes)) {
            while (nullNode.getParent() != null && nodes.contains(nullNode)) {
                // Attempt to raise the node
                PlanNode newRoot = raiseNullNode(plan, nodes, nullNode, metadata, capFinder);
                if(newRoot != null) {
                    plan = newRoot;
                } else {
                    break;
                }
            } 
            
            if (nullNode.getParent() == null) {
                nodes.remove(nullNode);
            }                            
        }
                
        return plan;
	}
    
    /**
     * @param nullNode
     * @param metadata
     * @param capFinder
     * @return null if the raising should not continue, else the newRoot
     */
    PlanNode raiseNullNode(PlanNode rootNode, List<PlanNode> nodes, PlanNode nullNode, QueryMetadataInterface metadata, CapabilitiesFinder capFinder) 
    throws QueryPlannerException, QueryMetadataException, MetaMatrixComponentException {
        
        PlanNode parentNode = nullNode.getParent();
        
        switch(parentNode.getType()) {
            case NodeConstants.Types.JOIN:
            {
                JoinType jt = (JoinType)parentNode.getProperty(NodeConstants.Info.JOIN_TYPE);
                if (jt == JoinType.JOIN_CROSS || jt == JoinType.JOIN_INNER) {
                    return raiseNullNode(rootNode, parentNode, nullNode, nodes);
                }
                //for outer joins if the null node is on the outer side, then the join itself is null
                //if the null node is on the inner side, then the join can be removed but the null values 
                //coming from the inner side will need to be placed into the frame
                if (jt == JoinType.JOIN_LEFT_OUTER) {
                    if (nullNode == parentNode.getFirstChild()) {
                        return raiseNullNode(rootNode, parentNode, nullNode, nodes);
                    } 
                    raiseNullThroughJoin(metadata, parentNode, parentNode.getLastChild());
                    return null;
                } 
                if (jt == JoinType.JOIN_RIGHT_OUTER) {
                    if (nullNode == parentNode.getLastChild()) {
                        return raiseNullNode(rootNode, parentNode, nullNode, nodes);
                    } 
                    raiseNullThroughJoin(metadata, parentNode, parentNode.getFirstChild());
                    return null;
                }   
                if (jt == JoinType.JOIN_FULL_OUTER) {
                    if (nullNode == parentNode.getLastChild()) {
                        raiseNullThroughJoin(metadata, parentNode, parentNode.getLastChild());
                    } else {
                        raiseNullThroughJoin(metadata, parentNode, parentNode.getFirstChild());
                    }
                    return null;
                }
                break;
            }            
            case NodeConstants.Types.SET_OP:
            {
                boolean isLeftChild = parentNode.getFirstChild() == nullNode;
                SetQuery.Operation operation = (SetQuery.Operation)parentNode.getProperty(NodeConstants.Info.SET_OPERATION);
                boolean raiseOverSetOp = (operation == SetQuery.Operation.INTERSECT || (operation == SetQuery.Operation.EXCEPT && isLeftChild));
                
                if (raiseOverSetOp) {
                    return raiseNullNode(rootNode, parentNode, nullNode, nodes);
                }
                
                boolean isAll = parentNode.hasBooleanProperty(NodeConstants.Info.USE_ALL);
                
                if (isLeftChild) {
                    PlanNode firstProject = NodeEditor.findNodePreOrder(parentNode, NodeConstants.Types.PROJECT);
                    
                    if (firstProject == null) { // will only happen if the other branch has only null nodes
                        return raiseNullNode(rootNode, parentNode, nullNode, nodes);
                    }

                    List<SingleElementSymbol> newProjectSymbols = (List<SingleElementSymbol>)firstProject.getProperty(NodeConstants.Info.PROJECT_COLS);
                    List<SingleElementSymbol> oldProjectSymbols = (List<SingleElementSymbol>)nullNode.getProperty(NodeConstants.Info.PROJECT_COLS);
                    
                    for (int i = 0; i < newProjectSymbols.size(); i++) {
                        SingleElementSymbol newSes = newProjectSymbols.get(i);
                        SingleElementSymbol oldSes = oldProjectSymbols.get(i);
                        if (newSes instanceof ExpressionSymbol || !newSes.getShortCanonicalName().equals(oldSes.getShortCanonicalName())) {
                            if (newSes instanceof AliasSymbol) {
                                newSes = ((AliasSymbol)newSes).getSymbol();
                            }
                            newProjectSymbols.set(i, new AliasSymbol(oldSes.getShortName(), newSes));
                        }
                    }
                    
                    PlanNode sort = NodeEditor.findParent(firstProject, NodeConstants.Types.SORT, NodeConstants.Types.SOURCE);
                    
                    if (sort != null) { //correct the sort to the new columns as well
                        List<SingleElementSymbol> sortOrder = (List<SingleElementSymbol>)sort.getProperty(NodeConstants.Info.SORT_ORDER);
                        for (int i = 0; i < sortOrder.size(); i++) {
                            SingleElementSymbol sortElement = sortOrder.get(i);
                            sortElement = newProjectSymbols.get(oldProjectSymbols.indexOf(sortElement));
                            sortOrder.set(i, sortElement);
                        }
                    }
                    
                    PlanNode sourceNode = NodeEditor.findParent(parentNode, NodeConstants.Types.SOURCE);
                    if (sourceNode != null && NodeEditor.findNodePreOrder(sourceNode, NodeConstants.Types.PROJECT) == firstProject) {
                        SymbolMap symbolMap = (SymbolMap)sourceNode.getProperty(NodeConstants.Info.SYMBOL_MAP);
                        symbolMap = SymbolMap.createSymbolMap(symbolMap.getKeys(), newProjectSymbols);
                        sourceNode.setProperty(NodeConstants.Info.SYMBOL_MAP, symbolMap);
                    }                                        
                }
                
                NodeEditor.removeChildNode(parentNode, nullNode);

                PlanNode grandParent = parentNode.getParent();
                
                if (!isAll) { //ensure that the new child is distinct
                	PlanNode nestedSetOp = NodeEditor.findNodePreOrder(parentNode.getFirstChild(), NodeConstants.Types.SET_OP, NodeConstants.Types.SOURCE);
                	if (nestedSetOp != null) {
                		nestedSetOp.setProperty(NodeConstants.Info.USE_ALL, false);
                	} else if (NodeEditor.findNodePreOrder(parentNode.getFirstChild(), NodeConstants.Types.DUP_REMOVE, NodeConstants.Types.SOURCE) == null) {
                		parentNode.getFirstChild().addAsParent(NodeFactory.getNewNode(NodeConstants.Types.DUP_REMOVE));
                	}
                }
                
                if (grandParent == null) {
                    PlanNode newRoot = parentNode.getFirstChild();
                    parentNode.removeChild(newRoot);
                    return newRoot;
                }

                //remove the set op
                NodeEditor.removeChildNode(grandParent, parentNode);
                
                PlanNode sourceNode = NodeEditor.findParent(grandParent.getFirstChild(), NodeConstants.Types.SOURCE, NodeConstants.Types.SET_OP);
                                
                if (sourceNode != null) {
                    return RuleMergeVirtual.doMerge(sourceNode, rootNode, metadata);
                }
                return null;
            }
            case NodeConstants.Types.GROUP:
            {
                //if there are grouping columns, then we can raise
                if (parentNode.hasCollectionProperty(NodeConstants.Info.GROUP_COLS)) {
                    return raiseNullNode(rootNode, parentNode, nullNode, nodes);
                }
                break; //- the else case could be implemented, but it's a lot of work for little gain, since the null node can't raise higher
            }
			case NodeConstants.Types.PROJECT: 
			{
				// check for project into
				PlanNode upperProject = NodeEditor.findParent(parentNode.getParent(), NodeConstants.Types.PROJECT);	
				
				if (upperProject == null
						|| upperProject.getProperty(NodeConstants.Info.INTO_GROUP) == null) {
	                return raiseNullNode(rootNode, parentNode, nullNode, nodes);
				}
				break;
			}
            default:
            {
                return raiseNullNode(rootNode, parentNode, nullNode, nodes);
            }                      
        }  
        return null;
    }

    private PlanNode raiseNullNode(PlanNode rootNode, PlanNode parentNode, PlanNode nullNode, List<PlanNode> nodes) {
        if (parentNode.getType() == NodeConstants.Types.SOURCE) {
            nullNode.getGroups().clear();
        } else if (parentNode.getType() == NodeConstants.Types.PROJECT) {
            nullNode.setProperty(NodeConstants.Info.PROJECT_COLS, parentNode.getProperty(NodeConstants.Info.PROJECT_COLS));
        }
        nullNode.addGroups(parentNode.getGroups());
        parentNode.removeChild(nullNode);
        nodes.removeAll(NodeEditor.findAllNodes(parentNode, NodeConstants.Types.NULL));
        if (parentNode.getParent() != null) {
            parentNode.getParent().replaceChild(parentNode, nullNode);
        } else {
            rootNode = nullNode;
        }
        return rootNode;
    }

    /** 
     * Given a joinNode that should be an outer join and a null node as one of its children, replace elements in 
     * the current frame from the null node groups with null values 
     * 
     * @param metadata
     * @param joinNode
     * @param nullNode
     * @throws QueryPlannerException
     * @throws QueryMetadataException
     * @throws MetaMatrixComponentException
     */
    static void raiseNullThroughJoin(QueryMetadataInterface metadata,
                                      PlanNode joinNode,
                                      PlanNode nullNode) throws QueryPlannerException,
                                                         QueryMetadataException,
                                                         MetaMatrixComponentException {
        Assertion.assertTrue(joinNode.getType() == NodeConstants.Types.JOIN);
        Assertion.assertTrue(nullNode.getType() == NodeConstants.Types.NULL);
        Assertion.assertTrue(nullNode.getParent() == joinNode);
        
        PlanNode frameStart = joinNode.getParent();
        
        NodeEditor.removeChildNode(joinNode, nullNode);
        NodeEditor.removeChildNode(joinNode.getParent(), joinNode);
        
        for (GroupSymbol group : nullNode.getGroups()) {
            Map nullSymbolMap = FrameUtil.buildSymbolMap(group, null, metadata);
            FrameUtil.convertFrame(frameStart, group, null, nullSymbolMap, metadata);
        }
        
    }    
    
    public String toString() {
		return "RaiseNull"; //$NON-NLS-1$
	}
	
}