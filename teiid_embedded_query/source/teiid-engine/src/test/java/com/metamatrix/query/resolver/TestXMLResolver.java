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

package com.metamatrix.query.resolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.metamatrix.common.types.DataTypeManager;
import com.metamatrix.query.analysis.AnalysisRecord;
import com.metamatrix.query.parser.ParseInfo;
import com.metamatrix.query.parser.QueryParser;
import com.metamatrix.query.sql.lang.Command;
import com.metamatrix.query.sql.lang.CompareCriteria;
import com.metamatrix.query.sql.lang.Criteria;
import com.metamatrix.query.sql.lang.Query;
import com.metamatrix.query.sql.symbol.Constant;
import com.metamatrix.query.sql.symbol.ElementSymbol;
import com.metamatrix.query.sql.symbol.Expression;
import com.metamatrix.query.sql.symbol.Function;
import com.metamatrix.query.sql.symbol.GroupSymbol;
import com.metamatrix.query.sql.util.ElementSymbolOptimizer;
import com.metamatrix.query.sql.visitor.VariableCollectorVisitor;
import com.metamatrix.query.unittest.FakeMetadataFactory;

import junit.framework.TestCase;

public class TestXMLResolver extends TestCase {
    
    public Command helpResolve(String sql) {
        Command cmd = TestResolver.helpResolve(sql, FakeMetadataFactory.example1Cached(), AnalysisRecord.createNonRecordingRecord());
        ElementSymbolOptimizer.fullyQualifyElements(cmd);
        return cmd;
    }
    
    public void helpResolveException(String sql) {
        TestResolver.helpResolveException(sql, FakeMetadataFactory.example1Cached());
    }
    
    public void helpResolveException(String sql, String expectedMessage) {
        TestResolver.helpResolveException(sql, FakeMetadataFactory.example1Cached(), expectedMessage);
    }

    public void testXMLCriteriaShortElement() {
        CompareCriteria expected = new CompareCriteria();
        ElementSymbol es = new ElementSymbol("xmltest.doc1.root.node1"); //$NON-NLS-1$
        GroupSymbol gs = new GroupSymbol("doc1"); //$NON-NLS-1$
        es.setGroupSymbol(gs);
        expected.setLeftExpression(es);
        expected.setOperator(CompareCriteria.EQ);
        expected.setRightExpression(new Constant("yyz")); //$NON-NLS-1$

        Query query = (Query) helpResolve("select * from xmltest.doc1 where node1 = 'yyz'"); //$NON-NLS-1$
        Criteria actual = query.getCriteria();
        assertEquals("Did not match expected criteria", expected, actual);     //$NON-NLS-1$
    }   

    public void testXMLCriteriaLongElement1() {
        CompareCriteria expected = new CompareCriteria();
        ElementSymbol es = new ElementSymbol("xmltest.doc1.root.node1"); //$NON-NLS-1$
        GroupSymbol gs = new GroupSymbol("doc1"); //$NON-NLS-1$
        es.setGroupSymbol(gs);
        expected.setLeftExpression(es);
        expected.setOperator(CompareCriteria.EQ);
        expected.setRightExpression(new Constant("yyz")); //$NON-NLS-1$

        Query query = (Query) helpResolve("select * from xmltest.doc1 where root.node1 = 'yyz'"); //$NON-NLS-1$
        Criteria actual = query.getCriteria();
        assertEquals("Did not match expected criteria", expected, actual);     //$NON-NLS-1$
    }
    
    public void testXMLCriteriaLongElement2() {
        CompareCriteria expected1 = new CompareCriteria();
        ElementSymbol es1 = new ElementSymbol("xmltest.doc4.root.node1"); //$NON-NLS-1$
        GroupSymbol gs = new GroupSymbol("doc4"); //$NON-NLS-1$
        es1.setGroupSymbol(gs);
        expected1.setLeftExpression(es1);
        expected1.setOperator(CompareCriteria.EQ);
        expected1.setRightExpression(new Constant("xyz"));         //$NON-NLS-1$
        
        Query query = (Query) helpResolve("select * from xmltest.doc4 where root.node1 = 'xyz'"); //$NON-NLS-1$
        Criteria actual = query.getCriteria();
        assertEquals("Did not match expected criteria", expected1, actual); //$NON-NLS-1$
    }
    
    public void testXMLCriteriaLongElement3() {
        GroupSymbol gs = new GroupSymbol("doc4"); //$NON-NLS-1$
        CompareCriteria expected2 = new CompareCriteria();
        ElementSymbol es2 = new ElementSymbol("xmltest.doc4.root.node1.@node2"); //$NON-NLS-1$
        es2.setGroupSymbol(gs);
        expected2.setLeftExpression(es2);
        expected2.setOperator(CompareCriteria.EQ);
        expected2.setRightExpression(new Constant("xyz")); //$NON-NLS-1$
        
        Query query = (Query) helpResolve("select * from xmltest.doc4 where root.node1.@node2 = 'xyz'"); //$NON-NLS-1$
        Criteria actual = query.getCriteria();
        assertEquals("Did not match expected criteria", expected2, actual); //$NON-NLS-1$
    }
        
    public void testXMLCriteriaLongElement4() {
        GroupSymbol gs = new GroupSymbol("doc4"); //$NON-NLS-1$
        CompareCriteria expected3 = new CompareCriteria();
        ElementSymbol es3 = new ElementSymbol("xmltest.doc4.root.node3"); //$NON-NLS-1$
        es3.setGroupSymbol(gs);
        expected3.setLeftExpression(es3);
        expected3.setOperator(CompareCriteria.EQ);
        expected3.setRightExpression(new Constant("xyz")); //$NON-NLS-1$

        Query query = (Query) helpResolve("select * from xmltest.doc4 where root.node3 = 'xyz'"); //$NON-NLS-1$
        Criteria actual = query.getCriteria();
        assertEquals("Did not match expected criteria", expected3, actual);                 //$NON-NLS-1$
    }
    
    public void testXMLCriteriaLongElement5() {
        helpResolve("select * from xmltest.doc4 where root.node1 = 'yyz'"); //$NON-NLS-1$
    }
    
    public void testXMLCriteriaLongElement6() {
        helpResolve("select * from xmltest.doc4 where root.node1.@node2 = 'yyz'"); //$NON-NLS-1$
    } 

    public void testXMLCriteriaLongElement7() {    
        helpResolve("select * from xmltest.doc4 where root.node3 = 'yyz'"); //$NON-NLS-1$
    }
    
    public void testXMLCriteriaLongElement8() {    
        helpResolve("select * from xmltest.doc4 where node3 = 'yyz'");         //$NON-NLS-1$
    }
    
    public void testXMLCriteriaLongElementFail1() {    
        helpResolveException("select * from xmltest.doc4 where node3.node1.node2 = 'xyz'"); //$NON-NLS-1$
    }
    
    public void testXMLCriteriaLongElementFail2() {    
        helpResolveException("select * from xmltest.doc4 where root.node1.node2.node3 = 'xyz'"); //$NON-NLS-1$
    }
    
    public void testXMLCriteriaLongElementFail3() {    
        helpResolveException("select * from xmltest.doc4 where root.node1.node3 = 'xyz'"); //$NON-NLS-1$
    }
    
    public void testXMLCriteriaLongElementFail4() {    
        helpResolveException("select * from xmltest.doc4 where node2.node1.node2 = 'xyz'");                              //$NON-NLS-1$
    }
    
    public void testXMLCriteriaTempElement1() {
        helpResolve("select * from xmltest.doc4 where tm1.g1.e1 = 'x'"); //$NON-NLS-1$
    } 
    
    public void testXMLCriteriaTempElement2() {
        helpResolve("select * from xmltest.doc4 where root.node1.@node2 = 'yyz' and tm1.g1.e2 = 'y'"); //$NON-NLS-1$
    }
    
    public void testXMLCriteriaTempElement3() {
        helpResolve("select * from xmltest.doc4 where tm1.g1.e1 = 'x' and tm1.g1.e2 = 'y'"); //$NON-NLS-1$
    }

    public void testXMLCriteriaTempElementFail1() {    
        helpResolveException("select * from xmltest.doc4 where tm1.g2.e1 = 'xyz'"); //$NON-NLS-1$
    } 
    
    public void testXMLCriteriaTempElementFail2() {
        helpResolveException("select * from xmltest.doc4 where root.node1.node2.node3 = 'xyz' and e1 = 'x'"); //$NON-NLS-1$
    }
    
    public void testXMLCriteriaTempElementFail3() {
        helpResolveException("select * from xmltest.doc4 where e3 = 'xyz' and tm1.g2.e4='m'"); //$NON-NLS-1$
    }

    //tests ambiguously-named elements in both root temp group and document
    public void testXMLAmbiguousName1() {
        helpResolve("select * from xmltest.doc4 where root.node1 is null"); //$NON-NLS-1$
    }
    
    public void testXMLAmbiguousName2() {
        helpResolve("select * from xmltest.doc4 where tm1.g1.node1 = 'yyz'"); //$NON-NLS-1$
    }
    
    public void testXMLAmbiguousName3() {
        helpResolveException("select * from xmltest.doc4 where node1 = 'yyz'"); //$NON-NLS-1$
    }    

    public void testXMLCriteriaLongElementInAnonymous() {                  
        CompareCriteria expected = new CompareCriteria();
        ElementSymbol es = new ElementSymbol("xmltest.doc2.root.node1.node3"); //$NON-NLS-1$
        GroupSymbol gs = new GroupSymbol("doc2"); //$NON-NLS-1$
        es.setGroupSymbol(gs);
        expected.setLeftExpression(es);
        expected.setOperator(CompareCriteria.EQ);
        expected.setRightExpression(new Constant("yyz")); //$NON-NLS-1$

        Query query = (Query) helpResolve("select * from xmltest.doc2 where root.node1.node3 = 'yyz'"); //$NON-NLS-1$
        Criteria actual = query.getCriteria();
        assertEquals("Did not match expected criteria", expected, actual);     //$NON-NLS-1$
    }    

    public void testXMLAmbiguousShortName() {                  
        CompareCriteria expected = new CompareCriteria();
        ElementSymbol es = new ElementSymbol("node2"); //$NON-NLS-1$
        GroupSymbol gs = new GroupSymbol("doc3"); //$NON-NLS-1$
        es.setGroupSymbol(gs);
        expected.setLeftExpression(es);
        expected.setOperator(CompareCriteria.EQ);
        expected.setRightExpression(new Constant("yyz")); //$NON-NLS-1$

        helpResolveException("select * from xmltest.doc3 where node2 = 'yyz'"); //$NON-NLS-1$
    }    

    /**
     * defect 9745
     */
    public void testXMLAttributeInCriteria() {
        helpResolve("select * from xmltest.doc4 where root.node1.@node2 = 'x'"); //$NON-NLS-1$
    }

    /**
     * defect 9745
     */
    public void testXMLAttributeInCriteria2() {
        helpResolve("select * from xmltest.doc4 where root.node1.node2 = 'x'"); //$NON-NLS-1$
    }

    /**
     * defect 9745
     */
    public void testXMLAttributeInCriteria3() {
        helpResolve("select * from xmltest.doc4 where node2 = 'x'"); //$NON-NLS-1$
    }

    public void testXMLAttributeElementAmbiguity1() {
        helpResolve("select * from xmltest.doc4 where root.node3.node4 = 'x'"); //$NON-NLS-1$
    }
    
    public void testXMLAttributeElementAmbiguity2() {        
        helpResolve("select * from xmltest.doc4 where root.node3.@node4 = 'x'"); //$NON-NLS-1$
    }
    
    public void testXMLAttributeElementAmbiguity3() {
        helpResolve("select * from xmltest.doc4 where root.node3.node4 = 'x' and root.node3.@node4='y'"); //$NON-NLS-1$
    }       

    /*
     * This should resolve to the XML element root.node3.root.node6
     */
    public void testXMLAttributeElementAmbiguity4() {
        helpResolve("select * from xmltest.doc4 where root.node6 = 'x'"); //$NON-NLS-1$
    }       

    /*
     * This should resolve to the XML attribute root.@node6
     */
    public void testXMLAttributeElementAmbiguity5() {
        helpResolve("select * from xmltest.doc4 where root.@node6 = 'x'"); //$NON-NLS-1$
    }       

    public void testXMLAttributeFullPath() {
        helpResolve("select * from xmltest.doc4 where xmltest.doc4.root.@node6 = 'x'"); //$NON-NLS-1$
    }       
    
    public void testXMLCriteriaLongElementWithGroup1() {
        helpResolve("select * from xmltest.doc4 where xmltest.doc4.root.node1 = 'yyz'"); //$NON-NLS-1$
    }
    
    public void testXMLCriteriaLongElementWithGroup2() {
        helpResolve("select * from xmltest.doc4 where xmltest.doc4.root.node1.@node2 = 'yyz'"); //$NON-NLS-1$
    } 

    public void testXMLCriteriaLongElementWithGroup3() {    
        helpResolve("select * from xmltest.doc4 where xmltest.doc4.root.node3 = 'yyz'"); //$NON-NLS-1$
    }

    /*public void testXMLElementPotentialAmbiguous() {    
        helpResolve("select * from xmltest.doc6 where node = 'yyz'");
    }*/

    public void testXMLSelect() {        
        helpResolve("select root.node3.@node4 from xmltest.doc4"); //$NON-NLS-1$
    }        

    public void testXMLSelect2() {        
        helpResolve("select root.node3.node4 from xmltest.doc4"); //$NON-NLS-1$
    }        

    public void testXMLSelect3() {        
        helpResolve("select root.@node6 from xmltest.doc4"); //$NON-NLS-1$
    }    

    public void testXMLSelect4() {        
        helpResolve("select root.node6 from xmltest.doc4"); //$NON-NLS-1$
    }    

    public void testXMLSelect5() {        
        helpResolve("select node2 from xmltest.doc4"); //$NON-NLS-1$
    }
    
    public void testDEFECT_19771() {
        helpResolveException("select node2 AS NODE2 from xmltest.doc4"); //$NON-NLS-1$
    }
        
    public void testContext() {                  
        ElementSymbol es1 = new ElementSymbol("xmltest.doc1.root.node1.node2.node3"); //$NON-NLS-1$
        GroupSymbol gs1 = new GroupSymbol("doc1"); //$NON-NLS-1$
        es1.setGroupSymbol(gs1);
        ElementSymbol es2 = new ElementSymbol("xmltest.doc1.root.node1"); //$NON-NLS-1$
        GroupSymbol gs2 = new GroupSymbol("doc1"); //$NON-NLS-1$
        es2.setGroupSymbol(gs2);
        Expression[] exprs = new Expression[]{es1, es2};
        
        Function context = new Function("context", exprs); //$NON-NLS-1$
        
        CompareCriteria expected = new CompareCriteria();
        expected.setLeftExpression(context);
        expected.setOperator(CompareCriteria.EQ);
        expected.setRightExpression(new Constant("yyz")); //$NON-NLS-1$

        Query query = (Query) helpResolve("select * from xmltest.doc1 where context(node3, node1) = 'yyz'"); //$NON-NLS-1$
        Criteria actual = query.getCriteria();
        assertEquals("Did not match expected criteria", expected, actual);         //$NON-NLS-1$
    }    

    public void testRowLimit() {                  
        ElementSymbol es1 = new ElementSymbol("xmltest.doc1.root.node1.node2.node3"); //$NON-NLS-1$
        GroupSymbol gs1 = new GroupSymbol("doc1"); //$NON-NLS-1$
        es1.setGroupSymbol(gs1);
        Expression[] exprs = new Expression[]{es1};
        
        Function context = new Function("rowlimit", exprs); //$NON-NLS-1$
        
        CompareCriteria expected = new CompareCriteria();
        expected.setLeftExpression(context);
        expected.setOperator(CompareCriteria.EQ);
        expected.setRightExpression(new Constant(new Integer(2))); 

        Query query = (Query) helpResolve("select * from xmltest.doc1 where rowlimit(node3) = 2"); //$NON-NLS-1$
        Criteria actual = query.getCriteria();
        assertEquals("Did not match expected criteria", expected, actual);         //$NON-NLS-1$
    }    

    public void testRowLimitException() {                  
        ElementSymbol es1 = new ElementSymbol("xmltest.doc1.root.node1.node2.node3"); //$NON-NLS-1$
        GroupSymbol gs1 = new GroupSymbol("doc1"); //$NON-NLS-1$
        es1.setGroupSymbol(gs1);
        Expression[] exprs = new Expression[]{es1};
        
        Function context = new Function("rowlimitexception", exprs); //$NON-NLS-1$
        
        CompareCriteria expected = new CompareCriteria();
        expected.setLeftExpression(context);
        expected.setOperator(CompareCriteria.EQ);
        expected.setRightExpression(new Constant(new Integer(2))); 

        Query query = (Query) helpResolve("select * from xmltest.doc1 where rowlimitexception(node3) = 2"); //$NON-NLS-1$
        Criteria actual = query.getCriteria();
        assertEquals("Did not match expected criteria", expected, actual);         //$NON-NLS-1$
    }     
    
    public void testXMLQueryFail1() {
        helpResolveException("SELECT DISTINCT * FROM vm1.doc1"); //$NON-NLS-1$
    }

    public void testXMLQueryFail2() {
        helpResolveException("SELECT a2 FROM vm1.doc1"); //$NON-NLS-1$
    }

    public void testXMLQueryFail3() {
        helpResolveException("SELECT * FROM vm1.doc1, vm1.doc2"); //$NON-NLS-1$
    }
    
    public void testXMLQueryWithParam1() throws Exception {
        Command command =  QueryParser.getQueryParser().parseCommand("select * from xmltest.doc4 where xmltest.doc4.root.node3 = pm1.sq5.in1"); //$NON-NLS-1$
        
        // resolve
        // Construct command metadata 
        GroupSymbol sqGroup = new GroupSymbol("pm1.sq5");  //$NON-NLS-1$
        ArrayList sqParams = new ArrayList();
        ElementSymbol in = new ElementSymbol("pm1.sq5.in1"); //$NON-NLS-1$
        in.setType(DataTypeManager.DefaultDataClasses.STRING);
        sqParams.add(in);
        Map externalMetadata = new HashMap();
        externalMetadata.put(sqGroup, sqParams);
        
        QueryResolver.resolveCommand(command, externalMetadata, false, FakeMetadataFactory.example1Cached(), AnalysisRecord.createNonRecordingRecord());
    
        // Verify results        
        Collection vars = VariableCollectorVisitor.getVariables(command, false);
        assertEquals("Did not find variable in resolved query", 1, vars.size()); //$NON-NLS-1$
    }
      
    public void testXMLWithOrderBy1() {
        helpResolveException("select * from xmltest.doc4 order by node1");             //$NON-NLS-1$
    }
    
    public void testConversionInXML() {
        // Expected left expression
        ElementSymbol es1 = new ElementSymbol("xmltest.doc1.root.node1"); //$NON-NLS-1$
        GroupSymbol gs1 = new GroupSymbol("doc1"); //$NON-NLS-1$
        es1.setGroupSymbol(gs1);

        // Expected right expression
        Function convert = new Function("convert", new Expression[] { new Constant(new Integer(5)), new Constant("string") }); //$NON-NLS-1$ //$NON-NLS-2$

        // Expected criteria
        CompareCriteria expected = new CompareCriteria();
        expected.setLeftExpression(es1);
        expected.setOperator(CompareCriteria.EQ);
        expected.setRightExpression(convert);

        // Resolve the query and check against expected objects
        Query query = (Query) helpResolve("select * from xmltest.doc1 where node1 = convert(5, string)"); //$NON-NLS-1$
        Criteria actual = query.getCriteria();
        assertEquals("Did not match expected criteria", expected, actual); //$NON-NLS-1$
        Function actualRightExpr = (Function) ((CompareCriteria)actual).getRightExpression();
        assertNotNull("Failed to resolve function", actualRightExpr.getFunctionDescriptor()); //$NON-NLS-1$
    }

    public void testXMLWithSelect1() throws Exception {
        CompareCriteria expected = new CompareCriteria();
        ElementSymbol es = new ElementSymbol("xmltest.doc1.root.node1"); //$NON-NLS-1$
        GroupSymbol gs = new GroupSymbol("doc1"); //$NON-NLS-1$
        es.setGroupSymbol(gs);
        expected.setLeftExpression(es);
        expected.setOperator(CompareCriteria.EQ);
        expected.setRightExpression(new Constant("yyz")); //$NON-NLS-1$
    
        ParseInfo info = new ParseInfo();
        info.allowDoubleQuotedVariable = true;
        Query query = (Query) TestResolver.helpResolve(QueryParser.getQueryParser().parseCommand("select \"xml\" from xmltest.doc1 where node1 = 'yyz'", info), FakeMetadataFactory.example1Cached(), null); //$NON-NLS-1$
        Criteria actual = query.getCriteria();
        assertEquals("Did not match expected criteria", expected, actual);     //$NON-NLS-1$
    } 
    
    public void testXMLWithSelect1a() {
        helpResolveException("select 'a' from xmltest.doc1 where node1 = 'yyz'", "Expressions cannot be selected by XML Queries"); //$NON-NLS-1$ //$NON-NLS-2$
    } 

    public void testXMLWithSelect2() {
        CompareCriteria expected = new CompareCriteria();
        ElementSymbol es = new ElementSymbol("xmltest.doc1.root.node1"); //$NON-NLS-1$
        GroupSymbol gs = new GroupSymbol("doc1"); //$NON-NLS-1$
        es.setGroupSymbol(gs);
        expected.setLeftExpression(es);
        expected.setOperator(CompareCriteria.EQ);
        expected.setRightExpression(new Constant("yyz")); //$NON-NLS-1$

        Query query = (Query) helpResolve("select xmltest.doc1.xml from xmltest.doc1 where node1 = 'yyz'"); //$NON-NLS-1$
        Criteria actual = query.getCriteria();
        assertEquals("Did not match expected criteria", expected, actual);     //$NON-NLS-1$
    }    

}
