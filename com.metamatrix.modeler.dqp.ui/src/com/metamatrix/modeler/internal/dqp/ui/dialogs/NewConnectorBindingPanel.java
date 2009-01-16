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
package com.metamatrix.modeler.internal.dqp.ui.dialogs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.views.properties.PropertySheetPage;
import com.metamatrix.common.config.api.ComponentType;
import com.metamatrix.common.config.api.ConnectorBinding;
import com.metamatrix.common.namedobject.BaseID;
import com.metamatrix.core.event.IChangeListener;
import com.metamatrix.core.event.IChangeNotifier;
import com.metamatrix.core.util.I18nUtil;
import com.metamatrix.core.util.StringUtil;
import com.metamatrix.modeler.dqp.DqpPlugin;
import com.metamatrix.modeler.dqp.internal.config.VdbDefnHelper;
import com.metamatrix.modeler.dqp.util.ModelerDqpUtils;
import com.metamatrix.modeler.internal.dqp.ui.config.ConnectorBindingsPropertySourceProvider;
import com.metamatrix.ui.internal.util.WidgetFactory;
import com.metamatrix.ui.internal.util.WidgetUtil;
import com.metamatrix.vdb.edit.VdbContextEditor;
import com.metamatrix.vdb.internal.edit.InternalVdbEditingContext;

/**
 * @since 4.2
 */
public class NewConnectorBindingPanel extends BaseNewConnectorBindingPanel {

    private static final String PREFIX = I18nUtil.getPropertyPrefix(NewConnectorBindingPanel.class);

    Map componentTypes;
    List sortedTypes;

    private Text nameField;
    private Combo typeCombo;
    private PropertySheetPage propertyPage;
    private InternalVdbEditingContext vdbContext;
    private VdbContextEditor vdbContextEditor;
    BaseID currentTypeID;
    ComponentType currentType;

    private ConnectorBinding binding;
    private String originalName;

    private IChangeListener configListener;

    private Button btnShowExpertProps;

    private ConnectorBindingsPropertySourceProvider sourceProvider;

    /**
     * @param parent
     * @param style
     * @since 4.2
     */
    public NewConnectorBindingPanel( Composite parent,
                                     String name,
                                     ComponentType type,
                                     InternalVdbEditingContext theContext ) {
        super(parent);
        this.currentType = type;
        this.originalName = name;
        this.vdbContext = theContext;
        this.vdbContextEditor = null;

        // register to receive configuration changes
        this.configListener = new IChangeListener() {
            public void stateChanged( IChangeNotifier theSource ) {
                handleConfigurationChanged();
            }
        };
        DqpPlugin.getInstance().getConfigurationManager().addChangeListener(this.configListener);

        buildControls();
    }

    public NewConnectorBindingPanel( Composite parent,
                                     String name,
                                     ComponentType type,
                                     VdbContextEditor theContext ) {
        super(parent);
        this.currentType = type;
        this.originalName = name;
        this.vdbContext = null;
        this.vdbContextEditor = theContext;

        // register to receive configuration changes
        this.configListener = new IChangeListener() {
            public void stateChanged( IChangeNotifier theSource ) {
                handleConfigurationChanged();
            }
        };
        DqpPlugin.getInstance().getConfigurationManager().addChangeListener(this.configListener);

        buildControls();
    }

    /**
     * @see com.metamatrix.modeler.internal.dqp.ui.dialogs.BaseNewConnectorBindingPanel#getI18nPrefix()
     * @since 4.3
     */
    @Override
    protected String getI18nPrefix() {
        return PREFIX;
    }

    /**
     * @see com.metamatrix.modeler.internal.dqp.ui.dialogs.BaseNewConnectorBindingPanel#getStatus()
     * @since 4.3
     */
    @Override
    protected IStatus getStatus() {
        // validate name first
        IStatus result = ModelerDqpUtils.isValidBindingName(getNewBindingName());

        if (result.getSeverity() != IStatus.ERROR) {
            int severity = IStatus.ERROR;
            String msg = "Message has not been set"; //$NON-NLS-1$
            VdbDefnHelper helper = getVdbDefnHelper();

            if (ModelerDqpUtils.isUniqueBindingName(getNewBindingName(), helper.getVdbDefn())) {
                // name is valid, unique so check to make sure a type has been selected
                if (this.typeCombo.getSelectionIndex() == -1) {
                    msg = getString("noConnectorTypeMsg"); //$NON-NLS-1$
                } else {
                    // everything is good
                    severity = IStatus.OK;
                    msg = UTIL.getString(PREFIX + "okMsg", getNewBindingName()); //$NON-NLS-1$
                }
            } else {
                // binding with that name already exists //MyCode : need check in the future
                severity = IStatus.ERROR;
                msg = UTIL.getString(PREFIX + "duplicateNameMsg", getNewBindingName()); //$NON-NLS-1$
            }

            result = BaseNewConnectorBindingPanel.createStatus(severity, msg);
        }

        return result;
    }

    /**
     * @see com.metamatrix.modeler.internal.dqp.ui.dialogs.BaseNewConnectorBindingPanel#getConnectorBinding()
     * @since 5.0
     */
    @Override
    public ConnectorBinding getConnectorBinding() {
        return getConnectorBinding(true);
    }

    /**
     * Obtains the binding.
     * 
     * @param theAddToConfigurationFlag the flag indicating if a new binding should be added to the configuration
     * @return the binding
     * @since 5.0
     */
    private ConnectorBinding getConnectorBinding( boolean theAddToConfigurationFlag ) {
        ConnectorBinding result = null;
        boolean createBinding = false;
        boolean useExisting = false;

        // bindings must have a type
        if (this.currentType != null) {
            // bindings must have a name
            if (!StringUtil.isEmpty(getNewBindingName())) {
                if (this.binding == null) {
                    createBinding = true;
                } else if (this.binding.getComponentTypeID() == this.currentTypeID) {
                    // types are the same. only create if adding to configuration
                    createBinding = theAddToConfigurationFlag;
                    useExisting = true;
                    result = this.binding;
                } else {
                    // always create if type changed
                    createBinding = true;
                }
            } else if ((this.binding != null) && !(this.binding.getComponentTypeID() == this.currentTypeID)) {
                // type change when name is empty
                this.binding = null;
            }
        } else {
            // make sure all other fields are cleared
            this.currentTypeID = null;
            this.binding = null;
        }

        if (createBinding) {
            if (theAddToConfigurationFlag && useExisting) {
                // create from existing binding
                try {
                    VdbDefnHelper helper = getVdbDefnHelper();
                    this.binding = helper.createConnectorBinding(this.binding, getNewBindingName());
                    result = this.binding;
                } catch (Exception theException) {
                    UTIL.log(theException);
                    theException.printStackTrace();
                    MessageDialog.openError(getShell(), getString("errorDialog.creatingConnector.title"), //$NON-NLS-1$
                                            getString("errorDialog.creatingConnector.msg")); //$NON-NLS-1$
                }
            } else {
                // create new binding
                try {
                    VdbDefnHelper helper = getVdbDefnHelper();
                    this.binding = helper.createConnectorBinding(this.currentType, getNewBindingName(), theAddToConfigurationFlag);
                    result = this.binding;
                } catch (Exception theException) {
                    UTIL.log(theException);
                    theException.printStackTrace();
                    MessageDialog.openError(getShell(), getString("errorDialog.creatingConnector.title"), //$NON-NLS-1$
                                            getString("errorDialog.creatingConnector.msg")); //$NON-NLS-1$
                }
            }
        }

        return result;
    }

    private String getNewBindingName() {
        return this.nameField.getText();
    }

    private void buildControls() {
        GridLayout layout = new GridLayout();
        layout.numColumns = 3;
        this.setLayout(layout);
        this.setLayoutData(new GridData(GridData.FILL_BOTH));

        WidgetFactory.createLabel(this, getString("lblName")); //$NON-NLS-1$
        this.nameField = WidgetFactory.createTextField(this, GridData.FILL_HORIZONTAL, 2);
        this.nameField.setText((this.originalName == null) ? "" : this.originalName); //$NON-NLS-1$
        this.nameField.addKeyListener(new KeyListener() {
            public void keyPressed( KeyEvent e ) {
            }

            public void keyReleased( KeyEvent e ) {
                handleBindingNameChanged();
            }
        });

        WidgetFactory.createLabel(this, getString("lblType")); //$NON-NLS-1$
        this.typeCombo = WidgetFactory.createCombo(this, SWT.READ_ONLY, GridData.FILL_HORIZONTAL);
        this.typeCombo.addSelectionListener(new SelectionListener() {
            public void widgetSelected( SelectionEvent e ) {
                connectorTypeChanged();
            }

            public void widgetDefaultSelected( SelectionEvent e ) {
                connectorTypeChanged();
            }
        });

        // load combo
        handleConfigurationChanged();

        buildProperties(this);

        // add dispose listener because when I overrode dispose() it never got called
        addDisposeListener(new DisposeListener() {
            public void widgetDisposed( DisposeEvent theEvent ) {
                handleDispose();
            }
        });
    }

    /**
     * Handler for when the button to show/hide advanced/expert properties is clicked.
     * 
     * @since 5.0.2
     */
    void handleShowPropertiesSelected() {
        this.sourceProvider.setShowExpertProperties(this.btnShowExpertProps.getSelection());
        this.propertyPage.refresh();
    }

    private void buildProperties( Composite parent ) {
        Composite c = WidgetFactory.createPanel(parent, SWT.NONE, GridData.FILL_BOTH, 3, 2);
        WidgetFactory.createLabel(c, getString("lblProperties")); //$NON-NLS-1$

        // toggle button to show/hide expert properties
        this.btnShowExpertProps = new Button(c, SWT.CHECK);
        this.btnShowExpertProps.setEnabled(true);
        this.btnShowExpertProps.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
        this.btnShowExpertProps.setText(getString("btnShowExpertProps.text")); //$NON-NLS-1$
        this.btnShowExpertProps.setToolTipText(getString("btnShowExpertProps.tooTip")); //$NON-NLS-1$
        this.btnShowExpertProps.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected( SelectionEvent theE ) {
                handleShowPropertiesSelected();
            }
        });

        this.propertyPage = new PropertySheetPage();
        this.sourceProvider = getConnectorBindingsPropertySourceProvider();
        this.sourceProvider.setEditable(true);
        this.propertyPage.setPropertySourceProvider(sourceProvider);
        this.propertyPage.createControl(c);
        Control propertyControl = this.propertyPage.getControl();
        GridData gid = new GridData(GridData.FILL_BOTH);
        gid.grabExcessHorizontalSpace = gid.grabExcessVerticalSpace = true;
        gid.horizontalSpan = 2;
        propertyControl.setLayoutData(gid);
        c.layout();
    }

    void connectorTypeChanged() {
        int index = this.typeCombo.getSelectionIndex();

        if (index != -1) {
            this.currentType = (ComponentType)this.componentTypes.get(sortedTypes.get(index));

            if ((this.currentTypeID == null) || !(this.currentTypeID == this.currentType.getID())) {
                this.currentTypeID = this.currentType.getID();
            }
        }

        // getting the binding will cause a new binding to be created
        getConnectorBinding(false);

        refreshPropertyPage();

        fireChangeEvent();
    }

    void handleBindingNameChanged() {
        boolean noCurrentBinding = (this.binding == null);

        // call this method to get a binding created if one is needed
        if ((getConnectorBinding(false) != null) && noCurrentBinding) {
            // need to refresh properties panel if binding created after name change
            refreshPropertyPage();
        }

        // alert listeners
        fireChangeEvent();
    }

    void handleConfigurationChanged() {
        if (!this.typeCombo.isDisposed()) {
            loadConnectorTypes();

            final Combo combo = this.typeCombo;
            final String selection = ((this.currentType == null) ? "" : this.currentType.getName()); //$NON-NLS-1$

            getDisplay().asyncExec(new Runnable() {
                public void run() {
                    if (!combo.isDisposed()) {
                        WidgetUtil.setComboItems(combo, NewConnectorBindingPanel.this.sortedTypes, null, false, selection);
                        ComponentType type = (ComponentType)NewConnectorBindingPanel.this.componentTypes.get(selection);
                        if (type != null) {
                            NewConnectorBindingPanel.this.currentType = type;
                            NewConnectorBindingPanel.this.currentTypeID = type.getID();
                        }

                        connectorTypeChanged();
                    }
                }
            });
        }
    }

    /**
     * Cleanup for when disposed.
     * 
     * @since 5.0
     */
    void handleDispose() {
        DqpPlugin.getInstance().getConfigurationManager().removeChangeListener(this.configListener);
    }

    private void loadConnectorTypes() {
        this.componentTypes = ModelerDqpUtils.getConnectorTypes();
        this.sortedTypes = new ArrayList(new TreeSet(this.componentTypes.keySet()));

        // see if currentType is in new list of types
        if (this.currentType != null) {
            boolean foundIt = false;

            for (int size = this.sortedTypes.size(), i = 0; i < size; ++i) {
                String name = (String)this.sortedTypes.get(i);

                if (this.currentType.getName().equals(name)) {
                    foundIt = true;
                    break;
                }
            }

            if (!foundIt) {
                this.currentType = null;
            }
        }
    }

    private void refreshPropertyPage() {
        IStructuredSelection selection = StructuredSelection.EMPTY;

        if (this.binding != null) {
            selection = new StructuredSelection(this.binding);
        }

        if (this.propertyPage != null) {
            // notify property page of new selection
            this.propertyPage.selectionChanged(null, selection);

            // pack the property page columns. couldn't find a better way to do this.
            Control c = this.propertyPage.getControl();

            if (c instanceof Tree) {
                TreeColumn[] cols = ((Tree)c).getColumns();

                for (int i = 0; i < cols.length; ++i) {
                    cols[i].pack();
                }
            }
        }
    }

    @Override
    public boolean setFocus() {
        return this.typeCombo.setFocus();
    }

    private VdbDefnHelper getVdbDefnHelper() {
        VdbDefnHelper helper = null;
        if (this.vdbContext != null) {
            helper = DqpPlugin.getInstance().getVdbDefnHelper(this.vdbContext);
        } else if (this.vdbContextEditor != null) {
            helper = DqpPlugin.getInstance().getVdbDefnHelper(this.vdbContextEditor);
        }
        return helper;
    }

    private ConnectorBindingsPropertySourceProvider getConnectorBindingsPropertySourceProvider() {
        ConnectorBindingsPropertySourceProvider sourceProvider = null;
        if (this.vdbContext != null) {
            sourceProvider = new ConnectorBindingsPropertySourceProvider(this.vdbContext);
        } else if (this.vdbContextEditor != null) {
            sourceProvider = new ConnectorBindingsPropertySourceProvider(this.vdbContextEditor);
        }
        return sourceProvider;
    }

}
