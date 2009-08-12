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
package com.metamatrix.connector.ldap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.naming.directory.Attribute;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.SortKey;

import org.teiid.connector.api.ConnectorException;
import org.teiid.connector.api.ConnectorLogger;
import org.teiid.connector.language.ICommand;
import org.teiid.connector.language.IQuery;
import org.teiid.connector.metadata.runtime.RuntimeMetadata;
import org.teiid.dqp.internal.datamgr.metadata.RuntimeMetadataImpl;

import junit.framework.TestCase;

import com.metamatrix.cdk.CommandBuilder;
import com.metamatrix.cdk.api.SysLogger;
import com.metamatrix.common.types.DataTypeManager;
import com.metamatrix.query.metadata.QueryMetadataInterface;
import com.metamatrix.query.unittest.FakeMetadataFacade;
import com.metamatrix.query.unittest.FakeMetadataFactory;
import com.metamatrix.query.unittest.FakeMetadataObject;
import com.metamatrix.query.unittest.FakeMetadataStore;

/** 
 * Test IQueryToLdapSearchParser.  
 */
/**
 * @author mdrilling
 *
 */
public class TestIQueryToLdapSearchParser extends TestCase {
    
    public TestIQueryToLdapSearchParser(String name) {
        super(name);
    }

	/**
     * Get Resolved Command using SQL String and metadata.
     */
    public ICommand getCommand(String sql, QueryMetadataInterface metadata) {
    	CommandBuilder builder = new CommandBuilder(metadata);
    	return builder.getCommand(sql);
    }
    
	/**
     * Helper method for testing the provided LDAPSearchDetails against expected values
     * @param searchDetails the LDAPSearchDetails object
     * @param expectedContextName the expected context name
     * @param expectedContextFilter the expected context filter string
     * @param expectedAttrNameList list of expected attribute names
     * @param expectedCountLimit the expected count limit
     * @param expectedSearchScope the expected search scope
     * @param expectedSortKeys the expected sortKeys list.
     */
    public void helpTestSearchDetails(final LDAPSearchDetails searchDetails, final String expectedContextName,
    		final String expectedContextFilter, final List expectedAttrNameList, final long expectedCountLimit, 
    		final int expectedSearchScope, final SortKey[] expectedSortKeys) {
    	
    	// Get all of the actual values
        String contextName = searchDetails.getContextName();
        String contextFilter = searchDetails.getContextFilter();
        List attrList = searchDetails.getAttributeList();
        long countLimit = searchDetails.getCountLimit();
    	int searchScope = searchDetails.getSearchScope();
    	SortKey[] sortKeys = searchDetails.getSortKeys();
    	
    	// Compare actual with Expected
    	assertEquals(expectedContextName, contextName);
    	assertEquals(expectedContextFilter, contextFilter);
    	
    	assertEquals(attrList.size(),expectedAttrNameList.size());
    	Iterator iter = attrList.iterator();
    	Iterator eIter = expectedAttrNameList.iterator();
    	while(iter.hasNext()&&eIter.hasNext()) {
			String actualName = ((Attribute)iter.next()).getID();
			String expectedName = (String)eIter.next();
			assertEquals(actualName, expectedName);
    	}

    	assertEquals(expectedCountLimit, countLimit);
    	assertEquals(expectedSearchScope, searchScope);
    	assertEquals(expectedSortKeys, sortKeys);
    }

	/**
     * Test a Query without criteria
     */
    public void testSelectFrom1() throws Exception {
        LDAPSearchDetails searchDetails = helpGetSearchDetails("SELECT UserID, Name FROM LdapModel.People"); //$NON-NLS-1$
        
        //-----------------------------------
        // Set Expected SearchDetails Values
        //-----------------------------------
        String expectedContextName = "ou=people,dc=metamatrix,dc=com"; //$NON-NLS-1$
        String expectedContextFilter = "(objectClass=*)"; //$NON-NLS-1$
        
        List expectedAttrNameList = new ArrayList();
        expectedAttrNameList.add("uid"); //$NON-NLS-1$
        expectedAttrNameList.add("cn"); //$NON-NLS-1$
        
        long expectedCountLimit = -1;
        int expectedSearchScope = SearchControls.ONELEVEL_SCOPE;
        SortKey[] expectedSortKeys = null;
        
        helpTestSearchDetails(searchDetails, expectedContextName, expectedContextFilter, expectedAttrNameList,
        		expectedCountLimit, expectedSearchScope, expectedSortKeys);
        
    }
    
	/**
     * Test a Query with a criteria
     */
    public void testSelectFromWhere1() throws Exception {
    	LDAPSearchDetails searchDetails = helpGetSearchDetails("SELECT UserID, Name FROM LdapModel.People WHERE Name = 'R%'"); //$NON-NLS-1$
        
        //-----------------------------------
        // Set Expected SearchDetails Values
        //-----------------------------------
        String expectedContextName = "ou=people,dc=metamatrix,dc=com"; //$NON-NLS-1$
        String expectedContextFilter = "(cn=R%)"; //$NON-NLS-1$
        
        List expectedAttrNameList = new ArrayList();
        expectedAttrNameList.add("uid"); //$NON-NLS-1$
        expectedAttrNameList.add("cn"); //$NON-NLS-1$
        
        long expectedCountLimit = -1;
        int expectedSearchScope = SearchControls.ONELEVEL_SCOPE;
        SortKey[] expectedSortKeys = null;
        
        helpTestSearchDetails(searchDetails, expectedContextName, expectedContextFilter, expectedAttrNameList,
        		expectedCountLimit, expectedSearchScope, expectedSortKeys);
        
    }
    
	/**
     * Test a Query with a criteria
     */
    public void testEscaping() throws Exception {
    	LDAPSearchDetails searchDetails = helpGetSearchDetails("SELECT UserID, Name FROM LdapModel.People WHERE Name = 'R*'"); //$NON-NLS-1$
        
        //-----------------------------------
        // Set Expected SearchDetails Values
        //-----------------------------------
        String expectedContextName = "ou=people,dc=metamatrix,dc=com"; //$NON-NLS-1$
        String expectedContextFilter = "(cn=R\\2a)"; //$NON-NLS-1$
        
        List expectedAttrNameList = new ArrayList();
        expectedAttrNameList.add("uid"); //$NON-NLS-1$
        expectedAttrNameList.add("cn"); //$NON-NLS-1$
        
        long expectedCountLimit = -1;
        int expectedSearchScope = SearchControls.ONELEVEL_SCOPE;
        SortKey[] expectedSortKeys = null;
        
        helpTestSearchDetails(searchDetails, expectedContextName, expectedContextFilter, expectedAttrNameList,
        		expectedCountLimit, expectedSearchScope, expectedSortKeys);
        
    }
    
    public void testNot() throws Exception {
    	LDAPSearchDetails searchDetails = helpGetSearchDetails("SELECT UserID, Name FROM LdapModel.People WHERE not (Name like 'R%' or Name like 'S%')"); //$NON-NLS-1$
        
        //-----------------------------------
        // Set Expected SearchDetails Values
        //-----------------------------------
        String expectedContextName = "ou=people,dc=metamatrix,dc=com"; //$NON-NLS-1$
        String expectedContextFilter = "(!(|(cn=R*)(cn=S*)))"; //$NON-NLS-1$
        
        List expectedAttrNameList = new ArrayList();
        expectedAttrNameList.add("uid"); //$NON-NLS-1$
        expectedAttrNameList.add("cn"); //$NON-NLS-1$
        
        long expectedCountLimit = -1;
        int expectedSearchScope = SearchControls.ONELEVEL_SCOPE;
        SortKey[] expectedSortKeys = null;
        
        helpTestSearchDetails(searchDetails, expectedContextName, expectedContextFilter, expectedAttrNameList,
        		expectedCountLimit, expectedSearchScope, expectedSortKeys);
        
    }
    
    public void testGT() throws Exception {
    	LDAPSearchDetails searchDetails = helpGetSearchDetails("SELECT UserID, Name FROM LdapModel.People WHERE Name > 'R'"); //$NON-NLS-1$
        
        //-----------------------------------
        // Set Expected SearchDetails Values
        //-----------------------------------
        String expectedContextName = "ou=people,dc=metamatrix,dc=com"; //$NON-NLS-1$
        String expectedContextFilter = "(!(cn<=R))"; //$NON-NLS-1$
        
        List expectedAttrNameList = new ArrayList();
        expectedAttrNameList.add("uid"); //$NON-NLS-1$
        expectedAttrNameList.add("cn"); //$NON-NLS-1$
        
        long expectedCountLimit = -1;
        int expectedSearchScope = SearchControls.ONELEVEL_SCOPE;
        SortKey[] expectedSortKeys = null;
        
        helpTestSearchDetails(searchDetails, expectedContextName, expectedContextFilter, expectedAttrNameList,
        		expectedCountLimit, expectedSearchScope, expectedSortKeys);
    }
    
    public void testLT() throws Exception {
    	LDAPSearchDetails searchDetails = helpGetSearchDetails("SELECT UserID, Name FROM LdapModel.People WHERE Name < 'R'"); //$NON-NLS-1$
        
        //-----------------------------------
        // Set Expected SearchDetails Values
        //-----------------------------------
        String expectedContextName = "ou=people,dc=metamatrix,dc=com"; //$NON-NLS-1$
        String expectedContextFilter = "(!(cn>=R))"; //$NON-NLS-1$
        
        List expectedAttrNameList = new ArrayList();
        expectedAttrNameList.add("uid"); //$NON-NLS-1$
        expectedAttrNameList.add("cn"); //$NON-NLS-1$
        
        long expectedCountLimit = -1;
        int expectedSearchScope = SearchControls.ONELEVEL_SCOPE;
        SortKey[] expectedSortKeys = null;
        
        helpTestSearchDetails(searchDetails, expectedContextName, expectedContextFilter, expectedAttrNameList,
        		expectedCountLimit, expectedSearchScope, expectedSortKeys);
    }

	private LDAPSearchDetails helpGetSearchDetails(String queryString) throws ConnectorException {
		ConnectorLogger logger = new SysLogger(false);
    	QueryMetadataInterface metadata = exampleLdap();
    	RuntimeMetadata rm = new RuntimeMetadataImpl(metadata);
    	Properties props = new Properties();
    	
    	IQueryToLdapSearchParser searchParser = new IQueryToLdapSearchParser(logger,rm,props);
    	
        IQuery query = (IQuery)getCommand(queryString, metadata);

        LDAPSearchDetails searchDetails = searchParser.translateSQLQueryToLDAPSearch(query);
		return searchDetails;
	}
    
    public static FakeMetadataFacade exampleLdap() { 
        // Create models
        FakeMetadataObject ldapModel = FakeMetadataFactory.createPhysicalModel("LdapModel"); //$NON-NLS-1$
        
        // Create physical groups
        FakeMetadataObject table = FakeMetadataFactory.createPhysicalGroup("LdapModel.People", ldapModel); //$NON-NLS-1$
        table.putProperty(FakeMetadataObject.Props.NAME_IN_SOURCE, "ou=people,dc=metamatrix,dc=com"); //$NON-NLS-1$
                
        // Create physical elements
        String[] elemNames = new String[] {
            "UserID", "Name"  //$NON-NLS-1$ //$NON-NLS-2$
        };
        String[] elemTypes = new String[] {  
            DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING
        };
        
        List cols = FakeMetadataFactory.createElements(table, elemNames, elemTypes);
        
        // Set name in source on each column
        String[] nameInSource = new String[] {
           "uid", "cn"             //$NON-NLS-1$ //$NON-NLS-2$  
        };
        for(int i=0; i<2; i++) {
            FakeMetadataObject obj = (FakeMetadataObject) cols.get(i);
            obj.putProperty(FakeMetadataObject.Props.NAME_IN_SOURCE, nameInSource[i]);
        }
        
        // Set column-specific properties
        ((FakeMetadataObject) cols.get(0)).putProperty(FakeMetadataObject.Props.SELECT, Boolean.FALSE);
        for(int i=1; i<2; i++) {
            ((FakeMetadataObject) cols.get(0)).putProperty(FakeMetadataObject.Props.SEARCHABLE_COMPARE, Boolean.FALSE);
            ((FakeMetadataObject) cols.get(0)).putProperty(FakeMetadataObject.Props.SEARCHABLE_LIKE, Boolean.FALSE);
        }
        
        // Add all objects to the store
        FakeMetadataStore store = new FakeMetadataStore();
        store.addObject(ldapModel);
        store.addObject(table);     
        store.addObjects(cols);
        
        // Create the facade from the store
        return new FakeMetadataFacade(store);
    }    
}

