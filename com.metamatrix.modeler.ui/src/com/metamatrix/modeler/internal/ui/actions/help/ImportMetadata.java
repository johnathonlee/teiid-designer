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

package com.metamatrix.modeler.internal.ui.actions.help;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchWindow;

import com.metamatrix.modeler.internal.ui.wizards.ImportWizard;
import com.metamatrix.modeler.ui.UiConstants;
import com.metamatrix.modeler.ui.UiPlugin;
import com.metamatrix.ui.internal.eventsupport.SelectionUtilities;
import com.metamatrix.ui.internal.util.WidgetFactory;

/**
 * ImportMetadata is a hook for the active help system to run the Import Metadata wizard.
 * The action is not exposed anywhere in the Modeler ui.
 */
public class ImportMetadata extends HelpProjectAction {
    private static final String TITLE = UiConstants.Util.getString("ImportMetadata.noProjectTitle"); //$NON-NLS-1$
    private static final String NO_PROJECT_MESSAGE = UiConstants.Util.getString("ImportMetadata.noProjectMessage"); //$NON-NLS-1$
    private static final String NO_OPEN_PROJECT_MESSAGE = UiConstants.Util.getString("ImportMetadata.noOpenProjectMessage"); //$NON-NLS-1$
    /**
     * Construct an instance of ImportMetadata.
     */
    public ImportMetadata() {
        super(TITLE, NO_PROJECT_MESSAGE, NO_OPEN_PROJECT_MESSAGE);
    }


    /* (non-Javadoc)
     * @see org.eclipse.jface.action.IAction#run()
     */
    @Override
    public void run() {
        IWorkbenchWindow iww = UiPlugin.getDefault().getCurrentWorkbenchWindow();

        ISelection theSelection =  UiPlugin.getDefault().getPreviousViewSelection();
        Object obj = SelectionUtilities.getSelectedObject(theSelection);
        
        if ( obj instanceof IContainer ) {
            if ( obj instanceof IProject && ! ((IProject) obj).isOpen() ) {
                notifyNoOpenProject();
                return;
            }
             
        } else {
            theSelection = getFirstOpenProject();
        }
        
        if( !theSelection.isEmpty() ) {
            if ( theSelection instanceof IStructuredSelection ) {
                WidgetFactory.createWizardDialog(iww.getShell(), new ImportWizard(iww.getWorkbench(), (IStructuredSelection) theSelection)).open();
            }
        }
    }
    
}
