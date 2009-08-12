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

package com.metamatrix.common.types.basic;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

import junit.framework.TestCase;

import com.metamatrix.common.types.ClobType;
import com.metamatrix.common.types.DataTypeManager;
import com.metamatrix.common.types.SQLXMLImpl;
import com.metamatrix.common.types.TestDataTypeManager;
import com.metamatrix.common.types.Transform;
import com.metamatrix.common.types.TransformationException;
import com.metamatrix.common.types.XMLType;
import com.metamatrix.common.types.DataTypeManager.DefaultDataClasses;
import com.metamatrix.common.types.DataTypeManager.DefaultDataTypes;


/** 
 * @since 4.2
 */
public class TestTransforms extends TestCase {
    
    private static void helpTestTransform(Object value, Object expectedValue) throws TransformationException {
        Transform transform = DataTypeManager.getTransform(value.getClass(), expectedValue.getClass());
        Object result = transform.transform(value);
        assertEquals(expectedValue, result);
    }
    
    private static void validateTransform(String src, Object value, String target, Object expectedValue) throws TransformationException {
        try {                        
            Transform transform = DataTypeManager.getTransform(DataTypeManager.getDataTypeClass(src), expectedValue.getClass());
            Object result = transform.transform(value);
            assertTrue(expectedValue.getClass().isAssignableFrom(result.getClass()));
        } catch (TransformationException e) {
            if (!isException(DataTypeManager.getDataTypeName(value.getClass()), target,value)) {
                throw e;
            }
        }
    }    

    private static void helpTransformException(Object value, Object expectedValue) {
        try {
            Transform transform = DataTypeManager.getTransform(value.getClass(), expectedValue.getClass());
            transform.transform(value);
            fail("Expected to get an exception during the transformation"); //$NON-NLS-1$
        } catch (TransformationException e) {
            assertTrue(true);
        }
    }    
    
    public void testBigDecimalToBigInteger_Defect16875() throws TransformationException {
        helpTestTransform(new BigDecimal("0.5867"), new BigInteger("0")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testString2Boolean() throws TransformationException {
        helpTestTransform(new String("1"), Boolean.TRUE); //$NON-NLS-1$
        helpTestTransform(new String("0"), Boolean.FALSE); //$NON-NLS-1$
        helpTestTransform(new String("true"), Boolean.TRUE); //$NON-NLS-1$
        helpTestTransform(new String("false"), Boolean.FALSE); //$NON-NLS-1$
        helpTestTransform(new String("foo"), Boolean.FALSE); //$NON-NLS-1$
    }
    
    public void testByte2Boolean() throws TransformationException {
        helpTestTransform(new Byte((byte)1), Boolean.TRUE);
        helpTestTransform(new Byte((byte)0), Boolean.FALSE);
        helpTransformException(new Byte((byte)12), Boolean.FALSE);
    }

    public void testShort2Boolean() throws TransformationException {
        helpTestTransform(new Short((short)1), Boolean.TRUE);
        helpTestTransform(new Short((short)0), Boolean.FALSE);
        helpTransformException(new Short((short)12), Boolean.FALSE);
    }

    public void testInteger2Boolean() throws TransformationException {
        helpTestTransform(new Integer(1), Boolean.TRUE);
        helpTestTransform(new Integer(0), Boolean.FALSE);
        helpTransformException(new Integer(12), Boolean.FALSE);
    }

    public void testLong2Boolean() throws TransformationException {
        helpTestTransform(new Long(1), Boolean.TRUE);
        helpTestTransform(new Long(0), Boolean.FALSE);
        helpTransformException(new Long(12), Boolean.FALSE);
    }
    
    public void testBigInteger2Boolean() throws TransformationException {
        helpTestTransform(new BigInteger("1"), Boolean.TRUE); //$NON-NLS-1$
        helpTestTransform(new BigInteger("0"), Boolean.FALSE); //$NON-NLS-1$
        helpTransformException(new BigInteger("12"), Boolean.FALSE); //$NON-NLS-1$
    }
    
    static Object[][] testData = {
        /*string-0*/  {"1", "0", "123"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ 
        /*char-1*/    {new Character('1'), new Character('0'), new Character('1')},
        /*boolean-2*/ {Boolean.TRUE, Boolean.FALSE, Boolean.FALSE},
        /*byte-3*/    {new Byte((byte)1), new Byte((byte)0), new Byte((byte)123)},
        /*short-4*/   {new Short((short)1), new Short((short)0), new Short((short)123)},
        /*integer-5*/ {new Integer(1), new Integer(0), new Integer(123)},
        /*long-6*/    {new Long(1), new Long(0), new Long(123)},
        /*biginteger-7*/ {new BigInteger("1"), new BigInteger("0"), new BigInteger("123")}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        /*float-8*/   {new Float(1.0f), new Float(0.0f), new Float(123.0f)},
        /*double-9*/  {new Double(1.0d), new Double(0.0d), new Double(123.0d)},
        /*bigdecimal-10*/{new BigDecimal("1"), new BigDecimal("0"), new BigDecimal("123")}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        /*date-11*/    {new Date(System.currentTimeMillis()), new Date(System.currentTimeMillis()), new Date(System.currentTimeMillis())},
        /*time-12*/    {new Time(System.currentTimeMillis()), new Time(System.currentTimeMillis()), new Time(System.currentTimeMillis())},
        /*timestamp-13*/{new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis())},
        /*object-14*/  {null, null, null},  
        /*blob-15*/    {null, null, null},
        /*clob-16*/    {new ClobType(ClobType.createClob("ClobData".toCharArray())), new ClobType(ClobType.createClob("0".toCharArray())), new ClobType(ClobType.createClob("123".toCharArray()))}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ 
        /*xml-17*/     {new XMLType(new SQLXMLImpl("<foo>bar</foo>")), new XMLType(new SQLXMLImpl("<foo>bar</foo>")), new XMLType(new SQLXMLImpl("<foo>bar</foo>"))}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
     }; 
    
    private String[] dataTypes = TestDataTypeManager.dataTypes;
    private char[][] conversions = TestDataTypeManager.conversions;
    private static boolean isException(String src, String tgt, Object source) {
        return (src.equals(DataTypeManager.DefaultDataTypes.STRING) && tgt.equals(DataTypeManager.DefaultDataTypes.XML))
            || (src.equals(DataTypeManager.DefaultDataTypes.STRING) && tgt.equals(DataTypeManager.DefaultDataTypes.CHAR))  
            || (src.equals(DataTypeManager.DefaultDataTypes.STRING) && tgt.equals(DataTypeManager.DefaultDataTypes.TIME)) 
            || (src.equals(DataTypeManager.DefaultDataTypes.STRING) && tgt.equals(DataTypeManager.DefaultDataTypes.TIMESTAMP)) 
            || (src.equals(DataTypeManager.DefaultDataTypes.STRING) && tgt.equals(DataTypeManager.DefaultDataTypes.DATE)) 
            || (src.equals(DataTypeManager.DefaultDataTypes.BYTE) && tgt.equals(DataTypeManager.DefaultDataTypes.BOOLEAN) && source == testData[3][2]) 
            || (src.equals(DataTypeManager.DefaultDataTypes.SHORT) && tgt.equals(DataTypeManager.DefaultDataTypes.BOOLEAN) && source == testData[4][2]) 
            || (src.equals(DataTypeManager.DefaultDataTypes.INTEGER) && tgt.equals(DataTypeManager.DefaultDataTypes.BOOLEAN) && source == testData[5][2]) 
            || (src.equals(DataTypeManager.DefaultDataTypes.LONG) && tgt.equals(DataTypeManager.DefaultDataTypes.BOOLEAN) && source == testData[6][2]) 
            || (src.equals(DataTypeManager.DefaultDataTypes.BIG_INTEGER) && tgt.equals(DataTypeManager.DefaultDataTypes.BOOLEAN) && source == testData[7][2]) 
            || (src.equals(DataTypeManager.DefaultDataTypes.FLOAT) && tgt.equals(DataTypeManager.DefaultDataTypes.BOOLEAN) && source == testData[8][2])         
            || (src.equals(DataTypeManager.DefaultDataTypes.DOUBLE) && tgt.equals(DataTypeManager.DefaultDataTypes.BOOLEAN) && source == testData[9][2]) 
            || (src.equals(DataTypeManager.DefaultDataTypes.BIG_DECIMAL) && tgt.equals(DataTypeManager.DefaultDataTypes.BOOLEAN) && source == testData[10][2]);             
    }
    
    public void testAllConversions() throws TransformationException {
        for (int src = 0; src < dataTypes.length; src++) {
            for (int tgt =0; tgt < dataTypes.length; tgt++) {
                char c = conversions[src][tgt];
                
                if (c == 'I' || c == 'C') {
                    Object[] srcdata = testData[src];
                    Object[] tgtdata = testData[tgt];
                    for (int i=0; i<tgtdata.length; i++) {
                        if (tgtdata[i] != null && srcdata[i] != null) {
                            validateTransform(dataTypes[src], srcdata[i], dataTypes[tgt],tgtdata[i]);
                        }
                    }                    
                }
            }
        }        
    }
    
    public void testAllConversionsAsObject() throws TransformationException {
        for (int src = 0; src < dataTypes.length; src++) {
            for (int tgt =0; tgt < dataTypes.length; tgt++) {
                char c = conversions[src][tgt];
                
                if (c == 'I' || c == 'C') {
                    Object[] srcdata = testData[src];
                    Object[] tgtdata = testData[tgt];
                    for (int i=0; i<tgtdata.length; i++) {
                        if (tgtdata[i] != null && srcdata[i] != null) {
                            validateTransform(DefaultDataTypes.OBJECT, srcdata[i], dataTypes[tgt],tgtdata[i]);
                        }
                    }                    
                }
            }
        }        
    }
    
    public void testObjectToAnyTransformFailure() {
        Transform transform = DataTypeManager.getTransform(DefaultDataClasses.OBJECT, DefaultDataClasses.TIME);
        try {
            transform.transform("1"); //$NON-NLS-1$
            fail("expected exception"); //$NON-NLS-1$
        } catch (TransformationException e) {
            assertEquals("Error Code:ERR.003.029.0025 Message:Invalid conversion from type class java.lang.Object with value '1' to type class java.sql.Time", e.getMessage()); //$NON-NLS-1$
        }
    }
    
    public void testSQLXMLToStringTransform() throws Exception {
        StringBuffer xml = new StringBuffer();
        int iters = DataTypeManager.MAX_STRING_LENGTH/10;
        for (int i = 0; i < iters; i++) {
            if (i < iters/2) {
                xml.append("<opentag>1"); //$NON-NLS-1$
            } else {
                xml.append("</opentag>"); //$NON-NLS-1$
            }
        }
        
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"; //$NON-NLS-1$
        expected += xml.substring(0, DataTypeManager.MAX_STRING_LENGTH - expected.length());
                
        helpTestTransform(new StringToSQLXMLTransform().transform(xml.toString()), expected);
    }
}
