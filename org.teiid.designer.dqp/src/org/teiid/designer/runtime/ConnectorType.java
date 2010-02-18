/*
 * JBoss DNA (http://www.jboss.org/dna)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of 
 * individual contributors.
 *
 * JBoss DNA is free software. Unless otherwise indicated, all code in JBoss DNA
 * is licensed to you under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * JBoss DNA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.teiid.designer.runtime;

import java.util.Collection;
import org.teiid.adminapi.PropertyDefinition;

/**
 *
 */
public final class ConnectorType {

    private final Collection<PropertyDefinition> propDefs;
    private final String name;
    private final ServerAdmin admin;

    public ConnectorType( String name,
                          Collection<PropertyDefinition> propDefs,
                          ServerAdmin admin ) {
        this.name = name;
        this.propDefs = propDefs;
        this.admin = admin;
    }

    public String getName() {
        return this.name;
    }

    public Collection<PropertyDefinition> getPropertyDefinitions() {
        return this.propDefs;
    }

    public PropertyDefinition getPropertyDefinition(String name) {
        for (PropertyDefinition propDef : propDefs) {
            if (propDef.getName().equals(name))
                return propDef;
        }
        return null;
    }
}
