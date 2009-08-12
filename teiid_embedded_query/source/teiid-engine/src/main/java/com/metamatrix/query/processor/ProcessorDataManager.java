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

package com.metamatrix.query.processor;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.MetaMatrixProcessingException;
import com.metamatrix.common.buffer.BlockedException;
import com.metamatrix.common.buffer.TupleSource;
import com.metamatrix.query.sql.lang.Command;
import com.metamatrix.query.util.CommandContext;

public interface ProcessorDataManager {

	TupleSource registerRequest(Object processorId, Command command, String modelName, String connectorBindingId, int nodeID)
		throws MetaMatrixComponentException, MetaMatrixProcessingException;
	
    /**
     * Lookup a value from a cached code table.  If the code table is not loaded, it will be 
     * loaded on the first query.  Code tables should be cached based on a combination of
     * the codeTableName, returnElementName, and keyElementName.  If the table is not loaded,
     * a request will be made and the method should throw a BlockedException.
     */
    Object lookupCodeValue(CommandContext context,
                                           String codeTableName,
                                           String returnElementName,
                                           String keyElementName,
                                           Object keyValue) throws BlockedException,
                                                           MetaMatrixComponentException, MetaMatrixProcessingException;
    
    void clearCodeTables();
}
