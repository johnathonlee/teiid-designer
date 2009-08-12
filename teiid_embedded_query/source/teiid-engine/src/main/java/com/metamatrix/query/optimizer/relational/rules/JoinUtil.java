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

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.metamatrix.api.exception.query.QueryValidatorException;
import com.metamatrix.core.util.Assertion;
import com.metamatrix.query.metadata.QueryMetadataInterface;
import com.metamatrix.query.optimizer.relational.plantree.NodeConstants;
import com.metamatrix.query.optimizer.relational.plantree.PlanNode;
import com.metamatrix.query.rewriter.QueryRewriter;
import com.metamatrix.query.sql.LanguageObject;
import com.metamatrix.query.sql.lang.Criteria;
import com.metamatrix.query.sql.lang.JoinType;
import com.metamatrix.query.sql.navigator.PreOrderNavigator;
import com.metamatrix.query.sql.symbol.Constant;
import com.metamatrix.query.sql.symbol.ElementSymbol;
import com.metamatrix.query.sql.symbol.Expression;
import com.metamatrix.query.sql.symbol.GroupSymbol;
import com.metamatrix.query.sql.visitor.ExpressionMappingVisitor;

/**
 * <p> 
 * Utility methods for query planning related to joins.
 * </p><p>
 * In some cases, a query plan can be made more optimal via a few possible
 * criteria/join optimizations.
 * </p>
 */
public class JoinUtil {

    /** 
     * Can't instantiate
     */
    private JoinUtil() {
        super();
    }
    
    /**
     * Will attempt to optimize the join type based upon the criteria provided.
     * 
     * Returns the new join type if one is found, otherwise null
     * 
     * An outer join can be optimized if criteria that is not dependent upon null values
     * is applied on the inner side of the join. 
     *  
     * @param critNode
     * @param joinNode
     * @return
     */
    static final JoinType optimizeJoinType(PlanNode critNode, PlanNode joinNode, QueryMetadataInterface metadata) {
        if (critNode.getGroups().isEmpty() || !joinNode.getGroups().containsAll(critNode.getGroups())) {
            return null;
        }

        JoinType joinType = (JoinType) joinNode.getProperty(NodeConstants.Info.JOIN_TYPE);

        if (!joinType.isOuter()) {
            return null;
        }
        
        PlanNode left = joinNode.getFirstChild();
        left = FrameUtil.findJoinSourceNode(left);
        PlanNode right = joinNode.getLastChild();
        right = FrameUtil.findJoinSourceNode(right);
        
        Collection<GroupSymbol> outerGroups = left.getGroups();
        Collection<GroupSymbol> innerGroups = right.getGroups();
        if (joinType == JoinType.JOIN_RIGHT_OUTER) {
            outerGroups = innerGroups;
            innerGroups = left.getGroups(); 
        }
        
        //sanity check
        if ((joinType == JoinType.JOIN_LEFT_OUTER || joinType == JoinType.JOIN_RIGHT_OUTER) 
                        && outerGroups.containsAll(critNode.getGroups())) {
            return null;
        }
        
        Criteria crit = (Criteria)critNode.getProperty(NodeConstants.Info.SELECT_CRITERIA);

        boolean isNullDepdendent = isNullDependent(metadata, innerGroups, crit);
        
        JoinType result = JoinType.JOIN_INNER;
        
        if (joinType == JoinType.JOIN_LEFT_OUTER || joinType == JoinType.JOIN_RIGHT_OUTER) {
            if (isNullDepdendent) {
                return null;
            } 
        } else {
            boolean isNullDepdendentOther = isNullDependent(metadata, outerGroups, crit);
            
            if (isNullDepdendent && isNullDepdendentOther) {
                return null;
            }
            
            if (isNullDepdendent && !isNullDepdendentOther) {
                result =  JoinType.JOIN_LEFT_OUTER;
            } else if (!isNullDepdendent && isNullDepdendentOther) {
                JoinUtil.swapJoinChildren(joinNode);
                result = JoinType.JOIN_LEFT_OUTER;
            }
        }
        
        joinNode.setProperty(NodeConstants.Info.JOIN_TYPE, result);
        
        return result;
    }

    /**
     *  Returns true if the given criteria can be anything other than false (or unknown) 
     *  given all null values for elements in the inner groups
     */
    public static boolean isNullDependent(QueryMetadataInterface metadata,
                                            final Collection<GroupSymbol> innerGroups,
                                            Criteria crit) {
        Criteria simplifiedCrit = (Criteria)replaceWithNullValues(innerGroups, crit);
        try {
            simplifiedCrit = QueryRewriter.rewriteCriteria(simplifiedCrit, null, null, metadata);
        } catch (QueryValidatorException err) {
            //log the exception
            return true;
        }
        return !(simplifiedCrit.equals(QueryRewriter.FALSE_CRITERIA) || simplifiedCrit.equals(QueryRewriter.UNKNOWN_CRITERIA));
    }
    
    public static boolean isNullDependent(QueryMetadataInterface metadata,
                                          final Collection<GroupSymbol> innerGroups,
                                          Expression expr) {
        Expression simplifiedExpression = (Expression)replaceWithNullValues(innerGroups, expr);
        try {
            simplifiedExpression = QueryRewriter.rewriteExpression(simplifiedExpression, null, null, metadata);
        } catch (QueryValidatorException err) {
            //log the exception
            return true;
        }
        return !QueryRewriter.isNull(simplifiedExpression);
    }

    private static LanguageObject replaceWithNullValues(final Collection<GroupSymbol> innerGroups,
                                                        LanguageObject obj) {
        ExpressionMappingVisitor emv = new ExpressionMappingVisitor(null) {
            
            public Expression replaceExpression(Expression element) {
                if (!(element instanceof ElementSymbol)) {
                    return element;
                }
                
                ElementSymbol symbol = (ElementSymbol)element;
                
                if (innerGroups.contains(symbol.getGroupSymbol())) {
                    return new Constant(null, symbol.getType());
                }
                
                return element;
            }
        };
        
        if (obj instanceof ElementSymbol) {
            return emv.replaceExpression((ElementSymbol)obj);
        }
        obj = (LanguageObject)obj.clone();
        PreOrderNavigator.doVisit(obj, emv);
        return obj;
    }

    static JoinType getJoinTypePreventingCriteriaOptimization(PlanNode joinNode, PlanNode critNode) {
        Set<GroupSymbol> groups = critNode.getGroups();
        
        //special case for 0 group criteria
        if (groups.size() == 0) {
            critNode = FrameUtil.findOriginatingNode(critNode, groups);
            if (critNode == null) {
                return null;
            }
            groups = critNode.getGroups();
        }

        return getJoinTypePreventingCriteriaOptimization(joinNode, groups);
    }

    public static JoinType getJoinTypePreventingCriteriaOptimization(PlanNode joinNode,
                                                                      Set<GroupSymbol> groups) {
        JoinType joinType = (JoinType) joinNode.getProperty(NodeConstants.Info.JOIN_TYPE);

        if(!joinType.isOuter()) {
            return null;
        }
        
        if(joinType.equals(JoinType.JOIN_FULL_OUTER)) {
            return joinType;
        } 
        
        Set<GroupSymbol> innerGroups = getInnerSideJoinNodes(joinNode)[0].getGroups();
        for (GroupSymbol group : groups) {
            if (innerGroups.contains(group)) {
                return joinType;
            }            
        }
        
        return null;
    }
            
    /**
     * Can be called after join planning on a join node to get the inner sides of the join 
     * @param joinNode
     * @return
     */
    static PlanNode[] getInnerSideJoinNodes(PlanNode joinNode) {
        Assertion.assertTrue(joinNode.getType() == NodeConstants.Types.JOIN);
        JoinType jt = (JoinType)joinNode.getProperty(NodeConstants.Info.JOIN_TYPE);

        if (jt == JoinType.JOIN_INNER || jt == JoinType.JOIN_CROSS) {
            return new PlanNode[] {joinNode.getFirstChild(), joinNode.getLastChild()};
        }
        if (jt == JoinType.JOIN_RIGHT_OUTER) {
            return new PlanNode[] {joinNode.getFirstChild()};
        }
        if (jt == JoinType.JOIN_LEFT_OUTER) {
            return new PlanNode[] {joinNode.getLastChild()};
        }
        //must be full outer, so there is no inner side
        return new PlanNode[] {};
    }
    
    /** 
     * @param joinNode
     */
    static void swapJoinChildren(PlanNode joinNode) {
        PlanNode leftChild = joinNode.getFirstChild();
        joinNode.removeChild(leftChild);
        joinNode.addLastChild(leftChild);
        List leftExpressions = (List)joinNode.getProperty(NodeConstants.Info.LEFT_EXPRESSIONS);
        List rightExpressions = (List)joinNode.getProperty(NodeConstants.Info.RIGHT_EXPRESSIONS);
        joinNode.setProperty(NodeConstants.Info.LEFT_EXPRESSIONS, rightExpressions);
        joinNode.setProperty(NodeConstants.Info.RIGHT_EXPRESSIONS, leftExpressions);
        JoinType jt = (JoinType)joinNode.getProperty(NodeConstants.Info.JOIN_TYPE);
        joinNode.setProperty(NodeConstants.Info.JOIN_TYPE, jt.getReverseType());
    }
    
}
