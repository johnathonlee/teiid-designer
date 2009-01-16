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

package com.metamatrix.modeler.internal.xml.ui.wizards;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;

import com.metamatrix.internal.core.xml.xmi.XMIHeader;
import com.metamatrix.metamodels.xml.XmlDocument;
import com.metamatrix.metamodels.xml.XmlDocumentPackage;
import com.metamatrix.modeler.internal.core.workspace.ModelUtil;
import com.metamatrix.modeler.internal.ui.explorer.ModelExplorerContentProvider;
import com.metamatrix.modeler.internal.ui.viewsupport.ModelUtilities;


/** 
 * @since 5.0
 */
public class XmlDocumentContentProvider extends ModelExplorerContentProvider {

    private Object root;
   
    public XmlDocumentContentProvider() {
        super.setShowModelContent(true);
    }
    
    public XmlDocumentContentProvider(Object rootNode) {
        this.root = rootNode;
        super.setShowModelContent(true);
    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.viewers.ITreeContentProvider#getChildren(java.lang.Object)
     */
    @Override
    public Object[] getChildren(Object parentElement) {
        if( isXmlDocumentModel(parentElement) ) {
            Object[] allChildren = super.getChildren(parentElement);
            // Now only return XmlDocuments
            List xmlDocNodes = new ArrayList();
            for( int i=0; i<allChildren.length; i++ ) {
                if( allChildren[i] instanceof XmlDocument ) {
                    xmlDocNodes.add(allChildren[i]);
                }
            }
            
            if( xmlDocNodes.isEmpty() ) {
                return new Object[0];
            }
            
            return xmlDocNodes.toArray();
        }
        
        return super.getChildren(parentElement);
    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.viewers.ITreeContentProvider#getParent(java.lang.Object)
     */
    @Override
    public Object getParent(Object element) {
        if ( element instanceof IResource ) {
            return root;
        }
        return super.getParent(element);
    }

    
//    /** 
//     * @param theRoot The root to set.
//     * @since 5.0
//     */
//    public void setRoot(Object theRoot) {
//        this.root = theRoot;
//    }
    
    private boolean isXmlDocumentModel(Object object) {
        if( object instanceof IResource && ModelUtilities.isModelFile((IResource)object) ) {
            IResource iResource = (IResource)object;
            XMIHeader header = ModelUtil.getXmiHeader(iResource);
            if( header != null ) {
                String mmURI = header.getPrimaryMetamodelURI();
                if(  mmURI != null  && mmURI.equals(XmlDocumentPackage.eNS_URI) ) {
                    return true;
                }
            }
        }
        
        return false;
    }

}
