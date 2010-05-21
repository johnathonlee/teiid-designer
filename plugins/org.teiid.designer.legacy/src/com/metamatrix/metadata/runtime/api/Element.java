/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */

package com.metamatrix.metadata.runtime.api;

/**
 * <p>Instances of this interface represent Elements for a Group.  The values of an Element are analogous to a Column ion a table in a database.</p> 
 */
public interface Element extends MetadataObject {

/**
 * Return the description.
 *  @return String 
 */
    String getDescription();
/**
 * Return the alias.
 *  @return String alias
 */
    String getAlias();
/**
 * Returns the label.
 * @return String 
 */
    String getLabel();
/**
 * Returns the name-in-soure for this element.
 * @return String is the name in source 
 */
    String getNameInSource();
/**
 * Returns whether the name-in-soure is defined for this element.
 * @return true if this element has the name in source; false otherwise.
 */
    boolean hasNameInSource();
/**
 * Returns the <code>DataType</code> this element is represented as.
 * If it is a user-defined type, the corresponding runtime type is returned.
 * @return DataType 
 */
    DataType getDataType();
/**
 * Returns the scale, which is the number of significant digits to the right of the decimal point.
 * The scale cannot exceed the length, and the scale defaults to 0 (meaning it is an integer number and the decimal point is dropped).
 *  @return int
 */   
    int getScale();
/**
 * If the data type is numeric, the length is the total number of significant digits used to express the number.  
 * If it is a string, character array, or bit array it represents the maximum length of the value.  
 * For time and timestamp data types, the length is the number of positions that make up the fractional seconds.
 *  @return int 
 */
    int getLength();
/**
 * Returns a boolean indicating if this a physical element.
 * @return boolean 
 */
    boolean isPhysical();
/**
 * Returns a boolean indicating if the length is fixed.
 * @return boolean 
 */
    boolean isLengthFixed();
/**
 * Returns a short indicating if the element can be set to null.
 * @return short
 *
 * @see com.metamatrix.metadata.runtime.api.MetadataConstants.NULL_TYPES
 */
    short getNullType();
/**
 * Returns a boolean indicating if the element can be selected
 * @return boolean 
 */
    boolean supportsSelect();
/**
 * Returns a boolean indicating if the element can be used in a SET operation.
 * @return boolean 
 */
    boolean supportsSet();
/**
 * Returns a boolean indicating if the element can be subscribed to.
 * @return boolean 
 */
    boolean supportsSubscription();
/**
 * Returns a boolean indicating if the element can be updated.
 * @return boolean 
 */
    boolean supportsUpdate();
/**
 * Returns a boolean indicating if the element data is case sensitive.
 * This value shall be false if the data type is not a character, character array or string type.
 * @return boolean
 */
    boolean isCaseSensitive();
/**
 * Returns a boolean indicating if the element data is signed.
 * @return boolean 
 */
    boolean isSigned();
/**
 * Returns a boolean indicating if the element data represents currency.
 *  @return boolean 
 */
    boolean isCurrency();
/**
 * Returns a boolean indicating if the element is auto incremented by the database.  Therefore, this element value should not be provided on an insert statement.
 * @return boolean 
 */
    boolean isAutoIncrement();
/**
 * Returns the minimum value that the element can represent.
 * @return String
 */
    String getMinimumRange();
/**
 * eturns the maximum value that the element can represent.
 * @return String
 */
    String getMaximumRange();
/**
 * Return short indicating the search type supported on this element.
 * @return short
 *
 * @see com.metamatrix.metadata.runtime.api.MetadataConstants.SEARCH_TYPES
 */
    short getSearchType();
/**
 * Returns the format the data for this element should be displayed as.
 * @return String 
 */
    String getFormat();
/**
 * Returns the default value in the object form based on the data type for this element.
 *  @return Object 
 */
    Object getDefaultValue();

    int getPrecisionLength();
    int getRadix();
    int getCharOctetLength();
}

