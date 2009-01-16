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

package com.metamatrix.modeler.modelgenerator.wsdl.model.internal;

import com.metamatrix.modeler.modelgenerator.wsdl.model.Message;
import com.metamatrix.modeler.modelgenerator.wsdl.model.Part;
import com.metamatrix.modeler.modelgenerator.wsdl.model.WSDLElement;

public class PartImpl extends WSDLElementImpl implements Part {

	
	private String m_elementName;
	private String m_elementNamespace;
	private String m_typeName;
	private String m_typeNamespace;
	private Message m_message;
	
	
	public PartImpl(Message message) {
		m_message = message;
	}
		
	public String getElementName() {
		return m_elementName;
	}

	public void setElementName(String name) {
		m_elementName = name;
	}

	public String getElementNamespace() {
		return m_elementNamespace;
	}

	public void setElementNamespace(String namespace) {
		m_elementNamespace = namespace;
	}

	public String getTypeName() {
		return m_typeName;
	}

	public void setTypeName(String name) {
		m_typeName = name;
	}

	public String getTypeNamespace() {
		return m_typeNamespace;
	}

	public void setTypeNamespace(String namespace) {
		m_typeNamespace = namespace;
	}
	
	public Message getMessage() {
		return m_message;
	}
	
	public boolean isType() {
		return m_typeName != null;
	}
	
	public boolean isElement() {
		return m_elementName != null;
	}

	public WSDLElement copy() {
		PartImpl newImpl = new PartImpl(getMessage());
		newImpl.setName(getName());
		newImpl.setId(getId());
		newImpl.setElementName(getElementName());
		newImpl.setElementNamespace(getElementNamespace());
		newImpl.setTypeName(getTypeName());
		newImpl.setTypeNamespace(getTypeNamespace());
		return newImpl;
	}
	
	@Override
    public String toString() {
		StringBuffer buff = new StringBuffer();
		buff.append("<part name='"); //$NON-NLS-1$
		buff.append(getName());
		buff.append("' id='"); //$NON-NLS-1$
		buff.append(getId());
		buff.append("'> <element> <name>"); //$NON-NLS-1$
		buff.append(getElementName());
		buff.append("</name> <namespace>"); //$NON-NLS-1$
		buff.append(getElementNamespace());
		buff.append("</namespace> </element> <type> <name>"); //$NON-NLS-1$
		buff.append(getTypeName());
		buff.append("</name> <namespace>"); //$NON-NLS-1$
		buff.append(getTypeNamespace());
		buff.append("</namespace> </type> </part>"); //$NON-NLS-1$
		return buff.toString();		
	}

}
