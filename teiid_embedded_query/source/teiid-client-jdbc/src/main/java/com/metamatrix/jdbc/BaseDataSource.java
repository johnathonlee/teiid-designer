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

package com.metamatrix.jdbc;

import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.ResourceBundle;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;
import javax.sql.XAConnection;
import javax.sql.XADataSource;

import com.metamatrix.common.api.MMURL;
import com.metamatrix.dqp.message.RequestMessage;
import com.metamatrix.jdbc.api.ExecutionProperties;
import com.metamatrix.jdbc.util.MMJDBCURL;

/**
 * The MetaMatrix JDBC DataSource implementation class of {@link javax.sql.DataSource} and
 * {@link javax.sql.XADataSource}.
 * <p>
 * The {@link javax.sql.DataSource} interface follows the JavaBean design pattern,
 * meaning the implementation class has <i>properties</i> that are accessed with getter methods
 * and set using setter methods, and where the getter and setter methods follow the JavaBean
 * naming convention (e.g., <code>get</code><i>PropertyName</i><code>() : </code><i>PropertyType</i>
 * and <code>set</code><i>PropertyName</i><code>(</code><i>PropertyType</i><code>) : void</code>).
 * </p>
 * The {@link javax.sql.XADataSource} interface is almost identical to the {@link javax.sql.DataSource}
 * interface, but rather than returning {@link java.sql.Connection} instances, there are methods that
 * return {@link javax.sql.XAConnection} instances that can be used with distributed transactions.
 * <p>
 * The following are the properties for this DataSource:
 * <table cellspacing="0" cellpadding="0" border="1" width="100%">
 *   <tr><td><b>Property Name</b></td><td><b>Type</b></td><td><b>Description</b></td></tr>
 *   <tr><td>applicationName  </td><td><code>String</code></td><td>The <i>optional</i> name of the application using the DataSource.</td></tr>
 *   <tr><td>clientToken      </td><td><code>Serializable</code></td><td>The <i>optional</i> client token that will be passed directly
 *                                                                 through to the connectors, which may use it and/or pass it
 *                                                                 down to their underlying data source.
 *                                                                 <p>
 *                                                                 The form and type of the client token is up to the client but it <i>must</i> implement the
 *                                                                 <code>Serializable</code> interface.  MetaMatrix does nothing with this token except to make it
 *                                                                 available for authentication/augmentation/replacement upon authentication to the system and to
 *                                                                 connectors that may require it at the data source level.
 *                                                                 </p></td></tr>
 *   <tr><td>databaseName     </td><td><code>String</code></td><td>The name of a particular virtual database on a
 *                                                                 MetaMatrix Server.</td></tr>
 *   <tr><td>databaseVersion  </td><td><code>String</code></td><td>The <i>optional</i> version of a particular
 *                                                                 virtual database on a MetaMatrix Server;
 *                                                                 if not supplied, then the latest version is assumed.</td></tr>
 *   <tr><td>dataSourceName   </td><td><code>String</code></td><td>The <i>optional</i> logical name for the underlying
 *                                                                 <code>XADataSource</code> or <code>ConnectionPoolDataSource</code>;
 *                                                                 used only when pooling connections or distributed transactions
 *                                                                 are implemented.</td></tr>
 *   <tr><td>description      </td><td><code>String</code></td><td>The <i>optional</i> description of this data source.</td></tr>
 *   <tr><td>logFile          </td><td><code>String</code></td><td>The <i>optional</i> path and file name to which JDBC Log Statements
 *                                                                 will be written; if none is specified, then no
 *                                                                 Log Statements will be written.</td></tr>
 *   <tr><td>logLevel         </td><td><code>int   </code></td><td>The <i>optional</i> level for logging, which only applies
 *                                                                 if the <code>logFile</code> property is set.  Value must be
 *                                                                 one of the following:
 *                                                                 <ul>
 *                                                                    <li>"<code>0</code>" - no JDBC log messages will be written to the file;
 *                                                                         this is the default</li>
 *                                                                    <li>"<code>1</code>" - all JDBC log messages will be written to the file</li>
 *                                                                    <li>"<code>2</code>" - all JDBC log messages as well as stack traces
 *                                                                         of any exceptions thrown from this driver will be written
 *                                                                         to the file</li>
 *                                                                 </ul>
 *   <tr><td>password</td><td><code>String</code></td><td>The user's password.</td></tr>
 *   <tr><td>user</td><td><code>String</code></td><td>The user name to use for the connection.</td></tr>
 *   <tr><td>partialResultsMode</td><td><code>boolean</code></td><td>Support partial results mode or not. </td></tr>
 *   <tr><td>fetchSize</td><td><code>int</code></td><td>Set default fetch size for statements, default=500.</td></tr>
 *   <tr><td>sqlOptions</td><td><code>String</code></td><td>Set sql options to use on every command. default=null</td></tr>
 * <table>
 * </p>
 */
public abstract class BaseDataSource extends WrapperImpl implements javax.sql.DataSource, XADataSource, ConnectionPoolDataSource, java.io.Serializable {
	public static final String DEFAULT_APP_NAME = "JDBC"; //$NON-NLS-1$
	
    // constant indicating Virtual database name
    public static final String VDB_NAME = MMURL.JDBC.VDB_NAME; 
    // constant indicating Virtual database version
    public static final String VDB_VERSION = MMURL.JDBC.VDB_VERSION; 
    // constant for vdb version part of serverURL
    public static final String VERSION = MMURL.JDBC.VERSION; 
    // name of the application which is obtaining connection
    public static final String APP_NAME = MMURL.CONNECTION.APP_NAME; 
    // constant for username part of url
    public static final String USER_NAME = MMURL.CONNECTION.USER_NAME; 
    // constant for password part of url
    public static final String PASSWORD = MMURL.CONNECTION.PASSWORD; 
    
    protected static final int DEFAULT_TIMEOUT = 0;
    protected static final int DEFAULT_LOG_LEVEL = 0;

    /**
     * The name of the virtual database on a particular MetaMatrix Server.
     * This property name is one of the standard property names defined by the JDBC 2.0 specification,
     * and is <i>required</i>.
     */
    private String databaseName;

    /**
     * The logical name for the underlying <code>XADataSource</code> or
     * <code>ConnectionPoolDataSource</code>;
     * used only when pooling connections or distributed transactions are implemented.
     * This property name is one of the standard property names defined by the JDBC 2.0 specification,
     * and is <i>optional</i>.
     */
    private String dataSourceName;

    /**
     * The description of this data source.
     * This property name is one of the standard property names defined by the JDBC 2.0 specification,
     * and is <i>optional</i>.
     */
    private String description;

    /**
     * The <code>Serializable</code> client token that will be passed directly
     * through to the connectors, which may use it and/or pass it down to their underlying data source.
     * This property is <i>optional</i>.
     * <p>
     * The form and type of the client token is up to the client but it <i>must</i> implement the
     * <code>Serializabe</code> interface.  MetaMatrix does nothing with this token except to make it
     * available for authentication/augmentation/replacement upon authentication to the system and to
     * connectors that may require it at the data source level.
     * </p>
     */
    private Serializable clientToken;

    /**
     * The user's name.
     * This property name is one of the standard property names defined by the JDBC 2.0 specification,
     * and is <i>required</i>.
     */
    private String user;

    /**
     * The user's password.
     * This property name is one of the standard property names defined by the JDBC 2.0 specification,
     * and is <i>required</i>.
     */
    private String password;

    /**
     * The version number of the virtual database to which a connection is to be established.
     * This property is <i>optional</i>; if not specified, the assumption is that the latest version
     * on the MetaMatrix Server is to be used.
     */
    private String databaseVersion;

    /**
     * The name of the application.  Supplying this property may allow an administrator of a
     * MetaMatrix Server to better identify individual connections and usage patterns.
     * This property is <i>optional</i>.
     */
    private String applicationName;

    /** Support partial results mode or not.*/
    private String partialResultsMode;
    
    /** Default fetch size, <= 0 indicates not set. */
    private int fetchSize = BaseDataSource.DEFAULT_FETCH_SIZE;

    /** Whether to use result set cache if it available **/
    private String resultSetCacheMode;
    
    /**
     * The number of milliseconds before timing out.
     * This property is <i>optional</i> and defaults to "0" (meaning no time out).
     */
    private int loginTimeout;
    
    private String sqlOptions;
    
    private String disableLocalTxn;

    /**
     * A setting that controls how connections created by this DataSource manage transactions for client
     * requests when client applications do not use transactions.  Because a MetaMatrix virtual database
     * will likely deal with multiple underlying information sources, the MetaMatrix XA Server will execute
     * all client requests within the contexts of transactions.  This method determines the semantics
     * of creating such transactions when the client does not explicitly do so.
     * <p>
     * The allowable values for this property are:
     * <ul>
     *   <li>"<code>OFF</code>" - Nothing is ever wrapped in a transaction and the server will execute
     * multi-source updates happily but outside a transaction.  This is least safe but highest performance.
     * The {@link #TXN_AUTO_WRAP_OFF} constant value is provided for convenience.</li>
     *   <li>"<code>ON</code>" - Always wrap every command in a transaction.  This is most safe but lowest
     * performance.
     * The {@link #TXN_AUTO_WRAP_ON} constant value is provided for convenience.</li>
     *   <li>"<code>PESSIMISTIC</code>" - Assume that any command might require a transaction.  Make a server
     * call to check whether the command being executed needs a transaction and wrap the command in a
     * transaction if necessary.  This will auto wrap in exactly the cases where it is needed but requires
     * an extra server call on every command execution (including queries).  This is as safe as ON, but
     * lower performance than OFF for cases where no transaction is actually needed (like queries).
     * This is the default value.
     * The {@link #TXN_AUTO_WRAP_PESSIMISTIC} constant value is provided for convenience.</li>
     *   <li>"<code>OPTIMISTIC</code>" - same as OFF but assume that no command not in a transaction actually
     * needs one.  In other words, we're letting the user decide when to use and not use a transaction and
     * assuming they are doing it correctly.  Only difference from OFF is that if the user executes a command
     * that requires a transaction but they don't use one, we will detect this and throw an exception.  This
     * provides the safety of ON or PESSIMISTIC mode but better performance in the common case of queries
     * that are not multi-source.
     * The {@link #TXN_AUTO_WRAP_OPTIMISTIC} constant value is provided for convenience.</li>
     * </ul>
     * </p>
     * <p>
     * This property is important only if connecting to a MetaMatrix XA Server.
     * </p>
     */
    private String transactionAutoWrap;
    
    /**
     * Reference to the logWriter, which is transient and is therefore not serialized with the DataSource.
     */
    private transient PrintWriter logWriter;
    public static final String METAMATRIX_PROTOCOL = "metamatrix"; //$NON-NLS-1$
    public static final String JDBC = "jdbc:"; //$NON-NLS-1$
    // Default execution property constants
    protected static final int DEFAULT_FETCH_SIZE = RequestMessage.DEFAULT_FETCH_SIZE;
    protected static final String DEFAULT_PARTIAL_RESULTS_MODE = "FALSE"; //$NON-NLS-1$
    protected static final String DEFAULT_RESULT_SET_CACHE_MODE = "TRUE"; //$NON-NLS-1$
    
    /**
     * Transaction auto wrap constant - never wrap a command execution in a transaction
     * and allow multi-source updates to occur outside of a transaction.
     */
    public static final String TXN_AUTO_WRAP_OFF = "OFF"; //$NON-NLS-1$

    /**
     * Transaction auto wrap constant - always wrap every non-transactional command
     * execution in a transaction.
     */
    public static final String TXN_AUTO_WRAP_ON = "ON"; //$NON-NLS-1$

    /**
     * Transaction auto wrap constant - pessimistic mode assumes that any command
     * execution might require a transaction to be wrapped around it.  To determine
     * this an extra server call is made to check whether the command requires
     * a transaction and a transaction will be automatically started.  This is most
     * accurate and safe, but has a performance impact.
     */
    public static final String TXN_AUTO_WRAP_PESSIMISTIC = "PESSIMISTIC"; //$NON-NLS-1$

    /**
     * Transaction auto wrap constant - optimistic mode assumes that non-transactional
     * commands typically do not require a transaction due to a multi-source update,
     * so no transaction is created.  In this respect, this mode is identical to
     * {@link #TXN_AUTO_WRAP_OFF}.  However, these modes differ because if we
     * discover during server execution that multiple sources will be updated by
     * a particular command, an exception is thrown to indicate that the command
     * cannot be executed.  This is the default mode.
     */
    public static final String TXN_AUTO_WRAP_OPTIMISTIC = "OPTIMISTIC"; //$NON-NLS-1$

    /**
     * String to hold additional properties that are not represented with an explicit getter/setter
     */
    private String additionalProperties;
    
    /**
     * Constructor for MMDataSource.
     */
    public BaseDataSource() {
        this.loginTimeout = DEFAULT_TIMEOUT;
    }

    // --------------------------------------------------------------------------------------------
    //                             H E L P E R   M E T H O D S
    // --------------------------------------------------------------------------------------------

    protected Properties buildProperties(final String userName, final String password) {
        Properties props = new Properties();
        props.setProperty(BaseDataSource.VDB_NAME,this.getDatabaseName());

        if ( this.getDatabaseVersion() != null && this.getDatabaseVersion().trim().length() != 0  ) {
            props.setProperty(BaseDataSource.VDB_VERSION,this.getDatabaseVersion());
        }

        if ( userName != null && userName.trim().length() != 0) {
            props.setProperty(BaseDataSource.USER_NAME, userName);
        } else if ( this.getUser() != null && this.getUser().trim().length() != 0) {
            props.setProperty(BaseDataSource.USER_NAME, this.getUser());
        }

        if ( password != null && password.trim().length() != 0) {
            props.setProperty(BaseDataSource.PASSWORD, password);
        } else if ( this.getPassword() != null && this.getPassword().trim().length() != 0) {
            props.setProperty(BaseDataSource.PASSWORD, this.getPassword());
        }

        if ( this.getApplicationName() != null && this.getApplicationName().trim().length() != 0 ) {
            props.setProperty(BaseDataSource.APP_NAME,this.getApplicationName());
        }
        
        Serializable token = this.getClientToken();
        if ( token != null ) {
            // Special case: token is a Serializable, not necessarily a String
            props.put(MMURL.CONNECTION.CLIENT_TOKEN_PROP, token);
        }

        if (this.getPartialResultsMode() != null && this.getPartialResultsMode().trim().length() != 0) {
            props.setProperty(ExecutionProperties.PROP_PARTIAL_RESULTS_MODE, this.getPartialResultsMode());
        }
        
        if(this.getFetchSize() > 0) {
            props.setProperty(ExecutionProperties.PROP_FETCH_SIZE, "" + this.getFetchSize()); //$NON-NLS-1$
        }

        if (this.getResultSetCacheMode() != null && this.getResultSetCacheMode().trim().length() != 0) {
            props.setProperty(ExecutionProperties.RESULT_SET_CACHE_MODE, this.getResultSetCacheMode());
        }
        
        if (this.getSqlOptions() != null) {
            props.setProperty(ExecutionProperties.PROP_SQL_OPTIONS, this.getSqlOptions());
        }
        
        if ( this.getTransactionAutoWrap() != null && this.getTransactionAutoWrap().trim().length() != 0   ) {
            props.setProperty(ExecutionProperties.PROP_TXN_AUTO_WRAP, this.getTransactionAutoWrap());
        }
        
        if (this.getDisableLocalTxn() != null) {
            props.setProperty(ExecutionProperties.DISABLE_LOCAL_TRANSACTIONS, this.getDisableLocalTxn());
        }
        
        if (this.additionalProperties != null) {
        	MMJDBCURL.parseConnectionProperties(this.additionalProperties, props);
        }
                      
        return props;
    }

    protected void validateProperties( final String userName, final String password) throws java.sql.SQLException {
        String reason = reasonWhyInvalidApplicationName(this.applicationName);
        if ( reason != null ) {
            throw new SQLException(reason);
        }

        reason = reasonWhyInvalidClientToken(this.clientToken);
        if ( reason != null ) {
            throw new SQLException(reason);
        }

        reason = reasonWhyInvalidDatabaseName(this.databaseName);
        if ( reason != null ) {
            throw new SQLException(reason);
        }

        reason = reasonWhyInvalidDatabaseVersion(this.databaseVersion);
        if ( reason != null ) {
            throw new SQLException(reason);
        }

        reason = reasonWhyInvalidDataSourceName(this.dataSourceName);
        if ( reason != null ) {
            throw new SQLException(reason);
        }

        reason = reasonWhyInvalidDescription(this.description);
        if ( reason != null ) {
            throw new SQLException(reason);
        }

        final String pwd = password != null ? password : getPassword();
        reason = reasonWhyInvalidPassword(pwd);
        if ( reason != null ) {
            throw new SQLException(reason);
        }

        reason = reasonWhyInvalidPartialResultsMode(this.partialResultsMode);
        if (reason != null) {
            throw new SQLException(reason);
        }

        reason = reasonWhyInvalidFetchSize(this.fetchSize);
        if (reason != null) {
            throw new SQLException(reason);
        }

        final String user = userName != null ? userName : getUser();
        reason = reasonWhyInvalidUser(user);
        if ( reason != null ) {
            throw new SQLException(reason);
        }

        reason = reasonWhyInvalidTransactionAutoWrap(this.transactionAutoWrap);
        if ( reason != null ) {
            throw new SQLException(reason);
        }
                
        
    }

    // --------------------------------------------------------------------------------------------
    //                        D A T A S O U R C E   M E T H O D S
    // --------------------------------------------------------------------------------------------

    /**
     * Attempt to establish a database connection.
     * @return a Connection to the database
     * @throws java.sql.SQLException if a database-access error occurs
     * @see javax.sql.DataSource#getConnection()
     */
    public Connection getConnection() throws java.sql.SQLException {
        return getConnection(null,null);
    }
    
    /** 
     * @see javax.sql.XADataSource#getXAConnection()
     */
    public XAConnection getXAConnection() throws SQLException {
        return getXAConnection(null,null);
    }
    
    /**
     * Attempt to establish a database connection that can be used with distributed transactions.
     * @param userName the database user on whose behalf the XAConnection is being made
     * @param password the user's password
     * @return an XAConnection to the database
     * @throws java.sql.SQLException if a database-access error occurs
     * @see javax.sql.XADataSource#getXAConnection(java.lang.String, java.lang.String)
     */
    public XAConnection getXAConnection(final String userName, final String password) throws java.sql.SQLException {
    	return MMXAConnection.newInstance(new MMXAConnection.ConnectionSource() {

            public MMConnection createConnection() throws SQLException {
                return (MMConnection)getConnection(userName, password);
            }});
    }
    
    public PooledConnection getPooledConnection() throws SQLException {
		return getPooledConnection(null, null);
	}

	public PooledConnection getPooledConnection(final String userName, final String password)
			throws SQLException {
		return getXAConnection(userName, password);
	}
	
    // --------------------------------------------------------------------------------------------
    //                        P R O P E R T Y   M E T H O D S
    // --------------------------------------------------------------------------------------------

	public String getDisableLocalTxn() {
		return disableLocalTxn;
	}

	public void setDisableLocalTxn(String disableLocalTxn) {
		this.disableLocalTxn = disableLocalTxn;
	}

    /**
     * Get the log writer for this data source.
     * <p>
     * The log writer is a character output stream to which all logging and tracing
     * messages for this data source object instance will be printed. This includes
     * messages printed by the methods of this object, messages printed by methods
     * of other objects manufactured by this object, and so on. Messages printed
     * to a data source specific log writer are not printed to the log writer
     * associated with the {@link java.sql.DriverManager} class. When a DataSource object is
     * created the log writer is initially null, in other words, logging is disabled.
     * @return the log writer for this data source, null if disabled
     * @throws java.sql.SQLException if a database-access error occurs
     * @see javax.sql.DataSource#getLogWriter()
     */
    public PrintWriter getLogWriter() throws java.sql.SQLException{
        return this.logWriter;
    }

    /**
     * Gets the maximum time in seconds that this data source can wait while attempting
     * to connect to a database. A value of zero means that the timeout is the default
     * system timeout if there is one; otherwise it means that there is no timeout.
     * When a DataSource object is created the login timeout is initially zero.
     * @return the data source login time limit
     * @throws java.sql.SQLException if a database-access error occurs
     * @see javax.sql.DataSource#getLoginTimeout()
     */
    public int getLoginTimeout() throws java.sql.SQLException {
        return this.loginTimeout;
    }

    /**
     * Set the log writer for this data source.
     * <p>
     * The log writer is a character output stream to which all logging and tracing
     * messages for this data source object instance will be printed. This includes
     * messages printed by the methods of this object, messages printed by methods
     * of other objects manufactured by this object, and so on. Messages printed
     * to a data source specific log writer are not printed to the log writer
     * associated with the {@link java.sql.DriverManager} class. When a DataSource object is
     * created the log writer is initially null, in other words, logging is disabled.
     * @param writer the log writer for this data source, null if disabled
     * @throws java.sql.SQLException if a database-access error occurs
     * @see javax.sql.DataSource#setLogWriter(java.io.PrintWriter)
     */
    public void setLogWriter( final PrintWriter writer) throws java.sql.SQLException{
        this.logWriter = writer;
    }

    /**
     * Sets the maximum time in seconds that this data source can wait while attempting
     * to connect to a database. A value of zero means that the timeout is the default
     * system timeout if there is one; otherwise it means that there is no timeout.
     * When a DataSource object is created the login timeout is initially zero.
     * @param timeOut the data source login time limit
     * @throws java.sql.SQLException if a database-access error occurs
     * @see javax.sql.DataSource#setLoginTimeout(int)
     */
    public void setLoginTimeout( final int timeOut) throws java.sql.SQLException {
        this.loginTimeout = timeOut;
    }

    /**
     * Returns the name of the application.  Supplying this property may allow an administrator of a
     * MetaMatrix Server to better identify individual connections and usage patterns.
     * This property is <i>optional</i>.
     * @return String the application name; may be null or zero-length
     */
    public String getApplicationName() {
        return applicationName!=null?applicationName:DEFAULT_APP_NAME;
    }

    /**
     * Returns the name of the virtual database on a particular MetaMatrix Server.
     * @return String
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * Returns the databaseVersion.
     * @return String
     */
    public String getDatabaseVersion() {
        return databaseVersion;
    }

    /**
     * Returns the logical name for the underlying <code>XADataSource</code> or
     * <code>ConnectionPoolDataSource</code>;
     * used only when pooling connections or distributed transactions are implemented.
     * @return the logical name for the underlying data source; may be null
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * Returns the description of this data source.
     * @return the description; may be null
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the user.
     * @return the name of the user for this data source
     */
    public String getUser() {
        return user;
    }

    /**
     * Returns the password.
     * @return the password for this data source.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Get the <code>Serializable</code> client token that will be passed directly
     * through to the connectors, which may use it and/or pass it down to their underlying data source.
     * This property is <i>optional</i>.
     * <p>
     * The form and type of the client token is up to the client but it <i>must</i> implement the
     * <code>Serializabe</code> interface.  MetaMatrix does nothing with this token except to make it
     * available for authentication/augmentation/replacement upon authentication to the system and to
     * connectors that may require it at the data source level.
     * </p>
     * @return The client token that was supplied by the client at system connection time; may be <code>null</code>.
     */
    public Serializable getClientToken() {
        return clientToken;
    }

    /**
     * Sets the name of the application.  Supplying this property may allow an administrator of a
     * MetaMatrix Server to better identify individual connections and usage patterns.
     * This property is <i>optional</i>.
     * @param applicationName The applicationName to set
     */
    public void setApplicationName(final String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * Sets the name of the virtual database on a particular MetaMatrix Server.
     * @param databaseName The name of the virtual database
     */
    public void setDatabaseName(final String databaseName) {
        this.databaseName = databaseName;
    }

    /**
     * Sets the databaseVersion.
     * @param databaseVersion The version of the virtual database
     */
    public void setDatabaseVersion(final String databaseVersion) {
        this.databaseVersion = databaseVersion;
    }

    /**
     * Sets the logical name for the underlying <code>XADataSource</code> or
     * <code>ConnectionPoolDataSource</code>;
     * used only when pooling connections or distributed transactions are implemented.
     * @param dataSourceName The dataSourceName for this data source; may be null
     */
    public void setDataSourceName(final String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    /**
     * Sets the user.
     * @param user The user to set
     */
    public void setUser(final String user) {
        this.user = user;
    }

    /**
     * Sets the password.
     * @param password The password for this data source
     */
    public void setPassword(final String password) {
        this.password = password;
    }

    /**
     * Set the <code>Serializable</code> client token that will be passed directly
     * through to the connectors, which may use it and/or pass it down to their underlying data source.
     * This property is <i>optional</i>.
     * <p>
     * The form and type of the client token is up to the client but it <i>must</i> implement the
     * <code>Serializabe</code> interface.  MetaMatrix does nothing with this token except to make it
     * available for authentication/augmentation/replacement upon authentication to the system and to
     * connectors that may require it at the data source level.
     * </p>
     * @param clientToken The client token that will be passed with this user's requests.
     */
    public void setClientToken(Serializable clientToken) {
        this.clientToken = clientToken;
    }

    /**
     * Sets the description of this data source.
     * @param description The description for this data source; may be null
     */
    public void setDescription(final String description) {
        this.description = description;
    }

    public void setPartialResultsMode(String partialResultsMode) {
        this.partialResultsMode = partialResultsMode;
    }

    public String getPartialResultsMode() {
        return this.partialResultsMode;
    }

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    public int getFetchSize() {
        return this.fetchSize;
    }
    
    public void setResultSetCacheMode(String resultSetCacheMode) {
        this.resultSetCacheMode = resultSetCacheMode;
    }

    public String getResultSetCacheMode() {
        return this.resultSetCacheMode;
    }
    
    /**
     * Get special sqlOptions string, which can currently be set only to SHOWPLAN
     * @return Returns sqlOptions string or null if none
     * @since 4.3
     */
    public String getSqlOptions() {
        return this.sqlOptions;
    }
    
    /** 
     * Sets special sqlOptions that should be used with each command.
     * @param sqlOptions SQL options, only "SHOWPLAN" is currently accepted
     * @since 4.3
     */
    public void setSqlOptions(String sqlOptions) {
        this.sqlOptions = sqlOptions;
    }

    /**
     * Returns the current setting for how connections are created by this DataSource manage transactions
     * for client requests when client applications do not use transactions.
     * Because a MetaMatrix virtual database will likely deal with multiple underlying information sources,
     * the MetaMatrix XA Server will execute all client requests within the contexts of transactions.
     * This method determines the semantics of creating such transactions when the client does not
     * explicitly do so.
     * @return the current setting, or null if the property has not been set and the default mode will
     * be used.
     */
    public String getTransactionAutoWrap() {
        return transactionAutoWrap;
    }

    /**
     * Sets the setting for how connections are created by this DataSource manage transactions
     * for client requests when client applications do not use transactions.
     * Because a MetaMatrix virtual database will likely deal with multiple underlying information sources,
     * the MetaMatrix XA Server will execute all client requests within the contexts of transactions.
     * This method determines the semantics of creating such transactions when the client does not
     * explicitly do so.
     * <p>
     * The allowable values for this property are:
     * <ul>
     *   <li>"<code>OFF</code>" - Nothing is ever wrapped in a transaction and the server will execute
     * multi-source updates happily but outside a transaction.  This is least safe but highest performance.
     * The {@link #TXN_AUTO_WRAP_OFF} constant value is provided for convenience.</li>
     *   <li>"<code>ON</code>" - Always wrap every command in a transaction.  This is most safe but lowest
     * performance.
     * The {@link #TXN_AUTO_WRAP_ON} constant value is provided for convenience.</li>
     *   <li>"<code>PESSIMISTIC</code>" - Assume that any command might require a transaction.  Make a server
     * call to check whether the command being executed needs a transaction and wrap the command in a
     * transaction if necessary.  This will auto wrap in exactly the cases where it is needed but requires
     * an extra server call on every command execution (including queries).  This is as safe as ON, but
     * lower performance than OFF for cases where no transaction is actually needed (like queries).
     * The {@link #TXN_AUTO_WRAP_PESSIMISTIC} constant value is provided for convenience.</li>
     *   <li>"<code>OPTIMISTIC</code>" - same as OFF but assume that no command not in a transaction actually
     * needs one.  In other words, we're letting the user decide when to use and not use a transaction and
     * assuming they are doing it correctly.  Only difference from OFF is that if the user executes a command
     * that requires a transaction but they don't use one, we will detect this and throw an exception.  This
     * provides the safety of ON or PESSIMISTIC mode but better performance in the common case of queries
     * that are not multi-source.
     * The {@link #TXN_AUTO_WRAP_OPTIMISTIC} constant value is provided for convenience.</li>
     * </ul>
     * </p>
     * <p>
     * This property is important only if connecting to a MetaMatrix XA Server.
     * </p>
     * @param transactionAutoWrap The transactionAutoWrap to set
     */
    public void setTransactionAutoWrap(String transactionAutoWrap) {
        this.transactionAutoWrap = transactionAutoWrap;
    }

    // --------------------------------------------------------------------------------------------
    //                  V A L I D A T I O N   M E T H O D S
    // --------------------------------------------------------------------------------------------

    /**
     * Return the reason why the supplied application name may be invalid, or null
     * if it is considered valid.
     * @param applicationName a possible value for the property
     * @return the reason why the property is invalid, or null if it is considered valid
     * @see #setApplicationName(String)
     */
    public static String reasonWhyInvalidApplicationName( final String applicationName ) {
        return null;        // anything is valid
    }

    /**
     * Return the reason why the supplied client token may be invalid, or null
     * if it is considered valid.
     * @param clientToken a possible value for the property
     * @return the reason why the property is invalid, or null if it is considered valid
     * @see #setClientToken(Serializable)
     */
    public static String reasonWhyInvalidClientToken(final Serializable clientToken) {
        return null;        // it is optional
    }

    /**
     * Return the reason why the supplied virtual database name may be invalid, or null
     * if it is considered valid.
     * @param databaseName a possible value for the property
     * @return the reason why the property is invalid, or null if it is considered valid
     * @see #setDatabaseName(String)
     */
    public static String reasonWhyInvalidDatabaseName( final String databaseName ) {
        if ( databaseName == null || databaseName.trim().length() == 0 ) {                                  
            return getResourceMessage("MMDataSource.Virtual_database_name_must_be_specified"); //$NON-NLS-1$
        }
        return null;
    }

    /**
     * Return the reason why the supplied user name may be invalid, or null
     * if it is considered valid.
     * @param userName a possible value for the property
     * @return the reason why the property is invalid, or null if it is considered valid
     * @see #setUser(String)
     */
    public static String reasonWhyInvalidUser( final String userName ) {
        return null;
    }

    /**
     * Return the reason why the supplied transaction auto wrap value may be invalid, or null
     * if it is considered valid.
     * <p>
     * This method checks to see that the value is one of the allowable values.
     * </p>
     * @param autoWrap a possible value for the auto wrap property.
     * @return the reason why the property is invalid, or null if it is considered valid
     * @see #setTransactionAutoWrap(String)
     */
    public static String reasonWhyInvalidTransactionAutoWrap( final String autoWrap ) {
        if ( autoWrap == null || autoWrap.trim().length() == 0 ) {
            return null;    // no longer require an app server name, 'cause will look on classpath
        }
        final String trimmedAutoWrap = autoWrap.trim();
        if( TXN_AUTO_WRAP_ON.equals(trimmedAutoWrap) ) {
            return null;
        }
        if( TXN_AUTO_WRAP_OFF.equals(trimmedAutoWrap) ) {
            return null;
        }
        if( TXN_AUTO_WRAP_OPTIMISTIC.equals(trimmedAutoWrap) ) {
            return null;
        }
        if( TXN_AUTO_WRAP_PESSIMISTIC.equals(trimmedAutoWrap) ) {
            return null;
        }

        Object[] params = new Object[] {
            TXN_AUTO_WRAP_ON, TXN_AUTO_WRAP_OFF, TXN_AUTO_WRAP_OPTIMISTIC, TXN_AUTO_WRAP_PESSIMISTIC };
        return JDBCPlugin.Util.getString("MMDataSource.Invalid_trans_auto_wrap_mode", params); //$NON-NLS-1$
    }
         
    /**
     * Return the reason why the supplied virtual database version may be invalid, or null
     * if it is considered valid.
     * @param databaseVersion a possible value for the property
     * @return the reason why the property is invalid, or null if it is considered valid
     * @see #setDatabaseVersion(String)
     */
    public static String reasonWhyInvalidDatabaseVersion( final String databaseVersion ) {
        return null;        // anything is valid (let server validate)
    }

    /**
     * Return the reason why the supplied data source name may be invalid, or null
     * if it is considered valid.
     * @param dataSourceName a possible value for the property
     * @return the reason why the property is invalid, or null if it is considered valid
     * @see #setDataSourceName(String)
     */
    public static String reasonWhyInvalidDataSourceName( final String dataSourceName) {
        return null;        // anything is valid
    }

    /**
     * Return the reason why the supplied password may be invalid, or null
     * if it is considered valid.
     * @param pwd a possible value for the property
     * @return the reason why the property is invalid, or null if it is considered valid
     * @see #setPassword(String)
     */
    public static String reasonWhyInvalidPassword( final String pwd ) {
        return null;
    }

    /**
     * Return the reason why the supplied description may be invalid, or null
     * if it is considered valid.
     * @param description a possible value for the property
     * @return the reason why the property is invalid, or null if it is considered valid
     * @see #setDescription(String)
     */
    public static String reasonWhyInvalidDescription( final String description ) {
        return null;        // anything is valid
    }

    /**
     * The reason why partialResultsMode is invalid.
     * @param partialMode boolean flag
     * @return String reason
     */
    public static String reasonWhyInvalidPartialResultsMode( final String partialMode) {
        if ( partialMode != null ) {
            if (partialMode.equalsIgnoreCase("true") || partialMode.equalsIgnoreCase("false")) { //$NON-NLS-1$ //$NON-NLS-2$
                return null;
            }
            return getResourceMessage("MMDataSource.The_partial_mode_must_be_boolean._47"); //$NON-NLS-1$
        }
        return null;
    }

    /**
     * The reason why fetchSize is invalid.
     * @param fetchSize Number of rows per batch
     * @return the reason why the property is invalid, or null if it is considered valid
     */
    public static String reasonWhyInvalidFetchSize( final int fetchSize) {
        if ( fetchSize <= 0 ) {
            return getResourceMessage("MMDataSource.The_fetch_size_must_be_greater_than_zero"); //$NON-NLS-1$
        }
        return null;
    }
    
    
    
    private static final String BUNDLE_NAME = "com.metamatrix.jdbc.basic_i18n"; //$NON-NLS-1$
    

    static String getResourceMessage(String key, Object[] args) {
        ResourceBundle messages = ResourceBundle.getBundle(BUNDLE_NAME);          
        String messageTemplate = messages.getString(key);
        return MessageFormat.format(messageTemplate, args);
    }
    
   
    protected static String getResourceMessage(String key) {
        ResourceBundle messages = ResourceBundle.getBundle(BUNDLE_NAME);          
        String messageTemplate = messages.getString(key);
        return MessageFormat.format(messageTemplate, (Object[])null);
    }

	public void setAdditionalProperties(String additionalProperties) {
		this.additionalProperties = additionalProperties;
	}

	public String getAdditionalProperties() {
		return additionalProperties;
	}

}

