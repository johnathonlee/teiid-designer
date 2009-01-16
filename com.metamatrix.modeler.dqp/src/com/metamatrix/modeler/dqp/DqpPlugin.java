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
package com.metamatrix.modeler.dqp;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;
import com.metamatrix.common.config.api.ConfigurationObjectEditor;
import com.metamatrix.core.PluginUtil;
import com.metamatrix.core.event.IChangeListener;
import com.metamatrix.core.event.IChangeNotifier;
import com.metamatrix.core.util.FileUtils;
import com.metamatrix.core.util.I18nUtil;
import com.metamatrix.core.util.PluginUtilImpl;
import com.metamatrix.modeler.dqp.config.ConfigurationManager;
import com.metamatrix.modeler.dqp.internal.config.ConfigFileManager;
import com.metamatrix.modeler.dqp.internal.config.ConfigurationManagerImpl;
import com.metamatrix.modeler.dqp.internal.config.DqpExtensionsHandler;
import com.metamatrix.modeler.dqp.internal.config.DqpPath;
import com.metamatrix.modeler.dqp.internal.config.VdbDefnHelper;
import com.metamatrix.modeler.dqp.internal.workspace.WorkspaceConfigurationManager;
import com.metamatrix.vdb.edit.VdbContextEditor;
import com.metamatrix.vdb.edit.VdbEditingContext;
import com.metamatrix.vdb.internal.edit.InternalVdbEditingContext;

/**
 * The main plugin class to be used in the desktop.
 */
public class DqpPlugin extends Plugin {

    public static final String PLUGIN_ID = "com.metamatrix.modeler.dqp"; //$NON-NLS-1$    
    private static final String BUNDLE_NAME = PLUGIN_ID + ".i18n"; //$NON-NLS-1$

    public static final String WORKSPACE_DEFN_FILE_NAME = "WorkspaceBindings.def"; //$NON-NLS-1$

    private static final String ADMIN_VDB = "Admin.vdb"; //$NON-NLS-1$
    private static final String WORKSPACE_PROPERTIES = "workspace.properties"; //$NON-NLS-1$

    public static final String UDF_JAR_MAPPER_FILE_NAME = "udfJarMappings.properties"; //$NON-NLS-1$

    /**
     * Provides access to the plugin's log and to it's resources.
     * 
     * @since 4.2.1
     */
    public static PluginUtil Util = new PluginUtilImpl(PLUGIN_ID, BUNDLE_NAME, ResourceBundle.getBundle(BUNDLE_NAME));

    private static DqpPlugin plugin;
    private ConfigurationManager manager;
    private DqpExtensionsHandler extensionsHandler;

    private static WorkspaceConfigurationManager workspaceConfig;

    // listener for context changes (specifically closed contexts)
    private IChangeListener changeListener = new IChangeListener() {
        public void stateChanged( IChangeNotifier theContext ) {
            handleContextChanged(theContext);
        }
    };

    /**
     * Collection of {@link VdbDefnHelper}s for a given {@link InternalVdbEditingContext}. Important to make sure only one context
     * and one helper is constructed for a given VDB. Made protected for testing purposes. Key=InternalVdbEditingContext,
     * value=VdbDefnHelper
     */
    private Map vdbHelperMap = new HashMap();

    /**
     * <p>
     * {@inheritDoc}
     * </p>
     * 
     * @see org.eclipse.core.runtime.Plugin#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start( BundleContext context ) throws Exception {
        super.start(context);
        plugin = this;
        initialize();

        // initialize logger
        ((PluginUtilImpl)Util).initializePlatformLogger(this);
    }

    private void initialize() throws Exception {
        try {
            IPath configLocation = DqpPath.getRuntimeConfigPath();

            // create required directories for running the DQP
            DqpPath.getRuntimeExtensionsPath();
            DqpPath.getLogPath();

            // ensure that the configuration files have been loaded to the state location
            File configurationFile = new File(configLocation.toFile(), ConfigFileManager.CONFIG_FILE_NAME);
            if (!configurationFile.exists()) {
                File originalFile = DqpPath.getInstallConfigPath().append(ConfigFileManager.CONFIG_FILE_NAME).toFile();
                if (!originalFile.exists()) {
                    throw new Exception(Util.getString(I18nUtil.getPropertyPrefix(DqpPlugin.class) + "missingConfigFile")); //$NON-NLS-1$
                }
                FileUtils.copy(originalFile.getAbsolutePath(), configurationFile.getAbsolutePath());
            }

            // init configuration manager
            manager = new ConfigurationManagerImpl(configLocation);

            // Clean up the VDB DEFN working folder
            File vdbExecDir = DqpPath.getVdbExecutionPath().toFile();
            FileUtils.removeChildrenRecursively(vdbExecDir);

            File src = DqpPath.getInstallConfigPath().append(ADMIN_VDB).toFile();
            File target = new File(vdbExecDir, ADMIN_VDB);
            if (!target.exists()) {
                FileUtils.copy(src.getAbsolutePath(), target.getAbsolutePath());
            }

            // copy the workspace.properties each time eclipse starts as we may
            // have modified the properties file
            src = DqpPath.getInstallDqpPath().append(WORKSPACE_PROPERTIES).toFile();
            target = new File(vdbExecDir, WORKSPACE_PROPERTIES);
            if (target.exists()) {
                target.delete();
            }
            FileUtils.copy(src.getAbsolutePath(), target.getAbsolutePath());

            // initialize the workspace configuration
            inititalizeWorkspaceConfig();
        } catch (Exception e) {
            if (e instanceof CoreException) {
                throw (CoreException)e;
            }

            throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, IStatus.OK, e.getLocalizedMessage(), e));
        }
    }

    /**
     * @return DqpPlugin
     * @since 4.3
     */
    public static DqpPlugin getInstance() {
        return plugin;
    }

    public ConfigurationManager getConfigurationManager() {
        return manager;
    }

    public DqpExtensionsHandler getExtensionsHandler() {
        if (this.extensionsHandler == null) {
            this.extensionsHandler = new DqpExtensionsHandler();
        }

        return this.extensionsHandler;
    }

    /**
     * @return
     * @since 5.0
     */
    public static WorkspaceConfigurationManager getWorkspaceConfig() {
        return workspaceConfig;
    }

    /**
     * @return ConfigurationObjectEditor
     * @since 4.3
     */
    public ConfigurationObjectEditor getConfigurationObjectEditor() {
        return manager.getBasicConfigurationObjectEditor();
    }

    /**
     * Obtains the <code>VdbDefnHelper</code> for the specified <code>InternalVdbEditingContext</code>.
     * 
     * @param theContext the context whose helper is being requested
     * @return the helper
     * @since 4.3
     */
    public VdbDefnHelper getVdbDefnHelper( InternalVdbEditingContext theContext ) {
        VdbDefnHelper result = (VdbDefnHelper)this.vdbHelperMap.get(theContext);

        if (result == null) {
            theContext.addChangeListener(this.changeListener);
            result = new VdbDefnHelper(theContext.getPathToVdb().toFile(), theContext);
            this.vdbHelperMap.put(theContext, result);
        }

        return result;
    }

    /**
     * Obtains the <code>VdbDefnHelper</code> for the specified <code>InternalVdbEditingContext</code>.
     * 
     * @param theContext the context whose helper is being requested
     * @return the helper
     * @since 4.3
     */
    public VdbDefnHelper getVdbDefnHelper( VdbContextEditor theContext ) {
        VdbDefnHelper result = (VdbDefnHelper)this.vdbHelperMap.get(theContext);

        if (result == null) {
            theContext.addChangeListener(this.changeListener);
            result = new VdbDefnHelper(theContext.getVdbFile(), theContext);
            this.vdbHelperMap.put(theContext, result);
        }

        return result;
    }

    /**
     * Cleans up the map of context helpers.
     * 
     * @param theContext the context whose state has changed
     * @since 4.3
     */
    void handleContextChanged( IChangeNotifier theContext ) {
        if (this.vdbHelperMap.get(theContext) != null) {
            // only care if the context is now closed
            if (theContext instanceof VdbEditingContext) {
                if (!((VdbEditingContext)theContext).isOpen()) {
                    this.vdbHelperMap.remove(theContext);
                    theContext.removeChangeListener(this.changeListener);
                }
            } else if (theContext instanceof VdbContextEditor) {
                if (!((VdbContextEditor)theContext).isOpen()) {
                    this.vdbHelperMap.remove(theContext);
                    theContext.removeChangeListener(this.changeListener);
                }
            }
        }
    }

    private void inititalizeWorkspaceConfig() {
        File workspaceDefnDirectory = DqpPath.getWorkspaceDefnPath().toFile();
        assert (workspaceDefnDirectory.exists() && workspaceDefnDirectory.isDirectory());

        // Check for config file
        String configFilePath = workspaceDefnDirectory.getAbsolutePath() + File.separator + WORKSPACE_DEFN_FILE_NAME;
        File configFile = new File(configFilePath);
        workspaceConfig = new WorkspaceConfigurationManager(configFile);
        if (!configFile.exists()) {
            try {
                workspaceConfig.save();
            } catch (Exception theException) {
                String msg = Util.getString(I18nUtil.getPropertyPrefix(DqpPlugin.class) + "problemSavingWorkspaceDefnFile", //$NON-NLS-1$
                                            WORKSPACE_DEFN_FILE_NAME);
                Util.log(IStatus.ERROR, theException, msg);
            }
        } else {
            try {
                workspaceConfig.load();
            } catch (Exception theException) {
                String msg = Util.getString(I18nUtil.getPropertyPrefix(DqpPlugin.class) + "problemLoadingWorkspaceDefnFile", //$NON-NLS-1$
                                            WORKSPACE_DEFN_FILE_NAME);
                Util.log(IStatus.ERROR, theException, msg);
            }
        }
    }

    /**
     * <strong>Method to facilitate testing. Should not be considered part of the public API.</strong>
     * 
     * @param theContext the context who's existing in the helper map is being requested
     * @return <code>true</code> if contained in map; <code>false</code> otherwise.
     */
    public boolean testVdbContextHelperMapContains( VdbEditingContext theContext ) {
        return this.vdbHelperMap.containsKey(theContext);
    }
}
