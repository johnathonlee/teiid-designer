/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package com.metamatrix.modeler.dqp.internal.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.eclipse.core.runtime.NullProgressMonitor;
import com.metamatrix.common.config.api.ComponentType;
import com.metamatrix.common.config.api.ComponentTypeDefn;
import com.metamatrix.common.config.api.ConnectorBinding;
import com.metamatrix.common.config.api.ConnectorBindingType;
import com.metamatrix.common.vdb.api.ModelInfo;
import com.metamatrix.core.modeler.util.ArgCheck;
import com.metamatrix.core.util.StringUtil;
import com.metamatrix.metamodels.core.ModelType;
import com.metamatrix.modeler.dqp.DqpPlugin;
import com.metamatrix.modeler.dqp.JDBCConnectionPropertyNames;
import com.metamatrix.modeler.dqp.config.ConfigurationManager;
import com.metamatrix.modeler.dqp.config.ModelConnectorBindingMapper;
import com.metamatrix.modeler.dqp.util.ModelerDqpUtils;
import com.metamatrix.vdb.edit.VdbContextEditor;
import com.metamatrix.vdb.edit.VdbEditingContext;
import com.metamatrix.vdb.edit.manifest.ModelReference;
import com.metamatrix.vdb.edit.manifest.VirtualDatabase;
import com.metamatrix.vdb.internal.edit.InternalVdbEditingContext;


/** 
 * @since 4.3
 */
public class ModelConnectorBindingMapperImpl implements ModelConnectorBindingMapper {

    private final InternalVdbEditingContext context;
    private final VdbContextEditor contextEditor;
    // modelreference -> Collection(ConnectorBinding)
    private Map modelConnectorBindingMatches;
    // modelreference -> Collection(ConnectorType)
    private Map modelConnectorTypeMatches;
    // modelreference -> ConnectorBinding
    private Map modelConnectorBindings;
    private ConfigurationManager manager;
    
    //Used to enable Unit Testing.
    public static boolean HEADLESS = false;

    /**
     * ModelConnectorBindingMapperImpl constructor.
     * @param context The editing context for the vdb...cannot be null.
     * @param vdbDefn The VDB definition, cannot be null.
     * @since 4.3
     */
    public ModelConnectorBindingMapperImpl(final VdbEditingContext theContext) throws Exception {
        ArgCheck.isNotNull(theContext);
        if(!theContext.isOpen()) {
            theContext.open();
        }
        this.context = (InternalVdbEditingContext)theContext; // safe to cast
        this.contextEditor = null;
    }
    public ModelConnectorBindingMapperImpl(final VdbContextEditor theContext) throws Exception {
        ArgCheck.isNotNull(theContext);
        if(!theContext.isOpen()) {
            theContext.open(new NullProgressMonitor());
        }
        this.context = null;
        this.contextEditor = theContext; 
    }

    /** 
     * @see com.metamatrix.modeler.dqp.config.ModelConnectorBindingMapper#findAllConnectorBindingMatches()
     * @since 4.3
     */
    public Map findAllConnectorBindingMatches() {
        if(this.modelConnectorBindingMatches == null) {
            updateConnectorBindingMatches();
        }
        return this.modelConnectorBindingMatches;
    }

    /** 
     * @see com.metamatrix.modeler.dqp.config.ModelConnectorBindingMapper#getAllConnectorBindings()
     * @since 4.3
     */
    public Map getAllConnectorBindings() {
        if(this.modelConnectorBindings == null) {
            VdbDefnHelper helper = getVdbDefnHelper();
            Map modelPathBindingMap = Collections.EMPTY_MAP;
            if(helper!=null) {
                modelPathBindingMap = helper.getVdbDefn().getModelToBindingMappings();
            }
            
            for(final Iterator iter = modelPathBindingMap.keySet().iterator(); iter.hasNext();) {
                String modelPath = (String) iter.next();
                ModelReference reference = getModelReference(modelPath);
                ModelInfo m = helper.getVdbDefn().getModel(reference.getName());
                List bindingNames = m.getConnectorBindingNames();
                if (bindingNames != null && bindingNames.size() > 0) {
                    String bindingName = (String) bindingNames.iterator().next();
                    ConnectorBinding binding = helper.getVdbDefn().getConnectorBindingByName(bindingName);
                    if (binding != null) {
                        this.modelConnectorBindings.put(reference, binding);
                    }
                    
                }
            }
        }
        return this.modelConnectorBindings;
    }

    /** 
     * @see com.metamatrix.modeler.dqp.config.ModelConnectorBindingMapper#findConnectorBindingMatch(com.metamatrix.vdb.edit.manifest.ModelReference)
     * @since 4.3
     */
    public Collection findConnectorBindingMatches(ModelReference modelReference) {
        if(modelReference.getModelType() != ModelType.PHYSICAL_LITERAL) {
            return Collections.EMPTY_LIST;
        }
        Map bindingMap = findAllConnectorBindingMatches();
        return (Collection) bindingMap.get(modelReference);
    }
    
    
    /** 
     * @see com.metamatrix.modeler.dqp.config.ModelConnectorBindingMapper#findConnectorTypeMatches(com.metamatrix.vdb.edit.manifest.ModelReference)
     * @since 4.3
     */
    public Collection findConnectorTypeMatches(ModelReference modelReference) {
        if(modelReference.getModelType() != ModelType.PHYSICAL_LITERAL) {
            return Collections.EMPTY_LIST;
        }        
        // update matches
        updateConnectorTypeMatches();
        Collection matches = (Collection) this.modelConnectorTypeMatches.get(modelReference);
        if(matches != null) {
            return matches;
        }
        return Collections.EMPTY_LIST;
    }

    /** 
     * @see com.metamatrix.modeler.dqp.config.ModelConnectorBindingMapper#getConnectorBinding(com.metamatrix.vdb.edit.manifest.ModelReference)
     * @since 4.3
     */
    public ConnectorBinding getConnectorBinding(ModelReference modelReference) {
        if(modelReference.getModelType() != ModelType.PHYSICAL_LITERAL) {
            return null;
        }
        Map bindingMap = getAllConnectorBindings();
        return (ConnectorBinding) bindingMap.get(modelReference);
    }

    /** 
     * @see com.metamatrix.modeler.dqp.config.ModelConnectorBindingMapper#setConnectorBinding(com.metamatrix.vdb.edit.manifest.ModelReference, com.metamatrix.common.config.api.ConnectorBinding)
     * @since 4.3
     */
    public void setConnectorBinding(ModelReference modelReference, ConnectorBinding binding) {
        if(modelReference.getModelType() != ModelType.PHYSICAL_LITERAL) {
            return;
        }        
        Map bindingMap = getAllConnectorBindings();
        bindingMap.put(modelReference, binding);
    }

    /**
     * @see com.metamatrix.modeler.dqp.config.ModelConnectorBindingMapper#createConnectorBinding(com.metamatrix.vdb.edit.manifest.ModelReference, com.metamatrix.common.config.api.ConnectorBindingType)
     * @since 4.3
     */
    public ConnectorBinding createConnectorBinding(ModelReference modelReference,
                                                   ConnectorBindingType connectorBindingType,
                                                   String theName) throws Exception {
        return getConfigurationManager().createConnectorBinding(modelReference, connectorBindingType, theName);
    }

    /**
     * Populate the the map of modelreferences to the connector bindings that
     * have matching connection properties. 
     * @since 4.3
     */
    private void updateConnectorBindingMatches() {
        if(this.modelConnectorBindingMatches == null) {
            this.modelConnectorBindingMatches = new HashMap();
        }
        for(final Iterator iter = getModelReferences().iterator(); iter.hasNext();) {
            ModelReference reference = (ModelReference) iter.next();
            Collection bindings = findMatchingConnectorBindings(reference);
            if(bindings != null && !bindings.isEmpty()) {
                this.modelConnectorBindingMatches.put(reference, bindings);
            }
        }
    }

    /**
     * Find a connector binding whose properties match the jdbc connection properties
     * stored on the model reference.  
     * @param reference The model reference 
     * @return collection of Matching connector binding
     * @since 4.3
     */
    private Collection findMatchingConnectorBindings(ModelReference reference) {
        if(reference.getModelType() != ModelType.PHYSICAL_LITERAL) {
            return Collections.EMPTY_LIST;
        }
        Map jdbcProperties = ModelerDqpUtils.getModelJdbcProperties(reference);
        if(!jdbcProperties.isEmpty()) {

        	// First check bindings in the context
        	VdbDefnHelper vdbDefnHelper = getVdbDefnHelper();
        	Collection contextBindings = Collections.EMPTY_LIST;
        	if(vdbDefnHelper!=null) {
            	contextBindings = vdbDefnHelper.getVdbDefn().getConnectorBindings().values();
        	}
        	Collection bindingMatches = getMatchingBindings(jdbcProperties,contextBindings);
        	
        	if(bindingMatches.isEmpty()) {
	            // get all the available connector bindings from config manager
	            Collection configBindings = getConfigurationManager().getConnectorBindings();
	            
	            // matching bindings
	            bindingMatches = getMatchingBindings(jdbcProperties,configBindings);
        	}
            
            return bindingMatches;
        }
        return Collections.EMPTY_LIST;
    }
    
    /**
     * Given the jdbc properties and a list of available bindings, find all the matching bindings
     * @param jdbcProperties the jdbc properties
     * @param bindings the list of available bindings
     * @return the list of matching bindings
     */
    private Collection getMatchingBindings(Map jdbcProperties, Collection bindings) {
        // matching bindings
        Collection bindingMatches = new ArrayList();
        // for each binding get properties
        for(final Iterator iter1 = bindings.iterator(); iter1.hasNext();) {
            ConnectorBinding binding = (ConnectorBinding) iter1.next();
            
            ComponentType type = DqpPlugin.getInstance().getConfigurationManager().getConnectorType(binding.getComponentTypeID());
            
            String driverClassName = binding.getProperty(JDBCConnectionPropertyNames.CONNECTOR_JDBC_DRIVER_CLASS);
            
            if( driverClassName == null ) {
                // if no value set see if the type has a default value
                ComponentTypeDefn defn = type.getComponentTypeDefinition(JDBCConnectionPropertyNames.CONNECTOR_JDBC_DRIVER_CLASS);

                if ((defn != null) && defn.getPropertyDefinition().hasDefaultValue()) {
                    Object defaultValue = defn.getPropertyDefinition().getDefaultValue();
                    
                    if (defaultValue != null) {
                    	driverClassName = defaultValue.toString();
                    }
                }
            }
            
            String url = binding.getProperty(JDBCConnectionPropertyNames.CONNECTOR_JDBC_URL);
            
            if( url == null ) {
                // if no value set see if the type has a default value
                ComponentTypeDefn defn = type.getComponentTypeDefinition(JDBCConnectionPropertyNames.CONNECTOR_JDBC_URL);

                if ((defn != null) && defn.getPropertyDefinition().hasDefaultValue()) {
                    Object defaultValue = defn.getPropertyDefinition().getDefaultValue();
                    
                    if (defaultValue != null) {
                    	url = defaultValue.toString();
                    }
                }
            }
            
            String user = binding.getProperty(JDBCConnectionPropertyNames.CONNECTOR_JDBC_USER);
            
            
            if( user == null ) {
                // if no value set see if the type has a default value
                ComponentTypeDefn defn = type.getComponentTypeDefinition(JDBCConnectionPropertyNames.CONNECTOR_JDBC_USER);

                if ((defn != null) && defn.getPropertyDefinition().hasDefaultValue()) {
                    Object defaultValue = defn.getPropertyDefinition().getDefaultValue();
                    
                    if (defaultValue != null) {
                    	user = defaultValue.toString();
                    }
                }
            }
            
            if(StringUtil.isEmpty(driverClassName) || StringUtil.isEmpty(url)) {
                continue;
            }
            String jdbcClassName = (String) jdbcProperties.get(JDBCConnectionPropertyNames.JDBC_IMPORT_DRIVER_CLASS);
            if(StringUtil.isEmpty(jdbcClassName) || !driverClassName.equals(jdbcClassName)) {
                continue;
            }

            String jdbcUrl = (String) jdbcProperties.get(JDBCConnectionPropertyNames.JDBC_IMPORT_URL);
            if(StringUtil.isEmpty(jdbcUrl) || !url.equalsIgnoreCase(jdbcUrl)) {
                continue;
            }

            String userName = (String) jdbcProperties.get(JDBCConnectionPropertyNames.JDBC_IMPORT_USERNAME);
            if ((StringUtil.isEmpty(userName) && !StringUtil.isEmpty(user))
                || (!StringUtil.isEmpty(userName) && StringUtil.isEmpty(user))
                || ((!StringUtil.isEmpty(userName) && !StringUtil.isEmpty(user)) && !user.equalsIgnoreCase(userName))) {
                continue;
            }

            bindingMatches.add(binding);
        }
        return bindingMatches;
    }

    /**
     * Populate the the map of modelreferences to the connector types that
     * have matching connection properties. 
     * @since 4.3
     */
    private void updateConnectorTypeMatches() {
        if(this.modelConnectorTypeMatches == null) {
            this.modelConnectorTypeMatches = new HashMap();
        }
        for(final Iterator iter = getModelReferences().iterator(); iter.hasNext();) {
            ModelReference reference = (ModelReference) iter.next();
            Collection types = findMatchingConnectorType(reference);
            if(types != null && !types.isEmpty()) {
                this.modelConnectorTypeMatches.put(reference, types);
            }
        }
    }

    /**
     * Find a connector types whose properties match the jdbc connection properties
     * stored on the model reference.  
     * @param reference The model reference 
     * @return Matching connector types
     * @since 4.3
     */
    private Collection findMatchingConnectorType(ModelReference reference) {
        if(reference.getModelType() != ModelType.PHYSICAL_LITERAL) {
            return Collections.EMPTY_LIST;
        }        
        Map jdbcProperties = ModelerDqpUtils.getModelJdbcProperties(reference);
        Collection connTypes = new ArrayList();
        if(!jdbcProperties.isEmpty()) {
            // get all the available connector bindings
            Collection types = getConfigurationManager().getConnectorTypes();
            // for each binding get properties
            for(final Iterator iter1 = types.iterator(); iter1.hasNext();) {
                ConnectorBindingType connectorType = (ConnectorBindingType) iter1.next();
                Properties connectorTypeProps = connectorType.getDefaultPropertyValues();
                String driverClassName = connectorTypeProps.getProperty(JDBCConnectionPropertyNames.CONNECTOR_JDBC_DRIVER_CLASS);
                if(StringUtil.isEmpty(driverClassName)) {
                    continue;
                }
                String jdbcClassName = (String) jdbcProperties.get(JDBCConnectionPropertyNames.JDBC_IMPORT_DRIVER_CLASS);
                if(StringUtil.isEmpty(jdbcClassName) || !driverClassName.equalsIgnoreCase(jdbcClassName)) {
                    continue;
                }
                connTypes.add(connectorType);
            }
        }
        return connTypes;
    }

    /**
     * Get all model references on the vdb manifest model. 
     * @return a collection of model references.
     * @since 4.3
     */
    private Collection getModelReferences() {
        VirtualDatabase virtualDatabase = null;
        if (this.context != null) {
            virtualDatabase = this.context.getVirtualDatabase();
        } else if (this.contextEditor != null) {
            virtualDatabase = this.contextEditor.getVirtualDatabase();
        }
        return virtualDatabase.getModels();
    }
    
    private ModelReference getModelReference(final String modelPath) {
        ModelReference reference = null;
        if (this.context != null) {
            reference = this.context.getModelReference(modelPath);
        } else if (this.contextEditor != null) {
            reference = this.contextEditor.getModelReference(modelPath);
        }
        return reference;
    }
    
    private VdbDefnHelper getVdbDefnHelper() {
        VdbDefnHelper helper = null;
        if(!HEADLESS) {
	        if (this.context != null) {
	            helper = DqpPlugin.getInstance().getVdbDefnHelper(this.context);
	        } else if (this.contextEditor != null) {
	            helper = DqpPlugin.getInstance().getVdbDefnHelper(this.contextEditor);
	        }
        }
        return helper;
    }

    /**
     * Get the configuration manager stored on the mapper or lookup the default manager
     * from the plugin.  
     * @return The config manage
     * @since 4.3
     */
    private ConfigurationManager getConfigurationManager() {
        return (this.manager != null) ? this.manager : DqpPlugin.getInstance().getConfigurationManager();
    }

    /**
     * Set the configuration manager used by the mapper to find
     * connector bindinggs. 
     * @param manager The manager to set.
     * @since 4.3
     */
    public void setManager(ConfigurationManager manager) {
        this.manager = manager;
    }    
}
