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

package com.metamatrix.metamodels.xml;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Xml Base Element</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link com.metamatrix.metamodels.xml.XmlBaseElement#getParent <em>Parent</em>}</li>
 * </ul>
 * </p>
 *
 * @see com.metamatrix.metamodels.xml.XmlDocumentPackage#getXmlBaseElement()
 * @model abstract="true"
 * @generated
 */
public interface XmlBaseElement extends XmlDocumentNode, ChoiceOption{
    /**
     * Returns the value of the '<em><b>Parent</b></em>' container reference.
     * It is bidirectional and its opposite is '{@link com.metamatrix.metamodels.xml.XmlElementHolder#getElements <em>Elements</em>}'.
     * <!-- begin-user-doc -->
     * <p>
     * If the meaning of the '<em>Parent</em>' container reference isn't clear,
     * there really should be more of a description here...
     * </p>
     * <!-- end-user-doc -->
     * @return the value of the '<em>Parent</em>' container reference.
     * @see #setParent(XmlElementHolder)
     * @see com.metamatrix.metamodels.xml.XmlDocumentPackage#getXmlBaseElement_Parent()
     * @see com.metamatrix.metamodels.xml.XmlElementHolder#getElements
     * @model opposite="elements"
     * @generated
     */
    XmlElementHolder getParent();

    /**
     * Sets the value of the '{@link com.metamatrix.metamodels.xml.XmlBaseElement#getParent <em>Parent</em>}' container reference.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @param value the new value of the '<em>Parent</em>' container reference.
     * @see #getParent()
     * @generated
     */
    void setParent(XmlElementHolder value);

} // XmlBaseElement
