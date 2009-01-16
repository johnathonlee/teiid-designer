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

package com.metamatrix.metamodels.wsdl.soap;

import com.metamatrix.metamodels.wsdl.BindingOperation;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Operation</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link com.metamatrix.metamodels.wsdl.soap.SoapOperation#getBindingOperation <em>Binding Operation</em>}</li>
 *   <li>{@link com.metamatrix.metamodels.wsdl.soap.SoapOperation#getStyle <em>Style</em>}</li>
 *   <li>{@link com.metamatrix.metamodels.wsdl.soap.SoapOperation#getAction <em>Action</em>}</li>
 * </ul>
 * </p>
 *
 * @see com.metamatrix.metamodels.wsdl.soap.SoapPackage#getSoapOperation()
 * @model
 * @generated
 */
public interface SoapOperation extends EObject{
    /**
     * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
     * @generated
     */
	String copyright = "Copyright � 2000-2005 MetaMatrix, Inc.  All rights reserved."; //$NON-NLS-1$

    /**
     * Returns the value of the '<em><b>Style</b></em>' attribute.
     * The default value is <code>"DOCUMENT"</code>.
     * The literals are from the enumeration {@link com.metamatrix.metamodels.wsdl.soap.SoapStyleType}.
     * <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Style</em>' attribute isn't clear,
	 * there really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
     * @return the value of the '<em>Style</em>' attribute.
     * @see com.metamatrix.metamodels.wsdl.soap.SoapStyleType
     * @see #setStyle(SoapStyleType)
     * @see com.metamatrix.metamodels.wsdl.soap.SoapPackage#getSoapOperation_Style()
     * @model default="DOCUMENT"
     * @generated
     */
	SoapStyleType getStyle();

    /**
     * Sets the value of the '{@link com.metamatrix.metamodels.wsdl.soap.SoapOperation#getStyle <em>Style</em>}' attribute.
     * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
     * @param value the new value of the '<em>Style</em>' attribute.
     * @see com.metamatrix.metamodels.wsdl.soap.SoapStyleType
     * @see #getStyle()
     * @generated
     */
	void setStyle(SoapStyleType value);

    /**
     * Returns the value of the '<em><b>Action</b></em>' attribute.
     * <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Action</em>' attribute isn't clear,
	 * there really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
     * @return the value of the '<em>Action</em>' attribute.
     * @see #setAction(String)
     * @see com.metamatrix.metamodels.wsdl.soap.SoapPackage#getSoapOperation_Action()
     * @model
     * @generated
     */
	String getAction();

    /**
     * Sets the value of the '{@link com.metamatrix.metamodels.wsdl.soap.SoapOperation#getAction <em>Action</em>}' attribute.
     * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
     * @param value the new value of the '<em>Action</em>' attribute.
     * @see #getAction()
     * @generated
     */
	void setAction(String value);

    /**
     * Returns the value of the '<em><b>Binding Operation</b></em>' container reference.
     * It is bidirectional and its opposite is '{@link com.metamatrix.metamodels.wsdl.BindingOperation#getSoapOperation <em>Soap Operation</em>}'.
     * <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Binding Operation</em>' container reference isn't clear,
	 * there really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
     * @return the value of the '<em>Binding Operation</em>' container reference.
     * @see #setBindingOperation(BindingOperation)
     * @see com.metamatrix.metamodels.wsdl.soap.SoapPackage#getSoapOperation_BindingOperation()
     * @see com.metamatrix.metamodels.wsdl.BindingOperation#getSoapOperation
     * @model opposite="soapOperation" required="true"
     * @generated
     */
	BindingOperation getBindingOperation();

    /**
     * Sets the value of the '{@link com.metamatrix.metamodels.wsdl.soap.SoapOperation#getBindingOperation <em>Binding Operation</em>}' container reference.
     * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
     * @param value the new value of the '<em>Binding Operation</em>' container reference.
     * @see #getBindingOperation()
     * @generated
     */
	void setBindingOperation(BindingOperation value);

} // SoapOperation
