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

package com.metamatrix.query.processor.relational;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.MetaMatrixProcessingException;
import com.metamatrix.common.buffer.IndexedTupleSource;
import com.metamatrix.query.sql.util.ValueIterator;

/**
 * A ValueIterator implementation that iterates over the TupleSource
 * results of a subquery ProcessorPlan.  The plan will
 * always have only one result column.  Constant Object values will
 * be returned, not Expressions.
 * 
 * This implementation is resettable.
 */
class TupleSourceValueIterator implements ValueIterator{

    private IndexedTupleSource tupleSourceIterator;
    private int columnIndex;
    
    TupleSourceValueIterator(IndexedTupleSource tupleSource, int columnIndex){
        this.tupleSourceIterator = tupleSource;
        this.columnIndex = columnIndex;
	}
    
	/**
	 * @see java.util.Iterator#hasNext()
	 */
	public boolean hasNext() throws MetaMatrixComponentException{
	    try {
            return tupleSourceIterator.hasNext();
        } catch (MetaMatrixProcessingException err) {
            throw new MetaMatrixComponentException(err, err.getMessage());
        }
	}

	/**
	 * Returns constant Object values, not Expressions.
	 * @see java.util.Iterator#next()
	 */
	public Object next() throws MetaMatrixComponentException{
	    try {
            return tupleSourceIterator.nextTuple().get(columnIndex);
        } catch (MetaMatrixProcessingException err) {
            throw new MetaMatrixComponentException(err, err.getMessage());
        }
	}
    
	/**
	 * Flags a reset as being needed
	 * @see com.metamatrix.query.sql.util.ValueIterator#reset()
	 */
	public void reset() {
		this.tupleSourceIterator.reset();
	}
}
