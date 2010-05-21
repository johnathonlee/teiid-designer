/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */

package com.metamatrix.metadata.runtime.model;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import com.metamatrix.metadata.runtime.api.DataTypeID;
import com.metamatrix.metadata.runtime.api.ModelID;
import com.metamatrix.metadata.runtime.api.VirtualDatabase;
import com.metamatrix.metadata.runtime.api.VirtualDatabaseID;
/**
 * @persistent 
 */
final public class BasicVirtualDatabase extends BasicMetadataObject implements VirtualDatabase {
    public static final long serialVersionUID = -4773877040040318864L;
    private String fileName;
    private String description;
    private short status;
    private String GUID;
    private boolean WSDLDefined;
    private Date versionDate;
    private String versionBy;
    private Date creationDate;
    private String createdBy;
    private Date updateDate;
    private String updatedBy;
    private Collection updatedAttributesList;
    private transient Collection modelIDs;
    private transient Collection dataTypeIDs;
/**
 * Call constructor to instantiate a VirtualDatabase runtime object by passing the VIrtualDatabaseID.
 */
    public BasicVirtualDatabase(BasicVirtualDatabaseID virtualDBID) {
        super(virtualDBID);
    }
   /**
 * returns the <code>VirtualDatabaseID</code>.  This method is overriding a method in order to return its id. 
 */
    @Override
    public VirtualDatabaseID getVirtualDatabaseID() {
        return  (VirtualDatabaseID) getID();
    }
    public String getDescription() {
	      return description;
    }
    public short getStatus() {
	      return status;
    }
    
    public boolean hasWSDLDefined() {
        return WSDLDefined;
    }

/**
 * @return Collection of ModelIDs
 */
    public Collection getModelIDs() {
	      return modelIDs;
    }
    public String getGUID() {
	      return GUID;
    }
    public Date getVersionDate() {
	      return versionDate;
    }
    public String getVersionBy() {
	      return versionBy;
    }
    public Collection getDataTypeIDs() {
	      return dataTypeIDs;
    }

/**
 * return the date the original Virtual Database version was created.
 * @return Date 
 */
    public Date getCreationDate() {
	return this.creationDate;
    }
/**
 * return the user name who create the original version of the Virtual Database.
 * @return String
 */
    public String getCreatedBy() {
	return this.createdBy;
    }

    public Date getUpdateDate() {
	      return updateDate;
    }
    public String getUpdatedBy() {
	      return updatedBy;
    }

    public void setDescription(String desc) {
	      this.description = desc;
    }
    public void setStatus(short status){
	      this.status = status;
    }

    public void setModelIDs(Collection models) {
	      this.modelIDs = models;
    }
    public void setGUID(String guid){
	      this.GUID = guid;
    }
    public void setVersionDate(Date dateVersioned){
	      this.versionDate = dateVersioned;
    }
    public void setVersionBy(String userName){
	      this.versionBy = userName;
    }

    public void setCreationDate(Date dateCreated){
	      this.creationDate = dateCreated;
    }
    public void setCreatedBy(String userName){
	      this.createdBy = userName;
    }
    public void setUpdateDate(Date dateUpdated){
	      this.updateDate = dateUpdated;
    }
    public void setUpdatedBy(String userName){
	      this.updatedBy = userName;
    }
     public void setDataTypeIDs(Collection dataTypeIDs) {
	      this.dataTypeIDs = dataTypeIDs;
    }
     
     public void setHasWSDLDefined(boolean isDefined) {
         this.WSDLDefined = isDefined;
     }
    public void addModelID(ModelID modelID){
        if(modelIDs == null)
            modelIDs = new HashSet();
	    this.modelIDs.add(modelID);
    }
    public void addDataTypeID(DataTypeID dataTypeID){
        if(dataTypeIDs == null)
            dataTypeIDs = new HashSet();
	    this.dataTypeIDs.add(dataTypeID);
    }
    public void update(String attribute, Object value){
        if(this.updatedAttributesList == null)
            this.updatedAttributesList = new HashSet();
        if(attribute.equals(VirtualDatabase.ModifiableAttributes.DESCRIPTION)){
            this.description = (String)value;
            
        }else{
            return;
        }
        this.updatedAttributesList.add(attribute);
    }
    public Collection getUpdatedAttributesList(){
        return this.updatedAttributesList;
    }    

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}

