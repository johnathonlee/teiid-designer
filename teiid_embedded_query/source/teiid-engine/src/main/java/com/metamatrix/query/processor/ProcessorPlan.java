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

package com.metamatrix.query.processor;

import java.util.Collection;
import java.util.List;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.MetaMatrixProcessingException;
import com.metamatrix.common.buffer.*;
import com.metamatrix.query.util.CommandContext;

/**
 * <p>This class represents a processor plan.  It is generic in that it 
 * abstracts the interface to the plan by the processor, meaning that the
 * actual implementation of the plan or the types of processing done by the
 * plan is not important to the processor.</p>
 * <p>All the implementations of this interface need to implement {@link #clone}
 * method. The plan is only clonable in the pre or post-processing stage, not
 * during the processing state (things like program state, result sets, etc).
 * It's only safe to clone in between query processings.  In other words, it's
 * only safe to call {@link #clone} before the call to {@link #open} or after
 * the call to {@link #close}.
 * </p>
 */
public interface ProcessorPlan extends Cloneable, Describable {

	/**
	 * Initialize the plan with some required pieces of data for making 
	 * queries.  The data manager is used to make queries and the processorID
	 * must be passed with the request so the data manager can find the 
	 * processor again.
	 * 
	 * @param context Process execution context
	 * @param dataMgr Data manager reference
     * @param bufferMgr Buffer manager reference
	 */
	void initialize(CommandContext context, ProcessorDataManager dataMgr, BufferManager bufferMgr);
	
    /**
     * Get all warnings found while processing this plan.  These warnings may
     * be detected throughout the plan lifetime, which means new ones may arrive
     * at any time.  This method returns all current warnings and clears 
     * the current warnings list.  The warnings are in order they were detected.
     * @return Current list of warnings, never null
     */
    List<Exception> getAndClearWarnings();
    
    /**
     * Reset a plan so that it can be processed again.
     */
    void reset();
    
    /**
     * Get list of resolved elements describing output columns for this plan.
     * @return List of SingleElementSymbol
     */
    List getOutputElements();
    
    /**
     * Get the processor context, which can be modified.
     * @return context object
     */
    CommandContext getContext();
    
    /**
     * Open the plan for processing.
     * @throws MetaMatrixComponentException
     */
    void open() throws MetaMatrixComponentException, MetaMatrixProcessingException;
    
    /**
     * Get a batch of results or possibly an Exception.
     * @return Batch of results
     * @throws BlockedException indicating next batch is not available yet
     * @throws MetaMatrixComponentException for non-business rule exception
     * @throws MetaMatrixProcessingException for business rule exception, related
     * to user input or modeling
     */
    TupleBatch nextBatch() throws BlockedException, MetaMatrixComponentException, MetaMatrixProcessingException;

    /**
     * Close the plan after processing.
     * @throws MetaMatrixComponentException
     */
    void close() throws MetaMatrixComponentException;
    
	
	/**
	 * Return a safe clone of the ProcessorPlan.  A ProcessorPlan may only be
	 * safely cloned in between processings.  That is, it is only safe to clone
	 * a plan before it is {@link #open opened} or after it is {@link #close
	 * closed}.
	 * @return safe clone of this ProcessorPlan, as long as it is not open for
	 * processing
	 */
	Object clone();
    
    /**
     * Finds all nested plans and returns them.
     * @return List of ProcessorPlan 
     * @since 4.2
     */
    Collection getChildPlans();
}
