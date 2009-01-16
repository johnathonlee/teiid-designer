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
import java.util.List;

import org.eclipse.emf.ecore.EObject;

import com.metamatrix.core.util.ArgCheck;
import com.metamatrix.metamodels.webservice.Operation;
import com.metamatrix.modeler.core.index.IndexConstants;
import com.metamatrix.modeler.core.metamodel.aspect.MetamodelEntity;
import com.metamatrix.modeler.core.metamodel.aspect.sql.SqlAspect;
import com.metamatrix.modeler.core.metamodel.aspect.sql.SqlAspectHelper;
import com.metamatrix.modeler.core.metamodel.aspect.sql.SqlProcedureAspect;
import com.metamatrix.modeler.core.metamodel.aspect.sql.SqlTableAspect;


/** 
 * OperationAspect
 */
public class OperationAspect extends WebServiceComponentAspect implements SqlProcedureAspect {

    protected OperationAspect(final MetamodelEntity entity) {
        super(entity);
    }

    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.sql.SqlProcedureAspect#isVirtual(org.eclipse.emf.ecore.EObject)
     * @since 4.2
     */
    public boolean isVirtual(final EObject eObject) {
        ArgCheck.isInstanceOf(Operation.class, eObject);
        return true;
    }

    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.sql.SqlProcedureAspect#isFunction(org.eclipse.emf.ecore.EObject)
     * @since 4.2
     */
    public boolean isFunction(final EObject eObject) {
        ArgCheck.isInstanceOf(Operation.class, eObject);
        return false;
    }

    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.sql.SqlProcedureAspect#getParameters(org.eclipse.emf.ecore.EObject)
     * @since 4.2
     */
    public List getParameters(final EObject eObject) {
        ArgCheck.isInstanceOf(Operation.class, eObject);
        Operation opearation = (Operation) eObject;
        List params = new ArrayList(1);
        params.add(opearation.getInput());
        return params;
    }

    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.sql.SqlProcedureAspect#getResult(org.eclipse.emf.ecore.EObject)
     * @since 4.2
     */
    public Object getResult(final EObject eObject) {
        ArgCheck.isInstanceOf(Operation.class, eObject);
        Operation opearation = (Operation) eObject;
        return opearation.getOutput();
    }
    
    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.sql.SqlProcedureAspect#getUpdateCount(org.eclipse.emf.ecore.EObject)
     * @since 5.5.3
     */
    public int getUpdateCount(EObject eObject) {
        return 0;
    }

    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.sql.SqlAspect#isRecordType(char)
     * @since 4.2
     */
    public boolean isRecordType(final char recordType) {
        return (recordType == IndexConstants.RECORD_TYPE.CALLABLE);
    }

    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.sql.SqlProcedureAspect#isMappable(org.eclipse.emf.ecore.EObject, int)
     * @since 4.2
     */
    public boolean isMappable(final EObject eObject, final int mappingType) {
        return (mappingType == SqlProcedureAspect.MAPPINGS.SQL_TRANSFORM);
    }

    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.sql.SqlTableAspect#canAcceptTransformationSource(org.eclipse.emf.ecore.EObject, org.eclipse.emf.ecore.EObject)
     * @since 4.3
     */
    public boolean canAcceptTransformationSource(EObject target, EObject source) {
        ArgCheck.isInstanceOf(Operation.class, target);
        ArgCheck.isNotNull(source);
        // no object should be source of itself
        if(source == target) {
            return false;
        }
        if(isVirtual(target)) {
            // source cannot be an operation
            if(source instanceof Operation) {
                return canBeTransformationSource(source, target);
            }
            SqlAspect sourceAspect = SqlAspectHelper.getSqlAspect(source);
            if(sourceAspect instanceof SqlTableAspect || sourceAspect instanceof SqlProcedureAspect) {
                return true;
            }
        }
        return false;
    }

    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.sql.SqlTableAspect#canBeTransformationSource(org.eclipse.emf.ecore.EObject, org.eclipse.emf.ecore.EObject)
     * @since 4.3
     */
    public boolean canBeTransformationSource(EObject source, EObject target) {
        ArgCheck.isInstanceOf(Operation.class, source);
        return false;
    }
}
