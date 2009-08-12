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

package com.metamatrix.query.processor.xml;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import junit.framework.TestCase;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.MetaMatrixProcessingException;
import com.metamatrix.api.exception.query.QueryParserException;
import com.metamatrix.api.exception.query.QueryPlannerException;
import com.metamatrix.api.exception.query.QueryResolverException;
import com.metamatrix.api.exception.query.QueryValidatorException;
import com.metamatrix.common.buffer.BlockedException;
import com.metamatrix.common.buffer.BufferManager;
import com.metamatrix.common.buffer.BufferManagerFactory;
import com.metamatrix.common.buffer.TupleSource;
import com.metamatrix.common.types.DataTypeManager;
import com.metamatrix.common.types.XMLType;
import com.metamatrix.core.util.UnitTestUtil;
import com.metamatrix.dqp.message.ParameterInfo;
import com.metamatrix.query.analysis.AnalysisRecord;
import com.metamatrix.query.mapping.relational.QueryNode;
import com.metamatrix.query.mapping.xml.MappingAttribute;
import com.metamatrix.query.mapping.xml.MappingChoiceNode;
import com.metamatrix.query.mapping.xml.MappingCommentNode;
import com.metamatrix.query.mapping.xml.MappingCriteriaNode;
import com.metamatrix.query.mapping.xml.MappingDocument;
import com.metamatrix.query.mapping.xml.MappingElement;
import com.metamatrix.query.mapping.xml.MappingNode;
import com.metamatrix.query.mapping.xml.MappingNodeConstants;
import com.metamatrix.query.mapping.xml.MappingRecursiveElement;
import com.metamatrix.query.mapping.xml.MappingSequenceNode;
import com.metamatrix.query.mapping.xml.Namespace;
import com.metamatrix.query.metadata.QueryMetadataInterface;
import com.metamatrix.query.optimizer.QueryOptimizer;
import com.metamatrix.query.optimizer.capabilities.BasicSourceCapabilities;
import com.metamatrix.query.optimizer.capabilities.CapabilitiesFinder;
import com.metamatrix.query.optimizer.capabilities.DefaultCapabilitiesFinder;
import com.metamatrix.query.optimizer.capabilities.SourceCapabilities;
import com.metamatrix.query.optimizer.capabilities.SourceCapabilities.Capability;
import com.metamatrix.query.optimizer.xml.TestXMLPlanner;
import com.metamatrix.query.parser.ParseInfo;
import com.metamatrix.query.parser.QueryParser;
import com.metamatrix.query.processor.FakeDataManager;
import com.metamatrix.query.processor.QueryProcessor;
import com.metamatrix.query.resolver.QueryResolver;
import com.metamatrix.query.rewriter.QueryRewriter;
import com.metamatrix.query.sql.lang.Command;
import com.metamatrix.query.sql.symbol.ElementSymbol;
import com.metamatrix.query.unittest.FakeMetadataFacade;
import com.metamatrix.query.unittest.FakeMetadataFactory;
import com.metamatrix.query.unittest.FakeMetadataObject;
import com.metamatrix.query.unittest.FakeMetadataStore;
import com.metamatrix.query.util.CommandContext;

/**
 * Tests XML processing, which involves XMLPlanner making a ProcessorPlan
 * (XMLPlan) from a mapping document (tree of MappingNode objects) and
 * metadata, and then that XMLPlan being processed with metadata, a 
 * ProcessorDataManager and a QueryProcessor.
 */
public class TestXMLProcessor extends TestCase {
    private static final boolean DEBUG = false;
    
    public TestXMLProcessor(String name) {
        super(name);
    }

    /**
     * Construct some fake metadata.  Basic conceptual tree is:
     * 
     * stock (physical model)
     *   items (physical group)
     *     itemNum (string)
     *     itemName (string)
     *     itemQuantity (integer)
     * xmltest (virtual model)
     *   rs (virtual group / result set definition)
     *     itemNum (string)
     *     itemName (string)
     *     itemQuantity (integer)
     */
    public static FakeMetadataFacade exampleMetadataCached() {
        return EXAMPLE_CACHED;
    } 
    
    private static final FakeMetadataFacade EXAMPLE_CACHED = exampleMetadata();
    
    public static FakeMetadataFacade exampleMetadata() {
        FakeMetadataStore store = new FakeMetadataStore();
        FakeMetadataFacade facade = new FakeMetadataFacade(store);
        
        // Create models
        FakeMetadataObject stock = FakeMetadataFactory.createPhysicalModel("stock"); //$NON-NLS-1$
        FakeMetadataObject xmltest = FakeMetadataFactory.createVirtualModel("xmltest");     //$NON-NLS-1$

        // Create physical groups
        FakeMetadataObject items = FakeMetadataFactory.createPhysicalGroup("stock.items", stock); //$NON-NLS-1$
        FakeMetadataObject item_supplier = FakeMetadataFactory.createPhysicalGroup("stock.item_supplier", stock); //$NON-NLS-1$

        FakeMetadataObject suppliers = FakeMetadataFactory.createPhysicalGroup("stock.suppliers", stock); //$NON-NLS-1$
        FakeMetadataObject orders = FakeMetadataFactory.createPhysicalGroup("stock.orders", stock); //$NON-NLS-1$
        FakeMetadataObject employees = FakeMetadataFactory.createPhysicalGroup("stock.employees", stock); //$NON-NLS-1$
             
        // Create physical elements
        List itemElements = FakeMetadataFactory.createElements(items, 
            new String[] { "itemNum", "itemName", "itemQuantity", "itemStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });

        //many-to-many join table
        List itemSupplierElements = FakeMetadataFactory.createElements(item_supplier, 
            new String[] { "itemNum", "supplierNum" }, //$NON-NLS-1$ //$NON-NLS-2$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });

        List supplierElements = FakeMetadataFactory.createElements(suppliers, 
            new String[] { "supplierNum", "supplierName", "supplierZipCode"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});

        List stockOrders = FakeMetadataFactory.createElements(orders, 
            new String[] { "orderNum", "itemFK", "supplierFK", "supplierNameFK", "orderDate", "orderQty", "orderStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING});

        List supplierEmployees = FakeMetadataFactory.createElements(employees, 
            new String[] { "employeeNum", "supplierNumFK", "supervisorNum", "firstName", "lastName" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});


        
// ======================================================================================================================

        // Create virtual groups
        // per defect 6829 - intentionally including the reserved word "group" as part of this virtual group name
        QueryNode rsQuery = new QueryNode("xmltest.group.items", "SELECT itemNum, itemName, itemQuantity, itemStatus FROM stock.items"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject rs = FakeMetadataFactory.createVirtualGroup("xmltest.group.items", xmltest, rsQuery); //$NON-NLS-1$

        // Created 2nd virtual group w/ nested result set & binding
        QueryNode rsQuery2 = new QueryNode("xmltest.suppliers", "SELECT concat(stock.suppliers.supplierNum, '') as supplierNum, supplierName, supplierZipCode FROM stock.suppliers, stock.item_supplier WHERE stock.suppliers.supplierNum = stock.item_supplier.supplierNum AND stock.item_supplier.itemNum = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        //QueryNode rsQuery2 = new QueryNode("xmltest.suppliers", "SELECT stock.suppliers.supplierNum, supplierName, supplierZipCode FROM stock.suppliers, stock.item_supplier WHERE stock.suppliers.supplierNum = stock.item_supplier.supplierNum AND stock.item_supplier.itemNum = ?");
        rsQuery2.addBinding("xmltest.group.items.itemNum"); //$NON-NLS-1$
        FakeMetadataObject rs2 = FakeMetadataFactory.createVirtualGroup("xmltest.suppliers", xmltest, rsQuery2); //$NON-NLS-1$

        // Created virtual group w/ nested result set & binding
        QueryNode rsQuery3 = new QueryNode("xmltest.orders", "SELECT orderNum, orderDate, orderQty, orderStatus FROM stock.orders WHERE itemFK = ? AND supplierFK = ? AND supplierNameFK = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        rsQuery3.addBinding("xmltest.group.items.itemNum"); //$NON-NLS-1$
        rsQuery3.addBinding("xmltest.suppliers.supplierNum"); //$NON-NLS-1$
        rsQuery3.addBinding("xmltest.suppliers.supplierName"); //$NON-NLS-1$
        FakeMetadataObject rs3 = FakeMetadataFactory.createVirtualGroup("xmltest.orders", xmltest, rsQuery3); //$NON-NLS-1$


// ======================================================================================================================

        //create employees - not connected to any of the above
        QueryNode rsEmployees = new QueryNode("xmltest.employees", "SELECT employeeNum, firstName, lastName FROM stock.employees WHERE supervisorNum IS NULL"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject rs4 = FakeMetadataFactory.createVirtualGroup("xmltest.employees", xmltest, rsEmployees); //$NON-NLS-1$

        //recursive piece
        QueryNode rsEmployeesRecursive = new QueryNode("xmltest.employeesRecursive", "SELECT employeeNum, firstName, lastName FROM stock.employees WHERE supervisorNum = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        rsEmployeesRecursive.addBinding("xmltest.employees.employeeNum"); //$NON-NLS-1$
        FakeMetadataObject rs4a = FakeMetadataFactory.createVirtualGroup("xmltest.employeesRecursive", xmltest, rsEmployeesRecursive); //$NON-NLS-1$

// ======================================================================================================================

        //create employees - not connected to any of the above
        QueryNode rsEmployees2 = new QueryNode("xmltest.employees2", "SELECT employeeNum, firstName, lastName, supervisorNum FROM stock.employees WHERE supplierNumFK = '2' AND NOT (supervisorNum IS NULL)"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject rs5 = FakeMetadataFactory.createVirtualGroup("xmltest.employees2", xmltest, rsEmployees2); //$NON-NLS-1$

        //recursive piece
        QueryNode rsEmployees2Recursive = new QueryNode("xmltest.employees2Recursive", "SELECT employeeNum, firstName, lastName, supervisorNum FROM stock.employees WHERE employeeNum = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        rsEmployees2Recursive.addBinding("xmltest.employees2.supervisorNum"); //$NON-NLS-1$
        FakeMetadataObject rs5a = FakeMetadataFactory.createVirtualGroup("xmltest.employees2Recursive", xmltest, rsEmployees2Recursive); //$NON-NLS-1$

//      ======================================================================================================================
// Alternate mapping class which selects from stored query

        // Created 2nd virtual group w/ nested result set & binding
        QueryNode rsQueryX = new QueryNode("xmltest.suppliersX", "SELECT * FROM (exec xmltest.sqX(?)) as X"); //$NON-NLS-1$ //$NON-NLS-2$
        rsQueryX.addBinding("xmltest.group.items.itemNum"); //$NON-NLS-1$
        FakeMetadataObject rsQX = FakeMetadataFactory.createVirtualGroup("xmltest.suppliersX", xmltest, rsQueryX); //$NON-NLS-1$

// ======================================================================================================================
// ALTERNATE METADATA A (temp groups)

        // root temp group
        QueryNode tempQuery = new QueryNode("tempGroup.orders", "SELECT * FROM stock.orders"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject temp = FakeMetadataFactory.createVirtualGroup("tempGroup.orders", xmltest, tempQuery); //$NON-NLS-1$

        // 2nd bogus root temp group selects from first - tests that temp groups can select from others
        QueryNode tempQuery2 = new QueryNode("tempGroup.orders2", "SELECT * FROM tempGroup.orders"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject temp2 = FakeMetadataFactory.createVirtualGroup("tempGroup.orders2", xmltest, tempQuery2); //$NON-NLS-1$
        
        // Created virtual group w/ nested result set & binding - selects from 2nd temp root group
        QueryNode rsQuery3a = new QueryNode("xmltest.ordersA", "SELECT orderNum, orderDate, orderQty, orderStatus FROM tempGroup.orders2 WHERE itemFK = ? AND supplierFK = ? AND supplierNameFK = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        rsQuery3a.addBinding("xmltest.group.items.itemNum"); //$NON-NLS-1$
        rsQuery3a.addBinding("xmltest.suppliers.supplierNum"); //$NON-NLS-1$
        rsQuery3a.addBinding("xmltest.suppliers.supplierName"); //$NON-NLS-1$
        FakeMetadataObject rs3a = FakeMetadataFactory.createVirtualGroup("xmltest.ordersA", xmltest, rsQuery3a); //$NON-NLS-1$

// ======================================================================================================================
// ALTERNATE METADATA B (temp groups)

        //temp group selects from root temp group and it has bindings to other mapping classes
        // from 5.5 bindings are not supported in the staging tables. even before we did not supported 
        // them in the modeler; but we did in execution; now we remove it as it poses more issues.
        QueryNode tempQuery3b = new QueryNode("tempGroup.orders3B", "SELECT orderNum, orderDate, orderQty, orderStatus FROM tempGroup.orders2 WHERE itemFK = ? AND supplierFK = ? AND supplierNameFK = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        tempQuery3b.addBinding("xmltest.group.items.itemNum"); //$NON-NLS-1$
        tempQuery3b.addBinding("xmltest.suppliers.supplierNum"); //$NON-NLS-1$
        tempQuery3b.addBinding("xmltest.suppliers.supplierName"); //$NON-NLS-1$
        FakeMetadataObject temp3b = FakeMetadataFactory.createVirtualGroup("tempGroup.orders3B", xmltest, tempQuery3b); //$NON-NLS-1$
        
        // Created virtual group w/ nested result set & binding
        QueryNode rsQuery3b = new QueryNode("xmltest.ordersB", "SELECT orderNum, orderDate, orderQty, orderStatus FROM tempGroup.orders3B"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject rs3b = FakeMetadataFactory.createVirtualGroup("xmltest.ordersB", xmltest, rsQuery3b); //$NON-NLS-1$


// ======================================================================================================================
// ALTERNATE METADATA C (temp group with union)

//        //temp group selects from root temp group and it has bindings to other mapping classes
//        QueryNode tempQuery3b = new QueryNode("tempGroup.orders3B", "SELECT orderNum, orderDate, orderQty, orderStatus FROM tempGroup.orders2 WHERE itemFK = ? AND supplierFK = ? AND supplierNameFK = ?");
//        tempQuery3b.addBinding("xmltest.group.items.itemNum");
//        tempQuery3b.addBinding("xmltest.suppliers.supplierNum");
//        tempQuery3b.addBinding("xmltest.suppliers.supplierName");
//        FakeMetadataObject temp3b = FakeMetadataFactory.createVirtualGroup("tempGroup.orders3B", xmltest, tempQuery3b);
//        
//        // Created virtual group w/ nested result set & binding
//        QueryNode rsQuery3b = new QueryNode("xmltest.ordersB", "SELECT orderNum, orderDate, orderQty, orderStatus FROM tempGroup.orders3B");
//        FakeMetadataObject rs3b = FakeMetadataFactory.createVirtualGroup("xmltest.ordersB", xmltest, rs   Query3b);

// ======================================================================================================================

// ======================================================================================================================
// ALTERNATE METADATA D (correlated subquery in mapping class)
       // Create virtual groups
       // per defect 12260 - correlated subquery in mapping class transformation
       QueryNode rsQuery12260 = new QueryNode("xmltest.group.itemsWithNumSuppliers", "SELECT itemNum, itemName, itemQuantity, itemStatus, convert((select count(*) from stock.item_supplier where stock.items.itemNum = stock.item_supplier.itemNum), string) as NUMSuppliers FROM stock.items"); //$NON-NLS-1$ //$NON-NLS-2$
       FakeMetadataObject rs12260 = FakeMetadataFactory.createVirtualGroup("xmltest.group.itemsWithNumSuppliers", xmltest, rsQuery12260); //$NON-NLS-1$

       List rsElements12260 = FakeMetadataFactory.createElements(rs12260, 
           new String[] { "itemNum", "itemName", "itemQuantity", "itemStatus", "numSuppliers" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
           new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER });        
    
// ======================================================================================================================
// ALTERNATE METADATA E (mapping class w/ Union)
       // Create virtual groups
       // per defect 8373
       QueryNode rsQuery8373 = new QueryNode("xmltest.items8373", "SELECT itemNum, itemName, itemQuantity, itemStatus FROM stock.items UNION ALL SELECT itemNum, itemName, itemQuantity, itemStatus FROM stock.items"); //$NON-NLS-1$ //$NON-NLS-2$
       FakeMetadataObject rs8373 = FakeMetadataFactory.createVirtualGroup("xmltest.items8373", xmltest, rsQuery8373); //$NON-NLS-1$

       List rsElements8373 = FakeMetadataFactory.createElements(rs8373, 
            new String[] { "itemNum", "itemName", "itemQuantity", "itemStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });        

       //select * from xmltest.items8373
       QueryNode rsQuery8373a = new QueryNode("xmltest.items8373a", "SELECT * FROM xmltest.items8373"); //$NON-NLS-1$ //$NON-NLS-2$
       FakeMetadataObject rs8373a = FakeMetadataFactory.createVirtualGroup("xmltest.items8373a", xmltest, rsQuery8373a); //$NON-NLS-1$

       List rsElements8373a = FakeMetadataFactory.createElements(rs8373a, 
            new String[] { "itemNum", "itemName", "itemQuantity", "itemStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });        

       QueryNode rsQuery8373b = new QueryNode("xmltest.items8373b", "SELECT * FROM xmltest.group.items UNION ALL SELECT * FROM xmltest.group.items"); //$NON-NLS-1$ //$NON-NLS-2$
       FakeMetadataObject rs8373b = FakeMetadataFactory.createVirtualGroup("xmltest.items8373b", xmltest, rsQuery8373b); //$NON-NLS-1$

       List rsElements8373b = FakeMetadataFactory.createElements(rs8373b, 
            new String[] { "itemNum", "itemName", "itemQuantity", "itemStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });        
       
// ======================================================================================================================


        // Test an update query as a mapping class transformation, as if it were a 
        // mapping class returning a single int - defect 8812
        QueryNode rsUpdateQuery = new QueryNode("xmltest.updateTest", "INSERT INTO stock.items (itemNum, itemName, itemQuantity, itemStatus) VALUES ('3','beer',12,'something')"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject rsUpdate = FakeMetadataFactory.createVirtualGroup("xmltest.updateTest", xmltest, rsUpdateQuery); //$NON-NLS-1$

        // Create virtual elements
        List rsElements = FakeMetadataFactory.createElements(rs, 
            new String[] { "itemNum", "itemName", "itemQuantity", "itemStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });        


        List rsElements2 = FakeMetadataFactory.createElements(rs2, 
            new String[] { "supplierNum", "supplierName", "supplierZipCode"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});

        List rsElements3 = FakeMetadataFactory.createElements(rs3, 
            new String[] { "orderNum", "orderDate", "orderQty", "orderStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING});

        List rsElements4 = FakeMetadataFactory.createElements(rs4, 
            new String[] { "employeeNum", "firstName", "lastName" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});

        List rsElements4a = FakeMetadataFactory.createElements(rs4a, 
            new String[] { "employeeNum", "firstName", "lastName" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});

        List rsElements5 = FakeMetadataFactory.createElements(rs5, 
            new String[] { "employeeNum", "firstName", "lastName", "supervisorNum" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});

        List rsElements5a = FakeMetadataFactory.createElements(rs5a, 
            new String[] { "employeeNum", "firstName", "lastName", "supervisorNum" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});

        List tempElements = FakeMetadataFactory.createElements(temp, 
            new String[] { "orderNum", "itemFK", "supplierFK", "supplierNameFK", "orderDate", "orderQty", "orderStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING});

        List tempElements2 = FakeMetadataFactory.createElements(temp2, 
            new String[] { "orderNum", "itemFK", "supplierFK", "supplierNameFK", "orderDate", "orderQty", "orderStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING});

        List rsElements3a = FakeMetadataFactory.createElements(rs3a, 
            new String[] { "orderNum", "orderDate", "orderQty", "orderStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING});

        List tempElements3b = FakeMetadataFactory.createElements(temp3b, 
            new String[] { "orderNum", "orderDate", "orderQty", "orderStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING});

        List rsElements3b = FakeMetadataFactory.createElements(rs3b, 
            new String[] { "orderNum", "orderDate", "orderQty", "orderStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING});

        List rsUpdateElement = FakeMetadataFactory.createElements(rsUpdate, 
            new String[] { "rowCount" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER});

        List rsElementsX = FakeMetadataFactory.createElements(rsQX, 
            new String[] { "supplierNum", "supplierName", "supplierZipCode" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
       
        // Create virtual docs
        FakeMetadataObject doc1 = FakeMetadataFactory.createVirtualGroup("xmltest.doc1", xmltest, createXMLMappingNode(true)); //$NON-NLS-1$
        List docE1 = FakeMetadataFactory.createElements(doc1, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER });
            
        FakeMetadataObject doc1a = FakeMetadataFactory.createVirtualGroup("xmltest.doc1Unformatted", xmltest, createXMLMappingNode(false)); //$NON-NLS-1$
        FakeMetadataObject doc1b = FakeMetadataFactory.createVirtualGroup("xmltest.doc1b", xmltest, createXMLPlan2(false, true, 0 )); //$NON-NLS-1$
        List docE1b = FakeMetadataFactory.createElements(doc1b, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
                                                        new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER });
        FakeMetadataObject doc1c = FakeMetadataFactory.createVirtualGroup("xmltest.doc1c", xmltest, createXMLPlan2(false, true, 1 )); //$NON-NLS-1$
        List docE1c = FakeMetadataFactory.createElements(doc1c, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
                                                        new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER });
        
        FakeMetadataObject docBounded = FakeMetadataFactory.createVirtualGroup("xmltest.docBounded", xmltest, createXMLMappingBoundingNode()); //$NON-NLS-1$
        List docBoundedElements = FakeMetadataFactory.createElements(docBounded, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER });
        

        FakeMetadataObject doc2  = FakeMetadataFactory.createVirtualGroup("xmltest.doc2",  xmltest, createXMLPlan2(1, -1, false)); //$NON-NLS-1$
        List docE2 = FakeMetadataFactory.createElements(doc2, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
        FakeMetadataObject doc2a = FakeMetadataFactory.createVirtualGroup("xmltest.doc2a", xmltest, createXMLPlan2(1, 1, false)); //$NON-NLS-1$
        FakeMetadataObject doc2b = FakeMetadataFactory.createVirtualGroup("xmltest.doc2b", xmltest, createXMLPlan2(1, -1, true)); //$NON-NLS-1$
        FakeMetadataObject doc2c = FakeMetadataFactory.createVirtualGroup("xmltest.doc2c", xmltest, createXMLPlan2(2, -1, false)); //$NON-NLS-1$
        FakeMetadataObject doc2d = FakeMetadataFactory.createVirtualGroup("xmltest.doc2d", xmltest, createXMLPlan2(2, 1, false)); //$NON-NLS-1$
        FakeMetadataObject doc2e = FakeMetadataFactory.createVirtualGroup("xmltest.doc2e", xmltest, createXMLPlan2(2, 3, false)); //$NON-NLS-1$

        FakeMetadataObject doc3 = FakeMetadataFactory.createVirtualGroup("xmltest.doc3", xmltest, createXMLPlanWithDefaults()); //$NON-NLS-1$
        List docE3 = FakeMetadataFactory.createElements(doc3, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER });
        FakeMetadataObject doc4 = FakeMetadataFactory.createVirtualGroup("xmltest.doc4", xmltest, createXMLPlanAdvanced()); //$NON-NLS-1$
        List docE4 = FakeMetadataFactory.createElements(doc4, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Fake", "Catalogs.Fake.FakeChild2", "Catalogs.Fake.FakeChild2.FakeChild2a", "Catalogs.Fake.FakeChild3", "Catalogs.Fake.FakeChild3.@FakeAtt" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER,DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});
        FakeMetadataObject doc5 = FakeMetadataFactory.createVirtualGroup("xmltest.doc5", xmltest, createXMLPlanUltraAdvanced()); //$NON-NLS-1$
        List docE5 = FakeMetadataFactory.createElements(doc5, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.DiscontinuedItem", "Catalogs.Catalog.items.DiscontinuedItem.@ItemID", "Catalogs.Catalog.items.DiscontinuedItem.Name", "Catalogs.Catalog.items.DiscontinuedItem.Quantity", "Catalogs.Catalog.items.StatusUnknown", "Catalogs.Catalog.items.StatusUnknown.@ItemID", "Catalogs.Catalog.items.StatusUnknown.Name", "Catalogs.Catalog.items.StatusUnknown.Quantity", "Catalogs.Catalog.items.Shouldn't see", "Catalogs.Catalog.items.Shouldn't see 2" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$ //$NON-NLS-15$ //$NON-NLS-16$ //$NON-NLS-17$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
        FakeMetadataObject doc6 = FakeMetadataFactory.createVirtualGroup("xmltest.doc6", xmltest, createXMLPlanUltraAdvancedExceptionOnDefault()); //$NON-NLS-1$
        List docE6 = FakeMetadataFactory.createElements(doc6, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.DiscontinuedItem", "Catalogs.Catalog.items.DiscontinuedItem.@ItemID", "Catalogs.Catalog.items.DiscontinuedItem.Name", "Catalogs.Catalog.items.DiscontinuedItem.Quantity", "Catalogs.Catalog.items.Shouldn't see"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });
        FakeMetadataObject doc7 = FakeMetadataFactory.createVirtualGroup("xmltest.doc7", xmltest, createTestAttributePlan()); //$NON-NLS-1$
        List docE7 = FakeMetadataFactory.createElements(doc7, new String[] { "FixedValueTest", "FixedValueTest.wrapper", "FixedValueTest.wrapper.@fixed", "FixedValueTest.wrapper.@key", "FixedValueTest.wrapper.@fixedAttr"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});
                        
        FakeMetadataObject doc8 = FakeMetadataFactory.createVirtualGroup("xmltest.doc8", xmltest, createXMLPlanNested()); //$NON-NLS-1$
        List docE8 = FakeMetadataFactory.createElements(doc8, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.item.suppliers", "Catalogs.Catalog.items.item.suppliers.supplier", "Catalogs.Catalog.items.item.suppliers.supplier.@SupplierID", "Catalogs.Catalog.items.item.suppliers.supplier.zip", "Catalogs.Catalog.items.item.suppliers.supplier.Name" },  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});
    
        FakeMetadataObject doc9 = FakeMetadataFactory.createVirtualGroup("xmltest.doc9", xmltest, createXMLPlanNested2()); //$NON-NLS-1$
        List docE9 = FakeMetadataFactory.createElements(doc9, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.item.suppliers", "Catalogs.Catalog.items.item.suppliers.supplier", "Catalogs.Catalog.items.item.suppliers.supplier.@SupplierID", "Catalogs.Catalog.items.item.suppliers.supplier.zip", "Catalogs.Catalog.items.item.suppliers.supplier.Name", "Catalogs.Catalog.items.item.suppliers.supplier.orders", "Catalogs.Catalog.items.item.suppliers.supplier.orders.order", "Catalogs.Catalog.items.item.suppliers.supplier.orders.order.@OrderID" ,"Catalogs.Catalog.items.item.suppliers.supplier.orders.order.OrderDate", "Catalogs.Catalog.items.item.suppliers.supplier.orders.order.OrderQuantity", "Catalogs.Catalog.items.item.suppliers.supplier.orders.order.OrderStatus"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$ //$NON-NLS-15$ //$NON-NLS-16$ //$NON-NLS-17$ //$NON-NLS-18$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});
            
        FakeMetadataObject doc9a = FakeMetadataFactory.createVirtualGroup("xmltest.doc9a", xmltest, createXMLPlanNested2a()); //$NON-NLS-1$
        List docE9a = FakeMetadataFactory.createElements(doc9a, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.item.suppliers", "Catalogs.Catalog.items.item.suppliers.supplier", "Catalogs.Catalog.items.item.suppliers.supplier.@SupplierID", "Catalogs.Catalog.items.item.suppliers.supplier.zip", "Catalogs.Catalog.items.item.suppliers.supplier.Name", "Catalogs.Catalog.items.item.suppliers.supplier.orders", "Catalogs.Catalog.items.item.suppliers.supplier.orders.order", "Catalogs.Catalog.items.item.suppliers.supplier.orders.order.@OrderID" ,"Catalogs.Catalog.items.item.suppliers.supplier.orders.order.OrderDate", "Catalogs.Catalog.items.item.suppliers.supplier.orders.order.OrderQuantity", "Catalogs.Catalog.items.item.suppliers.supplier.orders.order.OrderStatus"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$ //$NON-NLS-15$ //$NON-NLS-16$ //$NON-NLS-17$ //$NON-NLS-18$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});

        FakeMetadataObject doc9b = FakeMetadataFactory.createVirtualGroup("xmltest.doc9b", xmltest, createXMLPlanNested2b()); //$NON-NLS-1$
        FakeMetadataObject doc10 = FakeMetadataFactory.createVirtualGroup("xmltest.doc10", xmltest, createXMLPlanNestedWithChoice()); //$NON-NLS-1$
        List docE10 = FakeMetadataFactory.createElements(doc10, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.item.suppliers", "Catalogs.Catalog.items.item.suppliers.supplier", "Catalogs.Catalog.items.item.suppliers.supplier.@SupplierID", "Catalogs.Catalog.items.item.suppliers.supplier.zip", "Catalogs.Catalog.items.item.suppliers.supplier.Name", "Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders", "Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.order", "Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.order.@OrderID" ,"Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.order.OrderDate", "Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.order.OrderQuantity", "Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.order.OrderStatus", "Catalogs.Catalog.items.item.suppliers.supplier.orders.ProcessingOrders" ,"Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.otherorder.@OrderID", "Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.otherorder"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$ //$NON-NLS-15$ //$NON-NLS-16$ //$NON-NLS-17$ //$NON-NLS-18$ //$NON-NLS-19$ //$NON-NLS-20$ //$NON-NLS-21$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});

        FakeMetadataObject doc10L = FakeMetadataFactory.createVirtualGroup("xmltest.doc10L", xmltest, createXMLPlanNestedWithLookupChoice()); //$NON-NLS-1$
        List docE10L = FakeMetadataFactory.createElements(doc10L, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.item.suppliers", "Catalogs.Catalog.items.item.suppliers.supplier", "Catalogs.Catalog.items.item.suppliers.supplier.@SupplierID", "Catalogs.Catalog.items.item.suppliers.supplier.zip", "Catalogs.Catalog.items.item.suppliers.supplier.Name", "Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders", "Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.order", "Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.order.@OrderID" ,"Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.order.OrderDate", "Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.order.OrderQuantity", "Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.order.OrderStatus", "Catalogs.Catalog.items.item.suppliers.supplier.orders.ProcessingOrders" ,"Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.otherorder.@OrderID", "Catalogs.Catalog.items.item.suppliers.supplier.ProcessingOrders.otherorder"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$ //$NON-NLS-15$ //$NON-NLS-16$ //$NON-NLS-17$ //$NON-NLS-18$ //$NON-NLS-19$ //$NON-NLS-20$ //$NON-NLS-21$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});


        FakeMetadataObject doc11 = FakeMetadataFactory.createVirtualGroup("xmltest.doc11", xmltest, createXMLPlanMultipleDocs()); //$NON-NLS-1$
        List docE11 = FakeMetadataFactory.createElements(doc11, new String[] { "Item", "Item.@ItemID", "Item.Name", "Item.Quantity", "Item.Suppliers", "Item.Suppliers.Supplier", "Item.Suppliers.Supplier.@SupplierID", "Item.Suppliers.Supplier.Name", "Item.Suppliers.Supplier.Zip"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ 
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});

        //variations on the same recursive doc====================
        boolean useRecursiveCriteria = false;
        int recursionlimit = -1;
        boolean exceptionOnLimit = false;

        FakeMetadataObject doc12 = FakeMetadataFactory.createVirtualGroup("xmltest.doc12", xmltest, createXMLPlanRecursive(useRecursiveCriteria, recursionlimit, exceptionOnLimit)); //$NON-NLS-1$
        FakeMetadataObject doc12a = FakeMetadataFactory.createVirtualGroup("xmltest.doc12a", xmltest, createXMLPlanRecursiveA(useRecursiveCriteria, recursionlimit, exceptionOnLimit)); //$NON-NLS-1$

        useRecursiveCriteria = true;
        FakeMetadataObject doc13 = FakeMetadataFactory.createVirtualGroup("xmltest.doc13", xmltest, createXMLPlanRecursive(useRecursiveCriteria, recursionlimit, exceptionOnLimit)); //$NON-NLS-1$
        useRecursiveCriteria = false;
        recursionlimit = 2;
        FakeMetadataObject doc14 = FakeMetadataFactory.createVirtualGroup("xmltest.doc14", xmltest, createXMLPlanRecursive(useRecursiveCriteria, recursionlimit, exceptionOnLimit)); //$NON-NLS-1$
        exceptionOnLimit = true;
        FakeMetadataObject doc15 = FakeMetadataFactory.createVirtualGroup("xmltest.doc15", xmltest, createXMLPlanRecursive(useRecursiveCriteria, recursionlimit, exceptionOnLimit)); //$NON-NLS-1$

        useRecursiveCriteria = false;
        recursionlimit = -1;
        exceptionOnLimit = false;
        FakeMetadataObject doc16 = FakeMetadataFactory.createVirtualGroup("xmltest.doc16", xmltest, createXMLPlanRecursive2(useRecursiveCriteria, recursionlimit, exceptionOnLimit)); //$NON-NLS-1$

        FakeMetadataObject doc17 = FakeMetadataFactory.createVirtualGroup("xmltest.doc17", xmltest, createXMLPlanWithComment()); //$NON-NLS-1$

        FakeMetadataObject doc_5266a = FakeMetadataFactory.createVirtualGroup("xmltest.doc_5266a", xmltest, createXMLPlanNestedWithChoiceFor5266()); //$NON-NLS-1$
        List doc_E5266a = FakeMetadataFactory.createElements(doc_5266a, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                             new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });


        FakeMetadataObject doc_8917 = FakeMetadataFactory.createVirtualGroup("xmltest.doc_8917", xmltest, createXMLPlan_defect8917()); //$NON-NLS-1$
        FakeMetadataObject doc_9446 = FakeMetadataFactory.createVirtualGroup("xmltest.doc_9446", xmltest, createXMLPlan_defect9446()); //$NON-NLS-1$
        FakeMetadataObject doc_9530 = FakeMetadataFactory.createVirtualGroup("xmltest.doc_9530", xmltest, createXMLPlan_defect_9530()); //$NON-NLS-1$

        List docE_9446 = FakeMetadataFactory.createElements(doc_9446, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.XXXXX", "Catalogs.Catalog.items.item.XXXXX", "Catalogs.Catalog.items.item.XXXXX"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER});
        
        //Test doc w/ update mapping class transformation
        FakeMetadataObject docUpdateTest = FakeMetadataFactory.createVirtualGroup("xmltest.docUpdateTest", xmltest, createUpdateTestDoc()); //$NON-NLS-1$

        FakeMetadataObject doc_9893 = FakeMetadataFactory.createVirtualGroup("xmltest.doc9893", xmltest, createXMLPlan_9893()); //$NON-NLS-1$
        List docE_9893 = FakeMetadataFactory.createElements(doc_9893, new String[] { "Root", "Root.ItemName"}, //$NON-NLS-1$ //$NON-NLS-2$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});

        FakeMetadataObject doc18 = FakeMetadataFactory.createVirtualGroup("xmltest.doc18", xmltest, createXMLPlanNested("xmltest.suppliersX")); //$NON-NLS-1$ //$NON-NLS-2$
        List docE18 = FakeMetadataFactory.createElements(doc18, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.item.suppliers", "Catalogs.Catalog.items.item.suppliers.supplier", "Catalogs.Catalog.items.item.suppliers.supplier.@SupplierID", "Catalogs.Catalog.items.item.suppliers.supplier.zip", "Catalogs.Catalog.items.item.suppliers.supplier.Name" },  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});

        FakeMetadataObject doc12260 = FakeMetadataFactory.createVirtualGroup("xmltest.doc12260", xmltest, createXMLPlanCorrelatedSubqueryTransform()); //$NON-NLS-1$
        List docE12260 = FakeMetadataFactory.createElements(doc12260, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.item.numSuppliers" },  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-7$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-8$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING});

        FakeMetadataObject doc8373 = FakeMetadataFactory.createVirtualGroup("xmltest.doc8373", xmltest, createXMLPlan_defect8373()); //$NON-NLS-1$
        List docE8373 = FakeMetadataFactory.createElements(doc8373, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.item.numSuppliers" },  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-7$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-8$
                                                            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING});

        FakeMetadataObject doc8373a = FakeMetadataFactory.createVirtualGroup("xmltest.doc8373a", xmltest, createXMLPlan_defect8373a()); //$NON-NLS-1$
        List docE8373a = FakeMetadataFactory.createElements(doc8373a, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.item.numSuppliers" },  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-7$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-8$
                                                            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING});

        FakeMetadataObject doc8373b = FakeMetadataFactory.createVirtualGroup("xmltest.doc8373b", xmltest, createXMLPlan_defect8373b()); //$NON-NLS-1$
        List docE8373b = FakeMetadataFactory.createElements(doc8373b, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.item.numSuppliers" },  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-7$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-8$
                                                            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING});

        FakeMetadataObject doc13617 = FakeMetadataFactory.createVirtualGroup("xmltest.doc13617", xmltest, createXMLPlanDefect13617()); //$NON-NLS-1$
        List docE13617 = FakeMetadataFactory.createElements(doc13617, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity" },  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-7$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ 
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER});
        

        // recursive + staging ========================================================

        FakeMetadataObject doc19 = FakeMetadataFactory.createVirtualGroup("xmltest.doc19", xmltest, createXMLPlanRecursiveStaging(true, recursionlimit, exceptionOnLimit)); //$NON-NLS-1$
        
        // root temp group
        QueryNode doc19TempQuery = new QueryNode("xmltest.doc19temp", "SELECT employeeNum, firstName, lastName, supervisorNum FROM stock.employees"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject tempDoc19 = FakeMetadataFactory.createVirtualGroup("xmltest.doc19temp", xmltest, doc19TempQuery); //$NON-NLS-1$
        List doc19TempQueryE = FakeMetadataFactory.createElements(tempDoc19, 
                                                              new String[] { "employeeNum", "firstName", "lastName", "supervisorNum" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                                                              new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});
        

        //create employees - not connected to any of the above
        QueryNode rsEmployeesDoc19 = new QueryNode("xmltest.employeesDoc19", "SELECT employeeNum, firstName, lastName FROM xmltest.doc19temp WHERE supervisorNum IS NULL"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject mc1Doc19 = FakeMetadataFactory.createVirtualGroup("xmltest.employeesDoc19", xmltest, rsEmployeesDoc19); //$NON-NLS-1$
        List mc1Doc19E = FakeMetadataFactory.createElements(mc1Doc19, 
                                                                  new String[] { "employeeNum", "firstName", "lastName" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ 
                                                                  new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});

        //recursive piece
        QueryNode rsEmployeesRecursiveDoc19 = new QueryNode("xmltest.employeesRecursiveDoc19", "SELECT employeeNum, firstName, lastName FROM xmltest.doc19temp WHERE supervisorNum = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        rsEmployeesRecursiveDoc19.addBinding("xmltest.employeesDoc19.employeeNum"); //$NON-NLS-1$
        FakeMetadataObject mc2Doc19 = FakeMetadataFactory.createVirtualGroup("xmltest.employeesRecursiveDoc19", xmltest, rsEmployeesRecursiveDoc19); //$NON-NLS-1$
        List mc2Doc19E = FakeMetadataFactory.createElements(mc2Doc19, 
                                                            new String[] { "employeeNum", "firstName", "lastName" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ 
                                                            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});
        
        // recursive + staging ========================================================
        
        //========================================================

        // Stored queries
        FakeMetadataObject rsX = FakeMetadataFactory.createResultSet("xmltest.rsX", xmltest, new String[] { "supplierNum", "supplierName", "supplierZipCode" }, new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        FakeMetadataObject rsXp1 = FakeMetadataFactory.createParameter("ret", 1, ParameterInfo.RESULT_SET, DataTypeManager.DefaultDataTypes.OBJECT, rsX);  //$NON-NLS-1$
        FakeMetadataObject rsXp2 = FakeMetadataFactory.createParameter("in", 2, ParameterInfo.IN, DataTypeManager.DefaultDataTypes.STRING, null);  //$NON-NLS-1$
        //there is an extra statement in this proc so that the procedure wrapper is not removed
        QueryNode sqXn1 = new QueryNode("xmltest.sqX", "CREATE VIRTUAL PROCEDURE BEGIN declare string x; SELECT concat(stock.suppliers.supplierNum, '') as supplierNum, supplierName, supplierZipCode FROM stock.suppliers, stock.item_supplier WHERE stock.suppliers.supplierNum = stock.item_supplier.supplierNum AND stock.item_supplier.itemNum = xmltest.sqX.in; END"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject sqX = FakeMetadataFactory.createVirtualProcedure("xmltest.sqX", xmltest, Arrays.asList(new FakeMetadataObject[] { rsXp1, rsXp2 }), sqXn1);  //$NON-NLS-1$
       
        // Documents for Text Normalization Test 
        // normDoc1 - for collapse
        // normDoc2 - for replace
        // normDoc3 - for preserve
        FakeMetadataObject normDoc1 = FakeMetadataFactory.createVirtualGroup("xmltest.normDoc1", xmltest, createXMLPlanNormalization(MappingNodeConstants.NORMALIZE_TEXT_COLLAPSE)); //$NON-NLS-1$
        List normDocE1 = FakeMetadataFactory.createElements(normDoc1, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.DiscontinuedItem", "Catalogs.Catalog.items.DiscontinuedItem.@ItemID", "Catalogs.Catalog.items.DiscontinuedItem.Name", "Catalogs.Catalog.items.DiscontinuedItem.Quantity", "Catalogs.Catalog.items.StatusUnknown", "Catalogs.Catalog.items.StatusUnknown.@ItemID", "Catalogs.Catalog.items.StatusUnknown.Name", "Catalogs.Catalog.items.StatusUnknown.Quantity", "Catalogs.Catalog.items.Shouldn't see", "Catalogs.Catalog.items.Shouldn't see 2" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$ //$NON-NLS-15$ //$NON-NLS-16$ //$NON-NLS-17$
                                                        new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
        FakeMetadataObject normDoc2 = FakeMetadataFactory.createVirtualGroup("xmltest.normDoc2", xmltest, createXMLPlanNormalization(MappingNodeConstants.NORMALIZE_TEXT_REPLACE)); //$NON-NLS-1$
        List normDocE2 = FakeMetadataFactory.createElements(normDoc2, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.DiscontinuedItem", "Catalogs.Catalog.items.DiscontinuedItem.@ItemID", "Catalogs.Catalog.items.DiscontinuedItem.Name", "Catalogs.Catalog.items.DiscontinuedItem.Quantity", "Catalogs.Catalog.items.StatusUnknown", "Catalogs.Catalog.items.StatusUnknown.@ItemID", "Catalogs.Catalog.items.StatusUnknown.Name", "Catalogs.Catalog.items.StatusUnknown.Quantity", "Catalogs.Catalog.items.Shouldn't see", "Catalogs.Catalog.items.Shouldn't see 2" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$ //$NON-NLS-15$ //$NON-NLS-16$ //$NON-NLS-17$
                                                            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
 
        FakeMetadataObject normDoc3 = FakeMetadataFactory.createVirtualGroup("xmltest.normDoc3", xmltest, createXMLPlanNormalization(MappingNodeConstants.NORMALIZE_TEXT_PRESERVE)); //$NON-NLS-1$
        List normDocE3 = FakeMetadataFactory.createElements(normDoc3, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.DiscontinuedItem", "Catalogs.Catalog.items.DiscontinuedItem.@ItemID", "Catalogs.Catalog.items.DiscontinuedItem.Name", "Catalogs.Catalog.items.DiscontinuedItem.Quantity", "Catalogs.Catalog.items.StatusUnknown", "Catalogs.Catalog.items.StatusUnknown.@ItemID", "Catalogs.Catalog.items.StatusUnknown.Name", "Catalogs.Catalog.items.StatusUnknown.Quantity", "Catalogs.Catalog.items.Shouldn't see", "Catalogs.Catalog.items.Shouldn't see 2" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$ //$NON-NLS-15$ //$NON-NLS-16$ //$NON-NLS-17$
                                                            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
        
        QueryNode vspqn1 = new QueryNode("vsp1", "CREATE VIRTUAL PROCEDURE BEGIN SELECT * FROM xmltest.doc1; END"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vsprs1 = FakeMetadataFactory.createResultSet("pm1.vsprs1", xmltest, new String[] { "xml" }, new String[] { DataTypeManager.DefaultDataTypes.XML }); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vspp1 = FakeMetadataFactory.createParameter("ret", 1, ParameterInfo.RESULT_SET, DataTypeManager.DefaultDataTypes.XML, vsprs1); //$NON-NLS-1$
        FakeMetadataObject vsp1 = FakeMetadataFactory.createVirtualProcedure("xmltest.vsp1", xmltest, Arrays.asList(new FakeMetadataObject[] { vspp1 }), vspqn1); //$NON-NLS-1$

        // Add all objects to the store
        store.addObject(stock);
        store.addObject(items);
        store.addObject(item_supplier);
   
        store.addObject(suppliers);
        store.addObject(orders);
        store.addObject(employees);
        store.addObjects(itemElements);
        store.addObjects(itemSupplierElements);
        store.addObjects(supplierElements);
        store.addObjects(stockOrders);
        store.addObjects(supplierEmployees);
     
        store.addObject(xmltest);
        store.addObject(rs);
        store.addObject(rs2);
        store.addObject(rs3);
        store.addObject(rs4);
        store.addObject(rs4a);
        store.addObject(rs5);
        store.addObject(rs5a);
        store.addObject(temp);
        store.addObject(temp2);
        store.addObject(rs3a);
        store.addObject(temp3b);
        store.addObject(rs3b);
        store.addObject(rsQX);
        store.addObject(rs12260);
        store.addObject(rs8373);
        store.addObject(rs8373a);
        store.addObject(rs8373b);
        
        //Stored query
        store.addObject(rsX);
        store.addObject(sqX);
  
        store.addObject(rsUpdate);
        store.addObjects(rsElements);
        store.addObjects(rsElements2);
        store.addObjects(rsElements3);
        store.addObjects(rsElements4);
        store.addObjects(rsElements4a);
        store.addObjects(rsElements5);
        store.addObjects(rsElements5a);
        store.addObjects(tempElements);
        store.addObjects(tempElements2);
        store.addObjects(rsElements3a);
        store.addObjects(tempElements3b);
        store.addObjects(rsElements3b);
        store.addObjects(rsUpdateElement);
        store.addObjects(rsElementsX);
        store.addObjects(rsElements12260);
        store.addObjects(rsElements8373);
        store.addObjects(rsElements8373a);
        store.addObjects(rsElements8373b);

        store.addObject(doc1);
        store.addObject(docBounded);
        store.addObject(doc1a);
        store.addObject(doc1b);
        store.addObject(doc1c);
        store.addObject(doc2);
        store.addObject(doc2a);
        store.addObject(doc2b);
        store.addObject(doc2c);
        store.addObject(doc2d);
        store.addObject(doc2e);
        store.addObject(doc3);
        store.addObject(doc4);
        store.addObject(doc5);
 
        store.addObject(normDoc1);
        store.addObject(normDoc2);
        store.addObject(normDoc3);

        store.addObject(doc6);
        store.addObject(doc7);
        store.addObject(doc8);
        store.addObject(doc9);
        store.addObject(doc9a);
        store.addObject(doc9b);
     
        store.addObject(doc10);
        store.addObject(doc10L);
        store.addObject(doc11);
        store.addObject(doc12);
        store.addObject(doc12a);
        store.addObject(doc13);
        store.addObject(doc14);
        store.addObject(doc15);
        store.addObject(doc16);
        store.addObject(doc17);
        store.addObject(doc_8917);
        store.addObject(doc_9446);
        store.addObject(doc_9530);
        store.addObject(docUpdateTest);
        store.addObject(doc_9893);
        store.addObject(doc18);
        store.addObject(doc12260);
        store.addObject(doc8373);
        store.addObject(doc8373a);
        store.addObject(doc8373b);
        store.addObject(doc13617);
        store.addObject(doc19);
        store.addObject(tempDoc19);
        store.addObject(mc1Doc19);
        store.addObject(mc2Doc19);
        store.addObject(doc_5266a);

        store.addObjects(doc19TempQueryE);
        store.addObjects(mc1Doc19E);
        store.addObjects(mc2Doc19E);
        
        store.addObjects(docE1);
        store.addObjects(docE1b);
        store.addObjects(docE1c);
        store.addObjects(docBoundedElements);
        store.addObjects(docE2);
        store.addObjects(docE3);
        store.addObjects(docE4);
        store.addObjects(docE5);
        store.addObjects(normDocE1);
        store.addObjects(normDocE2);
        store.addObjects(normDocE3);
        store.addObjects(docE6);
        store.addObjects(docE7);
        store.addObjects(docE8);
        store.addObjects(docE9);
        store.addObjects(docE9a);
        store.addObjects(docE_9446);
        store.addObjects(docE_9893);
        store.addObjects(docE18);
        store.addObjects(docE12260);
        store.addObjects(docE8373);
        store.addObjects(docE8373a);
        store.addObjects(docE8373b);
        store.addObjects(docE13617);
        store.addObjects(doc_E5266a);
        
        store.addObjects(docE10);
        store.addObjects(docE10L);
        store.addObjects(docE11);
        
        store.addObject(vsp1);        
        return facade;
    }

    public FakeMetadataFacade exampleMetadataNestedWithSibling() {
		FakeMetadataStore store = new FakeMetadataStore();
		FakeMetadataFacade facade = new FakeMetadataFacade(store);
        
		// Create models
		FakeMetadataObject stock = FakeMetadataFactory.createPhysicalModel("stock"); //$NON-NLS-1$
		FakeMetadataObject xmltest = FakeMetadataFactory.createVirtualModel("xmltest");  //$NON-NLS-1$

		// Create physical groups
		FakeMetadataObject items = FakeMetadataFactory.createPhysicalGroup("stock.items", stock); //$NON-NLS-1$
		FakeMetadataObject item_supplier = FakeMetadataFactory.createPhysicalGroup("stock.item_supplier", stock); //$NON-NLS-1$
		FakeMetadataObject item_order = FakeMetadataFactory.createPhysicalGroup("stock.item_order", stock); //$NON-NLS-1$
		FakeMetadataObject orders = FakeMetadataFactory.createPhysicalGroup("stock.orders", stock); //$NON-NLS-1$
		FakeMetadataObject suppliers = FakeMetadataFactory.createPhysicalGroup("stock.suppliers", stock); //$NON-NLS-1$
		      
		// Create physical elements
		List itemElements = FakeMetadataFactory.createElements(items, 
			new String[] { "itemNum", "itemName", "itemQuantity", "itemStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });

		//many-to-many join table
		List itemSupplierElements = FakeMetadataFactory.createElements(item_supplier, 
			new String[] { "itemNum", "supplierNum" }, //$NON-NLS-1$ //$NON-NLS-2$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });

		List supplierElements = FakeMetadataFactory.createElements(suppliers, 
			new String[] { "supplierNum", "supplierName", "supplierZipCode" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });

		//many-to-many join table
		List itemOrderElements = FakeMetadataFactory.createElements(item_order, 
			new String[] { "itemNum", "orderNum" }, //$NON-NLS-1$ //$NON-NLS-2$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });

		List stockOrders = FakeMetadataFactory.createElements(orders, 
			new String[] { "orderNum", "orderName", "orderZipCode" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
	
// ======================================================================================================================

		// Create virtual groups
		QueryNode rsQuery1 = new QueryNode("xmltest.group.items", "SELECT itemNum, itemName, itemQuantity, itemStatus FROM stock.items"); //$NON-NLS-1$ //$NON-NLS-2$
		FakeMetadataObject rs1 = FakeMetadataFactory.createVirtualGroup("xmltest.group.items", xmltest, rsQuery1); //$NON-NLS-1$

		QueryNode rsQuery2 = new QueryNode("xmltest.suppliers", "SELECT concat(stock.suppliers.supplierNum, '') as supplierNum, supplierName, supplierZipCode FROM stock.suppliers, stock.item_supplier WHERE stock.suppliers.supplierNum = stock.item_supplier.supplierNum AND stock.item_supplier.itemNum = ?"); //$NON-NLS-1$ //$NON-NLS-2$
		rsQuery2.addBinding("xmltest.group.items.itemNum"); //$NON-NLS-1$
		FakeMetadataObject rs2 = FakeMetadataFactory.createVirtualGroup("xmltest.suppliers", xmltest, rsQuery2); //$NON-NLS-1$

		QueryNode rsQuery3 = new QueryNode("xmltest.orders", "SELECT concat(stock.orders.orderNum, '') as orderNum, orderName, orderZipCode FROM stock.orders, stock.item_order WHERE stock.orders.orderNum = stock.item_order.orderNum AND stock.item_order.itemNum = ?"); //$NON-NLS-1$ //$NON-NLS-2$
		rsQuery3.addBinding("xmltest.group.items.itemNum"); //$NON-NLS-1$
		FakeMetadataObject rs3= FakeMetadataFactory.createVirtualGroup("xmltest.orders", xmltest, rsQuery3); //$NON-NLS-1$

		// Create virtual elements
		List rsElements1 = FakeMetadataFactory.createElements(rs1, 
			new String[] { "itemNum", "itemName", "itemQuantity", "itemStatus" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });
		
		List rsElements2 = FakeMetadataFactory.createElements(rs2, 
			new String[] { "supplierNum", "supplierName", "supplierZipCode" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
		
		List rsElements3 = FakeMetadataFactory.createElements(rs3, 
			new String[] { "orderNum", "orderName", "orderZipCode" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
	
// =========================================================================================================

		// Create virtual docs
		FakeMetadataObject doc9c= FakeMetadataFactory.createVirtualGroup("xmltest.doc9c", xmltest, createXMLPlanNested2c()); //$NON-NLS-1$
		List docE9c = FakeMetadataFactory.createElements(doc9c, new String[] { "Catalogs", "Catalogs.Catalog", "Catalogs.Catalog.items", "Catalogs.Catalog.items.item", "Catalogs.Catalog.items.item.@ItemID", "Catalogs.Catalog.items.item.Name", "Catalogs.Catalog.items.item.Quantity", "Catalogs.Catalog.items.item.suppliers", "Catalogs.Catalog.items.item.suppliers.supplier", "Catalogs.Catalog.items.item.suppliers.supplier.@SupplierID", "Catalogs.Catalog.items.item.suppliers.supplier.zip", "Catalogs.Catalog.items.item.suppliers.supplier.Name", "Catalogs.Catalog.items.item.orders", "Catalogs.Catalog.items.item.orders.order", "Catalogs.Catalog.items.item.orders.order.@OrderID" ,"Catalogs.Catalog.items.item.orders.order.zip", "Catalogs.Catalog.items.item.orders.order.Name"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$ //$NON-NLS-15$ //$NON-NLS-16$ //$NON-NLS-17$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});
		
		//========================================================

		// Add all objects to the store
		store.addObject(stock);
		
		store.addObject(items);
		store.addObject(item_supplier);
		store.addObject(item_order);
		store.addObject(suppliers);
		store.addObject(orders);
		
		store.addObjects(itemElements);
		store.addObjects(itemSupplierElements);
		store.addObjects(supplierElements);
		store.addObjects(stockOrders);
		store.addObjects(itemOrderElements);

		store.addObject(xmltest);
		store.addObject(rs1);
		store.addObject(rs2);
		store.addObject(rs3);
		
		store.addObjects(rsElements1);
		store.addObjects(rsElements2);
		store.addObjects(rsElements3);

		store.addObject(doc9c);
		store.addObjects(docE9c);
     
		// Create the facade from the store
		return facade;		
	}
	
    public static FakeMetadataFacade exampleMetadata2() { 
        FakeMetadataStore store = new FakeMetadataStore();
        FakeMetadataFacade facade = new FakeMetadataFacade(store);
        
        // Create models
        FakeMetadataObject xqt = FakeMetadataFactory.createPhysicalModel("xqt"); //$NON-NLS-1$
        FakeMetadataObject xqttest = FakeMetadataFactory.createVirtualModel("xqttest");     //$NON-NLS-1$

        // Create physical groups
        FakeMetadataObject xqtGroup = FakeMetadataFactory.createPhysicalGroup("xqt.data", xqt); //$NON-NLS-1$
                
        // Create physical elements
        List xqtData = FakeMetadataFactory.createElements(xqtGroup, 
            new String[] { "intKey", "intNum", "stringNum" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });

        // Create new XML recursion tests virtual groups
        QueryNode xqtDataGroup = new QueryNode("xqttest.xqtData", "SELECT intKey as key, intNum as data, (intKey + 2) as nextKey FROM xqt.data"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject objData = FakeMetadataFactory.createVirtualGroup("xqttest.xqtData", xqttest, xqtDataGroup); //$NON-NLS-1$
        
        QueryNode rsGroup = new QueryNode("xqttest.group", "SELECT key as ID, data as CODE, nextKey as supervisorID FROM xqttest.xqtData"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject objGroup = FakeMetadataFactory.createVirtualGroup("xqttest.group", xqttest, rsGroup); //$NON-NLS-1$
        
        QueryNode rsSupervisor = new QueryNode("xqttest.supervisor", "SELECT key as ID, data as CODE, nextKey as groupID FROM xqttest.xqtData WHERE key = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        rsSupervisor.addBinding("xqttest.group.supervisorID"); //$NON-NLS-1$
        FakeMetadataObject objSupervisor = FakeMetadataFactory.createVirtualGroup("xqttest.supervisor", xqttest, rsSupervisor); //$NON-NLS-1$

        QueryNode rsGroup1 = new QueryNode("xqttest.group1", "SELECT key as ID, data as CODE, nextKey as supervisorID FROM xqttest.xqtData WHERE key = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        rsGroup1.addBinding("xqttest.supervisor.groupID"); //$NON-NLS-1$
        FakeMetadataObject objGroup1 = FakeMetadataFactory.createVirtualGroup("xqttest.group1", xqttest, rsGroup1); //$NON-NLS-1$
        
        // Create virtual elements
        
        List elemXQTData = FakeMetadataFactory.createElements(objData, 
            new String[] { "key", "data", "nextKey" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER });

        List elemGroup = FakeMetadataFactory.createElements(objGroup, 
            new String[] { "ID", "code", "supervisorID" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER });

        List elemSupervisor = FakeMetadataFactory.createElements(objSupervisor, 
            new String[] { "ID", "code", "groupID" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER });

        List elemGroup1 = FakeMetadataFactory.createElements(objGroup1, 
            new String[] { "ID", "code", "supervisorID" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER });
        
        // Create virtual groups
        QueryNode rsQuery = new QueryNode("xqttest.data", "SELECT intKey, intNum, stringNum FROM xqt.data WHERE intKey=13"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject rs = FakeMetadataFactory.createVirtualGroup("xqttest.data", xqttest, rsQuery); //$NON-NLS-1$
        
        QueryNode rsQuery2 = new QueryNode("xqttest.data2", "SELECT intKey, intNum, stringNum FROM xqt.data WHERE intKey = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        rsQuery2.addBinding("xqttest.data.intNum"); //$NON-NLS-1$
        FakeMetadataObject rs2 = FakeMetadataFactory.createVirtualGroup("xqttest.data2", xqttest, rsQuery2); //$NON-NLS-1$

        QueryNode rsQuery3 = new QueryNode("xqttest.data3", "SELECT intKey, intNum, stringNum FROM xqt.data WHERE intKey = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        rsQuery3.addBinding("xqttest.data2.intNum"); //$NON-NLS-1$
        FakeMetadataObject rs3 = FakeMetadataFactory.createVirtualGroup("xqttest.data3", xqttest, rsQuery3); //$NON-NLS-1$

        QueryNode rsQuery4 = new QueryNode("xqttest.data4", "SELECT intKey, intNum, stringNum FROM xqt.data WHERE intKey = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        rsQuery4.addBinding("xqttest.data.intNum"); //$NON-NLS-1$
        FakeMetadataObject rs4 = FakeMetadataFactory.createVirtualGroup("xqttest.data4", xqttest, rsQuery4); //$NON-NLS-1$

        QueryNode rsQuery5 = new QueryNode("xqttest.data5", "SELECT intKey, intNum, stringNum FROM xqt.data WHERE intKey = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        rsQuery5.addBinding("xqttest.data4.intNum"); //$NON-NLS-1$
        FakeMetadataObject rs5 = FakeMetadataFactory.createVirtualGroup("xqttest.data5", xqttest, rsQuery5); //$NON-NLS-1$

        QueryNode rsQuery6 = new QueryNode("xqttest.data6", "SELECT intKey, intNum, stringNum FROM xqt.data WHERE intKey = ?"); //$NON-NLS-1$ //$NON-NLS-2$
        rsQuery6.addBinding("xqttest.data5.intNum"); //$NON-NLS-1$
        FakeMetadataObject rs6 = FakeMetadataFactory.createVirtualGroup("xqttest.data6", xqttest, rsQuery6); //$NON-NLS-1$

        
        QueryNode rsQuery7 = new QueryNode("xqttest.data7", "SELECT intKey, intNum, stringNum FROM xqt.data"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject rs7 = FakeMetadataFactory.createVirtualGroup("xqttest.data7", xqttest, rsQuery7); //$NON-NLS-1$

        QueryNode rsQuery8 = new QueryNode("xqttest.data8", "SELECT intKey, intNum, stringNum FROM xqt.data WHERE intKey < ?"); //$NON-NLS-1$ //$NON-NLS-2$
        rsQuery8.addBinding("xqttest.data7.intNum"); //$NON-NLS-1$
        FakeMetadataObject rs8 = FakeMetadataFactory.createVirtualGroup("xqttest.data8", xqttest, rsQuery8); //$NON-NLS-1$

        // Create virtual elements
        List rsElements = FakeMetadataFactory.createElements(rs, 
            new String[] { "intKey", "intNum", "stringNum" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });

        List rsElements2 = FakeMetadataFactory.createElements(rs2, 
            new String[] { "intKey", "intNum", "stringNum" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });

        List rsElements3 = FakeMetadataFactory.createElements(rs3, 
            new String[] { "intKey", "intNum", "stringNum" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });

        List rsElements4 = FakeMetadataFactory.createElements(rs4, 
            new String[] { "intKey", "intNum", "stringNum" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });

        List rsElements5 = FakeMetadataFactory.createElements(rs5, 
            new String[] { "intKey", "intNum", "stringNum" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });

        List rsElements6 = FakeMetadataFactory.createElements(rs6, 
            new String[] { "intKey", "intNum", "stringNum" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });

        List rsElements7 = FakeMetadataFactory.createElements(rs7, 
            new String[] { "intKey", "intNum", "stringNum" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });

        List rsElements8 = FakeMetadataFactory.createElements(rs8, 
            new String[] { "intKey", "intNum", "stringNum" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.STRING });


        FakeMetadataObject doc1 = FakeMetadataFactory.createVirtualGroup("xqttest.doc1", xqttest,   createXQTPlanRecursive_5988()); //$NON-NLS-1$
        FakeMetadataObject doc1a = FakeMetadataFactory.createVirtualGroup("xqttest.doc1a", xqttest, createXQTPlanRecursive1a_5988()); //$NON-NLS-1$
        FakeMetadataObject doc2 = FakeMetadataFactory.createVirtualGroup("xqttest.doc2", xqttest,   createXQTPlanRecursiveSiblings()); //$NON-NLS-1$
        FakeMetadataObject doc3 = FakeMetadataFactory.createVirtualGroup("xqttest.doc3", xqttest,   createXQTPlanRecursive3_5988()); //$NON-NLS-1$
        FakeMetadataObject doc4 = FakeMetadataFactory.createVirtualGroup("xqttest.doc4", xqttest,   createXQTPlanChoice_6796()); //$NON-NLS-1$
        FakeMetadataObject doc5 = FakeMetadataFactory.createVirtualGroup("xqttest.doc5", xqttest,   createChoiceDefect24651()); //$NON-NLS-1$
        FakeMetadataObject groupDoc = FakeMetadataFactory.createVirtualGroup("xqttest.groupDoc", xqttest,   createGroupDoc()); //$NON-NLS-1$

        List elemGroupDoc = FakeMetadataFactory.createElements(groupDoc, new String[] { "group", "group.pseudoID" /*, etc...*/ }, //$NON-NLS-1$ //$NON-NLS-2$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});
        
        List elemGroupDoc4 = FakeMetadataFactory.createElements(doc4, new String[] { "root", "root.key", "root.key.keys", "root.key.keys.nestedkey", "root.wrapper.key", "root.wrapper.key.keys", "root.wrapper.key.keys.nestedkey"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
                                                               new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});
        
        List elemGroupDoc5 = FakeMetadataFactory.createElements(doc5, new String[] { "root", "root.wrapper.key" }, //$NON-NLS-1$ //$NON-NLS-2$
                                                                new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING});
         
        store.addObject(xqt);
        store.addObject(xqtGroup);
        store.addObjects(xqtData);

        store.addObject(xqttest);
        
        store.addObject(objData);
        store.addObject(objGroup);
        store.addObject(objSupervisor);
        store.addObject(objGroup1);
        store.addObjects(elemXQTData);
        store.addObjects(elemGroup);
        store.addObjects(elemSupervisor);
        store.addObjects(elemGroup1);
        
        store.addObject(rs);
        store.addObject(rs2);
        store.addObject(rs3);
        store.addObject(rs4);
        store.addObject(rs5);
        store.addObject(rs6);
        store.addObject(rs7);
        store.addObject(rs8);
        store.addObjects(rsElements);
        store.addObjects(rsElements2);
        store.addObjects(rsElements3);
        store.addObjects(rsElements4);
        store.addObjects(rsElements5);
        store.addObjects(rsElements6);
        store.addObjects(rsElements7);
        store.addObjects(rsElements8);

        store.addObject(doc1);
        store.addObject(doc1a);
        store.addObject(doc2);
        store.addObject(doc3);
        store.addObject(doc4);
        store.addObject(doc5);
        store.addObject(groupDoc);
        store.addObjects(elemGroupDoc);
        store.addObjects(elemGroupDoc4);
        store.addObjects(elemGroupDoc5);

        return facade;
    }

    public static FakeMetadataFacade exampleMetadataSoap1() {
        FakeMetadataStore store = new FakeMetadataStore();
        FakeMetadataFacade facade = new FakeMetadataFacade(store);
        
        // Create models
        FakeMetadataObject taxReport = FakeMetadataFactory.createPhysicalModel("taxReport"); //$NON-NLS-1$
        FakeMetadataObject xmltest = FakeMetadataFactory.createVirtualModel("xmltest");     //$NON-NLS-1$

        // Create physical groups
        FakeMetadataObject arrayOfItem = FakeMetadataFactory.createPhysicalGroup("taxReport.TaxIDs", taxReport); //$NON-NLS-1$

        // Create physical elements
        List itemElements = FakeMetadataFactory.createElements(arrayOfItem, 
            new String[] { "ID" }, //$NON-NLS-1$
            new String[] {DataTypeManager.DefaultDataTypes.STRING});


        QueryNode rsQuerySoap = new QueryNode("xmltest.group.TaxIDs", "SELECT ID FROM taxReport.TaxIDs"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject rsSoap = FakeMetadataFactory.createVirtualGroup("xmltest.group.TaxIDs", xmltest, rsQuerySoap); //$NON-NLS-1$

        List rsSoapElements = FakeMetadataFactory.createElements(rsSoap, 
        new String[] { "ID"}, //$NON-NLS-1$
        new String[] {DataTypeManager.DefaultDataTypes.STRING});        

        FakeMetadataObject doc_SOAP = FakeMetadataFactory.createVirtualGroup("xmltest.docSoap", xmltest, createXMLPlanSOAP()); //$NON-NLS-1$
        List doc_SOAPE1 = FakeMetadataFactory.createElements(doc_SOAP, new String[] { "TaxReports", "TaxReports.TaxReport", "TaxReports.TaxReport.ArrayOfTaxID","TaxReports.TaxReport.ArrayOfTaxID.TaxID","TaxReports.TaxReport.ArrayOfTaxID.TaxID.ID"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
                                               
        store.addObject(taxReport);
        store.addObject(arrayOfItem);
        store.addObjects(itemElements);

        store.addObject(xmltest);
        store.addObject(rsSoap);
        store.addObjects(rsSoapElements); 
        store.addObject(doc_SOAP);
        store.addObjects(doc_SOAPE1);
        return facade;
    }
	
    private static MappingNode createXQTPlanChoice_6796() {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("root")); //$NON-NLS-1$

        MappingChoiceNode choice = root.addChoiceNode(new MappingChoiceNode(false));
        choice.setSource("xqttest.data7"); //$NON-NLS-1$
        choice.setMaxOccurrs(-1);
        MappingCriteriaNode crit = choice.addCriteriaNode(new MappingCriteriaNode("xqttest.data7.intKey < 10", false)); //$NON-NLS-1$ 
        MappingElement wrapper1 = crit.addChildElement(new MappingElement("wrapper")); //$NON-NLS-1$

        MappingElement key = wrapper1.addChildElement(new MappingElement("key", "xqttest.data7.intKey")); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingElement keys = key.addChildElement(new MappingElement("keys")); //$NON-NLS-1$
        keys.setSource("xqttest.data8"); //$NON-NLS-1$
        keys.setMaxOccurrs(-1);
        keys.addChildElement(new MappingElement("nestedkey", "xqttest.data8.intKey")); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingCriteriaNode wrapper2 = choice.addCriteriaNode(new MappingCriteriaNode(null, true)); 
        
        key = wrapper2.addChildElement( new MappingElement("key", "xqttest.data7.intKey")); //$NON-NLS-1$ //$NON-NLS-2$
        
        keys = key.addChildElement(new MappingElement("keys")); //$NON-NLS-1$
        keys.setSource("xqttest.data8"); //$NON-NLS-1$
        keys.setMaxOccurrs(-1);
        keys.addChildElement(new MappingElement("nestedkey", "xqttest.data8.intKey")); //$NON-NLS-1$ //$NON-NLS-2$
        
        return doc;
    }
    
    private static MappingNode createChoiceDefect24651() {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("root")); //$NON-NLS-1$

        MappingChoiceNode choice = root.addChoiceNode(new MappingChoiceNode(false));
        choice.setSource("xqttest.data7"); //$NON-NLS-1$
        choice.setMaxOccurrs(-1);
        MappingCriteriaNode crit = choice.addCriteriaNode(new MappingCriteriaNode("xqttest.data7.intKey < 10", false)); //$NON-NLS-1$ 
        MappingElement wrapper1 = crit.addChildElement(new MappingElement("wrapper")); //$NON-NLS-1$

        MappingElement key = wrapper1.addChildElement(new MappingElement("key", "xqttest.data7.intKey")); //$NON-NLS-1$ //$NON-NLS-2$
        key.setExclude(true);
                
        return doc;
    }

    /**
     * Method createXQTPlanRecursive.
     * @return Object
     */
    private static MappingNode createXQTPlanRecursive_5988() {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("recursiveTest")); //$NON-NLS-1$
        
        MappingElement src1 = root.addChildElement(new MappingElement("src")); //$NON-NLS-1$
        src1.setSource("xqttest.data"); //$NON-NLS-1$
        
        MappingSequenceNode seq1 = new MappingSequenceNode();
        seq1.addChildElement(new MappingElement("key", "xqttest.data.intKey")); //$NON-NLS-1$ //$NON-NLS-2$
        seq1.addChildElement(new MappingElement("data", "xqttest.data.intNum")); //$NON-NLS-1$ //$NON-NLS-2$
        src1.addSequenceNode(seq1);

        MappingElement src2 = seq1.addChildElement(new MappingElement("srcNested")); //$NON-NLS-1$
        src2.setSource("xqttest.data2"); //$NON-NLS-1$
                
        MappingSequenceNode seq2 = src2.addSequenceNode(new MappingSequenceNode());
        seq2.addChildElement(new MappingElement("key", "xqttest.data2.intKey")); //$NON-NLS-1$ //$NON-NLS-2$
        seq2.addChildElement(new MappingElement("data", "xqttest.data2.intNum")); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingElement recursive = seq2.addChildElement(new MappingRecursiveElement("srcNestedRecursive", "xqttest.data2")); //$NON-NLS-1$ //$NON-NLS-2$
        recursive.setSource("xqttest.data3"); //$NON-NLS-1$
        
        MappingElement recursive2 = seq1.addChildElement(new MappingRecursiveElement("srcRecursive", "xqttest.data")); //$NON-NLS-1$ //$NON-NLS-2$
        recursive2.setSource("xqttest.data4"); //$NON-NLS-1$
        
        return doc;
    }

	/**
	 * Method createXQTPlanRecursive.
	 * @return Object
	 */
    private static MappingNode createXQTPlanRecursive1a_5988() {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("recursiveTest")); //$NON-NLS-1$

        MappingElement src1 = root.addChildElement(new MappingElement("src")); //$NON-NLS-1$
        src1.setSource("xqttest.data"); //$NON-NLS-1$

        MappingSequenceNode seq1 = new MappingSequenceNode();
        seq1.addChildElement(new MappingElement("key", "xqttest.data.intKey")); //$NON-NLS-1$ //$NON-NLS-2$
        seq1.addChildElement(new MappingElement("data", "xqttest.data.intNum")); //$NON-NLS-1$ //$NON-NLS-2$
        src1.addSequenceNode(seq1);

        MappingElement src2 = seq1.addChildElement(new MappingElement("srcNested")); //$NON-NLS-1$
        src2.setSource("xqttest.data2"); //$NON-NLS-1$

        MappingSequenceNode seq2 = src2.addSequenceNode(new MappingSequenceNode());
        seq2.addChildElement(new MappingElement("key", "xqttest.data2.intKey")); //$NON-NLS-1$ //$NON-NLS-2$
        seq2.addChildElement(new MappingElement("data", "xqttest.data2.intNum")); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingElement recursive = seq2.addChildElement(new MappingRecursiveElement("srcRecursive", "xqttest.data2")); //$NON-NLS-1$ //$NON-NLS-2$
        recursive.setSource("xqttest.data3"); //$NON-NLS-1$
        
        MappingElement recursive2 = seq1.addChildElement(new MappingRecursiveElement("srcRecursive", "xqttest.data")); //$NON-NLS-1$ //$NON-NLS-2$
        recursive2.setSource("xqttest.data4"); //$NON-NLS-1$
        
        return doc;
    }

    /**
     * Tests a non-recursive nested mapping class within a recursive mapping class, where
     * all nested "anchor" nodes are named "srcNested".  Test of defect #5988
     * @return Object
     */
    private static MappingNode createXQTPlanRecursive3_5988() {   
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("recursiveTest")); //$NON-NLS-1$

        MappingElement src1 = root.addChildElement(new MappingElement("src")); //$NON-NLS-1$
        src1.setSource("xqttest.data"); //$NON-NLS-1$

        MappingSequenceNode seq1 = src1.addSequenceNode(new MappingSequenceNode());
        seq1.addChildElement(new MappingElement("key", "xqttest.data.intKey")); //$NON-NLS-1$ //$NON-NLS-2$
        seq1.addChildElement(new MappingElement("data", "xqttest.data.intNum")); //$NON-NLS-1$ //$NON-NLS-2$

        MappingElement src2 = seq1.addChildElement(new MappingElement("srcNested")); //$NON-NLS-1$
        src2.setSource("xqttest.data2"); //$NON-NLS-1$
        
        MappingSequenceNode seq2 = src2.addSequenceNode(new MappingSequenceNode());
        seq2.addChildElement(new MappingElement("key", "xqttest.data2.intKey")); //$NON-NLS-1$ //$NON-NLS-2$
        seq2.addChildElement(new MappingElement("data", "xqttest.data2.intNum")); //$NON-NLS-1$ //$NON-NLS-2$

        MappingElement nested = seq1.addChildElement(new MappingRecursiveElement("srcNested", "xqttest.data")); //$NON-NLS-1$ //$NON-NLS-2$
        nested.setSource("xqttest.data4"); //$NON-NLS-1$
        
        return doc;
    }
    
    
    private static MappingNode createXQTPlanRecursiveSiblings() {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("recursiveTest")); //$NON-NLS-1$

        MappingElement src1 = root.addChildElement(new MappingElement("src")); //$NON-NLS-1$
        src1.setSource("xqttest.data"); //$NON-NLS-1$

        MappingSequenceNode seq1 = src1.addSequenceNode(new MappingSequenceNode());
        seq1.addChildElement(new MappingElement("key", "xqttest.data.intKey")); //$NON-NLS-1$ //$NON-NLS-2$
        seq1.addChildElement(new MappingElement("data", "xqttest.data.intNum")); //$NON-NLS-1$ //$NON-NLS-2$

        MappingElement sibiling1 = seq1.addChildElement(new MappingRecursiveElement("srcSibling1", "xqttest.data")); //$NON-NLS-1$ //$NON-NLS-2$
        sibiling1.setSource("xqttest.data2"); //$NON-NLS-1$
        sibiling1.setMaxOccurrs(-1);
        MappingElement sibiling2 = seq1.addChildElement(new MappingRecursiveElement("srcSibling2", "xqttest.data")); //$NON-NLS-1$ //$NON-NLS-2$
        sibiling2.setSource("xqttest.data2");//$NON-NLS-1$
        sibiling2.setMaxOccurrs(-1);
        return doc;
    }

	/**
	 * Method createXMLPlanNested.
	 * @return MappingNode root of mapping doc
	 */
	private static MappingNode createXMLPlanNested() {

        MappingDocument doc = new MappingDocument(true);
        MappingElement root = new MappingElement("Catalogs"); //$NON-NLS-1$
        doc.addChildElement(root);
        
        MappingSequenceNode sequence = new MappingSequenceNode();
        MappingElement cats = sequence.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cats.addChildElement(new MappingElement("Items")); //$NON-NLS-1$
        MappingSequenceNode sequence0 = items.addSequenceNode(new MappingSequenceNode());

        MappingElement item = sequence0.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        MappingSequenceNode sequence1 = item.addSequenceNode(new MappingSequenceNode());
        sequence1.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        sequence1.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$
                
        //NESTED STUFF======================================================================
        MappingElement nestedWrapper = new MappingElement("Suppliers"); //$NON-NLS-1$
        MappingSequenceNode sequence2 = nestedWrapper.addSequenceNode(new MappingSequenceNode());

        MappingElement supplier = sequence2.addChildElement(new MappingElement("Supplier")); //$NON-NLS-1$
        supplier.setSource("xmltest.suppliers"); //$NON-NLS-1$
        supplier.setMaxOccurrs(-1);
        supplier.addAttribute(new MappingAttribute("SupplierID", "xmltest.suppliers.supplierNum"));//$NON-NLS-1$ //$NON-NLS-2$

        
        MappingSequenceNode sequence3 = supplier.addSequenceNode(new MappingSequenceNode());
        sequence3.addChildElement(new MappingElement("Name", "xmltest.suppliers.supplierName")); //$NON-NLS-1$ //$NON-NLS-2$
        sequence3.addChildElement(new MappingElement("Zip", "xmltest.suppliers.supplierZipCode")); //$NON-NLS-1$ //$NON-NLS-2$
        //NESTED STUFF======================================================================
        
        sequence1.addChildElement(nestedWrapper);
        root.addSequenceNode(sequence);
        return doc;  
	}
    
    
    /**
     * for defect 9929
     */
    static MappingNode createXMLPlanNested(String queryGroup) {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = new MappingElement("Catalogs"); //$NON-NLS-1$
        doc.addChildElement(root);
        
        MappingSequenceNode sequence = new MappingSequenceNode();
        MappingElement cats = sequence.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cats.addChildElement(new MappingElement("Items")); //$NON-NLS-1$
        MappingSequenceNode sequence0 = items.addSequenceNode(new MappingSequenceNode());

        MappingElement item = sequence0.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items");         //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$

        MappingSequenceNode sequence1 = item.addSequenceNode(new MappingSequenceNode());
        sequence1.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        sequence1.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$

        //NESTED STUFF======================================================================
        MappingElement nestedWrapper = new MappingElement("Suppliers"); //$NON-NLS-1$
        MappingSequenceNode sequence2 = nestedWrapper.addSequenceNode(new MappingSequenceNode());

        MappingElement supplier = sequence2.addChildElement(new MappingElement("Supplier")); //$NON-NLS-1$
        supplier.setSource(queryGroup);
        supplier.setMaxOccurrs(-1);
        supplier.addAttribute(new MappingAttribute("SupplierID", queryGroup+".supplierNum"));//$NON-NLS-1$ //$NON-NLS-2$

        MappingSequenceNode sequence3 = supplier.addSequenceNode(new MappingSequenceNode());
        sequence3.addChildElement(new MappingElement("Name", queryGroup+".supplierName")); //$NON-NLS-1$ //$NON-NLS-2$
        sequence3.addChildElement(new MappingElement("Zip", queryGroup+".supplierZipCode")); //$NON-NLS-1$ //$NON-NLS-2$
        //NESTED STUFF======================================================================
       
        sequence1.addChildElement(nestedWrapper);
        root.addSequenceNode(sequence);
        return doc;  
    }    

    /**
     * for defect 12260
     */
    private static MappingNode createXMLPlanCorrelatedSubqueryTransform() {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$
        
        MappingSequenceNode sequence = new MappingSequenceNode();
        MappingElement cats = sequence.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cats.addChildElement(new MappingElement("Items")); //$NON-NLS-1$
        MappingSequenceNode sequence0 = items.addSequenceNode(new MappingSequenceNode());

        MappingElement item = sequence0.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.itemsWithNumSuppliers"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.itemsWithNumSuppliers.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$

        MappingSequenceNode sequence1 = item.addSequenceNode(new MappingSequenceNode());
        sequence1.addChildElement(new MappingElement("Name", "xmltest.group.itemsWithNumSuppliers.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        sequence1.addChildElement(new MappingElement("Quantity", "xmltest.group.itemsWithNumSuppliers.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$
        sequence1.addChildElement(new MappingElement("numSuppliers", "xmltest.group.itemsWithNumSuppliers.numSuppliers")); //$NON-NLS-1$ //$NON-NLS-2$

        root.addSequenceNode(sequence);
        return doc;  
    }

    private static MappingNode createXMLPlan_9893() {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Root")); //$NON-NLS-1$
        
        MappingSequenceNode seq = root.addSequenceNode(new MappingSequenceNode());
        
        MappingElement node = seq.addChildElement(new MappingElement("ItemName", "xmltest.group.items.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        node.setSource("xmltest.group.items"); //$NON-NLS-1$
        node.setMaxOccurrs(-1);
        node.setMaxOccurrs(MappingNodeConstants.CARDINALITY_UNBOUNDED.intValue());
        return doc;  
    }

    /** 
     * DEFECT 8373
     */
    private static Object createXMLPlan_defect8373() {
        
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$
        MappingElement cats = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cats.addChildElement(new MappingElement("Items")); //$NON-NLS-1$

        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.items8373"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.items8373.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.items8373.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Quantity", "xmltest.items8373.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$

        return doc;  
    }

    /** 
     * DEFECT 8373
     */
    private static Object createXMLPlan_defect8373a() {        
        MappingDocument doc = new MappingDocument(true);
        
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$
        MappingElement cats = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cats.addChildElement(new MappingElement("Items")); //$NON-NLS-1$

        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.items8373a"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.setStagingTables(Arrays.asList(new String[] {"xmltest.items8373"})); //$NON-NLS-1$
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.items8373a.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.items8373a.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Quantity", "xmltest.items8373a.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$

        return doc;  
    }
    
    /** 
     * DEFECT 8373
     */
    private static Object createXMLPlan_defect8373b() {
        
        MappingDocument doc = new MappingDocument(true);
        
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$
        MappingElement cats = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cats.addChildElement(new MappingElement("Items")); //$NON-NLS-1$

        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.items8373b"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.setStagingTables(Arrays.asList(new String[] {"xmltest.group.items"})); //$NON-NLS-1$
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.items8373b.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.items8373b.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Quantity", "xmltest.items8373b.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$

        return doc;
    }    
    
    private static MappingNode createXMLPlanNested2() {
        
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$
        
        MappingElement cats = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cats.addChildElement(new MappingElement("Items")); //$NON-NLS-1$

        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$
        
        //NESTED STUFF======================================================================
        MappingElement nestedWrapper = item.addChildElement(new MappingElement("Suppliers")); //$NON-NLS-1$
        
        MappingElement supplier = nestedWrapper.addChildElement(new MappingElement("Supplier")); //$NON-NLS-1$
        supplier.setSource("xmltest.suppliers"); //$NON-NLS-1$
        supplier.setMaxOccurrs(-1);
        supplier.addAttribute(new MappingAttribute("SupplierID", "xmltest.suppliers.supplierNum")); //$NON-NLS-1$ //$NON-NLS-2$
        supplier.addChildElement(new MappingElement("Name","xmltest.suppliers.supplierName")); //$NON-NLS-1$ //$NON-NLS-2$
        supplier.addChildElement(new MappingElement("Zip", "xmltest.suppliers.supplierZipCode")); //$NON-NLS-1$ //$NON-NLS-2$
                        
        MappingElement ordersWrapper = supplier.addChildElement(new MappingElement("Orders")); //$NON-NLS-1$
        
        MappingElement order = ordersWrapper.addChildElement(new MappingElement("Order")); //$NON-NLS-1$
        order.setSource("xmltest.orders"); //$NON-NLS-1$
        order.setMaxOccurrs(-1);
        order.addAttribute(new MappingAttribute("OrderID", "xmltest.orders.orderNum")); //$NON-NLS-1$ //$NON-NLS-2$
        order.addChildElement(new MappingElement("OrderDate", "xmltest.orders.orderDate")); //$NON-NLS-1$ //$NON-NLS-2$
        order.addChildElement(new MappingElement("OrderQuantity", "xmltest.orders.orderQty")); //$NON-NLS-1$ //$NON-NLS-2$

        order.addChildElement(new MappingElement("OrderStatus", "xmltest.orders.orderStatus")) //$NON-NLS-1$ //$NON-NLS-2$
            .setMinOccurrs(0);                
        //NESTED STUFF======================================================================
        return doc;  
    }

	/** nested with sibling*/
	private MappingNode createXMLPlanNested2c() {
        
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$
        
        MappingElement cats = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cats.addChildElement(new MappingElement("Items")); //$NON-NLS-1$

        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$

		//NESTED STUFF======================================================================
        MappingElement nestedWrapper = item.addChildElement(new MappingElement("Suppliers")); //$NON-NLS-1$
        
        MappingElement supplier = nestedWrapper.addChildElement(new MappingElement("Supplier")); //$NON-NLS-1$
        supplier.setSource("xmltest.suppliers"); //$NON-NLS-1$
        supplier.setMaxOccurrs(-1);
        supplier.addAttribute(new MappingAttribute("SupplierID", "xmltest.suppliers.supplierNum")); //$NON-NLS-1$ //$NON-NLS-2$
        supplier.addChildElement(new MappingElement("Name","xmltest.suppliers.supplierName")); //$NON-NLS-1$ //$NON-NLS-2$
        supplier.addChildElement(new MappingElement("Zip", "xmltest.suppliers.supplierZipCode")); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingElement ordersWrapper = item.addChildElement(new MappingElement("Orders")); //$NON-NLS-1$
        MappingElement order = ordersWrapper.addChildElement(new MappingElement("Order")); //$NON-NLS-1$
        order.setSource("xmltest.orders"); //$NON-NLS-1$
        order.setMaxOccurrs(-1);
        order.addAttribute(new MappingAttribute("OrderID", "xmltest.orders.orderNum")); //$NON-NLS-1$ //$NON-NLS-2$
        order.addChildElement(new MappingElement("Name", "xmltest.orders.orderName")); //$NON-NLS-1$ //$NON-NLS-2$
        order.addChildElement(new MappingElement("Zip", "xmltest.orders.orderZipCode")); //$NON-NLS-1$ //$NON-NLS-2$
		//NESTED STUFF======================================================================
		return doc;  
	}


    private static MappingNode createXMLPlanNested2a() {
        
        MappingDocument doc = new MappingDocument(true);
        
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$

        root.setStagingTables(Arrays.asList(new String[] {"tempGroup.orders", "tempGroup.orders2"})); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingElement cats = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cats.addChildElement(new MappingElement("Items")); //$NON-NLS-1$

        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);    
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$
        
        //NESTED STUFF======================================================================
        MappingElement nestedWrapper = item.addChildElement(new MappingElement("Suppliers")); //$NON-NLS-1$
        
        MappingElement supplier = nestedWrapper.addChildElement(new MappingElement("Supplier")); //$NON-NLS-1$
        supplier.setSource("xmltest.suppliers"); //$NON-NLS-1$
        supplier.setMaxOccurrs(-1);
        supplier.addAttribute(new MappingAttribute("SupplierID", "xmltest.suppliers.supplierNum")); //$NON-NLS-1$ //$NON-NLS-2$
        supplier.addChildElement(new MappingElement("Name","xmltest.suppliers.supplierName")); //$NON-NLS-1$ //$NON-NLS-2$
        supplier.addChildElement(new MappingElement("Zip", "xmltest.suppliers.supplierZipCode")); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingElement ordersWrapper = supplier.addChildElement(new MappingElement("Orders")); //$NON-NLS-1$        
        
        MappingElement order = ordersWrapper.addChildElement(new MappingElement("Order")); //$NON-NLS-1$
        order.setSource("xmltest.ordersA"); //$NON-NLS-1$
        order.setMaxOccurrs(-1);
        order.addAttribute(new MappingAttribute("OrderID", "xmltest.ordersA.orderNum")); //$NON-NLS-1$ //$NON-NLS-2$
        order.addChildElement(new MappingElement("OrderDate", "xmltest.ordersA.orderDate")); //$NON-NLS-1$ //$NON-NLS-2$
        order.addChildElement(new MappingElement("OrderQuantity", "xmltest.ordersA.orderQty")); //$NON-NLS-1$ //$NON-NLS-2$

        order.addChildElement(new MappingElement("OrderStatus", "xmltest.ordersA.orderStatus")) //$NON-NLS-1$ //$NON-NLS-2$
            .setMinOccurrs(0);                
        //NESTED STUFF======================================================================
        return doc;  
    }

    // for doc 9b - test temp group w/ bindings
    private static MappingNode createXMLPlanNested2b() {
        
        MappingDocument doc = new MappingDocument(true);
        
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$
        root.setStagingTables(Arrays.asList(new String[] {"tempGroup.orders", "tempGroup.orders2"})); //$NON-NLS-1$ //$NON-NLS-2$      
        
        MappingElement cats = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cats.addChildElement(new MappingElement("Items")); //$NON-NLS-1$

        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$
        
        //NESTED STUFF======================================================================
        MappingElement nestedWrapper = item.addChildElement(new MappingElement("Suppliers")); //$NON-NLS-1$

        MappingElement supplier = nestedWrapper.addChildElement(new MappingElement("Supplier")); //$NON-NLS-1$
        supplier.setSource("xmltest.suppliers"); //$NON-NLS-1$
        supplier.setMaxOccurrs(-1);
        supplier.addAttribute(new MappingAttribute("SupplierID", "xmltest.suppliers.supplierNum")); //$NON-NLS-1$ //$NON-NLS-2$
        supplier.addChildElement(new MappingElement("Name","xmltest.suppliers.supplierName")); //$NON-NLS-1$ //$NON-NLS-2$
        supplier.addChildElement(new MappingElement("Zip", "xmltest.suppliers.supplierZipCode")); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingElement ordersWrapper = supplier.addChildElement(new MappingElement("Orders")); //$NON-NLS-1$        
        
        MappingElement order = ordersWrapper.addChildElement(new MappingElement("Order")); //$NON-NLS-1$
        order.setSource("xmltest.ordersB"); //$NON-NLS-1$
        
        order.setStagingTables(Arrays.asList(new String[] {"tempGroup.orders3B"})); //$NON-NLS-1$ 
        order.addAttribute(new MappingAttribute("OrderID", "xmltest.ordersB.orderNum")); //$NON-NLS-1$ //$NON-NLS-2$
        order.addChildElement(new MappingElement("OrderDate", "xmltest.ordersB.orderDate")); //$NON-NLS-1$ //$NON-NLS-2$
        order.addChildElement(new MappingElement("OrderQuantity", "xmltest.ordersB.orderQty")); //$NON-NLS-1$ //$NON-NLS-2$

        order.addChildElement(new MappingElement("OrderStatus", "xmltest.ordersB.orderStatus")) //$NON-NLS-1$ //$NON-NLS-2$
            .setMinOccurrs(0);                
        //NESTED STUFF======================================================================
        return doc;
    }

    public static MappingDocument createXMLPlanNestedWithChoice() {
        MappingCriteriaNode critNode = new MappingCriteriaNode();
        MappingElement defaltElement = critNode.addChildElement(new MappingElement("OtherOrder")); //$NON-NLS-1$
        defaltElement.addAttribute(new MappingAttribute("OrderID", "xmltest.orders.orderNum"));//$NON-NLS-1$ //$NON-NLS-2$                
        return baseXMLPlanNestedWithLookupChoice("xmltest.orders.orderStatus = 'processing'", critNode); //$NON-NLS-1$
    }

    private static MappingNode createXMLPlanNestedWithChoiceFor5266() {
        MappingCriteriaNode critNode = new MappingCriteriaNode("xmltest.orders.orderStatus = 'shipped'", true); //$NON-NLS-1$
        return baseXMLPlanNestedWithLookupChoice("xmltest.orders.orderStatus = 'processing'", critNode); //$NON-NLS-1$        
    }
    
    private static MappingNode createXMLPlanNestedWithLookupChoice() {
        MappingCriteriaNode critNode = new MappingCriteriaNode(); 
        MappingElement defaltElement = critNode.addChildElement(new MappingElement("OtherOrder"));//$NON-NLS-1$
        defaltElement.addAttribute(new MappingAttribute("OrderID", "xmltest.orders.orderNum"));//$NON-NLS-1$ //$NON-NLS-2$                
        return baseXMLPlanNestedWithLookupChoice("lookup('stock.items', 'itemNum', 'itemName', xmltest.orders.orderStatus) = 'processing'", critNode); //$NON-NLS-1$
    }
    
    private static MappingDocument baseXMLPlanNestedWithLookupChoice(String criteria, MappingCriteriaNode defaultNode) {        
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$
        
        MappingSequenceNode seq1 = root.addSequenceNode(new MappingSequenceNode());
        MappingElement cats = seq1.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cats.addChildElement(new MappingElement("Items")); //$NON-NLS-1$
        
        MappingSequenceNode seq2 = items.addSequenceNode(new MappingSequenceNode());
        
        MappingElement item = seq2.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingSequenceNode seq3 = item.addSequenceNode(new MappingSequenceNode());        
        seq3.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        seq3.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$
        
        //NESTED STUFF======================================================================
        MappingElement suppliers = seq3.addChildElement(new MappingElement("Suppliers")); //$NON-NLS-1$
        
        MappingSequenceNode seq4 = suppliers.addSequenceNode(new MappingSequenceNode());        
        MappingElement supplier = seq4.addChildElement(new MappingElement("Supplier")); //$NON-NLS-1$
        supplier.setSource("xmltest.suppliers"); //$NON-NLS-1$
        supplier.setMaxOccurrs(-1);
        supplier.addAttribute(new MappingAttribute("SupplierID", "xmltest.suppliers.supplierNum")); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingSequenceNode seq5 = supplier.addSequenceNode(new MappingSequenceNode());
        seq5.addChildElement(new MappingElement("Name","xmltest.suppliers.supplierName")); //$NON-NLS-1$ //$NON-NLS-2$
        seq5.addChildElement(new MappingElement("Zip", "xmltest.suppliers.supplierZipCode")); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingElement ordersWrapper = seq5.addChildElement(new MappingElement("ProcessingOrders")); //$NON-NLS-1$
        
        MappingChoiceNode choice = ordersWrapper.addChoiceNode(new MappingChoiceNode(false));
        choice.setSource("xmltest.orders"); //$NON-NLS-1$
        choice.setMaxOccurrs(-1);
        MappingCriteriaNode crit = choice.addCriteriaNode(new MappingCriteriaNode(criteria, false)); 
        MappingElement order = crit.addChildElement(new MappingElement("Order")); //$NON-NLS-1$
        order.addAttribute(new MappingAttribute("OrderID", "xmltest.orders.orderNum")); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingSequenceNode seq6 = order.addSequenceNode(new MappingSequenceNode());      
        seq6.addChildElement(new MappingElement("OrderDate", "xmltest.orders.orderDate")); //$NON-NLS-1$ //$NON-NLS-2$
        seq6.addChildElement(new MappingElement("OrderQuantity", "xmltest.orders.orderQty")); //$NON-NLS-1$ //$NON-NLS-2$
        
        choice.addCriteriaNode(defaultNode);
        //NESTED STUFF======================================================================
        return doc;        
    }


    private static MappingNode createTestAttributePlan() {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("FixedValueTest")); //$NON-NLS-1$
        
        //sequence
        MappingSequenceNode seq = root.addSequenceNode(new MappingSequenceNode());       
        
        MappingElement wrapper = seq.addChildElement(new MappingElement("wrapper")); //$NON-NLS-1$
        wrapper.setSource("xmltest.group.items"); //$NON-NLS-1$
        wrapper.setMaxOccurrs(-1);
        MappingAttribute att = new MappingAttribute("fixedAttr"); //$NON-NLS-1$
        att.setValue("fixed attribute"); //$NON-NLS-1$
        wrapper.addAttribute(att);
        
        //sequence
        MappingSequenceNode seq1 = wrapper.addSequenceNode(new MappingSequenceNode());
        seq1.addChildElement(new MappingElement("key","xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        seq1.addChildElement(new MappingElement("fixed")) //$NON-NLS-1$
            .setValue("fixed value"); //$NON-NLS-1$
        
        return doc;
    }

    private static Object createUpdateTestDoc() {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("UpdateTest")); //$NON-NLS-1$
        
        MappingSequenceNode seq = root.addSequenceNode(new MappingSequenceNode());
        seq.setSource("xmltest.updateTest"); //$NON-NLS-1$
        seq.addChildElement(new MappingElement("data", "xmltest.updateTest.rowCount")); //$NON-NLS-1$ //$NON-NLS-2$        
        return doc;
    }

    private static MappingNode createXMLPlanWithComment(){
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Root")); //$NON-NLS-1$
        
        root.addCommentNode(new MappingCommentNode("Comment1")); //$NON-NLS-1$
        MappingElement node = root.addChildElement(new MappingElement("Something")); //$NON-NLS-1$
        node.addCommentNode(new MappingCommentNode("Comment2")); //$NON-NLS-1$
        return doc;
    }

    public static MappingDocument createXMLMappingBoundingNode() {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs"));//$NON-NLS-1$
        MappingElement cat = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$
       
        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMinOccurrs(1);
        item.setMaxOccurrs(2);
        
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName").setNillable(true)); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$        
        return doc;
    }
    
    public static MappingDocument createXMLMappingNode(boolean format) {
        MappingDocument doc = new MappingDocument(format);
        doc.addChildElement(createXMLPlan1Unformatted(false, 1));
        return doc;
    }
    
    private static MappingNode createXMLPlan2(boolean format, boolean testNillable, int cardinality ) {
        MappingDocument doc = new MappingDocument(format);
        doc.addChildElement(createXMLPlan1Unformatted(testNillable, cardinality));
        return doc;
    }    
    
    private static MappingNode createXMLPlanSOAP() {

        Namespace namespace = new Namespace("ORG", "http://www.mm.org/dummy"); //$NON-NLS-1$ //$NON-NLS-2$

        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("TaxReports", namespace)); //$NON-NLS-1$
        
        root.setNamespaces(new Namespace[] {namespace});
        
        MappingElement report = root.addChildElement(new MappingElement("TaxReport", namespace)); //$NON-NLS-1$
        report.setNamespaces(new Namespace[] {namespace});
               
        Namespace xsiNamespace = new Namespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"); //$NON-NLS-1$ //$NON-NLS-2$
        Namespace soapNamespace = new Namespace("SOAP-ENC", "http://schemas.xmlsoap.org/soap/encoding/"); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingElement taxIds = report.addChildElement(new MappingElement("ArrayOfTaxID", namespace)); //$NON-NLS-1$
        taxIds.setMinOccurrs(0);
        taxIds.setNamespaces(new Namespace[] {xsiNamespace, soapNamespace});
        
        MappingAttribute xsiType = new MappingAttribute("type", xsiNamespace); //$NON-NLS-1$
        xsiType.setValue(namespace.getPrefix()+":ArrayOfTaxIDType"); //$NON-NLS-1$        
        xsiType.setOptional(true);
        taxIds.addAttribute(xsiType);
        
        MappingAttribute arrayType = new MappingAttribute("arrayType", soapNamespace); //$NON-NLS-1$
        arrayType.setValue( namespace.getPrefix()+":TaxIDType[]"); //$NON-NLS-1$
        arrayType.setOptional(true);
        taxIds.addAttribute(arrayType);
          
        MappingElement taxId = taxIds.addChildElement(new MappingElement("TaxID", namespace)); //$NON-NLS-1$
        taxId.setSource("xmltest.group.TaxIDs"); //$NON-NLS-1$
        taxId.setMaxOccurrs(-1);
        MappingAttribute xsiType2 = new MappingAttribute("type", xsiNamespace); //$NON-NLS-1$
        xsiType2.setValue(namespace.getPrefix()+":TaxIDType"); //$NON-NLS-1$
        xsiType2.setOptional(true);
        taxId.addAttribute(xsiType2);

        taxId.addChildElement(new MappingElement("ID", "xmltest.group.TaxIDs.ID")); //$NON-NLS-1$ //$NON-NLS-2$
        return doc;
    }    
    
    
    private static MappingElement createXMLPlan1Unformatted( boolean testNillable, int cardinality ) {

        MappingElement root = new MappingElement("Catalogs");//$NON-NLS-1$
        MappingElement cat = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$
        items.setNillable(testNillable);
        items.setMinOccurrs(cardinality);
            
        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName").setNillable(true)); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$        
        return root;                                
    }

    private static MappingNode createXMLPlanDefect13617() {

        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$
        
        MappingElement cat = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$
       
        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.setMinOccurrs(0);
        
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
            .setMinOccurrs(0);
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")) //$NON-NLS-1$ //$NON-NLS-2$        
            .setMinOccurrs(0);
 
        return doc;                                
    }     
    

    private static MappingNode createXMLPlan2(int numChoices, int numDefaultChoice, boolean exception_on_Default) {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$
        MappingElement cat = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$

        // ======================================================================
        // CHOICE NODE STUFF
        //choice node, non-visual, so it has no name        
        MappingChoiceNode choice = items.addChoiceNode(new MappingChoiceNode(exception_on_Default));
        choice.setSource("xmltest.group.items"); //$NON-NLS-1$
        choice.setMaxOccurrs(-1);
        if (numChoices >= 1){
            MappingCriteriaNode item = getChoiceChild("Item", "xmltest.group.items.itemName='Lamp'", numDefaultChoice == 1); //$NON-NLS-1$ //$NON-NLS-2$
            choice.addCriteriaNode(item);
        }
        if (numChoices >= 2){
            MappingCriteriaNode item = getChoiceChild("Item2", "xmltest.group.items.itemName='Screwdriver'", numDefaultChoice == 2); //$NON-NLS-1$ //$NON-NLS-2$
            choice.addCriteriaNode(item);
        }
        if (numChoices >= 3){
            MappingCriteriaNode item = getChoiceChild("Item3", "xmltest.group.items.itemName='Goat'", numDefaultChoice == 3); //$NON-NLS-1$ //$NON-NLS-2$
            choice.addCriteriaNode(item);
        }
        if (numDefaultChoice > numChoices){
            MappingCriteriaNode item = getChoiceChild("ItemDefault", null, true); //$NON-NLS-1$
            choice.addCriteriaNode(item);
        }
        
        // ======================================================================
        return doc;                                
    }

    private static MappingCriteriaNode getChoiceChild(String name, String criteria, boolean defalt){
        MappingCriteriaNode crit = new MappingCriteriaNode(criteria, defalt);
        
        MappingElement item = crit.addChildElement(new MappingElement(name));
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
                .setNillable(true);
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$
        return crit;
    }    

    private static MappingDocument createXMLPlanWithDefaults() {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$

        MappingElement cat = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$

        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
                .setNillable(true);
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")) //$NON-NLS-1$ //$NON-NLS-2$
            .setDefaultValue("1"); //$NON-NLS-1$

        return doc;                                
    }

    private static MappingNode createXMLPlanUltraAdvanced() {

        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$

        MappingElement cat = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$

        //choice node, non-visual, so it has no name
        boolean exceptionOnDefault = false;
        MappingChoiceNode choice = items.addChoiceNode(new MappingChoiceNode(exceptionOnDefault));
        choice.setSource("xmltest.group.items"); //$NON-NLS-1$
        choice.setMaxOccurrs(-1);
        MappingCriteriaNode crit1 = choice.addCriteriaNode(new MappingCriteriaNode("xmltest.group.items.itemStatus = 'okay'", false)); //$NON-NLS-1$       
        MappingElement item = crit1.addChildElement(new MappingElement("Item")); //$NON-NLS-1$         
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
                .setNillable(true);
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")) //$NON-NLS-1$ //$NON-NLS-2$
            .setDefaultValue("0"); //$NON-NLS-1$

        MappingCriteriaNode crit2= choice.addCriteriaNode(new MappingCriteriaNode("xmltest.group.items.itemStatus = 'discontinued'", false)); //$NON-NLS-1$ 
        MappingElement discontinued = crit2.addChildElement(new MappingElement("DiscontinuedItem")); //$NON-NLS-1$         
        discontinued.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        discontinued.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
                .setNillable(true);
        discontinued.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")) //$NON-NLS-1$ //$NON-NLS-2$
            .setDefaultValue("0"); //$NON-NLS-1$
        
        MappingCriteriaNode crit3 = choice.addCriteriaNode(new MappingCriteriaNode()); 
        MappingElement unknown = crit3.addChildElement(new MappingElement("StatusUnknown"));//$NON-NLS-1$
        unknown.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        unknown.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
                .setNillable(true);
        unknown.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")) //$NON-NLS-1$ //$NON-NLS-2$
            .setDefaultValue("0"); //$NON-NLS-1$
                
        choice.addCriteriaNode(new MappingCriteriaNode("xmltest.group.items.itemStatus = 'something'", false)).setExclude(true); //$NON-NLS-1$ 
        choice.addCriteriaNode(new MappingCriteriaNode("xmltest.group.items.itemStatus = 'something'", false)).setExclude(true); //$NON-NLS-1$ 
        
        return doc;        
    }
    
  

    private static MappingNode createXMLPlanUltraAdvancedExceptionOnDefault() {

        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$

        MappingElement cat = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$

        //choice node, non-visual, so it has no name
        boolean exceptionOnDefault = true;
        MappingChoiceNode choice = items.addChoiceNode(new MappingChoiceNode(exceptionOnDefault));
        choice.setSource("xmltest.group.items"); //$NON-NLS-1$
        choice.setMaxOccurrs(-1);
        MappingCriteriaNode crit1 = choice.addCriteriaNode(new MappingCriteriaNode("xmltest.group.items.itemStatus = 'okay'", false)); //$NON-NLS-1$     
        MappingElement item = crit1.addChildElement(new MappingElement("Item")); //$NON-NLS-1$        
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
                .setNillable(true);
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")) //$NON-NLS-1$ //$NON-NLS-2$
            .setDefaultValue("0"); //$NON-NLS-1$

        MappingCriteriaNode crit2 = choice.addCriteriaNode(new MappingCriteriaNode("xmltest.group.items.itemStatus = 'discontinued'", false)); //$NON-NLS-1$ 
        MappingElement discontinued = crit2.addChildElement(new MappingElement("DiscontinuedItem")); //$NON-NLS-1$ 
        discontinued.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        discontinued.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
                .setNillable(true);
        discontinued.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")) //$NON-NLS-1$ //$NON-NLS-2$
            .setDefaultValue("0"); //$NON-NLS-1$
                        
        choice.addCriteriaNode(new MappingCriteriaNode("xmltest.group.items.itemStatus = 'discontinued'", false)).setExclude(true); //$NON-NLS-1$ 
        
        return doc; 
    }

    //advanced tests of namespace declarations, use and scope; also fixed values
    private static MappingNode createXMLPlanAdvanced() {
        //add to previous example
        MappingDocument doc = createXMLPlanWithDefaults();
        MappingElement root = (MappingElement)doc.getRootNode();

        Namespace nameSpaceOne = new Namespace("duh", "http://www.duh.org/duh"); //$NON-NLS-1$ //$NON-NLS-2$
        Namespace nameSpaceTwo = new Namespace("duh2", "http://www.duh2.org/duh2"); //$NON-NLS-1$ //$NON-NLS-2$
        Namespace nameSpaceThree = new Namespace("duh", "http://www.duh.org/duh/duh"); //$NON-NLS-1$ //$NON-NLS-2$
        Namespace nameSpaceFour = new Namespace(MappingNodeConstants.DEFAULT_NAMESPACE_PREFIX, ""); //$NON-NLS-1$                      
        Namespace nameSpaceFive = new Namespace(MappingNodeConstants.DEFAULT_NAMESPACE_PREFIX, "http://www.default.org/default"); //$NON-NLS-1$        
                        
        MappingElement fakeChildOfRoot = new MappingElement("Fake", nameSpaceOne); //$NON-NLS-1$
        fakeChildOfRoot.setValue("fixed constant value"); //$NON-NLS-1$                       
        fakeChildOfRoot.addNamespace(nameSpaceOne);
        fakeChildOfRoot.addNamespace(nameSpaceTwo);
        fakeChildOfRoot.addNamespace(nameSpaceFive);
        root.addChildElement(fakeChildOfRoot);

        MappingElement fakeChild2 = fakeChildOfRoot.addChildElement(new MappingElement("FakeChild2", nameSpaceOne)); //$NON-NLS-1$ 

        //add fakeChild2a with default namespace
        MappingElement fakeChild2a = fakeChild2.addChildElement(new MappingElement("FakeChild2a")); //$NON-NLS-1$
        fakeChild2a.setValue("another fixed constant value"); //$NON-NLS-1$
 
        MappingElement fakeChild3 = fakeChildOfRoot.addChildElement(new MappingElement("FakeChild3")); //$NON-NLS-1$
        fakeChild3.addNamespace(nameSpaceThree);
        fakeChild3.addNamespace(nameSpaceFour);        

        MappingAttribute fakeAtt = new MappingAttribute("FakeAtt", nameSpaceOne); //$NON-NLS-1$
        fakeAtt.setValue("fixed att value"); //$NON-NLS-1$
        fakeChild3.addAttribute(fakeAtt);
                
        return doc;                                
    }


    /**
     * Method createXMLPlanNested.
     * @return MappingNode root of mapping doc
     */
    private static MappingNode createXMLPlanMultipleDocs() {

        MappingDocument doc = new MappingDocument(true);
        
        MappingElement root = doc.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        root.setSource("xmltest.group.items"); //$NON-NLS-1$
        root.setMaxOccurrs(-1);
        root.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
         
        MappingSequenceNode sequence1 = root.addSequenceNode(new MappingSequenceNode());
        sequence1.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        sequence1.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$

        //NESTED STUFF======================================================================
        MappingElement nestedWrapper = sequence1.addChildElement(new MappingElement("Suppliers")); //$NON-NLS-1$
        MappingSequenceNode sequence2 =  nestedWrapper.addSequenceNode(new MappingSequenceNode());
        
        MappingElement supplier = sequence2.addChildElement(new MappingElement("Supplier")); //$NON-NLS-1$
        supplier.setSource("xmltest.suppliers"); //$NON-NLS-1$
        supplier.setMaxOccurrs(-1);
        supplier.addAttribute(new MappingAttribute("SupplierID", "xmltest.suppliers.supplierNum")); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingSequenceNode sequence3 = supplier.addSequenceNode(new MappingSequenceNode());
        sequence3.addChildElement(new MappingElement("Name", "xmltest.suppliers.supplierName")); //$NON-NLS-1$ //$NON-NLS-2$        
        sequence3.addChildElement(new MappingElement("Zip", "xmltest.suppliers.supplierZipCode")); //$NON-NLS-1$ //$NON-NLS-2$
        return doc;  
    }

    private static MappingNode createXMLPlanRecursive(boolean useRecursiveCriteria, int recursionLimit, boolean exceptionOnLimit) {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("OrgHierarchy")); //$NON-NLS-1$
        
        MappingElement ceo = root.addChildElement(new MappingElement("Employee")); //$NON-NLS-1$
        ceo.setSource("xmltest.employees"); //$NON-NLS-1$
        ceo.setMinOccurrs(0);
        ceo.setMaxOccurrs(-1);
        ceo.addAttribute(new MappingAttribute("ID", "xmltest.employees.employeeNum")); //$NON-NLS-1$ //$NON-NLS-2$

        MappingSequenceNode sequence = ceo.addSequenceNode(new MappingSequenceNode());
        sequence.addChildElement(new MappingElement("FirstName", "xmltest.employees.firstName")); //$NON-NLS-1$ //$NON-NLS-2$
        sequence.addChildElement(new MappingElement("LastName", "xmltest.employees.lastName")); //$NON-NLS-1$ //$NON-NLS-2$
        MappingElement subordinates = sequence.addChildElement(new MappingElement("Subordinates")); //$NON-NLS-1$
        
        //recursive piece
        MappingRecursiveElement employee = (MappingRecursiveElement)subordinates.addChildElement(new MappingRecursiveElement("Employee", "xmltest.employees")); //$NON-NLS-1$ //$NON-NLS-2$
        employee.setSource("xmltest.employeesRecursive"); //$NON-NLS-1$
        employee.setMinOccurrs(0);
        employee.setMaxOccurrs(-1);

        if (useRecursiveCriteria){
            employee.setCriteria("xmltest.employees.employeeNum = '04'"); //$NON-NLS-1$
        }        
        employee.setRecursionLimit(recursionLimit > 0 ? recursionLimit:10, exceptionOnLimit);
        return doc;  
    }

    /*
     * Recursion root mapping class is anchored at sequence node instead of "Employee" node
     */
    private static MappingNode createXMLPlanRecursiveA(boolean useRecursiveCriteria, int recursionLimit, boolean exceptionOnLimit) {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("OrgHierarchy")); //$NON-NLS-1$

        MappingSequenceNode seq0 = root.addSequenceNode(new MappingSequenceNode());
        seq0.setSource("xmltest.employees"); //$NON-NLS-1$
        seq0.setMinOccurrs(0);
        seq0.setMaxOccurrs(-1);
        
        MappingElement ceo = seq0.addChildElement(new MappingElement("Employee")); //$NON-NLS-1$
        ceo.addAttribute(new MappingAttribute("ID", "xmltest.employees.employeeNum")); //$NON-NLS-1$ //$NON-NLS-2$

        MappingSequenceNode seq = ceo.addSequenceNode(new MappingSequenceNode());
        seq.addChildElement(new MappingElement("FirstName", "xmltest.employees.firstName")); //$NON-NLS-1$ //$NON-NLS-2$
        seq.addChildElement(new MappingElement("LastName", "xmltest.employees.lastName")); //$NON-NLS-1$ //$NON-NLS-2$
        MappingElement subordinates = seq.addChildElement(new MappingElement("Subordinates")); //$NON-NLS-1$

        //recursive piece
        MappingRecursiveElement employee = (MappingRecursiveElement)subordinates.addChildElement(new MappingRecursiveElement("Subordinate", "xmltest.employees")); //$NON-NLS-1$ //$NON-NLS-2$
        employee.setSource("xmltest.employeesRecursive");  //$NON-NLS-1$
        employee.setMinOccurrs(0);
        employee.setMaxOccurrs(-1);

        if (useRecursiveCriteria){
            employee.setCriteria("xmltest.employees.employeeNum = '04'"); //$NON-NLS-1$
        }
        employee.setRecursionLimit(recursionLimit > 0 ? recursionLimit:10, exceptionOnLimit);
        return doc;  
    }
    
    
    private static MappingNode createXMLPlanRecursiveStaging(boolean useRecursiveCriteria, int recursionLimit, boolean exceptionOnLimit) {
        
        MappingDocument doc = new MappingDocument(true);
        
        MappingElement root = doc.addChildElement(new MappingElement("OrgHierarchy")); //$NON-NLS-1$
        root.setStagingTables(Arrays.asList(new String[] {"xmltest.doc19temp"})); //$NON-NLS-1$

        MappingElement ceo = root.addChildElement(new MappingElement("Employee")); //$NON-NLS-1$
        ceo.setSource("xmltest.employeesDoc19"); //$NON-NLS-1$
        ceo.setMinOccurrs(0);
        ceo.setMaxOccurrs(-1);
        ceo.addAttribute(new MappingAttribute("ID", "xmltest.employeesDoc19.employeeNum")); //$NON-NLS-1$ //$NON-NLS-2$

        MappingSequenceNode seq = ceo.addSequenceNode(new MappingSequenceNode());
        seq.addChildElement(new MappingElement("FirstName", "xmltest.employeesDoc19.firstName")); //$NON-NLS-1$ //$NON-NLS-2$
        seq.addChildElement(new MappingElement("LastName", "xmltest.employeesDoc19.lastName")); //$NON-NLS-1$ //$NON-NLS-2$
        MappingElement subordinates = seq.addChildElement(new MappingElement("Subordinates")); //$NON-NLS-1$

        //recursive piece
        MappingRecursiveElement employee = (MappingRecursiveElement)subordinates.addChildElement(new MappingRecursiveElement("Employee", "xmltest.employeesDoc19")); //$NON-NLS-1$ //$NON-NLS-2$
        employee.setSource("xmltest.employeesRecursiveDoc19"); //$NON-NLS-1$
        employee.setMinOccurrs(0);
        employee.setMaxOccurrs(-1);

        if (useRecursiveCriteria){
            employee.setCriteria("xmltest.employeesDoc19.employeeNum = '04'"); //$NON-NLS-1$
        }
        employee.setRecursionLimit(recursionLimit > 0 ? recursionLimit:10, exceptionOnLimit);

        return doc;  
    }
    
    private static MappingNode createXMLPlanRecursive2(boolean useRecursiveCriteria, int recursionLimit, boolean exceptionOnLimit) {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Employees")); //$NON-NLS-1$

        MappingElement ceo = root.addChildElement(new MappingElement("Employee")); //$NON-NLS-1$
        ceo.setSource("xmltest.employees2"); //$NON-NLS-1$
        ceo.setMinOccurrs(0);
        ceo.setMaxOccurrs(-1);
        ceo.addAttribute(new MappingAttribute("ID", "xmltest.employees2.employeeNum")); //$NON-NLS-1$ //$NON-NLS-2$

        MappingSequenceNode seq = ceo.addSequenceNode(new MappingSequenceNode());
        seq.addChildElement(new MappingElement("FirstName", "xmltest.employees2.firstName")); //$NON-NLS-1$ //$NON-NLS-2$
        seq.addChildElement(new MappingElement("LastName", "xmltest.employees2.lastName")); //$NON-NLS-1$ //$NON-NLS-2$

        //recursive piece
        MappingRecursiveElement employee = (MappingRecursiveElement)seq.addChildElement(new MappingRecursiveElement("Supervisor", "xmltest.employees2")); //$NON-NLS-1$ //$NON-NLS-2$
        employee.setSource("xmltest.employees2Recursive"); //$NON-NLS-1$
        employee.setMinOccurrs(0);
        employee.setMaxOccurrs(-1);

        if (useRecursiveCriteria){
            employee.setCriteria("xmltest.employees2.employeeNum = '04'"); //$NON-NLS-1$
        }
        employee.setRecursionLimit(recursionLimit > 0 ? recursionLimit:10, exceptionOnLimit);
        return doc;  
    }

    //this is for testing how "optional" XML elements are included/
    //excluded from the result document
    private static MappingNode createXMLPlan_defect8917() {
        
        Namespace namespace1 = new Namespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$

        root.addNamespace(namespace1);

        {       
        //FRAGMENT 1
        MappingElement cat = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$
        
        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
            .setNillable(true);        
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        {
        //FRAGMENT 2
        MappingElement cat = root.addChildElement(new MappingElement("OptionalCatalog")); //$NON-NLS-1$
        cat.setMinOccurrs(0);
        
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$
        
        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
        	.setNillable(true); 
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        {
        //FRAGMENT 3
        MappingElement cat = root.addChildElement(new MappingElement("OptionalCatalog2")); //$NON-NLS-1$
        cat.setMinOccurrs(0);
        
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$
        
        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
            .setNillable(true);               
        }        
        
        {
        //FRAGMENT 4
        MappingElement cat = root.addChildElement(new MappingElement("OptionalCatalog3")); //$NON-NLS-1$
        cat.setMinOccurrs(0);        
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$
        
        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        } 
        
        {
        //FRAGMENT 5
        MappingElement cat = root.addChildElement(new MappingElement("Catalog4")); //$NON-NLS-1$
        MappingElement items = cat.addChildElement(new MappingElement("OptionalItems")); //$NON-NLS-1$
        items.setMinOccurrs(0);
        
        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        }                 
        
        {
        //FRAGMENT 6            
        MappingElement cat = root.addChildElement(new MappingElement("Catalog5")); //$NON-NLS-1$
        MappingElement items = cat.addChildElement(new MappingElement("OptionalItems")); //$NON-NLS-1$
        items.setMinOccurrs(0);
        
        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.addChildElement(new MappingElement("FixedName")) //$NON-NLS-1$ 
            .setValue("Nugent"); //$NON-NLS-1$
        }        

        {
        //FRAGMENT 7            
        MappingElement cat = root.addChildElement(new MappingElement("Catalog6")); //$NON-NLS-1$
        MappingElement items = cat.addChildElement(new MappingElement("OptionalItems")); //$NON-NLS-1$
        items.setMinOccurrs(0);
        
        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.addChildElement(new MappingElement("EmptyName")); //$NON-NLS-1$ 
        }        
        return doc;                                
    }

    /*
     * Test of identically named nodes
     */
    private static MappingNode createXMLPlan_defect9446() {
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$

        MappingElement cat = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$        
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$        
        MappingElement item = items.addChildElement(new MappingElement("Item")); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addAttribute(new MappingAttribute("XXXXX", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("XXXXX", "xmltest.group.items.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("XXXXX", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$
        return doc;                                
    }
    
    private static MappingNode createXMLPlan_defect_9530() {        
        Namespace namespace = new Namespace("mm", "http://www.duh.org/duh"); //$NON-NLS-1$ //$NON-NLS-2$
        Namespace namespace2 = new Namespace("mm", "http://www.duh2.org/duh2"); //$NON-NLS-1$ //$NON-NLS-2$
        Namespace namespace3 = new Namespace("mm2", "http://www.duh3.org/duh3"); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs", namespace)); //$NON-NLS-1$
        root.addNamespace(namespace);

        MappingElement cat = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$        
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$        
        
        MappingElement item = items.addChildElement(new MappingElement("Item", namespace)); //$NON-NLS-1$
        item.setSource("xmltest.group.items"); //$NON-NLS-1$
        item.setMaxOccurrs(-1);
        item.addNamespace(namespace2);
        item.addNamespace(namespace3);

        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")); //$NON-NLS-1$ //$NON-NLS-2$
        return doc;                              
    }    
    
    private static MappingNode createGroupDoc() {
        MappingDocument doc = new MappingDocument(true);
        
        MappingElement root = doc.addChildElement(new MappingElement("group")); //$NON-NLS-1$
        root.setSource("xqttest.group"); //$NON-NLS-1$
        root.setMinOccurrs(0);
        root.setMaxOccurrs(-1);
        
        MappingAttribute attr = new MappingAttribute("pseudoID", "xqttest.group.ID"); //$NON-NLS-1$ //$NON-NLS-2$         
        attr.setExclude(true);
        root.addAttribute(attr);

        MappingSequenceNode sequence = root.addSequenceNode(new MappingSequenceNode());
        sequence.addChildElement(new MappingElement("ID", "xqttest.group.ID")); //$NON-NLS-1$ //$NON-NLS-2$
        sequence.addChildElement(new MappingElement("code", "xqttest.group.Code")); //$NON-NLS-1$ //$NON-NLS-2$

        MappingElement supervisor = sequence.addChildElement(new MappingElement("supervisor")); //$NON-NLS-1$
        supervisor.setSource("xqttest.supervisor"); //$NON-NLS-1$
        supervisor.setMinOccurrs(0);
        supervisor.setMaxOccurrs(-1);

        MappingSequenceNode sequence1 = supervisor.addSequenceNode(new MappingSequenceNode());
        sequence1.addChildElement(new MappingElement("ID", "xqttest.supervisor.ID")); //$NON-NLS-1$ //$NON-NLS-2$
        sequence1.addChildElement(new MappingElement("code", "xqttest.supervisor.Code")); //$NON-NLS-1$ //$NON-NLS-2$
        
        MappingRecursiveElement group1 = (MappingRecursiveElement)sequence1.addChildElement(new MappingRecursiveElement("group", "xqttest.group")); //$NON-NLS-1$ //$NON-NLS-2$
        group1.setSource("xqttest.group1"); //$NON-NLS-1$
        group1.setMinOccurrs(0);
        group1.setMaxOccurrs(-1);
        return doc;         
    }

     
    // Helper to create a list of elements - used in creating sample data
    private static List createElements(List elementIDs) { 
        List elements = new ArrayList();
        for(int i=0; i<elementIDs.size(); i++) {
            FakeMetadataObject elementID = (FakeMetadataObject) elementIDs.get(i);            
            ElementSymbol element = new ElementSymbol(elementID.getName());
            elements.add(element);
        }        
        
        return elements;
    }    

    public static FakeDataManager exampleDataManager(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);
        
            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", "Lamp", new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "002", "Screwdriver", new Integer(100), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "003", "Goat", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    }                    

    public static FakeDataManager exampleDataManager15117(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);
        
            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", " Lamp ", new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "002", "  Screw  driver  ", new Integer(100), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "003", " Goat ", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    }     

    /** unusual characters in text */
    public static FakeDataManager exampleDataManager15117a(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);
        
            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", "\t \n\r", new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "002", "  >Screw< \n driver  &", new Integer(100), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "003", " >>\rGoat ", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    }    
    
    public static FakeDataManager exampleDataManager14905(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);
        
            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { " ", " ", new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "  ", "  ", new Integer(100), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { " ", " ", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    }     
    
    public static FakeDataManager exampleDataManager13617(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);
        
            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", "Lamp", new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "002", "Screwdriver", new Integer(100), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "003", "Goat", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "004", null, new Integer(1), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ 
                    } );    

        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    }     
    
    public static FakeDataManager exampleDataManagerNested(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$

            // Group stock.supplier
            FakeMetadataObject groupID2 = (FakeMetadataObject) metadata.getGroupID("stock.suppliers"); //$NON-NLS-1$

            // Group stock.orders
            FakeMetadataObject groupID3 = (FakeMetadataObject) metadata.getGroupID("stock.orders"); //$NON-NLS-1$

            // Group stock.item_supplier
            FakeMetadataObject groupID1_2join = (FakeMetadataObject) metadata.getGroupID("stock.item_supplier"); //$NON-NLS-1$

            // Group stock.employees
            FakeMetadataObject groupEmployees = (FakeMetadataObject) metadata.getGroupID("stock.employees"); //$NON-NLS-1$

            // Items
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);

            // Supplier
            elementIDs = metadata.getElementIDsInGroupID(groupID2);
            List supplierElementSymbols = createElements(elementIDs);

            // Orders
            elementIDs = metadata.getElementIDsInGroupID(groupID3);
            List ordersElementSymbols = createElements(elementIDs);

            // Item_supplier
            elementIDs = metadata.getElementIDsInGroupID(groupID1_2join);
            List itemSupplierElementSymbols = createElements(elementIDs);

            // Employees
            elementIDs = metadata.getElementIDsInGroupID(groupEmployees);
            List employeeSymbols = createElements(elementIDs);
        
            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", "Lamp", new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "002", "Screwdriver", new Integer(100), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "003", "Goat", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

            dataMgr.registerTuples(
                groupID1_2join,
                itemSupplierElementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", "51" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "001", "52" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "001", "53" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "001", "56" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "002", "54" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "002", "55" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "002", "56" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "003", "56" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    } );    


            dataMgr.registerTuples(
                groupID2,
                supplierElementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "51", "Chucky", "11111" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "52", "Biff's Stuff", "22222" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "53", "AAAA", "33333" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "54", "Nugent Co.", "44444" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "55", "Zeta", "55555" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "56", "Microsoft", "66666" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

            dataMgr.registerTuples(
                groupID3,
                ordersElementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "1", "002", "54", "Nugent Co.", "10/23/01", new Integer(5), "complete" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    Arrays.asList( new Object[] { "2", "001", "52", "Biff's Stuff", "12/31/01", new Integer(87), "complete" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    Arrays.asList( new Object[] { "3", "003", "56", "Microsoft", "02/31/02", new Integer(12), "complete" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    Arrays.asList( new Object[] { "4", "003", "56", "Microsoft", "05/31/02", new Integer(9), "processing" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    Arrays.asList( new Object[] { "5", "002", "56", "Microsoft", "06/01/02", new Integer(87), "complete" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    Arrays.asList( new Object[] { "6", "002", "56", "Microsoft", "07/01/02", new Integer(1), null } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "7", "002", "56", "bad data, shouldn't see", "07/01/02", new Integer(1), "complete" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    } );    

            dataMgr.registerTuples(
                groupEmployees,
                employeeSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "01", "1", null, "Ted", "Nugent" } ), //ceo, Nugent Co. //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    Arrays.asList( new Object[] { "02", "1", "01", "Bill", "Squier" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "03", "1", "01", "John", "Smith" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "04", "1", "02", "Leland", "Sklar" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "05", "1", "03", "Kevin", "Moore" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "06", "1", "04", "John", "Zorn" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "07", "2", null, "Geoff", "Tate" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    Arrays.asList( new Object[] { "08", "2", "07", "Les", "Claypool" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "09", "2", "08", "Meat", "Loaf" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "10", "2", "08", "Keith", "Sweat" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "11", "1", "06", "Mike", "Patton" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "12", "1", "06", "Devin", "Townsend" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "13", "1", "11", "Puffy", "Bordin" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    } );    



        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    }                    

	private FakeDataManager exampleDataManagerNestedWithSibling(FakeMetadataFacade metadata) {
		FakeDataManager dataMgr = new FakeDataManager();
    
		try { 
			// Group stock.items
			FakeMetadataObject groupID1 = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$

			// Group stock.supplier
			FakeMetadataObject groupID2 = (FakeMetadataObject) metadata.getGroupID("stock.suppliers"); //$NON-NLS-1$

			// Group stock.orders
			FakeMetadataObject groupID3 = (FakeMetadataObject) metadata.getGroupID("stock.orders"); //$NON-NLS-1$

			// Group stock.item_supplier
			FakeMetadataObject groupID1_2join = (FakeMetadataObject) metadata.getGroupID("stock.item_supplier"); //$NON-NLS-1$

			// Group stock.item_order
			FakeMetadataObject groupID1_3join = (FakeMetadataObject) metadata.getGroupID("stock.item_order"); //$NON-NLS-1$

			// Items
			List elementIDs = metadata.getElementIDsInGroupID(groupID1);
			List elementSymbols = createElements(elementIDs);

			// Supplier
			elementIDs = metadata.getElementIDsInGroupID(groupID2);
			List supplierElementSymbols = createElements(elementIDs);

			// Orders
			elementIDs = metadata.getElementIDsInGroupID(groupID3);
			List ordersElementSymbols = createElements(elementIDs);

			// Item_supplier
			elementIDs = metadata.getElementIDsInGroupID(groupID1_2join);
			List itemSupplierElementSymbols = createElements(elementIDs);

			// Item_order
			elementIDs = metadata.getElementIDsInGroupID(groupID1_3join);
			List itemOrderElementSymbols = createElements(elementIDs);
        
			dataMgr.registerTuples(
				groupID1,
				elementSymbols,
                
				new List[] { 
					Arrays.asList( new Object[] { "001", "Lamp", new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "002", "Screwdriver", new Integer(100), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "003", "Goat", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					} );    

			dataMgr.registerTuples(
				groupID1_2join,
				itemSupplierElementSymbols,
                
				new List[] { 
					Arrays.asList( new Object[] { "001", "51" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					Arrays.asList( new Object[] { "001", "52" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					Arrays.asList( new Object[] { "001", "53" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					Arrays.asList( new Object[] { "001", "56" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					Arrays.asList( new Object[] { "002", "54" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					Arrays.asList( new Object[] { "002", "55" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					Arrays.asList( new Object[] { "002", "56" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					Arrays.asList( new Object[] { "003", "56" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					} );    


			dataMgr.registerTuples(
				groupID2,
				supplierElementSymbols,
                
				new List[] { 
					Arrays.asList( new Object[] { "51", "Chucky", "11111" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "52", "Biff's Stuff", "22222" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "53", "AAAA", "33333" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "54", "Nugent Co.", "44444" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "55", "Zeta", "55555" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "56", "Microsoft", "66666" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					} );    

			dataMgr.registerTuples(
				groupID3,
				ordersElementSymbols,
                
				new List[] { 
					Arrays.asList( new Object[] { "1", "KMart", "12345" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "2", "Sun", "94040" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "3", "Cisco", "94041" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "4", "Doc", "94042" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "5", "Excite", "21098" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "6", "Yahoo", "94043" } ),  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "7", "Inktomi", "94044" } ),  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					} );    

			dataMgr.registerTuples(
				groupID1_3join,
				itemOrderElementSymbols,
                
				new List[] { 
					Arrays.asList( new Object[] { "001", "1" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					Arrays.asList( new Object[] { "001", "2" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					Arrays.asList( new Object[] { "001", "3" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					Arrays.asList( new Object[] { "001", "4" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					Arrays.asList( new Object[] { "002", "5" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					Arrays.asList( new Object[] { "002", "6" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					Arrays.asList( new Object[] { "003", "7" } ),         //$NON-NLS-1$ //$NON-NLS-2$
					} );    



		} catch(Throwable e) { 
			e.printStackTrace();
			fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
		}
        
		return dataMgr;
	}                    

    /**
     * Returned with some null values in the tuples, to test default/fixed attributes of nodes
     * as well as nillable nodes
     */
    private FakeDataManager exampleDataManagerWithNulls(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);
        
            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", null, new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "002", "Screwdriver", null, "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "003", "Goat", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    }                    

    public static FakeDataManager exampleDataManagerForSoap1(FakeMetadataFacade metadata, boolean makeEmpty) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("taxReport.TaxIDs"); //$NON-NLS-1$
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);
        
            List[] tuples = null;
            if (makeEmpty){
                tuples = new List[0];
            } else {
                tuples = new List[] { 
                    Arrays.asList( new Object[] { "1"} ),         //$NON-NLS-1$
                    Arrays.asList( new Object[] { "2" } ),         //$NON-NLS-1$
                    Arrays.asList( new Object[] { "3" } ),         //$NON-NLS-1$
                    };
            }
        
            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                tuples );    

        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    } 

    /** data has a null value */
    private FakeDataManager exampleDataManager_8917(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);
        
            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", null, new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    } );    

        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    } 

    /** data has a NON-EMPTY WHITESPACE string */
    private FakeDataManager exampleDataManager_8917a(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);
        
            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", " ", new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    }

    /** data has an EMPTY STRING */
    private FakeDataManager exampleDataManager_8917b(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);
        
            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", "", new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    }    
    
	/**
	 * Duplicate records in data
	 * @param metadata
	 * @return FakeDataManager
	 */
	private FakeDataManager exampleDataManagerWithDuplicates(FakeMetadataFacade metadata) {
		FakeDataManager dataMgr = new FakeDataManager();
    
		try { 
			// Group stock.items
			FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
			List elementIDs = metadata.getElementIDsInGroupID(groupID);
			List elementSymbols = createElements(elementIDs);
        
			dataMgr.registerTuples(
				groupID,
				elementSymbols,
                
				new List[] { 
					Arrays.asList( new Object[] { "001", "Goat", new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "002", "Screwdriver",new Integer(100), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "003", "Goat", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					} );    

		} catch(Throwable e) { 
			e.printStackTrace();
			fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
		}
        
		return dataMgr;
	}                    

	/**
	 *
	 * Duplicate records in data to test more than two order by elements at the same depth
	 * @param metadata
	 * @return FakeDataManager
     */
	private FakeDataManager exampleDataManagerWithDuplicates1(FakeMetadataFacade metadata) {
		FakeDataManager dataMgr = new FakeDataManager();
    
		try { 
			// Group stock.items
			FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
			List elementIDs = metadata.getElementIDsInGroupID(groupID);
			List elementSymbols = createElements(elementIDs);
        
			dataMgr.registerTuples(
				groupID,
				elementSymbols,
                
				new List[] { 
					Arrays.asList( new Object[] { "001", "Goat", new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "003", "Screwdriver",new Integer(100), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					Arrays.asList( new Object[] { "001", "Goat", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					} );    

		} catch(Throwable e) { 
			e.printStackTrace();
			fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
		}
        
		return dataMgr;
	}                   
		
    /**
     * Deluxe example
     */
    private FakeDataManager exampleDataManagerDuJour(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);

            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", null, new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "002", "Screwdriver", null, "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "003", "Goat", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "004", "Flux Capacitor", new Integer(2), "discontinued" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "005", "Milkshake", new Integer(88), null } ), //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "006", "Feta Matrix", new Integer(0), "discontinued" } ) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    }   

    public static FakeDataManager exampleXQTDataManager(FakeMetadataFacade metadata) throws Exception {
        FakeDataManager dataMgr = new FakeDataManager();
    
        // Group stock.items
        FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("xqt.data"); //$NON-NLS-1$
        List elementIDs = metadata.getElementIDsInGroupID(groupID);
        List xqtData = createElements(elementIDs);
    
        dataMgr.registerTuples(
            groupID,
            xqtData,
            
            new List[] { 
                Arrays.asList( new Object[] { new Integer(1),  new Integer(-2), "-2" } ),         //$NON-NLS-1$
                Arrays.asList( new Object[] { new Integer(2),  new Integer(-1), null } ),        
                Arrays.asList( new Object[] { new Integer(3),  null,            "0" } ),         //$NON-NLS-1$
                Arrays.asList( new Object[] { new Integer(4),  new Integer(1),  "1" } ),         //$NON-NLS-1$
                Arrays.asList( new Object[] { new Integer(5),  new Integer(2),  "2" } ),         //$NON-NLS-1$
                Arrays.asList( new Object[] { new Integer(6),  new Integer(3),  "3" } ),         //$NON-NLS-1$
                Arrays.asList( new Object[] { new Integer(7),  new Integer(4),  "4" } ),         //$NON-NLS-1$
                Arrays.asList( new Object[] { new Integer(8),  new Integer(5),  null } ),        
                Arrays.asList( new Object[] { new Integer(9),  null,            "6" } ),         //$NON-NLS-1$
                Arrays.asList( new Object[] { new Integer(10), new Integer(7),  "7" } ),         //$NON-NLS-1$
                Arrays.asList( new Object[] { new Integer(11), new Integer(8),  "8" } ),         //$NON-NLS-1$
                Arrays.asList( new Object[] { new Integer(12), new Integer(9),  "9" } ),         //$NON-NLS-1$
                Arrays.asList( new Object[] { new Integer(13), new Integer(10), "10" } ),         //$NON-NLS-1$
                } );    

        return dataMgr;
    }                    

    public static Command helpGetCommand(String sql, QueryMetadataInterface metadata) throws QueryParserException, QueryResolverException, MetaMatrixComponentException, QueryValidatorException { 
        QueryParser parser = new QueryParser();
        ParseInfo info = new ParseInfo();
        info.allowDoubleQuotedVariable = true;
        Command command = parser.parseCommand(sql, info);
        QueryResolver.resolveCommand(command, metadata);
        command = QueryRewriter.rewrite(command, null, metadata, null);
        return command;
    }

    static XMLPlan helpTestProcess(String sql, String expectedDoc, FakeMetadataFacade metadata, FakeDataManager dataMgr) throws Exception{
        return helpTestProcess(sql, expectedDoc, metadata, dataMgr, true, MetaMatrixComponentException.class, null);
    }

    static XMLPlan helpTestProcess(String sql, String expectedDoc, FakeMetadataFacade metadata, FakeDataManager dataMgr, final boolean shouldSucceed, Class expectedException, final String shouldFailMsg) throws Exception{

        return helpTestProcess(sql, expectedDoc, metadata, dataMgr, shouldSucceed, expectedException, shouldFailMsg, new DefaultCapabilitiesFinder());
    }

    static XMLPlan helpTestProcess(String sql, String expectedDoc, FakeMetadataFacade metadata, FakeDataManager dataMgr, final boolean shouldSucceed, Class expectedException, final String shouldFailMsg, CapabilitiesFinder capFinder) throws Exception{
        Command command = helpGetCommand(sql, metadata);

        if (shouldSucceed){
            
            AnalysisRecord analysisRecord = new AnalysisRecord(false, false, DEBUG);
            CommandContext planningContext = new CommandContext(); //this should be the same as the processing context, but that's not easy to do
            
            XMLPlan plan = (XMLPlan)QueryOptimizer.optimizePlan(command, metadata, null, capFinder, analysisRecord, planningContext);
            
            if(DEBUG) {
                System.out.println(analysisRecord.getDebugLog());
            }
            
            // Verify we can get the child plans without error
            plan.getChildPlans();            

            // Process twice, to test reset and clone methods
            for (int i=1; i<=2; i++) {
                BufferManager bufferMgr = BufferManagerFactory.getStandaloneBufferManager();                
                CommandContext context = new CommandContext("pID", "TestConn", "testUser", null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                context.setProcessDebug(DEBUG);
                QueryProcessor processor = new QueryProcessor(plan, context, bufferMgr, dataMgr);
    	
                while(true) {
                    try {
                        processor.process();
                        break;
                    } catch(BlockedException e) {
                        // retry
                    }
                }
            
                //int count = bufferMgr.getFinalRowCount(tsID);
                //assertEquals("Incorrect number of records: ", 1, count); //$NON-NLS-1$
                
                TupleSource ts = bufferMgr.getTupleSource(processor.getResultsID());
                List row = ts.nextTuple();
                assertEquals("Incorrect number of columns: ", 1, row.size()); //$NON-NLS-1$
               

                XMLType result = (XMLType)row.get(0);
                String actualDoc = result.getString();
                
                bufferMgr.removeTupleSource(processor.getResultsID());
                
                if(DEBUG) {
                    System.out.println("expectedDoc = \n" + expectedDoc); //$NON-NLS-1$
                    System.out.println("actualDoc = \n" + actualDoc); //$NON-NLS-1$
                }
                //assertEquals("XML doc mismatch: ", expectedDoc, actualDoc); //$NON-NLS-1$
                compareDocuments(expectedDoc, actualDoc);
                //Test reset, clone methods
                if (i==1) {
                    plan.reset();
                    plan = (XMLPlan)plan.clone();
                }
            }
            return plan;
        } 
        Exception expected = null;
        AnalysisRecord analysisRecord = new AnalysisRecord(false, false, DEBUG);                                              
        try{
            XMLPlan plan = (XMLPlan)QueryOptimizer.optimizePlan(command, metadata, null, new DefaultCapabilitiesFinder(), analysisRecord, null);
    
            BufferManager bufferMgr = BufferManagerFactory.getStandaloneBufferManager();
            CommandContext context = new CommandContext("pID", null, null, null, null);                                                                 //$NON-NLS-1$
            QueryProcessor processor = new QueryProcessor(plan, context, bufferMgr, dataMgr);
            processor.process();
        } catch (Exception e){
            if (expectedException.isInstance(e)){
                expected = e;
            } else {
                throw e;
            }
        } finally {
            if(DEBUG) {
                System.out.println(analysisRecord.getDebugLog());
            }                
        }
        
        assertNotNull(shouldFailMsg, expected);
        return null;
    }

	public static void compareDocuments(String expectedDoc, String actualDoc) {
		StringTokenizer tokens1 = new StringTokenizer(expectedDoc, "\r\n"); //$NON-NLS-1$
		StringTokenizer tokens2 = new StringTokenizer(actualDoc, "\n");//$NON-NLS-1$
		while(tokens1.hasMoreTokens()){
			String token1 = tokens1.nextToken().trim();
			if(!tokens2.hasMoreTokens()){
				fail("XML doc mismatch: expected=" + token1 + "\nactual=none");//$NON-NLS-1$ //$NON-NLS-2$
			}
			String token2 = tokens2.nextToken().trim();
			assertEquals("XML doc mismatch: ", token1, token2); //$NON-NLS-1$
		}
		if(tokens2.hasMoreTokens()){
			fail("XML doc mismatch: expected=none\nactual=" + tokens2.nextToken().trim());//$NON-NLS-1$
		}
	}

	private void helpTestProcess(String sql, String[] expectedDocs, FakeMetadataFacade metadata, FakeDataManager dataMgr) throws Exception{
        Command command = helpGetCommand(sql, metadata);
        AnalysisRecord analysisRecord = new AnalysisRecord(false, false, DEBUG);                                              
        XMLPlan plan = (XMLPlan)QueryOptimizer.optimizePlan(command, metadata, null, new DefaultCapabilitiesFinder(), analysisRecord, null);
        if(DEBUG) {
            System.out.println(analysisRecord.getDebugLog());
        }
        
        BufferManager bufferMgr = BufferManagerFactory.getStandaloneBufferManager();
        CommandContext context = new CommandContext("pID", null, null, null, null);                                 //$NON-NLS-1$
        QueryProcessor processor = new QueryProcessor(plan, context, bufferMgr, dataMgr);
        processor.process();
        
       int count = bufferMgr.getFinalRowCount(processor.getResultsID());
       assertEquals("Incorrect number of records: ", expectedDocs.length, count); //$NON-NLS-1$
        
        TupleSource ts = bufferMgr.getTupleSource(processor.getResultsID());
        for (int i=0; i<expectedDocs.length; i++){        
            List row = ts.nextTuple();
            if(row.isEmpty()){
            	continue;
            }
            assertEquals("Incorrect number of columns: ", 1, row.size()); //$NON-NLS-1$
            XMLType result = (XMLType)row.get(0);
            String actualDoc = result.getString();
                 
            //assertEquals("XML doc result # " + i +" mismatch: ", expectedDocs[i], actualDoc); //$NON-NLS-1$ //$NON-NLS-2$
            compareDocuments(expectedDocs[i], actualDoc);
        }
        bufferMgr.removeTupleSource(processor.getResultsID());
    }    

    // =============================================================================================
    // T E S T S 
    // =============================================================================================

    public void test1() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    public void testOrderBy1() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 ORDER BY Catalogs.Catalog.Items.Item.Quantity ASC", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testOrderBy1a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT \"xml\" FROM xmltest.doc1 ORDER BY Catalogs.Catalog.Items.Item.Quantity ASC", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    public void testOrderBy1b() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT xmltest.doc1.xml FROM xmltest.doc1 ORDER BY Catalogs.Catalog.Items.Item.Quantity ASC", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
               
    public void testOrderBy2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 ORDER BY Catalogs.Catalog.Items.Item.Quantity DESC", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    public void testOrderBy3() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 ORDER BY Catalogs.Catalog.Items.Item.ItemID DESC, Catalogs.Catalog.Items.Item.Suppliers.Supplier.SupplierID ASC", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }   
    
    public void testOrderBy3a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 ORDER BY Catalogs.Catalog.Items.Item.ItemID ASC, Catalogs.Catalog.Items.Item.Suppliers.Supplier.SupplierID DESC", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }      
    
    public void testOrderBy4() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 WHERE ItemID='001' AND Quantity < 60 ORDER BY ItemID", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }     
     
    public void testOrderBy5() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
         String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
          
        helpTestProcess("SELECT * FROM xmltest.doc1 ORDER BY ItemID", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    } 
    
    public void testOrderBy6() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>12/31/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>05/31/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>9</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>processing</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>02/31/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>12</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
            
        
        helpTestProcess("SELECT * FROM xmltest.doc9 ORDER BY Catalogs.Catalog.Items.Item.ItemID ASC, Catalogs.Catalog.Items.Item.Suppliers.Supplier.SupplierID ASC, OrderID DESC", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }   
   
    //order by with temp group at the root    
    public void testOrderBy7() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        helpTestProcess("SELECT * FROM xmltest.doc9a ORDER BY ItemID DESC", EXPECTED_ORDERED_DOC9A, metadata, dataMgr);         //$NON-NLS-1$
    }   
           
    //order by with multiple elements and criteria with long name, short name doesn't work
    public void testOrderBy8() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>12/31/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +   //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>05/31/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>9</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>processing</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>02/31/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>12</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
            
        
        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE Catalogs.Catalog.Items.Item.Quantity < 60 ORDER BY Catalogs.Catalog.Items.Item.ItemID ASC, Catalogs.Catalog.Items.Item.Suppliers.Supplier.SupplierID ASC, OrderID DESC", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }   

    /*    
    public void testOrderBy9() throws Exception {
        FakeMetadataFacade metadata = exampleMetadata();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = "";
          
        boolean shouldSucceed = false;
        Class expectedException = QueryResolverException.class;
        String shouldFailMsg = "Unable to resolve element: Quantity";

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE Quantity < 60 ORDER BY ItemID ASC, SupplierID ASC, OrderID DESC", expectedDoc, metadata, dataMgr, shouldSucceed, expectedException, shouldFailMsg);        
    }
    */
  
    public void testOrderBy10() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 WHERE Quantity < 60 ORDER BY Name DESC", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }     
    
     public void testOrderBy11() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        helpTestProcess("SELECT * FROM xmltest.doc9a WHERE ItemID='001' OR ItemID='002' OR ItemID='003' ORDER BY ItemID DESC", EXPECTED_ORDERED_DOC9A, metadata, dataMgr);         //$NON-NLS-1$
    }   

    public void testOrderBy13() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 ORDER BY Name DESC, Quantity ASC ", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
        
    public void testOrderBy14() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
                "<Catalogs>\r\n" + //$NON-NLS-1$
                "    <Catalog>\r\n" +  //$NON-NLS-1$
                "        <Items>\r\n" + //$NON-NLS-1$
                "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
                "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
                "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
                "                <Suppliers>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
                "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders>\r\n" + //$NON-NLS-1$
                "                            <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
                "                                <OrderDate>05/31/02</OrderDate>\r\n" + //$NON-NLS-1$
                "                                <OrderQuantity>9</OrderQuantity>\r\n" + //$NON-NLS-1$
                "                                <OrderStatus>processing</OrderStatus>\r\n" + //$NON-NLS-1$
                "                            </Order>\r\n" + //$NON-NLS-1$
                "                            <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
                "                                <OrderDate>02/31/02</OrderDate>\r\n" + //$NON-NLS-1$
                "                                <OrderQuantity>12</OrderQuantity>\r\n" + //$NON-NLS-1$
                "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
                "                            </Order>\r\n" +  //$NON-NLS-1$
                "                        </Orders>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                </Suppliers>\r\n" + //$NON-NLS-1$
                "            </Item>\r\n" +                  //$NON-NLS-1$
                "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
                "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
                "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
                "                <Suppliers>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
                "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders>\r\n" + //$NON-NLS-1$
                "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
                "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
                "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
                "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
                "                            </Order>\r\n" + //$NON-NLS-1$
                "                        </Orders>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
                "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders/>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
                "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders>\r\n" + //$NON-NLS-1$
                "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
                "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
                "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
                "                            </Order>\r\n" + //$NON-NLS-1$
                "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
                "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
                "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
                "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
                "                            </Order>\r\n" +  //$NON-NLS-1$
                "                        </Orders>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                </Suppliers>\r\n" + //$NON-NLS-1$
                "            </Item>\r\n" +  //$NON-NLS-1$
                "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
                "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
                "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
                "                <Suppliers>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
                "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders/>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
                "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders>\r\n" + //$NON-NLS-1$
                "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
                "                                <OrderDate>12/31/01</OrderDate>\r\n" + //$NON-NLS-1$
                "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
                "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
                "                            </Order>\r\n" + //$NON-NLS-1$
                "                        </Orders>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
                "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders/>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
                "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders/>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                </Suppliers>\r\n" + //$NON-NLS-1$
                "            </Item>\r\n" +  //$NON-NLS-1$
                "        </Items>\r\n" +  //$NON-NLS-1$
                "    </Catalog>\r\n" +  //$NON-NLS-1$
                "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
            
        
        helpTestProcess("SELECT * FROM xmltest.doc9 ORDER BY Catalogs.Catalog.Items.Item.Suppliers.Supplier.SupplierID ASC, OrderID DESC, Catalogs.Catalog.Items.Item.ItemID DESC", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }   
     
    public void testOrderBy15() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +      //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +                          //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 ORDER BY Name ASC, Quantity DESC ", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    /** test null elements*/
    public void testOrderBy17() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerWithNulls(metadata);
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name xsi:nil=\"true\"/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity/>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +              //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 ORDER BY Name ASC, Quantity ASC ", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    /**  test duplicate elements*/
    public void testOrderBy18() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerWithDuplicates(metadata);
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +              //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 ORDER BY Name ASC, Quantity DESC ", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    /**  test more than two parallel elements*/
    public void testOrderBy19() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerWithDuplicates1(metadata);
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +              //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 ORDER BY ItemID ASC, Name ASC, Quantity DESC ", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    public void testOrderBy20() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +           //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +         //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +           //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
               
        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE ItemID='002' AND Catalogs.Catalog.Items.Item.Suppliers.Supplier.Orders.Order.OrderQuantity < 1000 " //$NON-NLS-1$
            + "ORDER BY SupplierID ", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }         
    
    /**
     * Doc nodes that are not mapped to data cannot be used in the 
     * ORDER BY clause of an XML doc query
     */
    public void testOrderBy_defect9803() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
               
        try {
            helpTestProcess("SELECT * FROM xmltest.doc9 WHERE ItemID='002' AND Catalogs.Catalog.Items.Item.Suppliers.Supplier.Orders.Order.OrderQuantity < 1000 " //$NON-NLS-1$
                + "ORDER BY Suppliers", //$NON-NLS-1$
                "", metadata, dataMgr);  //$NON-NLS-1$
            fail("Should have failed with QueryPlannerException but didn't"); //$NON-NLS-1$
        } catch (QueryPlannerException e) {
            String expectedMsg = "The XML document element [element] name='Suppliers' minOccurs=1 maxOccurs=1 is not mapped to data and cannot be used in the ORDER BY clause: ORDER BY Suppliers"; //$NON-NLS-1$
            assertEquals(expectedMsg, e.getMessage());
        }  
    }    
        
    //defect 8130
    public void test1CriteriaWithUnmappedElementFails() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        
        helpTestProcess("SELECT * FROM xmltest.doc1 WHERE Catalog = 'something'", null, metadata, dataMgr, false, QueryPlannerException.class, null);         //$NON-NLS-1$
    }    

    //defect 8130
    public void test1CriteriaWithUnmappedElementFails2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        
        helpTestProcess("SELECT * FROM xmltest.doc1 WHERE Item = 'something'", null, metadata, dataMgr, false, QueryPlannerException.class, null);         //$NON-NLS-1$
    }  
    
    public void testNested() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }    

    private static final String EXPECTED_DOC_NESTED_2 = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>12/31/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>02/31/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>12</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                            <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>05/31/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>9</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>processing</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

    private static final String EXPECTED_DOC_NESTED_3 = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>12/31/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
            
    public void testNested2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = EXPECTED_DOC_NESTED_2;
        helpTestProcess("SELECT * FROM xmltest.doc9", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }    

    /**
     * Tests a couple temp groups at the root - B selects from A, and a mapping class
     * selects from B
     */
    public void testNested2aTempGroup() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = EXPECTED_DOC_NESTED_2;
        helpTestProcess("SELECT * FROM xmltest.doc9a", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }   

    public void testNested2aTempGroupCriteria() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
        "<Catalogs>\r\n" + //$NON-NLS-1$
        "    <Catalog>\r\n" +  //$NON-NLS-1$
        "        <Items>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
        "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
        "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
        "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
        "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
        "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
        "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
        "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
        "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders>\r\n" + //$NON-NLS-1$
        "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
        "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
        "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
        "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
        "                            </Order>\r\n" + //$NON-NLS-1$
        "                        </Orders>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
        "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
        "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
        "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "        </Items>\r\n" +  //$NON-NLS-1$
        "    </Catalog>\r\n" +  //$NON-NLS-1$
        "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc9a WHERE tempGroup.orders.orderNum = 1", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }   

    /** defect 13172, CSE Case 1811 */
    public void testNested2aTempGroupCompoundCriteria() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
        "<Catalogs>\r\n" + //$NON-NLS-1$
        "    <Catalog>\r\n" +  //$NON-NLS-1$
        "        <Items>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
        "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
        "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
        "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
        "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
        "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
        "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
        "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
        "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
//        "                        <Orders>\r\n" + //$NON-NLS-1$
//        "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
//        "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
//        "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
//        "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
//        "                            </Order>\r\n" + //$NON-NLS-1$
//        "                        </Orders>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
        "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
        "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
        "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "        </Items>\r\n" +  //$NON-NLS-1$
        "    </Catalog>\r\n" +  //$NON-NLS-1$
        "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc9a WHERE tempGroup.orders.orderNum = '1' AND tempGroup.orders.orderStatus = 'processing'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    /** defect 13172, CSE Case 1811 */
    public void testNested2aTempGroupCompoundCriteria1() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
        "<Catalogs>\r\n" + //$NON-NLS-1$
        "    <Catalog>\r\n" +  //$NON-NLS-1$
        "        <Items>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
        "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
        "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
        "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
        "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
        "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
        "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
        "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
        "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
//        "                        <Orders>\r\n" + //$NON-NLS-1$
//        "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
//        "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
//        "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
//        "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
//        "                            </Order>\r\n" + //$NON-NLS-1$
//        "                        </Orders>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
        "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
        "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
        "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "        </Items>\r\n" +  //$NON-NLS-1$
        "    </Catalog>\r\n" +  //$NON-NLS-1$
        "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc9a WHERE tempGroup.orders.orderNum = 1 AND tempGroup.orders.orderQty = 87", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    /** defect 13172, CSE Case 1811 */
    public void testNested2aTempGroupCompoundCriteria2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
        "<Catalogs>\r\n" + //$NON-NLS-1$
        "    <Catalog>\r\n" +  //$NON-NLS-1$
        "        <Items>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
        "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
        "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
        "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
        "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
        "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
        "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
        "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
        "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders>\r\n" + //$NON-NLS-1$
        "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
        "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
        "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
        "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
        "                            </Order>\r\n" + //$NON-NLS-1$
        "                        </Orders>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
        "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
        "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
        "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
//        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                        <Orders>\r\n" + //$NON-NLS-1$
        "                            <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
        "                                <OrderDate>05/31/02</OrderDate>\r\n" + //$NON-NLS-1$
        "                                <OrderQuantity>9</OrderQuantity>\r\n" + //$NON-NLS-1$
        "                                <OrderStatus>processing</OrderStatus>\r\n" + //$NON-NLS-1$
        "                            </Order>\r\n" + //$NON-NLS-1$
        "                        </Orders>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "        </Items>\r\n" +  //$NON-NLS-1$
        "    </Catalog>\r\n" +  //$NON-NLS-1$
        "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc9a WHERE tempGroup.orders.orderNum = '1' OR tempGroup.orders.orderStatus = 'processing'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testNested2cTempGroup() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = EXPECTED_DOC_NESTED_3;
        helpTestProcess("SELECT * FROM xmltest.doc9a WHERE ItemID = '001'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }   

    /**
     * Tests a temp group C that selects from a root temp group, plus has bindings to
     * some ancestor mapping classes ( we no longer support bindings on staging tables)
     */
    public void defer_testNested2bTempGroup() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = EXPECTED_DOC_NESTED_2;
        helpTestProcess("SELECT * FROM xmltest.doc9b", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }   

    public void testNested2WithCriteria() throws Exception {

        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>12/31/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE ItemID='001'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    /**
     * <p>This test illustrates how to use the context operator
     * to specify that the result set to limit is the same as the
     * result set which the criteria originates from, when that
     * result set is nested below the top level.
     * Test {@link #testNested2WithCriteria2a} shows a similar
     * query without the context operator.</p>
     * 
     * @see #testNested2WithCriteria2a
     */
    public void testNested2WithCriteria2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>12/31/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers/>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers/>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        XMLPlan plan = helpTestProcess("SELECT * FROM xmltest.doc9 WHERE context(SupplierID, SupplierID)='52'", expectedDoc, metadata, dataMgr); //$NON-NLS-1$

        // check the staging base line (unknown cost)
        // one for staging and for unloading
        Map stats = XMLProgramUtil.getProgramStats(plan.getOriginalProgram());
        assertEquals(2, ((List)stats.get(ExecStagingTableInstruction.class)).size());
    }

    /**
     * <p>This test illustrates how to use the context operator
     * to specify that the result set to limit is the same as the
     * result set which the criteria originates from, when that
     * result set is nested below the top level.
     * Test {@link #testNested2WithCriteria2a} shows a similar
     * query without the context operator.</p>
     * 
     * <p>defect 9802, trying different ways of qualifying 1st arg
     * to context operator</p>
     * 
     * @see #testNested2WithCriteria2a
     */
    public void testNested2WithCriteria2_defect9802() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>12/31/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers/>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers/>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE context(Supplier.SupplierID, SupplierID)='52'", expectedDoc, metadata, dataMgr); //$NON-NLS-1$
    }    
    
    /**
     * <p>Same as {@link #testNested2WithCriteria2} but with a
     * type conversion on the right expression from integer to string.
     * This demonstrates a function of only constants is executed in
     * an XML criteria.</p>
     * shows a similar
     * @see #testNested2WithCriteria2
     */
    public void testNested2WithCriteria2_function() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" + //$NON-NLS-1$
            "        <Items>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" + //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" + //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>12/31/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" + //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" + //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers/>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" + //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" + //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers/>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "        </Items>\r\n" + //$NON-NLS-1$
            "    </Catalog>\r\n" + //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE context(SupplierID, SupplierID)=convert(52, string)", expectedDoc, metadata, dataMgr); //$NON-NLS-1$
    }

    /**
     * <p>This tests the unintuitive effects of specifying a user
     * criteria on a node inside a nested mapping class.  Here, the 
     * "Item" fragment is repeated, but the "Supplier" fragment inside
     * is limited by the criteria.  
     * Test {@link #testNested2WithCriteria2} shows how to use
     * the "context" syntax to control what context the criteria
     * is used to restrict.</p>
     * 
     * <P>UPDATE: With 3.0 sp1 the default behavior is changed.
     * Now, if "context" syntax is not used, the outer context (anchored 
     * at "Item" node) will be limited by default, instead of the one
     * the criteria is actually specified on.</p>
     */
    public void testNested2WithCriteria2a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>12/31/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
            

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE SupplierID='52'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }


    public void testNested2WithContextCriteria() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>12/31/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE context(Item, SupplierID)='52'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testNested2WithContextCriteria2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
//            "                                <OrderStatus/>\r\n" +
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE context(Item, OrderID)='5'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testNested2WithContextCriteria3() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers/>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers/>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE context(SupplierID, OrderID)='5'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    private static final String EXPECTED_DOC_NESTED_2_WITH_CONTEXT_CRITERIA_4 = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$


    public void testNested2WithContextCriteria4() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = EXPECTED_DOC_NESTED_2_WITH_CONTEXT_CRITERIA_4;

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE context(Item, OrderID)='5' OR context(Item, OrderID)='6'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testNested2WithContextCriteria4a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = EXPECTED_DOC_NESTED_2_WITH_CONTEXT_CRITERIA_4;

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE context(Item, OrderID)='5' OR OrderID='6'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testNested2WithContextCriteria4b() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = EXPECTED_DOC_NESTED_2_WITH_CONTEXT_CRITERIA_4;

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE OrderID='5' OR OrderID='6'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    private static final String EXPECTED_DOC_NESTED_2_WITH_CONTEXT_CRITERIA_5 = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>12/31/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

    public void testNested2WithContextCriteria5() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = EXPECTED_DOC_NESTED_2_WITH_CONTEXT_CRITERIA_5;

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE context(Item, OrderID)='5' OR context(Item, OrderID)='2'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testNested2WithContextCriteria5a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = EXPECTED_DOC_NESTED_2_WITH_CONTEXT_CRITERIA_5;

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE OrderID='5' OR context(Item, OrderID)='2'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testNested2WithContextCriteria5b() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = EXPECTED_DOC_NESTED_2_WITH_CONTEXT_CRITERIA_5;

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE OrderID='5' OR OrderID='2'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    public static String readFile(String fileName) throws Exception {
        FileInputStream fis = new FileInputStream(UnitTestUtil.getTestDataFile(fileName));
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        int c = 0;
        while ((c = fis.read()) != -1) {
            baos.write(c);
        }
        
        return baos.toString();
    }

    public void testNested2WithContextCriteria5Fail() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = ""; //doesn't matter //$NON-NLS-1$

        boolean shouldSucceed = false;
        Class expectedException = QueryPlannerException.class;
        String shouldFailMsg = "expected failure since two different contexts were specified in conjunct"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE context(Item, OrderID)='5' OR context(SupplierID, OrderID)='2'", expectedDoc, metadata, dataMgr, shouldSucceed, expectedException, shouldFailMsg);         //$NON-NLS-1$
    }

    public void testNested2WithContextCriteria6() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items/>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE context(Item, OrderID)='5' AND context(Item, OrderID)='2'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testNested2WithContextCriteria6b() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items/>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE OrderID='5' AND OrderID='2'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    //WHERE CONTEXT(SupplierID, OrderID)='5' AND context(OrderID, OrderID)='5'
    private static final String EXPECTED_DOC_NESTED_2_WITH_CONTEXT_CRITERIA_7 = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers/>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers/>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

    public void testNested2WithContextCriteria7() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = EXPECTED_DOC_NESTED_2_WITH_CONTEXT_CRITERIA_7;
        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE CONTEXT(SupplierID, OrderID)='5' AND context(OrderID, OrderID)='5'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testNested2WithContextCriteria7b() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = EXPECTED_DOC_NESTED_2_WITH_CONTEXT_CRITERIA_7;
        String query = "SELECT * FROM xmltest.doc9 WHERE CONTEXT(Catalogs.Catalog.Items.Item.Suppliers.Supplier.SupplierID, " //$NON-NLS-1$
         + "Catalogs.Catalog.Items.Item.Suppliers.Supplier.Orders.Order.OrderID)='5' AND context(OrderID, OrderID)='5'"; //$NON-NLS-1$
        helpTestProcess(query, expectedDoc, metadata, dataMgr);        
    }

    public void testNested2WithContextCriteria7c() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = EXPECTED_DOC_NESTED_2_WITH_CONTEXT_CRITERIA_7;
        String query = "SELECT * FROM xmltest.doc9 WHERE CONTEXT(Catalogs.Catalog.Items.Item.Suppliers.Supplier.SupplierID, " //$NON-NLS-1$
         + "Catalogs.Catalog.Items.Item.Suppliers.Supplier.Orders.Order.OrderID)='6' AND context(OrderID, OrderID)='5'"; //$NON-NLS-1$
        helpTestProcess(query, expectedDoc, metadata, dataMgr);    
    }

    /**
     * per defect 7333
     */
    public void testNested2WithContextCriteria_7333() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>02/31/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>12</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                            <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>05/31/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>9</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>processing</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE OrderID='4' AND ItemID='003'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    /**
     * per defect 7333
     */
    public void testNested2WithContextCriteria_7333b() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>02/31/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>12</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                            <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>05/31/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>9</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>processing</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE ItemID='003' AND OrderID='4'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    /**
     * per defect 7333
     */
    public void testNested2WithContextCriteria_7333c() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items/>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE OrderID='5' AND ItemID='003'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    /**
     * per defect 7333
     */
    public void testNested2WithContextCriteria_7333d() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items/>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE ItemID='003' AND OrderID='5'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    /**
     * Select a single item, and then limit the suppliers based on an order #
     */
    public void testNested2WithContextCriteria8() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc9 WHERE ItemID='002' AND Context(Supplier,OrderID)='5'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testNestedWithChoice() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                            <OtherOrder OrderID=\"2\"/>\r\n" + //$NON-NLS-1$
            "                        </ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                            <OtherOrder OrderID=\"1\"/>\r\n" + //$NON-NLS-1$
            "                        </ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                            <OtherOrder OrderID=\"5\"/>\r\n" + //$NON-NLS-1$
            "                            <OtherOrder OrderID=\"6\"/>\r\n" + //$NON-NLS-1$
            "                        </ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                            <OtherOrder OrderID=\"3\"/>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>05/31/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>9</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc10", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }    

    /**
     * Does not use 'context' operator
     */
    public void testNestedWithChoiceAndCriteria2_6796() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                            <OtherOrder OrderID=\"1\"/>\r\n" + //$NON-NLS-1$
            "                        </ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                            <OtherOrder OrderID=\"5\"/>\r\n" + //$NON-NLS-1$
            "                            <OtherOrder OrderID=\"6\"/>\r\n" + //$NON-NLS-1$
            "                        </ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc10 where Catalogs.Catalog.Items.Item.Suppliers.Supplier.ProcessingOrders.OtherOrder.OrderID='5'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }    

    /**
     * Uses the 'context' operator
     */
    public void testNestedWithChoiceAndCriteria2a_6796() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                            <OtherOrder OrderID=\"5\"/>\r\n" + //$NON-NLS-1$
            "                        </ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc10 where context(Catalogs.Catalog.Items.Item.Suppliers.Supplier.ProcessingOrders.OtherOrder.OrderID, Catalogs.Catalog.Items.Item.Suppliers.Supplier.ProcessingOrders.OtherOrder.OrderID)='5'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }    

    /**
     * Does not use 'context' operator
     */
    public void testNestedWithLookupChoice() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        dataMgr.setThrowBlocked(true);
        Map values = new HashMap();
        values.put("x", "y"); //$NON-NLS-1$ //$NON-NLS-2$
        dataMgr.defineCodeTable("stock.items", "itemName", "itemNum", values); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                            <OtherOrder OrderID=\"1\"/>\r\n" + //$NON-NLS-1$
            "                        </ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                            <OtherOrder OrderID=\"5\"/>\r\n" + //$NON-NLS-1$
            "                            <OtherOrder OrderID=\"6\"/>\r\n" + //$NON-NLS-1$
            "                        </ProcessingOrders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc10L where Catalogs.Catalog.Items.Item.Suppliers.Supplier.ProcessingOrders.OtherOrder.OrderID='5'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }    
    
    public void test1Unformatted() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" + //$NON-NLS-1$
            "<Catalog>" +  //$NON-NLS-1$
            "<Items>" +  //$NON-NLS-1$
            "<Item ItemID=\"001\">" +  //$NON-NLS-1$
            "<Name>Lamp</Name>" +  //$NON-NLS-1$
            "<Quantity>5</Quantity>" +  //$NON-NLS-1$
            "</Item>" +  //$NON-NLS-1$
            "<Item ItemID=\"002\">" +  //$NON-NLS-1$
            "<Name>Screwdriver</Name>" +  //$NON-NLS-1$
            "<Quantity>100</Quantity>" +  //$NON-NLS-1$
            "</Item>" +  //$NON-NLS-1$
            "<Item ItemID=\"003\">" +  //$NON-NLS-1$
            "<Name>Goat</Name>" +  //$NON-NLS-1$
            "<Quantity>4</Quantity>" +  //$NON-NLS-1$
            "</Item>" +  //$NON-NLS-1$
            "</Items>" +  //$NON-NLS-1$
            "</Catalog>" +  //$NON-NLS-1$
            "</Catalogs>\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1Unformatted", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }


    // jhTODO: complete this

    public void testChoice_5266a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" +  //$NON-NLS-1$
            "   <Catalog>\r\n" +  //$NON-NLS-1$
            "      <Items>\r\n" +  //$NON-NLS-1$
            "         <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "            <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "            <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            <Suppliers>\r\n" +  //$NON-NLS-1$
            "               <Supplier SupplierID=\"51\">\r\n" +  //$NON-NLS-1$
            "                  <Name>Chucky</Name>\r\n" +  //$NON-NLS-1$
            "                  <Zip>11111</Zip>\r\n" +  //$NON-NLS-1$
            "                  <ProcessingOrders/>\r\n" +  //$NON-NLS-1$
            "               </Supplier>\r\n" +  //$NON-NLS-1$
            "               <Supplier SupplierID=\"52\">\r\n" +  //$NON-NLS-1$
            "                  <Name>Biff's Stuff</Name>\r\n" +  //$NON-NLS-1$
            "                  <Zip>22222</Zip>\r\n" +  //$NON-NLS-1$
            "                  <ProcessingOrders/>\r\n" +  //$NON-NLS-1$
            "               </Supplier>\r\n" +  //$NON-NLS-1$
            "               <Supplier SupplierID=\"53\">\r\n" +  //$NON-NLS-1$
            "                  <Name>AAAA</Name>\r\n" +  //$NON-NLS-1$
            "                  <Zip>33333</Zip>\r\n" +  //$NON-NLS-1$
            "                  <ProcessingOrders/>\r\n" +  //$NON-NLS-1$
            "               </Supplier>\r\n" +  //$NON-NLS-1$
            "               <Supplier SupplierID=\"56\">\r\n" +  //$NON-NLS-1$
            "                  <Name>Microsoft</Name>\r\n" +  //$NON-NLS-1$
            "                  <Zip>66666</Zip>\r\n" +  //$NON-NLS-1$
            "                  <ProcessingOrders/>\r\n" +  //$NON-NLS-1$
            "               </Supplier>\r\n" +  //$NON-NLS-1$
            "            </Suppliers>\r\n" +  //$NON-NLS-1$
            "         </Item>\r\n" +  //$NON-NLS-1$
            "         <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "            <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "            <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            <Suppliers>\r\n" +  //$NON-NLS-1$
            "               <Supplier SupplierID=\"54\">\r\n" +  //$NON-NLS-1$
            "                  <Name>Nugent Co.</Name>\r\n" +  //$NON-NLS-1$
            "                  <Zip>44444</Zip>\r\n" +  //$NON-NLS-1$
            "                  <ProcessingOrders/>\r\n" +  //$NON-NLS-1$
            "               </Supplier>\r\n" +  //$NON-NLS-1$
            "               <Supplier SupplierID=\"55\">\r\n" +  //$NON-NLS-1$
            "                  <Name>Zeta</Name>\r\n" +  //$NON-NLS-1$
            "                  <Zip>55555</Zip>\r\n" +  //$NON-NLS-1$\r\n" 
            "                  <ProcessingOrders/>\r\n" +  //$NON-NLS-1$
            "               </Supplier>\r\n" +  //$NON-NLS-1$
            "               <Supplier SupplierID=\"56\">\r\n" +  //$NON-NLS-1$
            "                  <Name>Microsoft</Name>\r\n" +  //$NON-NLS-1$\r\n" +  
            "                  <Zip>66666</Zip>\r\n" +  //$NON-NLS-1$
            "                  <ProcessingOrders/>\r\n" +  //$NON-NLS-1$
            "               </Supplier>\r\n" +  //$NON-NLS-1$
            "            </Suppliers>\r\n" +  //$NON-NLS-1$
            "         </Item>\r\n" +  //$NON-NLS-1$
            "         <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "            <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "            <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            <Suppliers>\r\n" +  //$NON-NLS-1$
            "               <Supplier SupplierID=\"56\">\r\n" +  //$NON-NLS-1$
            "                  <Name>Microsoft</Name>\r\n" +  //$NON-NLS-1$
            "                  <Zip>66666</Zip>\r\n" +  //$NON-NLS-1$
            "                  <ProcessingOrders>\r\n" +  //$NON-NLS-1$
            "                     <Order OrderID=\"4\">\r\n" +  //$NON-NLS-1$
            "                        <OrderDate>05/31/02</OrderDate>\r\n" +  //$NON-NLS-1$
            "                        <OrderQuantity>9</OrderQuantity>\r\n" +  //$NON-NLS-1$
            "                     </Order>\r\n" +  //$NON-NLS-1$
            "                  </ProcessingOrders>\r\n" +  //$NON-NLS-1$
            "               </Supplier>\r\n" +  //$NON-NLS-1$
            "            </Suppliers>\r\n" +  //$NON-NLS-1$
            "         </Item>\r\n" +  //$NON-NLS-1$
            "      </Items>\r\n" +  //$NON-NLS-1$
            "   </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n";  //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc_5266a", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    
    public void test1WithCriteriaShortName() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 WHERE quantity < 50", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void test1WithCriteriaLongName() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 WHERE catalogs.catalog.items.item.quantity < 50", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void test2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc2", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void test2a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc2a", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void test2b() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        helpTestProcess("SELECT * FROM xmltest.doc2b", null, metadata, dataMgr, false, MetaMatrixComponentException.class, "Should have failed on default");         //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void test2c() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item2 ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item2>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc2c", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void test2d() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item2 ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item2>\r\n" +   //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc2d", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void test2e() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item2 ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item2>\r\n" +   //$NON-NLS-1$
            "            <ItemDefault ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </ItemDefault>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc2e", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testWithNillableNode() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerWithNulls(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name xsi:nil=\"true\"/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity/>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc1", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testWithDefault() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerWithNulls(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name xsi:nil=\"true\"/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>1</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc3", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testWithNamespaces() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerWithNulls(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name xsi:nil=\"true\"/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>1</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "    <duh:Fake xmlns:duh=\"http://www.duh.org/duh\" xmlns:duh2=\"http://www.duh2.org/duh2\"\n" + //$NON-NLS-1$
            "              xmlns=\"http://www.default.org/default\">fixed constant value<duh:FakeChild2>\r\n" +  //$NON-NLS-1$
            "            <FakeChild2a>another fixed constant value</FakeChild2a>\r\n" +  //$NON-NLS-1$
            "        </duh:FakeChild2>\r\n" +  //$NON-NLS-1$
            "        <FakeChild3 xmlns:duh=\"http://www.duh.org/duh/duh\" duh:FakeAtt=\"fixed att value\"/>\r\n" +  //$NON-NLS-1$
            "    </duh:Fake>\r\n" + //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc4", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testWithNewIter3Properties() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerDuJour(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name xsi:nil=\"true\"/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>0</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <DiscontinuedItem ItemID=\"004\">\r\n" +  //$NON-NLS-1$
            "                <Name>Flux Capacitor</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>2</Quantity>\r\n" +  //$NON-NLS-1$
            "            </DiscontinuedItem>\r\n" +  //$NON-NLS-1$
            "            <StatusUnknown ItemID=\"005\">\r\n" +  //$NON-NLS-1$
            "                <Name>Milkshake</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>88</Quantity>\r\n" +  //$NON-NLS-1$
            "            </StatusUnknown>\r\n" +  //$NON-NLS-1$
            "            <DiscontinuedItem ItemID=\"006\">\r\n" +  //$NON-NLS-1$
            "                <Name>Feta Matrix</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>0</Quantity>\r\n" +  //$NON-NLS-1$
            "            </DiscontinuedItem>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc5", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testWithNewIter3PropertiesException() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerDuJour(metadata);
        
        Command command = helpGetCommand("SELECT * FROM xmltest.doc6", metadata); //$NON-NLS-1$
        XMLPlan plan = TestXMLPlanner.preparePlan(command, metadata, new DefaultCapabilitiesFinder(), null);

        BufferManager bufferMgr = BufferManagerFactory.getStandaloneBufferManager();
        CommandContext context = new CommandContext("pID", null, null, null, null);                                 //$NON-NLS-1$
        QueryProcessor processor = new QueryProcessor(plan, context, bufferMgr, dataMgr);

        MetaMatrixComponentException failOnDefaultException = null;
        try{
            processor.process();
        } catch (MetaMatrixComponentException e){
            failOnDefaultException = e;
        }
        
        super.assertNotNull("Query processing should have failed on default of choice node.", failOnDefaultException); //$NON-NLS-1$
    }

    public void testAttributeBug() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<FixedValueTest>\r\n" + //$NON-NLS-1$
            "    <wrapper fixedAttr=\"fixed attribute\">\r\n" + //$NON-NLS-1$
            "        <key>001</key>\r\n" + //$NON-NLS-1$
            "        <fixed>fixed value</fixed>\r\n" + //$NON-NLS-1$
            "    </wrapper>\r\n" + //$NON-NLS-1$
            "    <wrapper fixedAttr=\"fixed attribute\">\r\n" + //$NON-NLS-1$
            "        <key>002</key>\r\n" + //$NON-NLS-1$
            "        <fixed>fixed value</fixed>\r\n" + //$NON-NLS-1$
            "    </wrapper>\r\n" + //$NON-NLS-1$
            "    <wrapper fixedAttr=\"fixed attribute\">\r\n" + //$NON-NLS-1$
            "        <key>003</key>\r\n" + //$NON-NLS-1$
            "        <fixed>fixed value</fixed>\r\n" + //$NON-NLS-1$
            "    </wrapper>\r\n" + //$NON-NLS-1$
            "</FixedValueTest>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc7", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testMultipleDocs() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);

        String expectedDoc1 = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "    <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "    <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "    <Suppliers>\r\n" + //$NON-NLS-1$
            "        <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "            <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "            <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "        </Supplier>\r\n" + //$NON-NLS-1$
            "        <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "            <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "            <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "        </Supplier>\r\n" + //$NON-NLS-1$
            "        <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "            <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "            <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "        </Supplier>\r\n" + //$NON-NLS-1$
            "        <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "            <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "            <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "        </Supplier>\r\n" + //$NON-NLS-1$
            "    </Suppliers>\r\n" + //$NON-NLS-1$
            "</Item>\r\n\r\n";  //$NON-NLS-1$

        String expectedDoc2 = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "    <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "    <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "    <Suppliers>\r\n" + //$NON-NLS-1$
            "        <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "            <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "            <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "        </Supplier>\r\n" + //$NON-NLS-1$
            "        <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "            <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "            <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "        </Supplier>\r\n" + //$NON-NLS-1$
            "        <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "            <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "            <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "        </Supplier>\r\n" + //$NON-NLS-1$
            "    </Suppliers>\r\n" + //$NON-NLS-1$
            "</Item>\r\n\r\n"; //$NON-NLS-1$
            
        String expectedDoc3 = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "    <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "    <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "    <Suppliers>\r\n" + //$NON-NLS-1$
            "        <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "            <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "            <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "        </Supplier>\r\n" + //$NON-NLS-1$
            "    </Suppliers>\r\n" + //$NON-NLS-1$
            "</Item>\r\n\r\n"; //$NON-NLS-1$


        String[] expectedDocs = new String[]{expectedDoc1, expectedDoc2, expectedDoc3};

        helpTestProcess("SELECT * FROM xmltest.doc11", expectedDocs, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testRecursive() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<OrgHierarchy>\r\n" +  //$NON-NLS-1$
            "    <Employee ID=\"01\">\r\n" +  //$NON-NLS-1$
            "        <FirstName>Ted</FirstName>\r\n" + //$NON-NLS-1$
            "        <LastName>Nugent</LastName>\r\n" + //$NON-NLS-1$
            "        <Subordinates>\r\n" + //$NON-NLS-1$
            "            <Employee ID=\"02\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Squier</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates>\r\n" + //$NON-NLS-1$
            "                    <Employee ID=\"04\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Leland</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Sklar</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates>\r\n" + //$NON-NLS-1$
            "                            <Employee ID=\"06\">\r\n" +  //$NON-NLS-1$
            "                                <FirstName>John</FirstName>\r\n" + //$NON-NLS-1$
            "                                <LastName>Zorn</LastName>\r\n" + //$NON-NLS-1$
            "                                <Subordinates>\r\n" + //$NON-NLS-1$
            "                                    <Employee ID=\"11\">\r\n" +  //$NON-NLS-1$
            "                                        <FirstName>Mike</FirstName>\r\n" + //$NON-NLS-1$
            "                                        <LastName>Patton</LastName>\r\n" + //$NON-NLS-1$
            "                                        <Subordinates>\r\n" + //$NON-NLS-1$
            "                                            <Employee ID=\"13\">\r\n" +  //$NON-NLS-1$
            "                                                <FirstName>Puffy</FirstName>\r\n" + //$NON-NLS-1$
            "                                                <LastName>Bordin</LastName>\r\n" + //$NON-NLS-1$
            "                                                <Subordinates/>\r\n" + //$NON-NLS-1$
            "                                            </Employee>\r\n" +  //$NON-NLS-1$
            "                                        </Subordinates>\r\n" + //$NON-NLS-1$
            "                                    </Employee>\r\n" +  //$NON-NLS-1$
            "                                    <Employee ID=\"12\">\r\n" +  //$NON-NLS-1$
            "                                        <FirstName>Devin</FirstName>\r\n" + //$NON-NLS-1$
            "                                        <LastName>Townsend</LastName>\r\n" + //$NON-NLS-1$
            "                                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                                    </Employee>\r\n" +  //$NON-NLS-1$
            "                                </Subordinates>\r\n" + //$NON-NLS-1$
            "                            </Employee>\r\n" +  //$NON-NLS-1$
            "                        </Subordinates>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Employee>\r\n" +  //$NON-NLS-1$
            "            <Employee ID=\"03\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>John</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Smith</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates>\r\n" + //$NON-NLS-1$
            "                    <Employee ID=\"05\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Kevin</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Moore</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Employee>\r\n" +  //$NON-NLS-1$
            "        </Subordinates>\r\n" + //$NON-NLS-1$
            "    </Employee>\r\n" +  //$NON-NLS-1$
            "    <Employee ID=\"07\">\r\n" +  //$NON-NLS-1$
            "        <FirstName>Geoff</FirstName>\r\n" + //$NON-NLS-1$
            "        <LastName>Tate</LastName>\r\n" + //$NON-NLS-1$
            "        <Subordinates>\r\n" + //$NON-NLS-1$
            "            <Employee ID=\"08\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>Les</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Claypool</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates>\r\n" + //$NON-NLS-1$
            "                    <Employee ID=\"09\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Meat</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Loaf</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                    <Employee ID=\"10\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Keith</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Sweat</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Employee>\r\n" +  //$NON-NLS-1$
            "        </Subordinates>\r\n" + //$NON-NLS-1$
            "    </Employee>\r\n" +  //$NON-NLS-1$
            "</OrgHierarchy>\r\n\r\n";  //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc12", expectedDoc, metadata, dataMgr); //$NON-NLS-1$
    }

    public void testRecursiveA() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<OrgHierarchy>\r\n" +  //$NON-NLS-1$
            "    <Employee ID=\"01\">\r\n" +  //$NON-NLS-1$
            "        <FirstName>Ted</FirstName>\r\n" + //$NON-NLS-1$
            "        <LastName>Nugent</LastName>\r\n" + //$NON-NLS-1$
            "        <Subordinates>\r\n" + //$NON-NLS-1$
            "            <Subordinate ID=\"02\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Squier</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates>\r\n" + //$NON-NLS-1$
            "                    <Subordinate ID=\"04\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Leland</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Sklar</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates>\r\n" + //$NON-NLS-1$
            "                            <Subordinate ID=\"06\">\r\n" +  //$NON-NLS-1$
            "                                <FirstName>John</FirstName>\r\n" + //$NON-NLS-1$
            "                                <LastName>Zorn</LastName>\r\n" + //$NON-NLS-1$
            "                                <Subordinates>\r\n" + //$NON-NLS-1$
            "                                    <Subordinate ID=\"11\">\r\n" +  //$NON-NLS-1$
            "                                        <FirstName>Mike</FirstName>\r\n" + //$NON-NLS-1$
            "                                        <LastName>Patton</LastName>\r\n" + //$NON-NLS-1$
            "                                        <Subordinates>\r\n" + //$NON-NLS-1$
            "                                            <Subordinate ID=\"13\">\r\n" +  //$NON-NLS-1$
            "                                                <FirstName>Puffy</FirstName>\r\n" + //$NON-NLS-1$
            "                                                <LastName>Bordin</LastName>\r\n" + //$NON-NLS-1$
            "                                                <Subordinates/>\r\n" + //$NON-NLS-1$
            "                                            </Subordinate>\r\n" +  //$NON-NLS-1$
            "                                        </Subordinates>\r\n" + //$NON-NLS-1$
            "                                    </Subordinate>\r\n" +  //$NON-NLS-1$
            "                                    <Subordinate ID=\"12\">\r\n" +  //$NON-NLS-1$
            "                                        <FirstName>Devin</FirstName>\r\n" + //$NON-NLS-1$
            "                                        <LastName>Townsend</LastName>\r\n" + //$NON-NLS-1$
            "                                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                                    </Subordinate>\r\n" +  //$NON-NLS-1$
            "                                </Subordinates>\r\n" + //$NON-NLS-1$
            "                            </Subordinate>\r\n" +  //$NON-NLS-1$
            "                        </Subordinates>\r\n" + //$NON-NLS-1$
            "                    </Subordinate>\r\n" +  //$NON-NLS-1$
            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Subordinate>\r\n" +  //$NON-NLS-1$
            "            <Subordinate ID=\"03\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>John</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Smith</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates>\r\n" + //$NON-NLS-1$
            "                    <Subordinate ID=\"05\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Kevin</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Moore</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Subordinate>\r\n" +  //$NON-NLS-1$
            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Subordinate>\r\n" +  //$NON-NLS-1$
            "        </Subordinates>\r\n" + //$NON-NLS-1$
            "    </Employee>\r\n" +  //$NON-NLS-1$
            "    <Employee ID=\"07\">\r\n" +  //$NON-NLS-1$
            "        <FirstName>Geoff</FirstName>\r\n" + //$NON-NLS-1$
            "        <LastName>Tate</LastName>\r\n" + //$NON-NLS-1$
            "        <Subordinates>\r\n" + //$NON-NLS-1$
            "            <Subordinate ID=\"08\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>Les</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Claypool</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates>\r\n" + //$NON-NLS-1$
            "                    <Subordinate ID=\"09\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Meat</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Loaf</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Subordinate>\r\n" +  //$NON-NLS-1$
            "                    <Subordinate ID=\"10\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Keith</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Sweat</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Subordinate>\r\n" +  //$NON-NLS-1$
            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Subordinate>\r\n" +  //$NON-NLS-1$
            "        </Subordinates>\r\n" + //$NON-NLS-1$
            "    </Employee>\r\n" +  //$NON-NLS-1$
            "</OrgHierarchy>\r\n\r\n";  //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc12a", expectedDoc, metadata, dataMgr); //$NON-NLS-1$
    }    
    
    /**
     * 4/25/05 sbale - This failing test raises a question about recursion
     * termination criteria - should the chunk of document that meets the 
     * criteria be included or not?  In this test below, it is expected to
     * be included, but is not included in actual results due to recent
     * changes for Booz Allen POC.  I could see it going either way.
     *  
     * sbale 4/27/05 I have changed expected results as a result of changes for
     * Booz Allen POC.  Previously, the recursive fragment of the document that
     * satisfied the recursion termination criteria was included, now it is not.
     * See commented out section below for previous expected results. 
     * @throws Exception
     */
    public void testRecursive2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<OrgHierarchy>\r\n" +  //$NON-NLS-1$
            "    <Employee ID=\"01\">\r\n" +  //$NON-NLS-1$
            "        <FirstName>Ted</FirstName>\r\n" + //$NON-NLS-1$
            "        <LastName>Nugent</LastName>\r\n" + //$NON-NLS-1$
            "        <Subordinates>\r\n" + //$NON-NLS-1$
            "            <Employee ID=\"02\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Squier</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates/>\r\n" + //$NON-NLS-1$
//            "                <Subordinates>\r\n" + //$NON-NLS-1$
//            "                    <Employee ID=\"04\">\r\n" +  //$NON-NLS-1$
//            "                        <FirstName>Leland</FirstName>\r\n" + //$NON-NLS-1$
//            "                        <LastName>Sklar</LastName>\r\n" + //$NON-NLS-1$
//            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
//            "                    </Employee>\r\n" +  //$NON-NLS-1$
//            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Employee>\r\n" +  //$NON-NLS-1$
            "            <Employee ID=\"03\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>John</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Smith</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates>\r\n" + //$NON-NLS-1$
            "                    <Employee ID=\"05\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Kevin</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Moore</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Employee>\r\n" +  //$NON-NLS-1$
            "        </Subordinates>\r\n" + //$NON-NLS-1$
            "    </Employee>\r\n" +  //$NON-NLS-1$
            "    <Employee ID=\"07\">\r\n" +  //$NON-NLS-1$
            "        <FirstName>Geoff</FirstName>\r\n" + //$NON-NLS-1$
            "        <LastName>Tate</LastName>\r\n" + //$NON-NLS-1$
            "        <Subordinates>\r\n" + //$NON-NLS-1$
            "            <Employee ID=\"08\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>Les</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Claypool</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates>\r\n" + //$NON-NLS-1$
            "                    <Employee ID=\"09\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Meat</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Loaf</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                    <Employee ID=\"10\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Keith</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Sweat</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Employee>\r\n" +  //$NON-NLS-1$
            "        </Subordinates>\r\n" + //$NON-NLS-1$
            "    </Employee>\r\n" +  //$NON-NLS-1$
            "</OrgHierarchy>\r\n\r\n";  //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc13", expectedDoc, metadata, dataMgr); //$NON-NLS-1$
    }
    public void testRecursive3() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<OrgHierarchy>\r\n" +  //$NON-NLS-1$
            "    <Employee ID=\"01\">\r\n" +  //$NON-NLS-1$
            "        <FirstName>Ted</FirstName>\r\n" + //$NON-NLS-1$
            "        <LastName>Nugent</LastName>\r\n" + //$NON-NLS-1$
            "        <Subordinates>\r\n" + //$NON-NLS-1$
            "            <Employee ID=\"02\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Squier</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates>\r\n" + //$NON-NLS-1$
            "                    <Employee ID=\"04\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Leland</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Sklar</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Employee>\r\n" +  //$NON-NLS-1$
            "            <Employee ID=\"03\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>John</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Smith</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates>\r\n" + //$NON-NLS-1$
            "                    <Employee ID=\"05\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Kevin</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Moore</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Employee>\r\n" +  //$NON-NLS-1$
            "        </Subordinates>\r\n" + //$NON-NLS-1$
            "    </Employee>\r\n" +  //$NON-NLS-1$
            "    <Employee ID=\"07\">\r\n" +  //$NON-NLS-1$
            "        <FirstName>Geoff</FirstName>\r\n" + //$NON-NLS-1$
            "        <LastName>Tate</LastName>\r\n" + //$NON-NLS-1$
            "        <Subordinates>\r\n" + //$NON-NLS-1$
            "            <Employee ID=\"08\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>Les</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Claypool</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates>\r\n" + //$NON-NLS-1$
            "                    <Employee ID=\"09\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Meat</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Loaf</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                    <Employee ID=\"10\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Keith</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Sweat</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Employee>\r\n" +  //$NON-NLS-1$
            "        </Subordinates>\r\n" + //$NON-NLS-1$
            "    </Employee>\r\n" +  //$NON-NLS-1$
            "</OrgHierarchy>\r\n\r\n";  //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc14", expectedDoc, metadata, dataMgr); //$NON-NLS-1$
    }

    public void testRecursive4Exception() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        helpTestProcess("SELECT * FROM xmltest.doc15", null, metadata, dataMgr, false, MetaMatrixComponentException.class, "Query processing should have failed on recursion limit."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Seems to be failing as a result of changes for defect 12288 
     */
    public void testRecursive5() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Employees>\r\n" +  //$NON-NLS-1$
            "    <Employee ID=\"08\">\r\n" +  //$NON-NLS-1$
            "        <FirstName>Les</FirstName>\r\n" + //$NON-NLS-1$
            "        <LastName>Claypool</LastName>\r\n" + //$NON-NLS-1$
            "        <Supervisor ID=\"07\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Geoff</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>Tate</LastName>\r\n" + //$NON-NLS-1$
            "        </Supervisor>\r\n" + //$NON-NLS-1$
            "    </Employee>\r\n" +  //$NON-NLS-1$
            "    <Employee ID=\"09\">\r\n" +  //$NON-NLS-1$
            "        <FirstName>Meat</FirstName>\r\n" + //$NON-NLS-1$
            "        <LastName>Loaf</LastName>\r\n" + //$NON-NLS-1$
            "        <Supervisor ID=\"08\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Les</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>Claypool</LastName>\r\n" + //$NON-NLS-1$
            "            <Supervisor ID=\"07\">\r\n" + //$NON-NLS-1$
            "                <FirstName>Geoff</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Tate</LastName>\r\n" + //$NON-NLS-1$
            "            </Supervisor>\r\n" + //$NON-NLS-1$
            "        </Supervisor>\r\n" + //$NON-NLS-1$
            "    </Employee>\r\n" +  //$NON-NLS-1$
            "    <Employee ID=\"10\">\r\n" +  //$NON-NLS-1$
            "        <FirstName>Keith</FirstName>\r\n" + //$NON-NLS-1$
            "        <LastName>Sweat</LastName>\r\n" + //$NON-NLS-1$
            "        <Supervisor ID=\"08\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Les</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>Claypool</LastName>\r\n" + //$NON-NLS-1$
            "            <Supervisor ID=\"07\">\r\n" + //$NON-NLS-1$
            "                <FirstName>Geoff</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Tate</LastName>\r\n" + //$NON-NLS-1$
            "            </Supervisor>\r\n" + //$NON-NLS-1$
            "        </Supervisor>\r\n" + //$NON-NLS-1$
            "    </Employee>\r\n" +  //$NON-NLS-1$
            "</Employees>\r\n\r\n";  //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc16", expectedDoc, metadata, dataMgr); //$NON-NLS-1$
    }

    /**
     * sbale 4/27/05 I have changed expected results as a result of changes for
     * Booz Allen POC.  Previously, the recursive fragment of the document that
     * satisfied the recursion termination criteria was included, now it is not.
     * See commented out section below for previous expected results. 
     * @throws Exception
     */
    public void testRecursiveWithStagingTable_defect15607() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<OrgHierarchy>\r\n" +  //$NON-NLS-1$
            "    <Employee ID=\"01\">\r\n" +  //$NON-NLS-1$
            "        <FirstName>Ted</FirstName>\r\n" + //$NON-NLS-1$
            "        <LastName>Nugent</LastName>\r\n" + //$NON-NLS-1$
            "        <Subordinates>\r\n" + //$NON-NLS-1$
            "            <Employee ID=\"02\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Squier</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates/>\r\n" + //$NON-NLS-1$
//            "                <Subordinates>\r\n" + //$NON-NLS-1$
//            "                    <Employee ID=\"04\">\r\n" +  //$NON-NLS-1$
//            "                        <FirstName>Leland</FirstName>\r\n" + //$NON-NLS-1$
//            "                        <LastName>Sklar</LastName>\r\n" + //$NON-NLS-1$
//            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
//            "                    </Employee>\r\n" +  //$NON-NLS-1$
//            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Employee>\r\n" +  //$NON-NLS-1$
            "            <Employee ID=\"03\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>John</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Smith</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates>\r\n" + //$NON-NLS-1$
            "                    <Employee ID=\"05\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Kevin</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Moore</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Employee>\r\n" +  //$NON-NLS-1$
            "        </Subordinates>\r\n" + //$NON-NLS-1$
            "    </Employee>\r\n" +  //$NON-NLS-1$
            "    <Employee ID=\"07\">\r\n" +  //$NON-NLS-1$
            "        <FirstName>Geoff</FirstName>\r\n" + //$NON-NLS-1$
            "        <LastName>Tate</LastName>\r\n" + //$NON-NLS-1$
            "        <Subordinates>\r\n" + //$NON-NLS-1$
            "            <Employee ID=\"08\">\r\n" +  //$NON-NLS-1$
            "                <FirstName>Les</FirstName>\r\n" + //$NON-NLS-1$
            "                <LastName>Claypool</LastName>\r\n" + //$NON-NLS-1$
            "                <Subordinates>\r\n" + //$NON-NLS-1$
            "                    <Employee ID=\"09\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Meat</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Loaf</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                    <Employee ID=\"10\">\r\n" +  //$NON-NLS-1$
            "                        <FirstName>Keith</FirstName>\r\n" + //$NON-NLS-1$
            "                        <LastName>Sweat</LastName>\r\n" + //$NON-NLS-1$
            "                        <Subordinates/>\r\n" + //$NON-NLS-1$
            "                    </Employee>\r\n" +  //$NON-NLS-1$
            "                </Subordinates>\r\n" + //$NON-NLS-1$
            "            </Employee>\r\n" +  //$NON-NLS-1$
            "        </Subordinates>\r\n" + //$NON-NLS-1$
            "    </Employee>\r\n" +  //$NON-NLS-1$
            "</OrgHierarchy>\r\n\r\n";  //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc19", expectedDoc, metadata, dataMgr); //$NON-NLS-1$
    }
    
    /**
     * Tests a recursive nested mapping class within a recursive mapping class, where
     * all nested "anchor" nodes are named "srcNestedRecursive".  Test of defect #5988
     */
    public void testXQTRecursive_5988() throws Exception {
        FakeMetadataFacade metadata = exampleMetadata2();
        FakeDataManager dataMgr = exampleXQTDataManager(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<recursiveTest>\r\n" +  //$NON-NLS-1$
            "    <src>\r\n" + //$NON-NLS-1$
            "        <key>13</key>\r\n" + //$NON-NLS-1$
            "        <data>10</data>\r\n" + //$NON-NLS-1$
            "        <srcNested>\r\n" + //$NON-NLS-1$
            "            <key>10</key>\r\n" + //$NON-NLS-1$
            "            <data>7</data>\r\n" + //$NON-NLS-1$
            "            <srcNestedRecursive>\r\n" + //$NON-NLS-1$
            "                <key>7</key>\r\n" + //$NON-NLS-1$
            "                <data>4</data>\r\n" + //$NON-NLS-1$
            "                <srcNestedRecursive>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcNestedRecursive>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcNestedRecursive>\r\n" + //$NON-NLS-1$
            "                </srcNestedRecursive>\r\n" + //$NON-NLS-1$
            "            </srcNestedRecursive>\r\n" + //$NON-NLS-1$
            "        </srcNested>\r\n" + //$NON-NLS-1$
            "        <srcRecursive>\r\n" + //$NON-NLS-1$
            "            <key>10</key>\r\n" + //$NON-NLS-1$
            "            <data>7</data>\r\n" + //$NON-NLS-1$
            "            <srcNested>\r\n" + //$NON-NLS-1$
            "                <key>7</key>\r\n" + //$NON-NLS-1$
            "                <data>4</data>\r\n" + //$NON-NLS-1$
            "                <srcNestedRecursive>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcNestedRecursive>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcNestedRecursive>\r\n" + //$NON-NLS-1$
            "                </srcNestedRecursive>\r\n" + //$NON-NLS-1$
            "            </srcNested>\r\n" + //$NON-NLS-1$
            "            <srcRecursive>\r\n" + //$NON-NLS-1$
            "                <key>7</key>\r\n" + //$NON-NLS-1$
            "                <data>4</data>\r\n" + //$NON-NLS-1$
            "                <srcNested>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcNestedRecursive>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcNestedRecursive>\r\n" + //$NON-NLS-1$
            "                </srcNested>\r\n" + //$NON-NLS-1$
            "                <srcRecursive>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcNested>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcNested>\r\n" + //$NON-NLS-1$
            "                    <srcRecursive>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcRecursive>\r\n" + //$NON-NLS-1$
            "                </srcRecursive>\r\n" + //$NON-NLS-1$
            "            </srcRecursive>\r\n" + //$NON-NLS-1$
            "        </srcRecursive>\r\n" + //$NON-NLS-1$
            "    </src>\r\n" + //$NON-NLS-1$
            "</recursiveTest>\r\n\r\n";  //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xqttest.doc1", expectedDoc, metadata, dataMgr); //$NON-NLS-1$
    }

    /**
     * Tests a non-recursive nested mapping class within a recursive mapping class, where
     * all nested "anchor" nodes are named "srcNested".  Test of defect #5988
     */
    public void DEFER_testXQTRecursive1a_5988() throws Exception {
        FakeMetadataFacade metadata = exampleMetadata2();
        FakeDataManager dataMgr = exampleXQTDataManager(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<recursiveTest>\r\n" +  //$NON-NLS-1$
            "    <src>\r\n" + //$NON-NLS-1$
            "        <key>13</key>\r\n" + //$NON-NLS-1$
            "        <data>10</data>\r\n" + //$NON-NLS-1$
            "        <srcNested>\r\n" + //$NON-NLS-1$
            "            <key>10</key>\r\n" + //$NON-NLS-1$
            "            <data>7</data>\r\n" + //$NON-NLS-1$
            "        </srcNested>\r\n" + //$NON-NLS-1$
            "        <srcNested>\r\n" + //$NON-NLS-1$
            "            <key>10</key>\r\n" + //$NON-NLS-1$
            "            <data>7</data>\r\n" + //$NON-NLS-1$
            "            <srcNested>\r\n" + //$NON-NLS-1$
            "                <key>7</key>\r\n" + //$NON-NLS-1$
            "                <data>4</data>\r\n" + //$NON-NLS-1$
            "            </srcNested>\r\n" + //$NON-NLS-1$
            "            <srcNested>\r\n" + //$NON-NLS-1$
            "                <key>7</key>\r\n" + //$NON-NLS-1$
            "                <data>4</data>\r\n" + //$NON-NLS-1$
            "                <srcNested>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                </srcNested>\r\n" + //$NON-NLS-1$
            "                <srcNested>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcNested>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcNested>\r\n" + //$NON-NLS-1$
            "                    <srcNested>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcNested>\r\n" + //$NON-NLS-1$
            "                </srcNested>\r\n" + //$NON-NLS-1$
            "            </srcNested>\r\n" + //$NON-NLS-1$
            "        </srcNested>\r\n" + //$NON-NLS-1$
            "    </src>\r\n" + //$NON-NLS-1$
            "</recursiveTest>\r\n\r\n";  //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xqttest.doc1a", expectedDoc, metadata, dataMgr); //$NON-NLS-1$
    }

    /**
     * Tests a non-recursive nested mapping class within a recursive mapping class, where
     * all nested "anchor" nodes are named "srcNested".  Test of defect #5988
     */
    public void testXQTRecursive2_5988() throws Exception {
        FakeMetadataFacade metadata = exampleMetadata2();
        FakeDataManager dataMgr = exampleXQTDataManager(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<recursiveTest>\r\n" +  //$NON-NLS-1$
            "    <src>\r\n" + //$NON-NLS-1$
            "        <key>13</key>\r\n" + //$NON-NLS-1$
            "        <data>10</data>\r\n" + //$NON-NLS-1$
            "        <srcNested>\r\n" + //$NON-NLS-1$
            "            <key>10</key>\r\n" + //$NON-NLS-1$
            "            <data>7</data>\r\n" + //$NON-NLS-1$
            "        </srcNested>\r\n" + //$NON-NLS-1$
            "        <srcNested>\r\n" + //$NON-NLS-1$
            "            <key>10</key>\r\n" + //$NON-NLS-1$
            "            <data>7</data>\r\n" + //$NON-NLS-1$
            "            <srcNested>\r\n" + //$NON-NLS-1$
            "                <key>7</key>\r\n" + //$NON-NLS-1$
            "                <data>4</data>\r\n" + //$NON-NLS-1$
            "            </srcNested>\r\n" + //$NON-NLS-1$
            "            <srcNested>\r\n" + //$NON-NLS-1$
            "                <key>7</key>\r\n" + //$NON-NLS-1$
            "                <data>4</data>\r\n" + //$NON-NLS-1$
            "                <srcNested>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                </srcNested>\r\n" + //$NON-NLS-1$
            "                <srcNested>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcNested>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcNested>\r\n" + //$NON-NLS-1$
            "                    <srcNested>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcNested>\r\n" + //$NON-NLS-1$
            "                </srcNested>\r\n" + //$NON-NLS-1$
            "            </srcNested>\r\n" + //$NON-NLS-1$
            "        </srcNested>\r\n" + //$NON-NLS-1$
            "    </src>\r\n" + //$NON-NLS-1$
            "</recursiveTest>\r\n\r\n";  //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xqttest.doc3", expectedDoc, metadata, dataMgr); //$NON-NLS-1$
    }


    /**
     * for defect 5988
     */
    public void testXQTRecursiveSiblings_5988() throws Exception {
        FakeMetadataFacade metadata = exampleMetadata2();
        FakeDataManager dataMgr = exampleXQTDataManager(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<recursiveTest>\r\n" +  //$NON-NLS-1$
            "    <src>\r\n" + //$NON-NLS-1$
            "        <key>13</key>\r\n" + //$NON-NLS-1$
            "        <data>10</data>\r\n" + //$NON-NLS-1$
            "        <srcSibling1>\r\n" + //$NON-NLS-1$
            "            <key>10</key>\r\n" + //$NON-NLS-1$
            "            <data>7</data>\r\n" + //$NON-NLS-1$
            "            <srcSibling1>\r\n" + //$NON-NLS-1$
            "                <key>7</key>\r\n" + //$NON-NLS-1$
            "                <data>4</data>\r\n" + //$NON-NLS-1$
            "                <srcSibling1>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcSibling1>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling1>\r\n" + //$NON-NLS-1$
            "                    <srcSibling2>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling2>\r\n" + //$NON-NLS-1$
            "                </srcSibling1>\r\n" + //$NON-NLS-1$
            "                <srcSibling2>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcSibling1>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling1>\r\n" + //$NON-NLS-1$
            "                    <srcSibling2>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling2>\r\n" + //$NON-NLS-1$
            "                </srcSibling2>\r\n" + //$NON-NLS-1$
            "            </srcSibling1>\r\n" + //$NON-NLS-1$
            "            <srcSibling2>\r\n" + //$NON-NLS-1$
            "                <key>7</key>\r\n" + //$NON-NLS-1$
            "                <data>4</data>\r\n" + //$NON-NLS-1$
            "                <srcSibling1>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcSibling1>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling1>\r\n" + //$NON-NLS-1$
            "                    <srcSibling2>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling2>\r\n" + //$NON-NLS-1$
            "                </srcSibling1>\r\n" + //$NON-NLS-1$
            "                <srcSibling2>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcSibling1>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling1>\r\n" + //$NON-NLS-1$
            "                    <srcSibling2>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling2>\r\n" + //$NON-NLS-1$
            "                </srcSibling2>\r\n" + //$NON-NLS-1$
            "            </srcSibling2>\r\n" + //$NON-NLS-1$
            "        </srcSibling1>\r\n" + //$NON-NLS-1$
            "        <srcSibling2>\r\n" + //$NON-NLS-1$
            "            <key>10</key>\r\n" + //$NON-NLS-1$
            "            <data>7</data>\r\n" + //$NON-NLS-1$
            "            <srcSibling1>\r\n" + //$NON-NLS-1$
            "                <key>7</key>\r\n" + //$NON-NLS-1$
            "                <data>4</data>\r\n" + //$NON-NLS-1$
            "                <srcSibling1>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcSibling1>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling1>\r\n" + //$NON-NLS-1$
            "                    <srcSibling2>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling2>\r\n" + //$NON-NLS-1$
            "                </srcSibling1>\r\n" + //$NON-NLS-1$
            "                <srcSibling2>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcSibling1>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling1>\r\n" + //$NON-NLS-1$
            "                    <srcSibling2>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling2>\r\n" + //$NON-NLS-1$
            "                </srcSibling2>\r\n" + //$NON-NLS-1$
            "            </srcSibling1>\r\n" + //$NON-NLS-1$
            "            <srcSibling2>\r\n" + //$NON-NLS-1$
            "                <key>7</key>\r\n" + //$NON-NLS-1$
            "                <data>4</data>\r\n" + //$NON-NLS-1$
            "                <srcSibling1>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcSibling1>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling1>\r\n" + //$NON-NLS-1$
            "                    <srcSibling2>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling2>\r\n" + //$NON-NLS-1$
            "                </srcSibling1>\r\n" + //$NON-NLS-1$
            "                <srcSibling2>\r\n" + //$NON-NLS-1$
            "                    <key>4</key>\r\n" + //$NON-NLS-1$
            "                    <data>1</data>\r\n" + //$NON-NLS-1$
            "                    <srcSibling1>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling1>\r\n" + //$NON-NLS-1$
            "                    <srcSibling2>\r\n" + //$NON-NLS-1$
            "                        <key>1</key>\r\n" + //$NON-NLS-1$
            "                        <data>-2</data>\r\n" + //$NON-NLS-1$
            "                    </srcSibling2>\r\n" + //$NON-NLS-1$
            "                </srcSibling2>\r\n" + //$NON-NLS-1$
            "            </srcSibling2>\r\n" + //$NON-NLS-1$
            "        </srcSibling2>\r\n" + //$NON-NLS-1$
            "    </src>\r\n" + //$NON-NLS-1$
            "</recursiveTest>\r\n\r\n";  //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xqttest.doc2", expectedDoc, metadata, dataMgr); //$NON-NLS-1$
    }

    public void testSelectElement1() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
          
        helpTestProcess("SELECT Name FROM xmltest.doc1", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
     
    public void testSelectElement2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>9</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>12</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +                  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
                
        helpTestProcess("SELECT SupplierID, OrderID, ItemID, OrderQuantity FROM xmltest.doc9 ORDER BY "+ //$NON-NLS-1$
            " Catalogs.Catalog.Items.Item.Suppliers.Supplier.SupplierID ASC," + //$NON-NLS-1$
            " OrderID DESC, Catalogs.Catalog.Items.Item.ItemID DESC", expectedDoc, metadata, dataMgr);           //$NON-NLS-1$
    }
    
    /** select element in the reverse order of depth*/
    public void testSelectElement3() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>9</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>12</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +                  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
                
        helpTestProcess("SELECT OrderQuantity, SupplierID, ItemID, OrderID FROM xmltest.doc9"  + //$NON-NLS-1$
            " ORDER BY Catalogs.Catalog.Items.Item.Suppliers.Supplier.SupplierID ASC," + //$NON-NLS-1$
            " OrderID DESC, Catalogs.Catalog.Items.Item.ItemID DESC", expectedDoc, metadata, dataMgr);           //$NON-NLS-1$
    }
    
    /** two select elements at the same level*/
    public void testSelectElement4() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +             //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
          
        helpTestProcess("SELECT ItemID, Name, Quantity FROM xmltest.doc1 WHERE ItemID='001' OR "  //$NON-NLS-1$
            + " ItemID='002' OR ItemID='003' ORDER BY ItemID", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }
    
    /** defect 9756 */
    public void testSelectElement4_defect9756() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\"/>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\"/>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\"/>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
          
        helpTestProcess("SELECT Catalogs.Catalog.Items.Item.@ItemID FROM xmltest.doc1", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }    
    
    /** three select elements with two of them at the same level and there are other nodes with the same name*/
    public void testSelectElement5() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" +           //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>9</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>12</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +                  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" +          //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" +            //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" +           //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" +          //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" +        //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" +            //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" +           //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
                
        helpTestProcess("SELECT OrderQuantity, SupplierID, Catalogs.Catalog.Items.Item.Suppliers.Supplier.Name, OrderID, ItemID "  //$NON-NLS-1$
            + "FROM xmltest.doc9 ORDER BY Catalogs.Catalog.Items.Item.Suppliers.Supplier.SupplierID ASC, "  //$NON-NLS-1$
            + "OrderID DESC, Catalogs.Catalog.Items.Item.ItemID DESC", expectedDoc, metadata, dataMgr);           //$NON-NLS-1$
    }
    
    /** check element.* case */
    public void testSelectElement6() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +             //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
         
        helpTestProcess("SELECT Item.* FROM xmltest.doc1 ORDER BY ItemID", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    /** check element.* case without attribute in order by*/
    public void testSelectElement6a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +             //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
         
        helpTestProcess("SELECT Catalogs.Catalog.Items.Item.Name, Catalogs.Catalog.Items.Item.Quantity" //$NON-NLS-1$
            + " FROM xmltest.doc1", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    public void testSelectElement7() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +             //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
         
        helpTestProcess("SELECT Catalogs.Catalog.Items.Item.* FROM xmltest.doc1 ORDER BY ItemID",  //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }
    
    public void testSelectElement8() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +             //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
         
        helpTestProcess("SELECT xmltest.doc1.Catalogs.Catalog.Items.Item.* FROM xmltest.doc1 ORDER BY ItemID",  //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }
    
    /** SELECT clause has element.*, but the sibling elements should not be included, only subtree should */
    public void testSelectElement9() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

      helpTestProcess("SELECT Catalogs.Catalog.Items.Item.Suppliers.Supplier.* FROM xmltest.doc9 "  //$NON-NLS-1$
          + "WHERE ItemID='002' AND Context(Supplier,OrderID)='5' ", //$NON-NLS-1$
          expectedDoc, metadata, dataMgr);        
    }         
    
    public void testSelectElement9a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT Catalogs.Catalog.Items.Item.Suppliers.Supplier.* FROM xmltest.doc9 "  //$NON-NLS-1$
            + "WHERE ItemID='002' AND Context(Supplier,OrderID)='5' AND Context(OrderID, OrderID)='5'", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }    
             
    /** check element.* case with criteria and order by clause */
    public void testSelectElement10() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +             //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
         
        helpTestProcess("SELECT Item.* FROM xmltest.doc1 WHERE Quantity <= 100 ORDER BY ItemID",  //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }
    
    public void testSelectElement12() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT Supplier.*, ItemID FROM xmltest.doc9 "  //$NON-NLS-1$
            + " WHERE ItemID='002'" //$NON-NLS-1$
            + " ORDER BY SupplierID, OrderID DESC, ItemID", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);                
    }
    
    public void testSelectElement13() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +             //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT Name, Quantity FROM xmltest.doc1 "  //$NON-NLS-1$
            + "WHERE ItemID='002'" //$NON-NLS-1$
            + "ORDER BY Name ", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);                
    }

    public void testSelectElement14() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +           //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +         //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders/>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                        <Orders>\r\n" + //$NON-NLS-1$
            "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" +  //$NON-NLS-1$
            "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
            "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
            "                            </Order>\r\n" + //$NON-NLS-1$
            "                        </Orders>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +           //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
               
            helpTestProcess("SELECT * FROM xmltest.doc9 WHERE ItemID='002' AND Catalogs.Catalog.Items.Item.Suppliers.Supplier.Orders.Order.OrderQuantity < 1000"  //$NON-NLS-1$
                + " ORDER By SupplierID ASC, OrderID ",  //$NON-NLS-1$
                expectedDoc, metadata, dataMgr);        
    }
    
    public void testSelectElement15() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
                "    <Catalog>\r\n" +  //$NON-NLS-1$
                "        <Items>\r\n" +           //$NON-NLS-1$
                "            <Item>\r\n" +  //$NON-NLS-1$
                "                <Suppliers>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
                "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders>\r\n" + //$NON-NLS-1$
                "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
                "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
                "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
                "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
                "                            </Order>\r\n" + //$NON-NLS-1$
                "                        </Orders>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
                "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders/>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
                "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders>\r\n" + //$NON-NLS-1$
                "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
                "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
                "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
                "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
                "                            </Order>\r\n" +  //$NON-NLS-1$
                "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
                "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
                "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
                "                            </Order>\r\n" + //$NON-NLS-1$
                "                        </Orders>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                </Suppliers>\r\n" + //$NON-NLS-1$
                "            </Item>\r\n" +           //$NON-NLS-1$
                "        </Items>\r\n" +  //$NON-NLS-1$
                "    </Catalog>\r\n" +  //$NON-NLS-1$
                "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
               
        helpTestProcess("SELECT Supplier.* FROM xmltest.doc9 "  //$NON-NLS-1$
            + " WHERE ItemID='002'" //$NON-NLS-1$
            + " ORDER By SupplierID ",  //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }

    public void testSelectElement16() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
                "<Catalogs>\r\n" + //$NON-NLS-1$
                "    <Catalog>\r\n" +  //$NON-NLS-1$
                "        <Items>\r\n" +           //$NON-NLS-1$
                "            <Item>\r\n" +  //$NON-NLS-1$
                "                <Suppliers>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
                "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders>\r\n" + //$NON-NLS-1$
                "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
                "                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
                "                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
                "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
                "                            </Order>\r\n" + //$NON-NLS-1$
                "                        </Orders>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
                "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders/>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
                "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
                "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
                "                        <Orders>\r\n" + //$NON-NLS-1$
                "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
                "                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
                "                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
                "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
                "                            </Order>\r\n" +  //$NON-NLS-1$
                "                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
                "                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
                "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
                "                            </Order>\r\n" + //$NON-NLS-1$
                "                        </Orders>\r\n" + //$NON-NLS-1$
                "                    </Supplier>\r\n" + //$NON-NLS-1$
                "                </Suppliers>\r\n" + //$NON-NLS-1$
                "            </Item>\r\n" +           //$NON-NLS-1$
                "        </Items>\r\n" +  //$NON-NLS-1$
                "    </Catalog>\r\n" +  //$NON-NLS-1$
                "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
               
        helpTestProcess("SELECT Supplier.* FROM xmltest.doc9a "  //$NON-NLS-1$
            + " WHERE ItemID='002'" //$NON-NLS-1$
            + " ORDER By SupplierID",  //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }         

    /**  CSE query 0 */
    public void testSelectElement17() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                        <Name>KMart</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>12345</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                        <Name>Sun</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94040</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
            "                        <Name>Cisco</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94041</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                        <Name>Doc</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94042</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +             //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" +              //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                        <Name>Excite</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>21098</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                        <Name>Yahoo</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94043</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +         //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"7\">\r\n" + //$NON-NLS-1$
            "                        <Name>Inktomi</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94044</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                </Orders>\r\n" +            //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc9c ", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }   

    /**  CSE query 1 */
    public void testSelectElement18() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +              //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                        <Name>Excite</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>21098</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                        <Name>Yahoo</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94043</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +         //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" +           //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc9c WHERE ItemID='002'", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }   

    /**  CSE query 2 */
    public void testSelectElement19() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +              //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                        <Name>Excite</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>21098</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                        <Name>Yahoo</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94043</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +         //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" +           //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc9c WHERE Catalogs.Catalog.Items.Item.Name='Screwdriver'", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }   
            

    /**  CSE query 3 */
    public void testSelectElement20() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +              //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +           //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT ItemID, Catalogs.Catalog.Items.Item.Quantity, Catalogs.Catalog.Items.Item.Suppliers.Supplier.Zip" + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE Catalogs.Catalog.Items.Item.Name='Screwdriver'", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }   

    /**  CSE query 3a */
    public void testSelectElement20a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +              //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order>\r\n" + //$NON-NLS-1$
            "                        <Zip>21098</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order>\r\n" + //$NON-NLS-1$
            "                        <Zip>94043</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +         //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" +           //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT ItemID, Catalogs.Catalog.Items.Item.Quantity, Catalogs.Catalog.Items.Item.Orders.Order.Zip" + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE Catalogs.Catalog.Items.Item.Name='Screwdriver'", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }   

    /**  CSE query 4 */
    public void testSelectElement21() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                        <Name>KMart</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>12345</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                        <Name>Sun</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94040</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
            "                        <Name>Cisco</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94041</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                        <Name>Doc</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94042</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +             //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" +              //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                        <Name>Excite</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>21098</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                        <Name>Yahoo</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94043</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +         //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"7\">\r\n" + //$NON-NLS-1$
            "                        <Name>Inktomi</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94044</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                </Orders>\r\n" +            //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
       
        helpTestProcess("SELECT * " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE SupplierID > '54' AND context(SupplierID, SupplierID)>'54' ", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }  

    /**  CSE query 4a */
    public void testSelectElement21a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +          //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                        <Name>Excite</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>21098</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                        <Name>Yahoo</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94043</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +         //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"7\">\r\n" + //$NON-NLS-1$
            "                        <Name>Inktomi</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94044</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                </Orders>\r\n" +            //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
            
        helpTestProcess("SELECT * " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE OrderID > '4' AND context(OrderID, OrderID)>'4'", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }  
    
    /**  CSE query 5 */
    public void testSelectElement22() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +              //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" +         //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT ItemID, Catalogs.Catalog.Items.Item.Name, Catalogs.Catalog.Items.Item.Suppliers.Supplier.Zip " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE SupplierID > '4' ", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    } 
    
    /**  CSE query 5a */
    public void testSelectElement22a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                        <Name>KMart</Name>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                        <Name>Sun</Name>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
            "                        <Name>Cisco</Name>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                        <Name>Doc</Name>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +             //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" +              //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                        <Name>Excite</Name>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                        <Name>Yahoo</Name>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +         //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"7\">\r\n" + //$NON-NLS-1$
            "                        <Name>Inktomi</Name>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                </Orders>\r\n" +            //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT ItemID, Catalogs.Catalog.Items.Item.Name, Catalogs.Catalog.Items.Item.Orders.Order.Name, OrderID  " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE OrderID > '3' ", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    } 
    
    /**  CSE query 6 */
    public void testSelectElement23() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);

        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +      //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" +         //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
            
        helpTestProcess("SELECT Catalogs.Catalog.Items.Item.Name, Catalogs.Catalog.Items.Item.Suppliers.Supplier.Name, SupplierID " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE OrderID > '4' ", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    } 
    
    /**  CSE query 6a */
    public void testSelectElement23a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"1\"/>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"2\"/>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"3\"/>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"4\"/>\r\n" +       //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" +              //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"5\"/>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"6\"/>\r\n" +   //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"7\"/>\r\n" + //$NON-NLS-1$
            "                </Orders>\r\n" +            //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        
        helpTestProcess("SELECT Catalogs.Catalog.Items.Item.ItemID, OrderID " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE SupplierID > '54' ", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }
                     
    /** test with order by and the element in the criteria is not in the select elements*/
    public void testSelectElement24() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"7\"/>\r\n" + //$NON-NLS-1$
            "                </Orders>\r\n" +            //$NON-NLS-1$
            "            </Item>\r\n" +          //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"6\"/>\r\n" +               //$NON-NLS-1$
            "                    <Order OrderID=\"5\"/>\r\n" + //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" +                          //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"4\"/>\r\n" +   //$NON-NLS-1$
            "                    <Order OrderID=\"3\"/>\r\n" +                   //$NON-NLS-1$
            "                    <Order OrderID=\"2\"/>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"1\"/>\r\n" + //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        
        helpTestProcess("SELECT Catalogs.Catalog.Items.Item.ITEMID, OrderID " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE SupplierID > '54' " + //$NON-NLS-1$
            " ORDER BY ItemID DESC, OrderID DESC", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }

    /** test element.* with order by and the element in the criteria is not in the select elements*/
    public void testSelectElement24a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"7\">\r\n" + //$NON-NLS-1$
            "                        <Name>Inktomi</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94044</Zip>\r\n" +             //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                </Orders>\r\n" +            //$NON-NLS-1$
            "            </Item>\r\n" +          //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                        <Name>Yahoo</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94043</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +                 //$NON-NLS-1$
            "                    <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                        <Name>Excite</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>21098</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" +                          //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                        <Name>Doc</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94042</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
            "                        <Name>Cisco</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94041</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +                 //$NON-NLS-1$
            "                    <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                        <Name>Sun</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94040</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                        <Name>KMart</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>12345</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +         //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        
        helpTestProcess("SELECT Catalogs.Catalog.Items.Item.ItemID, Order.* " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE SupplierID > '54' " + //$NON-NLS-1$
            " ORDER BY ItemID DESC, OrderID DESC", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }
                         
    /** test with order by with only necessary sub-mapping classes are queried*/
    public void testSelectElement25() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\"/>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" +             //$NON-NLS-1$
            "            </Item>\r\n" +          //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\"/>\r\n" +                //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\"/>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\"/>\r\n" +                //$NON-NLS-1$
            "                </Suppliers>\r\n" +     //$NON-NLS-1$
            "            </Item>\r\n" +                          //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\"/>\r\n" +    //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\"/>\r\n" +                    //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\"/>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\"/>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" +     //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        
        helpTestProcess("SELECT ItemID, SupplierID " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE SupplierID > '54' " + //$NON-NLS-1$
            " ORDER BY ItemID DESC, SupplierID DESC", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }
    
    /** test element.* with order by with only necessary sub-mapping classes are queried*/
    public void testSelectElement25a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                </Suppliers>\r\n" +             //$NON-NLS-1$
            "            </Item>\r\n" +          //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +          //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +                          //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +              //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" +     //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        
        helpTestProcess("SELECT ItemID, Supplier.* " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE SupplierID > '54' " + //$NON-NLS-1$
            " ORDER BY ItemID DESC, SupplierID DESC", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }
    
    /** Test element.* with order by with only necessary sub-mapping classes are queried 
     *  and case_insensitive nodes in the mapping tree
     */
    public void testSelectElement25b() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                </Suppliers>\r\n" +             //$NON-NLS-1$
            "            </Item>\r\n" +          //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +          //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +                          //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +              //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" +     //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        
        helpTestProcess("SELECT ItemID, SUPPLIER.* " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE SupplierID > '54' " + //$NON-NLS-1$
            " ORDER BY ItemID DESC, SupplierID DESC", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }
    
    public void testSelectElement26() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                </Suppliers>\r\n" +             //$NON-NLS-1$
            "            </Item>\r\n" +          //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +          //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +                          //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +              //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" +     //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        
        helpTestProcess("SELECT ItemID, xmltest.doc9c.catalogs.catalog.items.item.suppliers.SUPPLIER.* " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE SupplierID > '54' " + //$NON-NLS-1$
            " ORDER BY ItemID DESC, SupplierID DESC", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }

    /** test special elements: result set name, and name with format of "document.fully.qualified.element" 
     * --> refer to Defect9497, this should fail
     */
    public void testSelectElement27() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +          //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier/>\r\n" + //$NON-NLS-1$
            "                    <Supplier/>\r\n" + //$NON-NLS-1$
            "                    <Supplier/>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        helpTestProcess("SELECT doc9c.catalogs.catalog.items.item.itemID, items.item.name, item.Quantity, supplier " + //$NON-NLS-1$
        " FROM xmltest.doc9c " + //$NON-NLS-1$
        " WHERE doc9c.catalogs.catalog.items.item.suppliers.supplier.SupplierID > '54' AND itemID='002'" + //$NON-NLS-1$
        " ORDER BY doc9c.catalogs.catalog.items.item.ITEMid ", //$NON-NLS-1$
        expectedDoc, metadata, dataMgr);     
    }
        
    /** test special element, root element */
    public void testSelectElement28() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs/>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT catalogs " +  //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE item.suppliers.supplier.SupplierID > '54' AND itemID='002'", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);       
        
        /*String expectedDoc = "";
        try {
            helpTestProcess("SELECT catalogs " +
                " FROM xmltest.doc9c " +
                " WHERE item.suppliers.supplier.SupplierID > '54' AND itemID='002'",
                expectedDoc, metadata, dataMgr);
        } catch(QueryPlannerException qpe) {
        // ok, as expected
        } */         
    }
    
    /** test special element */
    public void testSelectElement28a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc =  
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog/>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        /*String expectedDoc = "";
        try {
            helpTestProcess("SELECT catalog " +
            " FROM xmltest.doc9c " +
            " WHERE item.suppliers.supplier.SupplierID > '54' AND itemID='002'",
            expectedDoc, metadata, dataMgr, false);
        } catch (QueryPlannerException qpe) {
        // ok, as expected
        } */
        
        helpTestProcess("SELECT catalog " +  //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE item.suppliers.supplier.SupplierID > '54' AND itemID='002'", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);             
    }

    /** test model.document.* */
    public void testSelectElement28b() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
            
        helpTestProcess("SELECT xmltest.doc1.* " +  //$NON-NLS-1$
            "FROM xmltest.doc1 ORDER BY ItemID ", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);       

    }
        
    /** test special element, root element */
    public void testSelectElement29() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs/>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT xmltest.doc9c.catalogs " +  //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE item.suppliers.supplier.SupplierID > '54' AND itemID='002'", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);             
        
        /*String expectedDoc = "";
        try {
            helpTestProcess("SELECT xmltest.doc9c.catalogs " +
            " FROM xmltest.doc9c " +
            " WHERE item.suppliers.supplier.SupplierID > '54' AND itemID='002'",
            expectedDoc, metadata, dataMgr);
        } catch (QueryPlannerException qpe) {
        // ok, as expected
        }*/

    }
    
    /** test simple case for two elements in a mapping class */
    public void testSelectElement30() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT item.* FROM xmltest.doc1 WHERE ItemID='001' AND Quantity < 60 ORDER BY ItemID", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }     
                        
    /** test NullPointerException*/
    public void testDefect_9496_1() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                </Suppliers>\r\n" +             //$NON-NLS-1$
            "            </Item>\r\n" +          //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +          //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +                          //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +              //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" +     //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        
        helpTestProcess("SELECT items.item.ItemID, suppliers.supplier.* " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE item.suppliers.supplier.SupplierID > '54' " + //$NON-NLS-1$
            " ORDER BY  ITEMid DESC, items.item.suppliers.supplier.SupplierID DESC", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }

    public void testDefect_9496_2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                </Suppliers>\r\n" +             //$NON-NLS-1$
            "            </Item>\r\n" +          //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +          //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +                          //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +  //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" +              //$NON-NLS-1$
            "                    <Supplier>\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" +     //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        
        helpTestProcess("SELECT doc9c.Catalogs.catalog.items.item.ItemID, items.item.Suppliers,SuppliER.Name " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE SupplierID > '54' " + //$NON-NLS-1$
            " ORDER BY ItemID DESC", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }
    
    /** test StringIndexOutOfBoundsException */
    public void testDefect_9496_3() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
            "                        <Name>KMart</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>12345</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
            "                        <Name>Sun</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94040</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
            "                        <Name>Cisco</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94041</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
            "                        <Name>Doc</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94042</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +             //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" +              //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
            "                        <Name>Excite</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>21098</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                    <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
            "                        <Name>Yahoo</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94043</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +         //$NON-NLS-1$
            "                </Orders>\r\n" +    //$NON-NLS-1$
            "            </Item>\r\n" + //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "                <Orders>\r\n" + //$NON-NLS-1$
            "                    <Order OrderID=\"7\">\r\n" + //$NON-NLS-1$
            "                        <Name>Inktomi</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>94044</Zip>\r\n" + //$NON-NLS-1$
            "                    </Order>\r\n" +  //$NON-NLS-1$
            "                </Orders>\r\n" +            //$NON-NLS-1$
            "            </Item>\r\n" +               //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        
        helpTestProcess("SELECT item.itemID, items.item.* " + //$NON-NLS-1$
            " FROM xmltest.doc9c " + //$NON-NLS-1$
            " WHERE item.suppliers.supplier.SupplierID > '54' " + //$NON-NLS-1$
            " ORDER BY  ITEMid, items.item.suppliers.supplier.SupplierID ", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }
    
    /** should fail: because there are other element other than "xml" */
    /*public void testResolver1() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = ""; 

        boolean shouldSucceed = false;
        Class expectedException = QueryResolverException.class;
        String shouldFailMsg = "If any symbol in SELECT clause is 'xml' or group.'xml' , then no other element is allowed.";
        
        helpTestProcess("SELECT xml, ItemID " +
            " FROM xmltest.doc9c " +
            " WHERE SupplierID > '54' " +
            " ORDER BY ItemID DESC", expectedDoc, metadata, dataMgr, shouldSucceed, expectedException, shouldFailMsg);          
    }*/

    /** should fail: partial qualified element name and "model.document.xml" */
    /*public void testResolver2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = ""; 

        boolean shouldSucceed = false;
        Class expectedException = QueryResolverException.class;
        String shouldFailMsg = "If any symbol in SELECT clause is 'xml' or group.'xml' , then no other element is allowed.";
        
        helpTestProcess("SELECT item.ItemID, xmltest.doc9c.xml " +
            " FROM xmltest.doc9c " +
            " WHERE SupplierID > '54' " +
            " ORDER BY ItemID DESC", expectedDoc, metadata, dataMgr, shouldSucceed, expectedException, shouldFailMsg);          
    }*/

    /** should fail: test XMLResolver validatation for model.* */
    /*public void testDefect_9498_1() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = ""; 

        boolean shouldSucceed = false;
        Class expectedException = QueryResolverException.class;
        String shouldFailMsg = "Unable to resolve element: xmltest";

        helpTestProcess("SELECT xmltest.* FROM xmltest.doc9c ", expectedDoc, metadata, dataMgr, shouldSucceed, expectedException, shouldFailMsg);        
    }*/

    /** should fail: test XMLResolver validatation for model.document.* */
    /*public void testDefect_9498_2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = ""; 

        boolean shouldSucceed = false;
        Class expectedException = QueryResolverException.class;
        String shouldFailMsg = "Unable to resolve element: xmltest.doc9c";

        helpTestProcess("SELECT xmltest.doc9c.* FROM xmltest.doc9c ", expectedDoc, metadata, dataMgr, shouldSucceed, expectedException, shouldFailMsg);         
    }*/

    /** should fail: test XMLResolver validatation for xml.* */
    /*public void testDefect_9498_3() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = ""; 

        boolean shouldSucceed = false;
        Class expectedException = QueryResolverException.class;
        String shouldFailMsg = "Unable to resolve element: xml";

        helpTestProcess("SELECT xml.* FROM xmltest.doc9c ", expectedDoc, metadata, dataMgr, shouldSucceed, expectedException, shouldFailMsg);        
    }*/
   
    /** Test element.* with order by with only necessary sub-mapping classes are queried 
     *  and case_insensitive nodes in the mapping tree
     */
    public void testCommentNodeInDoc() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Root><!--Comment1--><Something><!--Comment2--></Something>\r\n" + //$NON-NLS-1$
            "</Root>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc17", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    }    

    private static final String EXPECTED_DOC_DEFECT_8917_AND_11789 = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
        "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
        "    <Catalog>\r\n" +  //$NON-NLS-1$
        "        <Items>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
        "                <Name xsi:nil=\"true\"/>\r\n" +  //$NON-NLS-1$
        "                <Quantity>5</Quantity>\r\n" +           //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "        </Items>\r\n" +  //$NON-NLS-1$
        "    </Catalog>\r\n" +  //$NON-NLS-1$
        "    <OptionalCatalog>\r\n" +  //$NON-NLS-1$
        "        <Items>\r\n" +  //$NON-NLS-1$
        "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
        "                <Name xsi:nil=\"true\"/>\r\n" +  //$NON-NLS-1$
        "                <Quantity>5</Quantity>\r\n" +           //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "        </Items>\r\n" +  //$NON-NLS-1$
        "    </OptionalCatalog>\r\n" +  //$NON-NLS-1$
        "    <OptionalCatalog2>\r\n" +  //$NON-NLS-1$
        "        <Items>\r\n" +  //$NON-NLS-1$
        "            <Item>\r\n" +  //$NON-NLS-1$
        "                <Name xsi:nil=\"true\"/>\r\n" +  //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "        </Items>\r\n" +  //$NON-NLS-1$
        "    </OptionalCatalog2>\r\n" +  //$NON-NLS-1$
        "    <Catalog4/>\r\n" +  //$NON-NLS-1$
        "    <Catalog5>\r\n" +  //$NON-NLS-1$
        "        <OptionalItems>\r\n" +  //$NON-NLS-1$
        "            <Item>\r\n" +  //$NON-NLS-1$
        "                <FixedName>Nugent</FixedName>\r\n" +  //$NON-NLS-1$
        "            </Item>\r\n" +  //$NON-NLS-1$
        "        </OptionalItems>\r\n" +  //$NON-NLS-1$
        "    </Catalog5>\r\n" +              //$NON-NLS-1$
        "    <Catalog6/>\r\n" +  //$NON-NLS-1$
        "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

    public void testDefect8917() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager_8917(metadata);
        
//        helpTestProcess("SELECT * FROM xmltest.doc_8917 WHERE Catalog.Items.Item.ItemID = '001'",
        helpTestProcess("SELECT * FROM xmltest.doc_8917", //$NON-NLS-1$
        EXPECTED_DOC_DEFECT_8917_AND_11789, metadata, dataMgr);        
    } 
    /*
     * jhTODO
     */
    public void testNillableOptional() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" + //$NON-NLS-1$
            "<Catalog>" +  //$NON-NLS-1$
            "<Items xsi:nil=\"true\"/>" +  //$NON-NLS-1$
            "</Catalog>" +  //$NON-NLS-1$
            "</Catalogs>"; //$NON-NLS-1$
       
        // note: doc1b contains an 'items' element that is nillable = true, and minoccurs = 0 
        helpTestProcess("SELECT * FROM xmltest.doc1b WHERE ItemID='9999' ", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    /*
     * jhTODO
     */
    public void testNillableNonOptional() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" + //$NON-NLS-1$
            "<Catalog>" +  //$NON-NLS-1$
            "<Items xsi:nil=\"true\"/>" +  //$NON-NLS-1$
            "</Catalog>" +  //$NON-NLS-1$
            "</Catalogs>"; //$NON-NLS-1$
        
        // note: doc1c contains an 'items' element that has no nillable set, and minoccurs = 1 
        helpTestProcess("SELECT * FROM xmltest.doc1c WHERE ItemID='9999' ", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    /** 
     * Related to defect 8917
     * The expected result is slightly different because the data has a
     * NON-empty whitespace string, which will NOT be treated as null
     * see also defect 15117
     */
    public void testDefect11789() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager_8917a(metadata);

        String expected = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name> </Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "    <OptionalCatalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name> </Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </OptionalCatalog>\r\n" +  //$NON-NLS-1$
            "    <OptionalCatalog2>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name> </Name>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </OptionalCatalog2>\r\n" +  //$NON-NLS-1$
            "    <OptionalCatalog3>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name> </Name>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </OptionalCatalog3>\r\n" +  //$NON-NLS-1$
            "    <Catalog4>\r\n" +  //$NON-NLS-1$
            "        <OptionalItems>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name> </Name>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </OptionalItems>\r\n" +  //$NON-NLS-1$
            "    </Catalog4>\r\n" +  //$NON-NLS-1$
            "    <Catalog5>\r\n" +  //$NON-NLS-1$
            "        <OptionalItems>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <FixedName>Nugent</FixedName>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </OptionalItems>\r\n" +  //$NON-NLS-1$
            "    </Catalog5>\r\n" +              //$NON-NLS-1$
            "    <Catalog6/>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$        
        
        helpTestProcess("SELECT * FROM xmltest.doc_8917", //$NON-NLS-1$
            expected, metadata, dataMgr);        
    } 

    /** 
     * Related to defect 8917 - the result should be the same as 
     * testDefect8917
     */
    public void testDefect11789b() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager_8917b(metadata);

        helpTestProcess("SELECT * FROM xmltest.doc_8917", //$NON-NLS-1$
            EXPECTED_DOC_DEFECT_8917_AND_11789, metadata, dataMgr);        
    }    
    
    public void testDefect9446() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager_8917(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item XXXXX=\"001\">\r\n" +  //$NON-NLS-1$
            "                <XXXXX/>\r\n" +  //$NON-NLS-1$
            "                <XXXXX>5</XXXXX>\r\n" +           //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
//        helpTestProcess("SELECT * FROM xmltest.doc_8917 WHERE Catalog.Items.Item.ItemID = '001'",
        helpTestProcess("SELECT * FROM xmltest.doc_9446", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    } 

    public void testDefect9446_2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager_8917(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items/>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc_9446 WHERE Catalogs.Catalog.Items.Item.XXXXX = '001'", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    } 

    public void testDefect_9530() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<mm:Catalogs xmlns:mm=\"http://www.duh.org/duh\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <mm:Item xmlns:mm2=\"http://www.duh3.org/duh3\" xmlns:mm=\"http://www.duh2.org/duh2\"\r\n" + //$NON-NLS-1$
            "                     ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +           //$NON-NLS-1$
            "            </mm:Item>\r\n" +  //$NON-NLS-1$
            "            <mm:Item xmlns:mm2=\"http://www.duh3.org/duh3\" xmlns:mm=\"http://www.duh2.org/duh2\"\r\n" + //$NON-NLS-1$
            "                     ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +             //$NON-NLS-1$
            "            </mm:Item>\r\n" +  //$NON-NLS-1$
            "            <mm:Item xmlns:mm2=\"http://www.duh3.org/duh3\" xmlns:mm=\"http://www.duh2.org/duh2\"\r\n" + //$NON-NLS-1$
            "                     ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +           //$NON-NLS-1$
            "            </mm:Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</mm:Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc_9530", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr);        
    } 

    public void testSubqueryInXMLQueryCriteria() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
//            "            <Item ItemID=\"002\">\r\n" + 
//            "                <Name>Screwdriver</Name>\r\n" + 
//            "                <Quantity>100</Quantity>\r\n" + 
//            "            </Item>\r\n" + 
//            "            <Item ItemID=\"003\">\r\n" + 
//            "                <Name>Goat</Name>\r\n" + 
//            "                <Quantity>4</Quantity>\r\n" + 
//            "            </Item>\r\n" + 
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 WHERE ItemID IN (SELECT itemNum FROM stock.items WHERE itemNum = '001')", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testSubqueryInXMLQueryCriteria2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
//            "            <Item ItemID=\"001\">\r\n" + 
//            "                <Name>Lamp</Name>\r\n" + 
//            "                <Quantity>5</Quantity>\r\n" + 
//            "            </Item>\r\n" + 
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 WHERE ItemID > ANY (SELECT itemNum FROM stock.items WHERE itemNum IN ('001','002') )", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testSubqueryInXMLQueryCriteria3() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
//            "            <Item ItemID=\"001\">\r\n" + 
//            "                <Name>Lamp</Name>\r\n" + 
//            "                <Quantity>5</Quantity>\r\n" + 
//            "            </Item>\r\n" + 
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 WHERE NOT (ItemID IN (SELECT itemNum FROM stock.items WHERE itemNum = '001') )", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void XXXtestSubqueryInXMLQueryCriteria4() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 WHERE EXISTS (SELECT itemNum FROM stock.items WHERE itemNum = '001')", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testSubqueryInXMLQueryCriteriaNestedSubquery() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
//            "            <Item ItemID=\"001\">\r\n" + 
//            "                <Name>Lamp</Name>\r\n" + 
//            "                <Quantity>5</Quantity>\r\n" + 
//            "            </Item>\r\n" + 
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1 WHERE ItemID > ANY (SELECT itemNum FROM stock.items WHERE itemNum IN (SELECT itemNum FROM stock.items WHERE itemNum IN ('001','002') ) )", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testSubqueryInXMLQueryCriteriaNestedMappingClass() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
//            "            <Item ItemID=\"001\">\r\n" + 
//            "                <Name>Lamp</Name>\r\n" + 
//            "                <Quantity>5</Quantity>\r\n" +
//            "                <Suppliers>\r\n" +
//            "                    <Supplier SupplierID=\"51\">\r\n" +
//            "                        <Name>Chucky</Name>\r\n" +
//            "                        <Zip>11111</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                    <Supplier SupplierID=\"52\">\r\n" +
//            "                        <Name>Biff's Stuff</Name>\r\n" +
//            "                        <Zip>22222</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                    <Supplier SupplierID=\"53\">\r\n" +
//            "                        <Name>AAAA</Name>\r\n" +
//            "                        <Zip>33333</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                    <Supplier SupplierID=\"56\">\r\n" +
//            "                        <Name>Microsoft</Name>\r\n" +
//            "                        <Zip>66666</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                </Suppliers>\r\n" +
//            "            </Item>\r\n" + 
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE ItemID > ANY (SELECT itemNum FROM stock.items WHERE itemNum IN ('001','002') )", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }  

    public void testSubqueryInXMLQueryCriteriaNestedMappingClass2() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE SupplierID > ANY (SELECT supplierNum FROM stock.suppliers WHERE supplierNum IN ('53','54') )", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    } 

    public void testSubqueryInXMLQueryCriteriaNestedMappingClass3() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
//            "            <Item ItemID=\"002\">\r\n" + 
//            "                <Name>Screwdriver</Name>\r\n" + 
//            "                <Quantity>100</Quantity>\r\n" + 
//            "                <Suppliers>\r\n" +
//            "                    <Supplier SupplierID=\"54\">\r\n" +
//            "                        <Name>Nugent Co.</Name>\r\n" +
//            "                        <Zip>44444</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                    <Supplier SupplierID=\"55\">\r\n" +
//            "                        <Name>Zeta</Name>\r\n" +
//            "                        <Zip>55555</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                    <Supplier SupplierID=\"56\">\r\n" +
//            "                        <Name>Microsoft</Name>\r\n" +
//            "                        <Zip>66666</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                </Suppliers>\r\n" +
//            "            </Item>\r\n" + 
//            "            <Item ItemID=\"003\">\r\n" + 
//            "                <Name>Goat</Name>\r\n" + 
//            "                <Quantity>4</Quantity>\r\n" + 
//            "                <Suppliers>\r\n" +
//            "                    <Supplier SupplierID=\"56\">\r\n" +
//            "                        <Name>Microsoft</Name>\r\n" +
//            "                        <Zip>66666</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                </Suppliers>\r\n" +
//            "            </Item>\r\n" + 
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE SupplierID < ALL (SELECT supplierNum FROM stock.suppliers WHERE supplierNum IN ('52','54') )", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testSubqueryInXMLQueryCriteriaNestedMappingClass3a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
//            "            <Item ItemID=\"002\">\r\n" + 
//            "                <Name>Screwdriver</Name>\r\n" + 
//            "                <Quantity>100</Quantity>\r\n" + 
//            "                <Suppliers>\r\n" +
//            "                    <Supplier SupplierID=\"54\">\r\n" +
//            "                        <Name>Nugent Co.</Name>\r\n" +
//            "                        <Zip>44444</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                    <Supplier SupplierID=\"55\">\r\n" +
//            "                        <Name>Zeta</Name>\r\n" +
//            "                        <Zip>55555</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                    <Supplier SupplierID=\"56\">\r\n" +
//            "                        <Name>Microsoft</Name>\r\n" +
//            "                        <Zip>66666</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                </Suppliers>\r\n" +
//            "            </Item>\r\n" + 
//            "            <Item ItemID=\"003\">\r\n" + 
//            "                <Name>Goat</Name>\r\n" + 
//            "                <Quantity>4</Quantity>\r\n" + 
//            "                <Suppliers>\r\n" +
//            "                    <Supplier SupplierID=\"56\">\r\n" +
//            "                        <Name>Microsoft</Name>\r\n" +
//            "                        <Zip>66666</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                </Suppliers>\r\n" +
//            "            </Item>\r\n" + 
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE SupplierID IN (SELECT supplierNum FROM stock.suppliers WHERE supplierNum IN ('52') )", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }


    public void testSubqueryInXMLQueryCriteriaNestedMappingClass4() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
//            "                    <Supplier SupplierID=\"56\">\r\n" +
//            "                        <Name>Microsoft</Name>\r\n" +
//            "                        <Zip>66666</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers/>\r\n" + //$NON-NLS-1$
//            "                <Suppliers>\r\n" +
//            "                    <Supplier SupplierID=\"54\">\r\n" +
//            "                        <Name>Nugent Co.</Name>\r\n" +
//            "                        <Zip>44444</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                    <Supplier SupplierID=\"55\">\r\n" +
//            "                        <Name>Zeta</Name>\r\n" +
//            "                        <Zip>55555</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                    <Supplier SupplierID=\"56\">\r\n" +
//            "                        <Name>Microsoft</Name>\r\n" +
//            "                        <Zip>66666</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                </Suppliers>\r\n" +
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers/>\r\n" + //$NON-NLS-1$
//            "                <Suppliers>\r\n" +
//            "                    <Supplier SupplierID=\"56\">\r\n" +
//            "                        <Name>Microsoft</Name>\r\n" +
//            "                        <Zip>66666</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                </Suppliers>\r\n" +
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE context(SupplierID, SupplierID) < SOME (SELECT supplierNum FROM stock.suppliers WHERE supplierNum IN ('52','54') )", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testCritNestedMappingClass() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
//            "            <Item ItemID=\"002\">\r\n" + 
//            "                <Name>Screwdriver</Name>\r\n" + 
//            "                <Quantity>100</Quantity>\r\n" + 
//            "                <Suppliers>\r\n" +
//            "                    <Supplier SupplierID=\"54\">\r\n" +
//            "                        <Name>Nugent Co.</Name>\r\n" +
//            "                        <Zip>44444</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                    <Supplier SupplierID=\"55\">\r\n" +
//            "                        <Name>Zeta</Name>\r\n" +
//            "                        <Zip>55555</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                    <Supplier SupplierID=\"56\">\r\n" +
//            "                        <Name>Microsoft</Name>\r\n" +
//            "                        <Zip>66666</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                </Suppliers>\r\n" +
//            "            </Item>\r\n" + 
//            "            <Item ItemID=\"003\">\r\n" + 
//            "                <Name>Goat</Name>\r\n" + 
//            "                <Quantity>4</Quantity>\r\n" + 
//            "                <Suppliers>\r\n" +
//            "                    <Supplier SupplierID=\"56\">\r\n" +
//            "                        <Name>Microsoft</Name>\r\n" +
//            "                        <Zip>66666</Zip>\r\n" +
//            "                    </Supplier>\r\n" +
//            "                </Suppliers>\r\n" +
//            "            </Item>\r\n" + 
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE SupplierID = '52'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testDefect_9893() throws Exception{
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Root>\r\n" + //$NON-NLS-1$
            "    <ItemName>Lamp</ItemName>\r\n" +  //$NON-NLS-1$
            "    <ItemName>Screwdriver</ItemName>\r\n" +  //$NON-NLS-1$
            "    <ItemName>Goat</ItemName>\r\n" +  //$NON-NLS-1$
            "</Root>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc9893", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr); 
    }

    public void testDefect_9893_2() throws Exception{
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Root>\r\n" + //$NON-NLS-1$
            "    <ItemName>Lamp</ItemName>\r\n" +  //$NON-NLS-1$
            "    <ItemName>Screwdriver</ItemName>\r\n" +  //$NON-NLS-1$
            "    <ItemName>Goat</ItemName>\r\n" +  //$NON-NLS-1$
            "</Root>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT ItemName FROM xmltest.doc9893", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr); 
    }

    public void testDefect_9893_3() throws Exception{
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Root>\r\n" + //$NON-NLS-1$
            "    <ItemName>Lamp</ItemName>\r\n" +  //$NON-NLS-1$
            "    <ItemName>Screwdriver</ItemName>\r\n" +  //$NON-NLS-1$
            "    <ItemName>Goat</ItemName>\r\n" +  //$NON-NLS-1$
            "</Root>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT ItemName.* FROM xmltest.doc9893", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr); 
    }

    public void testDefect_9893_4() throws Exception{
        FakeMetadataFacade metadata = exampleMetadataNestedWithSibling();
        FakeDataManager dataMgr = exampleDataManagerNestedWithSibling(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item/>\r\n" +  //$NON-NLS-1$
            "            <Item/>\r\n" +  //$NON-NLS-1$
            "            <Item/>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        final boolean SHOULD_SUCCEED = true;
        helpTestProcess("SELECT Item FROM xmltest.doc9c", //$NON-NLS-1$
            expectedDoc, metadata, dataMgr, SHOULD_SUCCEED, null, null);       
    }

    public void testNestedWithStoredQueryInMappingClass() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc18", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    /** homegenous, simple array elements */
    public void testWithSOAPEncoding1() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataSoap1();
        FakeDataManager dataMgr = exampleDataManagerForSoap1(metadata, false);
         
        String expectedDoc = 
             "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
             "<ORG:TaxReports xmlns:ORG=\"http://www.mm.org/dummy\">\r\n" + //$NON-NLS-1$
             "    <ORG:TaxReport>\r\n" +  //$NON-NLS-1$
             "        <ORG:ArrayOfTaxID xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" + //$NON-NLS-1$
             "                          xmlns:SOAP-ENC=\"http://schemas.xmlsoap.org/soap/encoding/\"\r\n" + //$NON-NLS-1$
             "                          xsi:type=\"ORG:ArrayOfTaxIDType\"\r\n" + //$NON-NLS-1$
             " SOAP-ENC:arrayType=\"ORG:TaxIDType[]\">\r\n" +  //$NON-NLS-1$
             "            <ORG:TaxID xsi:type=\"ORG:TaxIDType\">\r\n" +  //$NON-NLS-1$
             "                <ID>1</ID>\r\n" +  //$NON-NLS-1$
             "            </ORG:TaxID>\r\n" +  //$NON-NLS-1$
             "            <ORG:TaxID xsi:type=\"ORG:TaxIDType\">\r\n" +  //$NON-NLS-1$
             "                <ID>2</ID>\r\n" +  //$NON-NLS-1$
             "            </ORG:TaxID>\r\n" +  //$NON-NLS-1$
             "            <ORG:TaxID xsi:type=\"ORG:TaxIDType\">\r\n" +  //$NON-NLS-1$
             "                <ID>3</ID>\r\n" +  //$NON-NLS-1$
             "            </ORG:TaxID>\r\n" +  //$NON-NLS-1$
             "        </ORG:ArrayOfTaxID>\r\n" +  //$NON-NLS-1$
             "    </ORG:TaxReport>\r\n" +  //$NON-NLS-1$
             "</ORG:TaxReports>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.docSoap", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

     
    /**
     * Test for Merrill - if no data is contained in the soap elements
     * (e.g. ORG:ArrayOfTaxID) and the schema allows it, eliminate the 
     * whole fragment
     */        
    public void testWithSOAPEncodingNoRows() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataSoap1();
        FakeDataManager dataMgr = exampleDataManagerForSoap1(metadata, true);
         
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<ORG:TaxReports xmlns:ORG=\"http://www.mm.org/dummy\">\r\n" + //$NON-NLS-1$
            "    <ORG:TaxReport/>\r\n" +  //$NON-NLS-1$
//            "    <ORG:TaxReport>\r\n" + 
//            "        <ORG:ArrayOfTaxID xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:SOAP-ENC=\"http://schemas.xmlsoap.org/soap/encoding/\" xsi:type=\"ORG:ArrayOfTaxIDType\" SOAP-ENC:arrayType=\"ORG:TaxIDType[]\">\r\n" + 
//            "            <ORG:TaxID xsi:type=\"ORG:TaxIDType\">\r\n" + 
//            "                <ID>1</ID>\r\n" + 
//            "            </ORG:TaxID>\r\n" + 
//            "            <ORG:TaxID xsi:type=\"ORG:TaxIDType\">\r\n" + 
//            "                <ID>2</ID>\r\n" + 
//            "            </ORG:TaxID>\r\n" + 
//            "            <ORG:TaxID xsi:type=\"ORG:TaxIDType\">\r\n" + 
//            "                <ID>3</ID>\r\n" + 
//            "            </ORG:TaxID>\r\n" + 
//            "        </ORG:ArrayOfTaxID>\r\n" + 
//            "    </ORG:TaxReport>\r\n" + 
            "</ORG:TaxReports>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.docSoap", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }  
    
    public void testDefect12260() throws Exception{
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <numSuppliers>4</numSuppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <numSuppliers>3</numSuppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <numSuppliers>1</numSuppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$


        // Set up capabilities to duplicate defect
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_CORRELATED, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_SCALAR, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$
        CapabilitiesFinder capFinder = new SimpleCapabilitiesFinder(caps); 
        
        helpTestProcess("SELECT * FROM xmltest.doc12260", expectedDoc, metadata, dataMgr, true, MetaMatrixComponentException.class, null, capFinder); //$NON-NLS-1$
    }
    
    public void testDefect8373() throws Exception{
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerWithNulls(metadata);
         
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity/>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity/>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc8373", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testDefect8373a() throws Exception{
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerWithNulls(metadata);
         
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity/>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity/>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc8373a", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testDefect8373b() throws Exception{
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerWithNulls(metadata);
         
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity/>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity/>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$

        helpTestProcess("SELECT * FROM xmltest.doc8373b", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }    

    public void testDefect13617() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager13617(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item>\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT Item.Name FROM xmltest.doc13617", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }       

    public void testDefect13617a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager13617(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"004\"/>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT ItemID, Item.Name FROM xmltest.doc13617", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }      

    /**
     * Tests that non-zero length whitespace string will be treated like
     * normal data 
     * @throws Exception
     * @since 4.2
     */
    public void testDefect14905() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager14905(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\" \">\r\n" +  //$NON-NLS-1$
            "                <Name> </Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"  \">\r\n" +  //$NON-NLS-1$
            "                <Name>  </Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\" \">\r\n" +  //$NON-NLS-1$
            "                <Name> </Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }    

    public void testTextUnnormalizedDefect15117() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager15117(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name> Lamp </Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>  Screw  driver  </Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name> Goat </Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    public void testTextUnnormalizedDefect15117a() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager15117a(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>\t \n&#xD;</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>  &gt;Screw&lt; \n driver  &amp;</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name> &gt;&gt;&#xD;Goat </Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc1", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }      
    
    public void testRecursiveGroupDoc() throws Exception {

        FakeMetadataFacade metadata = exampleMetadata2();
        FakeDataManager dataMgr = exampleXQTDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<group>\r\n" + //$NON-NLS-1$
            "    <ID>2</ID>\r\n" +  //$NON-NLS-1$
            "    <code>-1</code>\r\n" +  //$NON-NLS-1$
            "    <supervisor>\r\n" +  //$NON-NLS-1$
            "        <ID>4</ID>\r\n" +  //$NON-NLS-1$
            "        <code>1</code>\r\n" +  //$NON-NLS-1$
            "        <group>\r\n" +  //$NON-NLS-1$
            "            <ID>6</ID>\r\n" +  //$NON-NLS-1$
            "            <code>3</code>\r\n" +  //$NON-NLS-1$
            "            <supervisor>\r\n" +  //$NON-NLS-1$
            "                <ID>8</ID>\r\n" +  //$NON-NLS-1$
            "                <code>5</code>\r\n" +  //$NON-NLS-1$
            "                <group>\r\n" +  //$NON-NLS-1$
            "                    <ID>10</ID>\r\n" +  //$NON-NLS-1$
            "                    <code>7</code>\r\n" +  //$NON-NLS-1$
            "                    <supervisor>\r\n" +  //$NON-NLS-1$
            "                        <ID>12</ID>\r\n" +  //$NON-NLS-1$
            "                        <code>9</code>\r\n" +  //$NON-NLS-1$
            "                    </supervisor>\r\n" +  //$NON-NLS-1$
            "                </group>\r\n" +  //$NON-NLS-1$
            "            </supervisor>\r\n" +  //$NON-NLS-1$
            "        </group>\r\n" +  //$NON-NLS-1$
            "    </supervisor>\r\n" +  //$NON-NLS-1$
            "</group>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xqttest.groupDoc WHERE pseudoID = 2", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    public void testCase2951MaxRows() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    /** test rowlimitexception() doesn't throw exception is rowlimit isn't passed */
    public void testDefect19173RowLimitException() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE rowlimitexception(supplier) = 4", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }    

    /** test criteria can be written backwards */
    public void testDefect19173RowLimitExceptionBackwardsCriteria() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
            "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE 4 = rowlimitexception(supplier)", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }    
    
    public void testCase2951MaxRows2() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE rowlimit(supplier) = 2", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }    

    /** test processing exception is thrown if row limit is passed */
    public void testDefect19173RowLimitException2() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE rowlimitexception(supplier) = 2", null, metadata, dataMgr, false, MetaMatrixProcessingException.class, "");         //$NON-NLS-1$ //$NON-NLS-2$
    }      
    
    /** Two row limits on the same mapping class should be harmless as long as the row limits are identical. */
    public void testCase2951MaxRows2a() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE rowlimit(supplier) = 2 AND rowlimit(supplierid) = 2", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }    

    /** test processing exception is thrown if row limit is passed */
    public void testDefect19173RowLimitException2a() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE rowlimitexception(supplier) = 2 AND rowlimitexception(supplierid) = 2", null, metadata, dataMgr, false, MetaMatrixProcessingException.class, "");         //$NON-NLS-1$ //$NON-NLS-2$
    }      
    
    /** compound criteria */
    public void testCase2951MaxRows3() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE ItemID='002' AND rowlimit(supplier) = 2", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }    

    /** compound criteria */
    public void testDefect19173RowLimitException3() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE ItemID='002' AND rowlimitexception(supplier) = 2", null, metadata, dataMgr, false, MetaMatrixProcessingException.class, "");         //$NON-NLS-1$ //$NON-NLS-2$
    }     
    
    public void testCase2951MaxRows4() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE rowlimit(supplier) = 2 AND rowlimit(item) = 2", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }     

    public void testCase2951AndDefect19173MixTwoFunctions() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE rowlimit(supplier) = 2 AND rowlimitException(item) = 6", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }     
    
    /** arg to rowlimit function isn't in the scope of any mapping class */
    public void testCase2951MaxRowsFails() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE rowlimit(catalogs) = 2", null, metadata, dataMgr, false, QueryPlannerException.class, "");         //$NON-NLS-1$ //$NON-NLS-2$
    }     

    /** two conflicting row limits on the same mapping class */
    public void testCase2951MaxRowsFails2() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE rowlimit(supplier) = 2 AND rowlimit(supplierID) = 3", null, metadata, dataMgr, false, QueryPlannerException.class, "");         //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    /** arg to rowlimitexception function isn't in the scope of any mapping class */
    public void testDefect19173RowLimitExceptionFails() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE rowlimitexception(catalogs) = 2", null, metadata, dataMgr, false, QueryPlannerException.class, "");         //$NON-NLS-1$ //$NON-NLS-2$
    }     

    /** two conflicting rowlimitexceptions on the same mapping class */
    public void testDefect19173RowLimitExceptionFails2() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE rowlimitexception(supplier) = 2 AND rowlimitexception(supplierID) = 3", null, metadata, dataMgr, false, QueryPlannerException.class, "");         //$NON-NLS-1$ //$NON-NLS-2$
    }    
    
    /** two conflicting rowlimit and rowlimitexceptions on the same mapping class fails planning */
    public void testDefect19173RowLimitAndRowLimitExceptionMixFails2() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE rowlimit(supplier) = 2 AND rowlimitexception(supplierID) = 3", null, metadata, dataMgr, false, QueryPlannerException.class, "");         //$NON-NLS-1$ //$NON-NLS-2$
    }    

    /** try rowlimit criteria written the reverse way */
    public void testCase2951MaxRows5() throws Exception {
        
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNested(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
            "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
            "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
            "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
            "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "                <Suppliers>\r\n" + //$NON-NLS-1$
            "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
            "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
            "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
            "                    </Supplier>\r\n" + //$NON-NLS-1$
            "                </Suppliers>\r\n" + //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.doc8 WHERE 2 = rowlimit(supplier)", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    } 
    
    
    public void testNormalizationCollapse() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNormalization(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name xsi:nil=\"true\"/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>0</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <DiscontinuedItem ItemID=\"004\">\r\n" +  //$NON-NLS-1$
            "                <Name>Flux Capacitor</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>2</Quantity>\r\n" +  //$NON-NLS-1$
            "            </DiscontinuedItem>\r\n" +  //$NON-NLS-1$
            "            <StatusUnknown ItemID=\"005\">\r\n" +  //$NON-NLS-1$
            "                <Name>Milkshake</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>88</Quantity>\r\n" +  //$NON-NLS-1$
            "            </StatusUnknown>\r\n" +  //$NON-NLS-1$
            "            <DiscontinuedItem ItemID=\"006\">\r\n" +  //$NON-NLS-1$
            "                <Name>Feta Matrix</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>0</Quantity>\r\n" +  //$NON-NLS-1$
            "            </DiscontinuedItem>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("SELECT * FROM xmltest.normDoc1", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    public void testNormalizationReplace() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNormalization(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name xsi:nil=\"true\"/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>   Screwdriver       </Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>0</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>         Goat  </Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <DiscontinuedItem ItemID=\"004\">\r\n" +  //$NON-NLS-1$
            "                <Name>Flux     Capacitor</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>2</Quantity>\r\n" +  //$NON-NLS-1$
            "            </DiscontinuedItem>\r\n" +  //$NON-NLS-1$
            "            <StatusUnknown ItemID=\"005\">\r\n" +  //$NON-NLS-1$
            "                <Name>Milkshake</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>88</Quantity>\r\n" +  //$NON-NLS-1$
            "            </StatusUnknown>\r\n" +  //$NON-NLS-1$
            "            <DiscontinuedItem ItemID=\"006\">\r\n" +  //$NON-NLS-1$
            "                <Name> Feta               Matrix       </Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>0</Quantity>\r\n" +  //$NON-NLS-1$
            "            </DiscontinuedItem>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
 
        helpTestProcess("SELECT * FROM xmltest.normDoc2", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    public void testNormalizationPreserve() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManagerNormalization2(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name xsi:nil=\"true\"/>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>My Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>0</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>My Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <DiscontinuedItem ItemID=\"004\">\r\n" +  //$NON-NLS-1$
            "                <Name>My Flux Capacitor</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>2</Quantity>\r\n" +  //$NON-NLS-1$
            "            </DiscontinuedItem>\r\n" +  //$NON-NLS-1$
            "            <StatusUnknown ItemID=\"005\">\r\n" +  //$NON-NLS-1$
            "                <Name>My Milkshake</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>88</Quantity>\r\n" +  //$NON-NLS-1$
            "            </StatusUnknown>\r\n" +  //$NON-NLS-1$
            "            <DiscontinuedItem ItemID=\"006\">\r\n" +  //$NON-NLS-1$
            "                <Name>My Feta Matrix</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>0</Quantity>\r\n" +  //$NON-NLS-1$
            "            </DiscontinuedItem>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
    
        helpTestProcess("SELECT * FROM xmltest.normDoc3", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    /**
     * Deluxe example
     */
    private FakeDataManager exampleDataManagerNormalization(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);

            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", null, new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "002", " \n Screwdriver \t    \r", null, "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "003", "       \t\rGoat \n", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "004", "Flux \t\r\n Capacitor", new Integer(2), "discontinued" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "005", "Milkshake", new Integer(88), null } ), //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "006", " Feta               Matrix       ", new Integer(0), "discontinued" } ) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    }   
    private FakeDataManager exampleDataManagerNormalization2(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupID = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$
            List elementIDs = metadata.getElementIDsInGroupID(groupID);
            List elementSymbols = createElements(elementIDs);

            dataMgr.registerTuples(
                groupID,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", null, new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "002", "My Screwdriver", null, "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "003", "My Goat", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "004", "My Flux Capacitor", new Integer(2), "discontinued" } ), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "005", "My Milkshake", new Integer(88), null } ), //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "006", "My Feta Matrix", new Integer(0), "discontinued" } ) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    }   
    
    private static MappingNode createXMLPlanNormalization(String normMode) {

        MappingDocument doc = new MappingDocument(true);
        MappingElement root = doc.addChildElement(new MappingElement("Catalogs")); //$NON-NLS-1$

        MappingElement cat = root.addChildElement(new MappingElement("Catalog")); //$NON-NLS-1$
        MappingElement items = cat.addChildElement(new MappingElement("Items")); //$NON-NLS-1$

        //choice node, non-visual, so it has no name        
        MappingChoiceNode choice = items.addChoiceNode(new MappingChoiceNode());
        choice.setSource("xmltest.group.items"); //$NON-NLS-1$
        choice.setMaxOccurrs(-1);
        MappingCriteriaNode crit = choice.addCriteriaNode(new MappingCriteriaNode("xmltest.group.items.itemStatus = 'okay'", false)); //$NON-NLS-1$
        MappingElement item = crit.addChildElement(new MappingElement("Item")); //$NON-NLS-1$ 
        item.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        item.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
            .setNormalizeText(normMode) 
            .setNillable(true);
        item.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")) //$NON-NLS-1$ //$NON-NLS-2$
            .setDefaultValue("0"); //$NON-NLS-1$
        
        MappingCriteriaNode crit2 = choice.addCriteriaNode(new MappingCriteriaNode("xmltest.group.items.itemStatus = 'discontinued'", false)); //$NON-NLS-1$ 
        MappingElement discontinuedItem = crit2.addChildElement(new MappingElement("DiscontinuedItem")); //$NON-NLS-1$ 
        discontinuedItem.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        discontinuedItem.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
            .setNormalizeText(normMode) 
            .setNillable(true);
        discontinuedItem.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")) //$NON-NLS-1$ //$NON-NLS-2$
            .setDefaultValue("0"); //$NON-NLS-1$
        
        MappingCriteriaNode crit3 = choice.addCriteriaNode(new MappingCriteriaNode());      
        MappingElement unknownItem = crit3.addChildElement(new MappingElement("StatusUnknown")); //$NON-NLS-1$
        unknownItem.addAttribute(new MappingAttribute("ItemID", "xmltest.group.items.itemNum")); //$NON-NLS-1$ //$NON-NLS-2$
        unknownItem.addChildElement(new MappingElement("Name", "xmltest.group.items.itemName")) //$NON-NLS-1$ //$NON-NLS-2$
            .setNormalizeText(normMode) 
            .setNillable(true);
        unknownItem.addChildElement(new MappingElement("Quantity", "xmltest.group.items.itemQuantity")) //$NON-NLS-1$ //$NON-NLS-2$
            .setDefaultValue("0"); //$NON-NLS-1$        
        
        MappingCriteriaNode notIncludedSibling = choice.addCriteriaNode(new MappingCriteriaNode("xmltest.group.items.itemStatus = 'something'", false)); //$NON-NLS-1$ 
        notIncludedSibling.setExclude(true);

        MappingCriteriaNode notIncludedSibling2 = choice.addCriteriaNode(new MappingCriteriaNode("xmltest.group.items.itemStatus = 'something'", false)); //$NON-NLS-1$ 
        notIncludedSibling2.setExclude(true);
        return doc;        
    }

    static FakeDataManager exampleDataManagerCase3225(FakeMetadataFacade metadata) {
        FakeDataManager dataMgr = new FakeDataManager();
    
        try { 
            // Group stock.items
            FakeMetadataObject groupItems = (FakeMetadataObject) metadata.getGroupID("stock.items"); //$NON-NLS-1$

            // Group stock.supplier
            FakeMetadataObject groupSuppliers = (FakeMetadataObject) metadata.getGroupID("stock.suppliers"); //$NON-NLS-1$

            // Group stock.orders
            FakeMetadataObject groupOrders = (FakeMetadataObject) metadata.getGroupID("stock.orders"); //$NON-NLS-1$

            // Group stock.employees
            FakeMetadataObject groupEmployees = (FakeMetadataObject) metadata.getGroupID("stock.employees"); //$NON-NLS-1$
            
            // Group stock.item_supplier
            FakeMetadataObject groupItemSupplier = (FakeMetadataObject) metadata.getGroupID("stock.item_supplier"); //$NON-NLS-1$

            // Items
            List elementIDs = metadata.getElementIDsInGroupID(groupItems);
            List elementSymbols = createElements(elementIDs);

            // Supplier
            elementIDs = metadata.getElementIDsInGroupID(groupSuppliers);
            List supplierElementSymbols = createElements(elementIDs);

            // Orders
            elementIDs = metadata.getElementIDsInGroupID(groupOrders);
            List ordersElementSymbols = createElements(elementIDs);

            // Employees
            elementIDs = metadata.getElementIDsInGroupID(groupEmployees);
            List employeesElementSymbols = createElements(elementIDs);
            
            // Item_supplier
            elementIDs = metadata.getElementIDsInGroupID(groupItemSupplier);
            List itemSupplierElementSymbols = createElements(elementIDs);
        
            dataMgr.registerTuples(
                groupItems,
                elementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", "Lamp", new Integer(5), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "002", "Screwdriver", new Integer(100), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "003", "Goat", new Integer(4), "okay" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

            dataMgr.registerTuples(
                groupItemSupplier,
                itemSupplierElementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "001", "51" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "001", "52" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "001", "53" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "001", "56" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "002", "54" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "002", "55" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "002", "56" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    Arrays.asList( new Object[] { "003", "56" } ),         //$NON-NLS-1$ //$NON-NLS-2$
                    } );    


            dataMgr.registerTuples(
                groupSuppliers,
                supplierElementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "51", "Chucky", "11111" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "52", "Biff's Stuff", "22222" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "53", "AAAA", "33333" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "54", "Nugent Co.", "44444" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "55", "Zeta", "55555" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    Arrays.asList( new Object[] { "56", "Microsoft", "66666" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    } );    

            dataMgr.registerTuples(
                groupOrders,
                ordersElementSymbols,
                
                new List[] { 
                    Arrays.asList( new Object[] { "1", "001", "51", "2/13/05", new Integer(2), "complete" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "2", "001", "52", "3/13/05", new Integer(1), "processing" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "3", "002", "53", "4/13/05", new Integer(1), "complete" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "4", "002", "56", "5/13/05", new Integer(1), "cancelled" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    Arrays.asList( new Object[] { "5", "003", "56", "6/13/05", new Integer(800), "processing" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    } );    

            dataMgr.registerTuples(
                groupEmployees,
                employeesElementSymbols,
               
                new List[] { 
                    Arrays.asList( new Object[] { "1001", "51", "001", "1004", "Albert", "Pujols" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    Arrays.asList( new Object[] { "1002", "51", "001", "1004", "Jim", "Edmunds" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    Arrays.asList( new Object[] { "1003", "54", "002", "1004", "David", "Eckstein" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    Arrays.asList( new Object[] { "1004", null, null, "1009", "Tony", "LaRussa" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ 
                    Arrays.asList( new Object[] { "1005", "56", "001", "1007", "Derrek", "Lee" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    Arrays.asList( new Object[] { "1006", "56", "003", "1007", "Corey", "Patterson" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    Arrays.asList( new Object[] { "1007", null, null, "1010", "Dusty", "Baker" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ 
                    Arrays.asList( new Object[] { "1008", "56", "002", "1007", "Carlos", "Zambrano" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    Arrays.asList( new Object[] { "1009", null, null, null, "Bill", "DeWitt" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ 
                    Arrays.asList( new Object[] { "1010", null, null, null, "Some", "Guy" } ),         //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ 
                    } );             
            
        } catch(Throwable e) { 
            e.printStackTrace();
            fail("Exception building test data (" + e.getClass().getName() + "): " + e.getMessage());     //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        return dataMgr;
    }      
    
    /**
     * Test of doc model w/o criteria, just as a baseline 
     * @throws Exception
     */
    public void testCase3225() throws Exception {
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleCase3225();
        FakeDataManager dataMgr = exampleDataManagerCase3225(metadata);
        String expectedDoc = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
        "<Catalogs>\r\n" + //$NON-NLS-1$
        "    <Catalog>\r\n" + //$NON-NLS-1$
        "        <Items>\r\n" + //$NON-NLS-1$
        "            <Item ItemID=\"001\">\r\n" + //$NON-NLS-1$
        "                <Name>Lamp</Name>\r\n" + //$NON-NLS-1$
        "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
        "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders>\r\n" + //$NON-NLS-1$
        "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
        "                                <OrderDate>2/13/05</OrderDate>\r\n" + //$NON-NLS-1$
        "                                <OrderQuantity>2</OrderQuantity>\r\n" + //$NON-NLS-1$
        "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
        "                            </Order>\r\n" + //$NON-NLS-1$
        "                        </Orders>\r\n" + //$NON-NLS-1$
        "                        <Employees>\r\n" + //$NON-NLS-1$
        "                            <Employee EmployeeID=\"1001\" SupervisorID=\"1004\">\r\n" + //$NON-NLS-1$
        "                                <FirstName>Albert</FirstName>\r\n" + //$NON-NLS-1$
        "                                <LastName>Pujols</LastName>\r\n" + //$NON-NLS-1$
        "                            </Employee>\r\n" + //$NON-NLS-1$
        "                            <Employee EmployeeID=\"1002\" SupervisorID=\"1004\">\r\n" + //$NON-NLS-1$
        "                                <FirstName>Jim</FirstName>\r\n" + //$NON-NLS-1$
        "                                <LastName>Edmunds</LastName>\r\n" + //$NON-NLS-1$
        "                            </Employee>\r\n" + //$NON-NLS-1$
        "                        </Employees>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
        "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders>\r\n" + //$NON-NLS-1$
        "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
        "                                <OrderDate>3/13/05</OrderDate>\r\n" + //$NON-NLS-1$
        "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
        "                                <OrderStatus>processing</OrderStatus>\r\n" + //$NON-NLS-1$
        "                            </Order>\r\n" + //$NON-NLS-1$
        "                        </Orders>\r\n" + //$NON-NLS-1$
        "                        <Employees/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
        "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                        <Employees/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                        <Employees>\r\n" + //$NON-NLS-1$
        "                            <Employee EmployeeID=\"1005\" SupervisorID=\"1007\">\r\n" + //$NON-NLS-1$
        "                                <FirstName>Derrek</FirstName>\r\n" + //$NON-NLS-1$
        "                                <LastName>Lee</LastName>\r\n" + //$NON-NLS-1$
        "                            </Employee>\r\n" + //$NON-NLS-1$
        "                        </Employees>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" + //$NON-NLS-1$
        "            <Item ItemID=\"002\">\r\n" + //$NON-NLS-1$
        "                <Name>Screwdriver</Name>\r\n" + //$NON-NLS-1$
        "                <Quantity>100</Quantity>\r\n" + //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
        "                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                        <Employees>\r\n" + //$NON-NLS-1$
        "                            <Employee EmployeeID=\"1003\" SupervisorID=\"1004\">\r\n" + //$NON-NLS-1$
        "                                <FirstName>David</FirstName>\r\n" + //$NON-NLS-1$
        "                                <LastName>Eckstein</LastName>\r\n" + //$NON-NLS-1$
        "                            </Employee>\r\n" + //$NON-NLS-1$
        "                        </Employees>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
        "                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                        <Employees/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders>\r\n" + //$NON-NLS-1$
        "                            <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
        "                                <OrderDate>5/13/05</OrderDate>\r\n" + //$NON-NLS-1$
        "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
        "                                <OrderStatus>cancelled</OrderStatus>\r\n" + //$NON-NLS-1$
        "                            </Order>\r\n" + //$NON-NLS-1$
        "                        </Orders>\r\n" + //$NON-NLS-1$
        "                        <Employees>\r\n" + //$NON-NLS-1$
        "                            <Employee EmployeeID=\"1008\" SupervisorID=\"1007\">\r\n" + //$NON-NLS-1$
        "                                <FirstName>Carlos</FirstName>\r\n" + //$NON-NLS-1$
        "                                <LastName>Zambrano</LastName>\r\n" + //$NON-NLS-1$
        "                            </Employee>\r\n" + //$NON-NLS-1$
        "                        </Employees>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" + //$NON-NLS-1$
        "            <Item ItemID=\"003\">\r\n" + //$NON-NLS-1$
        "                <Name>Goat</Name>\r\n" + //$NON-NLS-1$
        "                <Quantity>4</Quantity>\r\n" + //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders>\r\n" + //$NON-NLS-1$
        "                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
        "                                <OrderDate>6/13/05</OrderDate>\r\n" + //$NON-NLS-1$
        "                                <OrderQuantity>800</OrderQuantity>\r\n" + //$NON-NLS-1$
        "                                <OrderStatus>processing</OrderStatus>\r\n" + //$NON-NLS-1$
        "                            </Order>\r\n" + //$NON-NLS-1$
        "                        </Orders>\r\n" + //$NON-NLS-1$
        "                        <Employees>\r\n" + //$NON-NLS-1$
        "                            <Employee EmployeeID=\"1006\" SupervisorID=\"1007\">\r\n" + //$NON-NLS-1$
        "                                <FirstName>Corey</FirstName>\r\n" + //$NON-NLS-1$
        "                                <LastName>Patterson</LastName>\r\n" + //$NON-NLS-1$
        "                            </Employee>\r\n" + //$NON-NLS-1$
        "                        </Employees>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" + //$NON-NLS-1$
        "        </Items>\r\n" + //$NON-NLS-1$
        "    </Catalog>\r\n" + //$NON-NLS-1$
        "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("select * from xmltest.itemsdoc", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }

    /**
     * For ESpace case 3225 tests, with criteria
     * "... where employee.@supervisorID='1004' and order.orderquantity > 1"
     */
    private static final String CASE_3225_WITH_CRITERIA_EXPECTED_DOC = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
        "<Catalogs>\r\n" + //$NON-NLS-1$
        "    <Catalog>\r\n" + //$NON-NLS-1$
        "        <Items>\r\n" + //$NON-NLS-1$
        "            <Item ItemID=\"001\">\r\n" + //$NON-NLS-1$
        "                <Name>Lamp</Name>\r\n" + //$NON-NLS-1$
        "                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
        "                <Suppliers>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
        "                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders>\r\n" + //$NON-NLS-1$
        "                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
        "                                <OrderDate>2/13/05</OrderDate>\r\n" + //$NON-NLS-1$
        "                                <OrderQuantity>2</OrderQuantity>\r\n" + //$NON-NLS-1$
        "                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
        "                            </Order>\r\n" + //$NON-NLS-1$
        "                        </Orders>\r\n" + //$NON-NLS-1$
        "                        <Employees>\r\n" + //$NON-NLS-1$
        "                            <Employee EmployeeID=\"1001\" SupervisorID=\"1004\">\r\n" + //$NON-NLS-1$
        "                                <FirstName>Albert</FirstName>\r\n" + //$NON-NLS-1$
        "                                <LastName>Pujols</LastName>\r\n" + //$NON-NLS-1$
        "                            </Employee>\r\n" + //$NON-NLS-1$
        "                            <Employee EmployeeID=\"1002\" SupervisorID=\"1004\">\r\n" + //$NON-NLS-1$
        "                                <FirstName>Jim</FirstName>\r\n" + //$NON-NLS-1$
        "                                <LastName>Edmunds</LastName>\r\n" + //$NON-NLS-1$
        "                            </Employee>\r\n" + //$NON-NLS-1$
        "                        </Employees>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
        "                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders>\r\n" + //$NON-NLS-1$
        "                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
        "                                <OrderDate>3/13/05</OrderDate>\r\n" + //$NON-NLS-1$
        "                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
        "                                <OrderStatus>processing</OrderStatus>\r\n" + //$NON-NLS-1$
        "                            </Order>\r\n" + //$NON-NLS-1$
        "                        </Orders>\r\n" + //$NON-NLS-1$
        "                        <Employees/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
        "                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                        <Employees/>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
        "                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
        "                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
        "                        <Orders/>\r\n" + //$NON-NLS-1$
        "                        <Employees>\r\n" + //$NON-NLS-1$
        "                            <Employee EmployeeID=\"1005\" SupervisorID=\"1007\">\r\n" + //$NON-NLS-1$
        "                                <FirstName>Derrek</FirstName>\r\n" + //$NON-NLS-1$
        "                                <LastName>Lee</LastName>\r\n" + //$NON-NLS-1$
        "                            </Employee>\r\n" + //$NON-NLS-1$
        "                        </Employees>\r\n" + //$NON-NLS-1$
        "                    </Supplier>\r\n" + //$NON-NLS-1$
        "                </Suppliers>\r\n" + //$NON-NLS-1$
        "            </Item>\r\n" + //$NON-NLS-1$
        "        </Items>\r\n" + //$NON-NLS-1$
        "    </Catalog>\r\n" + //$NON-NLS-1$
        "</Catalogs>\r\n\r\n"; //$NON-NLS-1$    

	private static String EXPECTED_ORDERED_DOC9A = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
	"<Catalogs>\r\n" + //$NON-NLS-1$
	"    <Catalog>\r\n" +  //$NON-NLS-1$
	"        <Items>\r\n" +  //$NON-NLS-1$
	"            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
	"                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
	"                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
	"                <Suppliers>\r\n" + //$NON-NLS-1$
	"                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
	"                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
	"                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
	"                        <Orders>\r\n" + //$NON-NLS-1$
	"                            <Order OrderID=\"3\">\r\n" + //$NON-NLS-1$
	"                                <OrderDate>02/31/02</OrderDate>\r\n" + //$NON-NLS-1$
	"                                <OrderQuantity>12</OrderQuantity>\r\n" + //$NON-NLS-1$
	"                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
	"                            </Order>\r\n" +  //$NON-NLS-1$
	"                            <Order OrderID=\"4\">\r\n" + //$NON-NLS-1$
	"                                <OrderDate>05/31/02</OrderDate>\r\n" + //$NON-NLS-1$
	"                                <OrderQuantity>9</OrderQuantity>\r\n" + //$NON-NLS-1$
	"                                <OrderStatus>processing</OrderStatus>\r\n" + //$NON-NLS-1$
	"                            </Order>\r\n" + //$NON-NLS-1$
	"                        </Orders>\r\n" + //$NON-NLS-1$
	"                    </Supplier>\r\n" + //$NON-NLS-1$
	"                </Suppliers>\r\n" + //$NON-NLS-1$
	"            </Item>\r\n" +              //$NON-NLS-1$
	"            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
	"                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
	"                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
	"                <Suppliers>\r\n" + //$NON-NLS-1$
	"                    <Supplier SupplierID=\"54\">\r\n" + //$NON-NLS-1$
	"                        <Name>Nugent Co.</Name>\r\n" + //$NON-NLS-1$
	"                        <Zip>44444</Zip>\r\n" + //$NON-NLS-1$
	"                        <Orders>\r\n" + //$NON-NLS-1$
	"                            <Order OrderID=\"1\">\r\n" + //$NON-NLS-1$
	"                                <OrderDate>10/23/01</OrderDate>\r\n" + //$NON-NLS-1$
	"                                <OrderQuantity>5</OrderQuantity>\r\n" + //$NON-NLS-1$
	"                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
	"                            </Order>\r\n" + //$NON-NLS-1$
	"                        </Orders>\r\n" + //$NON-NLS-1$
	"                    </Supplier>\r\n" + //$NON-NLS-1$
	"                    <Supplier SupplierID=\"55\">\r\n" + //$NON-NLS-1$
	"                        <Name>Zeta</Name>\r\n" + //$NON-NLS-1$
	"                        <Zip>55555</Zip>\r\n" + //$NON-NLS-1$
	"                        <Orders/>\r\n" + //$NON-NLS-1$
	"                    </Supplier>\r\n" + //$NON-NLS-1$
	"                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
	"                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
	"                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
	"                        <Orders>\r\n" + //$NON-NLS-1$
	"                            <Order OrderID=\"5\">\r\n" + //$NON-NLS-1$
	"                                <OrderDate>06/01/02</OrderDate>\r\n" + //$NON-NLS-1$
	"                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
	"                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
	"                            </Order>\r\n" +  //$NON-NLS-1$
	"                            <Order OrderID=\"6\">\r\n" + //$NON-NLS-1$
	"                                <OrderDate>07/01/02</OrderDate>\r\n" + //$NON-NLS-1$
	"                                <OrderQuantity>1</OrderQuantity>\r\n" + //$NON-NLS-1$
	"                            </Order>\r\n" + //$NON-NLS-1$
	"                        </Orders>\r\n" + //$NON-NLS-1$
	"                    </Supplier>\r\n" + //$NON-NLS-1$
	"                </Suppliers>\r\n" + //$NON-NLS-1$
	"            </Item>\r\n" +  //$NON-NLS-1$
	"            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
	"                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
	"                <Quantity>5</Quantity>\r\n" + //$NON-NLS-1$
	"                <Suppliers>\r\n" + //$NON-NLS-1$
	"                    <Supplier SupplierID=\"51\">\r\n" + //$NON-NLS-1$
	"                        <Name>Chucky</Name>\r\n" + //$NON-NLS-1$
	"                        <Zip>11111</Zip>\r\n" + //$NON-NLS-1$
	"                        <Orders/>\r\n" + //$NON-NLS-1$
	"                    </Supplier>\r\n" + //$NON-NLS-1$
	"                    <Supplier SupplierID=\"52\">\r\n" + //$NON-NLS-1$
	"                        <Name>Biff's Stuff</Name>\r\n" + //$NON-NLS-1$
	"                        <Zip>22222</Zip>\r\n" + //$NON-NLS-1$
	"                        <Orders>\r\n" + //$NON-NLS-1$
	"                            <Order OrderID=\"2\">\r\n" + //$NON-NLS-1$
	"                                <OrderDate>12/31/01</OrderDate>\r\n" + //$NON-NLS-1$
	"                                <OrderQuantity>87</OrderQuantity>\r\n" + //$NON-NLS-1$
	"                                <OrderStatus>complete</OrderStatus>\r\n" + //$NON-NLS-1$
	"                            </Order>\r\n" + //$NON-NLS-1$
	"                        </Orders>\r\n" + //$NON-NLS-1$
	"                    </Supplier>\r\n" + //$NON-NLS-1$
	"                    <Supplier SupplierID=\"53\">\r\n" + //$NON-NLS-1$
	"                        <Name>AAAA</Name>\r\n" + //$NON-NLS-1$
	"                        <Zip>33333</Zip>\r\n" + //$NON-NLS-1$
	"                        <Orders/>\r\n" + //$NON-NLS-1$
	"                    </Supplier>\r\n" + //$NON-NLS-1$
	"                    <Supplier SupplierID=\"56\">\r\n" + //$NON-NLS-1$
	"                        <Name>Microsoft</Name>\r\n" + //$NON-NLS-1$
	"                        <Zip>66666</Zip>\r\n" + //$NON-NLS-1$
	"                        <Orders/>\r\n" + //$NON-NLS-1$
	"                    </Supplier>\r\n" + //$NON-NLS-1$
	"                </Suppliers>\r\n" + //$NON-NLS-1$
	"            </Item>\r\n" +              //$NON-NLS-1$
	"        </Items>\r\n" +  //$NON-NLS-1$
	"    </Catalog>\r\n" +  //$NON-NLS-1$
	"</Catalogs>\r\n\r\n";
    
    /**
     * Test of query with criteria written one way.  This test is paired up 
     * with {@link #testCase3225WithCriteriaReversed()}; both tests have the same
     * expected results and are identical queries except their compound criteria are 
     * written in reverse order relative to each other.  What Alan Tetrault found at
     * ESpace is that this changes actual results, which it shouldn't.  The likely
     * culprit is the algorithm to apply criteria to the implied context mapping class.
     * That is, the criteria is on nodes in the scope of two nested mapping classes
     * (the ones for orders and employees), but the implied context mapping class of both
     * of them is the root mapping class (for items).
     * 
     * The criteria "@supervisorID='1004'" should limit the returned items to items 001 and 002,
     * the criteria "order.orderquantity > 1" should limit the returned items to 001 and 003, so
     * the combined criteria should limit returned items to item 001.
     * @throws Exception
     */
    public void testCase3225WithCriteria() throws Exception {
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleCase3225();
        FakeDataManager dataMgr = exampleDataManagerCase3225(metadata);
        helpTestProcess("select * from xmltest.itemsdoc where employee.@supervisorID='1004' and order.orderquantity > 1", CASE_3225_WITH_CRITERIA_EXPECTED_DOC, metadata, dataMgr);         //$NON-NLS-1$
    }    

    /**
     * Test of query with criteria written the other way.  This test is paired up 
     * with {@link #testCase3225WithCriteria()}; both tests have the same
     * expected results and are identical queries except their compound criteria are 
     * written in reverse order relative to each other.  What Alan Tetrault found at
     * ESpace is that this changes actual results, which it shouldn't.  The likely
     * culprit is the algorithm to apply criteria to the implied context mapping class.
     * That is, the criteria is on nodes in the scope of two nested mapping classes
     * (the ones for orders and employees), but the implied context mapping class of both
     * of them is the root mapping class (for items).
     * 
     * The criteria "@supervisorID='1004'" should limit the returned items to items 001 and 002,
     * the criteria "order.orderquantity > 1" should limit the returned items to 001 and 003, so
     * the combined criteria should limit returned items to item 001.
     * @throws Exception
     */
    public void testCase3225WithCriteriaReversed() throws Exception {
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleCase3225();
        FakeDataManager dataMgr = exampleDataManagerCase3225(metadata);
        helpTestProcess("select * from xmltest.itemsdoc where order.orderquantity > 1 and employee.@supervisorID='1004'", CASE_3225_WITH_CRITERIA_EXPECTED_DOC, metadata, dataMgr);         //$NON-NLS-1$
    }      

    /**
     * Test the criteria from previous test, plus additional criteria explicitly
     * on the context mapping class (in this case, the root "items" mapping class)
     * to make sure all of the criteria is processed correctly. 
     * @throws Exception
     */
    public void testCase3225WithEmptyDocCriteria() throws Exception {
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleCase3225();
        FakeDataManager dataMgr = exampleDataManagerCase3225(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs>\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" + //$NON-NLS-1$
            "        <Items/>\r\n" + //$NON-NLS-1$
            "    </Catalog>\r\n" + //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("select * from xmltest.itemsdoc where order.orderquantity > 1 and employee.@supervisorID='1004' and item.@itemid='002'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }      
    
    /**
     * This just tests selecting * from the document, nothing fancy 
     * @throws Exception
     * @since 4.3
     */
    public void testBaseballPlayersDoc() throws Exception {
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleCase3225();
        FakeDataManager dataMgr = exampleDataManagerCase3225(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<BaseballPlayers>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1001\">\r\n" + //$NON-NLS-1$
            "      <FirstName>Albert</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Pujols</LastName>\r\n" + //$NON-NLS-1$
            "      <Manager ManagerID=\"1004\">\r\n" + //$NON-NLS-1$
            "         <FirstName>Tony</FirstName>\r\n" + //$NON-NLS-1$
            "         <LastName>LaRussa</LastName>\r\n" + //$NON-NLS-1$
            "         <Owner OwnerID=\"1009\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>DeWitt</LastName>\r\n" + //$NON-NLS-1$
            "         </Owner>\r\n" + //$NON-NLS-1$
            "      </Manager>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1002\">\r\n" + //$NON-NLS-1$
            "      <FirstName>Jim</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Edmunds</LastName>\r\n" + //$NON-NLS-1$
            "      <Manager ManagerID=\"1004\">\r\n" + //$NON-NLS-1$
            "         <FirstName>Tony</FirstName>\r\n" + //$NON-NLS-1$
            "         <LastName>LaRussa</LastName>\r\n" + //$NON-NLS-1$
            "         <Owner OwnerID=\"1009\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>DeWitt</LastName>\r\n" + //$NON-NLS-1$
            "         </Owner>\r\n" + //$NON-NLS-1$
            "      </Manager>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1003\">\r\n" + //$NON-NLS-1$
            "      <FirstName>David</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Eckstein</LastName>\r\n" + //$NON-NLS-1$
            "      <Manager ManagerID=\"1004\">\r\n" + //$NON-NLS-1$
            "         <FirstName>Tony</FirstName>\r\n" + //$NON-NLS-1$
            "         <LastName>LaRussa</LastName>\r\n" + //$NON-NLS-1$
            "         <Owner OwnerID=\"1009\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>DeWitt</LastName>\r\n" + //$NON-NLS-1$
            "         </Owner>\r\n" + //$NON-NLS-1$
            "      </Manager>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1005\">\r\n" + //$NON-NLS-1$
            "      <FirstName>Derrek</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Lee</LastName>\r\n" + //$NON-NLS-1$
            "      <Manager ManagerID=\"1007\">\r\n" + //$NON-NLS-1$
            "         <FirstName>Dusty</FirstName>\r\n" + //$NON-NLS-1$
            "         <LastName>Baker</LastName>\r\n" + //$NON-NLS-1$
            "         <Owner OwnerID=\"1010\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Some</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>Guy</LastName>\r\n" + //$NON-NLS-1$
            "         </Owner>\r\n" + //$NON-NLS-1$
            "      </Manager>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1006\">\r\n" + //$NON-NLS-1$
            "      <FirstName>Corey</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Patterson</LastName>\r\n" + //$NON-NLS-1$
            "      <Manager ManagerID=\"1007\">\r\n" + //$NON-NLS-1$
            "         <FirstName>Dusty</FirstName>\r\n" + //$NON-NLS-1$
            "         <LastName>Baker</LastName>\r\n" + //$NON-NLS-1$
            "         <Owner OwnerID=\"1010\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Some</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>Guy</LastName>\r\n" + //$NON-NLS-1$
            "         </Owner>\r\n" + //$NON-NLS-1$
            "      </Manager>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1008\">\r\n" + //$NON-NLS-1$
            "      <FirstName>Carlos</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Zambrano</LastName>\r\n" + //$NON-NLS-1$
            "      <Manager ManagerID=\"1007\">\r\n" + //$NON-NLS-1$
            "         <FirstName>Dusty</FirstName>\r\n" + //$NON-NLS-1$
            "         <LastName>Baker</LastName>\r\n" + //$NON-NLS-1$
            "         <Owner OwnerID=\"1010\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Some</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>Guy</LastName>\r\n" + //$NON-NLS-1$
            "         </Owner>\r\n" + //$NON-NLS-1$
            "      </Manager>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "</BaseballPlayers>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("select * from xmltest.playersDoc", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
        
    }
    
    /**
     * This one seems to work fine - criteria mapping class is 
     * managers while implied context mapping class is players. 
     * Expected result is same as previous test.
     * @throws Exception
     * @since 4.3
     */
    public void testBaseballPlayersDocCriteria() throws Exception {
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleCase3225();
        FakeDataManager dataMgr = exampleDataManagerCase3225(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<BaseballPlayers>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1001\">\r\n" + //$NON-NLS-1$
            "      <FirstName>Albert</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Pujols</LastName>\r\n" + //$NON-NLS-1$
            "      <Manager ManagerID=\"1004\">\r\n" + //$NON-NLS-1$
            "         <FirstName>Tony</FirstName>\r\n" + //$NON-NLS-1$
            "         <LastName>LaRussa</LastName>\r\n" + //$NON-NLS-1$
            "         <Owner OwnerID=\"1009\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>DeWitt</LastName>\r\n" + //$NON-NLS-1$
            "         </Owner>\r\n" + //$NON-NLS-1$
            "      </Manager>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1002\">\r\n" + //$NON-NLS-1$
            "      <FirstName>Jim</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Edmunds</LastName>\r\n" + //$NON-NLS-1$
            "      <Manager ManagerID=\"1004\">\r\n" + //$NON-NLS-1$
            "         <FirstName>Tony</FirstName>\r\n" + //$NON-NLS-1$
            "         <LastName>LaRussa</LastName>\r\n" + //$NON-NLS-1$
            "         <Owner OwnerID=\"1009\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>DeWitt</LastName>\r\n" + //$NON-NLS-1$
            "         </Owner>\r\n" + //$NON-NLS-1$
            "      </Manager>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1003\">\r\n" + //$NON-NLS-1$
            "      <FirstName>David</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Eckstein</LastName>\r\n" + //$NON-NLS-1$
            "      <Manager ManagerID=\"1004\">\r\n" + //$NON-NLS-1$
            "         <FirstName>Tony</FirstName>\r\n" + //$NON-NLS-1$
            "         <LastName>LaRussa</LastName>\r\n" + //$NON-NLS-1$
            "         <Owner OwnerID=\"1009\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>DeWitt</LastName>\r\n" + //$NON-NLS-1$
            "         </Owner>\r\n" + //$NON-NLS-1$
            "      </Manager>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "</BaseballPlayers>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("select * from xmltest.playersDoc where manager.@managerid = '1004'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
        
    }     
    
    /**
     * This also seems to work fine.  The context mapping class is the 
     * middle one (managers). 
     * @throws Exception
     * @since 4.3
     */
    public void testBaseballPlayersDocContextCriteria() throws Exception {
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleCase3225();
        FakeDataManager dataMgr = exampleDataManagerCase3225(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<BaseballPlayers>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1001\">\r\n" + //$NON-NLS-1$
            "      <FirstName>Albert</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Pujols</LastName>\r\n" + //$NON-NLS-1$
            "      <Manager ManagerID=\"1004\">\r\n" + //$NON-NLS-1$
            "         <FirstName>Tony</FirstName>\r\n" + //$NON-NLS-1$
            "         <LastName>LaRussa</LastName>\r\n" + //$NON-NLS-1$
            "         <Owner OwnerID=\"1009\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>DeWitt</LastName>\r\n" + //$NON-NLS-1$
            "         </Owner>\r\n" + //$NON-NLS-1$
            "      </Manager>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1002\">\r\n" + //$NON-NLS-1$
            "      <FirstName>Jim</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Edmunds</LastName>\r\n" + //$NON-NLS-1$
            "      <Manager ManagerID=\"1004\">\r\n" + //$NON-NLS-1$
            "         <FirstName>Tony</FirstName>\r\n" + //$NON-NLS-1$
            "         <LastName>LaRussa</LastName>\r\n" + //$NON-NLS-1$
            "         <Owner OwnerID=\"1009\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>DeWitt</LastName>\r\n" + //$NON-NLS-1$
            "         </Owner>\r\n" + //$NON-NLS-1$
            "      </Manager>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1003\">\r\n" + //$NON-NLS-1$
            "      <FirstName>David</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Eckstein</LastName>\r\n" + //$NON-NLS-1$
            "      <Manager ManagerID=\"1004\">\r\n" + //$NON-NLS-1$
            "         <FirstName>Tony</FirstName>\r\n" + //$NON-NLS-1$
            "         <LastName>LaRussa</LastName>\r\n" + //$NON-NLS-1$
            "         <Owner OwnerID=\"1009\">\r\n" + //$NON-NLS-1$
            "            <FirstName>Bill</FirstName>\r\n" + //$NON-NLS-1$
            "            <LastName>DeWitt</LastName>\r\n" + //$NON-NLS-1$
            "         </Owner>\r\n" + //$NON-NLS-1$
            "      </Manager>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1005\">\r\n" + //$NON-NLS-1$
            "      <FirstName>Derrek</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Lee</LastName>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1006\">\r\n" + //$NON-NLS-1$
            "      <FirstName>Corey</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Patterson</LastName>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "   <Player PlayerID=\"1008\">\r\n" + //$NON-NLS-1$
            "      <FirstName>Carlos</FirstName>\r\n" + //$NON-NLS-1$
            "      <LastName>Zambrano</LastName>\r\n" + //$NON-NLS-1$
            "   </Player>\r\n" + //$NON-NLS-1$
            "</BaseballPlayers>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("select * from xmltest.playersDoc where context(manager, owner.@ownerid) = '1009'", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
        
    }    
    
    public void testProcedureAndXML() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        helpTestProcess("exec xmltest.vsp1()", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
    }
    
    /**
     * When a element with source node is specied, it can be unbouned or bounded. In the case
     * of bounded, but result set is returning more results then it should fail.
     */
    public void defer_testMinMaxOnSourceNode() throws Exception {
        FakeMetadataFacade metadata = exampleMetadataCached();
        FakeDataManager dataMgr = exampleDataManager(metadata);
        String expectedDoc = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +  //$NON-NLS-1$
            "<Catalogs xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n" + //$NON-NLS-1$
            "    <Catalog>\r\n" +  //$NON-NLS-1$
            "        <Items>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"001\">\r\n" +  //$NON-NLS-1$
            "                <Name>Lamp</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>5</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"002\">\r\n" +  //$NON-NLS-1$
            "                <Name>Screwdriver</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>100</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "            <Item ItemID=\"003\">\r\n" +  //$NON-NLS-1$
            "                <Name>Goat</Name>\r\n" +  //$NON-NLS-1$
            "                <Quantity>4</Quantity>\r\n" +  //$NON-NLS-1$
            "            </Item>\r\n" +  //$NON-NLS-1$
            "        </Items>\r\n" +  //$NON-NLS-1$
            "    </Catalog>\r\n" +  //$NON-NLS-1$
            "</Catalogs>\r\n\r\n"; //$NON-NLS-1$
        
        try {
            helpTestProcess("SELECT * FROM xmltest.docBounded", expectedDoc, metadata, dataMgr);         //$NON-NLS-1$
            fail("should have failed the document restrictions."); //$NON-NLS-1$
        } catch (MetaMatrixProcessingException e) {
            // pass
        }
    }

    private static final class SimpleCapabilitiesFinder implements CapabilitiesFinder{
        private SourceCapabilities caps;
        SimpleCapabilitiesFinder(SourceCapabilities caps){
            this.caps = caps;
        }    
        public SourceCapabilities findCapabilities(String modelName) {
            return caps;
        }    
    }
}
