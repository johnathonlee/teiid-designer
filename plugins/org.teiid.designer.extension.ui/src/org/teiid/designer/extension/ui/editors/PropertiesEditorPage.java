/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.teiid.designer.extension.ui.editors;

import static org.teiid.designer.extension.ui.UiConstants.EditorIds.MED_PROPERTIES_PAGE;
import static org.teiid.designer.extension.ui.UiConstants.Form.SECTION_STYLE;
import static org.teiid.designer.extension.ui.UiConstants.Form.VIEWER_STYLE;
import static org.teiid.designer.extension.ui.UiConstants.ImageIds.ADD_METACLASS;
import static org.teiid.designer.extension.ui.UiConstants.ImageIds.ADD_PROPERTY;
import static org.teiid.designer.extension.ui.UiConstants.ImageIds.CHECK_MARK;
import static org.teiid.designer.extension.ui.UiConstants.ImageIds.EDIT_METACLASS;
import static org.teiid.designer.extension.ui.UiConstants.ImageIds.EDIT_PROPERTY;
import static org.teiid.designer.extension.ui.UiConstants.ImageIds.MED_EDITOR;
import static org.teiid.designer.extension.ui.UiConstants.ImageIds.REMOVE_METACLASS;
import static org.teiid.designer.extension.ui.UiConstants.ImageIds.REMOVE_PROPERTY;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.teiid.designer.extension.definition.ModelExtensionDefinition.PropertyName;
import org.teiid.designer.extension.definition.ModelExtensionDefinitionValidator;
import org.teiid.designer.extension.properties.ModelExtensionPropertyDefinition;
import org.teiid.designer.extension.ui.Activator;
import org.teiid.designer.extension.ui.Messages;

import com.metamatrix.core.util.ArrayUtil;
import com.metamatrix.core.util.CoreStringUtil;
import com.metamatrix.modeler.internal.ui.forms.FormUtil;
import com.metamatrix.ui.internal.util.WidgetUtil;

/**
 * 
 */
public class PropertiesEditorPage extends MedEditorPage {

    private Button btnAddMetaclass;
    private Button btnAddProperty;
    private Button btnEditMetaclass;
    private Button btnEditProperty;
    private Button btnRemoveMetaclass;
    private Button btnRemoveProperty;

    private TableViewer metaclassViewer;
    private TableViewer propertyViewer;

    private final ErrorMessage metaclassError;
    private final ErrorMessage propertyError;

    public PropertiesEditorPage( ModelExtensionDefinitionEditor medEditor ) {
        super(medEditor, MED_PROPERTIES_PAGE, Messages.medEditorPropertiesPageTitle);
        this.metaclassError = new ErrorMessage();
        this.propertyError = new ErrorMessage();
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.teiid.designer.extension.ui.editors.MedEditorPage#createBody(org.eclipse.swt.widgets.Composite,
     *      org.eclipse.ui.forms.widgets.FormToolkit)
     */
    @SuppressWarnings("unused")
    @Override
    protected void createBody( Composite body,
                               FormToolkit toolkit ) {
        BODY: {
            body.setLayout(new GridLayout());
            body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        }

        final Composite finalTop;

        TOP: {
            Composite top = toolkit.createComposite(body, SWT.NONE);
            top.setLayout(new GridLayout());
            top.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            finalTop = top;
        }

        final Composite finalBottom;

        BOTTOM: {
            Composite bottom = toolkit.createComposite(body, SWT.NONE);
            bottom.setLayout(new GridLayout());
            bottom.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            finalBottom = bottom;
        }

        SECTIONS: {
            Section metaclassSection = createExtendedMetaclassSection(finalTop, toolkit);
            Section propertiesSection = createPropertiesSection(finalBottom, toolkit);
            propertiesSection.descriptionVerticalSpacing = metaclassSection.getTextClientHeightDifference();
        }

        // populate UI
        this.metaclassViewer.setInput(this);
        WidgetUtil.pack(this.metaclassViewer);
    }

    private void configureColumn( TableViewerColumn viewerColumn,
                                  int columnIndex,
                                  String headerText,
                                  String headerToolTip,
                                  boolean resizable ) {
        viewerColumn.setLabelProvider(new PropertyLabelProvider(columnIndex));

        TableColumn column = viewerColumn.getColumn();
        column.setText(headerText);
        column.setToolTipText(headerToolTip);
        column.setMoveable(false);
        column.setResizable(resizable);
        column.pack();
    }

    @SuppressWarnings("unused")
    private Section createExtendedMetaclassSection( Composite parent,
                                                    FormToolkit toolkit ) {
        final Section finalSection;

        SECTION: {
            Section section = FormUtil.createSection(getManagedForm(), toolkit, parent,
                                                     Messages.overviewPageExtendedMetaclassTitle,
                                                     Messages.overviewPageExtendedMetaclassDescription, SECTION_STYLE, true);
            finalSection = section;

            // configure section toolbar
            Button[] buttons = FormUtil.createSectionToolBar(finalSection, toolkit,
                                                             new Image[] { Activator.getDefault().getImage(ADD_METACLASS),
                                                                     Activator.getDefault().getImage(EDIT_METACLASS),
                                                                     Activator.getDefault().getImage(REMOVE_METACLASS) });

            // configure add button
            this.btnAddMetaclass = buttons[0];
            this.btnAddMetaclass.addSelectionListener(new SelectionAdapter() {

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
                 */
                @Override
                public void widgetSelected( SelectionEvent e ) {
                    handleAddMetaclass();
                }
            });
            this.btnAddMetaclass.setToolTipText(Messages.propertiesPageAddMetaclassButtonToolTip);

            // configure edit button
            this.btnEditMetaclass = buttons[1];
            this.btnEditMetaclass.setEnabled(false);
            this.btnEditMetaclass.addSelectionListener(new SelectionAdapter() {

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
                 */
                @Override
                public void widgetSelected( SelectionEvent e ) {
                    handleEditMetaclass();
                }
            });
            this.btnEditMetaclass.setToolTipText(Messages.propertiesPageEditMetaclassButtonToolTip);

            // configure remove button
            this.btnRemoveMetaclass = buttons[2];
            this.btnRemoveMetaclass.setEnabled(false);
            this.btnRemoveMetaclass.addSelectionListener(new SelectionAdapter() {

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
                 */
                @Override
                public void widgetSelected( SelectionEvent e ) {
                    handleRemoveMetaclass();
                }
            });
            this.btnRemoveMetaclass.setToolTipText(Messages.propertiesPageRemoveMetaclassButtonToolTip);
        }

        final Composite finalContainer;

        CONTAINER: {
            Composite container = toolkit.createComposite(finalSection);
            finalContainer = container;
            container.setLayout(new GridLayout());
            container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            finalSection.setClient(container);
        }

        VIEWER: {
            Table table = toolkit.createTable(finalContainer, VIEWER_STYLE);
            table.setLayoutData(new GridLayout());
            table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            ((GridData)table.getLayoutData()).heightHint = table.getItemHeight() * 5;
            this.metaclassError.setControl(table);

            this.metaclassViewer = new TableViewer(table);
            this.metaclassViewer.setContentProvider(new IStructuredContentProvider() {

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
                 */
                @Override
                public void dispose() {
                    // nothing to do
                }

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
                 */
                @Override
                public Object[] getElements( Object inputElement ) {
                    return getMed().getExtendedMetaclasses();
                }

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object,
                 *      java.lang.Object)
                 */
                @Override
                public void inputChanged( Viewer viewer,
                                          Object oldInput,
                                          Object newInput ) {
                    // nothing to do
                }
            });
            this.metaclassViewer.setLabelProvider(new LabelProvider());
            this.metaclassViewer.addDoubleClickListener(new IDoubleClickListener() {

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.jface.viewers.IDoubleClickListener#doubleClick(org.eclipse.jface.viewers.DoubleClickEvent)
                 */
                @Override
                public void doubleClick( DoubleClickEvent event ) {
                    handleEditMetaclass();
                }
            });
            this.metaclassViewer.addSelectionChangedListener(new ISelectionChangedListener() {

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.jface.viewers.ISelectionChangedListener#selectionChanged(org.eclipse.jface.viewers.SelectionChangedEvent)
                 */
                @Override
                public void selectionChanged( SelectionChangedEvent event ) {
                    handleMetaclassSelected();
                }
            });
        }

        return finalSection;
    }

    @SuppressWarnings("unused")
    private Section createPropertiesSection( Composite parent,
                                             FormToolkit toolkit ) {
        final Section finalSection;

        SECTION: {
            Section section = FormUtil.createSection(getManagedForm(), toolkit, parent,
                                                     Messages.propertiesPageExtensionPropertiesTitle,
                                                     Messages.propertiesPageExtensionPropertiesDescription, SECTION_STYLE, true);
            finalSection = section;

            // configure section toolbar
            Button[] buttons = FormUtil.createSectionToolBar(finalSection, toolkit,
                                                             new Image[] { Activator.getDefault().getImage(ADD_PROPERTY),
                                                                     Activator.getDefault().getImage(EDIT_PROPERTY),
                                                                     Activator.getDefault().getImage(REMOVE_PROPERTY) });

            // configure add button
            this.btnAddProperty = buttons[0];
            this.btnAddProperty.addSelectionListener(new SelectionAdapter() {

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
                 */
                @Override
                public void widgetSelected( SelectionEvent e ) {
                    handleAddProperty();
                }
            });
            this.btnAddProperty.setToolTipText(Messages.propertiesPageAddPropertyButtonToolTip);

            // configure edit button
            this.btnEditProperty = buttons[1];
            this.btnEditProperty.setEnabled(false);
            this.btnEditProperty.addSelectionListener(new SelectionAdapter() {

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
                 */
                @Override
                public void widgetSelected( SelectionEvent e ) {
                    handleEditProperty();
                }
            });
            this.btnEditProperty.setToolTipText(Messages.propertiesPageEditPropertyButtonToolTip);

            // configure remove button
            this.btnRemoveProperty = buttons[2];
            this.btnRemoveProperty.setEnabled(false);
            this.btnRemoveProperty.addSelectionListener(new SelectionAdapter() {

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
                 */
                @Override
                public void widgetSelected( SelectionEvent e ) {
                    handleRemoveProperty();
                }
            });
            this.btnRemoveProperty.setToolTipText(Messages.propertiesPageRemovePropertyButtonToolTip);
        }

        final Composite finalContainer;

        CONTAINER: {
            Composite container = toolkit.createComposite(finalSection);
            finalContainer = container;
            container.setLayout(new GridLayout());
            container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            finalSection.setClient(container);
        }

        VIEWER: {
            Table table = toolkit.createTable(finalContainer, VIEWER_STYLE);
            table.setHeaderVisible(true);
            table.setLinesVisible(true);
            table.setLayoutData(new GridLayout());
            table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            ((GridData)table.getLayoutData()).heightHint = table.getItemHeight() * 10;
            this.propertyError.setControl(table);

            this.propertyViewer = new TableViewer(table);
            this.propertyViewer.setContentProvider(new IStructuredContentProvider() {

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
                 */
                @Override
                public void dispose() {
                    // nothing to do
                }

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
                 */
                @Override
                public Object[] getElements( Object inputElement ) {
                    String metaclass = getSelectedMetaclass();

                    if (CoreStringUtil.isEmpty(metaclass)) {
                        return ArrayUtil.Constants.EMPTY_ARRAY;
                    }

                    return getMed().getPropertyDefinitions(metaclass).toArray();
                }

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object,
                 *      java.lang.Object)
                 */
                @Override
                public void inputChanged( Viewer viewer,
                                          Object oldInput,
                                          Object newInput ) {
                    // nothing to do
                }
            });
            this.propertyViewer.addDoubleClickListener(new IDoubleClickListener() {

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.jface.viewers.IDoubleClickListener#doubleClick(org.eclipse.jface.viewers.DoubleClickEvent)
                 */
                @Override
                public void doubleClick( DoubleClickEvent event ) {
                    handleEditProperty();
                }
            });
            this.propertyViewer.addSelectionChangedListener(new ISelectionChangedListener() {

                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.jface.viewers.ISelectionChangedListener#selectionChanged(org.eclipse.jface.viewers.SelectionChangedEvent)
                 */
                @Override
                public void selectionChanged( SelectionChangedEvent event ) {
                    handlePropertySelected();
                }
            });

            // create table columns
            TableViewerColumn column = new TableViewerColumn(this.propertyViewer, SWT.LEFT);
            configureColumn(column, ColumnIndexes.SIMPLE_ID, ColumnHeaders.SIMPLE_ID, ColumnToolTips.SIMPLE_ID, true);

            column = new TableViewerColumn(this.propertyViewer, SWT.LEFT);
            configureColumn(column, ColumnIndexes.RUNTIME_TYPE, ColumnHeaders.RUNTIME_TYPE, ColumnToolTips.RUNTIME_TYPE, true);

            column = new TableViewerColumn(this.propertyViewer, SWT.CENTER);
            configureColumn(column, ColumnIndexes.REQUIRED, ColumnHeaders.REQUIRED, ColumnToolTips.REQUIRED, false);

            column = new TableViewerColumn(this.propertyViewer, SWT.CENTER);
            configureColumn(column, ColumnIndexes.MODIFIABLE, ColumnHeaders.MODFIFIABLE, ColumnToolTips.MODFIFIABLE, false);

            column = new TableViewerColumn(this.propertyViewer, SWT.CENTER);
            configureColumn(column, ColumnIndexes.ADVANCED, ColumnHeaders.ADVANCED, ColumnToolTips.ADVANCED, false);

            column = new TableViewerColumn(this.propertyViewer, SWT.CENTER);
            configureColumn(column, ColumnIndexes.MASKED, ColumnHeaders.MASKED, ColumnToolTips.MASKED, false);

            column = new TableViewerColumn(this.propertyViewer, SWT.CENTER);
            configureColumn(column, ColumnIndexes.INDEXED, ColumnHeaders.INDEXED, ColumnToolTips.INDEXED, false);

            column = new TableViewerColumn(this.propertyViewer, SWT.LEFT);
            configureColumn(column, ColumnIndexes.DEFAULT_VALUE, ColumnHeaders.DEFAULT_VALUE, ColumnToolTips.DEFAULT_VALUE, true);

            column = new TableViewerColumn(this.propertyViewer, SWT.LEFT);
            configureColumn(column, ColumnIndexes.ALLOWED_VALUES, ColumnHeaders.ALLOWED_VALUES, ColumnToolTips.ALLOWED_VALUES, true);

            column = new TableViewerColumn(this.propertyViewer, SWT.LEFT);
            configureColumn(column, ColumnIndexes.DISPLAY_NAME, ColumnHeaders.DISPLAY_NAME, ColumnToolTips.DISPLAY_NAME, true);

            column = new TableViewerColumn(this.propertyViewer, SWT.LEFT);
            configureColumn(column, ColumnIndexes.DESCRIPTION, ColumnHeaders.DESCRIPTION, ColumnToolTips.DESCRIPTION, true);
        }

        return finalSection;
    }

    Collection<String> getExistingPropertyIds( String metaclassName ) {
        Collection<ModelExtensionPropertyDefinition> propDefns = getMed().getPropertyDefinitions(metaclassName);

        if (propDefns == null) {
            return new ArrayList<String>(0);
        }

        Collection<String> result = new ArrayList<String>(propDefns.size());

        for (ModelExtensionPropertyDefinition propDefn : propDefns) {
            result.add(propDefn.getSimpleId());
        }

        return result;
    }

    String getSelectedMetaclass() {
        IStructuredSelection selection = (IStructuredSelection)this.metaclassViewer.getSelection();
        return (selection.isEmpty() ? null : (String)selection.getFirstElement());
    }

    ModelExtensionPropertyDefinition getSelectedProperty() {
        IStructuredSelection selection = (IStructuredSelection)this.propertyViewer.getSelection();
        return (selection.isEmpty() ? null : (ModelExtensionPropertyDefinition)selection.getFirstElement());
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.eclipse.ui.part.EditorPart#getTitleToolTip()
     */
    @Override
    public String getTitleToolTip() {
        return Messages.medEditorPropertiesPageToolTip;
    }

    void handleAddMetaclass() {
        EditMetaclassDialog dialog = new EditMetaclassDialog(getShell(), Arrays.asList(getMed().getExtendedMetaclasses()));
        dialog.create();
        dialog.getShell().pack();

        if (dialog.open() == Window.OK) {
            String newMetaclassName = dialog.getMetaclassName();

            // select new metaclass
            if (getMed().addMetaclass(newMetaclassName)) {
                this.metaclassViewer.setSelection(new StructuredSelection(newMetaclassName));
            }
        }
    }

    void handleAddProperty() {
        assert (getSelectedMetaclass() != null) : "Selected metaclass is null and shouldn't be"; //$NON-NLS-1$
        String metaclassName = getSelectedMetaclass();
        EditPropertyDialog dialog = new EditPropertyDialog(getShell(),
                                                           getMed(),
                                                           metaclassName,
                                                           getExistingPropertyIds(metaclassName));
        dialog.create();
        dialog.getShell().pack();

        if (dialog.open() == Window.OK) {
            ModelExtensionPropertyDefinition newPropDefn = dialog.getPropertyDefinition();

            // select new property definition
            if (getMed().addPropertyDefinition(metaclassName, newPropDefn)) {
                this.propertyViewer.setSelection(new StructuredSelection(newPropDefn));
            }
        }
    }

    void handleEditMetaclass() {
        assert !CoreStringUtil.isEmpty(getSelectedMetaclass()) : "Edit metaclass button is enabled and there is no metaclass selected"; //$NON-NLS-1$
        String selectedMetaclassName = getSelectedMetaclass();
        EditMetaclassDialog dialog = new EditMetaclassDialog(getShell(),
                                                             Arrays.asList(getMed().getExtendedMetaclasses()),
                                                             selectedMetaclassName);
        dialog.create();
        dialog.getShell().pack();

        if (dialog.open() == Window.OK) {
            Collection<ModelExtensionPropertyDefinition> propDefns = getMed().removeMetaclass(selectedMetaclassName);
            String modifiedMetaclassName = dialog.getMetaclassName();
            getMed().addPropertyDefinitions(modifiedMetaclassName, propDefns);
        }
    }

    void handleEditProperty() {
        assert !CoreStringUtil.isEmpty(getSelectedMetaclass()) : "Edit property button is enabled and there is no metaclass selected"; //$NON-NLS-1$
        assert (getSelectedProperty() != null) : "Edit property button is enabled and there is no property selected"; //$NON-NLS-1$

        String metaclassName = getSelectedMetaclass();
        ModelExtensionPropertyDefinition selectedPropDefn = getSelectedProperty();
        EditPropertyDialog dialog = new EditPropertyDialog(getShell(),
                                                           getMed(),
                                                           metaclassName,
                                                           getExistingPropertyIds(metaclassName),
                                                           selectedPropDefn);
        dialog.create();
        dialog.getShell().pack();

        if (dialog.open() == Window.OK) {
            ModelExtensionPropertyDefinition modifiedPropDefn = dialog.getPropertyDefinition();
            getMed().removePropertyDefinition(metaclassName, selectedPropDefn);
            getMed().addPropertyDefinition(metaclassName, modifiedPropDefn);
        }
    }

    void handleMetaclassSelected() {
        boolean enable = (getSelectedMetaclass() != null);

        if (this.btnRemoveMetaclass.getEnabled() != enable) {
            this.btnRemoveMetaclass.setEnabled(enable);
        }

        if (this.btnEditMetaclass.getEnabled() != enable) {
            this.btnEditMetaclass.setEnabled(enable);
        }

        // alert property viewer the selection changed
        this.propertyViewer.setInput(this);
        WidgetUtil.pack(this.propertyViewer);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.teiid.designer.extension.ui.editors.MedEditorPage#handlePropertyChanged(java.beans.PropertyChangeEvent)
     */
    @Override
    protected void handlePropertyChanged( PropertyChangeEvent e ) {
        String propName = e.getPropertyName();

        if (PropertyName.METACLASS.toString().equals(propName)) {
            validateMetaclasses();
            this.metaclassViewer.refresh();
        } else if (PropertyName.PROPERTY_DEFINITION.toString().equals(propName)) {
            validatePropertyDefinitions();
            this.propertyViewer.refresh();
        }
    }

    void handlePropertySelected() {
        boolean enable = (getSelectedProperty() != null);

        if (this.btnRemoveProperty.getEnabled() != enable) {
            this.btnRemoveProperty.setEnabled(enable);
        }

        if (this.btnEditProperty.getEnabled() != enable) {
            this.btnEditProperty.setEnabled(enable);
        }
    }

    void handleRemoveMetaclass() {
        assert !CoreStringUtil.isEmpty(getSelectedMetaclass()) : "Remove metaclass button is enabled and there is no metaclass selected"; //$NON-NLS-1$
        String selectedMetaclassName = getSelectedMetaclass();

        if (FormUtil.openQuestion(getShell(), Messages.removeMetaclassDialogTitle, Activator.getDefault().getImage(MED_EDITOR),
                                  NLS.bind(Messages.removeMetaclassDialogMsg, selectedMetaclassName))) {
            getMed().removeMetaclass(selectedMetaclassName);
        }
    }

    void handleRemoveProperty() {
        assert !CoreStringUtil.isEmpty(getSelectedMetaclass()) : "Remove property button is enabled and there is no metaclass selected"; //$NON-NLS-1$
        assert (getSelectedProperty() != null) : "Remove property button is enabled and there is no property selected"; //$NON-NLS-1$
        ModelExtensionPropertyDefinition selectedPropDefn = getSelectedProperty();

        if (FormUtil.openQuestion(getShell(), Messages.removePropertyDialogTitle, Activator.getDefault().getImage(MED_EDITOR),
                                  NLS.bind(Messages.removePropertyDialogMsg, selectedPropDefn.getSimpleId()))) {
            getMed().removePropertyDefinition(getSelectedMetaclass(), selectedPropDefn);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.eclipse.ui.forms.editor.FormPage#setFocus()
     */
    @Override
    public void setFocus() {
        if (this.metaclassViewer != null) {
            this.metaclassViewer.getControl().setFocus();

            if (this.metaclassViewer.getTable().getItemCount() != 0) {
                this.metaclassViewer.getTable().select(0);
                Event event = new Event();
                event.widget = this.metaclassViewer.getTable();
                this.metaclassViewer.getTable().notifyListeners(SWT.Selection, event);
            }
        } else {
            super.setFocus();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.teiid.designer.extension.ui.editors.MedEditorPage#setResourceReadOnly(boolean)
     */
    @Override
    protected void setResourceReadOnly( boolean readOnly ) {
        // return if GUI hasn't been constructed yet
        if (this.btnAddMetaclass == null) {
            return;
        }

        if (this.btnAddMetaclass.getEnabled() == readOnly) {
            this.btnAddMetaclass.setEnabled(!readOnly);
        }

        if (this.btnAddProperty.getEnabled() == readOnly) {
            this.btnAddProperty.setEnabled(!readOnly);
        }

        if (this.btnEditMetaclass.getEnabled() == readOnly) {
            this.btnEditMetaclass.setEnabled(!readOnly);
        }

        if (this.btnEditProperty.getEnabled() == readOnly) {
            this.btnEditProperty.setEnabled(!readOnly);
        }

        if (this.btnRemoveMetaclass.getEnabled() == readOnly) {
            this.btnRemoveMetaclass.setEnabled(!readOnly);
        }

        if (this.btnRemoveProperty.getEnabled() == readOnly) {
            this.btnRemoveProperty.setEnabled(!readOnly);
        }

        if (this.metaclassViewer.getTable().getEnabled() == readOnly) {
            this.metaclassViewer.getTable().setEnabled(!readOnly);
        }

        if (this.propertyViewer.getTable().getEnabled() == readOnly) {
            this.propertyViewer.getTable().setEnabled(!readOnly);
        }

        if (!readOnly) {
            handleMetaclassSelected();
            handlePropertySelected();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.teiid.designer.extension.ui.editors.MedEditorPage#updateAllMessages()
     */
    @Override
    protected void updateAllMessages() {
        validateMetaclasses();
        validatePropertyDefinitions();
    }

    private void validateMetaclasses() {
        this.metaclassError.setMessage(ModelExtensionDefinitionValidator.validateMetaclassNames(getMed().getExtendedMetaclasses(),
                                                                                                true));
        updateMessage(this.metaclassError);
        validatePropertyDefinitions(); // need to do this to catch when a new metaclass is added
    }

    private void validatePropertyDefinitions() {
        this.propertyError.setMessage(ModelExtensionDefinitionValidator.validatePropertyDefinitions(getMed().getPropertyDefinitions()));
        updateMessage(this.propertyError);
    }

    interface ColumnHeaders {
        String ADVANCED = Messages.advancedPropertyAttributeColumnHeader;
        String ALLOWED_VALUES = Messages.allowedValuesPropertyAttributeColumnHeader;
        String DEFAULT_VALUE = Messages.defaultValuePropertyAttributeColumnHeader;
        String DESCRIPTION = Messages.descriptionPropertyAttributeColumnHeader;
        String DISPLAY_NAME = Messages.displayNamePropertyAttributeColumnHeader;
        String INDEXED = Messages.indexedPropertyAttributeColumnHeader;
        String MASKED = Messages.maskedPropertyAttributeColumnHeader;
        String MODFIFIABLE = Messages.modifiablePropertyAttributeColumnHeader;
        String REQUIRED = Messages.requiredPropertyAttributeColumnHeader;
        String RUNTIME_TYPE = Messages.runtimeTypePropertyAttributeColumnHeader;
        String SIMPLE_ID = Messages.simpleIdPropertyAttributeColumnHeader;
    }

    interface ColumnIndexes {
        int ADVANCED = 4;
        int ALLOWED_VALUES = 8;
        int DEFAULT_VALUE = 7;
        int DESCRIPTION = 10;
        int DISPLAY_NAME = 9;
        int INDEXED = 6;
        int MASKED = 5;
        int MODIFIABLE = 3;
        int REQUIRED = 2;
        int RUNTIME_TYPE = 1;
        int SIMPLE_ID = 0;
    }

    interface ColumnToolTips {
        String ADVANCED = Messages.advancedPropertyAttributeColumnHeaderToolTip;
        String ALLOWED_VALUES = Messages.allowedValuesPropertyAttributeColumnHeaderToolTip;
        String DEFAULT_VALUE = Messages.defaultValuePropertyAttributeColumnHeaderToolTip;
        String DESCRIPTION = Messages.descriptionPropertyAttributeColumnHeaderToolTip;
        String DISPLAY_NAME = Messages.displayNamePropertyAttributeColumnHeaderToolTip;
        String INDEXED = Messages.indexedPropertyAttributeColumnHeaderToolTip;
        String MASKED = Messages.maskedPropertyAttributeColumnHeaderToolTip;
        String MODFIFIABLE = Messages.modifiablePropertyAttributeColumnHeaderToolTip;
        String REQUIRED = Messages.requiredPropertyAttributeColumnHeaderToolTip;
        String RUNTIME_TYPE = Messages.runtimeTypePropertyAttributeColumnHeaderToolTip;
        String SIMPLE_ID = Messages.simpleIdPropertyAttributeColumnHeaderToolTip;
    }

    class PropertyLabelProvider extends ColumnLabelProvider {

        private final int columnIndex;

        public PropertyLabelProvider( final int columnIndex ) {
            this.columnIndex = columnIndex;
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.eclipse.jface.viewers.ColumnLabelProvider#getImage(java.lang.Object)
         */
        @Override
        public Image getImage( Object element ) {
            ModelExtensionPropertyDefinition propDefn = (ModelExtensionPropertyDefinition)element;
            boolean enabled = false;

            if ((ColumnIndexes.ADVANCED == this.columnIndex) && propDefn.isAdvanced()) {
                enabled = true;
            } else if ((ColumnIndexes.INDEXED == this.columnIndex) && propDefn.shouldBeIndexed()) {
                enabled = true;
            } else if ((ColumnIndexes.MASKED == this.columnIndex) && propDefn.isMasked()) {
                enabled = true;
            } else if ((ColumnIndexes.MODIFIABLE == this.columnIndex) && propDefn.isModifiable()) {
                enabled = true;
            } else if ((ColumnIndexes.REQUIRED == this.columnIndex) && propDefn.isRequired()) {
                enabled = true;
            }

            return (enabled ? Activator.getDefault().getImage(CHECK_MARK) : null);
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.eclipse.jface.viewers.ColumnLabelProvider#getText(java.lang.Object)
         */
        @Override
        public String getText( Object element ) {
            ModelExtensionPropertyDefinition propDefn = (ModelExtensionPropertyDefinition)element;

            if (ColumnIndexes.SIMPLE_ID == this.columnIndex) {
                return propDefn.getSimpleId();
            }

            if (ColumnIndexes.DEFAULT_VALUE == this.columnIndex) {
                return propDefn.getDefaultValue();
            }

            if (ColumnIndexes.DESCRIPTION == this.columnIndex) {
                return propDefn.getDescription();
            }

            if (ColumnIndexes.RUNTIME_TYPE == this.columnIndex) {
                return propDefn.getRuntimeType();
            }

            if (ColumnIndexes.DISPLAY_NAME == this.columnIndex) {
                return propDefn.getDisplayName();
            }

            if (ColumnIndexes.ALLOWED_VALUES == this.columnIndex) {
                String[] allowedValues = propDefn.getAllowedValues();

                if ((allowedValues != null) && (allowedValues.length != 0)) {
                    StringBuilder txt = new StringBuilder();

                    for (int size = allowedValues.length, i = 0; i < size; ++i) {
                        if (i != 0) {
                            txt.append(","); //$NON-NLS-1$
                        }

                        txt.append(allowedValues[i]);
                    }

                    return txt.toString();
                }
            }

            // don't return a value for the boolean columns
            return null;
        }

    }
}
