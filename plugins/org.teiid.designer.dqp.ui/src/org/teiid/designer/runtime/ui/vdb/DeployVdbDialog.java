/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.teiid.designer.runtime.ui.vdb;

import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;

import com.metamatrix.core.event.IChangeListener;
import com.metamatrix.core.event.IChangeNotifier;
import com.metamatrix.core.util.I18nUtil;
import com.metamatrix.modeler.dqp.ui.DqpUiConstants;
import com.metamatrix.modeler.internal.core.workspace.WorkspaceResourceFinderUtil;
import com.metamatrix.modeler.internal.ui.explorer.ModelExplorerContentProvider;
import com.metamatrix.modeler.internal.ui.explorer.ModelExplorerLabelProvider;
import com.metamatrix.modeler.internal.ui.viewsupport.ModelWorkspaceDialog;
import com.metamatrix.modeler.ui.viewsupport.IPropertiesContext;
import com.metamatrix.ui.internal.util.WidgetFactory;
import com.metamatrix.ui.internal.viewsupport.ClosedProjectFilter;
import com.metamatrix.ui.internal.viewsupport.StatusInfo;
import com.metamatrix.ui.internal.widget.Label;

public class DeployVdbDialog extends TitleAreaDialog implements DqpUiConstants,
		VdbConstants, IChangeListener {

	private static final String PREFIX = I18nUtil.getPropertyPrefix(DeployVdbDialog.class);

	private IFile selectedVdb;

	private ILabelProvider labelProvider = new ModelExplorerLabelProvider();

	private Button browseButton;
	private Text selectedVdbText;

	Properties designerProperties;

	/**
	 * @since 5.5.3
	 */
	public DeployVdbDialog(Shell parentShell, Properties properties) {
		super(parentShell);
		setShellStyle(getShellStyle() | SWT.RESIZE);
		this.designerProperties = properties;
	}

	/**
	 * @see org.eclipse.jface.dialogs.Dialog#close()
	 * @since 5.5.3
	 */
	@Override
	public boolean close() {

		if (this.labelProvider != null) {
			this.labelProvider.dispose();
		}

		return super.close();
	}

	/**
	 * @see org.eclipse.jface.window.Window#configureShell(org.eclipse.swt.widgets.Shell)
	 * @since 5.5.3
	 */
	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(UTIL.getString(PREFIX + "title")); //$NON-NLS-1$
	}

	/**
	 * @see org.eclipse.jface.dialogs.Dialog#createButtonBar(org.eclipse.swt.widgets.Composite)
	 * @since 5.5.3
	 */
	@Override
	protected Control createButtonBar(Composite parent) {
		Control buttonBar = super.createButtonBar(parent);
		getButton(OK).setEnabled(false);

		// set the first selection so that initial validation state is set
		// (doing it here since the selection handler uses OK
		// button)

		return buttonBar;
	}

	/**
	 * @see org.eclipse.jface.dialogs.TitleAreaDialog#createDialogArea(org.eclipse.swt.widgets.Composite)
	 * @since 5.5.3
	 */
	@Override
	protected Control createDialogArea(Composite parent) {

		Composite pnlOuter = (Composite) super.createDialogArea(parent);
		Composite panel = new Composite(pnlOuter, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 3;
		panel.setLayout(gridLayout);
		panel.setLayoutData(new GridData(GridData.FILL_BOTH));

		// set title
		setTitle(UTIL.getString(PREFIX + "subTitle")); //$NON-NLS-1$
		setMessage(UTIL.getString(PREFIX + "initialMessage")); //$NON-NLS-1$

		Label label = WidgetFactory.createLabel(panel,
				UTIL.getString(PREFIX + "vdb")); //$NON-NLS-1$
		label.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

		// textfield for named type
		this.selectedVdbText = WidgetFactory.createTextField(panel,
				GridData.FILL_HORIZONTAL/* GridData.HORIZONTAL_ALIGN_FILL */);
		this.selectedVdbText.setEditable(false);
		// this.selectedEobjectText.setLayoutData(new GridData(SWT.CENTER,
		// SWT.NONE, true, false));

		// browse type button
		this.browseButton = WidgetFactory.createButton(panel, "..."); //$NON-NLS-1$
		this.browseButton.setToolTipText(UTIL.getString(PREFIX + "button.browseType.tip")); //$NON-NLS-1$
		this.browseButton.setEnabled(true);
		this.browseButton.setLayoutData(new GridData(SWT.CENTER, SWT.NONE,
				false, false));
		this.browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent theEvent) {
				handleBrowseWorkspaceForVdbPressed();
			}
		});

		return panel;
	}

	public IFile getSelectedVdb() {
		return this.selectedVdb;
	}

	/**
	 * @see com.metamatrix.core.event.IChangeListener#stateChanged(com.metamatrix.core.event.IChangeNotifier)
	 * @since 5.5.3
	 */
	public void stateChanged(IChangeNotifier theSource) {
		updateState();
	}

	private void updateState() {
		IStatus status = Status.OK_STATUS;

		if (status.getSeverity() == IStatus.ERROR) {
			getButton(OK).setEnabled(false);
			setErrorMessage(status.getMessage());
		} else {
			getButton(OK).setEnabled(true);
			setErrorMessage(null);
			setMessage(UTIL.getString(PREFIX + "okMsg")); //$NON-NLS-1$
		}
	}

	private void handleBrowseWorkspaceForVdbPressed() {
		ModelWorkspaceDialog vdbDialog = createVdbSelector();

		// add filters
		((ModelWorkspaceDialog) vdbDialog).addFilter(new ClosedProjectFilter());

		vdbDialog.open();

		if (vdbDialog.getReturnCode() == Window.OK) {
			Object[] selections = vdbDialog.getResult();
			// should be single selection
			selectedVdb = (IFile) selections[0];
			this.selectedVdbText.setText(selectedVdb.getName());

			updateState();
		}

	}

	private ModelWorkspaceDialog createVdbSelector() {

		ModelWorkspaceDialog result = new ModelWorkspaceDialog(getShell(),
				null, new ModelExplorerLabelProvider(),
				new ModelExplorerContentProvider());

		String title = UTIL.getString(PREFIX + "selectionDialog.title"); //$NON-NLS-1$
		String message = UTIL.getString(PREFIX + "selectionDialog.message"); //$NON-NLS-1$
		result.setTitle(title);
		result.setMessage(message);
		result.setAllowMultiple(false);

		result.setInput(ResourcesPlugin.getWorkspace().getRoot());

		result.setValidator(new ISelectionStatusValidator() {
			public IStatus validate(Object[] selection) {
				if (selection != null && selection.length == 1) {
					if (selection[0] instanceof IFile) {
						String extension = ((IFile) selection[0]).getFileExtension();
						if (extension != null && extension.equals(VDB_EXTENSION)) {
							return new StatusInfo(DqpUiConstants.PLUGIN_ID);
						}
					}
				}
				if (selection == null || selection.length == 0) {
					return new StatusInfo(DqpUiConstants.PLUGIN_ID, IStatus.ERROR, UTIL.getString(PREFIX
									+ "selectionDialog.emptySelection")); //$NON-NLS-1$
				}
				String msg = UTIL.getString(PREFIX + "selectionDialog.invalidSelection"); //$NON-NLS-1$
				return new StatusInfo(DqpUiConstants.PLUGIN_ID, IStatus.ERROR, msg);
			}
		});

		return result;
	}

	@Override
	protected Control createContents(Composite parent) {
		Control control = super.createContents(parent);

		if (this.designerProperties != null) {
			// check for vdb name property
			String vdbName = this.designerProperties.getProperty(IPropertiesContext.KEY_LAST_VDB_NAME);
			if (vdbName != null) {
				// Try to find VDB in workspace
				final IResource vdbResource = getVdb(vdbName);
				if (vdbResource != null) {
					selectedVdb = (IFile) vdbResource;
					this.selectedVdbText.setText(selectedVdb.getName());
					updateState();
				}
			}
		}
		return control;
	}

	private IFile getVdb(String name) {
		// Collect only vdb archive resources from the workspace
		final Collection vdbResources = WorkspaceResourceFinderUtil.getAllWorkspaceResources(WorkspaceResourceFinderUtil.VDB_RESOURCE_FILTER);
		for (final Iterator iter = vdbResources.iterator(); iter.hasNext();) {
			final IResource vdb = (IResource) iter.next();
			if (vdb.getFullPath().lastSegment().equalsIgnoreCase(name)) {
				return (IFile) vdb;
			}
		}

		return null;

	}

}