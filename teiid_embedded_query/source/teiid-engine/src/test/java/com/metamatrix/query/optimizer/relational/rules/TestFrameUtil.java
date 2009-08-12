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

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.metamatrix.query.optimizer.relational.plantree.NodeConstants;
import com.metamatrix.query.optimizer.relational.plantree.NodeEditor;
import com.metamatrix.query.optimizer.relational.plantree.NodeFactory;
import com.metamatrix.query.optimizer.relational.plantree.PlanNode;
import com.metamatrix.query.optimizer.relational.plantree.NodeConstants.Info;
import com.metamatrix.query.sql.lang.IsNullCriteria;
import com.metamatrix.query.sql.lang.JoinType;
import com.metamatrix.query.sql.symbol.Constant;
import com.metamatrix.query.sql.symbol.ElementSymbol;
import com.metamatrix.query.sql.symbol.GroupSymbol;
import com.metamatrix.query.sql.util.SymbolMap;

public class TestFrameUtil {
    
    static GroupSymbol getGroup(int id) {
        return new GroupSymbol(String.valueOf(id));
    }
    
    @Test public void testFindJoinSourceNode() {
        PlanNode root = getExamplePlan();
        
        PlanNode joinSource = FrameUtil.findJoinSourceNode(root);
        
        assertSame(root, joinSource);
    }
    
    @Test public void testFindJoinSourceNode1() {
        PlanNode root = getExamplePlan();
        
        PlanNode joinSource = FrameUtil.findJoinSourceNode(root.getLastChild());
        
        assertEquals(NodeConstants.Types.JOIN, joinSource.getType());
    }
    
    @Test public void testFindSourceNode() {
        PlanNode root = getExamplePlan();
                
        Set<GroupSymbol> groups = new HashSet<GroupSymbol>();
        
        groups.add(getGroup(1));
        
        PlanNode originatingNode = FrameUtil.findOriginatingNode(root, groups);
        
        assertEquals(NodeConstants.Types.NULL, originatingNode.getType());
    }

    /**
     * Access nodes are not eligible originating nodes
     */
    @Test public void testFindSourceNodeWithAccessSource() {
        PlanNode root = getExamplePlan();
                
        Set<GroupSymbol> groups = new HashSet<GroupSymbol>();
        
        groups.add(getGroup(2));
        
        PlanNode originatingNode = FrameUtil.findOriginatingNode(root, groups);
        
        assertEquals(NodeConstants.Types.JOIN, originatingNode.getType());
    }

    @Test public void testFindSourceNode2() {
        PlanNode root = getExamplePlan();
                
        Set<GroupSymbol> groups = new HashSet<GroupSymbol>();
        
        groups.add(getGroup(3));
        
        PlanNode originatingNode = FrameUtil.findOriginatingNode(root, groups);
        
        assertEquals(NodeConstants.Types.SOURCE, originatingNode.getType());
    }
    
    @Test public void testNonExistentSource() {
        PlanNode root = getExamplePlan();
        
        Set<GroupSymbol> groups = new HashSet<GroupSymbol>();
        
        groups.add(getGroup(4));
        
        PlanNode originatingNode = FrameUtil.findOriginatingNode(root, groups);
        
        assertNull(originatingNode);
    }
    
    @Test public void testJoinGroups() throws Exception {
    	PlanNode joinNode = getExamplePlan();
    	PlanNode projectNode = NodeFactory.getNewNode(NodeConstants.Types.PROJECT);
    	ElementSymbol e1 = new ElementSymbol("e1"); //$NON-NLS-1$
    	e1.setGroupSymbol(getGroup(3));
    	projectNode.setProperty(Info.PROJECT_COLS, Arrays.asList(e1));
    	projectNode.addFirstChild(joinNode);
    	projectNode.addGroup(getGroup(3));
    	PlanNode sourceNode = NodeFactory.getNewNode(NodeConstants.Types.SOURCE);
    	sourceNode.addFirstChild(projectNode);
    	GroupSymbol four = getGroup(4);
    	sourceNode.addGroup(four);
    	ElementSymbol e2 = new ElementSymbol("e2"); //$NON-NLS-1$
    	e2.setGroupSymbol(four);
    	SymbolMap sm = SymbolMap.createSymbolMap(Arrays.asList(e2), Arrays.asList(e1));
    	sourceNode.setProperty(Info.SYMBOL_MAP, sm);
    	PlanNode projectNode1 = NodeFactory.getNewNode(NodeConstants.Types.PROJECT);
    	projectNode1.addFirstChild(sourceNode);
    	projectNode1.addGroup(four);
    	projectNode1.setProperty(Info.PROJECT_COLS, Arrays.asList(e2));
    	
    	//removing source node 3 completely
    	SymbolMap replacement = SymbolMap.createSymbolMap(Arrays.asList(e1), Arrays.asList(new Constant(null)));
    	FrameUtil.convertFrame(NodeEditor.findNodePreOrder(joinNode, NodeConstants.Types.SOURCE), getGroup(3), null, replacement.asMap(), null);
    	assertEquals(2, joinNode.getGroups().size()); //even though this is a cross join it should still retain its groups
    	assertEquals(0, NodeEditor.findNodePreOrder(joinNode, NodeConstants.Types.SELECT).getGroups().size());
    	assertEquals(1, projectNode1.getGroups().size());
    	assertEquals(0, projectNode.getGroups().size());
    }
    
    @Test public void testJoinGroups1() throws Exception {
    	PlanNode joinNode = getExamplePlan();
    	PlanNode projectNode = NodeFactory.getNewNode(NodeConstants.Types.PROJECT);
    	ElementSymbol e1 = new ElementSymbol("e1"); //$NON-NLS-1$
    	e1.setGroupSymbol(getGroup(3));
    	projectNode.setProperty(Info.PROJECT_COLS, Arrays.asList(e1));
    	projectNode.addFirstChild(joinNode);
    	projectNode.addGroup(getGroup(3));
    	PlanNode sourceNode = NodeFactory.getNewNode(NodeConstants.Types.SOURCE);
    	sourceNode.addFirstChild(projectNode);
    	GroupSymbol four = getGroup(4);
    	sourceNode.addGroup(four);
    	ElementSymbol e2 = new ElementSymbol("e2"); //$NON-NLS-1$
    	e2.setGroupSymbol(four);
    	SymbolMap sm = SymbolMap.createSymbolMap(Arrays.asList(e2), Arrays.asList(e1));
    	sourceNode.setProperty(Info.SYMBOL_MAP, sm);
    	PlanNode projectNode1 = NodeFactory.getNewNode(NodeConstants.Types.PROJECT);
    	projectNode1.addFirstChild(sourceNode);
    	projectNode1.addGroup(four);
    	projectNode1.setProperty(Info.PROJECT_COLS, Arrays.asList(e2));
    	
    	//replace source 3 with groups 5, 6
    	SymbolMap replacement = SymbolMap.createSymbolMap(Arrays.asList(e1), Arrays.asList(new Constant(null)));
    	FrameUtil.convertFrame(NodeEditor.findNodePreOrder(joinNode, NodeConstants.Types.SOURCE), getGroup(3), new HashSet<GroupSymbol>(Arrays.asList(getGroup(5), getGroup(6))), replacement.asMap(), null);
    	assertEquals(4, joinNode.getGroups().size()); //even though this is a cross join it should still retain its groups
    	assertEquals(0, NodeEditor.findNodePreOrder(joinNode, NodeConstants.Types.SELECT).getGroups().size());
    	assertEquals(1, projectNode1.getGroups().size());
    	assertEquals(0, projectNode.getGroups().size());
    }

    /**
     * <pre>
     * Join(groups=[3, 2, 1])
     *   Null(groups=[1])
     *   Select(groups=[2])
     *     Join(groups=[3, 2])
     *       Source(groups=[3])
     *       Access(groups=[2])
     * </pre>
     */
    public static PlanNode getExamplePlan() {
        PlanNode joinNode = NodeFactory.getNewNode(NodeConstants.Types.JOIN);
        joinNode.setProperty(NodeConstants.Info.JOIN_TYPE, JoinType.JOIN_CROSS);
        joinNode.addGroup(getGroup(1)); 
        joinNode.addGroup(getGroup(2)); 
        joinNode.addGroup(getGroup(3)); 
        
        PlanNode nullNode = NodeFactory.getNewNode(NodeConstants.Types.NULL);
        
        nullNode.addGroup(getGroup(1)); 
        joinNode.addFirstChild(nullNode);
        
        PlanNode childCriteria = NodeFactory.getNewNode(NodeConstants.Types.SELECT);
        childCriteria.setProperty(Info.SELECT_CRITERIA, new IsNullCriteria(new Constant(1)));
        childCriteria.addGroup(getGroup(2));
        joinNode.addLastChild(childCriteria);
        
        PlanNode childJoinNode = NodeFactory.getNewNode(NodeConstants.Types.JOIN);
        childJoinNode.setProperty(NodeConstants.Info.JOIN_TYPE, JoinType.JOIN_CROSS);
        childJoinNode.addGroup(getGroup(2)); 
        childJoinNode.addGroup(getGroup(3)); 
        childCriteria.addFirstChild(childJoinNode);
        
        PlanNode accessNode = NodeFactory.getNewNode(NodeConstants.Types.ACCESS);
        
        accessNode.addGroup(getGroup(2)); 
        childJoinNode.addFirstChild(accessNode);
        
        PlanNode sourceNode = NodeFactory.getNewNode(NodeConstants.Types.SOURCE);
        
        sourceNode.addGroup(getGroup(3)); 
        childJoinNode.addFirstChild(sourceNode);
        
        return joinNode;
    }
}
