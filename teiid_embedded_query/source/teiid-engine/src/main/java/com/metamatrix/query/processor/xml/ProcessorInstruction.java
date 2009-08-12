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

import java.util.HashMap;
import java.util.Map;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.MetaMatrixProcessingException;
import com.metamatrix.common.buffer.BlockedException;
import com.metamatrix.query.processor.Describable;

/**
 * <p>Abstract superclass of all XML Processor Instructions.</p>
 */
public abstract class ProcessorInstruction implements Describable {

    public ProcessorInstruction() {
    } 
   
    /**
     * Allow this ProcessorInstruction to do whatever processing it needs, and to
     * in turn manipulate the running program, (via the ProcessorEnvironment
     * {@link ProcessorEnvironment#getProgramStack getProgramStack} method.)
     * A typical instruction should simply {@link Program#incrementProgramCounter increment}
     * the program counter of the current program, but specialized instructions may add
     * sub programs to the stack or not increment the counter (so that they are
     * executed again.)
     * @param env instance of ProcessorEnvironment which is being used for the processing
     * of the XML document model query
     * @throws MetaMatrixComponentException for non-business exception
     * @throws MetaMatrixProcessingException for business exception due to user input or model
     * @throws BlockedException if data is not available now but may be at a later time
     */
    public abstract XMLContext process(XMLProcessorEnvironment env, XMLContext context) 
        throws BlockedException, MetaMatrixComponentException, MetaMatrixProcessingException;
        
    /* 
     * @see com.metamatrix.query.processor.Describable#getDescriptionProperties()
     */
    public Map getDescriptionProperties() {
        Map props = new HashMap();
        return props;        
    }
}
