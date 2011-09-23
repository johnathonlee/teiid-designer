/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.teiid.designer.extension.definition;

import com.metamatrix.core.util.CoreArgCheck;

/**
 * 
 */
public class ModelExtensionDefinitionHeader {

    /**
     * The default version number. Value is {@value} .
     */
    public static final int DEFAULT_VERSION = 1;

    /**
     * The metamodel URI that this definition is extended.
     */
    private final String metamodelUri;

    /**
     * The unique namespace prefix of this definition (never <code>null</code> or empty).
     */
    private final String namespacePrefix;

    /**
     * The unique namespace URI of this definition (never <code>null</code> or empty).
     */
    private final String namespaceUri;

    /**
     * The definition description (can be <code>null</code> or empty).
     */
    private String description;

    /**
     * The version number. Defaults to {@value} .
     */
    private int version = DEFAULT_VERSION;

    public ModelExtensionDefinitionHeader( String namespacePrefix,
                                           String namespaceUri,
                                           String metamodelUri,
                                           String description,
                                           int version ) {
        CoreArgCheck.isNotEmpty(namespacePrefix, "namespacePrefix is null"); //$NON-NLS-1$
        CoreArgCheck.isNotEmpty(namespaceUri, "namespaceUri is null"); //$NON-NLS-1$
        CoreArgCheck.isNotEmpty(metamodelUri, "metamodelUri is null"); //$NON-NLS-1$

        this.namespacePrefix = namespacePrefix;
        this.namespaceUri = namespaceUri;
        this.metamodelUri = metamodelUri;
        this.description = description;
        this.version = version;
    }

    public ModelExtensionDefinitionHeader( String namespacePrefix,
                                           String namespaceUri,
                                           String metamodelUri,
                                           String description ) {
        this(namespacePrefix, namespaceUri, metamodelUri, description, DEFAULT_VERSION);
    }

    public ModelExtensionDefinitionHeader( String namespacePrefix,
                                           String namespaceUri,
                                           String metamodelUri ) {
        this(namespacePrefix, namespaceUri, metamodelUri, null, DEFAULT_VERSION);
    }

    /**
     * @return description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description Sets description to the specified value.
     */
    public void setDescription( String description ) {
        this.description = description;
    }

    /**
     * @return version
     */
    public int getVersion() {
        return version;
    }

    /**
     * @param version Sets version to the specified value.
     */
    public void setVersion( int version ) {
        this.version = version;
    }

    /**
     * @return metamodelUri
     */
    public String getMetamodelUri() {
        return metamodelUri;
    }

    /**
     * @return namespacePrefix
     */
    public String getNamespacePrefix() {
        return namespacePrefix;
    }

    /**
     * @return namespaceUri
     */
    public String getNamespaceUri() {
        return namespaceUri;
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public final boolean equals( final Object object ) {
        if (this == object) return true;
        if (object == null) return false;
        if (getClass() != object.getClass()) return false;
        final ModelExtensionDefinitionHeader other = (ModelExtensionDefinitionHeader)object;

        if (this.namespacePrefix == null) {
            if (other.namespacePrefix != null) return false;
        } else if (!this.namespacePrefix.equals(other.namespacePrefix)) return false;

        if (this.namespaceUri == null) {
            if (other.namespaceUri != null) return false;
        } else if (!this.namespaceUri.equals(other.namespaceUri)) return false;

        if (this.metamodelUri == null) {
            if (other.metamodelUri != null) return false;
        } else if (!this.metamodelUri.equals(other.metamodelUri)) return false;

        if (this.description == null) {
            if (other.description != null) return false;
        } else if (!this.description.equals(other.description)) return false;

        if (this.version != other.version) return false;

        return true;
    }

    /**
     * {@inheritDoc}
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        text.append("Model Extension Definition Header: "); //$NON-NLS-1$
        text.append("namespacePrefix=").append(getNamespacePrefix()); //$NON-NLS-1$
        text.append(", namespaceUri=").append(getNamespaceUri()); //$NON-NLS-1$
        text.append(", metamodelUri=").append(getMetamodelUri()); //$NON-NLS-1$
        text.append(", version=").append(getVersion()); //$NON-NLS-1$

        return text.toString();
    }

}
