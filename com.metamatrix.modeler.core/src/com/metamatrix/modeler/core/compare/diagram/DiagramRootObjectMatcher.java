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

package com.metamatrix.modeler.core.compare.diagram;

import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.mapping.Mapping;
import org.eclipse.emf.mapping.MappingFactory;

import com.metamatrix.metamodels.diagram.DiagramContainer;
import com.metamatrix.modeler.core.compare.AbstractEObjectMatcher;


/** 
 * @since 4.2
 */
public class DiagramRootObjectMatcher extends AbstractEObjectMatcher {

    /** 
     * 
     * @since 4.2
     */
    public DiagramRootObjectMatcher() {
        super();
    }

    /** 
     * @see com.metamatrix.modeler.core.compare.EObjectMatcher#addMappingsForRoots(java.util.List, java.util.List, org.eclipse.emf.mapping.Mapping, org.eclipse.emf.mapping.MappingFactory)
     * @since 4.2
     */
    public void addMappingsForRoots(final List inputs,
                                    final List outputs,
                                    final Mapping mapping,
                                    final MappingFactory factory) {
        DiagramContainer diagramContainer = null;

        // Loop over the inputs and find any of the above objects ...
        final Iterator iter = inputs.iterator();
        while (iter.hasNext()) {
            final Object obj = iter.next();
            if ( obj instanceof DiagramContainer ) {
                diagramContainer = (DiagramContainer)obj;
            }
        }
        
        // Loop over the outputs and find matches for any of the above objects ...
        final Iterator outputIter = outputs.iterator();
        while (outputIter.hasNext()) {
            final Object obj = outputIter.next();
            if ( obj instanceof DiagramContainer ) {
                if ( diagramContainer != null ) {
                    outputIter.remove();
                    inputs.remove(diagramContainer);
                    addMapping(diagramContainer,(EObject)obj,mapping,factory);
                }
            }
        }        
    }

    /** 
     * @see com.metamatrix.modeler.core.compare.EObjectMatcher#addMappings(org.eclipse.emf.ecore.EReference, java.util.List, java.util.List, org.eclipse.emf.mapping.Mapping, org.eclipse.emf.mapping.MappingFactory)
     * @since 4.2
     */
    public void addMappings(final EReference reference,
                            final List inputs,
                            final List outputs,
                            final Mapping mapping,
                            final MappingFactory factory) {
    }

}
