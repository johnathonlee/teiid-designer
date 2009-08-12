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

package com.metamatrix.jdbc;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.metamatrix.jdbc.BatchResults.Batch;

import junit.framework.TestCase;



/** 
 * @since 4.3
 */
public class TestBatchResults extends TestCase {
	
	static class MockBatchFetcher implements BatchFetcher {

		private int totalRows = 50;
		private boolean throwException;
		List batchCalls = new ArrayList<int[]>();
		
		public MockBatchFetcher() {
			
		}
		
		public MockBatchFetcher(int totalRows) {
			this.totalRows = totalRows;
		}

		public Batch requestBatch(int beginRow, int endRow) throws SQLException {
			batchCalls.add(new int[] {beginRow, endRow});
			if (throwException) {
				throw new SQLException();
			}
	        boolean isLast = false;
	        if(beginRow > endRow) {
	            if(endRow < 1) {
	                endRow = 1;
	            }
	            int i = beginRow;
	            beginRow = endRow;
	            endRow = i;
	        } else if(endRow > totalRows) {
	            endRow = totalRows;
	            isLast = true;
	        }
			return new Batch(createBatch(beginRow, endRow), beginRow, endRow, isLast);
		}

		public void throwException() {
			this.throwException = true;
		}
		
	}
    
    public TestBatchResults (String name) {
        super(name);        
    }
    
    private static List[] createBatch(int begin, int end) {
        List[] results = new List[end - begin + 1];
        for(int i=0; i<(end - begin + 1); i++) {
            results[i] = new ArrayList();
            results[i].add(new Integer(i+begin));
        }
        return results;
    }
    
    private List[] createEmptyBatch() {
        List[] results = new List[0];
        return results;
    }

    
    public void testGetCurrentRow1() throws Exception{
        //empty batch
        BatchResults batchResults = new BatchResults(createEmptyBatch(), 0, 0, true, 100);
        assertNull(batchResults.getCurrentRow());
        batchResults.next();
        assertNull(batchResults.getCurrentRow());
    }
    
    public void testGetCurrentRow2() throws Exception{
    	BatchResults batchResults = new BatchResults(createBatch(1, 10), 1, 10, true, 100);
        assertNull(batchResults.getCurrentRow());
        batchResults.next();
        List expectedResult = new ArrayList();
        expectedResult.add(new Integer(1));
        assertEquals(batchResults.getCurrentRow(), expectedResult);
    }
    
    public void testHasNext1() throws Exception{
        //empty batch
    	BatchResults batchResults = new BatchResults(createEmptyBatch(), 0, 0, true, 100);
        assertFalse(batchResults.hasNext());
    }
    
    public void testHasNext2() throws Exception{
        //one row batch
    	BatchResults batchResults = new BatchResults(createBatch(1, 1), 1, 1, true, 100);
        assertTrue(batchResults.hasNext());
    }
    
    public void testHasNext3() throws Exception{
    	BatchResults batchResults = new BatchResults(createBatch(1, 10), 1, 10, true, 100);
        assertTrue(batchResults.hasNext());
    }
    
    public void testNext1() throws Exception{
        //empty batch
    	BatchResults batchResults = new BatchResults(createEmptyBatch(), 0, 0, true, 100);
        assertFalse(batchResults.next());
    }
    
    public void testNext2() throws Exception{
        //one row batch
    	BatchResults batchResults = new BatchResults(createBatch(1, 1), 1, 1, true, 100);
        assertTrue(batchResults.next());
        List expectedResult = new ArrayList();
        expectedResult.add(new Integer(1));
        assertEquals(batchResults.getCurrentRow(), expectedResult);
        assertFalse(batchResults.next());
    }
    
    public void testNext3() throws Exception{
        //one row batch, multiple batches
    	BatchResults batchResults = new BatchResults(createBatch(1, 1), 1, 1, false, 100);
        batchResults.setBatchFetcher(new MockBatchFetcher());
        assertTrue(batchResults.next());
        assertTrue(batchResults.next());
        List expectedResult = new ArrayList();
        expectedResult.add(new Integer(2));
        assertEquals(batchResults.getCurrentRow(), expectedResult);
    }
    
    public void testNext4() throws Exception{
    	BatchResults batchResults = new BatchResults(createBatch(1, 10), 1, 10, false, 100);
        batchResults.setBatchFetcher(new MockBatchFetcher());
        int i;
        for(i=0; i<10; i++) {
            assertTrue(batchResults.next());
            List expectedResult = new ArrayList();
            expectedResult.add(new Integer(i+1));
            assertEquals(batchResults.getCurrentRow(), expectedResult);
        }
        while(batchResults.next()) {
            List expectedResult = new ArrayList();
            expectedResult.add(new Integer((i++)+1));
            assertEquals(batchResults.getCurrentRow(), expectedResult);
        }
        assertFalse(batchResults.next());
    }
    
    public void testHasPrevious1() throws Exception{
        //empty batch
    	BatchResults batchResults = new BatchResults(createEmptyBatch(), 0, 0, true, 100);
        assertFalse(batchResults.hasPrevious());
    }
    
    public void testHasPrevious2() throws Exception{
        //one row batch
    	BatchResults batchResults = new BatchResults(createBatch(1, 1), 1, 1, true, 100);
        assertFalse(batchResults.hasPrevious());
        batchResults.next();
        assertFalse(batchResults.hasPrevious());
        batchResults.next();
        assertTrue(batchResults.hasPrevious());
    }
    
    public void testPrevious1() throws Exception{
        //empty batch
    	BatchResults batchResults = new BatchResults(createEmptyBatch(), 0, 0, true, 100);
        assertFalse(batchResults.previous());
    }
    
    public void testPrevious2() throws Exception{
        //one row batch
    	BatchResults batchResults = new BatchResults(createBatch(1, 1), 1, 1, true, 100);
        assertTrue(batchResults.next());
        assertFalse(batchResults.previous());
        List expectedResult = new ArrayList();
        expectedResult.add(new Integer(1));
        while(batchResults.next()) {
        }
        assertTrue(batchResults.previous());
        assertEquals(batchResults.getCurrentRow(), expectedResult);
    }
    
    public void testPrevious3() throws Exception{
        //one row batch, multiple batches
    	BatchResults batchResults = new BatchResults(createBatch(1, 1), 1, 1, false, 100);
        batchResults.setBatchFetcher(new MockBatchFetcher());
        assertFalse(batchResults.previous());
        assertTrue(batchResults.next());
        assertFalse(batchResults.previous());
        while(batchResults.next()) {         
        }
        assertTrue(batchResults.previous());
        while(batchResults.previous()) {         
        }
        batchResults.next();
        batchResults.next();
        batchResults.next();
        batchResults.previous();
        List expectedResult = new ArrayList();
        expectedResult.add(new Integer(2));
        assertEquals(batchResults.getCurrentRow(), expectedResult);
    }
    
    public void testPrevious4() throws Exception{
    	BatchResults batchResults = new BatchResults(createBatch(1, 10), 1, 10, false, 100);
        batchResults.setBatchFetcher(new MockBatchFetcher());
        int i;
        for(i=0; i<=10; i++) {
            assertTrue(batchResults.next());
        }
        for(i=10; i>0; i--) {
            batchResults.previous();
            List expectedResult = new ArrayList();
            expectedResult.add(new Integer(i));
            assertEquals(batchResults.getCurrentRow(), expectedResult);
        }
    }
    
    public void testAbsolute1() throws Exception{
        //empty batch
    	BatchResults batchResults = new BatchResults(createEmptyBatch(), 0, 0, true, 100);
        assertFalse(batchResults.absolute(0));
        assertFalse(batchResults.absolute(1));
    }
    
    public void testAbsolute2() throws Exception{
        //one row batch
    	BatchResults batchResults = new BatchResults(createBatch(1, 1), 1, 1, true, 100);
    	batchResults.setBatchFetcher(new MockBatchFetcher());
        assertFalse(batchResults.absolute(0));
        assertTrue(batchResults.absolute(1));
        assertTrue(batchResults.absolute(1));
        List expectedResult = new ArrayList();
        expectedResult.add(new Integer(1));
        assertEquals(batchResults.getCurrentRow(), expectedResult);
    }
    
    public void testAbsolute3() throws Exception{
        BatchResults batchResults = new BatchResults(createBatch(1, 10), 1, 10, false, 100);
        batchResults.setBatchFetcher(new MockBatchFetcher(200));
        assertFalse(batchResults.absolute(0));
        assertTrue(batchResults.absolute(11));
        List expectedResult = new ArrayList();
        expectedResult.add(new Integer(11));
        assertEquals(batchResults.getCurrentRow(), expectedResult);
        assertTrue(batchResults.absolute(1));
        expectedResult = new ArrayList();
        expectedResult.add(new Integer(1));
        assertEquals(batchResults.getCurrentRow(), expectedResult);
        assertTrue(batchResults.absolute(100));
        expectedResult = new ArrayList();
        expectedResult.add(new Integer(100));
        assertEquals(batchResults.getCurrentRow(), expectedResult);
    }

    //move backwards with absolute
    public void testAbsolute4() throws Exception{
        //one row batch
    	BatchResults batchResults = new BatchResults(createBatch(1, 1), 1, 1, false, 100);
    	batchResults.setBatchFetcher(new MockBatchFetcher());
        assertTrue(batchResults.absolute(10));
        assertTrue(batchResults.absolute(2));
        List expectedResult = new ArrayList();
        expectedResult.add(new Integer(2));
        assertEquals(batchResults.getCurrentRow(), expectedResult);
    }
    
    public void testAbsolute5() throws Exception{
        //one row batch
    	BatchResults batchResults = new BatchResults(createBatch(1, 1), 1, 1, false, 100);
    	batchResults.setBatchFetcher(new MockBatchFetcher());
        assertTrue(batchResults.absolute(-1));
        List expectedResult = new ArrayList();
        expectedResult.add(new Integer(50));
        assertEquals(batchResults.getCurrentRow(), expectedResult);
        
        assertFalse(batchResults.absolute(-100));
    }
        
    public void testCurrentRowNumber() throws Exception {
    	BatchResults batchResults = new BatchResults(createBatch(1, 1), 1, 1, true, 100);
        assertEquals(0, batchResults.getCurrentRowNumber());
        batchResults.next();
        assertEquals(1, batchResults.getCurrentRowNumber());
        batchResults.next();
        assertEquals(2, batchResults.getCurrentRowNumber());
        assertFalse(batchResults.next());
        assertEquals(2, batchResults.getCurrentRowNumber());
    }
    
    public void testSetException() throws Exception {
    	BatchResults batchResults = new BatchResults(createBatch(1, 1), 1, 1, false, 100);
    	MockBatchFetcher batchFetcher = new MockBatchFetcher();
    	batchResults.setBatchFetcher(batchFetcher);
    	batchFetcher.throwException();
    	batchResults.next();
        try {
            batchResults.hasNext();
            fail("Expected exception, but did not get.");
        }catch(SQLException e) {           
        }
    }
    
    public void testBatching() throws Exception {               
        BatchResults batchResults = new BatchResults(createBatch(1, 10), 1, 10, false, 10);
        MockBatchFetcher batchFetcher = new MockBatchFetcher(60);
        batchResults.setBatchFetcher(batchFetcher);
        
        for(int i=0; i<45; i++) {    
            assertTrue(batchResults.next());
        }
        
        for(int i=0; i<44; i++) {
            assertTrue(batchResults.previous());
            assertEquals(new Integer(44 - i), batchResults.getCurrentRow().get(0));
        }
        
        // verify batch calls
        checkResults(new int[][] { 
            // going forwards - end > begin
            new int[] { 11, 20 },
            new int[] { 21, 30 },
            new int[] { 31, 40 },
            new int[] { 41, 50 },
            // going backwards - begin > end
            // last 3 batches were saved, only need the first 2 again
            new int[] { 20, 11 },
            new int[] { 10, 1 },
        }, batchFetcher.batchCalls);        
        
        assertTrue(batchResults.absolute(50));
        assertEquals(new Integer(50), batchResults.getCurrentRow().get(0));
    }
    
    private void checkResults(int[][] expectedCalls, List<int[]> batchCalls) {
        assertEquals(expectedCalls.length, batchCalls.size());
        
        for(int i=0; i<batchCalls.size(); i++) {
            int[] range = batchCalls.get(i);
            int[] expected = expectedCalls[i];
            assertEquals("On call " + i + " expected different begin", expected[0], range[0]);
            assertEquals("On call " + i + " expected different end", expected[1], range[1]);
        }
    }
        
}
