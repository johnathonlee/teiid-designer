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

package com.metamatrix.query.processor.xml;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.query.FunctionExecutionException;
import com.metamatrix.common.types.DataTypeManager;
import com.metamatrix.query.function.FunctionMethods;


/**
 * This class will make a minimal effort to output xsd formatted values for a given
 * builtin type.  It will not attempt to narrrow or otherwise fit most values into
 * their output space (months can be greater than 12, nonNegative numbers can be 
 * negative, etc.)
 */
final class XMLValueTranslator {

    private static String NEGATIVE_INFINITY = "-INF"; //$NON-NLS-1$
    private static String POSITIVE_INFINITY = "INF"; //$NON-NLS-1$
    
    private static String GMONTHDAY_FORMAT = "--MM-dd"; //$NON-NLS-1$
    private static String GYEAR_FORMAT = "0000"; //$NON-NLS-1$
    private static String GYEARMONTH_FORMAT = "yyyy-MM"; //$NON-NLS-1$
    private static String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"; //$NON-NLS-1$
    
    //YEAR 0 in the server timezone. used to determine negative years
    private static long YEAR_ZERO;
    
    static {
        Calendar cal = Calendar.getInstance();
    
        for (int i = 0; i <= Calendar.MILLISECOND; i++) {
            cal.set(i, 0);
        }
        YEAR_ZERO = cal.getTimeInMillis();
    }
    
    private static String TIMESTAMP_MICROZEROS = "000000000"; //$NON-NLS-1$
    
    public static final String DATETIME    = "dateTime"; //$NON-NLS-1$
    public static final String DOUBLE      = "double"; //$NON-NLS-1$
    public static final String FLOAT       = "float"; //$NON-NLS-1$
    public static final String GDAY        = "gDay"; //$NON-NLS-1$
    public static final String GMONTH      = "gMonth"; //$NON-NLS-1$
    public static final String GMONTHDAY   = "gMonthDay"; //$NON-NLS-1$
    public static final String GYEAR       = "gYear"; //$NON-NLS-1$
    public static final String GYEARMONTH  = "gYearMonth"; //$NON-NLS-1$
    
    public static final String STRING = "string"; //$NON-NLS-1$
    
    private static final Map TYPE_CODE_MAP;
    
    private static final int DATETIME_CODE = 0;
    private static final int DOUBLE_CODE = 1;
    private static final int FLOAT_CODE = 2;
    private static final int GDAY_CODE = 3;
    private static final int GMONTH_CODE = 4;
    private static final int GMONTHDAY_CODE = 5;
    private static final int GYEAR_CODE = 6;
    private static final int GYEARMONTH_CODE = 7;
        
    static {
        TYPE_CODE_MAP = new HashMap(20);
        TYPE_CODE_MAP.put(DATETIME, new Integer(DATETIME_CODE));
        TYPE_CODE_MAP.put(DOUBLE, new Integer(DOUBLE_CODE));
        TYPE_CODE_MAP.put(FLOAT, new Integer(FLOAT_CODE));
        TYPE_CODE_MAP.put(GDAY, new Integer(GDAY_CODE));
        TYPE_CODE_MAP.put(GMONTH, new Integer(GMONTH_CODE));
        TYPE_CODE_MAP.put(GMONTHDAY, new Integer(GMONTHDAY_CODE));
        TYPE_CODE_MAP.put(GYEAR, new Integer(GYEAR_CODE));
        TYPE_CODE_MAP.put(GYEARMONTH, new Integer(GYEARMONTH_CODE));   
    }
       
    /**
     * Translate the value object coming from the mapping class into the string that will be 
     * placed in the XML document for a tag.  Usually, the value.toString() method is just called
     * to translate to a string.  In some special cases, the runtimeType and builtInType
     * will be used to determine a custom translation to string where the Java object toString()
     * does not comply with the XML Schema spec.
     * 
     * NOTE: date objects are not checked for years less than 1
     *    
     * @param value The value
     * @param runtimeType The runtime type
     * @param builtInType The design-time atomic built-in type (or null if none)
     * @return String representing the value
     * @throws FunctionExecutionException 
     * @since 5.0
     */
    static String translateToXMLValue(Object value, Class runtimeType, String builtInType) throws MetaMatrixComponentException, FunctionExecutionException {
        if (value == null) {
            return null;
        }
        
        Integer typeCode = (Integer)TYPE_CODE_MAP.get(builtInType);
        
        String valueStr = null;
        
        if (builtInType == null || typeCode == null || runtimeType == DataTypeManager.DefaultDataClasses.STRING || STRING.equals(builtInType)) {
            valueStr = defaultTranslation(value);
        } else {
        
            int type = typeCode.intValue();
            
            switch (type) {
                case DATETIME_CODE:
                    valueStr = timestampToDateTime((Timestamp)value);
                    break;
                case DOUBLE_CODE:
                    valueStr = doubleToDouble((Double)value);
                    break;
                case FLOAT_CODE:
                    valueStr = floatToFloat((Float)value);
                    break;
                case GDAY_CODE:
                    valueStr = bigIntegerTogDay((BigInteger)value);
                    break;
                case GMONTH_CODE:
                    valueStr = bigIntegerTogMonth((BigInteger)value);
                    break;
                case GMONTHDAY_CODE:
                    valueStr = (String)FunctionMethods.formatTimestamp(value, GMONTHDAY_FORMAT);
                    break;
                case GYEAR_CODE:
                    valueStr = (String)FunctionMethods.formatBigInteger(value, GYEAR_FORMAT);
                    break;
                case GYEARMONTH_CODE:
                    valueStr = timestampTogYearMonth(value);
                    break;
                default:
                    valueStr = defaultTranslation(value);
                    break;
            }
            
        }        

        //Per defects 11789, 14905, 15117
        if (valueStr != null && valueStr.length()==0){
            valueStr = null;
        }
    
        return valueStr;
    }

    /** 
     * @param value
     * @return
     * @throws FunctionExecutionException
     * @since 4.3
     */
    private static String timestampTogYearMonth(Object value) throws FunctionExecutionException {
        String valueStr;
        Timestamp time = (Timestamp)value;
        valueStr = (String)FunctionMethods.formatTimestamp(value, GYEARMONTH_FORMAT);
        if (time.getTime() < YEAR_ZERO) {
            valueStr = "-" + valueStr; //$NON-NLS-1$
        }
        return valueStr;
    }
    
    /**
     * Formats a timestamp to an xs:dateTime.  This uses DATETIME_FORMAT
     * with a trailing string for nanoseconds (without right zeros). 
     */
    static String timestampToDateTime(Timestamp time) throws FunctionExecutionException {
        String result = (String)FunctionMethods.formatTimestamp(time, DATETIME_FORMAT);
        int nanos = time.getNanos();
        if (nanos == 0) {
            return result;
        }
        
        StringBuffer resultBuffer = new StringBuffer();
        boolean first = true;
        int i = 0;
        for (; i < 9 && nanos > 0; i++) {
            int digit = nanos%10;
            if (first) {
                if (digit > 0) {
                    resultBuffer.insert(0, digit);
                    first = false;
                }
            } else {
                resultBuffer.insert(0, digit);
            }
            nanos /= 10;
        }
        if (i < 9) {
            resultBuffer.insert(0, TIMESTAMP_MICROZEROS.substring(i));
        }
        resultBuffer.insert(0, "."); //$NON-NLS-1$
        resultBuffer.insert(0, result);
        if (time.getTime() < YEAR_ZERO) {
            resultBuffer.insert(0, "-"); //$NON-NLS-1$
        }
        return resultBuffer.toString();
        
    }
    
    /**
     * Translate any non-null value to a string by using the Object toString() method.
     * This works in any case where the Java string representation of an object is the 
     * same as the expected XML Schema output form.
     *   
     * @param value Value returned from a mapping class
     * @return String content to put in XML output
     * @since 5.0
     */
    static String defaultTranslation(Object value) {
        return value.toString();
    }
    
    /**
     * Translate runtime float to xs:float string value.  The normal Java representation 
     * for floats is fine except the strings used for positive and negative infinity 
     * are different.
     *  
     * @param f Runtime float
     * @return String representing xs:float
     * @since 5.0
     */
    static String floatToFloat(Float f) {
        if(f.floatValue() == Float.NEGATIVE_INFINITY) {
            return NEGATIVE_INFINITY;
        } else if(f.floatValue() == Float.POSITIVE_INFINITY) {
            return POSITIVE_INFINITY;
        }
        return defaultTranslation(f);
    }

    /**
     * Translate runtime double to xs:double string value.  The normal Java representation 
     * for doubles is fine except the strings used for positive and negative infinity 
     * are different.
     *  
     * @param d Runtime double
     * @return String representing xs:double
     * @since 5.0
     */
    static String doubleToDouble(Double d) {
        if(d.doubleValue() == Double.NEGATIVE_INFINITY) {
            return NEGATIVE_INFINITY;
        } else if(d.doubleValue() == Double.POSITIVE_INFINITY) {
            return POSITIVE_INFINITY;
        }                    
        return defaultTranslation(d);
    }

    /**
     * gMonths out of the valid range are returned in tact. 
     * @param value
     * @return
     * @since 5.0
     */
    static String bigIntegerTogMonth(BigInteger value) {
        long month = value.longValue();
        
        if(month < 10) {
            // Add leading 0
            return "--0" + month; //$NON-NLS-1$
        } 

        // Don't need leading 0
        return "--" + month; //$NON-NLS-1$        
    }
    
    /**
     * gDays out of the valid range are returned in tact. 
     * @param value
     * @return
     * @since 5.0
     */
    static String bigIntegerTogDay(BigInteger value) {
        long day = value.longValue();
        
        if(day < 10) {
            // Add leading 0
            return "---0" + day; //$NON-NLS-1$
        } 

        // Don't need leading 0
        return "---" + day; //$NON-NLS-1$        
    }
    
}
