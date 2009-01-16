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

package com.metamatrix.metamodels.relational.compare;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;

import com.metamatrix.metamodels.relational.RelationalPackage;
import com.metamatrix.modeler.core.compare.EObjectMatcherFactory;

/**
 * UuidMatcherFactory
 */
public class RelationalMatcherFactory implements EObjectMatcherFactory {

    private final List standardMatchers;

    /**
     * Construct an instance of UuidMatcherFactory.
     * 
     */
    public RelationalMatcherFactory() {
        super();
        this.standardMatchers = new LinkedList();
        this.standardMatchers.add( new RelationalEntityNameToNameMatcher() );
        this.standardMatchers.add( new RelationalEntityNameInSourceToNameInSourceMatcher() );
        this.standardMatchers.add( new RelationalEntityNameToNameInSourceMatcher() );
        this.standardMatchers.add( new RelationalEntityNameInSourceToNameMatcher() );
        this.standardMatchers.add( new RelationalEntityNameToNameIgnoreCaseMatcher() );
        this.standardMatchers.add( new RelationalEntityNameInSourceToNameInSourceIgnoreCaseMatcher() );
        this.standardMatchers.add( new RelationalEntityNameToNameInSourceIgnoreCaseMatcher() );
        this.standardMatchers.add( new RelationalEntityNameInSourceToNameIgnoreCaseMatcher() );
    }

    /**
     * @see com.metamatrix.modeler.core.compare.EObjectMatcherFactory#createEObjectMatchersForRoots()
     */
    public List createEObjectMatchersForRoots() {
        // Relational objects can be roots, so return the matchers 
        return this.standardMatchers;
    }

    /**
     * @see com.metamatrix.modeler.core.compare.EObjectMatcherFactory#createEObjectMatchers(org.eclipse.emf.ecore.EReference)
     */
    public List createEObjectMatchers(final EReference reference) {
        // Make sure the reference is in the Relational metamodel ...
        final EClass containingClass = reference.getEContainingClass();
        final EPackage metamodel = containingClass.getEPackage();
        if ( !RelationalPackage.eINSTANCE.equals(metamodel) ) {
            // The feature isn't in the relational metamodel so return nothing ...
            return Collections.EMPTY_LIST;
        }
        
        return this.standardMatchers;
    }

}
