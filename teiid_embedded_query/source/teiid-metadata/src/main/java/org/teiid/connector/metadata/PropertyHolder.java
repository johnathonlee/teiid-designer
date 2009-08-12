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

package org.teiid.connector.metadata;

import java.util.Map;

/**
 */
public class PropertyHolder implements Map.Entry {
    private Object key;
    private String value;
    
    /**
     * 
     */
    public PropertyHolder(Object key) {
        this.key = key;
    }

    /* 
     * @see java.util.Map.Entry#getKey()
     */
    public Object getKey() {
        return key;
    }

    /* 
     * @see java.util.Map.Entry#getValue()
     */
    public Object getValue() {
        return value;
    }

    /* 
     * @see java.util.Map.Entry#setValue(java.lang.Object)
     */
    public Object setValue(Object value) {
        this.value = (String) value;
        return this.value;
    }

}
