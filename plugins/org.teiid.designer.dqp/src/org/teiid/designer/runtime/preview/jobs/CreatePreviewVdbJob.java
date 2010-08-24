/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */

package org.teiid.designer.runtime.preview.jobs;

import static com.metamatrix.modeler.dqp.DqpPlugin.PLUGIN_ID;
import java.io.ByteArrayInputStream;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.osgi.util.NLS;
import org.teiid.designer.runtime.preview.Messages;
import org.teiid.designer.runtime.preview.PreviewContext;
import org.teiid.designer.runtime.preview.PreviewManager;
import org.teiid.designer.vdb.Vdb;

/**
 * The <code>CreatePreviewVdbJob</code> creates a Preview VDB in the Eclipse workspace if it doesn't already exist. The Preview
 * VDB is associated to exactly one model in the workspace.
 */
public final class CreatePreviewVdbJob extends WorkspacePreviewVdbJob {

    /**
     * The model whose Preview VDB is being created (never <code>null</code>).
     */
    private final IFile model;

    /**
     * The project whose Preview VDB is being created (never <code>null</code>).
     */
    private final IProject project;

    /**
     * The Preview VDB or <code>null</code> if job did not complete successfully.
     */
    private IFile pvdbFile;

    /**
     * @param model the model whose Preview VDB is being created (may not be <code>null</code>)
     * @throws Exception if unable to construct the Preview VDB
     */
    public CreatePreviewVdbJob( IFile model,
                                PreviewContext context ) throws Exception {
        super(NLS.bind(Messages.CreatePreviewVdbJob, model.getFullPath()), context);
        assert PreviewManager.isPreviewable(model) : "model is not previewable" + model.getFullPath(); //$NON-NLS-1$
        this.model = model;
        this.project = null;
        initialize();
    }

    /**
     * @param project the project whose Preview VDB is being created (may not be <code>null</code>)
     * @throws Exception if unable to construct the Preview VDB
     */
    public CreatePreviewVdbJob( IProject project,
                                PreviewContext context ) throws Exception {
        super(NLS.bind(Messages.CreatePreviewVdbJob, project.getFullPath()), context);
        this.project = project;
        this.model = null;
        initialize();
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.teiid.designer.runtime.preview.jobs.WorkspacePreviewVdbJob#getPreviewVdb()
     */
    @Override
    public IFile getPreviewVdb() {
        return this.pvdbFile;
    }

    /**
     * <strong>Must be called by constructors.</strong>
     */
    private void initialize() {
        // set the PVDB
        IResource resource = (project == null) ? this.model : this.project;
        int size = (project == null) ? 3 : 2;
        this.pvdbFile = getContext().getPreviewVdb(resource);

        // set job scheduling rule on the PVDB resource
        ISchedulingRule[] rules = new ISchedulingRule[size];
        rules[0] = getSchedulingRuleFactory().createRule(this.pvdbFile);
        rules[1] = getSchedulingRuleFactory().modifyRule(this.pvdbFile);

        // if the model PVDB need to also "lock" on the model resource
        if (size == 3) {
            rules[2] = getSchedulingRuleFactory().modifyRule(this.model);
        }

        setRule(MultiRule.combine(rules));
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.teiid.designer.runtime.preview.jobs.WorkspacePreviewVdbJob#runImpl(org.eclipse.core.runtime.IProgressMonitor)
     */
    @Override
    protected IStatus runImpl( IProgressMonitor monitor ) throws Exception {
        IResource resource = ((this.project == null) ? this.model : this.project);

        // if the file was deleted from outside Eclipse, Eclipse will think it still exists
        if (this.pvdbFile.exists() && !this.pvdbFile.getLocation().toFile().exists()) {
            this.pvdbFile.delete(true, monitor);
        }

        boolean isNew = false;
        // create if necessary
        if (!this.pvdbFile.exists()) {
            isNew = true;
            this.pvdbFile.create(new ByteArrayInputStream(new byte[0]), false, null);
        }

        // make sure the file is hidden
        this.pvdbFile.setHidden(true);

        try {
            Vdb pvdb = new Vdb(this.pvdbFile, true, monitor);

            // don't do if a project PVDB
            if (resource instanceof IFile) {
                // don't add if already in the PVDB (only one model per PVDB)
                if (pvdb.getModelEntries().isEmpty()) {
                    pvdb.addModelEntry(this.model.getFullPath(), monitor);
                }
            }

            // this will trigger an resource change event which will eventually get an update job to run
            if (isNew || pvdb.isModified()) {
                pvdb.save(monitor);
            }
        } catch (Exception e) {
            IPath path = ((this.project == null) ? this.model.getFullPath() : this.project.getFullPath());
            throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, NLS.bind(Messages.CreatePreviewVdbJobError, path), e));
        }

        // all good
        return new Status(IStatus.OK, PLUGIN_ID, NLS.bind(Messages.CreatePreviewVdbJobSuccessfullyCompleted,
                                                          this.pvdbFile.getFullPath()));
    }

}
