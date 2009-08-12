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

package com.metamatrix.jdbc.api;

import java.util.HashSet;
import java.util.Set;


/** 
 * Utility class containing 1) SQL state constants used to represent MetaMatrix JDBC error state code, and
 * 2) utility methods to check whether a SQL state belongs to a particular class of exception states.
 * @since 4.3
 */
public class SQLStates {
	// Class 80 - connection exception

	/**
	 * Identifies the SQLState class Connection Exception (08).
	 */
	public static final SQLStateClass CLASS_CONNECTION_EXCEPTION = new SQLStateClass(
			"08"); //$NON-NLS-1$

	/**
	 * Connection Exception with no subclass (SQL-99 08000)
	 */
	public static final String CONNECTION_EXCEPTION_NO_SUBCLASS = "08000"; //$NON-NLS-1$

	/**
	 * SQL-client unable to establish SQL-connection (SQL-99 08001)
	 */
	public static final String CONNECTION_EXCEPTION_SQLCLIENT_UNABLE_TO_ESTABLISH_SQLCONNECTION = "08001"; //$NON-NLS-1$

	/**
	 * Connection name in use (SQL-99 08002)
	 */
	public static final String CONNECTION_EXCEPTION_CONNECTION_NAME_IN_USE = "08002"; //$NON-NLS-1$

	/**
	 * Connection does not exist (SQL-99 08003)
	 */
	public static final String CONNECTION_EXCEPTION_CONNECTION_DOES_NOT_EXIST = "08003"; //$NON-NLS-1$

	/**
	 * SQL-server rejected establishment of SQL-connection (SQL-99 08004)
	 */
	public static final String CONNECTION_EXCEPTION_SQLSERVER_REJECTED_ESTABLISHMENT_OF_SQLCONNECTION = "08004"; //$NON-NLS-1$

	/**
	 * Connection failure (SQL-99 08006)
	 */
	public static final String CONNECTION_EXCEPTION_CONNECTION_FAILURE = "08006"; //$NON-NLS-1$

	/**
	 * Transaction resolution unknown (SQL-99 08007)
	 */
	public static final String CONNECTION_EXCEPTION_TRANSACTION_RESOLUTION_UNKNOWN = "08007"; //$NON-NLS-1$

	/**
	 * Connection is stale and should no longer be used. (08S01)
	 * <p>
	 * The SQLState subclass S01 is an implementation-specified condition and
	 * conforms to the subclass DataDirect uses for SocketExceptions.
	 */
	public static final String CONNECTION_EXCEPTION_STALE_CONNECTION = "08S01"; //$NON-NLS-1$

	// Class 28 - invalid authorization specification

	/**
	 * Identifies the SQLState class Invalid Authorization Specification (28).
	 */
	public static final SQLStateClass CLASS_INVALID_AUTHORIZATION_SPECIFICATION = new SQLStateClass(
			"28"); //$NON-NLS-1$

	/**
	 * Invalid authorization specification with no subclass (SQL-99 28000)
	 */
	public static final String INVALID_AUTHORIZATION_SPECIFICATION_NO_SUBCLASS = "28000"; //$NON-NLS-1$
	
	
	// Class 38 - External Routine Exception (as defined by SQL spec):
    /** External routine exception. This is the default unknown code */
    public static final String DEFAULT = "38000"; //$NON-NLS-1$
    
    public static final String SUCESS = "00000"; //$NON-NLS-1$

    // Class 50 - Query execution errors
    public static final SQLStateClass CLASS_USAGE_ERROR = new SQLStateClass("50"); //$NON-NLS-1$
    /** General query execution error*/
    public static final String USAGE_ERROR = "50000"; //$NON-NLS-1$
    /** Error raised by ERROR instruction in virtual procedure.*/
    public static final String VIRTUAL_PROCEDURE_ERROR = "50001"; //$NON-NLS-1$
    
    private static final SQLStateClass[] stateClasses = {CLASS_USAGE_ERROR};
    static {
        CLASS_USAGE_ERROR.stateCodes.add(USAGE_ERROR);
        CLASS_USAGE_ERROR.stateCodes.add(VIRTUAL_PROCEDURE_ERROR);
    }

    public static boolean isSystemErrorState(String sqlStateCode) {
        return !isUsageErrorState(sqlStateCode);
    }
    
    public static boolean isUsageErrorState(String sqlStateCode) {
        return belongsToClass(sqlStateCode, CLASS_USAGE_ERROR);
    }
    
    public static boolean belongsToClass(String sqlStateCode, SQLStateClass sqlStateClass) {
        return sqlStateCode.startsWith(sqlStateClass.codeBeginsWith);
    }
    
    public static SQLStateClass getClass(String sqlStateCode) {
        for (int i = 0; i < stateClasses.length; i++) {
            if (stateClasses[i].containsSQLState(sqlStateCode)) {
                return stateClasses[i];
            }
        }
        return null;
    }
    
    public static final class SQLStateClass {
        private String codeBeginsWith;
        private Set stateCodes = new HashSet();
        private SQLStateClass(String beginsWith) {
            this.codeBeginsWith = beginsWith;
        }
        
        public boolean containsSQLState(String sqlState) {
            return stateCodes.contains(sqlState);
        }
    }
}
