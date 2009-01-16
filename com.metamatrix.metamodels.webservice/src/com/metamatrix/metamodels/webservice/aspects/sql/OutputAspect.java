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

package com.metamatrix.metamodels.webservice.aspects.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.impl.EObjectImpl;

import com.metamatrix.core.util.ArgCheck;
import com.metamatrix.metamodels.webservice.Output;
import com.metamatrix.metamodels.webservice.SampleMessages;
import com.metamatrix.metamodels.webservice.WebServicePackage;
import com.metamatrix.modeler.core.index.IndexConstants;
import com.metamatrix.modeler.core.metadata.runtime.MetadataConstants;
import com.metamatrix.modeler.core.metamodel.aspect.MetamodelEntity;
import com.metamatrix.modeler.core.metamodel.aspect.sql.SqlDatatypeCheckerAspect;
import com.metamatrix.modeler.core.metamodel.aspect.sql.SqlResultSetAspect;


/** 
 * OutputAspect
 */
public class OutputAspect extends WebServiceComponentAspect implements SqlResultSetAspect, SqlDatatypeCheckerAspect {

    /** 
     * OutputAspect
     * @param entity
     * @since 4.2
     */
    protected OutputAspect(final MetamodelEntity entity) {
        super(entity);
    }

    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.sql.SqlColumnSetAspect#getColumns(org.eclipse.emf.ecore.EObject)
     * @since 4.2
     */
    public List getColumns(final EObject eObject) {
        ArgCheck.isInstanceOf(Output.class, eObject);
        final Output output = (Output)eObject;
        SampleMessages sampleMsgs = output.getSamples();
        if(sampleMsgs != null) {
	        List columns = new ArrayList(1);
	        columns.add(sampleMsgs);
	        return columns;
        }
        return Collections.EMPTY_LIST;
    }

    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.sql.SqlColumnSetAspect#getColumnSetType()
     * @since 4.2
     */
    public int getColumnSetType() {
        return MetadataConstants.COLUMN_SET_TYPES.PROCEDURE_RESULT;
    }

    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.sql.SqlAspect#isRecordType(char)
     * @since 4.2
     */
    public boolean isRecordType(final char recordType) {
        return (recordType == IndexConstants.RECORD_TYPE.RESULT_SET);
    }

    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.sql.SqlDatatypeCheckerAspect#isDatatypeFeature(org.eclipse.emf.ecore.EObject, org.eclipse.emf.ecore.EStructuralFeature)
     * @since 4.3
     */
    public boolean isDatatypeFeature(EObject eObject, EStructuralFeature eFeature) {
        ArgCheck.isInstanceOf(Output.class, eObject); 
        final EObjectImpl eObjectImpl = super.getEObjectImpl(eObject);
        if (eObjectImpl != null) {
            switch (eObjectImpl.eDerivedStructuralFeatureID(eFeature)) {
                case WebServicePackage.OUTPUT__CONTENT_SIMPLE_TYPE:
                    return true;
            }
        }
        return false;
    }

    /**
     * Return the procedure that produces this result set. 
     * @return
     * @since 5.0.2
     */
    public Object getProcedure(EObject eObject) {
        ArgCheck.isInstanceOf(Output.class, eObject); 
        return ((Output)eObject).getOperation();
    }
}
