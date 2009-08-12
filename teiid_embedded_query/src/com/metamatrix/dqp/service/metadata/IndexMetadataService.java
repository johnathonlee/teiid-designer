/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package com.metamatrix.dqp.service.metadata;

import java.util.Map;
import java.util.Properties;
import org.teiid.connector.metadata.runtime.DatatypeRecordImpl;
import com.google.inject.Inject;
import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.common.application.ApplicationEnvironment;
import com.metamatrix.common.application.exception.ApplicationLifecycleException;
import com.metamatrix.common.log.LogManager;
import com.metamatrix.common.vdb.api.VDBArchive;
import com.metamatrix.connector.metadata.IndexFile;
import com.metamatrix.connector.metadata.MetadataConnectorConstants;
import com.metamatrix.connector.metadata.MultiObjectSource;
import com.metamatrix.connector.metadata.PropertyFileObjectSource;
import com.metamatrix.connector.metadata.internal.IObjectSource;
import com.metamatrix.dqp.DQPPlugin;
import com.metamatrix.dqp.service.DQPServiceNames;
import com.metamatrix.dqp.service.MetadataService;
import com.metamatrix.dqp.service.VDBService;
import com.metamatrix.dqp.util.LogConstants;
import com.metamatrix.modeler.core.index.IndexSelector;
import com.metamatrix.query.metadata.QueryMetadataInterface;

/**
 * Implementation of MetadaService using index files.
 */
/**
 * @since 4.2
 */
public class IndexMetadataService implements MetadataService {

    private VDBService vdbService;
    private boolean started = false;
    private QueryMetadataCache metadataCache;

    /**
     * Construct the IndexMetadataService
     */
    public IndexMetadataService() {
    }

    /**
     * Construct the IndexMetadataService
     */
    @Inject
    public IndexMetadataService( final QueryMetadataCache metadataCache ) {
        this.metadataCache = metadataCache;
    }

    /*
     * @see com.metamatrix.common.application.ApplicationService#initialize(java.util.Properties)
     */
    public void initialize( Properties props ) {
    }

    /*
     * @see com.metamatrix.common.application.ApplicationService#start(com.metamatrix.common.application.ApplicationEnvironment)
     */
    public void start( ApplicationEnvironment environment ) throws ApplicationLifecycleException {
        if (!started) {
            this.vdbService = (VDBService)environment.findService(DQPServiceNames.VDB_SERVICE);
            if (this.vdbService == null) {
                throw new ApplicationLifecycleException(
                                                        DQPPlugin.Util.getString("IndexMetadataService.VDB_Service_is_not_available._1")); //$NON-NLS-1$
            }
            // mark started
            started = true;
        }
    }

    /*
     * @see com.metamatrix.common.application.ApplicationService#stop()
     */
    public void stop() {
        started = false;
        this.vdbService = null;
    }

    public IObjectSource getMetadataObjectSource( String vdbName,
                                                  String vdbVersion ) {
        IndexSelector indexSelector = this.metadataCache.getCompositeSelector(vdbName, vdbVersion);

        // build up sources to be used by the index connector
        IObjectSource indexFile = new IndexFile(indexSelector, vdbName, vdbVersion, this.vdbService);

        PropertyFileObjectSource propertyFileSource = new PropertyFileObjectSource();
        IObjectSource multiObjectSource = new MultiObjectSource(indexFile, MetadataConnectorConstants.PROPERTIES_FILE_EXTENSION,
                                                                propertyFileSource);

        // return an adapter object that has access to all sources
        return multiObjectSource;
    }

    /**
     * @see com.metamatrix.dqp.service.MetadataService#lookupMetadata(java.lang.String, java.lang.String)
     * @since 4.2
     */
    public QueryMetadataInterface lookupMetadata( final String vdbName,
                                                  final String vdbVersion ) throws MetaMatrixComponentException {
        LogManager.logTrace(LogConstants.CTX_DQP, new Object[] {"IndexMetadataService lookup VDB", vdbName, vdbVersion}); //$NON-NLS-1$
        QueryMetadataInterface qmi = this.metadataCache.lookupMetadata(vdbName, vdbVersion);
        if (qmi == null) {
            LogManager.logTrace(LogConstants.CTX_DQP, new Object[] {
                "IndexMetadataService cache miss for VDB", vdbName, vdbVersion}); //$NON-NLS-1$
            return this.metadataCache.lookupMetadata(vdbName,
                                                     vdbVersion,
                                                     VDBArchive.writeToByteArray(this.vdbService.getVDB(vdbName, vdbVersion)));
        }
        return qmi;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.metamatrix.dqp.service.MetadataService#getBuiltinDatatypes()
     */
    @Override
    public Map<String, DatatypeRecordImpl> getBuiltinDatatypes() {
        return null;
    }

}
