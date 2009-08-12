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

import junit.framework.*;
import com.metamatrix.core.util.UnitTestUtil;
import com.metamatrix.query.sql.symbol.*;

public class TestMatchCriteria extends TestCase {

	// ################################## FRAMEWORK ################################
	
	public TestMatchCriteria(String name) { 
		super(name);
	}	
	
	// ################################## TEST HELPERS ################################	
		
	public static MatchCriteria example(String element, String str) {
		MatchCriteria crit = new MatchCriteria();
		crit.setLeftExpression(new ElementSymbol(element));
		crit.setRightExpression(new Constant(str));
		return crit;		    
	}		

	public static MatchCriteria example(String str) {
		MatchCriteria crit = new MatchCriteria();
		crit.setLeftExpression(new ElementSymbol("m.g1.e1")); //$NON-NLS-1$
		crit.setRightExpression(new Constant(str));
		return crit;		    
	}		

	public static MatchCriteria example(String str, char escapeChar) {
		MatchCriteria crit = new MatchCriteria();
		crit.setLeftExpression(new ElementSymbol("m.g1.e1")); //$NON-NLS-1$
		crit.setRightExpression(new Constant(str));
		crit.setEscapeChar(escapeChar);
		return crit;		    				    
	}		
		
	// ################################## ACTUAL TESTS ################################
	
	public void testEquals1() {    
		MatchCriteria c1 = example("abc"); //$NON-NLS-1$
		MatchCriteria c2 = example("abc"); //$NON-NLS-1$
		assertTrue("Equivalent match criteria don't compare as equal: " + c1 + ", " + c2, c1.equals(c2));				 //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void testEquals2() {    
		MatchCriteria c1 = example("abc", '#'); //$NON-NLS-1$
        c1.setNegated(true);
		MatchCriteria c2 = example("abc", '#'); //$NON-NLS-1$
        c2.setNegated(true);
		assertTrue("Equivalent match criteria don't compare as equal: " + c1 + ", " + c2, c1.equals(c2));				 //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void testEquals3() {    
		MatchCriteria c1 = example("abc", '#'); //$NON-NLS-1$
        c1.setNegated(true);
		MatchCriteria c2 = (MatchCriteria) c1.clone();
		assertTrue("Equivalent match criteria don't compare as equal: " + c1 + ", " + c2, c1.equals(c2));				 //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void testEquals4() {    
		MatchCriteria c1 = example("abc"); //$NON-NLS-1$
		MatchCriteria c2 = example("abc", '#'); //$NON-NLS-1$
		assertTrue("Different match criteria compare as equal: " + c1 + ", " + c2, ! c1.equals(c2));				 //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void testEquals5() {    
		MatchCriteria c1 = example("e1", "abc"); //$NON-NLS-1$ //$NON-NLS-2$
		MatchCriteria c2 = example("e2", "abc"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue("Different match criteria compare as equal: " + c1 + ", " + c2, ! c1.equals(c2));				 //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void testSelfEquivalence(){
		MatchCriteria c1 = example("abc"); //$NON-NLS-1$
		int equals = 0;
		UnitTestUtil.helpTestEquivalence(equals, c1, c1);
	}

	public void testEquivalence(){
		MatchCriteria c1 = example("abc"); //$NON-NLS-1$
		MatchCriteria c2 = example("abc"); //$NON-NLS-1$
		int equals = 0;
		UnitTestUtil.helpTestEquivalence(equals, c1, c2);
	}
	
	public void testCloneEquivalence(){
		MatchCriteria c1 = example("abc"); //$NON-NLS-1$
		MatchCriteria c2 = (MatchCriteria)c1.clone();
		int equals = 0;
		UnitTestUtil.helpTestEquivalence(equals, c1, c2);
	}	
	
    public void testNonEquivalence1(){
        //test transitivity with two nonequal Objects
        MatchCriteria c1 = example("e1", "abc"); //$NON-NLS-1$ //$NON-NLS-2$
        MatchCriteria c2 = example("ozzy", '#'); //$NON-NLS-1$
        int equals = -1;
        UnitTestUtil.helpTestEquivalence(equals, c1, c2);
    }
    
    public void testNonEquivalence2(){
        MatchCriteria c1 = example("abc", '#'); //$NON-NLS-1$
        c1.setNegated(true);
        MatchCriteria c2 = example("abc", '#'); //$NON-NLS-1$
        c2.setNegated(false);
        int equals = -1;
        UnitTestUtil.helpTestEquivalence(equals, c1, c2);
    }
}
