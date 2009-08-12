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

import junit.framework.TestCase;

import com.metamatrix.query.optimizer.capabilities.BasicSourceCapabilities;
import com.metamatrix.query.optimizer.capabilities.FakeCapabilitiesFinder;
import com.metamatrix.query.optimizer.capabilities.SourceCapabilities.Capability;
import com.metamatrix.query.processor.ProcessorPlan;
import com.metamatrix.query.processor.relational.RelationalPlan;
import com.metamatrix.query.sql.symbol.ElementSymbol;
import com.metamatrix.query.unittest.FakeMetadataFacade;
import com.metamatrix.query.unittest.FakeMetadataFactory;


public class TestRuleRaiseNull extends TestCase {
    
    public static final int[] FULLY_NULL = new int[] {
                0,      // Access
                0,      // DependentAccess
                0,      // DependentSelect
                0,      // DependentProject
                0,      // DupRemove
                0,      // Grouping
                0,      // NestedLoopJoinStrategy
                0,      // MergeJoinStrategy
                1,      // Null
                0,      // PlanExecution
                0,      // Project
                0,      // Select
                0,      // Sort
                0       // UnionAll
            };

    /**
     * Test that criteria will cause a branch of a union to be excised if the criteria renders it
     * impossible that the branch of the union would return results.  In the following test, 
     * each branch of the union projects a "null" in a different column.  So, an equality criteria on
     * one of those columns should render one of the branches of the union unnecessary (since null
     * never equals anything).  Expected behavior is that a NullNode is inserted in place of the
     * unnecessary access node. 
     */
    public void testUnionCriteriaOptimization() {

        String sql = "select * from ( select intkey as cola, null as colb, intnum as colc from bqt1.smalla union all select null, intkey, intnum from bqt2.smalla) as X where X.cola = 1";  //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQT(),
                                      new String[] {"SELECT intkey, intnum FROM bqt1.smalla WHERE intkey = 1"} ); //$NON-NLS-1$
        
        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });          
        
    }
    
    public void testRaiseNullWithInnerJoin() {
        String sql = "select b.intkey from (select intkey from bqt1.smalla where 1 = 0) a inner join (select intkey from bqt1.smallb) b on (a.intkey = b.intkey)"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(),  
                                                    new String[]{});
        TestOptimizer.checkNodeTypes(plan, FULLY_NULL);
    }
    
    public void testRaiseNullWithFullOuterJoin() {
        String sql = "select b.intkey from (select intkey from bqt1.smalla) a full outer join (select intkey from bqt1.smallb where 1 = 0) b on (a.intkey = b.intkey)"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(),  
                                                    new String[]{"SELECT BQT1.SmallA.IntKey FROM bqt1.smalla"}); //$NON-NLS-1$
        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });
    }
    
    public void testRaiseNullWithOuterJoin() {
        String sql = "select b.intkey from (select intkey from bqt1.smalla) a left outer join (select intkey from bqt1.smallb where 1 = 0) b on (a.intkey = b.intkey)"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(),  
                                                    new String[]{"SELECT BQT1.SmallA.IntKey FROM bqt1.smalla"}); //$NON-NLS-1$
        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });
    }
    
    public void testRaiseNullWithOuterJoin1() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SELECT_EXPRESSION, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        String sql = "select smallb.intkey, smalla.intkey from bqt1.smalla left outer join bqt1.smallb on (1 = 2)"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(), null, capFinder,  
                                                    new String[]{"SELECT null, bqt1.smalla.intkey FROM bqt1.smalla"}, true); //$NON-NLS-1$
        TestOptimizer.checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);
    }
    
    public void testRaiseNullWithUnion() {
        String sql = "select b.x from (select intkey as x from bqt1.smalla where 1 = 0 union all select intnum as y from bqt1.smalla) b"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(),  
                                                    new String[]{"SELECT IntNum FROM bqt1.smalla"}); //$NON-NLS-1$
        TestOptimizer.checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);
        
        assertEquals(Arrays.asList(new Object[] {new ElementSymbol("b.x")}), plan.getOutputElements()); //$NON-NLS-1$
    }    

    public void testRaiseNullWithUnion1() {
        String sql = "select b.intkey from (select intkey from bqt1.smalla union all select intnum from bqt1.smalla where 1 = 0) b"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(),  
                                                    new String[]{"SELECT intkey FROM bqt1.smalla"}); //$NON-NLS-1$
        TestOptimizer.checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);
    }    
    
    public void testRaiseNullWithUnion2() {
        String sql = "select b.intkey, b.x from (select intkey, intnum as x from bqt1.smalla where 1 = 0 union all select intnum as a, null from bqt1.smalla union all select 1 as z, intkey as b from bqt1.smallb) b"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(),  
                                                    new String[]{"SELECT intkey FROM bqt1.smallb", "SELECT IntNum FROM bqt1.smalla"}); //$NON-NLS-1$ //$NON-NLS-2$
        TestOptimizer.checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            2,      // Project
            0,      // Select
            0,      // Sort
            1       // UnionAll
        });
    }    
    
    public void testRaiseNullWithUnion3() {
        String sql = "select intkey, intnum as x from bqt1.smalla where 1 = 0 union all select intnum, intkey as z from bqt1.smalla where 1 = 0"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(),  
                                                    new String[]{});
        TestOptimizer.checkNodeTypes(plan, FULLY_NULL);
    } 

    public void testRaiseNullWithUnion4() throws Exception {
        String sql = "select b.intkey, b.x from (select intkey, intnum as x from bqt1.smalla where 1 = 0 union all select 1 as z, intkey as b from bqt1.smallb) b inner join bqt1.smalla on b.intkey = bqt1.smalla.intkey"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(),  
                                                    new String[]{"SELECT g_0.intkey FROM bqt1.smallb AS g_0, bqt1.smalla AS g_1 WHERE g_1.IntKey = 1"}, TestOptimizer.ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$
        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });
    }
    
    public void testRaiseNullWithUnion5() {
        String sql = "select intkey from bqt1.smalla union all select intkey from bqt2.smalla where 1 = 0"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(),  
                                                    new String[]{"SELECT intkey FROM bqt1.smalla"}); //$NON-NLS-1$
        TestOptimizer.checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);
    }
    
    public void testRaiseNullWithUnion6() {
        String sql = "select intkey from bqt1.smalla union all select intkey from bqt2.smalla union all select intkey from bqt2.smalla where 1 = 0"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(),  
                                                    new String[]{"SELECT intkey FROM bqt1.smalla", "SELECT intkey FROM bqt2.smalla"}); //$NON-NLS-1$ //$NON-NLS-2$
        TestOptimizer.checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            1       // UnionAll
        });
    }
    
    public void testPushCriteriaThroughUnion9() {
        TestOptimizer.helpPlan("select * from vm1.u8 where const = 's1'", TestOptimizer.example1(), //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1" } );     //$NON-NLS-1$
    }

    public void testPushCriteriaThroughUnion10() {
        TestOptimizer.helpPlan("select * from vm1.u8 where const = 's3'", TestOptimizer.example1(), //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g3" } );     //$NON-NLS-1$
    }
    
    public void testRaiseNullWithOuterJoinAndHaving() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SELECT_EXPRESSION, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        String sql = "select smallb.intkey, smalla.intkey from bqt1.smalla left outer join bqt1.smallb on (1 = 2) group by smalla.intkey, smallb.intkey having max(smallb.intkey) = 1"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(), null, capFinder,  
                                                    new String[]{"SELECT bqt1.smalla.intkey FROM bqt1.smalla"}, true); //$NON-NLS-1$
        TestOptimizer.checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });
    }
    
    /**
     * Ensures proper handling of the removal of the first branch and
     * duplicate symbol names in the next branch
     */
    public void testRaiseNullWithUnion7() throws Exception {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_INLINE_VIEWS, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$
        
        capFinder.addCapabilities("BQT2", caps); //$NON-NLS-1$
        
        String sql = "select max(intkey), intnum from (select intkey, intnum from bqt2.smalla where 1 = 0 union all select intnum, intnum from bqt2.smalla union all select intkey, stringkey from bqt2.smalla) x group by intnum"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(), null, capFinder, 
                                                    new String[]{"SELECT MAX(v_0.c_1), v_0.c_0 FROM (SELECT g_1.IntNum AS c_0, g_1.IntNum AS c_1 FROM bqt2.smalla AS g_1 UNION ALL SELECT g_0.StringKey AS c_0, g_0.IntKey AS c_1 FROM bqt2.smalla AS g_0) AS v_0 GROUP BY v_0.c_0"}, TestOptimizer.ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$
        TestOptimizer.checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);
    }
    
    public void testRaiseNullWithUnionOrderBy() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        capFinder.addCapabilities("BQT2", caps); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        String sql = "select intkey from bqt1.smalla where 1 = 0 union all select intnum from bqt2.smalla order by intkey"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, metadata, null, capFinder, 
                                                    new String[]{"SELECT intnum AS intkey FROM bqt2.smalla ORDER BY intkey"}, TestOptimizer.SHOULD_SUCCEED); //$NON-NLS-1$

        TestOptimizer.checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);
    }
    
    public void testRaiseNullWithGroupBy() {
        String sql = "select max(e2), e1 from pm1.g1 where 1 = 0 group by e1"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), new String[]{});

        TestOptimizer.checkNodeTypes(plan, FULLY_NULL);
    }

    public void testRaiseNullWithGroupBy1() {
        String sql = "select max(e2) from pm1.g1 where 1 = 0"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), new String[]{});

        TestOptimizer.checkNodeTypes(plan, new int[] {
            0,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            1,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });
    }
    
    public void testRaiseNullWithExcept() {
        String sql = "select e1 from pm1.g1 except select e2 from pm1.g2 where 1 = 0"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), new String[]{"SELECT DISTINCT g_0.e1 FROM pm1.g1 AS g_0"}); //$NON-NLS-1$

        TestOptimizer.checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);
    }

    public void testRaiseNullWithIntersect() {
        String sql = "select max(e2) from pm1.g1 intersect select e2 from pm1.g2 where 1 = 0"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(), new String[]{});

        TestOptimizer.checkNodeTypes(plan, FULLY_NULL);
    }
    
    /**
     * This tests that a criteria with no elements is not pushed down,
     * but instead is cleaned up properly later
     * See defect 9865
     */
    public void testCrossJoinNoElementCriteriaOptimization() {
        ProcessorPlan plan = TestOptimizer.helpPlan("select Y.e1, Y.e2 FROM vm1.g1 X, vm1.g1 Y where {b'true'} = {b'false'}", TestOptimizer.example1(),  //$NON-NLS-1$
            new String[0]);
        TestOptimizer.checkNodeTypes(plan, FULLY_NULL); 
    }
    
    public void testSelectLiteralFalseCriteria() {
        ProcessorPlan plan = TestOptimizer.helpPlan("Select 'x' from pm1.g1 where 1=0", TestOptimizer.example1(),  //$NON-NLS-1$
            new String[] { });
        TestOptimizer.checkNodeTypes(plan, FULLY_NULL); 
    }
    
    public void testRaiseNullWithUnionNotAll() {
        String sql = "select intkey from bqt2.smalla union select intkey from bqt2.smalla where 1 = 0"; //$NON-NLS-1$
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql, FakeMetadataFactory.exampleBQTCached(),  
                                                    new String[]{"SELECT DISTINCT intkey FROM bqt2.smalla"}); //$NON-NLS-1$
        TestOptimizer.checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);
    }
    
    public void testRaiseNullWithUnionAndAliases() {
        String sql = "select pm1.g1.e1 from pm1.g1, (select e1 from pm1.g1 where (1 = 0) union all select e1 as x from pm1.g2) x where pm1.g1.e1 <> x.e1"; //$NON-NLS-1$
        
        RelationalPlan plan = (RelationalPlan)TestOptimizer.helpPlan(sql, FakeMetadataFactory.example1Cached(),  
                                                    new String[]{"SELECT g_0.e1 FROM pm1.g1 AS g_0, pm1.g2 AS g_1 WHERE g_0.e1 <> g_1.e1"}); //$NON-NLS-1$ //$NON-NLS-2$
        TestOptimizer.checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);
    }
    
}
