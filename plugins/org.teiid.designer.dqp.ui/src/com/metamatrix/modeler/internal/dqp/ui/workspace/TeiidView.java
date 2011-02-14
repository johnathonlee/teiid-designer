/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package com.metamatrix.modeler.internal.dqp.ui.workspace;

import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ResourceTransfer;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.IPropertySourceProvider;
import org.eclipse.ui.views.properties.PropertySheetPage;
import org.teiid.adminapi.Model;
import org.teiid.designer.runtime.ExecutionAdmin;
import org.teiid.designer.runtime.ExecutionConfigurationEvent;
import org.teiid.designer.runtime.ExecutionConfigurationEvent.EventType;
import org.teiid.designer.runtime.ExecutionConfigurationEvent.TargetType;
import org.teiid.designer.runtime.IExecutionConfigurationListener;
import org.teiid.designer.runtime.PreferenceConstants;
import org.teiid.designer.runtime.Server;
import org.teiid.designer.runtime.ServerManager;
import org.teiid.designer.runtime.TeiidDataSource;
import org.teiid.designer.runtime.TeiidTranslator;
import org.teiid.designer.runtime.TeiidVdb;
import org.teiid.designer.runtime.preview.PreviewManager;
import org.teiid.designer.runtime.ui.DeleteServerAction;
import org.teiid.designer.runtime.ui.DisconnectFromServerAction;
import org.teiid.designer.runtime.ui.EditServerAction;
import org.teiid.designer.runtime.ui.ExecuteVDBAction;
import org.teiid.designer.runtime.ui.NewServerAction;
import org.teiid.designer.runtime.ui.ReconnectToServerAction;
import org.teiid.designer.runtime.ui.SetDefaultServerAction;
import org.teiid.designer.runtime.ui.connection.CreateDataSourceAction;

import com.metamatrix.core.util.CoreStringUtil;
import com.metamatrix.core.util.I18nUtil;
import com.metamatrix.core.util.StringUtilities;
import com.metamatrix.modeler.dqp.DqpPlugin;
import com.metamatrix.modeler.dqp.internal.workspace.SourceConnectionBinding;
import com.metamatrix.modeler.dqp.ui.DqpUiConstants;
import com.metamatrix.modeler.dqp.ui.DqpUiPlugin;
import com.metamatrix.modeler.internal.dqp.ui.workspace.TeiidViewTreeProvider.DataSourcesFolder;
import com.metamatrix.modeler.internal.ui.viewsupport.ModelerUiViewUtils;
import com.metamatrix.ui.internal.eventsupport.SelectionUtilities;
import com.metamatrix.ui.internal.util.UiUtil;
import com.metamatrix.ui.internal.widget.Label;

/**
 * The ConnectorsView provides a tree view of workspace connector bindings which are stored in a configuration.xml file and
 * corresponding model-to-connector mappings in a WorkspaceBindings.def file.
 */
public class TeiidView extends ViewPart implements IExecutionConfigurationListener {

    static final String PREFIX = I18nUtil.getPropertyPrefix(TeiidView.class);

    static final String ACTIVE_VDB = getString("activeVdb"); //$NON-NLS-1$
    static final String INACTIVE_VDB = getString("inactiveVdb"); //$NON-NLS-1$
    
    // memento info for saving and restoring menu state from session to session
    private static final String MENU_MEMENTO = "menu-settings"; //$NON-NLS-1$
    private static final String SHOW_PREVIEW_VDBS = "show-preview-vdbs"; //$NON-NLS-1$
    private static final String SHOW_PREVIEW_DATA_SOURCES = "show-preview-data-sources"; //$NON-NLS-1$
    private static final String SHOW_TRANSLATORS = "show-translators"; //$NON-NLS-1$
    
    /**
     * Used for restoring view state
     */
    private static IMemento viewMemento;

    static String getString( final String stringId ) {
        return DqpUiConstants.UTIL.getString(PREFIX + stringId);
    }

    static String getString( final String stringId,
                             final Object param ) {
        return DqpUiConstants.UTIL.getString(PREFIX + stringId, param);
    }

    TreeViewer viewer;
    TeiidViewTreeProvider treeProvider;

    /**
     * Collapses all tree nodes.
     */
    private IAction collapseAllAction;

    /**
     * Deletes a server.
     */
    private DeleteServerAction deleteServerAction;

    /**
     * Creates a new server.
     */
    private NewServerAction newServerAction;

    /**
     * Edits a server's properties.
     */
    private EditServerAction editServerAction;
    /**
     * Refreshes the server connections.
     */
    private ReconnectToServerAction reconnectAction;
    
    private DisconnectFromServerAction disconnectAction;

    /**
     * Sets the selected Server as the default server for preview and execution
     */
    private SetDefaultServerAction setDefaultServerAction;

    private Action createDataSourceAction;

    private Action deleteDataSourceAction;

    private Action undeployVdbAction;

    private Action executeVdbAction;

    /** needed for key listening */
    private KeyAdapter kaKeyAdapter;

    private IPropertySourceProvider propertySourceProvider;
    
    private IAction showPreviewVdbsAction;
    private IAction showPreviewDataSourcesAction;
    private IAction showTranslatorsAction;

    ExecutionAdmin currentSelectedAdmin;

    /**
     * The constructor.
     */
    public TeiidView() {
        this.setPartName(getString("title.text")); //$NON-NLS-1$
        this.setTitleImage(DqpUiPlugin.getDefault().getImage(DqpUiConstants.Images.SOURCE_BINDING_ICON));
        this.setTitleToolTip(getString("title.tooltip")); //$NON-NLS-1$
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.teiid.designer.runtime.IExecutionConfigurationListener#configurationChanged(org.teiid.designer.runtime.ExecutionConfigurationEvent)
     */
    @Override
    public void configurationChanged( final ExecutionConfigurationEvent event ) {
        if (viewer.getTree().isDisposed()) {
            return;
        }

        UiUtil.runInSwtThread(new Runnable() {
            public void run() {
                if (!viewer.getTree().isDisposed()) {
                    if ((event.getEventType() == EventType.REFRESH) && (event.getTargetType() == TargetType.SERVER)) {
                        viewer.refresh(event.getServer());
                    } else {
                        viewer.refresh();
                    }

                    viewer.expandAll();
                    // Get Selected Index
                    if (viewer.getTree().getSelectionCount() == 1) {
                        ISelection currentSelection = viewer.getSelection();
                        viewer.setSelection(new StructuredSelection());
                        viewer.setSelection(currentSelection);
                    }
                }
            }
        }, false);

        // Refresh the Model Explorer too
        ModelerUiViewUtils.refreshModelExplorerResourceNavigatorTree();
    }

    private void contributeToActionBars() {
        IActionBars bars = getViewSite().getActionBars();
        fillLocalPullDown(bars.getMenuManager());
        fillLocalToolBar(bars.getToolBarManager());
    }

    /**
     * This is a callback that will allow us to create the viewer and initialize it.
     */
    @Override
    public void createPartControl( Composite parent ) {
        viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);

        initDragAndDrop();

        treeProvider = new TeiidViewTreeProvider(true, false, true);
        viewer.setContentProvider(treeProvider);
        ILabelDecorator decorator = DqpUiPlugin.getDefault().getWorkbench().getDecoratorManager().getLabelDecorator();
        viewer.setLabelProvider(new DecoratingLabelProvider(treeProvider, decorator));

        hookToolTips();

        viewer.setSorter(new NameSorter());

        viewer.addSelectionChangedListener(new ISelectionChangedListener() {
            public void selectionChanged( SelectionChangedEvent event ) {
                handleSelectionChanged(event);
            }
        });

        initActions();

        hookContextMenu();

        contributeToActionBars();

        initKeyListener();

        // hook up this view's selection provider to this site
        getViewSite().setSelectionProvider(viewer);
        
        // populate viewer
        viewer.setInput(DqpPlugin.getInstance().getServerManager());
        viewer.expandAll();

        // Wire as listener to server manager and to receive configuration changes
        DqpPlugin.getInstance().getServerManager().addListener(this);
    }

    @Override
    public void dispose() {
        DqpPlugin.getInstance().getServerManager().removeListener(this);
        super.dispose();
    }

    void fillContextMenu( IMenuManager manager ) {
        List<Object> selectedObjs = getSelectedObjects();

        if (selectedObjs != null && !selectedObjs.isEmpty()) {
            if (selectedObjs.size() == 1) {
                Object selection = selectedObjs.get(0);
                if (selection instanceof Server) {
                	if( ((Server)selection).isConnected() ) {
	                    try {
	                        currentSelectedAdmin = ((Server)selection).getAdmin();
	                    } catch (Exception e) {
	                        // DO NOTHING
	                    }
                	}
                    manager.add(this.editServerAction);
                    
                    if (this.setDefaultServerAction.isEnabled()) {
                        manager.add(this.setDefaultServerAction);
                    }
                    if( currentSelectedAdmin != null )  {
                    	manager.add(this.disconnectAction);
                    }
                    manager.add(this.reconnectAction);
                    manager.add(new Separator());
                    manager.add(this.newServerAction);
                    if( currentSelectedAdmin != null ) {
                    	manager.add(this.createDataSourceAction);
                    }
                    manager.add(new Separator());
                    manager.add(this.deleteServerAction);

                } else if (selection instanceof TeiidTranslator) {
                	currentSelectedAdmin = ((TeiidTranslator)selection).getAdmin();
                    manager.add(this.newServerAction);
                    if( currentSelectedAdmin != null ) {
                    	manager.add(this.createDataSourceAction);
                    }
                } else if (selection instanceof TeiidDataSource) {
                	manager.add(this.createDataSourceAction);
                    manager.add(new Separator());
                    manager.add(this.deleteDataSourceAction);
                    manager.add(new Separator());
                    manager.add(this.newServerAction);
                    currentSelectedAdmin = ((TeiidDataSource)selection).getAdmin();
                } else if (selection instanceof TeiidVdb) {
                	currentSelectedAdmin = ((TeiidVdb)selection).getAdmin();
                    this.executeVdbAction.setEnabled(((TeiidVdb)selection).isActive());
                    manager.add(this.executeVdbAction);
                    manager.add(new Separator());
                    manager.add(this.undeployVdbAction);             
                    manager.add(new Separator());
                    manager.add(this.newServerAction);
                    manager.add(this.createDataSourceAction);
                } else if( selection instanceof DataSourcesFolder ) {
                	currentSelectedAdmin = ((DataSourcesFolder)selection).getAdmin();
                    if( currentSelectedAdmin != null ) {
                    	manager.add(this.createDataSourceAction);
                    }
                }
            } else {
                boolean allDataSources = true;

                for (Object obj : selectedObjs) {
                    if (!(obj instanceof TeiidDataSource)) {
                        allDataSources = false;
                        break;
                    }
                }
                if (allDataSources) {
                    manager.add(this.deleteDataSourceAction);
                    manager.add(new Separator());
                    manager.add(this.newServerAction);
                    manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
                    return;
                }

                boolean allVdbs = true;

                for (Object obj : selectedObjs) {
                    if (!(obj instanceof TeiidVdb)) {
                        allVdbs = false;
                        break;
                    }
                }
                if (allVdbs) {
                    manager.add(this.undeployVdbAction);
                    manager.add(new Separator());
                    manager.add(this.newServerAction);
                    manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
                    return;
                }

                manager.add(this.newServerAction);
                manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
            }

        } else {
            manager.add(this.newServerAction);
        }

        // Other plug-ins can contribute there actions here
        manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
    }

    private void fillLocalPullDown( IMenuManager menuMgr ) {
        // restore settings from last session
        restoreLocalPullDown();

        // add the show preview VDBs action
        this.showPreviewVdbsAction = new Action(DqpUiConstants.UTIL.getString(PREFIX + "showPreviewVdbsMenuItem"), SWT.TOGGLE) { //$NON-NLS-1$
            @Override
            public void run() {
                TeiidView.this.treeProvider.setShowPreviewVdbs(!TeiidView.this.treeProvider.isShowingPreviewVdbs());
                TeiidView.this.viewer.refresh(false);
                TeiidView.this.viewer.expandAll();
            }
        };

        // restore state and add to menu
        this.showPreviewVdbsAction.setChecked(this.treeProvider.isShowingPreviewVdbs());
        menuMgr.add(this.showPreviewVdbsAction);

        // add the show translators action
        this.showTranslatorsAction = new Action(DqpUiConstants.UTIL.getString(PREFIX + "showTranslatorsMenuItem"), SWT.TOGGLE) { //$NON-NLS-1$
            @Override
            public void run() {
                treeProvider.setShowTranslators(!TeiidView.this.treeProvider.isShowingTranslators());
                viewer.refresh(false);
                viewer.expandAll();
            }
        };

        // restore state and add to menu
        this.showTranslatorsAction.setChecked(this.treeProvider.isShowingTranslators());
        menuMgr.add(this.showTranslatorsAction);

        // add the show preview data sources action
        this.showPreviewDataSourcesAction = new Action(
                                                       DqpUiConstants.UTIL.getString(PREFIX + "showPreviewDataSourcesMenuItem"), SWT.TOGGLE) { //$NON-NLS-1$
            @Override
            public void run() {
                treeProvider.setShowPreviewDataSources(!TeiidView.this.treeProvider.isShowingPreviewDataSources());
                viewer.refresh(false);
                viewer.expandAll();
            }
        };

        // restore state and add to menu
        this.showPreviewDataSourcesAction.setChecked(this.treeProvider.isShowingPreviewDataSources());
        menuMgr.add(this.showPreviewDataSourcesAction);

        final IAction enablePreviewAction = new Action(
                                                       DqpUiConstants.UTIL.getString(PREFIX + "enablePreviewMenuItem"), SWT.TOGGLE) { //$NON-NLS-1$
            /**
             * {@inheritDoc}
             * 
             * @see org.eclipse.jface.action.Action#setChecked(boolean)
             */
            @Override
            public void setChecked( boolean checked ) {
                super.setChecked(checked);

                if (checked != isPreviewEnabled()) {
                    DqpPlugin.getInstance().getPreferences().putBoolean(PreferenceConstants.PREVIEW_ENABLED, checked);
                }
            }
        };

        menuMgr.add(enablePreviewAction);

        // before the menu shows set the state of the enable preview action
        menuMgr.addMenuListener(new IMenuListener() {

            @Override
            public void menuAboutToShow( IMenuManager manager ) {
                enablePreviewAction.setChecked(isPreviewEnabled());
            }
        });
    }

    private void fillLocalToolBar( IToolBarManager manager ) {
        manager.add(this.newServerAction);
        manager.add(this.reconnectAction);
        manager.add(new Separator());
        manager.add(this.collapseAllAction);
    }

    @Override
    public Object getAdapter( Class adapter ) {
        if (adapter.equals(IPropertySheetPage.class)) {
            propertySourceProvider = new RuntimePropertySourceProvider();
            ((RuntimePropertySourceProvider)propertySourceProvider).setEditable(true, false);
            PropertySheetPage page = new PropertySheetPage();
            page.setPropertySourceProvider(propertySourceProvider);
            return page;
        }
        return super.getAdapter(adapter);
    }

    String getConnectorToolTip( TeiidTranslator connector ) {
        return connector.getName();
    }

    SourceConnectionBinding getSelectedBinding() {
        StructuredSelection selection = (StructuredSelection)viewer.getSelection();
        if (!selection.isEmpty() && selection.getFirstElement() instanceof SourceConnectionBinding) {
            return (SourceConnectionBinding)selection.getFirstElement();
        }

        return null;
    }

    List<Object> getSelectedObjects() {
        StructuredSelection selection = (StructuredSelection)viewer.getSelection();
        if (!selection.isEmpty()) {
            return SelectionUtilities.getSelectedObjects(selection);
        }

        return null;
    }

    /**
     * @return the server manager being used by this view
     */
    private ServerManager getServerManager() {
        return DqpPlugin.getInstance().getServerManager();
    }

    String getVDBToolTip( TeiidVdb vdb ) {
        StringBuilder builder = new StringBuilder();
        builder.append("VDB:\t\t").append(vdb.getName()).append("\nState:\t"); //$NON-NLS-1$ //$NON-NLS-2$
        if (vdb.isActive()) {
            builder.append(ACTIVE_VDB);
        } else {
            builder.append(INACTIVE_VDB);
            for (String error : vdb.getVdb().getValidityErrors()) {
                builder.append("\nERROR:\t").append(error); //$NON-NLS-1$
            }
        }

        builder.append("\nModels:"); //$NON-NLS-1$
        for (Model model : vdb.getVdb().getModels()) {
            builder.append("\n\t   ").append(model.getName()); //$NON-NLS-1$
        }
        return builder.toString();
    }

    /**
     * On certain keys execute certain actions
     */
    void handleKeyEvent( KeyEvent event ) {
        if (event.stateMask != 0) return;

        if (event.character == SWT.DEL) {
            // if (deleteConnectorBindingAction != null && deleteConnectorBindingAction.isEnabled()) {
            // deleteConnectorBindingAction.run();
            // }
        }
    }

    void handleSelectionChanged( SelectionChangedEvent event ) {
        updateStatusLine((IStructuredSelection)event.getSelection());
    }

    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener() {
            public void menuAboutToShow( IMenuManager manager ) {
                TeiidView.this.fillContextMenu(manager);
            }
        });
        Menu menu = menuMgr.createContextMenu(viewer.getControl());
        viewer.getControl().setMenu(menu);
        getSite().registerContextMenu(menuMgr, viewer);
    }

    /**
     * Tooltips over connectors and types requires a mouse label listener
     */
    private void hookToolTips() {
        final Listener labelListener = new Listener() {
            public void handleEvent( Event event ) {
                Label label = (Label)event.widget;
                Shell shell = label.getShell();
                switch (event.type) {
                    case SWT.MouseDown:
                        viewer.setSelection(new StructuredSelection(label.getData("_TOOLTIP"))); //$NON-NLS-1$
                        shell.dispose();
                        break;
                    case SWT.MouseExit:
                        shell.dispose();
                        break;
                }
            }
        };

        Listener treeListener = new Listener() {

            Shell tip = null;
            Label label = null;

            private void disposeTip() {
                if (tip != null) {
                    tip.dispose();
                    tip = null;
                    label = null;
                }
            }

            public void handleEvent( Event event ) {
                switch (event.type) {
                    case SWT.MouseMove: {
                        if (tip == null) {
                            break;
                        }
                        TreeItem item = viewer.getTree().getItem(new Point(event.x, event.y));
                        if (item != null && !item.isDisposed()) {
                            Object data = item.getData();
                            if (!label.isDisposed() && data == label.getData("_TOOLTIP")) { //$NON-NLS-1$
                                break;
                            }
                        }
                        disposeTip();
                        break;
                    }
                    case SWT.FocusOut:
                    case SWT.Dispose:
                    case SWT.KeyDown: {
                        disposeTip();
                        break;
                    }
                    case SWT.MouseHover: {
                        TreeItem item = viewer.getTree().getItem(new Point(event.x, event.y));
                        if (item != null) {
                            if (tip != null && !tip.isDisposed()) {
                                tip.dispose();
                            }
                            Object data = item.getData();
                            if (data != null) {
                                String tooltip = CoreStringUtil.Constants.EMPTY_STRING;
                                if (data instanceof TeiidTranslator) {
                                    tooltip = getConnectorToolTip((TeiidTranslator)data);
                                } else if (data instanceof TeiidVdb) {
                                    tooltip = getVDBToolTip((TeiidVdb)data);
                                } else if (data instanceof TeiidViewTreeProvider.TeiidFolder) {
                                    tooltip = ((TeiidViewTreeProvider.TeiidFolder)data).getName();
                                } else if( data instanceof Server ) {
                                	Server server = (Server)data;
                                	String ttip = server.toString();
                                	if( server.getConnectionError() != null ) {
                                		ttip = ttip + "\n\n" + server.getConnectionError(); //$NON-NLS-1$
                                	}
                                	tooltip = ttip;
                                } else {
                                    tooltip = data.toString();
                                }
                                if (tooltip != null) {
                                    tip = new Shell(viewer.getTree().getShell(), SWT.ON_TOP | SWT.TOOL);
                                    FillLayout fillLayout = new FillLayout();
                                    fillLayout.marginHeight = 1;
                                    fillLayout.marginWidth = 1;
                                    tip.setLayout(fillLayout);
                                    label = new Label(tip, SWT.NONE);
                                    label.setForeground(tip.getDisplay().getSystemColor(SWT.COLOR_INFO_FOREGROUND));
                                    label.setBackground(tip.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
                                    label.setData("_TOOLTIP", data); //$NON-NLS-1$
                                    label.setText(tooltip);
                                    label.addListener(SWT.MouseExit, labelListener);
                                    label.addListener(SWT.MouseDown, labelListener);
                                    Point size = tip.computeSize(SWT.DEFAULT, SWT.DEFAULT);
                                    Point pt = viewer.getTree().toDisplay(event.x, event.y);
                                    tip.setBounds(pt.x, pt.y + 26, size.x, size.y);
                                    tip.setVisible(true);
                                }
                            }
                        }
                    }
                }
            }
        };
        viewer.getTree().setToolTipText(""); //$NON-NLS-1$
        viewer.getTree().addListener(SWT.FocusOut, treeListener);
        viewer.getTree().addListener(SWT.Dispose, treeListener);
        viewer.getTree().addListener(SWT.KeyDown, treeListener);
        viewer.getTree().addListener(SWT.MouseMove, treeListener);
        viewer.getTree().addListener(SWT.MouseHover, treeListener);
    }

    /*
     *  Initialize view actions, set icons and action text.
     */
    private void initActions() {
        this.collapseAllAction = new Action() {
            @Override
            public void run() {
                viewer.collapseAll();
            }
        };

        this.collapseAllAction.setImageDescriptor(DqpUiPlugin.getDefault().getImageDescriptor(DqpUiConstants.Images.COLLAPSE_ALL_ICON));
        this.collapseAllAction.setToolTipText(getString("collapseAllAction.tooltip")); //$NON-NLS-1$
        this.collapseAllAction.setEnabled(true);

        this.deleteDataSourceAction = new Action(getString("deleteTeiidDataSourceAction")) { //$NON-NLS-1$
            @Override
            public void run() {
                List<Object> selectedObjs = getSelectedObjects();
                for (Object obj : selectedObjs) {
                    TeiidDataSource tds = (TeiidDataSource)obj;
                    ExecutionAdmin admin = tds.getAdmin();
                    if (admin != null) {
                        try {
                            admin.deleteDataSource(tds.getName());
                        } catch (Exception e) {
                            DqpUiConstants.UTIL.log(IStatus.WARNING,
                                                    e,
                                                    DqpUiConstants.UTIL.getString(PREFIX + "errorDeletingDataSource", tds.getDisplayName())); //$NON-NLS-1$
                        }
                    }
                }

            }
        };

        this.deleteDataSourceAction.setImageDescriptor(DqpUiPlugin.getDefault().getImageDescriptor(DqpUiConstants.Images.DELETE_ICON));
        this.deleteDataSourceAction.setToolTipText(getString("deleteDataSourceAction.tooltip")); //$NON-NLS-1$
        this.deleteDataSourceAction.setEnabled(true);

        this.undeployVdbAction = new Action(getString("undeployVdbAction")) { //$NON-NLS-1$
            @Override
            public void run() {
                List<Object> selectedObjs = getSelectedObjects();
                for (Object obj : selectedObjs) {
                    TeiidVdb vdb = (TeiidVdb)obj;

                    ExecutionAdmin admin = vdb.getAdmin();
                    if (admin != null) {
                        try {
                            admin.undeployVdb(vdb.getVdb());
                        } catch (Exception e) {
                            DqpUiConstants.UTIL.log(IStatus.WARNING,
                                                    e,
                                                    DqpUiConstants.UTIL.getString(PREFIX + "errorUndeployingVdb", vdb.getName())); //$NON-NLS-1$
                        }
                    }
                }

            }
        };

        this.undeployVdbAction.setImageDescriptor(DqpUiPlugin.getDefault().getImageDescriptor(DqpUiConstants.Images.DELETE_ICON));
        this.undeployVdbAction.setToolTipText(getString("undeployVdbAction.tooltip")); //$NON-NLS-1$
        this.undeployVdbAction.setEnabled(true);

        this.executeVdbAction = new Action(getString("executeVdbAction")) { //$NON-NLS-1$
            @Override
            public void run() {
                List<Object> selectedObjs = getSelectedObjects();
                for (Object obj : selectedObjs) {
                    TeiidVdb vdb = (TeiidVdb)obj;

                    ExecutionAdmin admin = vdb.getAdmin();
                    if (admin != null) {
                        try {
                            // admin.undeployVdb(vdb.getVdb());
                            ExecuteVDBAction.executeVdb(admin.getServer(), vdb.getVdb().getName());
                        } catch (Exception e) {
                            DqpUiConstants.UTIL.log(IStatus.WARNING,
                                                    e,
                                                    DqpUiConstants.UTIL.getString("DeployVdbAction.problemDeployingVdbToServer", vdb.getName())); //$NON-NLS-1$
                        }
                    }
                }

            }
        };

        this.executeVdbAction.setImageDescriptor(DqpUiPlugin.getDefault().getImageDescriptor(DqpUiConstants.Images.EXECUTE_VDB));
        this.executeVdbAction.setToolTipText(getString("undeployVdbAction.tooltip")); //$NON-NLS-1$
        this.executeVdbAction.setEnabled(true);

        // the shell used for dialogs that the actions display
        Shell shell = this.getSite().getShell();
        // the reconnect action tries to ping a selected server
        this.reconnectAction = new ReconnectToServerAction(this.viewer);
        this.viewer.addSelectionChangedListener(this.reconnectAction);
        
        // the disconnect action clears the server's object cache, closes connection and null's admin references.
        this.disconnectAction = new DisconnectFromServerAction(this.viewer);
        this.viewer.addSelectionChangedListener(this.disconnectAction);

        // the delete action will delete one or more servers
        this.deleteServerAction = new DeleteServerAction(shell, getServerManager());
        this.viewer.addSelectionChangedListener(this.deleteServerAction);

        // the edit action is only enabled when one server is selected
        this.editServerAction = new EditServerAction(shell, getServerManager());
        this.viewer.addSelectionChangedListener(this.editServerAction);

        // the new server action is always enabled
        this.newServerAction = new NewServerAction(shell, getServerManager());

        this.createDataSourceAction = new Action() {
            @Override
            public void run() {
                if (currentSelectedAdmin != null) {
                    CreateDataSourceAction action = new CreateDataSourceAction();
                    action.setAdmin(currentSelectedAdmin);

                    action.setSelection(new StructuredSelection());

                    action.setEnabled(true);
                    action.run();
                }
            }
        };

        this.createDataSourceAction.setImageDescriptor(DqpUiPlugin.getDefault().getImageDescriptor(DqpUiConstants.Images.SOURCE_BINDING_ICON));
        this.createDataSourceAction.setText(getString("createDataSourceAction.text")); //$NON-NLS-1$
        this.createDataSourceAction.setToolTipText(getString("createDataSourceAction.tooltip")); //$NON-NLS-1$
        this.createDataSourceAction.setEnabled(true);

        // the edit action is only enabled when one server is selected
        this.setDefaultServerAction = new SetDefaultServerAction(getServerManager());
        this.viewer.addSelectionChangedListener(this.setDefaultServerAction);

    }

    /**
     * @see org.eclipse.ui.views.navigator.ResourceNavigator#initDragAndDrop()
     */
    private void initDragAndDrop() {
        // code copied from superclass. only change is to the drag adapter
        int ops = DND.DROP_COPY | DND.DROP_MOVE;
        Transfer[] transfers = new Transfer[] {ResourceTransfer.getInstance()};

        // drop support
        TeiidViewDropAdapter adapter = new TeiidViewDropAdapter(this.viewer);
        adapter.setFeedbackEnabled(false);

        viewer.addDropSupport(ops | DND.DROP_DEFAULT, transfers, adapter);
    }

    private void initKeyListener() {

        // create the adapter
        if (kaKeyAdapter == null) {

            kaKeyAdapter = new KeyAdapter() {

                @Override
                public void keyReleased( KeyEvent event ) {
                    handleKeyEvent(event);
                }
            };
        }

        // add the adapter as a listener
        if (viewer != null) {
            viewer.getControl().removeKeyListener(kaKeyAdapter);
            viewer.getControl().addKeyListener(kaKeyAdapter);

        }
    }
    
    /**
     * {@inheritDoc}
     * 
     * @see org.eclipse.ui.part.ViewPart#init(org.eclipse.ui.IViewSite, org.eclipse.ui.IMemento)
     */
    @Override
    public void init( IViewSite site,
                      IMemento memento ) throws PartInitException {
        // first time a view is opened in a session a memento is passed in. if view is closed and reopened the memento passed in
        // is null. so will save initial non-null memento to use when a view is reopened in same session. however, it will start
        // with the same settings that the session started with.
        if ((viewMemento == null) && (memento != null)) {
            viewMemento = memento;
        }

        super.init(site, memento);
    }

    /**
     * @return <code>true</code> if preview is enabled
     */
    boolean isPreviewEnabled() {
        PreviewManager previewManager = getServerManager().getPreviewManager();
        return ((previewManager != null) && previewManager.isPreviewEnabled());
    }
    
    private void restoreLocalPullDown() {
        // need to check for null since first time view is opened in a new workspace there won't be previous state
        if (viewMemento != null) {
            IMemento menuMemento = viewMemento.getChild(MENU_MEMENTO);
            
            // also need to check for null here if running an existing workspace that didn't have this memento created
            if (menuMemento != null) {
                this.treeProvider.setShowPreviewDataSources(menuMemento.getBoolean(SHOW_PREVIEW_DATA_SOURCES));
                this.treeProvider.setShowPreviewVdbs(menuMemento.getBoolean(SHOW_PREVIEW_VDBS));
                this.treeProvider.setShowTranslators(menuMemento.getBoolean(SHOW_TRANSLATORS));
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see org.eclipse.ui.part.ViewPart#saveState(org.eclipse.ui.IMemento)
     */
    @Override
    public void saveState( IMemento memento ) {
        IMemento menuMemento = memento.createChild(MENU_MEMENTO);
        menuMemento.putBoolean(SHOW_PREVIEW_DATA_SOURCES, this.showPreviewDataSourcesAction.isChecked());
        menuMemento.putBoolean(SHOW_PREVIEW_VDBS, this.showPreviewVdbsAction.isChecked());
        menuMemento.putBoolean(SHOW_TRANSLATORS, this.showTranslatorsAction.isChecked());
        super.saveState(memento);
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    @Override
    public void setFocus() {
        viewer.getControl().setFocus();
    }
    
    /**
     * @param object the object needing to be updated in the viewer
     */
    public void updateLabel(Object object) {
        this.viewer.update(object, null);
    }

    /**
     * Updates Eclipse's Status line based on current selection in Teiid View
     * 
     * @param selection the current viewer selection (never <code>null</code>)
     */
    private void updateStatusLine( IStructuredSelection selection ) {
        // If no selection or mutli-selection
        String msg = StringUtilities.EMPTY_STRING;

        if (selection.size() == 1) {
            Object selectedObject = selection.getFirstElement();

            if (selectedObject instanceof Server) {
                msg = getString("statusBar.server.label", ((Server)selectedObject).toString()); //$NON-NLS-1$
            } else if (selectedObject instanceof TeiidTranslator) {
                msg = getString("statusBar.translator.label", ((TeiidTranslator)selectedObject).getName()); //$NON-NLS-1$
            } else if (selectedObject instanceof TeiidVdb) {
                msg = getString("statusBar.vdb.label", ((TeiidVdb)selectedObject).getName()); //$NON-NLS-1$
            } else if (selectedObject instanceof TeiidDataSource) {
                msg = getString("statusBar.datasource.label", ((TeiidDataSource)selectedObject).getDisplayName()); //$NON-NLS-1$
            }
        }
        getViewSite().getActionBars().getStatusLineManager().setMessage(msg);
    }

    class NameSorter extends ViewerSorter {
    }

}
