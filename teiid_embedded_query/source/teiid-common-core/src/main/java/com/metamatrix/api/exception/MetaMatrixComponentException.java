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

package com.metamatrix.api.exception;

/**
 * Exception which occurs if an error occurs within the server that is not
 * business-related.  For instance, if a service or bean is not available
 * or communication fails.
 */
public class MetaMatrixComponentException extends MetaMatrixException {


    /**
     * No-arg CTOR
     */
    public MetaMatrixComponentException(  ) {
        super(  );
    }
    /**
     * Construct an instance with the message specified.
     *
     * @param message A message describing the exception
     */
    public MetaMatrixComponentException( String message ) {
        super( message );
    }

    /**
     * Construct an instance with a linked exception specified.
     *
     * @param e An exception to chain to this exception
     */
    public MetaMatrixComponentException( Throwable e ) {
		super( e );
    }

    /**
     * Construct an instance with the message and error code specified.
     *
     * @param message A message describing the exception
     * @param code The error code
     */
    public MetaMatrixComponentException( String code, String message ) {
        super( code, message );
    }

    /**
     * Construct an instance from a message and an exception to chain to this one.
     *
     * @param e An exception to nest within this one
     * @param message A message describing the exception
     */
    public MetaMatrixComponentException( Throwable e, String message ) {
        super( e, message );
    }

    /**
     * Construct an instance from a message and a code and an exception to
     * chain to this one.
     *
     * @param e An exception to nest within this one
     * @param message A message describing the exception
     * @param code A code denoting the exception
     */
    public MetaMatrixComponentException( Throwable e, String code, String message ) {
        super( e, code, message );
    }

} // END CLASS

