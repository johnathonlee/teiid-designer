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

package com.metamatrix.modeler.transformation.aspects.validation;

import org.eclipse.emf.ecore.EObject;

import com.metamatrix.core.util.ArgCheck;
import com.metamatrix.metamodels.transformation.SqlTransformationMappingRoot;
import com.metamatrix.modeler.core.metamodel.aspect.AspectManager;
import com.metamatrix.modeler.core.metamodel.aspect.MetamodelEntity;
import com.metamatrix.modeler.core.metamodel.aspect.ValidationAspect;
import com.metamatrix.modeler.core.metamodel.aspect.sql.SqlTransformationAspect;
import com.metamatrix.modeler.core.validation.ValidationContext;
import com.metamatrix.modeler.core.validation.ValidationRuleSet;

/**
 * SqlTransformationMappingRootAspect
 */
public class SqlTransformationMappingRootAspect extends TransformationAspect {

    public SqlTransformationMappingRootAspect(MetamodelEntity entity) {
        super(entity);
    }

	/**
	 * Get validation rules for SqlTransformationMappingRoot
	 */
	@Override
    public ValidationRuleSet getValidationRules() {
		addRule(MAPPINGROOT_RULE);
		return super.getValidationRules();
	}

	/* (non-Javadoc)
	 * @see com.metamatrix.modeler.core.metamodel.aspect.ValidationAspect#updateContext(com.metamatrix.modeler.core.validation.ValidationContext)
	 */
	@Override
    public void updateContext(final EObject eObject, final ValidationContext context) {
		SqlTransformationAspect transformationAspect = (SqlTransformationAspect) AspectManager.getSqlAspect(eObject);
		EObject transformedObject = (EObject) transformationAspect.getTransformedObject(eObject);
		// update the map		
		context.addTargetTransform(transformedObject, eObject);
	}

    /** 
     * @see com.metamatrix.modeler.core.metamodel.aspect.ValidationAspect#shouldValidate(org.eclipse.emf.ecore.EObject)
     * @since 4.2
     */
    @Override
    public boolean shouldValidate(final EObject eObject, final ValidationContext context) {
        ArgCheck.isInstanceOf(SqlTransformationMappingRoot.class, eObject);
        
        if(!context.shouldIgnore(eObject)) {
            SqlTransformationMappingRoot mappingRoot = (SqlTransformationMappingRoot) eObject;
            EObject targetObj = mappingRoot.getTarget();
            if(targetObj != null) {
	            ValidationAspect validAspect = AspectManager.getValidationAspect(targetObj);
	            if(validAspect != null) {
	                boolean shouldValidate = validAspect.shouldValidate(targetObj, context);
	                if(!shouldValidate) {
	    	            context.addObjectToIgnore(eObject, true);
	                }
	                return shouldValidate;
	            }
	        }
	        return true;
        }
        return false;        
    }
}
