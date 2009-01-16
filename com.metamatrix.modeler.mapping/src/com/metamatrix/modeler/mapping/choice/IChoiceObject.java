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

package com.metamatrix.modeler.mapping.choice;

import java.util.List;

import org.eclipse.emf.ecore.EObject;

/**
 * IChoiceObject
 */
public interface IChoiceObject {


    List getOrderedOptions();

    void setOrderedOptions( List lst );

    String getName( Object option );

    /**
     * @return The UUID form of the criteria.
     */
    String getCriteria( Object option );
    
    /**
     * Sets the criteria to the specified string, which is in a UUID form.
     */
    void setCriteria( Object option, String criteria );

    /**
     * @return The standard SQL form of the criteria.
     */
    String getSqlCriteria( Object option );
    
    /**
     * Sets the criteria to the specified string, which is in a standard SQL form.
     */
    void setSqlCriteria( Object option, String criteria );
    
    boolean isIncluded( Object option );
    
    void setIncluded( Object option, boolean b );
            
    void move( int iNewPosition, Object option );
        
    void move( int iNewPosition, int iOldPosition );

    int getMinOccurs();

    String getDefaultErrorMode();

    void setDefaultErrorMode( String sValue );

    String[] getValidErrorModeValues();

    Object getDefaultOption();

    void setDefaultOption( Object option );    

    EObject getRoot();

    EObject getChoice();

    EObject getParent();


}
