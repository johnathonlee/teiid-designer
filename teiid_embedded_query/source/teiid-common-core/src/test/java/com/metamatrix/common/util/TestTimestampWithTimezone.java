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

package com.metamatrix.common.util;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import junit.framework.TestCase;

public class TestTimestampWithTimezone extends TestCase {

    /**
     * Constructor for TestTimestampWithTimezone.
     * 
     * @param name
     */
    public TestTimestampWithTimezone(String name) {
        super(name);
    }
    
	public void setUp() { 
		TimestampWithTimezone.resetCalendar(TimeZone.getTimeZone("America/Chicago")); //$NON-NLS-1$ 
	}
	
	public void tearDown() { 
		TimestampWithTimezone.resetCalendar(null);
	}

    /**
     * Ensures that the same calendar fields in different timezones (initially different UTC) can be converted to the same UTC in
     * the local time zone
     * 
     * @param startts
     * @param startnanos
     * @param starttz
     * @param endtz
     * @since 4.3
     */
    public void helpTestSame(String startts,
                             int startnanos,
                             String starttz,
                             String endtz) {
        try {
            Timestamp start = getTimestamp(startts, startnanos, starttz);
            Timestamp end = getTimestamp(startts, startnanos, endtz);

            assertFalse("Initial timestamps should be different UTC times", start.getTime() == end.getTime()); //$NON-NLS-1$

            assertEquals(TimestampWithTimezone.createTimestamp(start, TimeZone.getTimeZone(starttz), Calendar.getInstance())
                                              .getTime(), TimestampWithTimezone.createTimestamp(end,
                                                                                                TimeZone.getTimeZone(endtz),
                                                                                                Calendar.getInstance()).getTime());
        } catch (ParseException e) {
            fail(e.toString());
        }
    }

    /**
     * Assuming local time zone of -06:00, change ts to endtz and compare to expected
     * 
     * @param ts
     * @param endtz
     * @since 4.3
     */
    public void helpTestChange(String ts,
                               String endtz,
                               String expected) {
        Timestamp start = Timestamp.valueOf(ts);
        Calendar target = Calendar.getInstance(TimeZone.getTimeZone(endtz));
        assertEquals(expected,
                     TimestampWithTimezone.createTimestamp(start, TimeZone.getTimeZone("America/Chicago"), target).toString()); //$NON-NLS-1$         
    }

    /**
     * @param startts
     * @param startnanos
     * @param starttz
     * @throws ParseException
     * @since 4.3
     */
    private Timestamp getTimestamp(String startts,
                                   int startnanos,
                                   String starttz) throws ParseException {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss"); //$NON-NLS-1$
        df.setTimeZone(TimeZone.getTimeZone(starttz));

        Timestamp ts = new Timestamp(df.parse(startts).getTime());
        ts.setNanos(startnanos);
        return ts;
    }

    public void testDST() {
        helpTestSame("2005-10-30 02:39:10", 1, "America/Chicago", //$NON-NLS-1$ //$NON-NLS-2$
                     "GMT-05:00"); //$NON-NLS-1$ 

        // ambiguous times are defaulted to standard time equivalent
        helpTestSame("2005-10-30 01:39:10", 1, "America/Chicago", //$NON-NLS-1$ //$NON-NLS-2$
                     "GMT"); //$NON-NLS-1$ 

        // test to ensure a time not representable in DST is converted correctly
        helpTestSame("2005-04-03 02:39:10", 1, "GMT", //$NON-NLS-1$ //$NON-NLS-2$
                     "America/Chicago"); //$NON-NLS-1$ 

        //expected is in DST
        helpTestChange("2005-10-30 02:39:10.1", "GMT", "2005-10-29 21:39:10.1"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        //expected is in standard time
        helpTestChange("2005-10-30 10:39:10.1", "GMT", "2005-10-30 04:39:10.1"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ 

    }

    public void testTimezone() {
        helpTestSame("2004-06-29 15:39:10", 1, "GMT-06:00", //$NON-NLS-1$ //$NON-NLS-2$
                     "GMT-05:00"); //$NON-NLS-1$ 
    }

    public void testTimezone2() {
        helpTestSame("2004-06-29 15:39:10", 1, "GMT-08:00", //$NON-NLS-1$ //$NON-NLS-2$
                     "GMT-06:00"); //$NON-NLS-1$ 
    }

    public void testTimezone3() {
        helpTestSame("2004-08-31 18:25:54", 1, "Europe/London", //$NON-NLS-1$ //$NON-NLS-2$
                     "GMT"); //$NON-NLS-1$ 
    }

    public void testTimezoneOverMidnight() {
        helpTestSame("2004-06-30 23:39:10", 1, "America/Los_Angeles", //$NON-NLS-1$ //$NON-NLS-2$
                     "America/Chicago"); //$NON-NLS-1$ 
    }

    public void testCase2852() {
        helpTestSame("2005-05-17 22:35:33", 508659, "GMT", //$NON-NLS-1$ //$NON-NLS-2$
                     "America/New_York"); //$NON-NLS-1$ 
    }

    public void testCreateDate() {
        Timestamp t = Timestamp.valueOf("2004-06-30 23:39:10.1201"); //$NON-NLS-1$
        Date date = TimestampWithTimezone.createDate(t);
        
        Calendar cal = Calendar.getInstance();
        
        cal.setTimeInMillis(date.getTime());
        
        assertEquals(cal.get(Calendar.HOUR_OF_DAY), 0);
        assertEquals(cal.get(Calendar.MINUTE), 0);
        assertEquals(cal.get(Calendar.SECOND), 0);
        assertEquals(cal.get(Calendar.MILLISECOND), 0);
        assertEquals(cal.get(Calendar.YEAR), 2004);
        assertEquals(cal.get(Calendar.MONTH), Calendar.JUNE);
        assertEquals(cal.get(Calendar.DATE), 30);
    }
    
    public void testCreateTime() {
        Timestamp t = Timestamp.valueOf("2004-06-30 23:39:10.1201"); //$NON-NLS-1$
        Time date = TimestampWithTimezone.createTime(t);
        
        Calendar cal = Calendar.getInstance();
        
        cal.setTimeInMillis(date.getTime());
        
        assertEquals(cal.get(Calendar.HOUR_OF_DAY), 23);
        assertEquals(cal.get(Calendar.MINUTE), 39);
        assertEquals(cal.get(Calendar.SECOND), 10);
        assertEquals(cal.get(Calendar.MILLISECOND), 0);
        assertEquals(cal.get(Calendar.YEAR), 1970);
        assertEquals(cal.get(Calendar.MONTH), Calendar.JANUARY);
        assertEquals(cal.get(Calendar.DATE), 1);
    }
    
    /**
     * Even though the id of the timezones are different, this should not change the result
     */
    public void testDateToDateConversion() {
    	Date t = Date.valueOf("2004-06-30"); //$NON-NLS-1$
    	Date converted = TimestampWithTimezone.createDate(t, TimeZone.getTimeZone("America/Chicago"), Calendar.getInstance(TimeZone.getTimeZone("US/Central"))); //$NON-NLS-1$ //$NON-NLS-2$
    	
    	assertEquals(t.getTime(), converted.getTime());
    }
    
    public void testDateToDateConversion1() {
    	Date t = Date.valueOf("2004-06-30"); //$NON-NLS-1$
    	Date converted = TimestampWithTimezone.createDate(t, TimeZone.getTimeZone("America/Chicago"), Calendar.getInstance(TimeZone.getTimeZone("GMT"))); //$NON-NLS-1$ //$NON-NLS-2$
    	
    	Calendar cal = Calendar.getInstance();
    	cal.setTime(converted);
    	
    	assertEquals(0, cal.get(Calendar.MILLISECOND));
    }

}