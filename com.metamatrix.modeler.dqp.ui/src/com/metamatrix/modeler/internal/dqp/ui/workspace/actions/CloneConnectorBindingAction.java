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

package com.metamatrix.modeler.internal.dqp.ui.workspace.actions;

import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;

import com.metamatrix.common.config.api.ConnectorBinding;
import com.metamatrix.modeler.dqp.DqpPlugin;
import com.metamatrix.modeler.dqp.ui.DqpUiConstants;
import com.metamatrix.modeler.dqp.ui.DqpUiPlugin;
import com.metamatrix.modeler.dqp.util.ModelerDqpUtils;
import com.metamatrix.modeler.internal.dqp.ui.workspace.dialogs.CloneConnectorBindingDialog;
import com.metamatrix.ui.internal.util.UiUtil;


/** 
 * @since 5.0
 */
public class CloneConnectorBindingAction extends ConfigurationManagerAction {

    /** 
     * 
     * @since 5.0
     */
    public CloneConnectorBindingAction() {
        super(DqpUiConstants.UTIL.getString("CloneConnectorBindingAction.label")); //$NON-NLS-1$
    }
    
    /**
     *  
     * @see org.eclipse.jface.action.IAction#run()
     * @since 5.0
     */
    @Override
    public void run() {
        //System.out.println("  CloneConnectorBindingAction.run()   ====>>> ");
        // Get Selection
        ConnectorBinding theBinding = (ConnectorBinding)getSelectedObject();
        
        if( theBinding != null ) {
            try {
                theBinding = DqpPlugin.getWorkspaceConfig().
                cloneConnectorBinding(theBinding, 
                                      generateUniqueBindingName(theBinding.getName()), 
                                      false);
                CloneConnectorBindingDialog dialog = new CloneConnectorBindingDialog(UiUtil.getWorkbenchShellOnlyIfUiThread(), theBinding) {
                    
                    /** 
                     * @see com.metamatrix.ui.internal.widget.ExtendedTitleAreaDialog#close()
                     * @since 5.5.3
                     */
                    @Override
                    public boolean close() {
                        if (getReturnCode() == Window.OK) {
                            ConnectorBinding newBinding = getNewConnectorBinding();
                            if( newBinding != null ) {
                                //System.out.println("  NewConnectorBindingAction.run() NEW BINDING = " + newBinding.getName());
                                try {
                                    getConfigurationManager().createConnectorBinding(newBinding, getNewConnectorBindingName());
                                } catch (Exception error) {
                                    DqpUiPlugin.showErrorDialog(getShell(), error);
                                    return false;
                                }
                            }
                        }
                        return super.close();
                    }
                };
    
                dialog.open();
            } catch (final Exception error) {
                UiUtil.runInSwtThread(new Runnable() {

                    public void run() {
                        DqpUiPlugin.showErrorDialog(Display.getCurrent().getActiveShell(), error);
                    }
                }, false);
            }
        }
    }
    
    private String generateUniqueBindingName(String originalName) {
        String proposedName = originalName;

        boolean validName = false;
        int iVersion = 1;
        
        while(!validName) { 
            
            if (!ModelerDqpUtils.isUniqueBindingName(proposedName)) {
                proposedName = originalName + "_" + iVersion; //$NON-NLS-1$
                iVersion++;
            } else {
                validName = true;
            }
        }
        
        return proposedName;
    }

    /**
     *  
     * @see com.metamatrix.modeler.internal.dqp.ui.workspace.actions.ConfigurationManagerAction#setEnablement()
     * @since 5.0
     */
    @Override
    protected void setEnablement() {
        boolean result = false;
        if( !isMultiSelection() && !isEmptySelection() ) {
            Object selectedObject = getSelectedObject();
            if( selectedObject instanceof ConnectorBinding) {
                result = true;
            }
        }
        
        setEnabled(result);
    }
}
