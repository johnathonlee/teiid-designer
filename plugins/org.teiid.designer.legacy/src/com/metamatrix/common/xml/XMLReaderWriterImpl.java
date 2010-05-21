/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */

package com.metamatrix.common.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.jdom.Document;
import org.jdom.output.XMLOutputter;
import org.jdom.JDOMException;

import org.teiid.internal.core.xml.JdomHelper;


/** 
* This implementation will use the JDOMhelper to read in XML files and will
* use the JDOM utility to write out JDOM XML files.
*/
public class XMLReaderWriterImpl implements XMLReaderWriter{

    public static final int DEFAULT_INDENT_SIZE = 4;
    public static final boolean DEFAULT_USE_NEW_LINES = true;
    
    private int indent = DEFAULT_INDENT_SIZE;
    private boolean newLines = DEFAULT_USE_NEW_LINES;

    /**
    * This method will write a JDOM Document to an OutputStream.
    *
    * @param doc the JDOM document to be written to the OutputStream
    * @param stream the output stream to be written to.
    * @throws IOException if there is a problem writing to the OutputStream
    */
    public void writeDocument(Document doc, OutputStream stream) throws IOException{
        StringBuffer indentBuffer = new StringBuffer();
        for (int i = 0; i < indent; i++) {
            indentBuffer.append(" "); //$NON-NLS-1$
        }
        XMLOutputter outputter = new XMLOutputter(JdomHelper.getFormat(indentBuffer.toString(), newLines));
        
        outputter.output(doc, stream);
        stream.flush();
        stream.close();
        
    }
    
    /**
    * This method will write a JDOM Document to an OutputStream.
    *
    * @param stream the input stream to read the XML document from.
    * @return the JDOM document reference that represents the XML text in the
    * InputStream.
    * @throws IOException if there is a problem reading from the InputStream
    * @throws JDOMException if the InputStream does not represent a JDOM 
    * compliant XML document.
    */
    public Document readDocument(InputStream stream) throws JDOMException, IOException{
        return JdomHelper.buildDocument(stream);
    }
    
    /**
    * This method will set the indent size of all JDOM Documents that are
    * written using this object.
    *
    * @param indent the number of spaces to indent the XML heirarchy in the 
    * output files.
    */
    public void setIndentSize(int indent) {
        this.indent = indent;
    }
    
    /**
    * This method will set whether or not new Lines are used to mimic the 
    * hierarchal structure of all JDOM Documents that are
    * written using this object.
    *
    * @param newLines whether or not to include new line chars in output files.
    */
    public void setUseNewLines(boolean newLines) {
        this.newLines = newLines;
    }
    
}
