/*
 * JBoss, Home of Professional Open Source.
*
* See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
*
* See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
*/
package org.teiid.designer.vdb.ui.editor.panels;

import static org.teiid.designer.vdb.ui.VdbUiConstants.Images.ADD;
import static org.teiid.designer.vdb.ui.VdbUiConstants.Images.REMOVE;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.teiid.core.designer.util.I18nUtil;
import org.teiid.designer.ui.common.util.WidgetFactory;
import org.teiid.designer.ui.common.util.WidgetUtil;
import org.teiid.designer.vdb.ui.VdbUiConstants;
import org.teiid.designer.vdb.ui.VdbUiPlugin;
import org.teiid.designer.vdb.ui.editor.VdbEditor;

/**
 *
 */
public class PropertiesPanel {
	static final String PREFIX = I18nUtil.getPropertyPrefix(PropertiesPanel.class);
	
    static final String INVALID_INTEGER_INPUT_TITLE = i18n("invalidQueryTimeoutValueTitle"); //$NON-NLS-1$
    static final String INVALID_INTEGER_INPUT_MESSAGE = i18n("invalidQueryTimeoutValueMessage"); //$NON-NLS-1$

    
	VdbEditor vdbEditor;
    

    ListViewer allowedLanguagesViewer;
	Button addLanguageButton;
	Button removeLanguageButton;
	
	Text securityDomainText;
	Text gssPatternText;
	Text passwordPatternText;
	Text authenticationTypeText;
		
    static String i18n( final String id ) {
        return VdbUiConstants.Util.getString(id);
    }
    
    static String prefixedI18n( final String id ) {
        return VdbUiConstants.Util.getString(PREFIX + id);
    }
	
	/**
     * @param parent
     * @param editor
     */
    public PropertiesPanel(Composite parent, VdbEditor editor) {
    	super();
    	this.vdbEditor = editor;
    	
    	createPanel(parent);
    }
    
	private void createPanel(Composite parent) {
    	Composite panel = WidgetFactory.createPanel(parent, SWT.NONE, GridData.FILL_BOTH, 1, 2);
    	panel.setLayout(new GridLayout(2, false));

		Group propertiesGroup = WidgetFactory.createGroup(panel, prefixedI18n("general"), SWT.FILL, 1, 2);  //$NON-NLS-1$
		GridData gd_1 = new GridData(GridData.FILL_BOTH);
		gd_1.widthHint = 240;
		propertiesGroup.setLayoutData(gd_1);

		Label label = new Label(propertiesGroup, SWT.NONE);
		label.setText(i18n("queryTimeoutLabel")); //$NON-NLS-1$

		final Text queryTimeoutText = new Text(propertiesGroup, SWT.BORDER | SWT.SINGLE);
		queryTimeoutText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		queryTimeoutText.setText(Integer.toString(vdbEditor.getVdb().getQueryTimeout()));
    	queryTimeoutText.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				try {
                    int valueInSecs = Integer.parseInt(queryTimeoutText.getText());
                    if (valueInSecs > -1) {
                        vdbEditor.getVdb().setQueryTimeout(valueInSecs);
					}
				} catch (NumberFormatException ex) {
					MessageDialog.openWarning(Display.getCurrent().getActiveShell(),
                            INVALID_INTEGER_INPUT_TITLE,
                            INVALID_INTEGER_INPUT_MESSAGE);
					queryTimeoutText.setText(Integer.toString(vdbEditor.getVdb().getQueryTimeout()));
				}
				
			}
		});
    	
    	{ // SECURITY PROPERTIES
	    	label = new Label(propertiesGroup, SWT.NONE);
			label.setText(prefixedI18n("securityDomain")); //$NON-NLS-1$
			label.setToolTipText(prefixedI18n("securityDomainTooltip")); //$NON-NLS-1$
	    	
	    	securityDomainText = new Text(propertiesGroup, SWT.BORDER | SWT.SINGLE);
	    	securityDomainText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	    	WidgetUtil.setText(securityDomainText, vdbEditor.getVdb().getSecurityDomain());
	    	securityDomainText.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
					vdbEditor.getVdb().setSecurityDomain(securityDomainText.getText());
					updateSecurityWidgets();
				}
			});
	    	
	    	label = new Label(propertiesGroup, SWT.NONE);
			label.setText(prefixedI18n("gssPattern")); //$NON-NLS-1$
			label.setToolTipText(prefixedI18n("passwordPatternTooltip")); //$NON-NLS-1$
			
	    	gssPatternText = new Text(propertiesGroup, SWT.BORDER | SWT.SINGLE);
	    	gssPatternText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	    	WidgetUtil.setText(gssPatternText, vdbEditor.getVdb().getGssPattern());
	    	gssPatternText.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
					vdbEditor.getVdb().setGssPattern(gssPatternText.getText());
					updateSecurityWidgets();
				}
			});
	    	
	    	label = new Label(propertiesGroup, SWT.NONE);
			label.setText(prefixedI18n("passwordPattern")); //$NON-NLS-1$
			label.setToolTipText(prefixedI18n("passwordPatternTooltip")); //$NON-NLS-1$
			
	    	passwordPatternText = new Text(propertiesGroup, SWT.BORDER | SWT.SINGLE);
	    	passwordPatternText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	    	WidgetUtil.setText(passwordPatternText, vdbEditor.getVdb().getPasswordPattern());
	    	passwordPatternText.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
					vdbEditor.getVdb().setPasswordPattern(passwordPatternText.getText());
					updateSecurityWidgets();
				}
			});
	    	
	    	label = new Label(propertiesGroup, SWT.NONE);
			label.setText(prefixedI18n("authenticationType")); //$NON-NLS-1$
			label.setToolTipText(prefixedI18n("authenticationTypeTooltip")); //$NON-NLS-1$
			
	    	authenticationTypeText = new Text(propertiesGroup, SWT.BORDER | SWT.SINGLE);
	    	authenticationTypeText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	    	WidgetUtil.setText(authenticationTypeText, vdbEditor.getVdb().getAuthenticationType());
	    	authenticationTypeText.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
					vdbEditor.getVdb().setAuthenticationType(authenticationTypeText.getText());
					updateSecurityWidgets();
				}
			});
	    	
	    	updateSecurityWidgets();

    	}
    	
    	
    	new Label(propertiesGroup, SWT.NONE);
    	//autGenRESTLabel.setText(i18n("autoGenerateRESTWAR")); //$NON-NLS-1$
		final Button autoGenRESTCheckbox =  WidgetFactory.createCheckBox(propertiesGroup, i18n("autoGenerateRESTWAR"), vdbEditor.getVdb().isAutoGenerateRESTWAR());  //$NON-NLS-1$
		autoGenRESTCheckbox.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetDefaultSelected( SelectionEvent e ) {
            	vdbEditor.getVdb().setAutoGenerateRESTWAR(((Button)e.getSource()).getSelection());
            }

            @Override
            public void widgetSelected( SelectionEvent e ) {
            	vdbEditor.getVdb().setAutoGenerateRESTWAR(((Button)e.getSource()).getSelection());
            }
        });
		
    	{
    		Group languageGroup = WidgetFactory.createGroup(panel, prefixedI18n("allowedLanguages"), SWT.FILL, 1, 1);  //$NON-NLS-1$
    		GridData gd_2 = new GridData(GridData.FILL_BOTH);
    		gd_2.widthHint = 220;
//    		gd_2.horizontalSpan = 2;
    		languageGroup.setLayoutData(gd_2);
    		// Add a simple list box entry form with String contents
        	this.allowedLanguagesViewer = new ListViewer(languageGroup, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
            GridData data = new GridData(GridData.FILL_BOTH);
            data.horizontalSpan=2;
            this.allowedLanguagesViewer.getControl().setLayoutData(data);
            
            for( String value : vdbEditor.getVdb().getAllowedLanguages() ) {
            		this.allowedLanguagesViewer.add(value);
            }
            
            this.allowedLanguagesViewer.addSelectionChangedListener(new ISelectionChangedListener() {
                /**
                 * {@inheritDoc}
                 * 
                 * @see org.eclipse.jface.viewers.ISelectionChangedListener#selectionChanged(org.eclipse.jface.viewers.SelectionChangedEvent)
                 */
                @Override
                public void selectionChanged( SelectionChangedEvent event ) {
                    handleLanguageSelected();
                }
            });
            
            Composite toolbarPanel = WidgetFactory.createPanel(languageGroup, SWT.NONE, GridData.VERTICAL_ALIGN_BEGINNING, 1, 2);
            
            this.addLanguageButton = WidgetFactory.createButton(toolbarPanel, GridData.FILL);
            this.addLanguageButton.setImage(VdbUiPlugin.singleton.getImage(ADD));
            this.addLanguageButton.setToolTipText(prefixedI18n("addLanguageButton.tooltip")); //$NON-NLS-1$
            this.addLanguageButton.addSelectionListener(new SelectionListener() {
    			
    			@Override
    			public void widgetSelected(SelectionEvent e) {
    				handleAddLanguage();
    			}

    			@Override
    			public void widgetDefaultSelected(SelectionEvent e) {
    			}
    		});
            
            this.removeLanguageButton = WidgetFactory.createButton(toolbarPanel, GridData.FILL);
            this.removeLanguageButton.setImage(VdbUiPlugin.singleton.getImage(REMOVE));
            this.removeLanguageButton.setToolTipText(prefixedI18n("removeLangueButton.tooltip")); //$NON-NLS-1$
            this.removeLanguageButton.addSelectionListener(new SelectionListener() {
    			
    			@Override
    			public void widgetSelected(SelectionEvent e) {
    				handleRemoveLanguage();
    			}

    			@Override
    			public void widgetDefaultSelected(SelectionEvent e) {
    			}
    		});
            
            this.removeLanguageButton.setEnabled(false);
    	}
    	

	}
	
	void updateSecurityWidgets() {
		boolean enabled = vdbEditor.getVdb().getSecurityDomain() != null;
		
		gssPatternText.setEnabled(enabled);
    	passwordPatternText.setEnabled(enabled);
    	authenticationTypeText.setEnabled(enabled);
	}
	
	void handleLanguageSelected() {
		boolean hasSelection = !this.allowedLanguagesViewer.getSelection().isEmpty();
		this.removeLanguageButton.setEnabled(hasSelection);
	}
	
    private String getSelectedLanguage() {
        IStructuredSelection selection = (IStructuredSelection)this.allowedLanguagesViewer.getSelection();

        if (selection.isEmpty()) {
            return null;
        }

        return (String)selection.getFirstElement();
    }
	
    
    void handleAddLanguage() {
        assert (!this.allowedLanguagesViewer.getSelection().isEmpty());

        AddLanguagePropertyDialog dialog = 
        		new AddLanguagePropertyDialog(allowedLanguagesViewer.getControl().getShell(), 
        				vdbEditor.getVdb().getAllowedLanguages());


        if (dialog.open() == Window.OK) {
            // update model
            String language = dialog.getLanguage();

            vdbEditor.getVdb().addAllowedLanguage(language);

            // update UI from model
            this.allowedLanguagesViewer.add(language);
            
            this.allowedLanguagesViewer.refresh();

            // select the new property
            
            
            String lang = null;
            
            for(String item : this.allowedLanguagesViewer.getList().getItems() ) {
            	if( item.equals(language) ) {
            		lang = item;
            		break;
            	}
            }

            if( lang != null ) {
                this.allowedLanguagesViewer.setSelection(new StructuredSelection(lang), true);
            }
        }
    }
    
    void handleRemoveLanguage() {
        String selectedLanguage = getSelectedLanguage();
        assert (selectedLanguage != null);

        // update model
        this.vdbEditor.getVdb().removeAllowedLanguage(selectedLanguage);
        
        this.allowedLanguagesViewer.remove(selectedLanguage);
        // update UI
        this.allowedLanguagesViewer.refresh();
    }

}
