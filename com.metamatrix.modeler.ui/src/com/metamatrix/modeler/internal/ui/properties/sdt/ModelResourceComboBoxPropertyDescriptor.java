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

package com.metamatrix.modeler.internal.ui.properties.sdt;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.views.properties.ComboBoxPropertyDescriptor;

import org.eclipse.emf.ecore.EObject;

import com.metamatrix.modeler.core.workspace.ModelResource;
import com.metamatrix.modeler.internal.core.workspace.ModelUtil;
import com.metamatrix.modeler.internal.ui.properties.ModelObjectPropertyDescriptor;
import com.metamatrix.modeler.internal.ui.properties.ReadOnlyPropertyDescriptor;
import com.metamatrix.modeler.internal.ui.viewsupport.ModelUtilities;
import com.metamatrix.modeler.ui.UiPlugin;
import com.metamatrix.modeler.ui.editors.ModelEditorManager;

/**
 * ModelResourceComboBoxPropertyDescriptor
 */
public class ModelResourceComboBoxPropertyDescriptor extends ComboBoxPropertyDescriptor {

    private EObject owner;
    private boolean isEditable = true;
    private boolean showReadOnlyDialog = true;   

    /**
     * Construct an instance of RuntimeTypePropertyDescriptor.
     * @param id
     * @param displayName
     * @param valuesArray
     */
    public ModelResourceComboBoxPropertyDescriptor(EObject owner, Object id, String displayName, String[] valuesArray) {
        this(id, displayName, valuesArray);
        this.owner = owner;
    }

    public ModelResourceComboBoxPropertyDescriptor(Object id, String displayName, String[] valuesArray) {
        super(id, displayName, valuesArray);
    }
        
    //============================================================
    // Instance Methods
    //============================================================
    public void setShowReadOnlyDialog(boolean enable) {
        showReadOnlyDialog = enable;
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.properties.IPropertyDescriptor#getCategory()
     */
    @Override
    public String getCategory() {
        return NodePropertyDescriptor.CATEGORY;
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.properties.IPropertyDescriptor#createPropertyEditor(org.eclipse.swt.widgets.Composite)
     */
    @Override
    public CellEditor createPropertyEditor(Composite parent) {
        if (!isEditable) {
            return null;
        }

        // check failure conditions: ModelResource is null, or read-only, or not open in an editor 
        ModelResource modelResource = ModelUtilities.getModelResourceForModelObject(this.owner);
        if (modelResource == null) { // if the modelResource is null, we can't edit the properties
            return null;
        }
        if (ModelUtil.isIResourceReadOnly(modelResource.getResource())) {
            if (showReadOnlyDialog) {
                Shell shell = UiPlugin.getDefault().getCurrentWorkbenchWindow().getShell();
                MessageDialog.openError(
                    shell,
                    ReadOnlyPropertyDescriptor.READ_ONLY_TITLE,
                    ReadOnlyPropertyDescriptor.READ_ONLY_MESSAGE);
            }
            return null;
        }

        IFile file = (IFile)modelResource.getResource();
        if (file != null) {
            if (!ModelEditorManager.isOpen(file)) {
                // can't modify a property value on an EObject if it's ModelEditor is not open.
                Shell shell = UiPlugin.getDefault().getCurrentWorkbenchWindow().getShell();
                if (MessageDialog
                    .openQuestion(
                        shell,
                        ModelObjectPropertyDescriptor.OPEN_EDITOR_TITLE,
                        ModelObjectPropertyDescriptor.OPEN_EDITOR_MESSAGE)) {
                    ModelEditorManager.open(this.owner, true);

                }
                return null;
            }
        }

        return super.createPropertyEditor(parent);
    }

    public boolean isEditable() {
        return this.isEditable;
    }

    public void setEditable(boolean isEditable) {
        this.isEditable = isEditable;
    }
}
