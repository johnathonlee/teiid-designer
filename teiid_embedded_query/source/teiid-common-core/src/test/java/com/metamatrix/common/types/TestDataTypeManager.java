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

package com.metamatrix.common.types;

import java.sql.Types;
import java.util.Iterator;
import java.util.Set;

import javax.sql.rowset.serial.SerialBlob;

import junit.framework.TestCase;

public class TestDataTypeManager extends TestCase {

	// ################################## FRAMEWORK ################################
	
	public TestDataTypeManager(String name) { 
		super(name);
	}	
	
	// ################################## TEST HELPERS ################################
	
    private void helpDetermineDataType(Object value, Class expectedClass) { 
        Class actualClass = DataTypeManager.determineDataTypeClass(value);
        assertNotNull("Should never receive null when determining data type of object: " + value); //$NON-NLS-1$
        assertEquals("Mismatch in expected and actual MetaMatrix type class for [" + value + "]: ", expectedClass, actualClass); //$NON-NLS-1$ //$NON-NLS-2$
    }
    public static String[] dataTypes = {"string","char","boolean","byte","short","integer","long","biginteger",  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
        "float","double","bigdecimal","date","time","timestamp","object","blob","clob", DataTypeManager.DefaultDataTypes.XML}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
    
    /**
     * I - Implicitly Converted
     * C - Explicitly Converted
     * N - Cannot be converted
     * o - No conversion needed
     */
    public static char conversions [][] =
    {
        /*                                                          Big                             */         
        /*                                       i                   d                              */
        /*                   s                   n       b       d   e          Time o              */
        /*                   t               s   t       i   f   o   c           s   b              */
        /*                   r   c   b   b   h   e   L   g   l   u   i   d   t   t   j   b   c      */
        /*                   i   h   o   y   o   g   o   i   o   b   m   a   i   a   e   l   l   x   */
        /*                   n   a   o   t   r   e   n   n   a   l   a   t   m   m   c   o   o   m   */
        /*                   g   r   l   e   t   r   g   t   t   e   l   e   e   p   t   b   b   l   */
        /*                  -------------------------------------------------------------------------*/
       /*String*/       {   'O','C','C','C','C','C','C','C','C','C','C','C','C','C','I','N','I','C'     },
        /*char*/        {   'I','O','N','N','N','N','N','N','N','N','N','N','N','N','I','N','N','N'     },
        /*bool*/        {   'I','N','O','I','I','I','I','I','I','I','I','N','N','N','I','N','N','N'     },
        /*byte*/        {   'I','N','C','O','I','I','I','I','I','I','I','N','N','N','I','N','N','N'     },
        /*short*/       {   'I','N','C','C','O','I','I','I','I','I','I','N','N','N','I','N','N','N'     },
        /*int*/         {   'I','N','C','C','C','O','I','I','I','I','I','N','N','N','I','N','N','N'     },
        /*long*/        {   'I','N','C','C','C','C','O','I','C','C','I','N','N','N','I','N','N','N'     },
        /*bigint*/      {   'I','N','C','C','C','C','C','O','C','C','I','N','N','N','I','N','N','N'     },
        /*float*/       {   'I','N','C','C','C','C','C','C','O','I','I','N','N','N','I','N','N','N'     },
        /*double*/      {   'I','N','C','C','C','C','C','C','C','O','I','N','N','N','I','N','N','N'     },
        /*bigdecimal*/  {   'I','N','C','C','C','C','C','C','C','C','O','N','N','N','I','N','N','N'     },
        /*date*/        {   'I','N','N','N','N','N','N','N','N','N','N','O','N','I','I','N','N','N'     },
        /*time*/        {   'I','N','N','N','N','N','N','N','N','N','N','N','O','I','I','N','N','N'     },
        /*timestamp*/   {   'I','N','N','N','N','N','N','N','N','N','N','C','C','O','I','N','N','N'     },
        /*object*/      {   'C','C','C','C','C','C','C','C','C','C','C','C','C','C','O','C','C','C'     },
        /*blob*/        {   'N','N','N','N','N','N','N','N','N','N','N','N','N','N','I','O','N','N'     },
        /*clob*/        {   'C','N','N','N','N','N','N','N','N','N','N','N','N','N','I','N','O','N'     },
        /*xml*/         {   'C','N','N','N','N','N','N','N','N','N','N','N','N','N','I','N','N','O'     }
    };
    

	// ################################## ACTUAL TESTS ################################
	
	public void testTypeMappings() {
		Set dataTypeNames = DataTypeManager.getAllDataTypeNames();
		Iterator iter = dataTypeNames.iterator();
		while(iter.hasNext()) { 
			String dataTypeName = (String) iter.next();
			Class dataTypeClass = DataTypeManager.getDataTypeClass(dataTypeName);
			assertNotNull("Data type class was null for type " + dataTypeName, dataTypeClass); //$NON-NLS-1$
			String dataTypeName2 = DataTypeManager.getDataTypeName(dataTypeClass);
			assertEquals("Name to class to name not equals: ", dataTypeName, dataTypeName2); //$NON-NLS-1$
		}
	}
    
    public void testCheckConversions() {
        for (int src = 0; src < dataTypes.length; src++) {
            for (int tgt =0; tgt < dataTypes.length; tgt++) {
                char c = conversions[src][tgt];
                
                if (c == 'I') {
                    assertTrue("src="+dataTypes[src]+" target="+dataTypes[tgt]+" should be Implicit", DataTypeManager.isImplicitConversion(dataTypes[src], dataTypes[tgt])); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    assertFalse("src="+dataTypes[src]+" target="+dataTypes[tgt]+" should be not be Explicit", DataTypeManager.isExplicitConversion(dataTypes[src], dataTypes[tgt]));                     //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    assertTrue("src="+dataTypes[src]+" target="+dataTypes[tgt]+" transform should be avaialble", DataTypeManager.isTransformable(dataTypes[src], dataTypes[tgt])); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
                else if (c == 'C') {
                    assertFalse("src="+dataTypes[src]+" target="+dataTypes[tgt]+" should not be Implicit", DataTypeManager.isImplicitConversion(dataTypes[src], dataTypes[tgt])); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    assertTrue("src="+dataTypes[src]+" target="+dataTypes[tgt]+" should be Explicit", DataTypeManager.isExplicitConversion(dataTypes[src], dataTypes[tgt]));                     //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    assertTrue("src="+dataTypes[src]+" target="+dataTypes[tgt]+" transform should be avaialble", DataTypeManager.isTransformable(dataTypes[src], dataTypes[tgt])); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$                    
                }
                else if ( c == 'O' || c == 'N') {                    
                    assertFalse("src="+dataTypes[src]+" target="+dataTypes[tgt]+" should not be Implicit", DataTypeManager.isImplicitConversion(dataTypes[src], dataTypes[tgt])); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    assertFalse("src="+dataTypes[src]+" target="+dataTypes[tgt]+" should not be Explicit", DataTypeManager.isExplicitConversion(dataTypes[src], dataTypes[tgt]));                     //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    assertFalse("src="+dataTypes[src]+" target="+dataTypes[tgt]+" No transform should be avaialble", DataTypeManager.isTransformable(dataTypes[src], dataTypes[tgt])); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$                    
                }
            }
        }
    }
	
    /** Test determine data type for a STRING object. */
    public void testDetermineDataType1() {
        helpDetermineDataType("abc", DataTypeManager.DefaultDataClasses.STRING); //$NON-NLS-1$
    }

    /** Test determine data type for a NULL object. */
    public void testDetermineDataType2() {
        helpDetermineDataType(null, DataTypeManager.DefaultDataClasses.NULL);
    }
    	
    /** Test determine data type for an unknown object type - should be typed as an OBJECT. */
    public void testDetermineDataType3() throws Exception {
        java.net.URL url = new java.net.URL("http://fake"); //$NON-NLS-1$
        helpDetermineDataType(url, DataTypeManager.DefaultDataClasses.OBJECT);
    }
    
    public void testCheckAllConversions() {
        Set allTypes = DataTypeManager.getAllDataTypeNames();
        Iterator srcIter = allTypes.iterator();
        while(srcIter.hasNext()) { 
            String src = (String) srcIter.next();
            
            Iterator tgtIter = allTypes.iterator();
            while(tgtIter.hasNext()) { 
                String tgt = (String) tgtIter.next();    
                
                boolean isImplicit = DataTypeManager.isImplicitConversion(src, tgt);
                boolean isExplicit = DataTypeManager.isExplicitConversion(src, tgt);
                
                if(isImplicit && isExplicit) { 
                    fail("Can't be both implicit and explicit for " + src + " to " + tgt);     //$NON-NLS-1$ //$NON-NLS-2$
                }                
            }
        }        
    }
    
    public void testTimeConversions() {
        Transform t = DataTypeManager.getPreferredTransform(DataTypeManager.DefaultDataTypes.TIMESTAMP, DataTypeManager.DefaultDataTypes.DATE);
        
        assertEquals(DataTypeManager.DefaultDataClasses.TIMESTAMP, t.getTargetType());
        
        t = DataTypeManager.getPreferredTransform(DataTypeManager.DefaultDataTypes.TIME, DataTypeManager.DefaultDataTypes.TIMESTAMP);
        
        assertEquals(DataTypeManager.DefaultDataClasses.TIMESTAMP, t.getTargetType());
    }
    
    public void testJDBCSQLTypeInfo() {
        
        String[] types = MMJDBCSQLTypeInfo.getMMTypeNames();
        
        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            
            assertEquals("Didn't get match for "+ type, MMJDBCSQLTypeInfo.getSQLType(type), MMJDBCSQLTypeInfo.getSQLTypeFromRuntimeType(DataTypeManager.getDataTypeClass(type))); //$NON-NLS-1$
            
            //the classnames will not match the runtime types for xml, clob, blob
            if (!type.equalsIgnoreCase(DataTypeManager.DefaultDataTypes.XML) && !type.equalsIgnoreCase(DataTypeManager.DefaultDataTypes.CLOB) && !type.equalsIgnoreCase(DataTypeManager.DefaultDataTypes.BLOB)) {
                assertEquals("Didn't get match for "+ type, MMJDBCSQLTypeInfo.getSQLType(type), MMJDBCSQLTypeInfo.getSQLTypeFromClass(DataTypeManager.getDataTypeClass(type).getName())); //$NON-NLS-1$
            }
        }
        
        assertEquals(Types.TIMESTAMP, MMJDBCSQLTypeInfo.getSQLTypeFromRuntimeType(DataTypeManager.DefaultDataClasses.TIMESTAMP));
        //## JDBC4.0-begin ##
        assertEquals(Types.SQLXML, MMJDBCSQLTypeInfo.getSQLTypeFromRuntimeType(DataTypeManager.DefaultDataClasses.XML));
        //## JDBC4.0-end ##
        assertEquals(DataTypeManager.DefaultDataTypes.STRING, MMJDBCSQLTypeInfo.getTypeName(Types.CHAR));
        assertEquals(Types.CHAR, MMJDBCSQLTypeInfo.getSQLTypeFromRuntimeType(DataTypeManager.DefaultDataClasses.CHAR));
    }
    
    public void testRuntimeTypeConversion() throws Exception {
    	assertNull(DataTypeManager.convertToRuntimeType(null));
    	
    	assertTrue(DataTypeManager.convertToRuntimeType(new SerialBlob(new byte[0])) instanceof BlobType);
    
    	//unknown type should return as same
    	Object foo = new Object();
    	assertEquals(foo, DataTypeManager.convertToRuntimeType(foo));
    
    	//known type should return as same
    	Integer bar = new Integer(1);
    	assertEquals(bar, DataTypeManager.convertToRuntimeType(bar));
    }
    
    public void testObjectType() {
    	assertEquals(DataTypeManager.DefaultDataClasses.OBJECT, DataTypeManager.getDataTypeClass("foo")); //$NON-NLS-1$
    	
    	assertEquals(DataTypeManager.DefaultDataTypes.OBJECT, DataTypeManager.getDataTypeName(TestDataTypeManager.class));
    }
	
}
