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

package com.metamatrix.modeler.jdbc.metadata.impl;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.eclipse.core.runtime.IStatus;

import com.metamatrix.modeler.internal.jdbc.JdbcUtil;
import com.metamatrix.modeler.jdbc.JdbcPlugin;
import com.metamatrix.modeler.jdbc.data.MetadataRequest;
import com.metamatrix.modeler.jdbc.data.Response;

/**
 * GetIndexesRequest
 */
public class GetIndexesRequest extends MetadataRequest {
    
    public static final String NAME = JdbcPlugin.Util.getString("GetIndexesRequestName"); //$NON-NLS-1$
    private static final String METHOD_NAME = "getIndexInfo"; //$NON-NLS-1$
    
    /**
     * Construct an instance of GetIndexesRequest.
     * @param metadata
     * @param catalogNamePattern
     * @param schemaNamePattern
     * @param tableNamePattern
     * @param uniqueValuesOnly
     * @param approximateAllowed
     */
    public GetIndexesRequest( final DatabaseMetaData metadata,
                              final String catalogNamePattern, 
                              final String schemaNamePattern,
                              final String tableNamePattern,
                              final boolean uniqueValuesOnly, 
                              final boolean approximateAllowed  ) {
        super(NAME, metadata, METHOD_NAME, 
              new Object[]{catalogNamePattern,schemaNamePattern,tableNamePattern,
                           new Boolean(uniqueValuesOnly), new Boolean(approximateAllowed)});
    }
    
    /** 
     * This method is overridden to optimize performance.
     * @see com.metamatrix.modeler.jdbc.data.MethodRequest#performInvocation(com.metamatrix.modeler.jdbc.data.Response)
     * @since 4.2
     */
    @Override
    protected IStatus performInvocation(final Response results) {
        // Override to optimize ...
        final DatabaseMetaData dbmd = this.getDatabaseMetaData();
        ResultSet resultSet = null;
        IStatus status = null;
        try {
            final String catalogPattern = (String)getParameters()[0];
            final String schemaPattern  = (String)getParameters()[1];
            final String tablePattern   = (String)getParameters()[2];
            final boolean unique       = ((Boolean)getParameters()[3]).booleanValue();
            final boolean approximate  = ((Boolean)getParameters()[4]).booleanValue();
            resultSet = dbmd.getIndexInfo(catalogPattern, schemaPattern, tablePattern, unique, approximate);
            Response.addResults(results,resultSet,this.isMetadataRequested());
        } catch ( SQLException e ) {
            status = JdbcUtil.createIStatus(e,e.getLocalizedMessage());
        } finally {
            if ( resultSet != null ) {
                try {
                    resultSet.close();
                } catch (SQLException e1) {
                }
            }
        }
        return status;
    }
    
}
