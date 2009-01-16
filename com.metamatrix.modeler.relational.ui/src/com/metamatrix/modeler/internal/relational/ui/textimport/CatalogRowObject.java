/* ================================================================================== 
 * JBoss, Home of Professional Open Source. 
 * 
 * Copyright (c) 2000, 2009 MetaMatrix, Inc. and Red Hat, Inc. 
 * 
 * Some portions of this file may be copyrighted by other 
 * contributors and licensed to Red Hat, Inc. under one or more 
 * contributor license agreements. See the copyright.txt file in the 
 * distribution for a full listing of individual contributors. 
 * 
 * This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html 
 * ================================================================================== */ 

package com.metamatrix.modeler.internal.relational.ui.textimport;

import java.util.ArrayList;
import java.util.List;

import com.metamatrix.modeler.tools.textimport.ui.wizards.AbstractRowObject;

public class CatalogRowObject extends AbstractRowObject {
	
    private boolean valid;
    /** 
     * @param row
     * @since 4.2
     */
    public CatalogRowObject(String row) {
        super(row);
        setObjectType(RelationalRowFactory.CATALOG);
    }

    private List parseIntoSegments() {
        List segments = new ArrayList(5);
        String row = getDataString();
        // Extract the table name
    	// NAME,  "DESCRIPTION", Project\PhysicalModel\SchemaWithinModel
    	try {
    		String seg = null;
    		String restOfRow = null;
    		boolean lastSegment = false;
	        if( row != null && row.length() > 8 ) {
	        	int commaIndex = row.indexOf(COMMA); // Name should not have double quotes
	        	seg = row.substring(0, commaIndex).trim();

	        	segments.add(seg);
	        	
	        	// Description is Optional
	        	if( commaIndex == -1 )
	        		lastSegment = true;
	        	if( lastSegment ) {
	        		return segments;
	        	}
	        	restOfRow = row.substring(commaIndex + 1);
	        	
	        	
	        	// NOTE: USER NOT REQUIRED TO HAVE DESCRIPTION OR LOCATION
	        	int indexOfFirstDQuote = restOfRow.indexOf(DQUOTE);
	        	boolean hasDQuotes = indexOfFirstDQuote > -1;
	        	if( !hasDQuotes) {
	        		// find next comma index
	        		// Get Description Segment (could be EMPTY)
		        	commaIndex = restOfRow.indexOf(COMMA); // Description has double quotes
		        	if( commaIndex == -1 ) // 
		        		lastSegment = true;
		        	if( lastSegment ) {
		        		seg = restOfRow.trim();
		        		segments.add(seg);
		        		return segments;
		        	}
		        	
		        	seg = restOfRow.substring(0, commaIndex).trim();
		        	segments.add(seg);
		        	
	        	} else {
	        		// We need to find the index of last DQuote
	        		int indexOfLastDQuote = restOfRow.lastIndexOf(DQUOTE);
	        		if( indexOfLastDQuote > -1 && indexOfLastDQuote > indexOfFirstDQuote ) {
	        			seg = restOfRow.substring(indexOfFirstDQuote, indexOfLastDQuote).trim();
	        			segments.add(seg);
	        			restOfRow = restOfRow.substring(indexOfLastDQuote + 1);
	        			commaIndex = restOfRow.indexOf(COMMA);
	        			if( commaIndex == -1 )
	        				return segments;
	        		}
	        	}
	        	
	        	// WE HAVE A LOCATION 
	        	seg = restOfRow.substring(commaIndex + 1).trim();
	        	if( seg.length() > 1 )
	        		segments.add(seg);
	        }
    	} catch (Exception ex ) {
    		valid = false;
    	}
    	
    	return segments;
    }
    
    /** 
     * @see com.metamatrix.modeler.tools.textimport.ui.wizards.AbstractRowObject#parseRow()
     * @since 4.2
     */
    @Override
    public void parseRow() {
    	String rowString = getDataString();
    	try {
	        if( rowString != null && rowString.length() > 8 ) {
	            List segments = parseIntoSegments();
	        	int nSegs = segments.size();
	            if( nSegs > 0 ) {
	            	valid = true;
		            String tableName = (String)segments.get(0);
		            if( tableName != null ) {
		                setName(tableName);
		            }

	            	if( nSegs > 1 && ((String)segments.get(1)) != null ) {
	    	            String someDescription = parseDescription((String)segments.get(1));
	    	            if( someDescription != null ) {
	    	                setDescription(someDescription);
	    	            }
	            	}
	            	if( nSegs > 2 && ((String)segments.get(2)) != null ) {
	            		String location = (String)segments.get(2);
	            		if( location != null ) {
	            			setLocation(location);
	            		}
	            	}
	            }
	        }
    	} catch (Exception ex ) {
    		valid = false;
    	}
    }
    
	@Override
    public boolean isValid() {
		return valid;
	}

}
