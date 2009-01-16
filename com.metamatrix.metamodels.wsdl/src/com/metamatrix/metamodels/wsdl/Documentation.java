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

package com.metamatrix.metamodels.wsdl;

import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Documentation</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link com.metamatrix.metamodels.wsdl.Documentation#getTextContent <em>Text Content</em>}</li>
 *   <li>{@link com.metamatrix.metamodels.wsdl.Documentation#getContents <em>Contents</em>}</li>
 *   <li>{@link com.metamatrix.metamodels.wsdl.Documentation#getDocumented <em>Documented</em>}</li>
 * </ul>
 * </p>
 *
 * @see com.metamatrix.metamodels.wsdl.WsdlPackage#getDocumentation()
 * @model
 * @generated
 */
public interface Documentation extends ElementOwner{
    /**
     * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
     * @generated
     */
	String copyright = "Copyright � 2000-2005 MetaMatrix, Inc.  All rights reserved."; //$NON-NLS-1$

    /**
     * Returns the value of the '<em><b>Text Content</b></em>' attribute.
     * <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Text Content</em>' attribute isn't clear,
	 * there really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
     * @return the value of the '<em>Text Content</em>' attribute.
     * @see #setTextContent(String)
     * @see com.metamatrix.metamodels.wsdl.WsdlPackage#getDocumentation_TextContent()
     * @model
     * @generated
     */
	String getTextContent();

    /**
     * Sets the value of the '{@link com.metamatrix.metamodels.wsdl.Documentation#getTextContent <em>Text Content</em>}' attribute.
     * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
     * @param value the new value of the '<em>Text Content</em>' attribute.
     * @see #getTextContent()
     * @generated
     */
	void setTextContent(String value);

    /**
     * Returns the value of the '<em><b>Contents</b></em>' containment reference list.
     * The list contents are of type {@link org.eclipse.emf.ecore.EObject}.
     * <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Contents</em>' containment reference list isn't clear,
	 * there really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
     * @return the value of the '<em>Contents</em>' containment reference list.
     * @see com.metamatrix.metamodels.wsdl.WsdlPackage#getDocumentation_Contents()
     * @model type="org.eclipse.emf.ecore.EObject" containment="true"
     * @generated
     */
	EList getContents();

    /**
     * Returns the value of the '<em><b>Documented</b></em>' container reference.
     * It is bidirectional and its opposite is '{@link com.metamatrix.metamodels.wsdl.Documented#getDocumentation <em>Documentation</em>}'.
     * <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Documented</em>' container reference isn't clear,
	 * there really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
     * @return the value of the '<em>Documented</em>' container reference.
     * @see #setDocumented(Documented)
     * @see com.metamatrix.metamodels.wsdl.WsdlPackage#getDocumentation_Documented()
     * @see com.metamatrix.metamodels.wsdl.Documented#getDocumentation
     * @model opposite="documentation"
     * @generated
     */
	Documented getDocumented();

    /**
     * Sets the value of the '{@link com.metamatrix.metamodels.wsdl.Documentation#getDocumented <em>Documented</em>}' container reference.
     * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
     * @param value the new value of the '<em>Documented</em>' container reference.
     * @see #getDocumented()
     * @generated
     */
	void setDocumented(Documented value);

} // Documentation
