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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.MetaMatrixProcessingException;
import com.metamatrix.common.types.MMJDBCSQLTypeInfo;
import com.metamatrix.common.util.SqlUtil;
import com.metamatrix.common.util.TimestampWithTimezone;
import com.metamatrix.core.util.ArgCheck;
import com.metamatrix.core.util.ObjectConverterUtil;
import com.metamatrix.dqp.client.MetadataResult;
import com.metamatrix.dqp.message.RequestMessage;
import com.metamatrix.jdbc.api.ExecutionProperties;

/**
 * <p> Instances of PreparedStatement contain a SQL statement that has already been
 * compiled.  The SQL statement contained in a PreparedStatement object may have
 * one or more IN parameters. An IN parameter is a parameter whose value is not
 * specified when a SQL statement is created. Instead the statement has a placeholder
 * for each IN parameter.</p>
 * <p> The MMPreparedStatement object wraps the server's PreparedStatement object.
 * The methods in this class are used to set the IN parameters on a server's
 * preparedstatement object.</p>
 */

public class MMPreparedStatement extends MMStatement implements PreparedStatement {
    // sql, which this prepared statement is operating on
    protected String prepareSql;

    //map that holds parameter index to values for prepared statements
    private Map<Integer, Object> parameterMap;
    
    //a list of map that holds parameter index to values for prepared statements
    protected List<List<Object>> batchParameterList;

    // metadata
    private ResultSetMetaData metadata;
    
    private Calendar serverCalendar;

    /**
     * Factory Constructor 
     * @param connection
     * @param sql
     * @param resultSetType
     * @param resultSetConcurrency
     */
    static MMPreparedStatement newInstance(MMConnection connection, String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return new MMPreparedStatement(connection, sql, resultSetType, resultSetConcurrency);        
    }
    
    /**
     * <p>MMPreparedStatement constructor.
     * @param Driver's connection object.
     * @param String object representing the prepared statement
     */
    MMPreparedStatement(MMConnection connection, String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        super(connection, resultSetType, resultSetConcurrency);

        // this sql is for callable statement, don't check any more
        ArgCheck.isNotNull(sql, JDBCPlugin.Util.getString("MMPreparedStatement.Err_prep_sql")); //$NON-NLS-1$
        this.prepareSql = sql;

        TimeZone timezone = connection.getServerConnection().getLogonResult().getTimeZone();

        if (timezone != null && !timezone.hasSameRules(getDefaultCalendar().getTimeZone())) {
        	this.serverCalendar = Calendar.getInstance(timezone);
        }        
    }

    /**
     * <p>Adds a set of parameters to this PreparedStatement object's list of commands
     * to be sent to the database for execution as a batch.
     * @throws SQLException if there is an error
     */
    public void addBatch() throws SQLException {
        checkStatement();
    	if(batchParameterList == null){
    		batchParameterList = new ArrayList<List<Object>>();
		}
    	batchParameterList.add(getParameterValues());
		clearParameters();
    }

    /**
     * Makes the set of commands in the current batch empty.
     *
     * @throws SQLException if a database access error occurs or the
     * driver does not support batch statements
     */
    public void clearBatch() throws SQLException {
    	if (batchParameterList != null ) {
    		batchParameterList.clear();
    	}
    }

    /**
     * <p>Clears the values set for the PreparedStatement object's IN parameters and
     * releases the resources used by those values. In general, parameter values
     * remain in force for repeated use of statement.
     * @throws SQLException if there is an error while clearing params
     */
    public void clearParameters() throws SQLException {
        checkStatement();
        //clear the parameters list on servers prepared statement object
        if(parameterMap != null){
            parameterMap.clear();
        }
    }
    
    @Override
    public boolean execute(String sql) throws SQLException {
    	String msg = JDBCPlugin.Util.getString("JDBC.Method_not_supported"); //$NON-NLS-1$
        throw new MMSQLException(msg);
    }
    
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
    	String msg = JDBCPlugin.Util.getString("JDBC.Method_not_supported"); //$NON-NLS-1$
        throw new MMSQLException(msg);
    }
    
    @Override
    public int executeUpdate(String sql) throws SQLException {
    	String msg = JDBCPlugin.Util.getString("JDBC.Method_not_supported"); //$NON-NLS-1$
        throw new MMSQLException(msg);
    }
    
    @Override
    public void addBatch(String sql) throws SQLException {
    	String msg = JDBCPlugin.Util.getString("JDBC.Method_not_supported"); //$NON-NLS-1$
        throw new MMSQLException(msg);
    }

	//## JDBC4.0-begin ##
	@Override
	//## JDBC4.0-end ##
    public boolean execute() throws SQLException {
        executeSql(new String[] {this.prepareSql}, false, null);
        return hasResultSet();
    }
    
    @Override
    public int[] executeBatch() throws SQLException {
    	if (batchParameterList == null || batchParameterList.isEmpty()) {
   	     	return new int[0];
    	}
	   	try{
	   		executeSql(new String[] {this.prepareSql}, true, false);
	   	}finally{
	   		batchParameterList.clear();
	   	}
	   	return this.updateCounts;
    }

	//## JDBC4.0-begin ##
	@Override
	//## JDBC4.0-end ##
    public ResultSet executeQuery() throws SQLException {
        executeSql(new String[] {this.prepareSql}, false, true);
        return resultSet;
    }

	//## JDBC4.0-begin ##
	@Override
	//## JDBC4.0-end ##
    public int executeUpdate() throws SQLException {
        executeSql(new String[] {this.prepareSql}, false, false);
        return this.updateCounts[0];
    }
    
    @Override
    protected RequestMessage createRequestMessage(String[] commands,
    		boolean isBatchedCommand, Boolean requiresResultSet) {
    	RequestMessage message = super.createRequestMessage(commands, false, requiresResultSet);
    	message.setPreparedStatement(true);
    	message.setParameterValues(isBatchedCommand?getParameterValuesList(): getParameterValues());
    	message.setPreparedBatchUpdate(isBatchedCommand);
    	return message;
    }

    /**
     * <p>Retreives a ResultSetMetaData object with information about the numbers,
     * types, and properties of columns in the ResultSet object that will be returned
     * when this preparedstatement object is executed.
     * @return ResultSetMetaData object
     * @throws SQLException, currently there is no means of getting results
     * metadata before getting results.
     */
    public ResultSetMetaData getMetaData() throws SQLException {

        // check if the statement is open
        checkStatement();

        if(metadata == null) {
        	if (updateCounts != null) {
        		return null;
        	} else if(resultSet != null) {
                metadata = resultSet.getMetaData();
            } else {
    			MetadataResult results;
				try {
					results = this.getDQP().getMetadata(this.currentRequestID, prepareSql, Boolean.valueOf(getExecutionProperty(ExecutionProperties.ALLOW_DBL_QUOTED_VARIABLE)).booleanValue());
				} catch (MetaMatrixComponentException e) {
					throw MMSQLException.create(e);
				} catch (MetaMatrixProcessingException e) {
					throw MMSQLException.create(e);
				}
				if (results.getColumnMetadata() == null) {
					return null;
				}
                StaticMetadataProvider provider = StaticMetadataProvider.createWithData(results.getColumnMetadata(), results.getParameterCount());
                metadata = ResultsMetadataWithProvider.newInstance(provider);
            }
        }

        return metadata;
    }

    /**
     * <p>Sets the parameter in position parameterIndex to the input stream object
     * fin, from which length bytes will be read and sent to metamatrix.
     * @param parameterIndex of the parameter whose value is to be set
     * @param in input stream in ASCII to which the parameter value is to be set.
     * @param length, number of bytes to read from the stream
     */
    public void setAsciiStream (int parameterIndex, java.io.InputStream in, int length) throws SQLException {
        //create a clob from the ascii stream
    	try {
			setObject(parameterIndex, new SerialClob(ObjectConverterUtil.convertToCharArray(in, length, "ASCII"))); //$NON-NLS-1$
		} catch (IOException e) {
			throw MMSQLException.create(e);
		} 
    }

    /**
     * <p>Sets the IN parameter at paramaterIndex to a BigDecimal object. The parameter
     * type is set to NUMERIC
     * @param parameterIndex of the parameter whose value is to be set
     * @param BigDecimal object to which the parameter value is to be set.
     * @throws SQLException, should not occur
     */
    public void setBigDecimal (int parameterIndex, java.math.BigDecimal value) throws SQLException {
        setObject(parameterIndex, value);
    }

    /**
     * <p>Sets the parameter in position parameterIndex to the input stream object
     * fin, from which length bytes will be read and sent to metamatrix.
     * @param parameterIndex of the parameter whose value is to be set
     * @param input stream in binary to which the parameter value is to be set.
     * @param length, number of bytes to read from the stream
     */
    public void setBinaryStream(int parameterIndex, java.io.InputStream in, int length) throws SQLException {
    	//create a blob from the ascii stream
    	try {
			setObject(parameterIndex, new SerialBlob(ObjectConverterUtil.convertToByteArray(in, length)));
		} catch (IOException e) {
			throw MMSQLException.create(e);
		}
    }

    /**
     * <p>Sets the parameter in position parameterIndex to a Blob object.
     * @param parameterIndex of the parameter whose value is to be set
     * @param Blob object to which the parameter value is to be set.
     * @throws SQLException if parameter type/datatype do not match
     */
    public void setBlob (int parameterIndex, Blob x) throws SQLException {
        setObject(parameterIndex, x);
    }

    /**
     * <p>Sets parameter number parameterIndex to b, a Java boolean value. The parameter
     * type is set to BIT
     * @param parameterIndex of the parameter whose value is to be set
     * @param boolean value to which the parameter value is to be set.
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setBoolean (int parameterIndex, boolean value) throws SQLException {
        setObject(parameterIndex, new Boolean(value));
    }

    /**
     * <p>Sets parameter number parameterIndex to x, a Java byte value. The parameter
     * type is set to TINYINT
     * @param parameterIndex of the parameter whose value is to be set
     * @param byte value to which the parameter value is to be set.
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setByte(int parameterIndex, byte value) throws SQLException {
        setObject(parameterIndex, new Byte(value));
    }

    /**
     * <p>Sets parameter number parameterIndex to x[], a Java array of bytes.
     * @param parameterIndex of the parameter whose value is to be set
     * @param bytes array to which the parameter value is to be set.
     */
    public void setBytes(int parameterIndex, byte bytes[]) throws SQLException {
    	//create a blob from the ascii stream
    	setObject(parameterIndex, new SerialBlob(bytes));
    }

    public void setCharacterStream (int parameterIndex, java.io.Reader reader, int length) throws SQLException {
    	//create a clob from the ascii stream
    	try {
			setObject(parameterIndex, new SerialClob(ObjectConverterUtil.convertToCharArray(reader, length)));
		} catch (IOException e) {
			throw MMSQLException.create(e);
		}
    }

    /**
     * <p>Sets the parameter in position parameterIndex to a Clob object.
     * @param parameterIndex of the parameter whose value is to be set
     * @param Clob object to which the parameter value is to be set.
     * @throws SQLException if parameter type/datatype do not match.
     */
    public void setClob (int parameterIndex, Clob x) throws SQLException {
        setObject(parameterIndex, x);
    }

    /**
     * <p>Sets parameter number parameterIndex to x, a java.sql.Date object. The parameter
     * type is set to DATE
     * @param parameterIndex of the parameter whose value is to be set
     * @param Date object to which the parameter value is to be set.
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setDate(int parameterIndex, java.sql.Date value) throws SQLException {
        setDate(parameterIndex, value, null);
    }

    /**
     * <p>Sets parameter number parameterIndex to x, a java.sql.Date object. The parameter
     * type is set to DATE
     * @param parameterIndex of the parameter whose value is to be set
     * @param Date object to which the parameter value is to be set.
     * @param Calendar object to constrct date(useful to get include timezone info)
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setDate(int parameterIndex, java.sql.Date x ,java.util.Calendar cal) throws SQLException {

        if (cal == null || x == null) {
            setObject(parameterIndex, x);
            return;
        }
                
        // set the parameter on the stored procedure
        setObject(parameterIndex, TimestampWithTimezone.createDate(x, cal.getTimeZone(), getDefaultCalendar()));
    }

    /**
     * <p>Sets parameter number parameterIndex to x, a double value. The parameter
     * type is set to DOUBLE
     * @param parameterIndex of the parameter whose value is to be set
     * @param double value to which the parameter value is to be set.
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setDouble(int parameterIndex, double value) throws SQLException {
        setObject(parameterIndex, new Double(value));
    }

    /**
     * <p>Sets parameter number parameterIndex to value, a float value. The parameter
     * type is set to FLOAT
     * @param parameterIndex of the parameter whose value is to be set
     * @param float value to which the parameter value is to be set.
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setFloat(int parameterIndex, float value) throws SQLException {
        setObject(parameterIndex, new Float(value));
    }

    /**
     * <p>Sets parameter number parameterIndex to value, a int value. The parameter
     * type is set to INTEGER
     * @param parameterIndex of the parameter whose value is to be set
     * @param int value to which the parameter value is to be set.
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setInt(int parameterIndex, int value) throws SQLException {
        setObject(parameterIndex, new Integer(value));
    }

    /**
     * <p>Sets parameter number parameterIndex to x, a long value. The parameter
     * type is set to BIGINT
     * @param parameterIndex of the parameter whose value is to be set
     * @param long value to which the parameter value is to be set.
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setLong(int parameterIndex, long value) throws SQLException {
        setObject(parameterIndex, new Long(value));
    }

    /**
     * <p>Sets parameter number parameterIndex to a null value.
     * @param parameterIndex of the parameter whose value is to be set
     * @param jdbc type of the parameter whose value is to be set to null
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setNull(int parameterIndex, int jdbcType) throws SQLException {
        setObject(parameterIndex, null);
    }

    /**
     * <p>Sets parameter number parameterIndex to a null value.
     * @param parameterIndex of the parameter whose value is to be set
     * @param jdbc type of the parameter whose value is to be set to null
     * @param fully qualifies typename of the parameter being set.
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setNull(int parameterIndex, int jdbcType, String typeName) throws SQLException {
        setObject(parameterIndex, null);
    }

    /**
     * <p>Sets parameter number parameterIndex to an object value
     * @param parameterIndex of the parameter whose value is to be set
     * @param an object value to which the parameter value is to be set.
     * @param int value giving the JDBC type to conver the object to
     * @param int value giving the scale to be set if the type is DECIMAL or NUMERIC
     * @throws SQLException, if there is an error setting the parameter value
     */
    public void setObject (int parameterIndex, Object value, int targetJdbcType, int scale) throws SQLException {

       if(value == null) {
            setObject(parameterIndex, null);
            return;
        }

       if(targetJdbcType != Types.DECIMAL || targetJdbcType != Types.NUMERIC) {
            setObject(parameterIndex, value, targetJdbcType);
        // Decimal and NUMERIC types correspong to java.math.BigDecimal
        } else {
            // transform the object to a BigDecimal
            BigDecimal bigDecimalObject = DataTypeTransformer.getBigDecimal(value);
            // set scale on the BigDecimal
            bigDecimalObject.setScale(scale);

            setObject(parameterIndex, bigDecimalObject);
        }
    }
    
    public void setObject(int parameterIndex, Object value, int targetJdbcType) throws SQLException {

        Object targetObject = null;

       if(value == null) {
            setObject(parameterIndex, null);
            return;
        }

        // get the java class name for the given JDBC type
        String javaClassName = MMJDBCSQLTypeInfo.getJavaClassName(targetJdbcType);
        // transform the value to the target datatype
        if(javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.STRING_CLASS)) {
           targetObject = value.toString();
        } else if(javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.CHAR_CLASS)) {
            targetObject = DataTypeTransformer.getCharacter(value);
        } else if(javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.INTEGER_CLASS)) {
            targetObject = DataTypeTransformer.getInteger(value);
        } else if(javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.BYTE_CLASS)) {
            targetObject = DataTypeTransformer.getByte(value);
        } else if(javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.SHORT_CLASS)) {
            targetObject = DataTypeTransformer.getShort(value);
        } else if(javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.LONG_CLASS)) {
            targetObject = DataTypeTransformer.getLong(value);
        } else if(javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.FLOAT_CLASS)) {
            targetObject = DataTypeTransformer.getFloat(value);
        } else if(javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.DOUBLE_CLASS)) {
            targetObject = DataTypeTransformer.getDouble(value);
        } else if(javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.BOOLEAN_CLASS)) {
            targetObject = DataTypeTransformer.getBoolean(value);
        } else if(javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.BIGDECIMAL_CLASS)) {
            targetObject = DataTypeTransformer.getBigDecimal(value);
        } else if(javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.TIMESTAMP_CLASS)) {
            targetObject = DataTypeTransformer.getTimestamp(value);
        } else if(javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.DATE_CLASS)) {
            targetObject = DataTypeTransformer.getDate(value);
        } else if(javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.TIME_CLASS)) {
            targetObject = DataTypeTransformer.getTime(value);
        } else if (javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.BLOB_CLASS)) {
            targetObject = DataTypeTransformer.getBlob(value);
        } else if (javaClassName.equalsIgnoreCase(MMJDBCSQLTypeInfo.CLOB_CLASS)) {
            targetObject = DataTypeTransformer.getClob(value);
        } else {
            String msg = JDBCPlugin.Util.getString("MMPreparedStatement.Err_transform_obj"); //$NON-NLS-1$
            throw new MMSQLException(msg);
        }

        setObject(parameterIndex, targetObject);
    }

    /**
     * <p>Sets parameter number parameterIndex to an object value
     * @param parameterIndex of the parameter whose value is to be set
     * @param an object value to which the parameter value is to be set.
     * @throws SQLException, if there is an error setting the parameter value
     */
    public void setObject(int parameterIndex, Object value) throws SQLException {
        ArgCheck.isPositive(parameterIndex, JDBCPlugin.Util.getString("MMPreparedStatement.Invalid_param_index")); //$NON-NLS-1$

        if(parameterMap == null){
            parameterMap = new TreeMap<Integer, Object>();
        }
        
        Object val = null;
        if (serverCalendar != null && value instanceof java.util.Date) {
            val = TimestampWithTimezone.create((java.util.Date)value, getDefaultCalendar().getTimeZone(), serverCalendar, value.getClass());
        } else val = value;

        parameterMap.put(new Integer(parameterIndex), val);
    }

    /**
     * <p>Sets parameter number parameterIndex to x, a short value. The parameter
     * type is set to TINYINT
     * @param parameterIndex of the parameter whose value is to be set
     * @param short value to which the parameter value is to be set.
     * @throws SQLException, if there is an error setting the parameter value
     */
    public void setShort(int parameterIndex, short value) throws SQLException {
        setObject(parameterIndex, new Short(value));
    }

    /**
     * <p>Sets parameter number parameterIndex to x, a String value. The parameter
     * type is set to VARCHAR
     * @param parameterIndex of the parameter whose value is to be set
     * @param String object to which the parameter value is to be set.
     * @throws SQLException
     */
    public void setString(int parameterIndex, String value) throws SQLException {
        setObject(parameterIndex, value);
    }

    /**
     * <p>Sets parameter number parameterIndex to x, a java.sql.Time object. The parameter
     * type is set to TIME
     * @param parameterIndex of the parameter whose value is to be set
     * @param Time object to which the parameter value is to be set.
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setTime(int parameterIndex, java.sql.Time value) throws SQLException {
        setTime(parameterIndex, value, null);
    }

    /**
     * <p>Sets parameter number parameterIndex to x, a java.sql.Time object. The parameter
     * type is set to TIME
     * @param parameterIndex of the parameter whose value is to be set
     * @param Time object to which the parameter value is to be set.
     * @param Calendar object to constrct Time(useful to get include timezone info)
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setTime(int parameterIndex, java.sql.Time x, java.util.Calendar cal) throws SQLException {

       if (cal == null || x == null) {
           setObject(parameterIndex, x);
           return;
       }
               
       // set the parameter on the stored procedure
       setObject(parameterIndex, TimestampWithTimezone.createTime(x, cal.getTimeZone(), getDefaultCalendar()));
    }

    /**
     * <p>Sets parameter number parameterIndex to x, a java.sql.Timestamp object. The
     * parameter type is set to TIMESTAMP
     * @param parameterIndex of the parameter whose value is to be set
     * @param Timestamp object to which the parameter value is to be set.
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setTimestamp(int parameterIndex, java.sql.Timestamp value) throws SQLException {
        setTimestamp(parameterIndex, value, null);
    }

    /**
     * <p>Sets parameter number parameterIndex to x, a java.sql.Timestamp object. The
     * parameter type is set to TIMESTAMP
     * @param parameterIndex of the parameter whose value is to be set
     * @param Timestamp object to which the parameter value is to be set.
     * @param Calendar object to constrct timestamp(useful to get include timezone info)
     * @throws SQLException, if parameter type/datatype do not match
     */
    public void setTimestamp(int parameterIndex, java.sql.Timestamp x, java.util.Calendar cal) throws SQLException {

        if (cal == null || x == null) {
            setObject(parameterIndex, x);
            return;
        }
                
        // set the parameter on the stored procedure
        setObject(parameterIndex, TimestampWithTimezone.createTimestamp(x, cal.getTimeZone(), getDefaultCalendar()));
    }

    /**
     * Sets the designated parameter to the given java.net.URL value. The driver
     * converts this to an SQL DATALINK value when it sends it to the database.
     * @param parameter int index
     * @param x URL to be set
     * @throws SQLException
     */
    public void setURL(int parameterIndex, URL x) throws SQLException {
        setObject(parameterIndex, x);
    }

    List<List<Object>> getParameterValuesList() {
    	if(batchParameterList == null || batchParameterList.isEmpty()){
    		return Collections.emptyList();
    	}
    	return new ArrayList<List<Object>>(batchParameterList);
    }
    
    List<Object> getParameterValues() {
        if(parameterMap == null || parameterMap.isEmpty()){
            return Collections.emptyList();
        }
        return new ArrayList<Object>(parameterMap.values());
    }

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#getParameterMetaData()
	 */
	public ParameterMetaData getParameterMetaData() throws SQLException {
		/* Implement for JDBC 3.0 */
		return null;
	}

    /**
     * Exposed for unit testing 
     */
    void setServerCalendar(Calendar serverCalendar) {
        this.serverCalendar = serverCalendar;
    }

	public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
		setObject(parameterIndex, xmlObject);
	}

	public void setArray(int parameterIndex, Array x) throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setAsciiStream(int parameterIndex, InputStream x)
			throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setAsciiStream(int parameterIndex, InputStream x, long length)
			throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setBinaryStream(int parameterIndex, InputStream x)
			throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setBinaryStream(int parameterIndex, InputStream x, long length)
			throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setBlob(int parameterIndex, InputStream inputStream)
			throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setCharacterStream(int parameterIndex, Reader reader)
			throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setCharacterStream(int parameterIndex, Reader reader,
			long length) throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setClob(int parameterIndex, Reader reader) throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setClob(int parameterIndex, Reader reader, long length)
			throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setNCharacterStream(int parameterIndex, Reader value)
			throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setNCharacterStream(int parameterIndex, Reader value,
			long length) throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	//## JDBC4.0-begin ##
	public void setNClob(int parameterIndex, NClob value) throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}
	//## JDBC4.0-end ##

	public void setNClob(int parameterIndex, Reader reader) throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setNClob(int parameterIndex, Reader reader, long length)
			throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setNString(int parameterIndex, String value)
			throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setRef(int parameterIndex, Ref x) throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	//## JDBC4.0-begin ##
	public void setRowId(int parameterIndex, RowId x) throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}
	//## JDBC4.0-end ##

	public void setUnicodeStream(int parameterIndex, InputStream x, int length)
			throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}
}
