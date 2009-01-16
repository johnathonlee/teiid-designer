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

package com.metamatrix.ui.product;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;

import com.metamatrix.ui.internal.product.WorkbenchState;


/** 
 * @since 4.3
 */
public class DefaultProductCharacteristics implements IProductCharacteristics {
    private WorkbenchState defaultWorkbenchState;

    /** 
     * 
     * @since 4.3
     */
    public DefaultProductCharacteristics() {
        super();
    }

    /** 
     * @see com.metamatrix.ui.product.IProductCharacteristics#workspaceLocationExposed()
     * @since 4.3
     */
    public boolean workspaceLocationExposed() {
        return true;
    }

    /** 
     * @see com.metamatrix.ui.product.IProductCharacteristics#getPrimaryNavigationViewId()
     * @since 4.3
     */
    public String getPrimaryNavigationViewId() {
        return null;
    }
    
    /** 
     * @see com.metamatrix.ui.product.IProductCharacteristics#getDefaultPerspectiveId()
     * @since 5.0
     */
    public String getDefaultPerspectiveId() {
        return null;
    }

    /** 
     * @see com.metamatrix.ui.product.IProductCharacteristics#getHiddenProject()
     * @since 4.3
     */
    public IProject getHiddenProject() {
        return getHiddenProject(true);
    }

    /** 
     * @see com.metamatrix.ui.product.IProductCharacteristics#getHiddenProject(boolean)
     * @since 4.3
     */
    public IProject getHiddenProject(boolean theCreateProjectFlag) {
        return null;
    }

   /** 
     * @see com.metamatrix.ui.product.IProductCharacteristics#isHiddenProjectCentric()
     * @since 4.3
     */
    public boolean isHiddenProjectCentric() {
        return false;
    }
    
    public IAction getProductAction(String productActionID) {
        return null;
    }
    
    public IAction getRetargetableAction(String retargetableActionID, IWorkbenchWindow theWindow) {
        return null;
    }
    
    /** 
     * @see com.metamatrix.ui.product.IProductCharacteristics#getCreateHiddenProjectWizardPage()
     * @since 4.4
     */
    public IWizardPage getCreateHiddenProjectWizardPage() {
        return null;
    }
    
    /** 
     * @see com.metamatrix.ui.product.IProductCharacteristics#getWorkbenchState()
     * @since 5.0
     */
    public WorkbenchState getWorkbenchState() {
        if( defaultWorkbenchState == null ) {
            defaultWorkbenchState = new WorkbenchState();
        }
        return defaultWorkbenchState;
    }

    /** 
     * @see com.metamatrix.ui.product.IProductCharacteristics#getFileMruMenu()
     * @since 5.0
     */
    public ContributionItem getFileMruMenu() {
        return null;
    }

    /** 
     * @see com.metamatrix.ui.product.IProductCharacteristics#getRootWorkspaceContext()
     * @since 5.0
     */
    public Object getRootWorkspaceContext() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    /** 
     * @see com.metamatrix.ui.product.IProductCharacteristics#handlePartEvent(int, org.eclipse.ui.IWorkbenchPartReference)
     * @since 5.0
     */
    public void handlePartEvent(int theEventID,
                                IWorkbenchPartReference theRef) {
        // no default implementation
    }

    /** 
     * @see com.metamatrix.ui.product.IProductCharacteristics#handlePerspectiveEvent(int, org.eclipse.ui.IWorkbenchPage, org.eclipse.ui.IPerspectiveDescriptor)
     * @since 5.0
     */
    public void handlePerspectiveEvent(int theEventID,
                                       IWorkbenchPage thePage,
                                       IPerspectiveDescriptor thePerspective) {
        // no default implementation
    }
    
    /**
     *  
     * @see com.metamatrix.ui.product.IProductCharacteristics#getNewModelInput(org.eclipse.jface.viewers.ISelection)
     * @since 5.0
     */
    public Object getNewModelInput(ISelection theSelection) {
        return null;
    }

    /**
     *  
     * @see com.metamatrix.ui.product.IProductCharacteristics#postProcess(java.lang.Object, org.eclipse.swt.widgets.Shell)
     * @since 5.0
     */
    public boolean postProcess(Object theSomeObject,
                               Shell theShell) {
        // Default implementation is a pass-through:  TRUE
        return true;
    }

    /**
     *  
     * @see com.metamatrix.ui.product.IProductCharacteristics#preProcess(java.lang.Object, org.eclipse.swt.widgets.Shell)
     * @since 5.0
     */
    public boolean preProcess(Object theSomeObject,
                              Shell theShell) {
        // Default implementation is a pass-through:  TRUE
        return true;
    }
    

    /**
     * @see com.metamatrix.ui.product.IProductCharacteristics#getObjectInfo(java.lang.Object, int)
     * @since 5.0
     */
    public Object getObjectInfo( int infoType, 
                                  Object theSomeObject ) {
        // Default implementation is a no-op
        return null;
    }

    /**
     * @see com.metamatrix.ui.product.IProductCharacteristics#setObjectInfo(java.lang.Object, int, java.lang.Object)
     * @since 5.0
     */
    public void setObjectInfo(int infoType, 
                                Object theSomeObject, 
                                Object theValue) {
        // Default implementation is a no-op        
    }

}
