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
package com.metamatrix.metamodels.relationship.provider;

import org.eclipse.emf.common.EMFPlugin;
import org.eclipse.emf.common.util.ResourceLocator;
import org.eclipse.emf.ecore.provider.EcoreEditPlugin;
import com.metamatrix.metamodels.relationship.RelationshipMetamodelPlugin;

/**
 * This is the central singleton for the Relationship edit plugin. <!-- begin-user-doc --> <!-- end-user-doc -->
 * 
 * @generated
 */
public final class RelationshipEditPlugin extends EMFPlugin {
    /**
     * <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    public static final String copyright = "Copyright (c) 2000-2005 MetaMatrix Corporation.  All rights reserved."; //$NON-NLS-1$

    /**
     * Keep track of the singleton. <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    public static final RelationshipEditPlugin INSTANCE = new RelationshipEditPlugin();

    /**
     * Keep track of the singleton. <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    static Implementation plugin;

    /**
     * Create the instance. <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    public RelationshipEditPlugin() {
        super(new ResourceLocator[] {EcoreEditPlugin.INSTANCE,});
    }

    /**
     * Returns the singleton instance of the Eclipse plugin. <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @return the singleton instance.
     * @generated NOT
     */
    @Override
    public ResourceLocator getPluginResourceLocator() {
        return RelationshipMetamodelPlugin.getPluginResourceLocator();
    }

    /**
     * Returns the singleton instance of the Eclipse plugin. <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @return the singleton instance.
     * @generated
     */
    public ResourceLocator getPluginResourceLocatorGen() {
        return plugin;
    }

    /**
     * Returns the singleton instance of the Eclipse plugin. <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @return the singleton instance.
     * @generated
     */
    public static Implementation getPlugin() {
        return plugin;
    }

    /**
     * The actual implementation of the Eclipse <b>Plugin</b>. <!-- begin-user-doc --> <!-- end-user-doc -->
     * 
     * @generated
     */
    public static class Implementation extends EclipsePlugin {
        /**
         * Creates an instance. <!-- begin-user-doc --> <!-- end-user-doc -->
         * 
         * @generated
         */
        public Implementation() {

            // Remember the static instance.
            //
            plugin = this;
        }
    }
}
