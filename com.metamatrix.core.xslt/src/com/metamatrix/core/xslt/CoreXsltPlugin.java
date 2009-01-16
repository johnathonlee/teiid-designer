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

package com.metamatrix.core.xslt;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ResourceBundle;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamSource;
import org.jdom.Document;
import org.jdom.output.XMLOutputter;
import com.metamatrix.core.BundleUtil;
import com.metamatrix.core.MetaMatrixCoreException;
import com.metamatrix.core.util.ArgCheck;

/**
 * CoreXsltPlugin
 */
public class CoreXsltPlugin {

    public static final String PLUGIN_ID = "com.metamatrix.core.xslt" ; //$NON-NLS-1$

	public static final BundleUtil Util = new BundleUtil(PLUGIN_ID,
	                                                     PLUGIN_ID + ".i18n", ResourceBundle.getBundle(PLUGIN_ID + ".i18n")); //$NON-NLS-1$ //$NON-NLS-2$

    public CoreXsltPlugin() {
    }

    /**
     * Create an instance of TransformerFactory.
     * @return a new TransformerFactory
     * @throws TransformerFactoryConfigurationError if there is a problem configuring the factory
     */
    public static TransformerFactory createFactory() throws TransformerFactoryConfigurationError {
//        final TransformerFactory factory = TransformerFactory.newInstance();
//        final TransformerFactory factory = new oracle.xml.jaxp.JXSAXTransformerFactory();
//        final TransformerFactory factory = new org.apache.xalan.processor.TransformerFactoryImpl();
        final TransformerFactory factory = new net.sf.saxon.TransformerFactoryImpl();
        //factory.setAttribute(FeatureKeys.RECOVERY_POLICY, new Integer(Controller.RECOVER_SILENTLY));
        return factory;
    }

    /**
     * Transform the supplied source document (or fragment of the source document, if the URI of
     * a fragment root is specified) using the XSLT.
     * @param sourceDoc the source document; may not be null
     * as the root of the fragment to be transformed; may be null if the whole document is to be transformed.
     * @param output the stream to which the transformed
     */
    public static Source createSource(final Document sourceDoc) throws MetaMatrixCoreException {
        ArgCheck.isNotNull(sourceDoc);

        /*
        * Here we convert the JDOM Document to an outputStream and feed it
        * to the XSLT processor. it is possible to conver the JDOM Document
        * to a DOM document, but using streams is just as fast when using
        * a Xalan processor.  This is due to the processing required to
        * convert a DOM Document into the proper internal representation
        * that is required by the Xalan processor (Our default XSLT processor).
        */

        // Read in the document and convert to something that can be used as a StreamSource
        try {

            // Get a means for output of the JDOM Document
            final XMLOutputter xmlOutputter = new XMLOutputter();

            // Output to the input stream
            final StringWriter sourceOut = new StringWriter();
            xmlOutputter.output(sourceDoc, sourceOut);
            StringReader transformSource = new StringReader(sourceOut.toString());
            // Create the source ...
            return new StreamSource(transformSource);
        } catch ( Throwable e ) {
            final String msg = CoreXsltPlugin.Util.getString("CoreXsltPlugin.Error_loading_the_XSLT_transform"); //$NON-NLS-1$
            throw new MetaMatrixCoreException(e,msg);
        }
    }

}
