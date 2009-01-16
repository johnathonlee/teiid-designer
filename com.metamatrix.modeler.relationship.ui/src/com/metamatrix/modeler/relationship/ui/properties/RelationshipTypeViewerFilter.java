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

package com.metamatrix.modeler.relationship.ui.properties;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import com.metamatrix.metamodels.relationship.RelationshipFolder;
import com.metamatrix.metamodels.relationship.RelationshipPackage;
import com.metamatrix.metamodels.relationship.RelationshipType;
import com.metamatrix.modeler.core.workspace.ModelResource;
import com.metamatrix.modeler.internal.ui.viewsupport.ModelUtilities;
import com.metamatrix.modeler.relationship.ui.UiConstants;

/**
 * RelationshipTypeViewerFilter
 */
public class RelationshipTypeViewerFilter extends ViewerFilter {

    /**
     * Construct an instance of RelationshipModelViewerFilter.
     * 
     */
    public RelationshipTypeViewerFilter() {
        super(); 
    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.viewers.ViewerFilter#select(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
     */
    @Override
    public boolean select(Viewer viewer, Object parentElement, Object element) {
        if ( element instanceof IContainer ) {
            return true;
        }
        
        if ( element instanceof IFile ) {
            if ( ModelUtilities.isModelFile((IFile) element) ) {
                try {
                    ModelResource resource = ModelUtilities.getModelResource((IFile) element, false);
                    if ( resource != null && resource.getPrimaryMetamodelDescriptor() != null ) {
                        if ( RelationshipPackage.eNS_URI.equals(resource.getPrimaryMetamodelDescriptor().getNamespaceURI()) ) {
                            return true;
                        }
                    }
                } catch (Exception e) {
                    UiConstants.Util.log(e);
                }
            }
        }
        
        if ( element instanceof RelationshipType ) {
            return true;
        }
        
        if ( element instanceof RelationshipFolder ) {
            return true;
        }

        if ( element instanceof RelationshipTypeFolder ) {
            return true;
        }

        return false;
    }

}
