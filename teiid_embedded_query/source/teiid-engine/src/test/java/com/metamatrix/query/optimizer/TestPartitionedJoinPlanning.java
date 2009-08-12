/*
 * JBoss, Home of Professional Open Source.
 * Copyright (C) 2009 Red Hat, Inc.
 * Licensed to Red Hat, Inc. under one or more contributor 
 * license agreements.  See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import static com.metamatrix.query.optimizer.TestOptimizer.*;

import org.junit.Test;

import com.metamatrix.query.optimizer.capabilities.BasicSourceCapabilities;
import com.metamatrix.query.optimizer.capabilities.FakeCapabilitiesFinder;
import com.metamatrix.query.optimizer.capabilities.SourceCapabilities.Capability;
import com.metamatrix.query.processor.ProcessorPlan;
import com.metamatrix.query.processor.relational.PartitionedSortJoin;
import com.metamatrix.query.unittest.FakeMetadataFacade;
import com.metamatrix.query.unittest.FakeMetadataFactory;
import com.metamatrix.query.unittest.FakeMetadataObject;

public class TestPartitionedJoinPlanning {
	
    @Test public void testUsePartitionedMergeJoin(){
        // Create query
        String sql = "SELECT pm1.g1.e1 FROM pm1.g1, pm1.g2 WHERE pm1.g1.e1 = pm1.g2.e1";//$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
        FakeMetadataObject g1 = metadata.getStore().findObject("pm1.g1", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g1.putProperty(FakeMetadataObject.Props.CARDINALITY, 600);
        FakeMetadataObject g2 = metadata.getStore().findObject("pm1.g2", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g2.putProperty(FakeMetadataObject.Props.CARDINALITY, 3000);
    
        ProcessorPlan plan = helpPlan(sql, metadata,  
            null, capFinder,
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1 ORDER BY pm1.g1.e1", "SELECT pm1.g2.e1 FROM pm1.g2" }, SHOULD_SUCCEED); //$NON-NLS-1$ //$NON-NLS-2$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });  
        checkNodeTypes(plan, new int[] {1}, new Class[] {PartitionedSortJoin.class});
    }    


}
