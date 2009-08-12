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

import org.teiid.dqp.internal.datamgr.language.DeleteImpl;

import com.metamatrix.query.sql.lang.CompoundCriteria;
import com.metamatrix.query.sql.lang.Delete;

import junit.framework.TestCase;

public class TestDeleteImpl extends TestCase {

    /**
     * Constructor for TestDeleteImpl.
     * @param name
     */
    public TestDeleteImpl(String name) {
        super(name);
    }

    public static Delete helpExample() {
        return new Delete(TestGroupImpl.helpExample("vm1.g1"), //$NON-NLS-1$
                          TestCompoundCriteriaImpl.helpExample(CompoundCriteria.AND));
    }
    
    public static DeleteImpl example() throws Exception {
        return (DeleteImpl)TstLanguageBridgeFactory.factory.translate(helpExample());
    }

    public void testGetGroup() throws Exception {
        assertNotNull(example().getGroup());
    }

    public void testGetCriteria() throws Exception {
        assertNotNull(example().getCriteria());
    }

}
