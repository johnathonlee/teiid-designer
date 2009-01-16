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
package com.metamatrix.modeler.modelgenerator.ui.wizards;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import com.metamatrix.core.util.StringUtil;
import com.metamatrix.modeler.core.workspace.ModelProject;
import com.metamatrix.modeler.core.workspace.ModelResource;
import com.metamatrix.modeler.modelgenerator.ui.ModelGeneratorUiConstants;
import com.metamatrix.modeler.modelgenerator.ui.ModelerModelGeneratorUiPlugin;
import com.metamatrix.modeler.ui.UiConstants;
import com.metamatrix.ui.internal.InternalUiConstants;
import com.metamatrix.ui.internal.util.WidgetUtil;
import com.metamatrix.ui.internal.wizard.AbstractWizard;

/**
 * @since 4.0
 */
public final class PasteSpecialUmlToRelationalWizard extends AbstractWizard
    implements INewWizard, InternalUiConstants.Widgets, StringUtil.Constants, ModelGeneratorUiConstants,
    UiConstants.ProductInfo.Capabilities {

    private static final String TITLE = Util.getString("PasteSpecialUmlToRelationalWizard.title"); //$NON-NLS-1$

    private IModelGeneratorManager modelGeneratorMgr;
    private List selectedSourceObjs;
    private ModelResource targetModelResource;
    private IdentifySubsetsWizardPage identifySubsetsPage;
    private RelationshipOptionsWizardPage relationshipOptionsPage;
    private GeneralOptionsWizardPage generalOptionsPage;
    private DatatypeOptionsWizardPage datatypeOptionsPage;
    private GeneratedKeyOptionsWizardPage generatedKeyOptionsPage;
    private CustomPropertyOptionsWizardPage customPropertyOptionsPage;
    private RefreshOptionsWizardPage refreshOptionsPage;

    /**
     * @since 4.0
     */
    public PasteSpecialUmlToRelationalWizard( ModelResource targetModelResource,
                                              List selectedSourceObjs ) {
        super(ModelerModelGeneratorUiPlugin.getDefault(), TITLE, null);
        this.modelGeneratorMgr = new ModelGeneratorManager();
        this.selectedSourceObjs = selectedSourceObjs;
        this.targetModelResource = targetModelResource;
    }

    /**
     * @see org.eclipse.jface.wizard.IWizard#performFinish()
     * @since 4.0
     */
    @Override
    public boolean finish() {
        // PasteSpecial into the Model (modelFile) with constructs from the Generator.
        final WorkspaceModifyOperation op = new WorkspaceModifyOperation() {
            @Override
            public void execute( IProgressMonitor theMonitor ) throws InvocationTargetException {
                try {
                    getModelGeneratorMgr().performMerge(theMonitor);
                    getModelGeneratorMgr().save(theMonitor);
                } catch (Exception theException) {
                    throw new InvocationTargetException(theException);
                }
            }
        };

        try {
            ProgressMonitorDialog dlg = new ProgressMonitorDialog(getShell());
            dlg.run(true, false, op);
        } catch (InvocationTargetException theException) {
            Util.log(theException);
            WidgetUtil.showError(theException);
        } catch (InterruptedException theException) {
        }

        return true;
    }

    IModelGeneratorManager getModelGeneratorMgr() {
        return this.modelGeneratorMgr;
    }

    /**
     * @see org.eclipse.ui.IWorkbenchWizard#init(org.eclipse.ui.IWorkbench, org.eclipse.jface.viewers.IStructuredSelection)
     * @since 4.0
     */
    public void init( final IWorkbench workbench,
                      final IStructuredSelection selection ) {
        this.modelGeneratorMgr = new ModelGeneratorManager();
        // GeneratorManager Options
        GeneratorManagerOptions mgrOptions = this.modelGeneratorMgr.getGeneratorManagerOptions();
        // For now, generated relationships must be placed in a selected relationships model
        mgrOptions.setRelationshipsModelOption(GeneratorManagerOptions.PUT_RELATIONSHIPS_IN_SELECTED_MODEL);

        // ------------------------------------
        // Page1: UML Source selections
        // ------------------------------------
        IProject targetProj = getProject(targetModelResource);
        String title = Util.getString("IdentifySubsetsWizardPage.pasteSpecial.title"); //$NON-NLS-1$
        String desc = Util.getString("IdentifySubsetsWizardPage.pasteSpecial.description"); //$NON-NLS-1$
        identifySubsetsPage = new IdentifySubsetsWizardPage("identifySubsetsWizardPage", //$NON-NLS-1$
                                                            title, desc, this.modelGeneratorMgr);
        identifySubsetsPage.setTreeSelections(this.selectedSourceObjs);
        // ------------------------------------
        // Page2: Relationship Model Selection
        // ------------------------------------
        relationshipOptionsPage = new RelationshipOptionsWizardPage("relationshipOptionsWizardPage", mgrOptions, targetProj, null); //$NON-NLS-1$
        // ------------------------------------
        // Page3: General Options
        // ------------------------------------
        generalOptionsPage = new GeneralOptionsWizardPage("generalOptionsWizardPage", mgrOptions); //$NON-NLS-1$
        // ------------------------------------
        // Page4: Datatype model selection
        // ------------------------------------
        datatypeOptionsPage = new DatatypeOptionsWizardPage("datatypeOptionsWizardPage", mgrOptions); //$NON-NLS-1$
        // ------------------------------------
        // Page5: Generated Key Options
        // ------------------------------------
        generatedKeyOptionsPage = new GeneratedKeyOptionsWizardPage("generatedKeyOptionsWizardPage", mgrOptions); //$NON-NLS-1$
        // ------------------------------------
        // Page6: Custom Property Options
        // ------------------------------------
        customPropertyOptionsPage = new CustomPropertyOptionsWizardPage("customPropertyOptionsWizardPage", mgrOptions); //$NON-NLS-1$

        // ------------------------------------
        // Page7: Refresh Options
        // ------------------------------------
        refreshOptionsPage = new RefreshOptionsWizardPage("refreshOptionsWizardPage", //$NON-NLS-1$
                                                          this.modelGeneratorMgr, this.targetModelResource);

        addPage(this.identifySubsetsPage);
        addPage(this.relationshipOptionsPage);
        addPage(this.generalOptionsPage);
        addPage(this.datatypeOptionsPage);
        addPage(this.generatedKeyOptionsPage);
        addPage(this.customPropertyOptionsPage);
        addPage(this.refreshOptionsPage);
        this.refreshOptionsPage.setPageComplete(false);
    }

    private IProject getProject( ModelResource modelResource ) {
        IProject result = null;
        if (modelResource != null) {
            ModelProject project = modelResource.getModelProject();
            if (project != null) {
                result = project.getProject();
            }
        }
        return result;
    }

    /**
     * @see org.eclipse.jface.wizard.IWizard#canFinish()
     * @since 4.0
     */
    @Override
    public boolean canFinish() {
        boolean canFinish = false;
        if (identifySubsetsPage.isPageComplete() && relationshipOptionsPage.isPageComplete()
            && generalOptionsPage.isPageComplete() && datatypeOptionsPage.isPageComplete()
            && generatedKeyOptionsPage.isPageComplete() && customPropertyOptionsPage.isPageComplete()
            && refreshOptionsPage.isPageComplete() && refreshOptionsPage.isVisible()) {
            canFinish = true;
        }
        return canFinish;
    }

    Composite createEmptyPageControl( final Composite parent ) {
        return new Composite(parent, SWT.NONE);
    }
}
