/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package com.metamatrix.query.validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.MetaMatrixException;
import com.metamatrix.api.exception.query.QueryMetadataException;
import com.metamatrix.api.exception.query.QueryResolverException;
import com.metamatrix.common.types.DataTypeManager;
import com.metamatrix.core.MetaMatrixRuntimeException;
import com.metamatrix.dqp.message.ParameterInfo;
import com.metamatrix.query.analysis.AnalysisRecord;
import com.metamatrix.query.mapping.relational.QueryNode;
import com.metamatrix.query.mapping.xml.MappingDocument;
import com.metamatrix.query.mapping.xml.MappingElement;
import com.metamatrix.query.metadata.QueryMetadataInterface;
import com.metamatrix.query.metadata.StoredProcedureInfo;
import com.metamatrix.query.metadata.TempMetadataAdapter;
import com.metamatrix.query.metadata.TempMetadataStore;
import com.metamatrix.query.parser.QueryParser;
import com.metamatrix.query.resolver.QueryResolver;
import com.metamatrix.query.sql.LanguageObject;
import com.metamatrix.query.sql.lang.Command;
import com.metamatrix.query.sql.lang.SPParameter;
import com.metamatrix.query.sql.symbol.ElementSymbol;
import com.metamatrix.query.sql.symbol.GroupSymbol;
import com.metamatrix.query.sql.visitor.SQLStringVisitor;
import com.metamatrix.query.unittest.FakeMetadataFacade;
import com.metamatrix.query.unittest.FakeMetadataFactory;
import com.metamatrix.query.unittest.FakeMetadataObject;
import com.metamatrix.query.unittest.FakeMetadataStore;

public class TestValidator extends TestCase {

	// ################################## FRAMEWORK ################################
	
	public TestValidator(String name) { 
		super(name);
	}	
    
    public static Map getStoredProcedureExternalMetadata(GroupSymbol virtualProc, QueryMetadataInterface metadata)
    throws QueryMetadataException, MetaMatrixComponentException {

        Map externalMetadata = new HashMap();

        StoredProcedureInfo info = metadata.getStoredProcedureInfoForProcedure(virtualProc.getName());
        if(info!=null) {
            virtualProc.setMetadataID(info.getProcedureID());

            // List of ElementSymbols - Map Values
            List paramList = info.getParameters();
            Iterator iter = paramList.iterator();
            // Create Symbol List from parameter list
            List symbolList = new ArrayList();
            while(iter.hasNext()) {
                SPParameter param = (SPParameter) iter.next();
                if(param.getParameterType() == ParameterInfo.IN ||
                    param.getParameterType() == ParameterInfo.INOUT) {
                    // Create Element Symbol
                    ElementSymbol eSymbol = new ElementSymbol(param.getName());
                    eSymbol.setMetadataID(param);
                    eSymbol.setType(param.getClassType());
                    symbolList.add(eSymbol);
                }
            }
            // Create external Metadata Map
            externalMetadata = new HashMap();
            externalMetadata.put(virtualProc, symbolList);
        }

        return externalMetadata;
    }

    public static FakeMetadataFacade exampleMetadata() {
        // Create metadata objects        
        FakeMetadataObject modelObj = FakeMetadataFactory.createPhysicalModel("test"); //$NON-NLS-1$
        FakeMetadataObject vModelObj2 = FakeMetadataFactory.createVirtualModel("vTest");  //$NON-NLS-1$
        FakeMetadataObject groupObj = FakeMetadataFactory.createPhysicalGroup("test.group", modelObj);         //$NON-NLS-1$
        FakeMetadataObject elemObj0 = FakeMetadataFactory.createElement("test.group.e0", groupObj, DataTypeManager.DefaultDataTypes.INTEGER, 0); //$NON-NLS-1$
        elemObj0.putProperty(FakeMetadataObject.Props.NULL, Boolean.FALSE);
        FakeMetadataObject elemObj1 = FakeMetadataFactory.createElement("test.group.e1", groupObj, DataTypeManager.DefaultDataTypes.STRING, 1); //$NON-NLS-1$
        elemObj1.putProperty(FakeMetadataObject.Props.SELECT, Boolean.FALSE);
        FakeMetadataObject elemObj2 = FakeMetadataFactory.createElement("test.group.e2", groupObj, DataTypeManager.DefaultDataTypes.STRING, 2); //$NON-NLS-1$
        elemObj2.putProperty(FakeMetadataObject.Props.SEARCHABLE_COMPARE, Boolean.FALSE);
        FakeMetadataObject elemObj3 = FakeMetadataFactory.createElement("test.group.e3", groupObj, DataTypeManager.DefaultDataTypes.STRING, 3); //$NON-NLS-1$
        elemObj3.putProperty(FakeMetadataObject.Props.SEARCHABLE_LIKE, Boolean.FALSE);
    
        FakeMetadataObject group2Obj = FakeMetadataFactory.createPhysicalGroup("test.group2", modelObj);         //$NON-NLS-1$
        FakeMetadataObject elemObj2_0 = FakeMetadataFactory.createElement("test.group2.e0", group2Obj, DataTypeManager.DefaultDataTypes.INTEGER, 0); //$NON-NLS-1$
        elemObj2_0.putProperty(FakeMetadataObject.Props.UPDATE, Boolean.FALSE);
        FakeMetadataObject elemObj2_1 = FakeMetadataFactory.createElement("test.group2.e1", group2Obj, DataTypeManager.DefaultDataTypes.STRING, 1); //$NON-NLS-1$
        FakeMetadataObject elemObj2_2 = FakeMetadataFactory.createElement("test.group2.e2", group2Obj, DataTypeManager.DefaultDataTypes.STRING, 2); //$NON-NLS-1$
        elemObj2_2.putProperty(FakeMetadataObject.Props.UPDATE, Boolean.FALSE);
    
        FakeMetadataObject group3Obj = FakeMetadataFactory.createPhysicalGroup("test.group3", modelObj);         //$NON-NLS-1$
        group3Obj.putProperty(FakeMetadataObject.Props.UPDATE, Boolean.FALSE); 
        FakeMetadataObject elemObj3_0 = FakeMetadataFactory.createElement("test.group3.e0", group3Obj, DataTypeManager.DefaultDataTypes.INTEGER, 0); //$NON-NLS-1$
        FakeMetadataObject elemObj3_1 = FakeMetadataFactory.createElement("test.group3.e1", group3Obj, DataTypeManager.DefaultDataTypes.STRING, 1); //$NON-NLS-1$
        FakeMetadataObject elemObj3_2 = FakeMetadataFactory.createElement("test.group3.e2", group3Obj, DataTypeManager.DefaultDataTypes.STRING, 2); //$NON-NLS-1$
    
        // Create virtual group & elements.
        QueryNode vNode = new QueryNode("vTest.vGroup", "SELECT * FROM test.group WHERE e2 = 'x'"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vGroup = FakeMetadataFactory.createVirtualGroup("vTest.vGroup", vModelObj2, vNode);         //$NON-NLS-1$
        List vGroupE = FakeMetadataFactory.createElements(vGroup, 
            new String[] { "e0", "e1", "e2", "e3" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });

        QueryNode vNode2 = new QueryNode("vTest.vMap", "SELECT * FROM test.group"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vGroup2 = FakeMetadataFactory.createVirtualGroup("vTest.vMap", vModelObj2, vNode2);         //$NON-NLS-1$
        List vGroupE2 = FakeMetadataFactory.createElements(vGroup2, 
            new String[] { "e0", "e1", "e2", "e3" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
        ((FakeMetadataObject)vGroupE2.get(0)).putProperty(FakeMetadataObject.Props.NULL, Boolean.FALSE);
        ((FakeMetadataObject)vGroupE2.get(1)).putProperty(FakeMetadataObject.Props.SELECT, Boolean.FALSE);
        ((FakeMetadataObject)vGroupE2.get(2)).putProperty(FakeMetadataObject.Props.SEARCHABLE_COMPARE, Boolean.FALSE);
        ((FakeMetadataObject)vGroupE2.get(3)).putProperty(FakeMetadataObject.Props.SEARCHABLE_LIKE, Boolean.FALSE);
    
        // Create virtual documents
        MappingDocument doc = new MappingDocument(false);
        MappingElement complexRoot = doc.addChildElement(new MappingElement("a0")); //$NON-NLS-1$
        
        MappingElement sourceNode = complexRoot.addChildElement(new MappingElement("a1")); //$NON-NLS-1$
        sourceNode.setSource("test.group"); //$NON-NLS-1$
        sourceNode.addChildElement(new MappingElement("a2", "test.group.e1")); //$NON-NLS-1$ //$NON-NLS-2$
        sourceNode.addChildElement(new MappingElement("b2", "test.group.e2")); //$NON-NLS-1$ //$NON-NLS-2$
        sourceNode.addChildElement(new MappingElement("c2", "test.group.e3")); //$NON-NLS-1$ //$NON-NLS-2$
        
    	FakeMetadataObject docModel = FakeMetadataFactory.createVirtualModel("vm1"); //$NON-NLS-1$
        FakeMetadataObject doc1 = FakeMetadataFactory.createVirtualGroup("vm1.doc1", docModel, doc); //$NON-NLS-1$
    	List docE1 = FakeMetadataFactory.createElements(doc1, new String[] { "a0", "a0.a1", "a0.a1.a2", "a0.a1.b2", "a0.a1.c2" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    		new String[] {DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
                        
    	// set up validator metadata
        FakeMetadataStore store = new FakeMetadataStore();
        store.addObject(modelObj);
        store.addObject(groupObj);
        store.addObject(elemObj0);
        store.addObject(elemObj1);
        store.addObject(elemObj2);
        store.addObject(elemObj3);
        store.addObject(vGroup);
        store.addObjects(vGroupE);
        store.addObject(vGroup2);
        store.addObjects(vGroupE2);
        store.addObject(docModel);
        store.addObject(group2Obj);
        store.addObject(elemObj2_0);
        store.addObject(elemObj2_1);
        store.addObject(elemObj2_2);
        store.addObject(group3Obj);
        store.addObject(elemObj3_0);
        store.addObject(elemObj3_1);
        store.addObject(elemObj3_2);
        store.addObject(doc1);
        store.addObjects(docE1);
    	return new FakeMetadataFacade(store);
    }
	
    public FakeMetadataFacade exampleMetadata1() {
        // Create metadata objects        
        FakeMetadataObject modelObj = FakeMetadataFactory.createPhysicalModel("test"); //$NON-NLS-1$
        FakeMetadataObject groupObj = FakeMetadataFactory.createPhysicalGroup("test.group", modelObj);         //$NON-NLS-1$

        FakeMetadataObject elemObj0 = FakeMetadataFactory.createElement("test.group.e0", groupObj, DataTypeManager.DefaultDataTypes.INTEGER, 0); //$NON-NLS-1$
        FakeMetadataObject elemObj1 = FakeMetadataFactory.createElement("test.group.e1", groupObj, DataTypeManager.DefaultDataTypes.STRING, 1); //$NON-NLS-1$
        FakeMetadataObject elemObj2 = FakeMetadataFactory.createElement("test.group.e2", groupObj, DataTypeManager.DefaultDataTypes.STRING, 2); //$NON-NLS-1$
        FakeMetadataObject elemObj3 = FakeMetadataFactory.createElement("test.group.e3", groupObj, DataTypeManager.DefaultDataTypes.STRING, 3);         //$NON-NLS-1$

        elemObj0.putProperty(FakeMetadataObject.Props.NULL, Boolean.FALSE);
        elemObj0.putProperty(FakeMetadataObject.Props.DEFAULT_VALUE, Boolean.FALSE);

        elemObj1.putProperty(FakeMetadataObject.Props.NULL, Boolean.TRUE);
        elemObj1.putProperty(FakeMetadataObject.Props.DEFAULT_VALUE, Boolean.TRUE);
        
        elemObj2.putProperty(FakeMetadataObject.Props.NULL, Boolean.TRUE);
        elemObj2.putProperty(FakeMetadataObject.Props.DEFAULT_VALUE, Boolean.FALSE);
        
		// set up validator metadata
        FakeMetadataStore store = new FakeMetadataStore();
        store.addObject(modelObj);
        store.addObject(groupObj);
        store.addObject(elemObj0);
        store.addObject(elemObj1);
        store.addObject(elemObj2);
        store.addObject(elemObj3);
		return new FakeMetadataFacade(store);
    }

    /**
     * Group has element with type object
     * @return FakeMetadataFacade
     */
    public static FakeMetadataFacade exampleMetadata2() {
        // Create metadata objects
        FakeMetadataObject modelObj = FakeMetadataFactory.createPhysicalModel("test"); //$NON-NLS-1$
        FakeMetadataObject groupObj = FakeMetadataFactory.createPhysicalGroup("test.group", modelObj); //$NON-NLS-1$
        
        List elements = FakeMetadataFactory.createElements(groupObj, new String[] {
            "e0", "e1", "e2", "e3", "e4", "e5" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        }, new String[] {
            DataTypeManager.DefaultDataTypes.INTEGER,
            DataTypeManager.DefaultDataTypes.STRING,
            DataTypeManager.DefaultDataTypes.OBJECT,
            DataTypeManager.DefaultDataTypes.BLOB,
            DataTypeManager.DefaultDataTypes.CLOB,
            DataTypeManager.DefaultDataTypes.XML,
        });

        // set up validator metadata
        FakeMetadataStore store = new FakeMetadataStore();
        store.addObject(modelObj);
        store.addObject(groupObj);
        store.addObjects(elements);
        return new FakeMetadataFacade(store);
    }

    public static FakeMetadataFacade exampleMetadata3() {
        // Create metadata objects        
        FakeMetadataObject modelObj = FakeMetadataFactory.createPhysicalModel("test"); //$NON-NLS-1$
        FakeMetadataObject groupObj = FakeMetadataFactory.createPhysicalGroup("test.group", modelObj);         //$NON-NLS-1$

        FakeMetadataObject elemObj0 = FakeMetadataFactory.createElement("test.group.e0", groupObj, DataTypeManager.DefaultDataTypes.INTEGER, 0); //$NON-NLS-1$
        FakeMetadataObject elemObj1 = FakeMetadataFactory.createElement("test.group.e1", groupObj, DataTypeManager.DefaultDataTypes.STRING, 1); //$NON-NLS-1$

        elemObj1.putProperty(FakeMetadataObject.Props.NULL, Boolean.FALSE);
        elemObj1.putProperty(FakeMetadataObject.Props.DEFAULT_VALUE, Boolean.FALSE);
        elemObj1.putProperty(FakeMetadataObject.Props.AUTO_INCREMENT, Boolean.TRUE);
        elemObj1.putProperty(FakeMetadataObject.Props.NAME_IN_SOURCE, "e1:SEQUENCE=MYSEQUENCE.nextVal"); //$NON-NLS-1$
        
        // set up validator metadata
        FakeMetadataStore store = new FakeMetadataStore();
        store.addObject(modelObj);
        store.addObject(groupObj);
        store.addObject(elemObj0);
        store.addObject(elemObj1);
        return new FakeMetadataFacade(store);
    }

    public static FakeMetadataFacade exampleMetadata4() {
        // Create metadata objects 
    	FakeMetadataObject modelObj = FakeMetadataFactory.createPhysicalModel("test");  //$NON-NLS-1$
        FakeMetadataObject groupObj = FakeMetadataFactory.createPhysicalGroup("test.group", modelObj); //$NON-NLS-1$
        FakeMetadataObject elemObj0 = FakeMetadataFactory.createElement("test.group.e0", groupObj, DataTypeManager.DefaultDataTypes.INTEGER, 0); //$NON-NLS-1$
        FakeMetadataObject elemObj1 = FakeMetadataFactory.createElement("test.group.e1", groupObj, DataTypeManager.DefaultDataTypes.STRING, 1); //$NON-NLS-1$
        FakeMetadataObject elemObj2 = FakeMetadataFactory.createElement("test.group.e2", groupObj, DataTypeManager.DefaultDataTypes.STRING, 2); //$NON-NLS-1$
        FakeMetadataObject vModelObj = FakeMetadataFactory.createVirtualModel("vTest");  //$NON-NLS-1$
        QueryNode vNode = new QueryNode("vTest.vGroup", "SELECT * FROM test.group"); //$NON-NLS-1$ //$NON-NLS-2$ 
        FakeMetadataObject vGroupObj = FakeMetadataFactory.createVirtualGroup("vTest.vGroup", vModelObj, vNode); //$NON-NLS-1$
        FakeMetadataObject vElemObj0 = FakeMetadataFactory.createElement("vTest.vGroup.e0", vGroupObj, DataTypeManager.DefaultDataTypes.INTEGER, 0); //$NON-NLS-1$
        FakeMetadataObject vElemObj1 = FakeMetadataFactory.createElement("vTest.vGroup.e1", vGroupObj, DataTypeManager.DefaultDataTypes.STRING, 1); //$NON-NLS-1$
        FakeMetadataObject vElemObj2 = FakeMetadataFactory.createElement("vTest.vGroup.e2", vGroupObj, DataTypeManager.DefaultDataTypes.STRING, 2); //$NON-NLS-1$
        List elements = new ArrayList(1);
        elements.add(vElemObj0); 
        elements.add(vElemObj1);
        FakeMetadataObject vGroupAp1 = FakeMetadataFactory.createAccessPattern("vTest.vGroup.ap1", vGroupObj, elements); //e1 //$NON-NLS-1$
        
        QueryNode vNode2 = new QueryNode("vTest.vGroup", "SELECT * FROM vTest.vGroup"); //$NON-NLS-1$ //$NON-NLS-2$ 
        FakeMetadataObject vGroupObj2 = FakeMetadataFactory.createVirtualGroup("vTest.vGroup2", vModelObj, vNode2); //$NON-NLS-1$
        FakeMetadataObject vElemObj20 = FakeMetadataFactory.createElement("vTest.vGroup2.e0", vGroupObj2, DataTypeManager.DefaultDataTypes.INTEGER, 0); //$NON-NLS-1$
        FakeMetadataObject vElemObj21 = FakeMetadataFactory.createElement("vTest.vGroup2.e1", vGroupObj2, DataTypeManager.DefaultDataTypes.STRING, 1); //$NON-NLS-1$
        FakeMetadataObject vElemObj22 = FakeMetadataFactory.createElement("vTest.vGroup2.e2", vGroupObj2, DataTypeManager.DefaultDataTypes.STRING, 2); //$NON-NLS-1$
        elements = new ArrayList(1);
        elements.add(vElemObj20); 
        elements.add(vElemObj21);
        FakeMetadataObject vGroup2Ap1 = FakeMetadataFactory.createAccessPattern("vTest.vGroup2.ap1", vGroupObj2, elements); //e1 //$NON-NLS-1$
        
        // set up validator metadata
        FakeMetadataStore store = new FakeMetadataStore();
        store.addObject(modelObj);
        store.addObject(groupObj);
        store.addObject(elemObj0);
        store.addObject(elemObj1);
        store.addObject(elemObj2);
        store.addObject(vModelObj);
        store.addObject(vGroupObj);
        store.addObject(vElemObj0);
        store.addObject(vElemObj1);
        store.addObject(vElemObj2);
        store.addObject(vGroupAp1);
        store.addObject(vGroupObj2);
        store.addObject(vElemObj20);
        store.addObject(vElemObj21);
        store.addObject(vElemObj22);
        store.addObject(vGroup2Ap1);
        return new FakeMetadataFacade(store);
    }
    
	// ################################## TEST HELPERS ################################

    static Command helpResolve(String sql, QueryMetadataInterface metadata) { 
        return helpResolve(sql, metadata, Collections.EMPTY_MAP);
    }
    
	public static Command helpResolve(String sql, QueryMetadataInterface metadata, Map externalMetadata) { 
		Command command = null;
		
		// parse
		try { 
			command = QueryParser.getQueryParser().parseCommand(sql);
		} catch(Exception e) { 
            throw new MetaMatrixRuntimeException(e);
		}	
		
		// resolve
		try { 
			QueryResolver.resolveCommand(command, externalMetadata, true, metadata, AnalysisRecord.createNonRecordingRecord());
		} catch(Exception e) {
            throw new MetaMatrixRuntimeException(e);
		} 

		return command;
	}

	static ValidatorReport helpValidate(String sql, String[] expectedStringArray, QueryMetadataInterface metadata) {
        Command command = helpResolve(sql, metadata);

        return helpRunValidator(command, expectedStringArray, metadata);
    }

    public static ValidatorReport helpRunValidator(Command command, String[] expectedStringArray, QueryMetadataInterface metadata) {
        try {
            ValidatorReport report = Validator.validate(command, metadata);
            
            ValidatorReport report3 = Validator.validate(command, metadata, new ValueValidationVisitor(), true);

            // Get invalid objects from report
            Collection actualObjs = new ArrayList();
            report.collectInvalidObjects(actualObjs);
            report3.collectInvalidObjects(actualObjs);

            // Compare expected and actual objects
            Set expectedStrings = new HashSet(Arrays.asList(expectedStringArray));
            Set actualStrings = new HashSet();
            Iterator objIter = actualObjs.iterator();
            while(objIter.hasNext()) {
                LanguageObject obj = (LanguageObject) objIter.next();
                actualStrings.add(SQLStringVisitor.getSQLString(obj));
            }

            if(expectedStrings.size() == 0 && actualStrings.size() > 0) {
                fail("Expected no failures but got some: " + report.getFailureMessage() + ", "  + report3.getFailureMessage()); //$NON-NLS-1$ //$NON-NLS-2$ 
            } else if(actualStrings.size() == 0 && expectedStrings.size() > 0) {
                fail("Expected some failures but got none for sql = " + command); //$NON-NLS-1$
            } else {
                assertEquals("Expected and actual sets of strings are not the same: ", expectedStrings, actualStrings); //$NON-NLS-1$
            }
            return report;
        } catch(MetaMatrixException e) {
			throw new MetaMatrixRuntimeException(e);
        }
	}

	private void helpValidateProcedure(String procedure, String userUpdateStr, String procedureType) {

        QueryMetadataInterface metadata = FakeMetadataFactory.exampleUpdateProc(procedureType, procedure);

        try {
        	
	        Command command = helpResolve(userUpdateStr, metadata);
        	//System.out.println(command.printCommandTree());
            ValidatorReport report = Validator.validate(command, metadata); 
            //System.out.println("\nReport = \n" + report);
        
            // Get invalid objects from report
            Collection actualObjs = new ArrayList();
            report.collectInvalidObjects(actualObjs);
            if(actualObjs.size() > 0) {
                fail("Expected no failures but got some: " + report.getFailureMessage());            	 //$NON-NLS-1$
            }
        } catch(MetaMatrixException e) {
            throw new MetaMatrixRuntimeException(e);
        }
	}
	
	private void helpFailProcedure(String procedure, String userUpdateStr, String procedureType) {

        QueryMetadataInterface metadata = FakeMetadataFactory.exampleUpdateProc(procedureType, procedure);

        try {
        	
	        Command command = helpResolve(userUpdateStr, metadata);
        	
            ValidatorReport report = Validator.validate(command, metadata); 
            //System.out.println("\nReport = \n" + report);
        
            // Get invalid objects from report
            Collection actualObjs = new ArrayList();
            report.collectInvalidObjects(actualObjs);
            assertTrue("Expected some failures but got none for procedure = " + procedure, !actualObjs.isEmpty()); //$NON-NLS-1$ 
        } catch(MetaMatrixException e) {
			throw new RuntimeException(e);
        }
	}	
    
	// ################################## ACTUAL TESTS ################################
	
	
    public void testSelectStarWhereNoElementsAreNotSelectable() {
        helpValidate("SELECT * FROM pm1.g5", new String[] {"SELECT * FROM pm1.g5"}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
    }

	public void testValidateSelect1() {        
        helpValidate("SELECT e1, e2 FROM test.group", new String[] {"e1"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void testValidateSelect2() {        
        helpValidate("SELECT e2 FROM test.group", new String[] {}, exampleMetadata()); //$NON-NLS-1$
	}
 
	public void testValidateCompare1() {        
        helpValidate("SELECT e2 FROM vTest.vMap WHERE e2 = 'a'", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
	}

    public void testValidateCompare4() {        
        helpValidate("SELECT e3 FROM vTest.vMap WHERE e3 LIKE 'a'", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }

    public void testValidateCompare6() {        
        helpValidate("SELECT e0 FROM vTest.vMap WHERE e0 BETWEEN 1000 AND 2000", new String[] {}, exampleMetadata()); //$NON-NLS-1$
    }

	public void testValidateCompareInHaving2() {        
        helpValidate("SELECT e2 FROM vTest.vMap GROUP BY e2 HAVING e2 IS NULL", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
	}

	public void testValidateCompareInHaving3() {        
        helpValidate("SELECT e2 FROM vTest.vMap GROUP BY e2 HAVING e2 IN ('a')", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
	}

    public void testValidateCompareInHaving4() {        
        helpValidate("SELECT e3 FROM vTest.vMap GROUP BY e3 HAVING e3 LIKE 'a'", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }

    public void testValidateCompareInHaving5() {        
        helpValidate("SELECT e2 FROM vTest.vMap GROUP BY e2 HAVING e2 BETWEEN 1000 AND 2000", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }

	public void testInvalidAggregate1() {        
        helpValidate("SELECT SUM(e3) FROM test.group GROUP BY e2", new String[] {"SUM(e3)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void testInvalidAggregate2() {        
        helpValidate("SELECT e3 FROM test.group GROUP BY e2", new String[] {"e3"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void testInvalidAggregate3() {        
        helpValidate("SELECT SUM(e2) FROM test.group GROUP BY e2", new String[] {"SUM(e2)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void testInvalidAggregate4() {        
        helpValidate("SELECT AVG(e2) FROM test.group GROUP BY e2", new String[] {"AVG(e2)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
	}
    
    public void testInvalidAggregate5() {
        helpValidate("SELECT e1 || 'x' frOM pm1.g1 GROUP BY e2 + 1", new String[] {"e1"}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testInvalidAggregate6() {
        helpValidate("SELECT e2 + 1 frOM pm1.g1 GROUP BY e2 + 1 HAVING e1 || 'x' > 0", new String[] {"e1"}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    public void testInvalidAggregate7() {
        helpValidate("SELECT StringKey, SUM(length(StringKey || 'x')) + 1 AS x FROM BQT1.SmallA GROUP BY StringKey || 'x' HAVING space(MAX(length((StringKey || 'x') || 'y'))) = '   '", //$NON-NLS-1$
                     new String[] {"StringKey"}, FakeMetadataFactory.exampleBQTCached() ); //$NON-NLS-1$
    }
    
    public void testInvalidAggregateIssue190644() {
        helpValidate("SELECT e3 + 1 from pm1.g1 GROUP BY e2 + 1 HAVING e2 + 1 = 5", new String[] {"e3"}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testValidAggregate1() {
        helpValidate("SELECT (e2 + 1) * 2 frOM pm1.g1 GROUP BY e2 + 1", new String[] {}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ 
    }

    public void testValidAggregate2() {
        helpValidate("SELECT e2 + 1 frOM pm1.g1 GROUP BY e2 + 1", new String[] {}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ 
    }

    public void testValidAggregate3() {
        helpValidate("SELECT sum (IntKey), case when IntKey>=5000 then '5000 +' else '0-999' end " + //$NON-NLS-1$
            "FROM BQT1.SmallA GROUP BY case when IntKey>=5000 then '5000 +' else '0-999' end", //$NON-NLS-1$
            new String[] {}, FakeMetadataFactory.exampleBQTCached());
    }
	public void testInvalidHaving1() {        
        helpValidate("SELECT e3 FROM test.group HAVING e3 > 0", new String[] {"e3"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void testInvalidHaving2() {        
        helpValidate("SELECT e3 FROM test.group HAVING concat(e3,'a') > 0", new String[] {"e3"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
	}
 
	public void testNestedAggregateInHaving() {        
        helpValidate("SELECT e0 FROM test.group GROUP BY e0 HAVING SUM(COUNT(e0)) > 0", new String[] {"COUNT(e0)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
	}

    public void testNestedAggregateInSelect() {        
        helpValidate("SELECT SUM(COUNT(e0)) FROM test.group GROUP BY e0", new String[] {"COUNT(e0)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    public void testValidateCaseInGroupBy() {        
        helpValidate("SELECT SUM(e2) FROM pm1.g1 GROUP BY CASE e2 WHEN 0 THEN 1 ELSE 2 END", new String[] {}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ 
    }
    
    public void testValidateFunctionInGroupBy() {        
        helpValidate("SELECT SUM(e2) FROM pm1.g1 GROUP BY (e2 + 1)", new String[] {}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ 
    }

    public void testInvalidScalarSubqueryInGroupBy() {        
        helpValidate("SELECT COUNT(*) FROM pm1.g1 GROUP BY (SELECT 1)", new String[] { "(SELECT 1)" }, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testInvalidConstantInGroupBy() {        
        helpValidate("SELECT COUNT(*) FROM pm1.g1 GROUP BY 1", new String[] { "1" }, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testInvalidReferenceInGroupBy() {        
        helpValidate("SELECT COUNT(*) FROM pm1.g1 GROUP BY ?", new String[] { "?" }, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testValidateObjectType1() {
        helpValidate("SELECT DISTINCT * FROM test.group", new String[] {"test.\"group\".e2", "test.\"group\".e3", "test.\"group\".e4", "test.\"group\".e5"}, exampleMetadata2()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }

    public void testValidateObjectType2() {
        helpValidate("SELECT * FROM test.group ORDER BY e1, e2", new String[] {"e2"}, exampleMetadata2()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testValidateObjectType3() {
        helpValidate("SELECT e2 AS x FROM test.group ORDER BY x", new String[] {"x"}, exampleMetadata2()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    public void testValidateNonComparableType() {
        helpValidate("SELECT e3 FROM test.group ORDER BY e3", new String[] {"e3"}, exampleMetadata2()); //$NON-NLS-1$ //$NON-NLS-2$
    }
 
    public void testValidateNonComparableType1() {
        helpValidate("SELECT e3 FROM test.group union SELECT e3 FROM test.group", new String[] {"e3"}, exampleMetadata2()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testValidateNonComparableType2() {
        helpValidate("SELECT e3 FROM test.group GROUP BY e3", new String[] {"e3"}, exampleMetadata2()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    public void testValidateNonComparableType3() {
        helpValidate("SELECT e3 FROM test.group intersect SELECT e3 FROM test.group", new String[] {"e3"}, exampleMetadata2()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testValidateNonComparableType4() {
        helpValidate("SELECT e3 FROM test.group except SELECT e3 FROM test.group", new String[] {"e3"}, exampleMetadata2()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    public void testValidateIntersectAll() {
        helpValidate("SELECT e3 FROM pm1.g1 intersect all SELECT e3 FROM pm1.g1", new String[] {"SELECT e3 FROM pm1.g1 INTERSECT ALL SELECT e3 FROM pm1.g1"}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    public void testInsert1() {
        helpValidate("INSERT INTO test.group (e0) VALUES (null)", new String[] {"e0"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    public void testInsert2() throws Exception {
        QueryMetadataInterface metadata = exampleMetadata();

        Command command = QueryParser.getQueryParser().parseCommand("INSERT INTO test.group (e0) VALUES (p1)"); //$NON-NLS-1$

        // Create external metadata
        GroupSymbol sqGroup = new GroupSymbol("pm1.sq5"); //$NON-NLS-1$
        ArrayList sqParams = new ArrayList();
        ElementSymbol in = new ElementSymbol("pm1.sq5.p1"); //$NON-NLS-1$
        in.setType(DataTypeManager.DefaultDataClasses.INTEGER);
        sqParams.add(in);
        Map externalMetadata = new HashMap();
        externalMetadata.put(sqGroup, sqParams);

        QueryResolver.resolveCommand(command, externalMetadata, true, metadata, AnalysisRecord.createNonRecordingRecord());

        helpRunValidator(command, new String[] {}, metadata);
    }

    public void testInsert3() throws Exception {
        QueryMetadataInterface metadata = exampleMetadata();

        Command command = QueryParser.getQueryParser().parseCommand("INSERT INTO test.group (e0) VALUES (p1+2)"); //$NON-NLS-1$

        // Create external metadata
        GroupSymbol sqGroup = new GroupSymbol("pm1.sq5"); //$NON-NLS-1$
        ArrayList sqParams = new ArrayList();
        ElementSymbol in = new ElementSymbol("pm1.sq5.p1"); //$NON-NLS-1$
        in.setType(DataTypeManager.DefaultDataClasses.INTEGER);
        sqParams.add(in);
        Map externalMetadata = new HashMap();
        externalMetadata.put(sqGroup, sqParams);

        QueryResolver.resolveCommand(command, externalMetadata, true, metadata, AnalysisRecord.createNonRecordingRecord());

        helpRunValidator(command, new String[] {}, metadata);
    }
    
	// non-null, no-default elements not left
    public void testInsert4() throws Exception {
        QueryMetadataInterface metadata = exampleMetadata1();

        Command command = QueryParser.getQueryParser().parseCommand("INSERT INTO test.group (e0) VALUES (2)"); //$NON-NLS-1$

        QueryResolver.resolveCommand(command, metadata);

        helpRunValidator(command, new String[] {}, metadata);
    }
    
	// non-null, no-default elements left
    public void testInsert5() throws Exception {
        QueryMetadataInterface metadata = exampleMetadata1();

        Command command = QueryParser.getQueryParser().parseCommand("INSERT INTO test.group (e1, e2) VALUES ('x', 'y')"); //$NON-NLS-1$
        QueryResolver.resolveCommand(command, metadata);

        helpRunValidator(command, new String[] {"test.\"group\".e0"}, metadata); //$NON-NLS-1$
    }    

    public void testValidateInsertElements1() throws Exception {
        QueryMetadataInterface metadata = exampleMetadata();

        Command command = QueryParser.getQueryParser().parseCommand("INSERT INTO test.group2 (e0, e1, e2) VALUES (5, 'x', 'y')"); //$NON-NLS-1$
        QueryResolver.resolveCommand(command, metadata);

        helpRunValidator(command, new String[] {"e2", "e0"}, metadata); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testValidateInsertElements2() throws Exception {
        QueryMetadataInterface metadata = exampleMetadata();

        Command command = QueryParser.getQueryParser().parseCommand("INSERT INTO test.group2 (e1) VALUES ('y')"); //$NON-NLS-1$

        QueryResolver.resolveCommand(command, metadata);

        helpRunValidator(command, new String[] {}, metadata);
    }

    public void testValidateInsertElements3_autoIncNotRequired() throws Exception {
    	helpValidate("INSERT INTO test.group (e0) VALUES (1)", new String[] {}, exampleMetadata3()); //$NON-NLS-1$
    }

    public void testUpdate1() {
        helpValidate("UPDATE test.group SET e0=null", new String[] {"e0"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    
        
    public void testUpdate2() {
        helpValidate("UPDATE test.group SET e0=1, e0=2", new String[] {"e0"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    
    
    public void testUpdate3() throws Exception {
        QueryMetadataInterface metadata = exampleMetadata();
        
        Command command = QueryParser.getQueryParser().parseCommand("UPDATE test.group SET p1=1"); //$NON-NLS-1$

        // Create external metadata
        GroupSymbol sqGroup = new GroupSymbol("pm1.sq5"); //$NON-NLS-1$
        ArrayList sqParams = new ArrayList();
        ElementSymbol in = new ElementSymbol("pm1.sq5.p1"); //$NON-NLS-1$
        in.setType(DataTypeManager.DefaultDataClasses.STRING);
        sqParams.add(in);
        Map externalMetadata = new HashMap();
        externalMetadata.put(sqGroup, sqParams);
        
        QueryResolver.resolveCommand(command, externalMetadata, true, metadata, AnalysisRecord.createNonRecordingRecord());
                
        helpRunValidator(command, new String[] {"p1"}, metadata); //$NON-NLS-1$
    }

    public void testUpdate4() throws Exception {
        QueryMetadataInterface metadata = exampleMetadata();

        Command command = QueryParser.getQueryParser().parseCommand("UPDATE test.group SET e0=p1"); //$NON-NLS-1$
        
        // Create external metadata
        GroupSymbol sqGroup = new GroupSymbol("pm1.sq5"); //$NON-NLS-1$
        ArrayList sqParams = new ArrayList();
        ElementSymbol in = new ElementSymbol("pm1.sq5.p1"); //$NON-NLS-1$
        in.setType(DataTypeManager.DefaultDataClasses.INTEGER);
        sqParams.add(in);
        Map externalMetadata = new HashMap();
        externalMetadata.put(sqGroup, sqParams);

        QueryResolver.resolveCommand(command, externalMetadata, true, metadata, AnalysisRecord.createNonRecordingRecord());

        helpRunValidator(command, new String[] {}, metadata);
    }

    public void testUpdate5() throws Exception {
        QueryMetadataInterface metadata = exampleMetadata();

        Command command = QueryParser.getQueryParser().parseCommand("UPDATE test.group SET e0=p1+2"); //$NON-NLS-1$

        // Create external metadata
        GroupSymbol sqGroup = new GroupSymbol("pm1.sq5"); //$NON-NLS-1$
        ArrayList sqParams = new ArrayList();
        ElementSymbol in = new ElementSymbol("pm1.sq5.p1"); //$NON-NLS-1$
        in.setType(DataTypeManager.DefaultDataClasses.INTEGER);
        sqParams.add(in);
        Map externalMetadata = new HashMap();
        externalMetadata.put(sqGroup, sqParams);

        QueryResolver.resolveCommand(command, externalMetadata, true, metadata, AnalysisRecord.createNonRecordingRecord());
        helpRunValidator(command, new String[] {}, metadata);
    }

    public void testValidateUpdateElements1() throws Exception {
        QueryMetadataInterface metadata = exampleMetadata();

        Command command = QueryParser.getQueryParser().parseCommand("UPDATE test.group2 SET e0 = 5, e1 = 'x', e2 = 'y'"); //$NON-NLS-1$
        QueryResolver.resolveCommand(command, metadata);

        helpRunValidator(command, new String[] {"e2", "e0"}, metadata); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testValidateUpdateElements2() throws Exception {
        QueryMetadataInterface metadata = exampleMetadata();

        Command command = QueryParser.getQueryParser().parseCommand("UPDATE test.group2 SET e1 = 'x'"); //$NON-NLS-1$
        QueryResolver.resolveCommand(command, metadata);

        helpRunValidator(command, new String[] {}, metadata);
    }
    public void testXMLQuery1() {
    	helpValidate("SELECT * FROM vm1.doc1", new String[] {}, exampleMetadata()); //$NON-NLS-1$
    }

    public void testXMLQuery2() {
    	helpValidate("SELECT * FROM vm1.doc1 where a2='x'", new String[] {}, exampleMetadata()); //$NON-NLS-1$
    }

    public void testXMLQuery3() {
    	helpValidate("SELECT * FROM vm1.doc1 order by a2", new String[] {}, exampleMetadata()); //$NON-NLS-1$
    }

    public void testXMLQuery6() {
    	helpValidate("SELECT * FROM vm1.doc1 UNION SELECT * FROM vm1.doc1", new String[] {"\"xml\"", "SELECT * FROM vm1.doc1 UNION SELECT * FROM vm1.doc1"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
    
    public void testXMLQueryWithLimit() {
    	helpValidate("SELECT * FROM vm1.doc1 limit 1", new String[] {"SELECT * FROM vm1.doc1 LIMIT 1"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** test rowlimit function is valid */
    public void testXMLQueryRowLimit() {
        helpValidate("SELECT * FROM vm1.doc1 where 2 = RowLimit(a2)", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }
    
    /** rowlimit function operand must be nonnegative integer */
    public void testXMLQueryRowLimit1() {
        helpValidate("SELECT * FROM vm1.doc1 where RowLimit(a2)=-1", new String[] {"RowLimit(a2) = -1"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** rowlimit function operand must be nonnegative integer */
    public void testXMLQueryRowLimit2() {
        helpValidate("SELECT * FROM vm1.doc1 where RowLimit(a2)='x'", new String[] {"RowLimit(a2) = 'x'"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    /** rowlimit function cannot be nested within another function (this test inserts an implicit type conversion) */
    public void testXMLQueryRowLimitNested() {
        helpValidate("SELECT * FROM vm1.doc1 where RowLimit(a2)=a2", new String[] {"RowLimit(a2) = a2"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** rowlimit function cannot be nested within another function */
    public void testXMLQueryRowLimitNested2() {
        helpValidate("SELECT * FROM vm1.doc1 where convert(RowLimit(a2), string)=a2", new String[] {"convert(RowLimit(a2), string) = a2"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    /** rowlimit function operand must be nonnegative integer */
    public void testXMLQueryRowLimit3a() {
        helpValidate("SELECT * FROM vm1.doc1 where RowLimit(a2) = convert(a2, integer)", new String[] {"RowLimit(a2) = convert(a2, integer)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    /** rowlimit function operand must be nonnegative integer */
    public void testXMLQueryRowLimit3b() {
        helpValidate("SELECT * FROM vm1.doc1 where convert(a2, integer) = RowLimit(a2)", new String[] {"convert(a2, integer) = RowLimit(a2)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    
    
    /** rowlimit function arg must be an element symbol */
    public void testXMLQueryRowLimit4() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit('x') = 3", new String[] {"rowlimit('x')"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** rowlimit function arg must be an element symbol */
    public void testXMLQueryRowLimit5() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit(concat(a2, 'x')) = 3", new String[] {"rowlimit(concat(a2, 'x'))"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    /** rowlimit function arg must be a single conjunct */
    public void testXMLQueryRowLimitConjunct() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit(a2) = 3 OR a2 = 'x'", new String[] {"(rowlimit(a2) = 3) OR (a2 = 'x')"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** rowlimit function arg must be a single conjunct */
    public void testXMLQueryRowLimitCompound() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit(a2) = 3 AND a2 = 'x'", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }

    /** rowlimit function arg must be a single conjunct */
    public void testXMLQueryRowLimitCompound2() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit(a2) = 3 AND concat(a2, 'y') = 'xy'", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }

    /** rowlimit function arg must be a single conjunct */
    public void testXMLQueryRowLimitCompound3() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit(a2) = 3 AND (concat(a2, 'y') = 'xy' OR concat(a2, 'y') = 'zy')", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }    

    /** each rowlimit function arg must be a single conjunct */
    public void testXMLQueryRowLimitCompound4() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit(a2) = 3 AND rowlimit(c2) = 4", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }    

    /** 
     * It doesn't make sense to use rowlimit twice on same element, but can't be
     * invalidated here (could be two different elements but in the same 
     * mapping class - needs to be caught in XMLPlanner)
     */
    public void testXMLQueryRowLimitCompound5() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit(a2) = 3 AND rowlimit(a2) = 4", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }    

    public void testXMLQueryRowLimitInvalidCriteria() {
        helpValidate("SELECT * FROM vm1.doc1 where not(rowlimit(a2) = 3)", new String[] {"NOT (rowlimit(a2) = 3)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    public void testXMLQueryRowLimitInvalidCriteria2() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit(a2) IN (3)", new String[] {"rowlimit(a2) IN (3)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    
    
    public void testXMLQueryRowLimitInvalidCriteria3() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit(a2) LIKE 'x'", new String[] {"rowlimit(a2) LIKE 'x'"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    public void testXMLQueryRowLimitInvalidCriteria4() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit(a2) IS NULL", new String[] {"rowlimit(a2) IS NULL"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    public void testXMLQueryRowLimitInvalidCriteria5() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit(a2) IN (SELECT e0 FROM vTest.vMap)", new String[] {"rowlimit(a2) IN (SELECT e0 FROM vTest.vMap)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    public void testXMLQueryRowLimitInvalidCriteria6() {
        helpValidate("SELECT * FROM vm1.doc1 where 2 = CASE WHEN rowlimit(a2) = 2 THEN 2 END", new String[] {"2 = CASE WHEN rowlimit(a2) = 2 THEN 2 END"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    public void testXMLQueryRowLimitInvalidCriteria6a() {
        helpValidate("SELECT * FROM vm1.doc1 where 2 = CASE rowlimit(a2) WHEN 2 THEN 2 END", new String[] {"2 = CASE rowlimit(a2) WHEN 2 THEN 2 END"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    
    
    public void testXMLQueryRowLimitInvalidCriteria7() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit(a2) BETWEEN 2 AND 3", new String[] {"rowlimit(a2) BETWEEN 2 AND 3"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    public void testXMLQueryRowLimitInvalidCriteria8() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimit(a2) = ANY (SELECT e0 FROM vTest.vMap)", new String[] {"rowlimit(a2) = ANY (SELECT e0 FROM vTest.vMap)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    
    
    /** using rowlimit pseudo-function in non-XML query is invalid */
    public void testNonXMLQueryRowLimit() {        
        helpValidate("SELECT e2 FROM vTest.vMap WHERE rowlimit(e1) = 2", new String[] {"rowlimit(e1)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    /** test rowlimitexception function is valid */
    public void testXMLQueryRowLimitException() {
        helpValidate("SELECT * FROM vm1.doc1 where 2 = RowLimitException(a2)", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }
    
    /** rowlimitexception function operand must be nonnegative integer */
    public void testXMLQueryRowLimitException1() {
        helpValidate("SELECT * FROM vm1.doc1 where RowLimitException(a2)=-1", new String[] {"RowLimitException(a2) = -1"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** rowlimitexception function operand must be nonnegative integer */
    public void testXMLQueryRowLimitException2() {
        helpValidate("SELECT * FROM vm1.doc1 where RowLimitException(a2)='x'", new String[] {"RowLimitException(a2) = 'x'"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    /** rowlimitexception function cannot be nested within another function (this test inserts an implicit type conversion) */
    public void testXMLQueryRowLimitExceptionNested() {
        helpValidate("SELECT * FROM vm1.doc1 where RowLimitException(a2)=a2", new String[] {"RowLimitException(a2) = a2"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** rowlimitexception function cannot be nested within another function */
    public void testXMLQueryRowLimitExceptionNested2() {
        helpValidate("SELECT * FROM vm1.doc1 where convert(RowLimitException(a2), string)=a2", new String[] {"convert(RowLimitException(a2), string) = a2"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    /** rowlimitexception function operand must be nonnegative integer */
    public void testXMLQueryRowLimitException3a() {
        helpValidate("SELECT * FROM vm1.doc1 where RowLimitException(a2) = convert(a2, integer)", new String[] {"RowLimitException(a2) = convert(a2, integer)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    /** rowlimitexception function operand must be nonnegative integer */
    public void testXMLQueryRowLimitException3b() {
        helpValidate("SELECT * FROM vm1.doc1 where convert(a2, integer) = RowLimitException(a2)", new String[] {"convert(a2, integer) = RowLimitException(a2)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    
    
    /** rowlimitexception function arg must be an element symbol */
    public void testXMLQueryRowLimitException4() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception('x') = 3", new String[] {"rowlimitexception('x')"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** rowlimitexception function arg must be an element symbol */
    public void testXMLQueryRowLimitException5() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception(concat(a2, 'x')) = 3", new String[] {"rowlimitexception(concat(a2, 'x'))"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    /** rowlimitexception function arg must be a single conjunct */
    public void testXMLQueryRowLimitExceptionConjunct() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception(a2) = 3 OR a2 = 'x'", new String[] {"(rowlimitexception(a2) = 3) OR (a2 = 'x')"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** rowlimitexception function arg must be a single conjunct */
    public void testXMLQueryRowLimitExceptionCompound() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception(a2) = 3 AND a2 = 'x'", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }

    /** rowlimitexception function arg must be a single conjunct */
    public void testXMLQueryRowLimitExceptionCompound2() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception(a2) = 3 AND concat(a2, 'y') = 'xy'", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }

    /** rowlimitexception function arg must be a single conjunct */
    public void testXMLQueryRowLimitExceptionCompound3() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception(a2) = 3 AND (concat(a2, 'y') = 'xy' OR concat(a2, 'y') = 'zy')", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }    

    /** each rowlimitexception function arg must be a single conjunct */
    public void testXMLQueryRowLimitExceptionCompound4() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception(a2) = 3 AND rowlimitexception(c2) = 4", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }    

    /** 
     * It doesn't make sense to use rowlimitexception twice on same element, but can't be
     * invalidated here (could be two different elements but in the same 
     * mapping class - needs to be caught in XMLPlanner)
     */
    public void testXMLQueryRowLimitExceptionCompound5() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception(a2) = 3 AND rowlimitexception(a2) = 4", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }    

    public void testXMLQueryRowLimitExceptionInvalidCriteria() {
        helpValidate("SELECT * FROM vm1.doc1 where not(rowlimitexception(a2) = 3)", new String[] {"NOT (rowlimitexception(a2) = 3)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    public void testXMLQueryRowLimitExceptionInvalidCriteria2() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception(a2) IN (3)", new String[] {"rowlimitexception(a2) IN (3)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    
    
    public void testXMLQueryRowLimitExceptionInvalidCriteria3() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception(a2) LIKE 'x'", new String[] {"rowlimitexception(a2) LIKE 'x'"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    public void testXMLQueryRowLimitExceptionInvalidCriteria4() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception(a2) IS NULL", new String[] {"rowlimitexception(a2) IS NULL"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    public void testXMLQueryRowLimitExceptionInvalidCriteria5() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception(a2) IN (SELECT e0 FROM vTest.vMap)", new String[] {"rowlimitexception(a2) IN (SELECT e0 FROM vTest.vMap)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    public void testXMLQueryRowLimitExceptionInvalidCriteria6() {
        helpValidate("SELECT * FROM vm1.doc1 where 2 = CASE WHEN rowlimitexception(a2) = 2 THEN 2 END", new String[] {"2 = CASE WHEN rowlimitexception(a2) = 2 THEN 2 END"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    public void testXMLQueryRowLimitExceptionInvalidCriteria6a() {
        helpValidate("SELECT * FROM vm1.doc1 where 2 = CASE rowlimitexception(a2) WHEN 2 THEN 2 END", new String[] {"2 = CASE rowlimitexception(a2) WHEN 2 THEN 2 END"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    
    
    public void testXMLQueryRowLimitExceptionInvalidCriteria7() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception(a2) BETWEEN 2 AND 3", new String[] {"rowlimitexception(a2) BETWEEN 2 AND 3"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    public void testXMLQueryRowLimitExceptionInvalidCriteria8() {
        helpValidate("SELECT * FROM vm1.doc1 where rowlimitexception(a2) = ANY (SELECT e0 FROM vTest.vMap)", new String[] {"rowlimitexception(a2) = ANY (SELECT e0 FROM vTest.vMap)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    
    
    /** using rowlimit pseudo-function in non-XML query is invalid */
    public void testNonXMLQueryRowLimitException() {        
        helpValidate("SELECT e2 FROM vTest.vMap WHERE rowlimitexception(e1) = 2", new String[] {"rowlimitexception(e1)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }    

    /** using context pseudo-function in non-XML query is invalid */
    public void testNonXMLQueryContextOperator() {        
        helpValidate("SELECT e2 FROM vTest.vMap WHERE context(e1, e1) = 2", new String[] {"context(e1, e1)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }      
    
    public void testValidateSubquery1() {        
        helpValidate("SELECT e2 FROM (SELECT e2 FROM vTest.vMap WHERE e2 = 'a') AS x", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }

    public void testValidateSubquery2() {        
        helpValidate("SELECT e2 FROM (SELECT e3 FROM vTest.vMap) AS x, vTest.vMap WHERE e2 = 'a'", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }
    
    public void testValidateSubquery3() {        
        helpValidate("SELECT * FROM pm1.g1, (EXEC pm1.sq1( )) AS alias", new String[] {}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$
    }

    public void testValidateUnionWithSubquery() {        
        helpValidate("SELECT e2 FROM test.group2 union all SELECT e3 FROM test.group union all select * from (SELECT e1 FROM test.group) as subquery1", new String[] {"e1"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testValidateExistsSubquery() {        
        helpValidate("SELECT e2 FROM test.group2 WHERE EXISTS (SELECT e2 FROM vTest.vMap WHERE e2 = 'a')", new String[] {}, exampleMetadata()); //$NON-NLS-1$ 
    }

    public void testValidateScalarSubquery() {        
        helpValidate("SELECT e2, (SELECT e1 FROM vTest.vMap WHERE e2 = '3') FROM test.group2", new String[] {"e1"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$ 
    }

    public void testValidateAnyCompareSubquery() {        
        helpValidate("SELECT e2 FROM test.group2 WHERE e1 < ANY (SELECT e1 FROM test.group)", new String[] {"e1"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testValidateAllCompareSubquery() {        
        helpValidate("SELECT e2 FROM test.group2 WHERE e1 = ALL (SELECT e1 FROM test.group)", new String[] {"e1"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testValidateSomeCompareSubquery() {        
        helpValidate("SELECT e2 FROM test.group2 WHERE e1 <= SOME (SELECT e1 FROM test.group)", new String[] {"e1"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testValidateCompareSubquery() {        
        helpValidate("SELECT e2 FROM test.group2 WHERE e1 >= (SELECT e1 FROM test.group WHERE e1 = 1)", new String[] {"e1"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testValidateInClauseSubquery() {        
        helpValidate("SELECT e2 FROM test.group2 WHERE e1 IN (SELECT e1 FROM test.group)", new String[] {"e1"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testValidateExec1() {
        helpValidate("EXEC pm1.sq1()", new String[] {}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$
    }
    
	// variable declared is of special type INPUT
    public void testCreateUpdateProcedure1() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer INPUT;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$
        
		helpFailProcedure(procedure, userUpdateStr,
									 FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
	// variable declared is of special type CHANGING
    public void testCreateUpdateProcedure3() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer CHANGING;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$
        
		helpFailProcedure(procedure, userUpdateStr,
									 FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }

	// valid variable declared
    public void testCreateUpdateProcedure4() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$
        
		helpValidateProcedure(procedure, userUpdateStr,
									 FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
	// validating criteria selector(on HAS CRITERIA), elements on it should be virtual group elements
    public void testCreateUpdateProcedure5() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "if(HAS CRITERIA ON (vm1.g1.E1, vm1.g1.e1))\n";                 //$NON-NLS-1$
        procedure = procedure + "BEGIN\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n";         //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$
        
		helpValidateProcedure(procedure, userUpdateStr,
									 FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
	// validating Translate CRITERIA, elements on it should be virtual group elements
    public void testCreateUpdateProcedure7() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Select pm1.g1.e1 from pm1.g1 where Translate CRITERIA WITH (vm1.g1.e1 = 1, vm1.g1.e1 = 2);\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$
        
		helpValidateProcedure(procedure, userUpdateStr,
									 FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
	// ROWS_UPDATED not assigned
    public void testCreateUpdateProcedure8() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Select pm1.g1.e1 from pm1.g1 where Translate CRITERIA WITH (vm1.g1.e1 = 1);\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$
        
		helpFailProcedure(procedure, userUpdateStr,
									 FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }

	// validating AssignmentStatement, ROWS_UPDATED element assigned
    public void testCreateUpdateProcedure9() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED = Select pm1.g1.e1 from pm1.g1;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$
        
		helpFailProcedure(procedure, userUpdateStr,
									 FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
	// validating AssignmentStatement, variable type and assigned type 
	// do not match
    public void testCreateUpdateProcedure10() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "var1 = Select pm1.g1.e1 from pm1.g1;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$
        
		helpFailProcedure(procedure, userUpdateStr,
									 FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }

	// validating AssignmentStatement, more than one project symbol on the
	// command
    public void testCreateUpdateProcedure11() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "var1 = Select pm1.g1.e2, pm1.g1.e1 from pm1.g1;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$
        
		helpFailProcedure(procedure, userUpdateStr,
									 FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
	// validating AssignmentStatement, more than one project symbol on the
	// command
    public void testCreateUpdateProcedure12() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "var1 = Select pm1.g1.e2, pm1.g1.e1 from pm1.g1;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$
        
		helpFailProcedure(procedure, userUpdateStr,
									 FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
	// TranslateCriteria on criteria of the if statement
    public void testCreateUpdateProcedure13() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "if(TRANSLATE CRITERIA ON (vm1.g1.e1) WITH (vm1.g1.e1 = 1))\n"; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "Select pm1.g1.e2 from pm1.g1;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$
        
		helpFailProcedure(procedure, userUpdateStr,
									 FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
	// INPUT ised in command
    public void testCreateUpdateProcedure16() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "INSERT into pm1.g1 (pm1.g1.e1) values (INPUT.e1);\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$
        
		helpValidateProcedure(procedure, userUpdateStr,
									 FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
	// virtual group elements used in procedure in if statement(TRANSLATE CRITERIA)
	// elements on with should be on ON
    public void testCreateUpdateProcedure17() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Select pm1.g1.e2 from pm1.g1, pm1.g2 where TRANSLATE = CRITERIA ON (e1) WITH (e1 = 20, e2 = 30);\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$
        
		helpFailProcedure(procedure, userQuery,
									 FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
	// virtual group elements used in procedure in if statement(TRANSLATE CRITERIA)
	// failure, aggregate function in query transform
    public void testCreateUpdateProcedure18() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Select pm1.g1.e2 from pm1.g1 where TRANSLATE = CRITERIA ON (e3);\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where e3= 1"; //$NON-NLS-1$

		helpFailProcedure(procedure, userQuery, 
				FakeMetadataObject.Props.UPDATE_PROCEDURE);
	}
    
	// virtual group elements used in procedure in if statement(TRANSLATE CRITERIA)
	// failure, aggregate function in query transform
    public void testCreateUpdateProcedure18a() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Select pm1.g1.e2 from pm1.g1 where TRANSLATE = CRITERIA ON (e3);\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y like '%a' and e3= 1"; //$NON-NLS-1$

		helpFailProcedure(procedure, userQuery, 
				FakeMetadataObject.Props.UPDATE_PROCEDURE);
	}

	
	// virtual group elements used in procedure in if statement(TRANSLATE CRITERIA)
	// failure, translated criteria elements not present on groups of command
    public void testCreateUpdateProcedure19() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Select pm1.g2.e2 from pm1.g2 where TRANSLATE = CRITERIA ON (x, y);\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y= 1"; //$NON-NLS-1$

		helpFailProcedure(procedure, userQuery, 
				FakeMetadataObject.Props.UPDATE_PROCEDURE);
	}
	
	// virtual group elements used in procedure in if statement(TRANSLATE CRITERIA)
    public void testCreateUpdateProcedure20() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Select pm1.g1.e2 from pm1.g1 where TRANSLATE = CRITERIA WITH (y = e2+1);\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y= 1"; //$NON-NLS-1$

		helpValidateProcedure(procedure, userQuery, 
				FakeMetadataObject.Props.UPDATE_PROCEDURE);
	}

	// variables cannot be used among insert elements
    public void testCreateUpdateProcedure23() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Update pm1.g1 SET pm1.g1.e2 =1 , var1 = 2;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where e3= 1"; //$NON-NLS-1$

		helpFailProcedure(procedure, userQuery, 
				FakeMetadataObject.Props.UPDATE_PROCEDURE);
	}
	
	// variables cannot be used among insert elements
    public void testCreateUpdateProcedure24() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Update pm1.g1 SET pm1.g1.e2 =1 , INPUT.x = 2;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where e3= 1"; //$NON-NLS-1$

		helpFailProcedure(procedure, userQuery, 
				FakeMetadataObject.Props.UPDATE_PROCEDURE);
	}

	// virtual group elements used in procedure in if statement(TRANSLATE CRITERIA)
    public void testCreateUpdateProcedure25() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Select pm1.g2.e2 from pm1.g2 where TRANSLATE > CRITERIA ON (y);\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y > 1"; //$NON-NLS-1$

		helpFailProcedure(procedure, userQuery, 
				FakeMetadataObject.Props.UPDATE_PROCEDURE);
	}

	// virtual group elements used in procedure in if statement(TRANSLATE CRITERIA)
    public void testCreateUpdateProcedure26() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Select pm1.g1.e2 from pm1.g1 where TRANSLATE = CRITERIA WITH (e3 = e2+1);\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where e3 > 1"; //$NON-NLS-1$

		helpValidateProcedure(procedure, userQuery, 
				FakeMetadataObject.Props.UPDATE_PROCEDURE);
	}

	// virtual group elements used in procedure in if statement(TRANSLATE CRITERIA)
    public void testCreateUpdateProcedure27() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Select pm1.g2.e2 from pm1.g2 where TRANSLATE LIKE CRITERIA WITH (y = e2+1);\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1"; //$NON-NLS-1$

		helpValidateProcedure(procedure, userQuery, 
				FakeMetadataObject.Props.UPDATE_PROCEDURE);
	}
    
    public void testCreateUpdateProcedure28() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Select pm1.g2.e2 from pm1.g2 where TRANSLATE CRITERIA;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1 or y = 2"; //$NON-NLS-1$

		helpFailProcedure(procedure, userQuery, 
				FakeMetadataObject.Props.UPDATE_PROCEDURE);
	}

    // using aggregate function within a procedure - defect #8394
    public void testCreateUpdateProcedure31() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE string MaxTran;\n"; //$NON-NLS-1$
        procedure = procedure + "MaxTran = SELECT MAX(e1) FROM pm1.g1;\n";         //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1"; //$NON-NLS-1$

        helpValidateProcedure(procedure, userQuery,
                FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
	// assigning null values to known datatype variable
	public void testCreateUpdateProcedure32() {
		String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
		procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
		procedure = procedure + "DECLARE string var;\n"; //$NON-NLS-1$
		procedure = procedure + "var = null;\n";         //$NON-NLS-1$
		procedure = procedure + "ROWS_UPDATED =0;\n"; //$NON-NLS-1$
		procedure = procedure + "END\n"; //$NON-NLS-1$

		String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1"; //$NON-NLS-1$

		helpValidateProcedure(procedure, userQuery,
				FakeMetadataObject.Props.UPDATE_PROCEDURE);
	}
    
    public void testDefect13643() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "LOOP ON (SELECT * FROM pm1.g1) AS myCursor\n"; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "var1 = SELECT COUNT(*) FROM myCursor;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n";         //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED = 0;\n";         //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1"; //$NON-NLS-1$

        helpFailProcedure(procedure, userQuery, 
                FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
    public void testValidHaving() {
        helpValidate(
            "SELECT DISTINCT ProdHier.prod_num " + //$NON-NLS-1$
            "FROM Sales, ProdHier, Cust " + //$NON-NLS-1$
            "WHERE ((ProdHier.prod_fam_name <> 'garbage') AND ((Sales.month_no = 1) AND (Sales.year_no = 1996)) AND ((Cust.cust_name <> 'garbage') AND (Cust.cust_num = 1001))) AND (Sales.prod_num = ProdHier.prod_num) AND (Sales.cust_num = Cust.cust_num) " + //$NON-NLS-1$
            "GROUP BY ProdHier.prod_num " + //$NON-NLS-1$
            "HAVING SUM(Sales.sales_amount) > 1",  //$NON-NLS-1$
            new String[] { }, FakeMetadataFactory.exampleSymphony());
    } 
    
    public void testValidHaving2() {
        String sql =  "SELECT intkey FROM bqt1.smalla WHERE intkey = 1 " + //$NON-NLS-1$
            "GROUP BY intkey HAVING intkey = 1";         //$NON-NLS-1$
        QueryMetadataInterface metadata = FakeMetadataFactory.exampleBQTCached();        
        Command command = helpResolve(sql, metadata);

        try {
            ValidatorReport report = Validator.validate(command, metadata);
            assertEquals("Should get no report items", 0, report.getItems().size()); //$NON-NLS-1$
        } catch(MetaMatrixComponentException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }        
    } 
    
    public void testVirtualProcedure(){
          helpValidate("EXEC pm1.vsp1()", new String[] { }, FakeMetadataFactory.example1Cached());  //$NON-NLS-1$
    }
        
    public void testSelectWithNoFrom() {        
        helpValidate("SELECT 5", new String[] {}, exampleMetadata()); //$NON-NLS-1$
    }
    
    public void testSelectIntoTempGroup() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "SELECT e1, e2, e3, e4 INTO #myTempTable FROM pm1.g2;\n";         //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED = SELECT COUNT(*) FROM #myTempTable;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1"; //$NON-NLS-1$

        helpValidateProcedure(procedure, userQuery,
                FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
    /**
     * Defect 24346
     */
    public void testInvalidSelectIntoTempGroup() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "SELECT e1, e2, e3, e4 INTO #myTempTable FROM pm1.g2;\n";         //$NON-NLS-1$
        procedure = procedure + "SELECT e1, e2, e3 INTO #myTempTable FROM pm1.g2;\n";         //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED = SELECT COUNT(*) FROM #myTempTable;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1"; //$NON-NLS-1$

        helpFailProcedure(procedure, userQuery,
                FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
    /**
     * Defect 24346 with type mismatch
     */
    public void testInvalidSelectIntoTempGroup1() {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "create local temporary table #myTempTable (e1 integer);\n";         //$NON-NLS-1$
        procedure = procedure + "SELECT e1 INTO #myTempTable FROM pm1.g2;\n";         //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED = SELECT COUNT(*) FROM #myTempTable;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1"; //$NON-NLS-1$

        helpFailProcedure(procedure, userQuery,
                FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }

    
    public void testSelectIntoPhysicalGroup() {
        helpValidate("SELECT e1, e2, e3, e4 INTO pm1.g1 FROM pm1.g2", new String[] { }, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$
        
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "SELECT e1, e2, e3, e4 INTO pm1.g1 FROM pm1.g2;\n";         //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED = 0;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1"; //$NON-NLS-1$

        helpValidateProcedure(procedure, userQuery,
                FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
    public void testSelectIntoPhysicalGroupNotUpdateable_Defect16857() {
        helpValidate("SELECT e0, e1, e2 INTO test.group3 FROM test.group2", new String[] {"test.group3"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    public void testSelectIntoElementsNotUpdateable() {
    	helpValidate("SELECT e0, e1, e2 INTO test.group2 FROM test.group3", new String[] {"test.group2"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    public void testInvalidSelectIntoTooManyElements() {
    	helpValidate("SELECT e1, e2, e3, e4, 'val' INTO pm1.g1 FROM pm1.g2", new String[] {"SELECT e1, e2, e3, e4, 'val' INTO pm1.g1 FROM pm1.g2"}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
        
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "SELECT e1, e2, e3, e4, 'val' INTO pm1.g1 FROM pm1.g2;\n";         //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED = 0;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1"; //$NON-NLS-1$

        helpFailProcedure(procedure, userQuery,
                FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
    public void testInvalidSelectIntoTooFewElements() {
    	helpValidate("SELECT e1, e2, e3 INTO pm1.g1 FROM pm1.g2", new String[] {"SELECT e1, e2, e3 INTO pm1.g1 FROM pm1.g2"}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
        
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "SELECT e1, e2, e3 INTO pm1.g1 FROM pm1.g2;\n";         //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED = 0;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1"; //$NON-NLS-1$

        helpFailProcedure(procedure, userQuery,
                FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
    public void testInvalidSelectIntoIncorrectTypes() {
        helpValidate("SELECT e1, convert(e2, string), e3, e4 INTO pm1.g1 FROM pm1.g2", new String[] {"SELECT e1, convert(e2, string), e3, e4 INTO pm1.g1 FROM pm1.g2"}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
        
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "SELECT e1, convert(e2, string), e3, e4 INTO pm1.g1 FROM pm1.g2;\n";         //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED = 0;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1"; //$NON-NLS-1$

        helpFailProcedure(procedure, userQuery,
                FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
    public void testSelectIntoWithStar() {
        helpResolve("SELECT * INTO pm1.g1 FROM pm1.g2", FakeMetadataFactory.example1Cached()); //$NON-NLS-1$
    }
    
    public void testInvalidSelectIntoWithStar() {
        helpValidate("SELECT * INTO pm1.g1 FROM pm1.g2, pm1.g1", new String[] {"SELECT * INTO pm1.g1 FROM pm1.g2, pm1.g1"}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
        
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "SELECT * INTO pm1.g1 FROM pm1.g2, pm1.g1;\n";         //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED = 0;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1"; //$NON-NLS-1$

        helpFailProcedure(procedure, userQuery,
                FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
    public void testInvalidSelectIntoVirtualGroup() {
        helpValidate("SELECT e1, e2, e3, e4 INTO vm1.g1 FROM pm1.g2", new String[] {"vm1.g1"}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
        
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "SELECT e1, e2, e3, e4 INTO vm1.g1 FROM pm1.g2;\n";         //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED = 0;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1"; //$NON-NLS-1$

        helpFailProcedure(procedure, userQuery,
                FakeMetadataObject.Props.UPDATE_PROCEDURE);
    }
    
    public void testVirtualProcedure2(){
          helpValidate("EXEC pm1.vsp13()", new String[] { }, FakeMetadataFactory.example1Cached());  //$NON-NLS-1$
    }

    //procedure that has another procedure in the transformation
    public void testVirtualProcedure3(){
        helpValidate("EXEC pm1.vsp27()", new String[] { }, FakeMetadataFactory.example1Cached());  //$NON-NLS-1$
    }
    
    public void testNonEmbeddedSubcommand_defect11000() {        
        helpValidate("SELECT e0 FROM vTest.vGroup", new String[0], exampleMetadata()); //$NON-NLS-1$ 
    }
    
    public void testValidateObjectInComparison() throws Exception {
        String sql = "SELECT IntKey FROM BQT1.SmallA WHERE ObjectValue = 5";   //$NON-NLS-1$
        ValidatorReport report = helpValidate(sql, new String[] {"ObjectValue = 5"}, FakeMetadataFactory.exampleBQTCached()); //$NON-NLS-1$
        assertEquals("Expressions of type OBJECT, CLOB, BLOB, or XML cannot be used in comparison: ObjectValue = 5.", report.toString()); //$NON-NLS-1$
    }

    public void testValidateAssignmentWithFunctionOnParameter_InServer() throws Exception{
        String sql = "EXEC pm1.vsp36(5)";  //$NON-NLS-1$
        QueryMetadataInterface metadata = FakeMetadataFactory.example1Cached();
        
        Command command = new QueryParser().parseCommand(sql);
        QueryResolver.resolveCommand(command, metadata);
        
        // Validate
        ValidatorReport report = Validator.validate(command, metadata); 
        assertEquals(0, report.getItems().size());                      
    }

    public void testDefect9917() throws Exception{
    	QueryMetadataInterface metadata = FakeMetadataFactory.example1Cached();
        String sql = "SELECT lookup('pm1.g1', 'e1a', 'e2', e2) AS x, lookup('pm1.g1', 'e4', 'e3', e3) AS y FROM pm1.g1"; //$NON-NLS-1$
        Command command = new QueryParser().parseCommand(sql);
        try{
        	QueryResolver.resolveCommand(command, metadata); 
        	fail("Did not get exception"); //$NON-NLS-1$
        }catch(QueryResolverException e){
        	//expected
        }
        
        sql = "SELECT lookup('pm1.g1a', 'e1', 'e2', e2) AS x, lookup('pm1.g1', 'e4', 'e3', e3) AS y FROM pm1.g1"; //$NON-NLS-1$
        command = new QueryParser().parseCommand(sql);
        try{
        	QueryResolver.resolveCommand(command, metadata); 
        	fail("Did not get exception"); //$NON-NLS-1$
        }catch(QueryResolverException e){
        	//expected
        }
    }
    
    public void testLookupKeyElementComparable() throws Exception {
    	QueryMetadataInterface metadata = exampleMetadata2();
        String sql = "SELECT lookup('test.group', 'e2', 'e3', convert(e2, blob)) AS x FROM test.group"; //$NON-NLS-1$
        Command command = QueryParser.getQueryParser().parseCommand(sql);
    	QueryResolver.resolveCommand(command, metadata); 
        
        ValidatorReport report = Validator.validate(command, metadata);
        assertEquals("Expressions of type OBJECT, CLOB, BLOB, or XML cannot be used as LOOKUP key columns: test.\"group\".e3.", report.toString()); //$NON-NLS-1$
    }
    
    public void testDefect12107() throws Exception{
    	QueryMetadataInterface metadata = FakeMetadataFactory.example1Cached();
        String sql = "SELECT SUM(DISTINCT lookup('pm1.g1', 'e2', 'e2', e2)) FROM pm1.g1"; //$NON-NLS-1$
        Command command = helpResolve(sql, metadata);
        sql = "SELECT SUM(DISTINCT lookup('pm1.g1', 'e3', 'e2', e2)) FROM pm1.g1"; //$NON-NLS-1$
        command = helpResolve(sql, metadata);
        ValidatorReport report = Validator.validate(command, metadata);
        assertEquals("The aggregate function SUM cannot be used with non-numeric expressions: SUM(DISTINCT lookup('pm1.g1', 'e3', 'e2', e2))", report.toString()); //$NON-NLS-1$
    }
    
    private ValidatorReport helpValidateInModeler(String procName, String procSql, QueryMetadataInterface metadata) throws Exception {
        Command command = new QueryParser().parseCommand(procSql);
        
        GroupSymbol group = new GroupSymbol(procName);
        Map externalMetadata = getStoredProcedureExternalMetadata(group, metadata);
        QueryResolver.resolveCommand(command, externalMetadata, false, metadata, AnalysisRecord.createNonRecordingRecord());
        
        // Validate
        return Validator.validate(command, metadata);         
    }

    public void testValidateDynamicCommandWithNonTempGroup_InModeler() throws Exception{
        // SQL is same as pm1.vsp36() in example1 
        String sql = "CREATE VIRTUAL PROCEDURE BEGIN execute string 'select ' || '1' as X integer into pm1.g3; END";  //$NON-NLS-1$        
        QueryMetadataInterface metadata = FakeMetadataFactory.example1Cached();
        
        // Validate
        ValidatorReport report = helpValidateInModeler("pm1.vsp36", sql, metadata);  //$NON-NLS-1$
        assertEquals(1, report.getItems().size());
        assertEquals("Wrong number of elements being SELECTed INTO the target table. Expected 4 elements, but was 1.", report.toString()); //$NON-NLS-1$
    }
    
    public void testDynamicDupUsing() throws Exception {
        String sql = "CREATE VIRTUAL PROCEDURE BEGIN execute string 'select ' || '1' as X integer into #temp using id=1, id=2; END";  //$NON-NLS-1$        
        QueryMetadataInterface metadata = FakeMetadataFactory.example1Cached();
        
        // Validate
        ValidatorReport report = helpValidateInModeler("pm1.vsp36", sql, metadata);  //$NON-NLS-1$
        assertEquals(1, report.getItems().size());
        assertEquals("Elements cannot appear more than once in a SET or USING clause.  The following elements are duplicated: [\"USING\".id]", report.toString()); //$NON-NLS-1$
    }    
    
    public void testValidateAssignmentWithFunctionOnParameter_InModeler() throws Exception{
        // SQL is same as pm1.vsp36() in example1 
        String sql = "CREATE VIRTUAL PROCEDURE BEGIN DECLARE integer x; x = pm1.vsp36.param1 * 2; SELECT x; END";  //$NON-NLS-1$        
        QueryMetadataInterface metadata = FakeMetadataFactory.example1Cached();
        
        // Validate
        ValidatorReport report = helpValidateInModeler("pm1.vsp36", sql, metadata);  //$NON-NLS-1$
        assertEquals(0, report.getItems().size());                      
    }
    
    public void testDefect12533() {
        String sql = "SELECT BQT1.SmallA.DateValue, BQT2.SmallB.ObjectValue FROM BQT1.SmallA, BQT2.SmallB " +  //$NON-NLS-1$
            "WHERE BQT1.SmallA.DateValue = BQT2.SmallB.DateValue AND BQT1.SmallA.ObjectValue = BQT2.SmallB.ObjectValue " + //$NON-NLS-1$
            "AND BQT1.SmallA.IntKey < 30 AND BQT2.SmallB.IntKey < 30 ORDER BY BQT1.SmallA.DateValue"; //$NON-NLS-1$
        QueryMetadataInterface metadata = FakeMetadataFactory.exampleBQTCached();
        
        // Validate
        helpValidate(sql, new String[] {"BQT1.SmallA.ObjectValue = BQT2.SmallB.ObjectValue"}, metadata);  //$NON-NLS-1$ 
    }

    public void testDefect16772() throws Exception{
        String sql = "CREATE VIRTUAL PROCEDURE BEGIN IF (pm1.vsp42.param1 > 0) SELECT 1 AS x; ELSE SELECT 0 AS x; END"; //$NON-NLS-1$
        
        QueryMetadataInterface metadata = FakeMetadataFactory.example1Cached();
        
        // Validate
        ValidatorReport report = helpValidateInModeler("pm1.vsp42", sql, metadata);  //$NON-NLS-1$ 
        assertEquals("Expected report to have no validation failures", false, report.hasItems()); //$NON-NLS-1$
    }
	
	public void testDefect14886() throws Exception{        
        String sql = "CREATE VIRTUAL PROCEDURE BEGIN END";  //$NON-NLS-1$        
        QueryMetadataInterface metadata = FakeMetadataFactory.example1Cached();
        
        Command command = new QueryParser().parseCommand(sql);
        QueryResolver.resolveCommand(command, new HashMap(), false, metadata, AnalysisRecord.createNonRecordingRecord());
        
        // Validate
        ValidatorReport report = Validator.validate(command, metadata); 
        // Validate
        assertEquals(1, report.getItems().size());  	
    }
	
    public void testDefect21389() throws Exception{        
        String sql = "CREATE VIRTUAL PROCEDURE BEGIN SELECT * INTO #temptable FROM pm1.g1; INSERT INTO #temptable (e1) VALUES ('a'); END"; //$NON-NLS-1$      
        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
        FakeMetadataObject e1 = metadata.getStore().findObject("pm1.g1.e1", FakeMetadataObject.ELEMENT); //$NON-NLS-1$
        e1.putProperty(FakeMetadataObject.Props.UPDATE, Boolean.FALSE);
        
        Command command = new QueryParser().parseCommand(sql);
        QueryResolver.resolveCommand(command, new HashMap(), false, metadata, AnalysisRecord.createNonRecordingRecord());
        
        // Validate
        ValidatorReport report = Validator.validate(command, metadata); 
        // Validate
        assertEquals(0, report.getItems().size());      
    }
    
    public void testMakeNotDep() {
        helpValidate("select group2.e1, group3.e2 from group2, group3 WHERE group2.e0 = group3.e0 OPTION MAKENOTDEP group2, group3", new String[0], exampleMetadata()); //$NON-NLS-1$
    }
    public void testInvalidMakeNotDep() {
    	helpValidate("select group2.e1, group3.e2 from group2, group3 WHERE group2.e0 = group3.e0 OPTION MAKEDEP group2 MAKENOTDEP group2, group3", new String[] {"OPTION MAKEDEP group2 MAKENOTDEP group2, group3"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    public void testInvalidLimit() {
        helpValidate("SELECT * FROM pm1.g1 LIMIT -5", new String[] {"LIMIT -5"}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testInvalidLimit_Offset() {
    	helpValidate("SELECT * FROM pm1.g1 LIMIT -1, 100", new String[] {"LIMIT -1, 100"}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    /**
     * Test case 4237.  This test simulates the way the modeler transformation 
     * panel uses the query resolver and validator to validate a transformation for
     * a virtual procedure.  The modeler has to supply external metadata for the 
     * virtual procedure group and parameter names (simulated in this test).
     * 
     * This virtual procedure calls a physical stored procedure directly.  
     */
    public void testCase4237() {

        FakeMetadataFacade metadata = helpCreateCase4237VirtualProcedureMetadata();
        Map externalMetadata = helpCreateCase4237ExternalMetadata(metadata);
        
        String sql = "CREATE VIRTUAL PROCEDURE BEGIN EXEC pm1.sp(vm1.sp.in1); END"; //$NON-NLS-1$ 
        Command command = helpResolve(sql, metadata, externalMetadata);
        helpRunValidator(command, new String[0], metadata);
    }

    /**
     * This test was already working before the case was logged, due for some reason
     * to the exec() statement being inside an inline view.  This is a control test. 
     */
    public void testCase4237InlineView() {

        FakeMetadataFacade metadata = helpCreateCase4237VirtualProcedureMetadata();
        Map externalMetadata = helpCreateCase4237ExternalMetadata(metadata);
        
        String sql = "CREATE VIRTUAL PROCEDURE BEGIN SELECT * FROM (EXEC pm1.sp(vm1.sp.in1)) AS FOO; END"; //$NON-NLS-1$ 
        Command command = helpResolve(sql, metadata, externalMetadata);
        helpRunValidator(command, new String[0], metadata);
    }    
    
    /**
     * Set up external metadata describing the virtual procedure and parameters 
     * @param metadata FakeMetadataFacade
     * @return external metadata Map
     */
    private Map helpCreateCase4237ExternalMetadata(FakeMetadataFacade metadata) {
        GroupSymbol sp = new GroupSymbol("vm1.sp");//$NON-NLS-1$ 
        FakeMetadataObject spObj = metadata.getStore().findObject("vm1.sp", FakeMetadataObject.PROCEDURE);//$NON-NLS-1$ 
        sp.setMetadataID(spObj);
        ElementSymbol param = new ElementSymbol("vm1.sp.in1");//$NON-NLS-1$ 
        List paramIDs = (List)spObj.getProperty(FakeMetadataObject.Props.PARAMS);
        Iterator i = paramIDs.iterator();
        while (i.hasNext()) {
            FakeMetadataObject paramID = (FakeMetadataObject)i.next();
            if (paramID.getProperty(FakeMetadataObject.Props.DIRECTION).equals(new Integer(ParameterInfo.IN))) {
                param.setMetadataID(paramID);
                break;
            }
        }
        
        Map externalMetadata = new HashMap();
        externalMetadata.put(sp, Arrays.asList(new ElementSymbol[] { param }));
 
        return externalMetadata;
    }

    /**
     * Create fake metadata for this case.  Need a physical stored procedure and
     * a virtual stored procedure which calls the physical one. 
     * @return
     */
    private FakeMetadataFacade helpCreateCase4237VirtualProcedureMetadata() {
        FakeMetadataObject physicalModel = FakeMetadataFactory.createPhysicalModel("pm1"); //$NON-NLS-1$
        FakeMetadataObject resultSet = FakeMetadataFactory.createResultSet("pm1.rs", physicalModel, new String[] { "e1", "e2" }, new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        FakeMetadataObject returnParam = FakeMetadataFactory.createParameter("ret", 1, ParameterInfo.RESULT_SET, DataTypeManager.DefaultDataTypes.OBJECT, resultSet);  //$NON-NLS-1$
        FakeMetadataObject inParam = FakeMetadataFactory.createParameter("in", 2, ParameterInfo.IN, DataTypeManager.DefaultDataTypes.STRING, null);  //$NON-NLS-1$
        FakeMetadataObject storedProcedure = FakeMetadataFactory.createStoredProcedure("pm1.sp", physicalModel, Arrays.asList(new FakeMetadataObject[] { returnParam, inParam }), "pm1.sp2");  //$NON-NLS-1$ //$NON-NLS-2$
        
        FakeMetadataObject virtualModel = FakeMetadataFactory.createVirtualModel("vm1"); //$NON-NLS-1$
        FakeMetadataObject virtualResultSet = FakeMetadataFactory.createResultSet("vm1.rs", physicalModel, new String[] { "e1", "e2" }, new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        FakeMetadataObject virtualReturnParam = FakeMetadataFactory.createParameter("ret", 1, ParameterInfo.RESULT_SET, DataTypeManager.DefaultDataTypes.OBJECT, virtualResultSet);  //$NON-NLS-1$
        FakeMetadataObject virtualInParam = FakeMetadataFactory.createParameter("in1", 2, ParameterInfo.IN, DataTypeManager.DefaultDataTypes.STRING, null);  //$NON-NLS-1$
        QueryNode queryNode = new QueryNode("vm1.sp", "CREATE VIRTUAL PROCEDURE BEGIN EXEC pm1.sp(vm1.sp.in1); END"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject virtualStoredProcedure = FakeMetadataFactory.createVirtualProcedure("vm1.sp", physicalModel, Arrays.asList(new FakeMetadataObject[] { virtualReturnParam, virtualInParam }), queryNode);  //$NON-NLS-1$
                
        FakeMetadataStore store = new FakeMetadataStore();
        store.addObject(physicalModel);
        store.addObject(resultSet);
        store.addObject(storedProcedure);
        store.addObject(virtualModel);
        store.addObject(virtualResultSet);
        store.addObject(virtualStoredProcedure);
        return new FakeMetadataFacade(store);
    }       
    
    public void testSelectIntoWithNull() {
        helpValidate("SELECT null, null, null, null INTO pm1.g1 FROM pm1.g2", new String[] {}, FakeMetadataFactory.example1Cached()); //$NON-NLS-1$
    }
        
    public void testDropNonTemporary() {
        QueryMetadataInterface metadata = FakeMetadataFactory.example1Cached();
        Command command = helpResolve("drop table pm1.g1", metadata); //$NON-NLS-1$
        helpRunValidator(command, new String[] {command.toString()}, FakeMetadataFactory.example1Cached()); 
    }
    
    public void testNestedContexts() {
        helpValidate("SELECT * FROM vm1.doc1 where context(a0, context(a0, a2))='x'", new String[] {"context(a0, context(a0, a2))"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    public void testValidContextElement() {
        helpValidate("SELECT * FROM vm1.doc1 where context(1, a2)='x'", new String[] {"context(1, a2)"}, exampleMetadata()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    public void testValidTempUpdateDelete() {
        FakeMetadataFacade fakeMetadata = exampleMetadata();
        
        TempMetadataStore store = new TempMetadataStore(new HashMap());
        
        ElementSymbol e1 = new ElementSymbol("#temp.e1");//$NON-NLS-1$
        e1.setType(DataTypeManager.DefaultDataClasses.INTEGER);
        
        store.addTempGroup("#temp", Arrays.asList(e1), false, true); //$NON-NLS-1$ 
        
        QueryMetadataInterface metadata = new TempMetadataAdapter(fakeMetadata, store);
        
        helpValidate("delete from #temp", new String[] {"#temp"}, metadata); //$NON-NLS-1$ //$NON-NLS-2$
        
        helpValidate("update #temp set e1 = 1", new String[] {"#temp"}, metadata); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void testSelectIntoVirtual() throws Exception {
        QueryMetadataInterface metadata = FakeMetadataFactory.example1Cached();
        Command command = helpResolve("select * into vm1.g1 from pm1.g1", metadata); //$NON-NLS-1$
        ValidatorReport report = Validator.validate(command, metadata);
        assertEquals("The target table for a SELECT INTO or an INSERT with a query expression can only be a physical table or a temporary table.", report.toString()); //$NON-NLS-1$
    }
    
    public void testInsertIntoVirtualWithQuery() throws Exception {
        QueryMetadataInterface metadata = FakeMetadataFactory.example1Cached();
        Command command = helpResolve("insert into vm1.g1 select 1, 2, true, 3", metadata); //$NON-NLS-1$
        ValidatorReport report = Validator.validate(command, metadata);
        assertEquals("The target table for a SELECT INTO or an INSERT with a query expression can only be a physical table or a temporary table.", report.toString()); //$NON-NLS-1$
    }
    
    public void testDynamicIntoDeclaredTemp() throws Exception {
        StringBuffer procedure = new StringBuffer("CREATE VIRTUAL PROCEDURE  ") //$NON-NLS-1$
                                .append("BEGIN\n") //$NON-NLS-1$
                                .append("CREATE LOCAL TEMPORARY TABLE x (column1 string);") //$NON-NLS-1$
                                .append("execute string 'SELECT e1 FROM pm1.g2' as e1 string INTO x;\n") //$NON-NLS-1$
                                .append("select column1 from x;\n") //$NON-NLS-1$
                                .append("END\n"); //$NON-NLS-1$
        
        QueryMetadataInterface metadata = FakeMetadataFactory.example1Cached();
        
        // Validate
        ValidatorReport report = helpValidateInModeler("pm1.vsp36", procedure.toString(), metadata);  //$NON-NLS-1$
        assertEquals(report.toString(), 0, report.getItems().size());
    }
    
    public void testVariablesGroupSelect() {
        String procedure = "CREATE VIRTUAL PROCEDURE "; //$NON-NLS-1$
        procedure += "BEGIN\n"; //$NON-NLS-1$
        procedure += "DECLARE integer VARIABLES.var1 = 1;\n"; //$NON-NLS-1$
        procedure += "select * from variables;\n"; //$NON-NLS-1$
        procedure += "END\n"; //$NON-NLS-1$
        
        QueryMetadataInterface metadata = FakeMetadataFactory.example1Cached();
        
        Command command = helpResolve(procedure, metadata);
        helpRunValidator(command, new String[] {"variables"}, metadata); //$NON-NLS-1$
    }
    
    public void testClobEquals() {
        TestValidator.helpValidate("SELECT * FROM test.group where e4 = '1'", new String[] {"e4 = '1'"}, TestValidator.exampleMetadata2()); //$NON-NLS-1$ //$NON-NLS-2$ 
    }
    
    /**
     *  Should not fail since the update changing set is not really criteria
     */
    public void testUpdateWithClob() {
        TestValidator.helpValidate("update test.group set e4 = ?", new String[] {}, TestValidator.exampleMetadata2()); //$NON-NLS-1$ 
    }

    public void testBlobLessThan() {
        TestValidator.helpValidate("SELECT * FROM test.group where e3 < ?", new String[] {"e3 < ?"}, TestValidator.exampleMetadata2()); //$NON-NLS-1$ //$NON-NLS-2$ 
    }
    
	public void testValidateCompare2() {        
        helpValidate("SELECT e2 FROM test.group WHERE e4 IS NULL", new String[] {}, exampleMetadata2()); //$NON-NLS-1$ 
	}

	public void testValidateCompare3() {        
        helpValidate("SELECT e2 FROM test.group WHERE e4 IN ('a')", new String[] {"e4 IN ('a')"}, exampleMetadata2()); //$NON-NLS-1$ //$NON-NLS-2$
	}

    public void testValidateCompare5() {        
        helpValidate("SELECT e2 FROM test.group WHERE e4 BETWEEN '1' AND '2'", new String[] {"e4 BETWEEN '1' AND '2'"}, exampleMetadata2()); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
	public void testValidateCompareInHaving1() {        
        helpValidate("SELECT e1 FROM test.group GROUP BY e1 HAVING convert(e1, clob) = 'a'", new String[] {"convert(e1, clob) = 'a'"}, exampleMetadata2()); //$NON-NLS-1$ //$NON-NLS-2$
	}

}
