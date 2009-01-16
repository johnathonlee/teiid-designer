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

package com.metamatrix.ui.tree;

import org.eclipse.jface.viewers.ITreeContentProvider;

import com.metamatrix.ui.internal.widget.DefaultContentProvider;

/**
 * @since 4.0
 */
public abstract class AbstractTreeContentProvider extends DefaultContentProvider implements
                                                                                ITreeContentProvider {

    // ===========================================================================================================================
    // Methods

    /**
     * @see org.eclipse.jface.viewers.ITreeContentProvider#getChildren(java.lang.Object)
     * @since 5.0.1
     */
    public Object[] getChildren(Object parent) {
        return super.getElements(parent);
    }

    /** 
     * @see com.metamatrix.ui.internal.widget.DefaultContentProvider#getElements(java.lang.Object)
     * @since 5.0.1
     */
    @Override
    public Object[] getElements(Object inputElement) {
        return getChildren(inputElement);
    }
    
    /**
     * @see org.eclipse.jface.viewers.ITreeContentProvider#hasChildren(java.lang.Object)
     * @since 4.0
     */
    public boolean hasChildren(Object element) {
        return (getChildren(element).length > 0);
    }
}
