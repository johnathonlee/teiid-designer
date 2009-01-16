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

package com.metamatrix.modeler.internal.ui.refactor;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;

import com.metamatrix.modeler.core.refactor.ResourceMoveCommand;
import com.metamatrix.modeler.ui.UiConstants;
import com.metamatrix.ui.internal.viewsupport.StatusInfo;

/**
 * ModelContainerSelectionValidator
 */
public class ModelContainerSelectionValidator 
  implements ISelectionStatusValidator {


    private StatusInfo stStatus;
    private ResourceMoveCommand command;
    
    // Strings
    private static final String NOTHING_SELECTED 
        = UiConstants.Util.getString("ModelContainerSelectionValidator.noSelection.text"); //$NON-NLS-1$
    private static final String WRONG_CLASS_ERROR_MSG 
        = UiConstants.Util.getString("ModelContainerSelectionValidator.wrongClassErrorMessage.text"); //$NON-NLS-1$  
      
    public ModelContainerSelectionValidator( ResourceMoveCommand command ) {
          super();
          this.command = command;     
                      
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.ui.dialogs.ISelectionStatusValidator#validate(java.lang.Object[])
     */
    public IStatus validate( Object[] selection ) {


        // deal with empty selection
        if ( selection == null || selection.length == 0 ) {
            return new StatusInfo(UiConstants.PLUGIN_ID, IStatus.ERROR, NOTHING_SELECTED);
        }
        
        // note: The Move command assumes single selection, so the dialog enforces it
        
        // check class
        if ( selection[ 0 ] instanceof IContainer ) {
            command.setDestination( (IContainer)selection[ 0 ] );  
            
            // let the command check everything else
            IStatus status = command.canExecute();                                                              
                 
            if ( status.isOK()  ) {
                
                stStatus =  new StatusInfo( UiConstants.PLUGIN_ID,  
                                            IStatus.OK, 
                                            "" );     //$NON-NLS-1$
            } else {
                stStatus =  new StatusInfo( UiConstants.PLUGIN_ID,  
                                            IStatus.ERROR, 
                                            status.getMessage() );      
                                
            }
        } else {
            stStatus =  new StatusInfo( UiConstants.PLUGIN_ID,     
                                        IStatus.ERROR, 
                                        WRONG_CLASS_ERROR_MSG );                              
        }
        
        return stStatus;
    }
   

    
    public void setCommand( ResourceMoveCommand command ) {
        this.command = command;
    }

}
