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

package com.metamatrix.modeler.ddl;

import com.metamatrix.core.xslt.Style;


/**
 * The options for writing out the DDL for a {@link RelationalPackage relational} model.
 */
public interface DdlOptions {

    public static boolean DEFAULT_GENERATE_SCHEMA       = false;
    public static boolean DEFAULT_GENERATE_DROPS        = false;
    public static boolean DEFAULT_GENERATE_COMMENTS     = true;
    public static boolean DEFAULT_USE_NAME_IN_SOURCE    = true;
    public static boolean DEFAULT_USE_NATIVE_TYPE       = false;
    public static boolean DEFAULT_ENFORCE_UNIQUE_NAMES  = true;

    /**
     * Get the style of DDL that is to be written out.
     * @return the style, or null if no style has been specified
     */
    public Style getStyle();
    
    /**
     * Get the style of DDL that is to be written out.
     * @param style the style, or null if the current style is to be cleared
     */
    public void setStyle( Style style );

    /**
     * Return whether the DDL should contain create statements for the schema(s).
     * @return true if schema creation statements are to be included in the DDL, or
     * false otherwise.
     */
    public boolean isGenerateSchema();

    /**
     * Set whether the DDL should contain create statements for the schema(s).
     * @param includeSchemas true if schema creation statements are to be included in the DDL, or
     * false otherwise.
     */
    public void setGenerateSchema(boolean generateSchemas);
    
    /**
     * Return whether comments are to be put into the DDL
     * @return true if comments are to be generated in the DDL, or false otherwise
     */
    public boolean isGenerateComments();

    /**
     * Return whether drop statements are to be put into the DDL
     * @return true if drop statements are to be generated in the DDL, or false otherwise
     */
    public boolean isGenerateDropStatements();

    /**
     * Set whether comments are to be put into the DDL
     * @param generateDrops true if comments are to be generated in the DDL, or false otherwise
     */
    public void setGenerateComments(boolean generateComments);

    /**
     * Set whether drop statements are to be put into the DDL
     * @param generateDrops true if drop statements are to be generated in the DDL, or false otherwise
     */
    public void setGenerateDropStatements(boolean generateDrops);

    /**
     * Determine whether names in source should be used in the DDL, or whether only names should be used.
     * This option does not apply when there are no names in source.
     * @return true if entities' name in source rather than their name should be used for the object
     * names in the DDL, or false if only their names should be used.
     */
    public boolean isNameInSourceUsed();

    /**
     * Set whether names in source should be used in the DDL, or whether only names should be used.
     * This option does not apply when there are no names in source.
     * @param useNameInSource true if entities' name in source rather than their name should be used for the object
     * names in the DDL, or false if only their names should be used.
     */
    public void setNameInSourceUsed(boolean useNameInSource);

    /**
     * Determine whether native types are used in the DDL, or whether only the column's datatypes are
     * used to generate the column types in the DDL.
     * This option does not apply when there are no native types in the model(s).
     * @return true if entities' native types rather than their datatype should be used for the
     * column types in the DDL, or false otherwise.
     */
    public boolean isNativeTypeUsed();

    /**
     * Set whether native types should be used in the DDL, or whether only the column's datatypes should 
     * be used to generate the column types in the DDL.
     * This option does not apply when there are no native types in the model(s).
     * @param useNativeType true if entities' native types rather than their datatype should be used for the
     * column types in the DDL, or false otherwise.
     */
    public void setNativeTypeUsed(boolean useNativeType);

    /**
     * Determine whether primary keys, foreign keys, unique keys, and indexes should be renamed
     * if their names are not unique
     * @return true if uniqueness among key, constraint, and index names should be enforced
     */
    public boolean isUniqueNamesEnforced();

    /**
     * Set whether primary keys, foreign keys, unique keys, and indexes should be renamed
     * if their names are not unique.
     * @param useUniqueNames true if keys, constraints, and indexes should have unique names,
     * or false otherwise.
     */
    public void setUniqueNamesEnforced(boolean useUniqueNames);
}
