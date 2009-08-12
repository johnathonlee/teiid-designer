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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.teiid.connector.language.IOrderByItem;
import org.teiid.dqp.internal.datamgr.language.OrderByImpl;

import com.metamatrix.query.sql.lang.OrderBy;

import junit.framework.TestCase;

public class TestOrderByImpl extends TestCase {

    /**
     * Constructor for TestOrderByImpl.
     * @param name
     */
    public TestOrderByImpl(String name) {
        super(name);
    }

    public static OrderBy helpExample() {
        ArrayList elements = new ArrayList();
        elements.add(TestElementImpl.helpExample("vm1.g1", "e1")); //$NON-NLS-1$ //$NON-NLS-2$
        elements.add(TestElementImpl.helpExample("vm1.g1", "e2")); //$NON-NLS-1$ //$NON-NLS-2$
        elements.add(TestElementImpl.helpExample("vm1.g1", "e3")); //$NON-NLS-1$ //$NON-NLS-2$
        elements.add(TestElementImpl.helpExample("vm1.g1", "e4")); //$NON-NLS-1$ //$NON-NLS-2$
        
        ArrayList types = new ArrayList();
        types.add(Boolean.TRUE);
        types.add(Boolean.FALSE);
        types.add(Boolean.TRUE);
        types.add(Boolean.FALSE);
        return new OrderBy(elements, types);
    }
    
    public static OrderByImpl example() throws Exception {
        return (OrderByImpl)TstLanguageBridgeFactory.factory.translate(helpExample());
    }

    public void testGetItems() throws Exception {
        List items = example().getItems();
        assertNotNull(items);
        assertEquals(4, items.size());
        for (Iterator i = items.iterator(); i.hasNext();) {
            assertTrue(i.next() instanceof IOrderByItem);
        }
        
    }

}
