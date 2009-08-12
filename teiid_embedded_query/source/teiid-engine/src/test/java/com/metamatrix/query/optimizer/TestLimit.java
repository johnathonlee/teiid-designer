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

package com.metamatrix.query.optimizer;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.metamatrix.common.types.DataTypeManager;
import com.metamatrix.query.mapping.relational.QueryNode;
import com.metamatrix.query.optimizer.TestOptimizer.DependentProjectNode;
import com.metamatrix.query.optimizer.TestOptimizer.DependentSelectNode;
import com.metamatrix.query.optimizer.TestOptimizer.DupRemoveNode;
import com.metamatrix.query.optimizer.TestOptimizer.DupRemoveSortNode;
import com.metamatrix.query.optimizer.capabilities.BasicSourceCapabilities;
import com.metamatrix.query.optimizer.capabilities.FakeCapabilitiesFinder;
import com.metamatrix.query.optimizer.capabilities.SourceCapabilities.Capability;
import com.metamatrix.query.processor.FakeDataManager;
import com.metamatrix.query.processor.ProcessorPlan;
import com.metamatrix.query.processor.TestProcessor;
import com.metamatrix.query.processor.relational.AccessNode;
import com.metamatrix.query.processor.relational.DependentAccessNode;
import com.metamatrix.query.processor.relational.GroupingNode;
import com.metamatrix.query.processor.relational.LimitNode;
import com.metamatrix.query.processor.relational.MergeJoinStrategy;
import com.metamatrix.query.processor.relational.NestedLoopJoinStrategy;
import com.metamatrix.query.processor.relational.NullNode;
import com.metamatrix.query.processor.relational.PlanExecutionNode;
import com.metamatrix.query.processor.relational.ProjectNode;
import com.metamatrix.query.processor.relational.SelectNode;
import com.metamatrix.query.processor.relational.SortNode;
import com.metamatrix.query.processor.relational.UnionAllNode;
import com.metamatrix.query.unittest.FakeMetadataFacade;
import com.metamatrix.query.unittest.FakeMetadataFactory;
import com.metamatrix.query.unittest.FakeMetadataObject;
import com.metamatrix.query.unittest.FakeMetadataStore;


/** 
 * @since 4.3
 */
public class TestLimit extends TestCase {

    private static final int[] FULL_PUSHDOWN = new int[] {
                1,      // Access
                0,      // DependentAccess
                0,      // DependentSelect
                0,      // DependentProject
                0,      // DupRemove
                0,      // Grouping
                0,      // Limit
                0,      // NestedLoopJoinStrategy
                0,      // MergeJoinStrategy
                0,      // Null
                0,      // PlanExecution
                0,      // Project
                0,      // Select
                0,      // Sort
                0       // UnionAll
            };
    
    public static final Class<?>[] NODE_TYPES = new Class[] {
        AccessNode.class,
        DependentAccessNode.class,
        DependentSelectNode.class,
        DependentProjectNode.class,
        DupRemoveNode.class,
        GroupingNode.class,
        LimitNode.class,
        NestedLoopJoinStrategy.class,
        MergeJoinStrategy.class,
        NullNode.class,
        PlanExecutionNode.class,
        ProjectNode.class,
        SelectNode.class,
        SortNode.class,
        UnionAllNode.class
    };
    
    public TestLimit(String name) {
        super(name);
    }

    private static FakeMetadataFacade exampleMetadata() {
        // Create models
        FakeMetadataObject pm1 = FakeMetadataFactory.createPhysicalModel("pm1"); //$NON-NLS-1$
        FakeMetadataObject vm1 = FakeMetadataFactory.createVirtualModel("vm1");  //$NON-NLS-1$

        // Create physical groups
        FakeMetadataObject pm1g1 = FakeMetadataFactory.createPhysicalGroup("pm1.g1", pm1); //$NON-NLS-1$
        FakeMetadataObject pm1g2 = FakeMetadataFactory.createPhysicalGroup("pm1.g2", pm1); //$NON-NLS-1$
        FakeMetadataObject pm1g3 = FakeMetadataFactory.createPhysicalGroup("pm1.g3", pm1); //$NON-NLS-1$
        FakeMetadataObject pm1g4 = FakeMetadataFactory.createPhysicalGroup("pm1.g4", pm1); //$NON-NLS-1$
        FakeMetadataObject pm1g5 = FakeMetadataFactory.createPhysicalGroup("pm1.g5", pm1); //$NON-NLS-1$
        FakeMetadataObject pm1g6 = FakeMetadataFactory.createPhysicalGroup("pm1.g6", pm1); //$NON-NLS-1$
                
        // Create physical elements
        List pm1g1e = FakeMetadataFactory.createElements(pm1g1, 
            new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
        List pm1g2e = FakeMetadataFactory.createElements(pm1g2, 
            new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
        List pm1g3e = FakeMetadataFactory.createElements(pm1g3, 
            new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
        List pm1g4e = FakeMetadataFactory.createElements(pm1g4,
            new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
        ((FakeMetadataObject)pm1g4e.get(1)).putProperty(FakeMetadataObject.Props.SELECT, Boolean.FALSE);
        ((FakeMetadataObject)pm1g4e.get(3)).putProperty(FakeMetadataObject.Props.SELECT, Boolean.FALSE);
        List pm1g5e = FakeMetadataFactory.createElements(pm1g5,
            new String[] { "e1" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.STRING });
        ((FakeMetadataObject)pm1g5e.get(0)).putProperty(FakeMetadataObject.Props.SELECT, Boolean.FALSE);
        List pm1g6e = FakeMetadataFactory.createElements(pm1g6,
            new String[] { "in", "in3" }, //$NON-NLS-1$ //$NON-NLS-2$ 
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
         
        // Create virtual groups
        QueryNode vm1g1n1 = new QueryNode("vm1.g1", "SELECT * FROM pm1.g1 LIMIT 100"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1g1 = FakeMetadataFactory.createVirtualGroup("vm1.g1", vm1, vm1g1n1); //$NON-NLS-1$

        // Create virtual elements
        List vm1g1e = FakeMetadataFactory.createElements(vm1g1, 
            new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });

        QueryNode vm1g2n1 = new QueryNode("vm1.g2", "SELECT * FROM vm1.g1 ORDER BY e1"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1g2 = FakeMetadataFactory.createVirtualGroup("vm1.g2", vm1, vm1g2n1); //$NON-NLS-1$

        // Create virtual elements
        List vm1g2e = FakeMetadataFactory.createElements(vm1g2, 
            new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });

        // Add all objects to the store
        FakeMetadataStore store = new FakeMetadataStore();
        store.addObject(pm1);
        store.addObject(pm1g1);     
        store.addObjects(pm1g1e);
        store.addObject(pm1g2);     
        store.addObjects(pm1g2e);
        store.addObject(pm1g3); 
        store.addObjects(pm1g3e);
        store.addObject(pm1g4);
        store.addObjects(pm1g4e);
        store.addObject(pm1g5);
        store.addObjects(pm1g5e);
        store.addObject(pm1g6);
        store.addObjects(pm1g6e);
        
        store.addObject(vm1);
        store.addObject(vm1g1);
        store.addObjects(vm1g1e);
        store.addObject(vm1g2);
        store.addObjects(vm1g2e);

        // Create the facade from the store
        return new FakeMetadataFacade(store);
    }
    public void testLimit() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM pm1.g1 limit 100";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            1,      // Limit
            0,      // NestedLoopJoinStrategy
            
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }, NODE_TYPES);
    }
    
    public void testLimitPushdown() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true); 
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM pm1.g1 limit 100";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 LIMIT 100" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, FULL_PUSHDOWN, NODE_TYPES);
    }

    public void testLimitWithOffset() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM pm1.g1 limit 50, 100";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            1,      // Limit
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }, NODE_TYPES);
    }
    
    public void testPushedLimitWithOffset() throws Exception {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true); 
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM pm1.g1 limit 50, 100";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm1.g1.e1 AS c_0, pm1.g1.e2 AS c_1, pm1.g1.e3 AS c_2, pm1.g1.e4 AS c_3 FROM pm1.g1 LIMIT (100 + 50)" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, TestOptimizer.ComparisonMode.EXACT_COMMAND_STRING);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            1,      // Limit
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }, NODE_TYPES);
    }
    
    public void testLimitWithOffsetFullyPushed() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true); 
        caps.setCapabilitySupport(Capability.ROW_OFFSET, true); 
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM pm1.g1 limit 50, 100";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 LIMIT 50, 100" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, FULL_PUSHDOWN, NODE_TYPES);
    }
    
    public void testSort() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM pm1.g1 order by e1 limit 100";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            1,      // Limit
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            1,      // Sort
            0       // UnionAll
        }, NODE_TYPES);
    }

    public void testSortPushed() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        // pm3 model supports order by
        capFinder.addCapabilities("pm3", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM pm3.g1 order by e1 limit 100";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1 ORDER BY pm3.g1.e1" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            1,      // Limit
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }, NODE_TYPES);
    }

    public void testSortPushedWithLimit() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true);
        // pm3 model supports order by
        capFinder.addCapabilities("pm3", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM pm3.g1 order by e1 limit 100";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1 ORDER BY pm3.g1.e1 LIMIT 100" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, FULL_PUSHDOWN, NODE_TYPES);
    }

    public void testSortUnderLimitNotRemoved() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        // pm3 model supports order by
        capFinder.addCapabilities("pm3", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM (SELECT * FROM pm3.g1 order by e1 limit 100) AS V1 ORDER BY v1.e2";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            1,      // Limit
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            2,      // Sort
            0       // UnionAll
        }, NODE_TYPES);
    }
    
    //TODO: there is a redundent project node here
    public void testSortAboveLimitNotPushed() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM vm1.g2 order by e1";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 LIMIT 100" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, exampleMetadata(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // Limit
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            1,      // Sort
            0       // UnionAll
        }, NODE_TYPES);
    }
    
    public void testLimitNotPushedWithUnion() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true);
        // pm1 model supports order by
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM pm1.g1 UNION SELECT * FROM PM1.g2 LIMIT 100";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 UNION SELECT pm1.g2.e1, pm1.g2.e2, pm1.g2.e3, pm1.g2.e4 FROM PM1.g2" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            1,      // Limit
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }, NODE_TYPES);
    }
    
    public void testLimitPushedWithUnion() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true);
        // pm1 model supports order by
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM pm1.g1 UNION SELECT * FROM PM1.g2 LIMIT 100";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm1.g2.e1, pm1.g2.e2, pm1.g2.e3, pm1.g2.e4 FROM PM1.g2 LIMIT 100", "SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 LIMIT 100" //$NON-NLS-1$ //$NON-NLS-2$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            1,      // DupRemove
            0,      // Grouping
            1,      // Limit
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            1       // UnionAll
        }, NODE_TYPES);
    }
    
    public void testLimitWithOffsetPushedWithUnion() throws Exception {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true);
        caps.setCapabilitySupport(Capability.ROW_OFFSET, true);
        // pm1 model supports order by
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM pm1.g1 UNION SELECT * FROM PM1.g2 LIMIT 50, 100";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT PM1.g2.e1 AS c_0, PM1.g2.e2 AS c_1, PM1.g2.e3 AS c_2, PM1.g2.e4 AS c_3 FROM PM1.g2 LIMIT (100 + 50)", "SELECT pm1.g1.e1 AS c_0, pm1.g1.e2 AS c_1, pm1.g1.e3 AS c_2, pm1.g1.e4 AS c_3 FROM pm1.g1 LIMIT (100 + 50)" //$NON-NLS-1$ //$NON-NLS-2$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, TestOptimizer.ComparisonMode.EXACT_COMMAND_STRING);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            1,      // DupRemove
            0,      // Grouping
            1,      // Limit
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            1       // UnionAll
        }, NODE_TYPES);
    }
    
    public void testLimitNotPushedWithUnionOrderBy() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true);
        // pm1 model supports order by
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM pm1.g1 UNION SELECT * FROM PM1.g2 ORDER BY e1 LIMIT 100";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm1.g2.e1, pm1.g2.e2, pm1.g2.e3, pm1.g2.e4 FROM PM1.g2", "SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1" //$NON-NLS-1$ //$NON-NLS-2$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            1,      // Limit
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            1       // UnionAll
        }, NODE_TYPES);
        TestOptimizer.checkNodeTypes(plan, new int[] {1}, new Class[]{DupRemoveSortNode.class});
    }
    
    public void testCombinedLimits() throws Exception {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true);
        // pm1 model supports order by
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT * from (SELECT pm1.g1.e1 FROM pm1.g1 LIMIT 10, 100) x LIMIT 20, 75";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm1.g1.e1 AS c_0 FROM pm1.g1 LIMIT CASE WHEN (75 + (20 + 10)) < (100 + 10) THEN (75 + (20 + 10)) ELSE (100 + 10) END" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, TestOptimizer.ComparisonMode.EXACT_COMMAND_STRING);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            1,      // Limit
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }, NODE_TYPES);
    }

    public void testCombinedLimitsWithOffset() throws Exception {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true);
        caps.setCapabilitySupport(Capability.ROW_OFFSET, true);
        // pm1 model supports order by
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT * from (SELECT pm1.g1.e1 FROM pm1.g1 LIMIT 10, 100) x LIMIT 20, 75";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm1.g1.e1 AS c_0 FROM pm1.g1 LIMIT (20 + 10), CASE WHEN 75 < 100 THEN 75 ELSE 100 END" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, TestOptimizer.ComparisonMode.EXACT_COMMAND_STRING);  

        TestOptimizer.checkNodeTypes(plan, FULL_PUSHDOWN, NODE_TYPES);
    }

    public void testInlineView() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        //caps.setCapabilitySupport(SourceCapabilities.QUERY_ORDERBY, true);
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_INLINE_VIEWS, true);
        // pm3 model supports order by
        capFinder.addCapabilities("pm3", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM (SELECT * FROM pm3.g1) as v1 limit 100";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1 LIMIT 100" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, FULL_PUSHDOWN, NODE_TYPES);
    }
    
    /**
     * This turns out to be an important test for LIMIT: there are several nodes
     * (e.g. grouping, inline views, aggregates, sorting, joins, etc) that should not be pushed
     * down (because they change row order or row count) if there is already a limit node in that plan branch,
     * which can only be placed above LIMIT with an inline view. This test acts as a gatekeeper for avoiding
     * several of those pushdowns.
     * 
     * @since 4.3
     */
    public void testInlineViewAboveLimitNotMerged() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_INLINE_VIEWS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        // pm3 model supports order by
        capFinder.addCapabilities("pm3", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM (SELECT * FROM pm3.g1 limit 100) as v1 order by e1";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT v_0.c_0, v_0.c_1, v_0.c_2, v_0.c_3 FROM (SELECT pm3.g1.e1 AS c_0, pm3.g1.e2 AS c_1, pm3.g1.e3 AS c_2, pm3.g1.e4 AS c_3 FROM pm3.g1 LIMIT 100) AS v_0 ORDER BY c_0" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);
    }
    
    /**
     * since there is no order by with the nested limit, the criteria can be pushed through 
     *
     */
    public void testCriteriaPushedUnderLimit() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        // pm3 model supports order by
        capFinder.addCapabilities("pm3", caps); //$NON-NLS-1$

        String sql = "SELECT * FROM (SELECT * FROM pm3.g1 limit 100) as v1 where v1.e1 = 1";//$NON-NLS-1$
        String[] expectedSql = new String[] {
            "SELECT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1 WHERE pm3.g1.e1 = '1' LIMIT 100" //$NON-NLS-1$
            };
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                                    null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, FULL_PUSHDOWN, NODE_TYPES);
    }
    
    public void testInlineViewJoin() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_INLINE_VIEWS, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "SELECT x FROM ((SELECT e1 as x FROM pm1.g1 LIMIT 700) c INNER JOIN (SELECT e1 FROM pm1.g2) d ON d.e1 = c.x) order by x LIMIT 5";//$NON-NLS-1$
        String[] expectedSql = new String[] {"SELECT e1 FROM pm1.g1 LIMIT 700", "SELECT e1 FROM pm1.g2"};//$NON-NLS-1$ //$NON-NLS-2$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                      null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
                                        2,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        1,      // Limit
                                        0,      // NestedLoopJoinStrategy
                                        1,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        1,      // Sort
                                        0       // UnionAll
        }, NODE_TYPES);
        
        //test to ensure that the unnecessary inline view removal is done properly
        FakeDataManager fdm = new FakeDataManager();
        TestProcessor.sampleData1(fdm);
        TestProcessor.helpProcess(plan, fdm, new List[] {
        		Arrays.asList("a"), //$NON-NLS-1$
        		Arrays.asList("a"), //$NON-NLS-1$
        		Arrays.asList("a"), //$NON-NLS-1$
        		Arrays.asList("a"), //$NON-NLS-1$
        		Arrays.asList("a"), //$NON-NLS-1$
        });
    }
    
    public void testDontPushSelectWithOrderedLimit() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "select * from (SELECT e1 as x FROM pm1.g1 order by x LIMIT 700) y where x = 1";//$NON-NLS-1$
        String[] expectedSql = new String[] {"SELECT g_0.e1 AS c_0 FROM pm1.g1 AS g_0 ORDER BY c_0"};//$NON-NLS-1$ 
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                      null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        1,      // Limit
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        1,      // Select
                                        0,      // Sort
                                        0       // UnionAll
        }, NODE_TYPES);
    }
    
    public void testDontPushSelectWithOrderedLimit1() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "select * from (SELECT e1 as x FROM pm1.g1 order by x LIMIT 10, 700) y where x = 1";//$NON-NLS-1$
        String[] expectedSql = new String[] {"SELECT g_0.e1 AS c_0 FROM pm1.g1 AS g_0 ORDER BY c_0"};//$NON-NLS-1$ 
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                      null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        1,      // Limit
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        1,      // Select
                                        0,      // Sort
                                        0       // UnionAll
        }, NODE_TYPES);
    }
    
    public void testLimitWithNoAccessNode() {
        String sql = "select 1 limit 1";//$NON-NLS-1$
        String[] expectedSql = new String[] {};
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), expectedSql);  

        TestOptimizer.checkNodeTypes(plan, new int[] {
                                        0,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        1,      // Limit
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
        }, NODE_TYPES);
    }
    
    /**
     * Note here that the criteria made it to the having clause 
     */
    public void testAggregateCriteriaOverUnSortedLimit() {
        String sql = "select a from (SELECT MAX(e2) as a FROM pm1.g1 GROUP BY e2 LIMIT 1) x where a = 0"; //$NON-NLS-1$
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.ROW_LIMIT, true);
        caps.setCapabilitySupport(Capability.QUERY_GROUP_BY, true);
        caps.setCapabilitySupport(Capability.QUERY_HAVING, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
        
        String[] expectedSql = new String[] {"SELECT MAX(e2) FROM pm1.g1 GROUP BY e2 HAVING MAX(e2) = 0 LIMIT 1"};//$NON-NLS-1$ 
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), null, capFinder, expectedSql, true);  

        TestOptimizer.checkNodeTypes(plan, FULL_PUSHDOWN, NODE_TYPES);
    }
    
    public void testSortWithLimitInlineView() {
        String sql = "select e1 from (select pm1.g1.e1, pm1.g1.e2 from pm1.g1 order by pm1.g1.e1, pm1.g1.e2 limit 1) x"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), new String[] {"SELECT g_0.e1 AS c_0, g_0.e2 AS c_1 FROM pm1.g1 AS g_0 ORDER BY c_0, c_1"}); //$NON-NLS-1$
        
        TestOptimizer.checkNodeTypes(plan, new int[] {
                1,      // Access
                0,      // DependentAccess
                0,      // DependentSelect
                0,      // DependentProject
                0,      // DupRemove
                0,      // Grouping
                1,      // Limit
                0,      // NestedLoopJoinStrategy
                0,      // MergeJoinStrategy
                0,      // Null
                0,      // PlanExecution
                1,      // Project
                0,      // Select
                0,      // Sort
                0       // UnionAll
        }, NODE_TYPES);
    }

}
