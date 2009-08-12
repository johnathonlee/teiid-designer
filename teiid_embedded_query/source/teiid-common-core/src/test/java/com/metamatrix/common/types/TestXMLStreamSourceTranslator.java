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

package com.metamatrix.common.types;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.xml.transform.stream.StreamSource;

import com.metamatrix.core.util.ObjectConverterUtil;

import junit.framework.TestCase;


/** 
 * XML StreamSource Translator.
 */
public class TestXMLStreamSourceTranslator extends TestCase {

    private static final String sourceXML = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +  //$NON-NLS-1$
        "<Books:bookCollection xmlns:Books=\"http://www.metamatrix.com/XMLSchema/DataSets/Books\">\r\n" +  //$NON-NLS-1$
        "   <book isbn=\"0-7356-0877-6\">\r\n" +  //$NON-NLS-1$
        "      <title>After the Gold Rush</title>\r\n" +  //$NON-NLS-1$
        "      <subtitle>Creating a True Profession of Software Engineering</subtitle>\r\n" +  //$NON-NLS-1$
        "      <edition>1</edition>\r\n" +  //$NON-NLS-1$
        "      <authors>\r\n" +  //$NON-NLS-1$
        "         <author>McConnell</author>\r\n" +  //$NON-NLS-1$
        "      </authors>\r\n" +  //$NON-NLS-1$
        "      <publishingInformation>\r\n" +  //$NON-NLS-1$
        "         <publisher>Microsoft Press</publisher>\r\n" +  //$NON-NLS-1$
        "         <publishDate>1999</publishDate>\r\n" +  //$NON-NLS-1$
        "      </publishingInformation>\r\n" +  //$NON-NLS-1$
        "   </book>\r\n" +  //$NON-NLS-1$
        "   <book isbn=\"1-55615-484-4\">\r\n" +  //$NON-NLS-1$
        "      <title>Code Complete</title>\r\n" +  //$NON-NLS-1$
        "      <subtitle>A Practical Handbook of Software Construction</subtitle>\r\n" +  //$NON-NLS-1$
        "      <edition>1</edition>\r\n" +  //$NON-NLS-1$
        "      <authors>\r\n" +  //$NON-NLS-1$
        "         <author>McConnell</author>\r\n" +  //$NON-NLS-1$
        "      </authors>\r\n" +  //$NON-NLS-1$
        "      <publishingInformation>\r\n" +  //$NON-NLS-1$
        "         <publisher>Microsoft Press</publisher>\r\n" +  //$NON-NLS-1$
        "         <publishDate>1993</publishDate>\r\n" +  //$NON-NLS-1$
        "      </publishingInformation>\r\n" +  //$NON-NLS-1$
        "   </book>\r\n" +  //$NON-NLS-1$
        "   <book isbn=\"1-556-15900-5\">\r\n" +  //$NON-NLS-1$
        "      <title>Rapid Development</title>\r\n" +  //$NON-NLS-1$
        "      <subtitle>Taming Wild Software Schedules</subtitle>\r\n" +  //$NON-NLS-1$
        "      <edition>1</edition>\r\n" +  //$NON-NLS-1$
        "      <authors>\r\n" +  //$NON-NLS-1$
        "         <author>McConnell</author>\r\n" +  //$NON-NLS-1$
        "      </authors>\r\n" +  //$NON-NLS-1$
        "      <publishingInformation>\r\n" +  //$NON-NLS-1$
        "         <publisher>Microsoft Press</publisher>\r\n" +  //$NON-NLS-1$
        "         <publishDate>1996</publishDate>\r\n" +  //$NON-NLS-1$
        "      </publishingInformation>\r\n" +  //$NON-NLS-1$
        "   </book>\r\n" + //$NON-NLS-1$
        "</Books:bookCollection>"; //$NON-NLS-1$
    /*
     * Test method for 'com.metamatrix.common.types.XMLStreamSourceTranslator.getReader()'
     */
    public void testGetReader() throws Exception {        
        XMLStreamSourceTranslator translator = new XMLStreamSourceTranslator(new StreamSource(new StringReader(sourceXML)),  new Properties());
        Reader reader = translator.getReader();
        assertEquals(sourceXML, ObjectConverterUtil.convertToString(reader));
    }

    public void testCharInput() throws Exception {        
        XMLStreamSourceTranslator translator = new XMLStreamSourceTranslator(sourceXML.toCharArray(),  new Properties());
        Reader reader = translator.getReader();
        assertEquals(sourceXML, ObjectConverterUtil.convertToString(reader));
    }
    
    public void testXMLReaderFactory() throws Exception {        
        XMLStreamSourceTranslator translator = new XMLStreamSourceTranslator(new XMLReaderFactory() {
            public Reader getReader() throws IOException {
                return new StringReader(sourceXML);
            }            
        }, new Properties());
        Reader reader = translator.getReader();
        assertEquals(sourceXML, ObjectConverterUtil.convertToString(reader));
    }    
    
    /*
     * Test method for 'com.metamatrix.common.types.XMLStreamSourceTranslator.getInputStream()'
     */
    public void testGetInputStream() throws Exception {
        XMLStreamSourceTranslator translator = new XMLStreamSourceTranslator(new StreamSource(new ByteArrayInputStream(sourceXML.getBytes())),  new Properties());
        InputStream stream = translator.getInputStream();
        ByteArrayOutputStream out = getCoontents(stream);       
        compareDocuments(sourceXML, new String(out.toByteArray()));
    }

    private ByteArrayOutputStream getCoontents(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        int chr = stream.read();
        while(chr != -1) {
            out.write((byte)chr);
            chr = stream.read();
        }
        return out;
    }

    public void testStreamSourceWithStream() throws Exception {
        XMLStreamSourceTranslator translator = new XMLStreamSourceTranslator(new StreamSource(new StringReader(sourceXML)),  new Properties());
        InputStream stream = translator.getInputStream();
        ByteArrayOutputStream out = getCoontents(stream);       
        compareDocuments(sourceXML, new String(out.toByteArray()));
    }    
    
    /*
     * Test method for 'com.metamatrix.common.types.XMLStreamSourceTranslator.getSource()'
     */
    public void testGetSource() throws Exception {
        StreamSource src = new StreamSource(new StringReader(sourceXML));
        XMLStreamSourceTranslator translator = new XMLStreamSourceTranslator(src, new Properties());
        assertTrue(translator.getSource() instanceof StreamSource);
        StreamSource s = (StreamSource)translator.getSource();
        Reader reader = s.getReader();
        assertEquals(sourceXML, ObjectConverterUtil.convertToString(reader));
    }

    /*
     * Test method for 'com.metamatrix.common.types.BaseXMLTranslator.getString()'
     */
    public void testGetString() throws Exception {
        XMLStreamSourceTranslator translator = new XMLStreamSourceTranslator(sourceXML,  new Properties());
        assertEquals(sourceXML, translator.getString());

        // in the case of the stream source there is optimization that 
        // if a string is used to xmlize then same string is returned.
        assertEquals(sourceXML, translator.getString());
    }

    private void compareDocuments(String expectedDoc, String actualDoc) {
        StringTokenizer tokens1 = new StringTokenizer(expectedDoc, "\r\n"); //$NON-NLS-1$
        StringTokenizer tokens2 = new StringTokenizer(actualDoc, "\n");//$NON-NLS-1$
        while(tokens1.hasMoreTokens()){
            String token1 = tokens1.nextToken().trim();
            if(!tokens2.hasMoreTokens()){
                fail("XML doc mismatch: expected=" + token1 + "\nactual=none");//$NON-NLS-1$ //$NON-NLS-2$
            }
            String token2 = tokens2.nextToken().trim();
            assertEquals("XML doc mismatch: ", token1, token2); //$NON-NLS-1$
        }
        if(tokens2.hasMoreTokens()){
            fail("XML doc mismatch: expected=none\nactual=" + tokens2.nextToken().trim());//$NON-NLS-1$
        }
    }    
}
