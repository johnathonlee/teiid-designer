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
import com.metamatrix.common.log.LogManager;
import com.metamatrix.query.util.LogConstants;

/**
 * This instruction marks the current document in progress as
 * finished.
 */
public class EndDocumentInstruction extends ProcessorInstruction {

    /**
     * Constructor for EndBlockInstruction.
     */
    public EndDocumentInstruction() {
        super();
    }

    /**
     * @see ProcessorInstruction#process(ProcessorEnvironment)
     */
    public XMLContext process(XMLProcessorEnvironment env, XMLContext context)
    throws BlockedException, MetaMatrixComponentException, MetaMatrixProcessingException{

        // Only process this instruction if there are no recursive programs in the
        // program stack (don't want to start a new doc in the middle of 
        // recursive processing)
        if (!env.isRecursiveProgramInStack()) {
            LogManager.logTrace(LogConstants.CTX_XML_PLAN, "ending document"); //$NON-NLS-1$
            env.getDocumentInProgress().markAsFinished();
        }
            
        env.incrementCurrentProgramCounter();
        return context;
    }

    public String toString() {
        return "END DOC"; //$NON-NLS-1$
    }

    public Map getDescriptionProperties() {
        Map props = new HashMap();
        props.put(PROP_TYPE, "END DOCUMENT"); //$NON-NLS-1$
        
        return props;
    }

}
