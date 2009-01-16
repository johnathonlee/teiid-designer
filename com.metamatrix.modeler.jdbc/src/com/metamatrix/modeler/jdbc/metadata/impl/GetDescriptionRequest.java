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

import com.metamatrix.modeler.jdbc.JdbcPlugin;
import com.metamatrix.modeler.jdbc.data.MethodRequest;
import com.metamatrix.modeler.jdbc.metadata.JdbcNode;

/**
 * GetDescriptionRequest
 */
public class GetDescriptionRequest extends MethodRequest {
    
    public static final String NAME = JdbcPlugin.Util.getString("GetDescriptionRequestName"); //$NON-NLS-1$
    
    /**
     * Construct an instance of GetDescriptionRequest.
     * 
     */
    public GetDescriptionRequest( final JdbcNode node, final String methodName ) {
        super(NAME,node,methodName,new Object[]{});
    }

}
