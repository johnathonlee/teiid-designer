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

package com.metamatrix.modeler.core.util;

import java.util.Set;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * A {@link ModelVisitor} implementation that records the number of instances of each {@link EClass} and the number of
 * {@link Resource} instances.
 * <p>
 * This class is not thread safe.
 * </p>
 * <p>
 * For usage information, see {@link ModelVisitor}
 * </p>
 */
public class ModelStatisticsVisitor implements ModelVisitor {

    private final ModelStatistics stats;

    /**
     * Construct an instance of ModelStatisticsVisitor.
     */
    public ModelStatisticsVisitor() {
        super();
        this.stats = new ModelStatistics();
    }

    public ModelStatistics getModelStatistics() {
        return stats;
    }

    /**
     * Return the {@link EClass} objects for which instances were found by this visitor.
     * 
     * @return the set of metaclasses; never null
     */
    public Set getEClassesFound() {
        return this.stats.getEClasses();
    }

    /**
     * Return the number of instances of the supplied {@link EClass metaclass} that were found by this visitor.
     * 
     * @param metaclass the {@link EClass}
     * @return the number of instances of the metaclass found by this visitor.
     */
    public int getCount( final EClass metaclass ) {
        return this.stats.getCount(metaclass);
    }

    /**
     * Return the number of resources that were found by this visitor.
     * 
     * @return the number of resources found by this visitor.
     */
    public int getResourceCount() {
        return this.stats.getResourceCount();
    }

    /**
     * Clear any statistics gathered by this visitor.
     */
    public void clear() {
        this.stats.clear();
    }

    /**
     * @see com.metamatrix.modeler.core.util.ModelVisitor#visit(org.eclipse.emf.ecore.EObject)
     */
    public boolean visit( EObject object ) {
        if (object == null) {
            return false;
        }
        final EClass metaclass = object.eClass();
        this.stats.add(metaclass, 1);
        return true;
    }

    /**
     * @see com.metamatrix.modeler.core.util.ModelVisitor#visit(org.eclipse.emf.ecore.resource.Resource)
     */
    public boolean visit( Resource resource ) {
        if (resource == null) {
            return false;
        }
        this.stats.addResourceCount(1);
        return true;
    }

}
