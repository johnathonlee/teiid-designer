/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */

package org.teiid.designer.metadata.runtime.api;

/**
 * @since 8.0
 */
public class RuntimeMetadataPropertyNames {

    /**
     * The resource name that identifies runtime metadata.
     */
    public static final String RESOURCE_NAME = "RuntimeMetadata"; //$NON-NLS-1$

    // connection properties
    /**
     * The environment property name for the class that is to be used for the MetadataConnectionFactory implementation. This
     * property is required (there is no default).
     */
    public static final String CONNECTION_FACTORY = "metamatrix.metadata.runtime.connection.Factory"; //$NON-NLS-1$

    /**
     * The environment property name for the class of the driver. This property is optional.
     */
    public static final String CONNECTION_DRIVER = "metamatrix.metadata.runtime.connection.Driver"; //$NON-NLS-1$

    /**
     * The environment property name for the protocol for connecting to the metadata store. This property is optional.
     */
    public static final String CONNECTION_PROTOCOL = "metamatrix.metadata.runtime.connection.Protocol"; //$NON-NLS-1$

    /**
     * The environment property name for the name of the metadata store database. This property is optional.
     */
    public static final String CONNECTION_DATABASE = "metamatrix.metadata.runtime.connection.Database"; //$NON-NLS-1$

    /**
     * The environment property name for the username that is to be used for connecting to the metadata store. This property is
     * optional.
     */
    public static final String CONNECTION_USERNAME = "metamatrix.metadata.runtime.connection.User"; //$NON-NLS-1$

    /**
     * The environment property name for the password that is to be used for connecting to the metadata store. This property is
     * optional.
     */
    public static final String CONNECTION_PASSWORD = "metamatrix.metadata.runtime.connection.Password"; //$NON-NLS-1$

    /**
     * The environment property name for the maximum number of milliseconds that a metadata connection may remain unused before it
     * becomes a candidate for garbage collection. This property is optional.
     */
    public static final String CONNECTION_POOL_MAXIMUM_AGE = "metamatrix.metadata.runtime.connection.MaximumAge"; //$NON-NLS-1$

    /**
     * The environment property name for the maximum number of concurrent users of a single metadata connection. This property is
     * optional.
     */
    public static final String CONNECTION_POOL_MAXIMUM_CONCURRENT_USERS = "metamatrix.metadata.runtime.connection.MaximumConcurrentReaders"; //$NON-NLS-1$

    // Others
    /*
     * The property determines whether to persist virtual database in runtime repository.
     * By default, the value is true, which means the virtual databases will be stored in runtime repository.
     * Otherwise, the virtual database will only stay in memory.
     * The property is optional.
     */
    public static final String PERSIST = "metamatrix.metadata.runtime.persist"; //$NON-NLS-1$

    public static final String METADATA_SUPPLIER_CLASS_NAME = "metamatrix.metadata.runtime.supplierClass"; //$NON-NLS-1$

    public static final String METADATA_RESOURCE_CLASS_NAME = "metamatrix.metadata.runtime.resourceClass"; //$NON-NLS-1$

    public static final String RT_VIRTUAL_MODEL_NAME = "metamatrix.metadata.runtime.virtualmodel.name"; //$NON-NLS-1$

    public static final String RT_VIRTUAL_MODEL_VERSION = "metamatrix.metadata.runtime.virtualmodel.version"; //$NON-NLS-1$

    public static final String RT_PHYSICAL_MODEL_NAME = "metamatrix.metadata.runtime.physicalmodel.name"; //$NON-NLS-1$

    public static final String RT_PHYSICAL_MODEL_VERSION = "metamatrix.metadata.runtime.physicalmodel.version"; //$NON-NLS-1$

    /*
     * Default values for creating VDB not through console
     */
    public static final String RT_USER_VDB_NAME = "metamatrix.metadata.runtime.uservdb.name"; //$NON-NLS-1$
    public static final String RT_USER_VDB_GUID = "metamatrix.metadata.runtime.uservdb.guid"; //$NON-NLS-1$
    public static final String RT_USER_VDB_PRINCIPAL_NAME = "metamatrix.metadata.runtime.uservdb.principalName"; //$NON-NLS-1$
    public static final String RT_USER_VDB_DESCRIPTION = "metamatrix.metadata.runtime.uservdb.description"; //$NON-NLS-1$
}
