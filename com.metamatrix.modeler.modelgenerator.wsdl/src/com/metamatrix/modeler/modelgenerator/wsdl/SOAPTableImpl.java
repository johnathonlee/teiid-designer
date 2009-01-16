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

package com.metamatrix.modeler.modelgenerator.wsdl;

import org.jdom.Namespace;

import com.metamatrix.modeler.schema.tools.model.jdbc.Column;
import com.metamatrix.modeler.schema.tools.model.jdbc.Table;
import com.metamatrix.modeler.schema.tools.model.jdbc.internal.TableImpl;
import com.metamatrix.modeler.schema.tools.model.schema.SchemaModel;
import com.metamatrix.modeler.schema.tools.model.schema.SchemaObject;


/**
 * 
 * This class decorates the Table class with additional SOAP properties.
 *
 */
class SOAPTableImpl implements SOAPTable {
	private Table table;
	boolean m_isRequestTable = false;
	String m_soapAction;
	SoapBindingInfo m_bindingInfo;
	
	SOAPTableImpl(Table table, boolean isRequest, String soapAction, SoapBindingInfo info) {
		this.table = table;
		m_isRequestTable = isRequest;
		m_soapAction = soapAction;
		m_bindingInfo = info;
	}
	
	public SOAPTableImpl() {
		table = new TableImpl();
	}

	public SOAPTableImpl(Table table) {
		this.table = table;
	}

	public Table getTable() {
		return table;
	}
	
	public boolean isRequest() {
		return m_isRequestTable;
	}
	
	public String getSoapAction() {
		return m_soapAction;
	}
	
	public SoapBindingInfo getSoapBindingInfo() {
		return m_bindingInfo;
	}
	
	public String getName() {
		return table.getName();
	}

	public int getMaxOccurs() {
		return table.getMaxOccurs();
	}

	public String getNamespaceDeclaration() {
		return table.getNamespaceDeclaration();
	}

	public Table[] getParentTables() {
		return table.getParentTables();
	}

	public int getRelationToParent() {
		return table.getRelationToParent();
	}

	public String getSchema() {
		return table.getSchema();
	}

	public void setSchema(String schema) {
		table.setSchema(schema);
	}

	public String getInputXPath() {
		return table.getInputXPath();
	}

	public String getOutputXPath() {
		return table.getOutputXPath();
	}

	public void setInputXPath(String xpathIn) {
		table.setInputXPath(xpathIn);
	}

	public void setName(String name) {
		table.setName(name);
	}

	public void setOutputXPath(String xpath) {
		table.setOutputXPath(xpath);
	}

	public Table[] getChildTables() {
		return table.getChildTables();
	}

	public void addColumn(Column column) {
		table.addColumn(column);
	}

	public void addNamespace(Namespace ns) {
		table.addNamespace(ns);
	}

	public String getCatalog() {
		return table.getCatalog();
	}

	public Column[] getColumns() {
		return table.getColumns();
	}

	public void setCatalog(String catalog) {
		table.setCatalog(catalog);
	}

	public void setSchemaModel(SchemaModel schemaModel) {
		table.setSchemaModel(schemaModel);
	}

	public SchemaObject getElement() {
		return table.getElement();
	}

	public void setBase(boolean b) {
		table.setBase(b);
	}

	public void setElement(SchemaObject element) {
		table.setElement(element);
	}
}
