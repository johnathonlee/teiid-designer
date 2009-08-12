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

package org.teiid.dqp.internal.datamgr.language;

import org.teiid.dqp.internal.datamgr.language.NotCriteriaImpl;

import com.metamatrix.query.sql.lang.CompareCriteria;
import com.metamatrix.query.sql.lang.NotCriteria;

import junit.framework.TestCase;

public class TestNotCriteriaImpl extends TestCase {

    /**
     * Constructor for TestNotCriteriaImpl.
     * @param name
     */
    public TestNotCriteriaImpl(String name) {
        super(name);
    }

    public static NotCriteria helpExample() {
        return new NotCriteria(TestCompareCriteriaImpl.helpExample(CompareCriteria.GE, 100, 200));
    }
    
    public static NotCriteriaImpl example() throws Exception {
        return (NotCriteriaImpl)TstLanguageBridgeFactory.factory.translate(helpExample());
    }

    public void testGetCriteria() throws Exception {
        assertNotNull(example().getCriteria());
    }

}
