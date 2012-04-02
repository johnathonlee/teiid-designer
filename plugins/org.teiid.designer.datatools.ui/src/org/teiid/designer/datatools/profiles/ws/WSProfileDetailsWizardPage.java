/*
 * JBoss, Home of Professional Open Source.
*
* See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
*
* See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
*/
package org.teiid.designer.datatools.profiles.ws;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Properties;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.datatools.connectivity.IConnection;
import org.eclipse.datatools.connectivity.IConnectionProfile;
import org.eclipse.datatools.connectivity.internal.ui.ConnectivityUIPlugin;
import org.eclipse.datatools.connectivity.internal.ui.dialogs.ExceptionHandler;
import org.eclipse.datatools.connectivity.oda.OdaException;
import org.eclipse.datatools.connectivity.ui.wizards.ConnectionProfileDetailsPage;
import org.eclipse.datatools.connectivity.ui.wizards.NewConnectionProfileWizard;
import org.eclipse.datatools.enablement.oda.xml.util.XMLSourceFromPath;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.progress.UIJob;
import org.teiid.designer.datatools.profiles.ws.IWSProfileConstants.SecurityType;
import org.teiid.designer.datatools.profiles.xml.IXmlProfileConstants;
import org.teiid.designer.datatools.ui.DatatoolsUiConstants;
import com.metamatrix.ui.internal.util.WidgetFactory;

public class WSProfileDetailsWizardPage extends ConnectionProfileDetailsPage
		implements Listener, DatatoolsUiConstants {

	
    private Composite scrolled;

    private Label profileLabel;
    private CLabel profileText;
    private Label descriptionLabel;
    private Text descriptionText;
    private Label usernameLabel;
    private Text usernameText;
    private Label passwordLabel;
    private Text passwordText;
    private Label urlLabel;
    private Text urlText;
    private Label securityLabel;
    private Combo securityCombo;

    /**
     * @param wizardPageName
     */
    public WSProfileDetailsWizardPage( String pageName ) {
        super(pageName, UTIL.getString("WSProfileDetailsWizardPage.Name"), //$NON-NLS-1$
              AbstractUIPlugin.imageDescriptorFromPlugin(DatatoolsUiConstants.PLUGIN_ID, "icons/full/obj16/web-service-cp.png")); //$NON-NLS-1$
    }

	@Override
	public void createCustomControl(Composite parent) {
        GridData gd;

        Group group = new Group(parent, SWT.BORDER);
        group.setText(UTIL.getString("Common.Properties.Label")); //$NON-NLS-1$
        group.setLayout(new FillLayout());

        scrolled = new Composite(group, SWT.NONE);
        GridLayout gridLayout = new GridLayout();
        gridLayout.numColumns = 2;
        scrolled.setLayout(gridLayout);

        profileLabel = new Label(scrolled, SWT.NONE);
        profileLabel.setText(UTIL.getString("Common.Profile.Label")); //$NON-NLS-1$

        profileText = WidgetFactory.createLabel(scrolled, SWT.SINGLE | SWT.BORDER);
        gd = new GridData();
        gd.horizontalSpan = 1;
        profileText.setLayoutData(gd);
        profileText.setText(((ConnectionProfileWizard)getWizard()).getProfileName());

        descriptionLabel = new Label(scrolled, SWT.NONE);
        descriptionLabel.setText(UTIL.getString("Common.Description.Label")); //$NON-NLS-1$
        gd = new GridData();
        descriptionLabel.setLayoutData(gd);

        descriptionText = WidgetFactory.createTextBox(scrolled, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY, GridData.FILL);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.grabExcessHorizontalSpace = true;
        gd.horizontalSpan = 1;
        descriptionText.setLayoutData(gd);
        String description = ((ConnectionProfileWizard)getWizard()).getProfileDescription();
        descriptionText.setText(description);
        descriptionText.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));

        urlLabel = new Label(scrolled, SWT.NONE);
        urlLabel.setText(UTIL.getString("Common.URL.Label")); //$NON-NLS-1$
        urlLabel.setToolTipText(UTIL.getString("Common.URL.ToolTip")); //$NON-NLS-1$
        gd = new GridData();
        urlLabel.setLayoutData(gd);

        urlText = new Text(scrolled, SWT.SINGLE | SWT.BORDER);
        urlText.setToolTipText(UTIL.getString("Common.URL.ToolTip")); //$NON-NLS-1$
        gd = new GridData(GridData.FILL_HORIZONTAL);
        urlText.setLayoutData(gd);

        securityLabel = new Label(scrolled, SWT.NONE);
        securityLabel.setText(UTIL.getString("Common.Security.Type.Label")); //$NON-NLS-1$
        securityLabel.setToolTipText(UTIL.getString("Common.Context.Factory.ToolTip")); //$NON-NLS-1$
        gd = new GridData(GridData.FILL_HORIZONTAL);
        securityLabel.setLayoutData(gd);

        securityCombo = new Combo(scrolled, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
        securityCombo.setToolTipText(UTIL.getString("Common.Context.Factory.ToolTip")); //$NON-NLS-1$
        gd = new GridData(GridData.FILL_HORIZONTAL);
        securityCombo.setLayoutData(gd);
        securityCombo.setItems(new String[]{SecurityType.None.name(), SecurityType.HTTPBasic.name(), SecurityType.WSSecurity.name()});
        securityCombo.setText(SecurityType.None.name());
        
        usernameLabel = new Label(scrolled, SWT.NONE);
        usernameLabel.setText(UTIL.getString("Common.Username.Label")); //$NON-NLS-1$
        usernameLabel.setToolTipText(UTIL.getString("Common.Username.ToolTip")); //$NON-NLS-1$
        gd = new GridData(GridData.FILL_HORIZONTAL);
        usernameLabel.setLayoutData(gd);

        usernameText = new Text(scrolled, SWT.SINGLE | SWT.BORDER);
        usernameText.setToolTipText(UTIL.getString("Common.Username.ToolTip")); //$NON-NLS-1$
        gd = new GridData(GridData.FILL_HORIZONTAL);
        usernameText.setLayoutData(gd);
        usernameText.setEnabled(false);
        
        passwordLabel = new Label(scrolled, SWT.NONE);
        passwordLabel.setText(UTIL.getString("Common.Password.Label")); //$NON-NLS-1$
        passwordLabel.setToolTipText(UTIL.getString("Common.Password.ToolTip")); //$NON-NLS-1$
        gd = new GridData(GridData.FILL_HORIZONTAL);
        passwordLabel.setLayoutData(gd);

        passwordText = new Text(scrolled, SWT.SINGLE | SWT.BORDER | SWT.PASSWORD);
        passwordText.setToolTipText(UTIL.getString("Common.Password.ToolTip")); //$NON-NLS-1$
        gd = new GridData(GridData.FILL_HORIZONTAL);
        passwordText.setLayoutData(gd);
        passwordText.setEnabled(false);
        
        setPingButtonVisible(true);
        setPingButtonEnabled(false);
        setAutoConnectOnFinishDefault(false);
        setCreateAutoConnectControls(false);
        setShowAutoConnect(false);
        setShowAutoConnectOnFinish(false);
        setPageComplete(false);
        addListeners();

	}
	
    /* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.DialogPage#setVisible(boolean)
	 */
	@Override
	public void setVisible(boolean visible) {
		// TODO Auto-generated method stub
		super.setVisible(visible);
		
        String description = ((ConnectionProfileWizard)getWizard()).getProfileDescription();
        descriptionText.setText(description);
        
        profileText.setText(((ConnectionProfileWizard)getWizard()).getProfileName());
	}

	/**
     * 
     */
    private void addListeners() {
        usernameText.addListener(SWT.Modify, this);
        passwordText.addListener(SWT.Modify, this);
        urlText.addListener(SWT.Modify, this);
        securityCombo.addListener(SWT.Modify, this);
    }
    
    /**
     * {@inheritDoc}
     * 
     * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
     */
    @Override
    public void handleEvent( Event event ) {

        if (event.widget == usernameText) {
            Properties properties = ((NewConnectionProfileWizard)getWizard()).getProfileProperties();
            properties.setProperty(IWSProfileConstants.USERNAME_PROP_ID, usernameText.getText());
        }
        if (event.widget == passwordText) {
            Properties properties = ((NewConnectionProfileWizard)getWizard()).getProfileProperties();
            properties.setProperty(IWSProfileConstants.PASSWORD_PROP_ID, passwordText.getText());
        }
        if (event.widget == urlText) {
            Properties properties = ((NewConnectionProfileWizard)getWizard()).getProfileProperties();
            String urlStr = urlText.getText();
            if (urlStr != null) urlStr = urlStr.trim();
            properties.setProperty(IWSProfileConstants.URL_PROP_ID, urlStr);
        }
        if (event.widget == securityCombo) {
            Properties properties = ((NewConnectionProfileWizard)getWizard()).getProfileProperties();
            properties.setProperty(IWSProfileConstants.SECURITY_TYPE_ID, securityCombo.getText());
        	if(securityCombo.getText().equals(SecurityType.None.name())) {
        		usernameText.setEnabled(false);
        		passwordText.setEnabled(false);
        	} else {
        		usernameText.setEnabled(true);
        		passwordText.setEnabled(true);
        	}
        	

        }
        updateState();
    }
    
    /**
     * {@inheritDoc}
     * 
     * @see org.eclipse.datatools.connectivity.ui.wizards.ConnectionProfileDetailsPage#createControl(org.eclipse.swt.widgets.Composite)
     */
    @Override
    public void createControl( Composite parent ) {
        super.createControl(parent);
        updateState();
    }
    
    void updateState() {
        setPingButtonVisible(true);
        setPingButtonEnabled(false);

        profileText.setText(((NewConnectionProfileWizard)getWizard()).getProfileName());
        descriptionText.setText(((NewConnectionProfileWizard)getWizard()).getProfileDescription());

        Properties properties = ((NewConnectionProfileWizard)getWizard()).getProfileProperties();
        if (null == properties.get(IWSProfileConstants.URL_PROP_ID)
                || properties.get(IWSProfileConstants.URL_PROP_ID).toString().isEmpty()) {
                setErrorMessage(UTIL.getString("Common.URL.Error.Message")); //$NON-NLS-1$
                return;
        }
        setErrorMessage(null);
        try {
        	@SuppressWarnings("unused")
			URL url = new URL(properties.get(IWSProfileConstants.URL_PROP_ID).toString());
        } catch(MalformedURLException e) {
        	setErrorMessage(UTIL.getString("Common.URL.Invalid.Message") + e.getMessage()); //$NON-NLS-1$
        	return;
        }
        
        if (null != properties.get(IWSProfileConstants.SECURITY_TYPE_ID) &&
        		!SecurityType.None.name().equals(properties.get(IWSProfileConstants.SECURITY_TYPE_ID))) {
        	if (null == properties.get(IWSProfileConstants.USERNAME_PROP_ID)
                    || properties.get(IWSProfileConstants.USERNAME_PROP_ID).toString().isEmpty()) {
                    setErrorMessage(UTIL.getString("Common.Username.Error.Message")); //$NON-NLS-1$
                    return;
                }
                setErrorMessage(null);
                if (null == properties.get(IWSProfileConstants.PASSWORD_PROP_ID)
                    || properties.get(IWSProfileConstants.PASSWORD_PROP_ID).toString().isEmpty()) {
                    setErrorMessage(UTIL.getString("Common.Password.Error.Message")); //$NON-NLS-1$
                    return;
                }
                
        }
        
        setErrorMessage(null);
        setPageComplete(true);
        setMessage(UTIL.getString("Click.Next.or.Finish")); //$NON-NLS-1$

    }
    
    /**
     * {@inheritDoc}
     * 
     * @see org.eclipse.jface.wizard.WizardPage#canFlipToNextPage()
     */
    @Override
    public boolean canFlipToNextPage() {
        return internalComplete(super.canFlipToNextPage());
    }

	/**
	 * @param complete
	 * @return
	 */
	private boolean internalComplete(boolean complete) {
		Properties properties = ((NewConnectionProfileWizard) getWizard())
				.getProfileProperties();
		if (complete
				&& (null == properties.get(IWSProfileConstants.URL_PROP_ID) || properties
						.get(IWSProfileConstants.URL_PROP_ID).toString()
						.isEmpty())) {
			complete = false;
		}
		if (complete
				&& null != properties.get(IWSProfileConstants.SECURITY_TYPE_ID) && (!SecurityType.None.name().equals(
						properties.get(IWSProfileConstants.SECURITY_TYPE_ID)
								.toString()))) {
			if (complete
					&& (null == properties
							.get(IWSProfileConstants.USERNAME_PROP_ID) || properties
							.get(IWSProfileConstants.USERNAME_PROP_ID)
							.toString().isEmpty())) {
				complete = false;
			}
			if (complete
					&& (null == properties
							.get(IWSProfileConstants.PASSWORD_PROP_ID) || properties
							.get(IWSProfileConstants.PASSWORD_PROP_ID)
							.toString().isEmpty())) {
				complete = false;
			}

		}
		return complete;
	}
	
    /**
     * {@inheritDoc}
     * 
     * @see org.eclipse.datatools.connectivity.internal.ui.wizards.BaseWizardPage#getSummaryData()
     */
    @Override
    public List getSummaryData() {
        List result = super.getSummaryData();
        result.add(new String[] {UTIL.getString("Common.URL.Label"), urlText.getText()}); //$NON-NLS-1$
        result.add(new String[] {UTIL.getString("Common.Username.Label"), usernameText.getText()}); //$NON-NLS-1$
        result.add(new String[] {UTIL.getString("Common.Security.Type.Label"), securityCombo.getText()}); //$NON-NLS-1$
        return result;
    }
    
    @Override
    public void testConnection() {
        super.testConnection();
    }

    @Override
    protected Runnable createTestConnectionRunnable( final IConnectionProfile profile ) {
        final Job pingJob = new URLPingJob(getShell(), profile);
        pingJob.schedule();
        return new Runnable() {
            @Override
            public void run() {
                try {
                    pingJob.join();
                } catch (InterruptedException e) {
                }
            }
        };
    }
    
    /**
     * Executes a ping operation as a background job.
     */
    public class URLPingJob extends Job {

        private IConnectionProfile icp;
        private Shell shell;

        /**
         * @param exceptions
         * @param name
         */
        public URLPingJob( Shell shell,
                                  IConnectionProfile profile ) {
            super(ConnectivityUIPlugin.getDefault().getResourceString("actions.ping.job")); //$NON-NLS-1$
            setSystem(false);
            setUser(true);
            this.shell = shell;
            icp = profile;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
         */
        @Override
        protected IStatus run( IProgressMonitor monitor ) {
            monitor.beginTask(ConnectivityUIPlugin.getDefault().getResourceString("actions.ping.title"), //$NON-NLS-1$
                              IProgressMonitor.UNKNOWN);

            Exception exception = testXmlUrlConnection(icp);

            monitor.done();

            new XmlUrlPingUIJob(shell, exception).schedule();

            return Status.OK_STATUS;
        }

        public Exception testXmlUrlConnection( IConnectionProfile icp ) {
        	Properties connProperties = icp.getBaseProperties();
			//InputStream not provided, check XML file
			String xmlFile = connProperties == null ? null :(String) connProperties.get( IXmlProfileConstants.URL_PROP_ID );
			try {
				InputStream is = new XMLSourceFromPath(xmlFile, null).openInputStream();
				try
				{
					is.close( );
				}catch ( IOException e ) {
				}
			} catch (OdaException odaEx) {
				return odaEx;
			}
			
			return null;
        }

        public Throwable getTestConnectionException( IConnection conn ) {
            return conn != null ? conn.getConnectException() : 
            	new RuntimeException(ConnectivityUIPlugin.getDefault().getResourceString("actions.ping.failure")); //$NON-NLS-1$
        }

        public class XmlUrlPingUIJob extends UIJob {

            private Shell shell;
            private Throwable exception;

            /**
             * @param name
             */
            public XmlUrlPingUIJob( Shell shell,
                                        Throwable exception ) {
                super(ConnectivityUIPlugin.getDefault().getResourceString("actions.ping.uijob")); //$NON-NLS-1$
                setSystem(false);
                this.exception = exception;
                this.shell = shell;
            }

            /*
             * (non-Javadoc)
             * 
             * @see org.eclipse.ui.progress.UIJob#runInUIThread(org.eclipse.core.runtime.IProgressMonitor)
             */
            @Override
            public IStatus runInUIThread( IProgressMonitor monitor ) {
                showTestConnectionMessage(shell, exception);
                return Status.OK_STATUS;
            }

            public void showTestConnectionMessage( Shell shell,
                                                   Throwable exception ) {
                if (exception == null) {
                    MessageDialog.openInformation(
                    	shell,                         
                    	ConnectivityUIPlugin.getDefault().getResourceString("dialog.title.success"), //$NON-NLS-1$             
                    	ConnectivityUIPlugin.getDefault().getResourceString("actions.ping.success")); //$NON-NLS-1$
                    //setValidatedConnection(true);
                    updateState();
                } else {
                    ExceptionHandler.showException(
                    	shell,
                        ConnectivityUIPlugin.getDefault().getResourceString("dialog.title.error"), //$NON-NLS-1$
                        ConnectivityUIPlugin.getDefault().getResourceString("actions.ping.failure"), //$NON-NLS-1$
                         exception);
                    //setValidatedConnection(false);
                    setErrorMessage(exception.getMessage());
                    updateState();

                }
            }

        }

    }

}
