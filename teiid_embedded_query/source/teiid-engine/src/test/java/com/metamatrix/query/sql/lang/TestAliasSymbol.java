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

package com.metamatrix.query.sql.lang;

import junit.framework.TestCase;

import com.metamatrix.query.sql.symbol.AliasSymbol;
import com.metamatrix.query.sql.symbol.Constant;
import com.metamatrix.query.sql.symbol.ExpressionSymbol;

public class TestAliasSymbol extends TestCase {

    public void testAliasEquals() {
        AliasSymbol a1 = new AliasSymbol("X", new ExpressionSymbol("x", new Constant(1))); //$NON-NLS-1$ //$NON-NLS-2$
        AliasSymbol a2 = new AliasSymbol("X", new ExpressionSymbol("x", new Constant(2))); //$NON-NLS-1$ //$NON-NLS-2$
        AliasSymbol a3 = new AliasSymbol("x", new ExpressionSymbol("x", new Constant(1))); //$NON-NLS-1$ //$NON-NLS-2$
        
        assertEquals(a1, a3); //just a different case for the alias
        
        assertFalse(a1.equals(a2)); //different express 
    }
    
    public void testClone() {
        AliasSymbol a1 = new AliasSymbol("X", new ExpressionSymbol("x", new Constant(1))); //$NON-NLS-1$ //$NON-NLS-2$
        a1.setOutputName("foo"); //$NON-NLS-1$
        AliasSymbol clone = (AliasSymbol)a1.clone();
        assertEquals(a1, clone);
        assertEquals(a1.getOutputName(), clone.getOutputName());
    }
    
}
