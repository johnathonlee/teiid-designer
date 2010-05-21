/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */

package com.metamatrix.connector.metadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teiid.core.util.ArgCheck;
import com.metamatrix.modeler.core.metadata.runtime.VdbRecord;

/**
 * Extends a VdbRecord object with additional runtime data.
 * This allows the System model to obtain the VDB name and VDB version of the deployed VDB.
 */
public class RuntimeVdbRecord implements VdbRecord {
    /**
     */
    private static final long serialVersionUID = 1L;
    private String vdbRuntimeName;
    private String vdbRuntimeVersion;
    private VdbRecord vdbRecord;
    private transient Map propValues;
    
    public RuntimeVdbRecord(VdbRecord vdbRecord, String vdbRuntimeName, String vdbRuntimeVersion) {
        this.vdbRuntimeName = vdbRuntimeName;
        this.vdbRuntimeVersion = vdbRuntimeVersion;
        this.vdbRecord = vdbRecord;
    }

    public String getVdbRuntimeName() {
        return vdbRuntimeName;
    }

    public String getVdbRuntimeVersion() {
        return vdbRuntimeVersion;
    }
    
    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.VdbRecord#getDescription()
     */
    public String getDescription() {
        return vdbRecord.getDescription();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.VdbRecord#getIdentifier()
     */
    public String getIdentifier() {
        return vdbRecord.getIdentifier();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.VdbRecord#getModelIDs()
     */
    public List getModelIDs() {
        return vdbRecord.getModelIDs();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.VdbRecord#getProducerName()
     */
    public String getProducerName() {
        return vdbRecord.getProducerName();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.VdbRecord#getProducerVersion()
     */
    public String getProducerVersion() {
        return vdbRecord.getProducerVersion();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.VdbRecord#getProvider()
     */
    public String getProvider() {
        return vdbRecord.getProvider();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.VdbRecord#getTimeLastChanged()
     */
    public String getTimeLastChanged() {
        return vdbRecord.getTimeLastChanged();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.VdbRecord#getTimeLastProduced()
     */
    public String getTimeLastProduced() {
        return vdbRecord.getTimeLastProduced();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.VdbRecord#getVersion()
     */
    public String getVersion() {
        return vdbRecord.getVersion();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getEObject()
     */
    public Object getEObject() {
        return vdbRecord.getEObject();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getFullName()
     */
    public String getFullName() {
        return vdbRecord.getFullName();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getModelName()
     */
    public String getModelName() {
        return vdbRecord.getModelName();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getName()
     */
    public String getName() {
        return vdbRecord.getName();
    }

    /**
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getNameInSource()
     */
    public String getNameInSource() {
        return vdbRecord.getNameInSource();
    }

    /**
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getParentFullName()
     * @deprecated the returned fullname may be incorrect (see defects #11326 and #11362)
     */
    @Deprecated
    public String getParentFullName() {
        return vdbRecord.getParentFullName();
    }

    /** 
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getParentPathString()
     * @deprecated the returned parent path may be incorrect (see defects #11326 and #11362)
     */
    @Deprecated
    public String getParentPathString() {
        return vdbRecord.getParentPathString();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getParentUUID()
     */
    public String getParentUUID() {
        return vdbRecord.getParentUUID();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getPath()
     */
    public String getPath() {
        return vdbRecord.getPath();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getPathString()
     */
    public String getPathString() {
        return vdbRecord.getPathString();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getRecordType()
     */
    public char getRecordType() {
        return vdbRecord.getRecordType();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getResourcePath()
     */
    public String getResourcePath() {
        return vdbRecord.getResourcePath();
    }

    /* 
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getUUID()
     */
    public String getUUID() {
        return vdbRecord.getUUID();
    }
    /** 
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#getPropertyValue(java.lang.String)
     */
    public Object getPropertyValue(String propertyName) {
        ArgCheck.isNotNull(propertyName);
        if(propValues != null) {
            return propValues.get(propertyName);
        }
        return null;
    }

    /** 
     * @see com.metamatrix.modeler.core.metadata.runtime.MetadataRecord#setPropertyValue(java.lang.String, java.lang.Object)
     */
    public void setPropertyValue(String propertyName, Object propertyValue) {
        ArgCheck.isNotNull(propertyName);
        ArgCheck.isNotNull(propertyValue);
        if(propValues == null) {
            propValues = new HashMap();
        }

        propValues.put(propertyName, propertyValue);
    }

}
