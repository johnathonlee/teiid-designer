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

package com.metamatrix.query.function.metadata;

import java.util.Collection;
import java.util.Iterator;

import com.metamatrix.api.exception.query.FunctionMetadataException;
import com.metamatrix.common.types.DataTypeManager;
import com.metamatrix.query.QueryPlugin;
import com.metamatrix.query.report.ActivityReport;
import com.metamatrix.query.util.ErrorMessageKeys;

/**
 * The validator encodes business logic with respect to what a valid function
 * description is.  These methods call each other from the most complex components
 * (FunctionMethod) to the simplest pieces (function name).  Certain users
 * of the validator may only need to call lower level methods.
 */
public class FunctionMetadataValidator {

	/**
	 *  Maximum length for function names, parameter names, categories, and descriptions.
	 */
	public static final int MAX_LENGTH = 128;

    // Can't construct
	private FunctionMetadataValidator() {
    }

	/**
	 * Validate a collection of {@link FunctionMethod} objects.
	 * @param methods Collection of {@link FunctionMethod} objects
	 * @param report Report to store validation errors
	 */
	public static final void validateFunctionMethods(Collection methods, ActivityReport report) {
	    if(methods != null) {
	    	Iterator methodIter = methods.iterator();
	    	while(methodIter.hasNext()) {
	    		FunctionMethod method = (FunctionMethod) methodIter.next();
	    		validateFunctionMethod(method, report);
	    	}
	    }
	}

    /**
     * Determine whether a FunctionMethod is valid.  The following items are validated:
     * <UL>
     * <LI>Validate method name</LI>
     * <LI>Validate description</LI>
     * <LI>Validate category</LI>
     * <LI>Validate invocation method</LI>
     * <LI>Validate all input parameters</LI>
     * <LI>Validate output parameter</LI>
     * </UL>
     * @param method The method to validate
     * @param report The report to update during validation
     */
    public static final void validateFunctionMethod(FunctionMethod method, ActivityReport report) {
        if(method == null) {
            updateReport(report, method, QueryPlugin.Util.getString(ErrorMessageKeys.FUNCTION_0052, "FunctionMethod")); //$NON-NLS-1$
            return;  // can't validate
        }

        try {
	        // Validate attributes
	        validateName(method.getName());
	        validateDescription(method.getDescription());
	        validateCategory(method.getCategory());
	        validateInvocationMethod(method.getInvocationClass(), method.getInvocationMethod(), method.getPushdown());

	        // Validate input parameters
	        FunctionParameter[] params = method.getInputParameters();
	        if(params != null) {
	            for(int i=0; i<params.length; i++) {
	                validateFunctionParameter(params[i]);
	            }
	        }

	        // Validate output parameters
	        validateFunctionParameter(method.getOutputParameter());
        } catch(FunctionMetadataException e) {
        	updateReport(report, method, e.getMessage());
        }
    }

	/**
	 * Update a report with a validation error.
	 * @param report The report to update
	 * @param method The function method
	 * @param message The message about the validation failure
	 */
	private static final void updateReport(ActivityReport report, FunctionMethod method, String message) {
	    report.addItem(new InvalidFunctionItem(method, message));
	}

    /**
     * Determine whether a FunctionParameter is valid.  The following items are validated:
     * <UL>
     * <LI>Validate parameter name</LI>
     * <LI>Validate description</LI>
     * <LI>Validate type</LI>
     * </UL>
     * @param param The parameter to validate
     * @throws FunctionMetadataException Thrown if function parameter is not valid in some way
     */
    public static final void validateFunctionParameter(FunctionParameter param) throws FunctionMetadataException {
        if(param == null) {
            throw new FunctionMetadataException(ErrorMessageKeys.FUNCTION_0053, QueryPlugin.Util.getString(ErrorMessageKeys.FUNCTION_0053));
        }

        // Validate attributes
        validateName(param.getName());
        validateType(param.getType());
        validateDescription(param.getDescription());
    }

    /**
     * Determine whether a function or parameter name is valid.  The following items are validated:
     * <UL>
     * <LI>Validate that name is not null</LI>
     * <LI>Validate that name has length <= 128</LI>
     * <LI>Validate that name starts with alphabetic character</LI>
     * <LI>Validate that name contains only valid characters: letters, numbers, and _</LI>
     * </UL>
     * @param name Name to validate
     * @throws FunctionMetadataException Thrown if function or parameter name is not valid in some way
     */
    public static final void validateName(String name) throws FunctionMetadataException {
        validateIsNotNull(name, "Name"); //$NON-NLS-1$
        validateLength(name, MAX_LENGTH, "Name"); //$NON-NLS-1$
        validateNameCharacters(name, "Name"); //$NON-NLS-1$
    }

    /**
     * Determine whether a parameter type is valid.  The following items are validated:
     * <UL>
     * <LI>Validate that type is not null</LI>
     * <LI>Validate that type is a known MetaMatrix type</LI>
     * </UL>
     * @param type Type to validate
     * @throws FunctionMetadataException Thrown if parameter type is not valid in some way
     */
    public static final void validateType(String type) throws FunctionMetadataException {
        validateIsNotNull(type, "Type"); //$NON-NLS-1$

        if(DataTypeManager.getDataTypeClass(type) == null) {
            throw new FunctionMetadataException(ErrorMessageKeys.FUNCTION_0054, QueryPlugin.Util.getString(ErrorMessageKeys.FUNCTION_0054, type));
        }
    }

    /**
     * Determine whether a description is valid.  The following items are validated:
     * <UL>
     * <LI>Validate that description (if not null) has length <= 128</LI>
     * </UL>
     * @param description Description to validate
     * @throws FunctionMetadataException Thrown if description is not valid in some way
     */
    public static final void validateDescription(String description) throws FunctionMetadataException {
		if(description != null) {
        	validateLength(description, MAX_LENGTH, "Description"); //$NON-NLS-1$
		}
    }

    /**
     * Determine whether a category is valid.  The following items are validated:
     * <UL>
     * <LI>Validate that category is not null</LI>
     * <LI>Validate that category has length <= 128</LI>
     * </UL>
     * @param category Category to validate
     * @throws FunctionMetadataException Thrown if category is not valid in some way
     */
    public static final void validateCategory(String category) throws FunctionMetadataException {
        validateIsNotNull(category, "Category"); //$NON-NLS-1$
        validateLength(category, MAX_LENGTH, "Category"); //$NON-NLS-1$
    }

    /**
     * Determine whether an invocation class and method are valid.  The following items are validated:
     * <UL>
     * <LI>Validate that invocation class is not null</LI>
     * <LI>Validate that invocation method is not null</LI>
     * <LI>Validate that class is valid Java class name</LI>
     * <LI>Validate that method is valid Java method name</LI>
     * </UL>
     * @param invocationClass Invocation class to validate
     * @param invocationMethod Invocation method to validate
     * @throws FunctionMetadataException Thrown if invocation method is not valid in some way
     */
    public static final void validateInvocationMethod(String invocationClass, String invocationMethod, int pushdown) throws FunctionMetadataException {
    	if (pushdown == FunctionMethod.CAN_PUSHDOWN || pushdown == FunctionMethod.CANNOT_PUSHDOWN) {
            validateIsNotNull(invocationClass, "Invocation class"); //$NON-NLS-1$
            validateIsNotNull(invocationMethod, "Invocation method"); //$NON-NLS-1$
            validateJavaIdentifier(invocationClass, "Invocation class", true); //$NON-NLS-1$
            validateJavaIdentifier(invocationMethod, "Invocation method", false); //$NON-NLS-1$
        }
    }

    /**
     * Check that specified object is not null.  If object is null, throw exception using objName.
     * @param object Object to check for null
     * @param objName Object name used when throwing exception
     * @throws FunctionMetadataException Thrown when object == null
     */
	private static final void validateIsNotNull(Object object, String objName) throws FunctionMetadataException {
		if(object == null) {
		    throw new FunctionMetadataException(ErrorMessageKeys.FUNCTION_0052, QueryPlugin.Util.getString(ErrorMessageKeys.FUNCTION_0052, objName));
		}
	}

    /**
     * Check that specified string is no longer than maxLength.  If string is longer, an exception is thrown
     * using strName.
     * @param string String to check for length
     * @param maxLength Maximum valid length
     * @param strName Name of string to use in exception message
     * @throws FunctionMetadataException Thrown when string.length() > maxLength
     */
	private static final void validateLength(String string, int maxLength, String strName) throws FunctionMetadataException {
	 	if(string.length() > maxLength) {
	 	 	throw new FunctionMetadataException(ErrorMessageKeys.FUNCTION_0055, QueryPlugin.Util.getString(ErrorMessageKeys.FUNCTION_0055,strName, new Integer(maxLength)));
	 	}
	}

    /**
     * Check that specified string uses valid allowed character set.  If not, an exception is thrown using
     * strName for the exception message.
     * @param name String to check
     * @param strName String to use in exception message
     * @throws FunctionMetadataException Thrown when string uses characters not in allowed character sets
     */
	private static final void validateNameCharacters(String name, String strName) throws FunctionMetadataException {
	    // First check first character
		if(name.length() > 0) {
		    // Check special cases
		    if(name.equals("+") || name.equals("-") || name.equals("*") || name.equals("/") || name.equals("||")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		        return;
		    }

			char firstChar = name.charAt(0);
			if(! Character.isLetter(firstChar)) {
			 	throw new FunctionMetadataException(ErrorMessageKeys.FUNCTION_0056, QueryPlugin.Util.getString(ErrorMessageKeys.FUNCTION_0056,strName, new Character(firstChar)));
			}

			// Then check the rest of the characters
			for(int i=1; i<name.length(); i++) {
				char ch = name.charAt(i);
				if(! (Character.isLetterOrDigit(ch) || ch == '_')) {
				 	throw new FunctionMetadataException(ErrorMessageKeys.FUNCTION_0057, QueryPlugin.Util.getString(ErrorMessageKeys.FUNCTION_0057,strName, new Character(ch)));
				}
			}
	    }
	}

    /**
     * Check that specified string is valid Java identifier.  If not, an exception is thrown using
     * strName for the exception message.
     * @param identifier String to check
     * @param strName String to use in exception message
     * @param allowMultiple True if multiple identifiers are allowed, as in a class name
     * @throws FunctionMetadataException Thrown when string is not valid Java identifier
     */
	private static final void validateJavaIdentifier(String identifier, String strName, boolean allowMultiple) throws FunctionMetadataException {
	    // First check first character
		if(identifier.length() > 0) {
			char firstChar = identifier.charAt(0);
			if(! Character.isJavaIdentifierStart(firstChar)) {
			 	throw new FunctionMetadataException(ErrorMessageKeys.FUNCTION_0056, QueryPlugin.Util.getString(ErrorMessageKeys.FUNCTION_0056,strName, new Character(firstChar)));
			}

			// Then check the rest of the characters
			for(int i=1; i<identifier.length(); i++) {
				char ch = identifier.charAt(i);
				if(! Character.isJavaIdentifierPart(ch)) {
				    if(! allowMultiple || ! (ch == '.')) {
					 	throw new FunctionMetadataException(ErrorMessageKeys.FUNCTION_0057, QueryPlugin.Util.getString(ErrorMessageKeys.FUNCTION_0057,strName, new Character(ch)));
				    }
				}
			}

			if(identifier.charAt(identifier.length()-1) == '.') {
			 	throw new FunctionMetadataException(ErrorMessageKeys.FUNCTION_0058, QueryPlugin.Util.getString(ErrorMessageKeys.FUNCTION_0058,strName));
			}
	    }
	}

}
