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

package com.metamatrix.query.sql.util;

import java.util.*;

import junit.framework.*;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.query.QueryMetadataException;
import com.metamatrix.api.exception.query.QueryParserException;
import com.metamatrix.api.exception.query.QueryResolverException;
import com.metamatrix.query.analysis.AnalysisRecord;
import com.metamatrix.query.metadata.QueryMetadataInterface;
import com.metamatrix.query.parser.QueryParser;
import com.metamatrix.query.resolver.QueryResolver;
import com.metamatrix.query.sql.lang.Command;
import com.metamatrix.query.sql.symbol.ElementSymbol;
import com.metamatrix.query.sql.symbol.GroupSymbol;
import com.metamatrix.query.unittest.FakeMetadataFactory;

/**
 */
public class TestElementSymbolOptimizer extends TestCase {

    /**
     * Constructor for TestElementSymbolOptimizer.
     * @param name
     */
    public TestElementSymbolOptimizer(String name) {
        super(name);
    }
    
    public Command helpResolve(String sql, QueryMetadataInterface metadata, Map externalMetadata) throws QueryParserException, QueryResolverException, MetaMatrixComponentException {
        Command command = QueryParser.getQueryParser().parseCommand(sql);

        final boolean USE_METADATA_COMMANDS = true;
        QueryResolver.resolveCommand(command, externalMetadata, USE_METADATA_COMMANDS, metadata, AnalysisRecord.createNonRecordingRecord());
        
        return command;      
    }
    

    public void helpTestOptimize(String sql, QueryMetadataInterface metadata, String expected) throws QueryMetadataException, MetaMatrixComponentException, QueryParserException, QueryResolverException {
        this.helpTestOptimize(sql, metadata, expected, Collections.EMPTY_MAP);    
    }
    
    
    public void helpTestOptimize(String sql, QueryMetadataInterface metadata, String expected, Map externalMetadata) throws QueryMetadataException, MetaMatrixComponentException, QueryParserException, QueryResolverException {
        Command command = helpResolve(sql, metadata, externalMetadata);
        ElementSymbolOptimizer.optimizeElements(command, metadata);
        String actual = command.toString();
            
        assertEquals("Expected different optimized string", expected, actual);             //$NON-NLS-1$
    }

    /** Can be optimized */
    public void testOptimize1() throws Exception {
        helpTestOptimize("SELECT pm1.g1.e1, pm1.g1.e2 FROM pm1.g1", //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "SELECT e1, e2 FROM pm1.g1"); //$NON-NLS-1$
    }

    /** Can't be optimized */
    public void testOptimize2() throws Exception {
        helpTestOptimize("SELECT pm1.g1.e1, pm1.g1.e2 FROM pm1.g1, pm1.g2", //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "SELECT pm1.g1.e1, pm1.g1.e2 FROM pm1.g1, pm1.g2"); //$NON-NLS-1$
    }

    public void testOptimize3() throws Exception {
        helpTestOptimize("UPDATE pm1.g1 SET pm1.g1.e1 = 'e' WHERE pm1.g1.e2 = 3", //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "UPDATE pm1.g1 SET e1 = 'e' WHERE e2 = 3"); //$NON-NLS-1$
    }

    public void testOptimize4() throws Exception {
        helpTestOptimize("INSERT INTO pm1.g1 (pm1.g1.e1, pm1.g1.e2) VALUES ('e', 3)", //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "INSERT INTO pm1.g1 (e1, e2) VALUES ('e', 3)"); //$NON-NLS-1$
    }

    public void testOptimize5() throws Exception {
        helpTestOptimize("DELETE FROM pm1.g1 WHERE pm1.g1.e2 = 3", //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "DELETE FROM pm1.g1 WHERE e2 = 3"); //$NON-NLS-1$
    }
    
    public void testOptimize6() throws Exception {
        helpTestOptimize("SELECT pm1.g1.e1, pm1.g1.e2 FROM pm1.g1 WHERE e2 > (SELECT AVG(pm1.g2.e2) FROM pm1.g2 WHERE pm1.g1.e1 = pm1.g2.e1)", //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "SELECT e1, e2 FROM pm1.g1 WHERE e2 > (SELECT AVG(pm1.g2.e2) FROM pm1.g2 WHERE pm1.g1.e1 = pm1.g2.e1)"); //$NON-NLS-1$
    }

    /** alias */
    public void testOptimize7() throws Exception {
        helpTestOptimize("SELECT 'text' AS zz, pm1.g1.e2 FROM pm1.g1", //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "SELECT 'text' AS zz, e2 FROM pm1.g1"); //$NON-NLS-1$
    }

    public void testOptimize8() throws Exception {
        helpTestOptimize("SELECT 1, 'xyz'", //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "SELECT 1, 'xyz'"); //$NON-NLS-1$
    }

    public void helpTestFullyQualify(String sql, QueryMetadataInterface metadata, String expected) throws QueryParserException, QueryResolverException, MetaMatrixComponentException {
        Command command = helpResolve(sql, metadata, Collections.EMPTY_MAP);
        ElementSymbolOptimizer.fullyQualifyElements(command);
        String actual = command.toString();

        assertEquals("Expected different fully qualified string", expected, actual); //$NON-NLS-1$
    }
    
    public void testFullyQualify1() throws Exception {
        helpTestFullyQualify("SELECT e1, e2 FROM pm1.g1",  //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "SELECT pm1.g1.e1, pm1.g1.e2 FROM pm1.g1"); //$NON-NLS-1$
    }
    
    public void testXMLQuery() throws Exception {
        helpTestOptimize("SELECT root.node1.node2.node3 FROM xmltest.doc1",  //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "SELECT root.node1.node2.node3 FROM xmltest.doc1"); //$NON-NLS-1$
    }
    
    public void testVirtualStoredProcedure() throws Exception {
        helpTestOptimize("EXEC pm1.vsp7(5)",  //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "EXEC pm1.vsp7(5)"); //$NON-NLS-1$
    }

    public void testStoredQueryTransform() throws Exception {
        
        // Set up external metadata - stored query pm1.sq3 with
        // params in and in2
        Map externalMetadata = new HashMap();
        GroupSymbol gs = new GroupSymbol("pm1.sq3"); //$NON-NLS-1$
        List elements = new ArrayList(2);
        elements.add(new ElementSymbol("pm1.sq3.in")); //$NON-NLS-1$
        elements.add(new ElementSymbol("pm1.sq3.in2")); //$NON-NLS-1$
        externalMetadata.put(gs, elements);
        
        helpTestOptimize("SELECT pm1.g6.in, pm1.g6.in3 FROM pm1.g6 WHERE pm1.g6.in=pm1.sq3.in AND pm1.g6.in3=pm1.sq3.in2",  //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(),  
                            "SELECT pm1.g6.\"in\", in3 FROM pm1.g6 WHERE (pm1.g6.\"in\" = pm1.sq3.\"in\") AND (in3 = in2)", //$NON-NLS-1$
                            externalMetadata); 
    }
    
    /** Test stored query whose transformation is another stored query */
    public void testStoredQueryTransform2() throws Exception {
        // Set up external metadata - stored query pm1.sq3 with
        // params in and in2
        Map externalMetadata = new HashMap();
        GroupSymbol gs = new GroupSymbol("pm1.sq3"); //$NON-NLS-1$
        List elements = new ArrayList(2);
        elements.add(new ElementSymbol("pm1.sq3.in")); //$NON-NLS-1$
        elements.add(new ElementSymbol("pm1.sq3.in2")); //$NON-NLS-1$
        externalMetadata.put(gs, elements);
        
        helpTestOptimize("EXEC pm1.sq2(pm1.sq3.in)",  //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(),  
                            "EXEC pm1.sq2(pm1.sq3.\"in\")", //$NON-NLS-1$
                            externalMetadata);         
    }

    public void testStoredQuerySubquery() throws Exception {
        helpTestOptimize("select x.e1 from (EXEC pm1.sq1()) as x",  //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "SELECT e1 FROM (EXEC pm1.sq1()) AS x"); //$NON-NLS-1$
    }

    public void testStoredQuerySubquery2() throws Exception {
        helpTestOptimize("select x.e1 from (EXEC pm1.sq1()) as x WHERE x.e2 = 3",  //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "SELECT e1 FROM (EXEC pm1.sq1()) AS x WHERE e2 = 3"); //$NON-NLS-1$
    }

    public void testStoredQuerySubquery3() throws Exception {
        
        // Set up external metadata - stored query pm1.sq3 with
        // params in and in2
        Map externalMetadata = new HashMap();
        GroupSymbol gs = new GroupSymbol("SYSTEM.DESCRIBE"); //$NON-NLS-1$
        List elements = new ArrayList(2);
        elements.add(new ElementSymbol("SYSTEM.DESCRIBE.entity")); //$NON-NLS-1$
        externalMetadata.put(gs, elements);
        
        
        helpTestOptimize("select nvl(entities.entityType, '') as Description " +  //$NON-NLS-1$
                         "from (SELECT 'Model' AS entityType, pm1.g1.e1 AS entityName FROM pm1.g1 " + //$NON-NLS-1$
                         "UNION ALL SELECT 'Group', pm1.g2.e1 FROM pm1.g2) as entities " +  //$NON-NLS-1$
                         "WHERE ucase(entities.entityName) = ucase(SYSTEM.DESCRIBE.entity)",  //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "SELECT nvl(entityType, '') AS Description " +  //$NON-NLS-1$
                            "FROM (SELECT 'Model' AS entityType, e1 AS entityName FROM pm1.g1 " + //$NON-NLS-1$
                            "UNION ALL SELECT 'Group', e1 FROM pm1.g2) AS entities " +  //$NON-NLS-1$
                            "WHERE ucase(entityName) = ucase(entity)",  //$NON-NLS-1$
                           externalMetadata);
    }
    
    public void testOptimizeOrderBy() throws Exception {
        helpTestOptimize("SELECT pm1.g1.e1 FROM pm1.g1 order by pm1.g1.e1",  //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "SELECT e1 FROM pm1.g1 ORDER BY e1"); //$NON-NLS-1$
    }
    
    /**
     * It is by design that order by optimization only works in one direction.  It is not desirable to
     * fully qualify order by elements 
     */
    public void testOptimizeOrderBy1() throws Exception {
        helpTestFullyQualify("SELECT e1 FROM pm1.g1 order by e1",  //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "SELECT pm1.g1.e1 FROM pm1.g1 ORDER BY e1"); //$NON-NLS-1$
    }
    
    public void testOptimizeOrderByWithoutGroup() throws Exception {
        helpTestOptimize("SELECT pm1.g1.e1, count(*) as x FROM pm1.g1 order by x",  //$NON-NLS-1$
                            FakeMetadataFactory.example1Cached(), 
                            "SELECT e1, COUNT(*) AS x FROM pm1.g1 ORDER BY x"); //$NON-NLS-1$
    }

}
