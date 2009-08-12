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

package com.metamatrix.query.processor.xml;

import java.util.HashMap;
import java.util.Map;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.MetaMatrixProcessingException;
import com.metamatrix.common.buffer.BlockedException;
import com.metamatrix.common.log.LogManager;
import com.metamatrix.query.mapping.xml.ResultSetInfo;
import com.metamatrix.query.util.LogConstants;


/** 
 * This instruction is to start loading a staging table. The difference between the loading the 
 * staging table and execute sql node is that sql node will capture the results and save them in
 * the context object, where as staging does not care about the results, beacuse they are actully
 * stored in the temp table store, will be accessed by required query directly from there, as these
 * results are nothing do with producing the document directly.
 * 
 * NOTE: In future we can improve this to load parallelly, if there are more than single 
 * staging table defined on the mapping document
 */
public class ExecStagingTableInstruction extends ProcessorInstruction {
    String resultSetName;
    ResultSetInfo info;
    
    public ExecStagingTableInstruction(String resultName, ResultSetInfo info) {
        this.resultSetName = resultName;
        this.info = info;
    }
    
    /** 
     * @see com.metamatrix.query.processor.xml.ProcessorInstruction#process(com.metamatrix.query.processor.xml.XMLProcessorEnvironment, com.metamatrix.query.processor.xml.XMLContext)
     */
    public XMLContext process(XMLProcessorEnvironment env, XMLContext context) 
        throws BlockedException, MetaMatrixComponentException, MetaMatrixProcessingException {
        
        if (!env.isStagingTableLoaded(this.resultSetName)) {
            LogManager.logTrace(LogConstants.CTX_XML_PLAN, new Object[]{"SQL: Result set DOESN'T exist:",resultSetName}); //$NON-NLS-1$
            
            PlanExecutor executor = context.getResultExecutor(resultSetName);
            if (executor == null) {
                executor = env.createResultExecutor(resultSetName, info);
                context.setResultExecutor(resultSetName, executor);
            }
            
            // this execute can throw the blocked exception; note that staging tables will not have any
            // bound references; they are not part of the document; so they do not know about document
            // details.
            Map referenceValues = null;
            executor.execute(referenceValues);
            env.markStagingTableAsLoaded(this.resultSetName);
            
            // now that we done executing the plan; remove the plan from context
            context.removeResultExecutor(resultSetName);
        }
        
        env.incrementCurrentProgramCounter();
        return context;
    }
    
    public String toString() {
        return "STAGING:"+resultSetName; //$NON-NLS-1$
    }

    public Map getDescriptionProperties() {
        Map props = new HashMap();
        props.put(PROP_TYPE, "Staging Table"); //$NON-NLS-1$
        props.put(PROP_RESULT_SET, this.resultSetName);
        props.put(PROP_IS_STAGING, "true"); //$NON-NLS-1$        
        return props;
    }    
    
}
