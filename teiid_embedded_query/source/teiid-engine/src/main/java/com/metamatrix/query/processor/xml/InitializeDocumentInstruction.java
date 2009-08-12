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
import com.metamatrix.query.mapping.xml.MappingNodeConstants;
import com.metamatrix.query.util.LogConstants;
import com.metamatrix.query.util.XMLFormatConstants;

/**
 * Should be the first instruction of any document.  Initializes the encoding
 * and formatting of a document in the XML results.
 */
public class InitializeDocumentInstruction extends ProcessorInstruction {

    private String encoding;
    private boolean isFormatted;

    /**
     * Constructor for InitializeDocumentInstruction.
     */
    public InitializeDocumentInstruction() {
        super();
        this.encoding = MappingNodeConstants.Defaults.DEFAULT_DOCUMENT_ENCODING;
        this.isFormatted = MappingNodeConstants.Defaults.DEFAULT_FORMATTED_DOCUMENT.booleanValue();
    }

    /**
     * Constructor for InitializeDocumentInstruction.
     * @param encoding The encoding of the XML results doc
     * @param isFormatted true means human-readable form, false means
     * compacted machine-readable form
     */
    public InitializeDocumentInstruction(String encoding, boolean isFormatted) {
        super();
        this.encoding = encoding;
        this.isFormatted = isFormatted;
    }

    /**
     * Sets the format in which XML results are returned
     * @param isFormatted true means human-readable form, false means
     * compacted machine-readable form
     */    
	void setXMLFormat(boolean isFormatted) {
		this.isFormatted = isFormatted;
	}

    /**
     * @see ProcessorInstruction#getCapabilities(ProcessorEnvironment, DocumentInProgress)
     */
    public XMLContext process(XMLProcessorEnvironment env, XMLContext context)
        throws BlockedException, MetaMatrixComponentException, MetaMatrixProcessingException{
            
        LogManager.logTrace(LogConstants.CTX_XML_PLAN, "DOC begin"); //$NON-NLS-1$

        // Only process this instruction if there are no recursive programs in the
        // program stack (don't want to start a new doc in the middle of 
        // recursive processing)
        if (!env.isRecursiveProgramInStack()) {
            DocumentInProgress doc = new SAXDocumentInProgress();
            //DocumentInProgress doc = new JDOMDocumentInProgress();
            env.setDocumentInProgress(doc);
                
            doc.setDocumentEncoding(encoding);

            // Override the xml format flag from the model with
            // the format specified with the user's query request, if any.
            boolean formatted = this.isFormatted;
            String xmlFormat = env.getXMLFormat();
            if(xmlFormat != null) {
                if(xmlFormat.equalsIgnoreCase(XMLFormatConstants.XML_TREE_FORMAT)) {
                    formatted = true;
                } else if (xmlFormat.equalsIgnoreCase(XMLFormatConstants.XML_COMPACT_FORMAT)) {
                    formatted = false;
                }
            }

            doc.setDocumentFormat(formatted);
        }
            
        env.incrementCurrentProgramCounter();
        return context;
    }

    public String toString() {
        return "DOC  encoding: " + encoding + ", is formatted: " + isFormatted; //$NON-NLS-1$ //$NON-NLS-2$
    }

    public Map getDescriptionProperties() {
        Map props = new HashMap();
        props.put(PROP_TYPE, "START DOCUMENT"); //$NON-NLS-1$

        props.put(PROP_ENCODING, this.encoding);
        props.put(PROP_FORMATTED, ""+this.isFormatted); //$NON-NLS-1$
                
        return props;
    }

}
