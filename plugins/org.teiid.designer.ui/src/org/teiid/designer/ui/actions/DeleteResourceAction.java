/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.teiid.designer.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.actions.SelectionListenerAction;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.teiid.designer.core.ModelerCore;
import org.teiid.designer.core.builder.ModelBuildUtil;
import org.teiid.designer.core.container.Container;
import org.teiid.designer.core.notification.util.DefaultIgnorableNotificationSource;
import org.teiid.designer.core.refactor.ModelResourceCollectorVisitor;
import org.teiid.designer.core.refactor.RefactorModelExtensionManager;
import org.teiid.designer.core.refactor.RefactorResourceEvent;
import org.teiid.designer.core.workspace.ModelResource;
import org.teiid.designer.core.workspace.ModelUtil;
import org.teiid.designer.core.workspace.ModelWorkspaceException;
import org.teiid.designer.core.workspace.WorkspaceResourceFinderUtil;
import org.teiid.designer.ui.UiConstants;
import org.teiid.designer.ui.UiPlugin;
import org.teiid.designer.ui.common.actions.AbstractAction;
import org.teiid.designer.ui.common.actions.ActionService;
import org.teiid.designer.ui.common.actions.IActionConstants;
import org.teiid.designer.ui.common.eventsupport.SelectionUtilities;
import org.teiid.designer.ui.common.util.UiUtil;
import org.teiid.designer.ui.common.widget.ListMessageDialog;
import org.teiid.designer.ui.editors.ModelEditorManager;
import org.teiid.designer.ui.event.ModelResourceEvent;
import org.teiid.designer.ui.refactor.RefactorUndoManager;
import org.teiid.designer.ui.undo.ModelerUndoManager;
import org.teiid.designer.ui.viewsupport.ModelUtilities;


/**
 * The <code>DeleteResourceAction</code> is used to close models before deletion. If the model is open in an editor, the editor is
 * closed. It delegates to Eclipse's delete resource action for the actual delete. Also it deletes EObjects by delegating to
 * {@link org.teiid.designer.ui.actions.DeleteAction}.
 *
 * @since 8.0
 */
public class DeleteResourceAction extends AbstractAction implements UiConstants {
	
	// The actual IDEWorkbenchMessages.DeleteResourceAction_jobName is an internal class, so we are borrowing the 
	// constant here.
	private static final String DeleteResourceAction_jobName = "Deleting resources"; //$NON-NLS-1$


    /** Delegate action to delete resources. */
    private SelectionListenerAction deleteResourceAction;

    private SelectionListenerAction deleteProjectsAction;

    /** Delegate action to delete EObjects. */
    private AbstractAction deleteEObjectAction;

    /** The current delegate. */
    private IAction delegateAction;

    /**
     * Constructs a <code>DeleteResourceAction</code>.
     */
    public DeleteResourceAction() {
        super(UiPlugin.getDefault());
        initDelegateActions();
        this.delegateAction = this.deleteResourceAction;

        setHoverImageDescriptor(deleteResourceAction.getHoverImageDescriptor());
        setImageDescriptor(deleteResourceAction.getImageDescriptor());
        setDisabledImageDescriptor(deleteResourceAction.getDisabledImageDescriptor());
        setText(deleteResourceAction.getText());
        setToolTipText(deleteResourceAction.getToolTipText());

        // get EObject delete action
        final ActionService actionService = UiPlugin.getDefault().getActionService(UiUtil.getWorkbenchWindowOnlyIfUiThread().getActivePage());

        try {
            deleteEObjectAction = (AbstractAction)actionService.getAction(IActionConstants.EclipseGlobalActions.DELETE);
        } catch (final CoreException theException) {
            Util.log(theException);
        }
    }

    /*
     * This method collects all model files either contained in the selected resources or
     * contained in a selected folder.
     */
    private List allSelectedAndContainedModelFiles( final List selectedResources ) {
        List allSelectedAndContainedModelFiles = Collections.EMPTY_LIST;

        // Iterator over the selected resources
        for (int size = selectedResources.size(), i = 0; i < size; i++) {
            final Object obj = selectedResources.get(i);
            if (obj instanceof IResource) {
                if (allSelectedAndContainedModelFiles.isEmpty()) allSelectedAndContainedModelFiles = new ArrayList();

                final IResource iRes = (IResource)obj;

                if (!(obj instanceof IFolder) && ModelUtilities.isModelFile(iRes)) {
                    // if the resource is not a folder and is a model file
                    // Add to the selected for deletion list
                    if (!allSelectedAndContainedModelFiles.contains(iRes)) allSelectedAndContainedModelFiles.add(iRes);
                } else if (obj instanceof IFolder || obj instanceof IProject) {
                    // If the resource is a folder (and maybe a project) get all models
                    // contained under that folder
                    final Collection folderModels = getContainedModelFiles(obj);
                    final Iterator iter = folderModels.iterator();
                    IResource nextRes = null;

                    // Iterator over the contained models and check for duplicates.
                    while (iter.hasNext()) {
                        nextRes = (IResource)iter.next();
                        if (!allSelectedAndContainedModelFiles.contains(nextRes)) allSelectedAndContainedModelFiles.add(nextRes);
                    }
                }
            }
        }
        return allSelectedAndContainedModelFiles;
    }

    /*
     * Filters modified resources to check for already existing in list, or in the list to be deleted.
     */
    private void appendDependentModelFiles( final Collection affectedResources,
                                            final Collection allAffectedResources,
                                            final Collection targetedResources,
                                            final Collection selectedObjects ) {
        // Walk through affectedResources list and add to allAffectedResources only if it doesn't contain the model resourse
        final Iterator iter = affectedResources.iterator();
        IResource mr = null;

        while (iter.hasNext()) {
            mr = (IResource)iter.next();
            if (!targetedResources.contains(mr) && 
            	!allAffectedResources.contains(mr) && 
            	!isUnderSelectedObjects(mr, selectedObjects)) {
            	allAffectedResources.add(mr);
            }
        }
    }

    /**
     * If the specified <code>IFile</code> is a model resource it is closed. If the model is open in an editor, the editor is
     * closed.
     * 
     * @param theFile the <code>IFile</code> being closed
     * @return <code>true</code> if the model closed successfully or if not a model file; <code>false</code> otherwise.
     */
    private boolean closeFile( final IFile theFile ) {
        boolean result = true;

        ModelResource model = null;
        if (ModelUtilities.isModelFile(theFile)) {
            try {
                model = ModelUtil.getModelResource(theFile, false);

                if (model != null) if (model.isLoaded()) {
                    // see if the model is open, in an initialized ModelEditor
                    if (ModelEditorManager.isOpen(theFile)) {
                        if (!ModelEditorManager.isOpenAndInitialized(theFile)) // System.out.println("[DeleteResourceAction.closeFile] model is open; about to close: "
                        // +
                        // theFile.getName() );
                        ModelEditorManager.close(theFile, false);
                    } else {
                        // jh Defect 19139:
                        // if the model is not open it might be in an Editor Reference. If so, clean that up.
                        final IEditorReference editorRef = ModelEditorManager.getEditorReferenceForFile(theFile);
                        if (editorRef != null) // System.out.println("[DeleteResourceAction.closeFile] Found an EditorReference; about to remove: "
                        // + editorRef.getName() );
                        ModelEditorManager.removeEditorReference(editorRef);
                    }
                    // }
                } else {
                    // jh Defect 19139:
                    // if the model is not loaded it might be in an Editor Reference. If so, clean that up.
                    final IEditorReference editorRef = ModelEditorManager.getEditorReferenceForFile(theFile);
                    if (editorRef != null) // System.out.println("[DeleteResourceAction.closeFile] Found an EditorReference; about to remove: "
                    // +
                    // editorRef.getName() );
                    ModelEditorManager.removeEditorReference(editorRef);
                }

            } catch (final ModelWorkspaceException theException) {
                Util.log(theException);
                result = false;
            }

            // don't close model if editor wasn't closed. user aborted close.
            if (result) try {
                if (model != null) // Need to close the model in the Explorer?
                // System.out.println("[DeleteResourceAction.closeFile] result is true; about to call closeModel(model): "
                // + model.getItemName() );
                closeModel(model);
            } catch (final ModelWorkspaceException theException) {
                Util.log(theException);
                result = false;
            }
        } else if (ModelUtil.isVdbArchiveFile(theFile)) {
            // IResource vbdResource = (IResource)theFile;

            final IEditorPart editor = UiUtil.getEditorForFile(theFile, false);
            if (editor != null) {
                if (editor.isDirty()) {
                    final String title = UiConstants.Util.getString("DeleteResourceAction.pendingChangesTitle"); //$NON-NLS-1$
                    final String message = UiConstants.Util.getString("DeleteResourceAction.pendingChangesMessage", theFile.getName()); //$NON-NLS-1$
                    result = MessageDialog.openQuestion(getShell(), title, message);
                }
                if (result) UiUtil.close(theFile, false);
            }

        }

        return result;
    }

    /**
     * Closes all models under the specified <code>IFolder</code>. If a model is open in an editor, the editor is closed.
     * 
     * @param theFolder the <code>IFolder</code> whose models are being closed
     * @return <code>true</code> if all models closed successfully; <code>false</code> otherwise.
     */
    private boolean closeFolder( final IFolder theFolder ) {
        boolean result = true;

        try {
            final IResource[] kids = theFolder.members();

            if (kids.length > 0) for (int i = 0; i < kids.length; i++)
                if (!closeResource(kids[i])) result = false;
        } catch (final CoreException theException) {
            Util.log(theException);
            result = false;
        }

        return result;
    }

    private void closeModel( final ModelResource modelResource ) throws ModelWorkspaceException {
        ModelResourceEvent event = new ModelResourceEvent(modelResource, ModelResourceEvent.CLOSING, this);
        UiPlugin.getDefault().getEventBroker().processEvent(event);
        if (modelResource.isOpen() && modelResource.isLoaded()) {
            modelResource.getEmfResource().setModified(false);
            modelResource.close();
            event = new ModelResourceEvent(modelResource, ModelResourceEvent.CLOSED, this);
            UiPlugin.getDefault().getEventBroker().processEvent(event);
        }
    }

    /**
     * Closes all models under the specified <code>IProject</code>. If a model is open in an editor, the editor is closed.
     * 
     * @param theProject the <code>IProject</code> whose models are being closed
     * @return <code>true</code> if all models closed successfully; <code>false</code> otherwise.
     */
    private boolean closeProject( final IProject theProject ) {
        boolean result = true;

        try {
            final IResource[] kids = theProject.members();

            if (kids.length > 0) for (int i = 0; i < kids.length; i++)
                if (!closeResource(kids[i])) result = false;
        } catch (final CoreException theException) {
            Util.log(theException);
            result = false;
        }

        return result;
    }

    /**
     * Closes all models under the specified <code>IResource</code>. If a model is open in an editor, the editor is closed.
     * 
     * @param theResource the <code>IResource</code> whose models are being closed
     * @return <code>true</code> if all models closed successfully; <code>false</code> otherwise.
     */
    private boolean closeResource( final IResource theResource ) {
        boolean result = true;

        if (ModelerCore.hasModelNature(theResource.getProject())) if (theResource instanceof IProject) result = closeProject((IProject)theResource);
        else if (theResource instanceof IFolder) result = closeFolder((IFolder)theResource);
        else if (theResource instanceof IFile) result = closeFile((IFile)theResource);

        return result;
    }

    private void closeResources( final List resources ) {

        boolean userOK = true;
        final List modifiedResources = ModelBuildUtil.getModifiedResources();

        final boolean started = ModelerCore.startTxn(false, false, "Closing Resources for Delete", //$NON-NLS-1$
                                                     new DefaultIgnorableNotificationSource(DeleteResourceAction.this));
        boolean succeeded = false;
        try {
            for (int size = resources.size(), i = 0; i < size && userOK; i++) {
                final Object obj = resources.get(i);

                if (obj instanceof IResource) userOK = closeResource((IResource)obj);
            }
            succeeded = true;

        } catch (final Exception err) {
            final String msg = Util.getString("DeleteResourceAction.closeResources"); //$NON-NLS-1$
            getPluginUtils().log(IStatus.ERROR, err, msg);
        } finally {
            if (started) if (succeeded) ModelerCore.commitTxn();
            else ModelerCore.rollbackTxn();
        }

        ModelBuildUtil.setModifiedResources(modifiedResources);
    }

    protected Container doGetContainer() {
        try {
            return ModelerCore.getModelContainer();
        } catch (final CoreException err) {
            final String message = Util.getString("DeleteResourceAction.doGetContainerProblemMessage"); //$NON-NLS-1$
            UiConstants.Util.log(IStatus.ERROR, err, message);
        }
        return null;
    }

    /**
     * @see org.teiid.designer.ui.common.actions.AbstractAction#doRun()
     */
    @Override
    protected void doRun() {
        final Collection deletedModelPaths = new ArrayList();
        boolean deleteApproved = false;

        if (this.delegateAction == this.deleteResourceAction) {
            // this should never be called when there is one or more selected objects that are NOT an IResource.
            // the delegate action enablement should assure this.
            final List resources = getSelectedObjects();

            /* Temp dependant model list */
            Collection depModelFiles = Collections.EMPTY_LIST;
            /*Cached list of all dependent models for all contained resources */
            final Collection allDependantModelFiles = new ArrayList();
            /* All models contained in select and under objects selected (i.e. projects and folders */
            final List allSelectedAndContainedModelFiles = allSelectedAndContainedModelFiles(resources);

            for (final Iterator iter2 = allSelectedAndContainedModelFiles.iterator(); iter2.hasNext();)
                deletedModelPaths.add(((IResource)iter2.next()).getFullPath());

            final Iterator iter = allSelectedAndContainedModelFiles.iterator();
            // Loop through all contained/objects targeted for deletion
            while (iter.hasNext()) {
                // Obtain all model file IResources for each model targeted for deletion
                depModelFiles = WorkspaceResourceFinderUtil.getResourcesThatUse((IResource)iter.next());
                // Append these to the big list using the private appendXXXX method below
                if (!depModelFiles.isEmpty()) appendDependentModelFiles(depModelFiles,
                                                                        allDependantModelFiles,
                                                                        allSelectedAndContainedModelFiles,
                                                                        resources);
            }

            if (isOkToCloseResources(resources)) {
                boolean okToDelete = true;
                // If we find dependent models, we need to warn the user
                if (!allDependantModelFiles.isEmpty()) okToDelete = warnUserAboutDependants(allDependantModelFiles);

                if (okToDelete) {
                    closeResources(resources);

                    // get the appropriate action based on the selected objects
                    final SelectionListenerAction action = getDelegateDeleteResourceAction(resources);
                    // If we make this call we can keep additional confirm dialogs from popping up
                    action.run();
                    
                    // Find all Deleting resources jobs and add listeners to count down the delete jobs, so this class
                    // can perform both Undo manager cleanup and notifyDeleted() to listeners.
                    Job[] jobs = Job.getJobManager().find(DeleteResourceAction_jobName);
                    
                    CountDownLatch latch = new CountDownLatch(jobs.length);
                    IJobChangeListener deleteFinishedJobListener = new DeleteFinishedJobListener(latch, deletedModelPaths);
                    
                    if( jobs != null && jobs.length > 0 ) {
                    	deleteApproved = true;
                    	
                    	for( Job job : jobs ) {
                    		job.addJobChangeListener(deleteFinishedJobListener);
                    	}
                    }
                    
                    
                    // if there are any dependent models AND the delete was approved (i.e. Delete jobs were started)
                    // We assume we need to clean-up and revalidate dependent models.
                    if (! allDependantModelFiles.isEmpty() && deleteApproved ) {

                    	// Call the refactor model extension manager to update for Delete resource operations.
                    	// This is to clean up both SQL transformation inputs as well as Custom Diagram components that
                    	// may be been made "stale".
                    	// NOTE: This isn't a problem with delete "EObject" because of the inherent EMF framework.
                    	RefactorModelExtensionManager.helpUpdateModelContentsForDelete(
                    			deletedModelPaths, 
                    			allDependantModelFiles, 
                    			new NullProgressMonitor());
                    	
                        // make a call to validate the dependent models so the appropriate problem markers are generated and
                        // displayed to user.

                        final WorkspaceModifyOperation op = new WorkspaceModifyOperation() {
                            @Override
                            public void execute( final IProgressMonitor theMonitor ) {
                                validateDependentResources(allDependantModelFiles, theMonitor);
                                theMonitor.done();
                            }
                        };
                        
                        try {
                            new ProgressMonitorDialog(Display.getCurrent().getActiveShell()).run(true, true, op);
                        } catch (final InterruptedException e) {
                        } catch (final InvocationTargetException e) {
                            UiConstants.Util.log(e.getTargetException());
                        }
                    }
                }
            }

        } else if (this.delegateAction == this.deleteEObjectAction) this.delegateAction.run();
    }

    /*
     * Private method used to obtain all Model file IResources contained in an IFolder
     * or IProject IResource.  This is required because if these containers are being deleted, we need to gather
     * up all objects that will go along with it and check for dependencies so we can validate all affected
     * resources.
     */
    private Collection getContainedModelFiles( final Object folderOrProject ) {
        Collection containedModelFiles = Collections.EMPTY_LIST;

        final ModelResourceCollectorVisitor visitor = new ModelResourceCollectorVisitor();
        if (folderOrProject instanceof IFolder) {

            // get the folder's project
            final IProject project = ((IFolder)folderOrProject).getProject();
            if (project != null) {
                // Insure that the project is open and is a Modeler Project
                if (project.isOpen() && ModelerCore.hasModelNature(project)) try {
                    ((IFolder)folderOrProject).accept(visitor);
                } catch (final CoreException e) {
                    UiConstants.Util.log(e);
                }

                // Get the resources and weed out non-model files
                final List pResources = visitor.getResources();
                containedModelFiles = new ArrayList(pResources.size());
                final Iterator iter = pResources.iterator();
                IResource nextRes = null;
                while (iter.hasNext()) {
                    nextRes = (IResource)iter.next();
                    if (ModelUtilities.isModelFile(nextRes)) containedModelFiles.add(nextRes);
                }
            }
        } else if (folderOrProject instanceof IProject) {
            // get the folder's project
            final IProject project = (IProject)folderOrProject;
            // Insure that the project is open and is a Modeler Project
            if (project.isOpen() && ModelerCore.hasModelNature(project)) try {
                project.accept(visitor);
            } catch (final CoreException e) {
                UiConstants.Util.log(e);
            }

            // Get the resources and weed out non-model files
            final List pResources = visitor.getResources();
            containedModelFiles = new ArrayList(pResources.size());
            final Iterator iter = pResources.iterator();
            IResource nextRes = null;
            while (iter.hasNext()) {
                nextRes = (IResource)iter.next();
                if (ModelUtilities.isModelFile(nextRes)) containedModelFiles.add(nextRes);
            }
        }

        // return on project model files.
        return containedModelFiles;
    }

    /**
     * The resources being deleted will always either be all projects or all non-projects.
     * 
     * @param resources the resources being deleted (never <code>null</code> and never empty)
     * @return the Eclipse {@link org.eclipse.ui.actions.DeleteResourceAction}
     * @since 6.0.0
     */
    private SelectionListenerAction getDelegateDeleteResourceAction( final List resources ) {
        SelectionListenerAction action = null;

        // either all projects or all non-projects
        if (resources.iterator().next() instanceof IProject) // don't change "fTestingMode" variable here as setting this to "true"
        // stops the confirmation dialog from showing
        action = this.deleteProjectsAction;
        else action = this.deleteResourceAction;

        action.selectionChanged(new StructuredSelection(resources));
        return action;
    }

    protected Shell getShell() {
        return UiPlugin.getDefault().getCurrentWorkbenchWindow().getShell();
    }

    protected void initDelegateActions() {
        this.deleteResourceAction = new org.eclipse.ui.actions.DeleteResourceAction(UiUtil.getWorkbenchWindowOnlyIfUiThread()) {
            @Override
            public void run() {
                // setting this to testing mode in essence brings back the functionality of 5.5.3. Eclipse's
                // DeleteResourceAction was changed to now use the LTK framework. When "fTestingMode" is "false" the LTK
                // framework is used. This was causing a problem problem with a Shell being disposed and had something to
                // do with our dialog that warned the user when a dependent model was being deleted. Couldn't figure out
                // a way to get the LTK stuff working so this is the workaround.
                this.fTestingMode = true;
                super.run();
            }
        };

        // when deleting projects use the default behavior of the Eclipse action
        this.deleteProjectsAction = new org.eclipse.ui.actions.DeleteResourceAction(UiUtil.getWorkbenchWindowOnlyIfUiThread());
    }

    private boolean isOkToCloseFile( final IFile theFile ) {
        boolean result = true;

        ModelResource model = null;
        if (ModelUtilities.isModelFile(theFile)) try {
            model = ModelUtil.getModelResource(theFile, false);
            if (model != null && model.isLoaded() && model.getEmfResource().isModified()) {
                // first, see if the model has pending changes that need to be saved.
                final String title = UiConstants.Util.getString("DeleteResourceAction.pendingChangesTitle"); //$NON-NLS-1$
                final String message = UiConstants.Util.getString("DeleteResourceAction.pendingChangesMessage", theFile.getName()); //$NON-NLS-1$
                result = MessageDialog.openQuestion(getShell(), title, message);
            }

        } catch (final ModelWorkspaceException theException) {
            Util.log(theException);
            result = false;
        }
        else if (ModelUtil.isVdbArchiveFile(theFile)) {

            final IEditorPart editor = UiUtil.getEditorForFile(theFile, false);
            if (editor != null) if (editor.isDirty()) {
                final String title = UiConstants.Util.getString("DeleteResourceAction.pendingChangesTitle"); //$NON-NLS-1$
                final String message = UiConstants.Util.getString("DeleteResourceAction.pendingChangesMessage", theFile.getName()); //$NON-NLS-1$
                result = MessageDialog.openQuestion(getShell(), title, message);
            }

        }

        return result;
    }

    private boolean isOkToCloseFolder( final IFolder theFolder ) {
        boolean result = true;

        try {
            final IResource[] kids = theFolder.members();

            if (kids.length > 0) for (int i = 0; i < kids.length; i++)
                if (!isOkToCloseResource(kids[i])) result = false;
        } catch (final CoreException theException) {
            Util.log(theException);
            result = false;
        }

        return result;
    }

    private boolean isOkToCloseProject( final IProject theProject ) {
        boolean result = true;

        try {
            final IResource[] kids = theProject.members();

            if (kids.length > 0) for (int i = 0; i < kids.length; i++)
                if (!isOkToCloseResource(kids[i])) result = false;
        } catch (final CoreException theException) {
            Util.log(theException);
            result = false;
        }

        return result;
    }

    private boolean isOkToCloseResource( final IResource theResource ) {
        boolean result = true;

        if (ModelerCore.hasModelNature(theResource.getProject())) if (theResource instanceof IProject) result = isOkToCloseProject((IProject)theResource);
        else if (theResource instanceof IFolder) result = isOkToCloseFolder((IFolder)theResource);
        else if (theResource instanceof IFile) result = isOkToCloseFile((IFile)theResource);

        return result;
    }

    private boolean isOkToCloseResources( final List resources ) {
        boolean userOK = true;

        // We need to wrap this in a transaction
        final boolean started = ModelerCore.startTxn(false, false, "Confirming Close Resources", //$NON-NLS-1$
                                                     new DefaultIgnorableNotificationSource(DeleteResourceAction.this));
        boolean succeeded = false;
        try {
            for (int size = resources.size(), i = 0; i < size && userOK; i++) {
                final Object obj = resources.get(i);

                if (obj instanceof IResource) userOK = isOkToCloseResource((IResource)obj);
            }
            succeeded = true;
        } catch (final Exception err) {
            final String msg = UiConstants.Util.getString("DeleteResourceAction.confirmingCloseResources"); //$NON-NLS-1$
            UiConstants.Util.log(IStatus.ERROR, err, msg);
        } finally {
            if (started) if (succeeded) ModelerCore.commitTxn();
            else ModelerCore.rollbackTxn();
        }

        return userOK;
    }

    private boolean isUnderSelectedObjects( final IResource dependentResource,
                                            final Collection selectedObjects ) {
        final Iterator iter = selectedObjects.iterator();
        while (iter.hasNext())
            if (dependentResource.getProject().equals(iter.next())) return true;
        return false;
    }

    private boolean isValidSelection() {
        // Check for mixed Selection
        // Can't delete EObjects & IResources together
        if (SelectionUtilities.isMixedObjectTypes(getSelection())) return false;

        final boolean isValid = true;
        // 1) Can't delete a .project file
        // 2) Can't delete the "Configuration" project
        // 3) Can't delete the "FunctionDefinitions" model
        final List selectedObjects = getSelectedObjects();
        for (final Iterator iter = selectedObjects.iterator(); iter.hasNext();) {
            final Object nextObj = iter.next();
            if (nextObj instanceof IProject) {
                final String projName = ((IProject)nextObj).getName();
                if (ModelerCore.isReservedProjectName(projName)) return false;
            } else if (nextObj instanceof IFile) {
                final IFile file = (IFile)nextObj;
                if (file.getFileExtension() != null && file.getFileExtension().equalsIgnoreCase(ModelerCore.DOT_PROJECT_EXTENSION)) return false;
                else if (file.getName().equalsIgnoreCase(ModelerCore.UDF_MODEL_NAME)) return false;
            }
        }

        return isValid;
    }

    /*
     * Notify with RefactorRenameEvent.TYPE_DELETE
     */
    private void notifyDeleted( final IPath deletedResourcePath ) {
        ((ModelerCore)ModelerCore.getPlugin()).notifyRefactored(new RefactorResourceEvent(null, RefactorResourceEvent.TYPE_DELETE,
                                                                                          this, deletedResourcePath));
    }

    /* (non-Javadoc)
     * see org.teiid.designer.ui.common.actions.AbstractAction#selectionChanged(org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection)
     */
    @Override
    public void selectionChanged( final IWorkbenchPart thePart,
                                  final ISelection theSelection ) {
        super.selectionChanged(thePart, theSelection);

        // make the delegate actions aware of the new selection and set enablement
        IStructuredSelection selection = null;

        if (theSelection instanceof IStructuredSelection) selection = (IStructuredSelection)theSelection;
        else selection = StructuredSelection.EMPTY;

        this.deleteResourceAction.selectionChanged(selection);

        if (this.deleteEObjectAction != null) this.deleteEObjectAction.selectionChanged(thePart, selection);

        // set enablement
        boolean enable = false;

        if (this.deleteResourceAction.isEnabled()) {
            this.delegateAction = this.deleteResourceAction;
            enable = true;
        } else if ((this.deleteResourceAction != null) && this.deleteEObjectAction.isEnabled()) {
            this.delegateAction = this.deleteEObjectAction;
            enable = true;
        }
        // Now make sure we aren't deleting a .project, FunctionDefinitions.xmi, or Configuration project
        if (enable) enable = isValidSelection();
        setEnabled(enable);
    }

    /* (non-Javadoc)
     * see org.teiid.designer.ui.common.actions.AbstractAction#selectionChanged(org.eclipse.jface.viewers.SelectionChangedEvent)
     */
    @Override
    public void selectionChanged( final SelectionChangedEvent theEvent ) {
        super.selectionChanged(theEvent);

        // make the delegate actions aware of the new selection and set enablement
        this.deleteResourceAction.selectionChanged(theEvent);

        if (this.deleteResourceAction != null) this.deleteEObjectAction.selectionChanged(theEvent);

        // set enablement
        boolean enable = false;

        if (deleteResourceAction.isEnabled()) {
            this.delegateAction = this.deleteResourceAction;
            enable = true;
        } else if ((this.deleteResourceAction != null) && this.deleteEObjectAction.isEnabled()) {
            this.delegateAction = this.deleteEObjectAction;
            enable = true;
        }

        // Now make sure we aren't deleting a .project, FunctionDefinitions.xmi, or Configuration project
        if (enable) enable = isValidSelection();
        setEnabled(enable);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.eclipse.jface.action.Action#setEnabled(boolean)
     */
    @Override
    public void setEnabled( final boolean enabled ) {
        super.setEnabled(enabled);
    }

    /*
     * This method calls the appropriate validate method to create the "Missing" import problem markers (and others) for the
     * resources just deleted.
     */
    void validateDependentResources( final Collection modelIResources,
                                     final IProgressMonitor theMontitor ) {
        if (!modelIResources.isEmpty()) {
            // In order for the notifications caused by "opening models" for validation, to be swallowed, the validation
            // call needs to be wrapped in a transaction. This was discovered and relayed by Goutam on 2/14/05.
            final boolean started = ModelerCore.startTxn(false, false, "Validate Dependent Resources", //$NON-NLS-1$
                                                         new DefaultIgnorableNotificationSource(DeleteResourceAction.this));
            boolean succeeded = false;
            try {
                ModelBuildUtil.validateResources(theMontitor, modelIResources, doGetContainer(), false);

                succeeded = true;

            } catch (final Exception err) {
                final String msg = Util.getString("DeleteResourceAction.validateDependentResources"); //$NON-NLS-1$
                getPluginUtils().log(IStatus.ERROR, err, msg);
            } finally {
                if (started) if (succeeded) ModelerCore.commitTxn();
                else ModelerCore.rollbackTxn();
            }

        }
    }

    private boolean warnUserAboutDependants( final Collection dependentResources ) {
        final String title = Util.getString("DeleteResourceAction.confirmDependenciesTitle"); //$NON-NLS-1$
        final String msg = Util.getString("DeleteResourceAction.confirmDependenciesMessage"); //$NON-NLS-1$
        final List resourceList = new ArrayList(dependentResources.size());
        for (final Iterator iter = dependentResources.iterator(); iter.hasNext();) {
            final IPath shortPath = ((IResource)iter.next()).getFullPath().makeRelative();
            resourceList.add(shortPath);
        }
        return ListMessageDialog.openWarningQuestion(getShell(), title, null, msg, resourceList, null);
    }

    // Inner class designed to count down each Deleting resources
    class DeleteFinishedJobListener extends JobChangeAdapter {
    	private final CountDownLatch latch;
    	
    	Collection deletedModelPaths;

        public DeleteFinishedJobListener( CountDownLatch latch, Collection deletedModelPaths) {
        	this.latch = latch;
        	this.deletedModelPaths = new ArrayList(deletedModelPaths);
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.eclipse.core.runtime.jobs.JobChangeAdapter#done(org.eclipse.core.runtime.jobs.IJobChangeEvent)
         */
        @Override
        public void done( IJobChangeEvent event ) {
        	this.latch.countDown();
            
        	
            if( latch.getCount() == 0 ) {
            	ModelerUndoManager.getInstance().clearAllEdits();
	            RefactorUndoManager.getInstance().clear();
	
	            for (final Object path : deletedModelPaths)
	                notifyDeleted((IPath)path);
            }
        }
    }
}
