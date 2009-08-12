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

package com.metamatrix.common.comm.platform.socket;

import java.io.Serializable;

import com.metamatrix.common.util.ApplicationInfo;

/**
 * Represents the information needed in a socket connection handshake  
 */
public class Handshake implements Serializable {
    
	private static final long serialVersionUID = 7839271224736355515L;
    
    private String version = ApplicationInfo.getInstance().getMajorReleaseNumber();
    private byte[] publicKey;
    
    /** 
     * @return Returns the version.
     */
    public String getVersion() {
        return this.version;
    }
    
    /** 
     * @param version The version to set.
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /** 
     * @return Returns the key.
     */
    public byte[] getPublicKey() {
        return this.publicKey;
    }
    
    /** 
     * @param key The key to set.
     */
    public void setPublicKey(byte[] key) {
        this.publicKey = key;
    }

    
}
