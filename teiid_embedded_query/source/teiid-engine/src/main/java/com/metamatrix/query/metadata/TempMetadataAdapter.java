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

package com.metamatrix.query.metadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.query.QueryMetadataException;
import com.metamatrix.common.types.DataTypeManager;
import com.metamatrix.query.QueryPlugin;
import com.metamatrix.query.mapping.relational.QueryNode;
import com.metamatrix.query.mapping.xml.MappingNode;

/**
 * <p>This is an adapter class, it contains another instance of 
 * QueryMetadataInterface as well as a TempMetadataStore.  It defers to
 * either one of these when appropriate.</p>
 * 
 * <p>When a metadataID Object is requested for a group or element name, this
 * will first check the QueryMetadataInterface.  If an ID wasn't found there,
 * it will then check the TempMetadataStore.</p>
 * 
 * <p>For methods that take a metadataID arg, this class may check whether it
 * is a TempMetadataID or not and react accordingly.</p>
 */
public class TempMetadataAdapter extends BasicQueryMetadataWrapper {

    private static final String SEPARATOR = "."; //$NON-NLS-1$
    private static final TempMetadataID TEMP_MODEL = new TempMetadataID("__TEMP__", Collections.EMPTY_LIST); //$NON-NLS-1$

    private TempMetadataStore tempStore;
    private Map materializationTables;
    private Map queryNodes;
	
	public TempMetadataAdapter(QueryMetadataInterface metadata, TempMetadataStore tempStore) {
		super(metadata);
        this.tempStore = tempStore;
	}
    
    public TempMetadataAdapter(QueryMetadataInterface metadata, TempMetadataStore tempStore, Map materializationTables, Map queryNodes) {
    	super(metadata);
        this.tempStore = tempStore;
        this.materializationTables = materializationTables;
        this.queryNodes = queryNodes;
    }    
    
    public TempMetadataStore getMetadataStore() {
        return this.tempStore;    
    }
    
    public QueryMetadataInterface getMetadata() {
        return this.actualMetadata;
    }
    
    /**
     * Check metadata first, then check temp groups if not found
     */
    public Object getElementID(String elementName)
        throws MetaMatrixComponentException, QueryMetadataException {

        Object tempID = null;
        try {
            tempID = this.actualMetadata.getElementID(elementName);
        } catch (QueryMetadataException e) {
            //ignore
        }

        if (tempID == null){
            tempID = this.tempStore.getTempElementID(elementName);
        }

        if(tempID != null) {
            return tempID;
        }
        Object[] params = new Object[]{elementName};
        String msg = QueryPlugin.Util.getString("TempMetadataAdapter.Element_____{0}_____not_found._1", params); //$NON-NLS-1$
        throw new QueryMetadataException(msg);
    }
    
    /**
     * Check metadata first, then check temp groups if not found
     */
    public Object getGroupID(String groupName)
        throws MetaMatrixComponentException, QueryMetadataException {
        
        Object tempID = null;
        try {
            tempID = this.actualMetadata.getGroupID(groupName);
        } catch (QueryMetadataException e) {
            //ignore
        }
        
        if (tempID == null){
            tempID = this.tempStore.getTempGroupID(groupName);
        }
        
        if(tempID != null) {
            return tempID;
        }
        Object[] params = new Object[]{groupName};
        String msg = QueryPlugin.Util.getString("TempMetadataAdapter.Group_____{0}_____not_found._1", params); //$NON-NLS-1$
        throw new QueryMetadataException(msg);
    }


    public Object getModelID(Object groupOrElementID)
        throws MetaMatrixComponentException, QueryMetadataException {
		
        if(groupOrElementID instanceof TempMetadataID) {
            return TempMetadataAdapter.TEMP_MODEL;    
        }        
 		return this.actualMetadata.getModelID(groupOrElementID);
	}

	// SPECIAL: Override for temp groups
    public String getFullName(Object metadataID)
        throws MetaMatrixComponentException, QueryMetadataException {
		
		if(metadataID instanceof TempMetadataID) {
			return ((TempMetadataID)metadataID).getID();
		}
		return this.actualMetadata.getFullName(metadataID);
	}    

	// SPECIAL: Override for temp groups
    public List getElementIDsInGroupID(Object groupID)
        throws MetaMatrixComponentException, QueryMetadataException {
		
		if(groupID instanceof TempMetadataID) { 
			return new ArrayList(((TempMetadataID)groupID).getElements());
		}
		return this.actualMetadata.getElementIDsInGroupID(groupID);		
	}

	// SPECIAL: Override for temp groups
    public Object getGroupIDForElementID(Object elementID)
        throws MetaMatrixComponentException, QueryMetadataException {
		
		if(elementID instanceof TempMetadataID) {
			String elementName = ((TempMetadataID)elementID).getID();
			String groupName = elementName.substring(0, elementName.lastIndexOf(SEPARATOR));
			return this.tempStore.getTempGroupID(groupName);
		}	
		return this.actualMetadata.getGroupIDForElementID(elementID);
	}

	// SPECIAL: Override for temp groups
	public String getElementType(Object elementID)
		throws MetaMatrixComponentException, QueryMetadataException {
		
		if(elementID instanceof TempMetadataID) { 
            TempMetadataID tempID = (TempMetadataID)elementID;
            if (tempID.getType() != null) {
                return DataTypeManager.getDataTypeName( tempID.getType() );
            } 
            // If type is null, check element ID stored in temp group store.
            TempMetadataID storedTempID = this.tempStore.getTempElementID(tempID.getID());
            return DataTypeManager.getDataTypeName( storedTempID.getType() );
        }
		return this.actualMetadata.getElementType(elementID);
	}

    public Object getDefaultValue(Object elementID)
        throws MetaMatrixComponentException, QueryMetadataException {
            
        if(elementID instanceof TempMetadataID) {
            return null;
        }
        return this.actualMetadata.getDefaultValue(elementID);
	}	

    /* 
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getMaximumValue(java.lang.Object)
     */
    public Object getMaximumValue(Object elementID) throws MetaMatrixComponentException, QueryMetadataException {
        if (elementID instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)elementID;
            elementID = id.getOriginalMetadataID();
            if (elementID == null) {
                return null;
            }
        }
        return this.actualMetadata.getMaximumValue(elementID);
    }

    /* 
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getMinimumValue(java.lang.Object)
     */
    public Object getMinimumValue(Object elementID) throws MetaMatrixComponentException, QueryMetadataException {
        if (elementID instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)elementID;
            elementID = id.getOriginalMetadataID();
            if (elementID == null) {
                return null;
            }
        }
        return this.actualMetadata.getMinimumValue(elementID);
    }

    /**
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getDistinctValues(java.lang.Object)
     */
    public int getDistinctValues(Object elementID) throws MetaMatrixComponentException, QueryMetadataException {
        if(elementID instanceof TempMetadataID) {
            return -1;
        }         
        return this.actualMetadata.getDistinctValues(elementID);
    }

    /**
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getNullValues(java.lang.Object)
     */
    public int getNullValues(Object elementID) throws MetaMatrixComponentException, QueryMetadataException {
        if (elementID instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)elementID;
            elementID = id.getOriginalMetadataID();
            if (elementID == null) {
                return -1;
            }
        }         
        return this.actualMetadata.getNullValues(elementID);
    }

    public QueryNode getVirtualPlan(Object groupID)
        throws MetaMatrixComponentException, QueryMetadataException {
		
        if (this.queryNodes != null && this.queryNodes.containsKey(groupID)) {
            return (QueryNode)this.queryNodes.get(groupID);
        }
        
        if(groupID instanceof TempMetadataID && !(actualMetadata instanceof TempMetadataAdapter)) {
            return null;
        }            
   		return this.actualMetadata.getVirtualPlan(groupID);
	}
	
	// SPECIAL: Override for temp groups
    public boolean isVirtualGroup(Object groupID)
        throws MetaMatrixComponentException, QueryMetadataException {

		if(groupID instanceof TempMetadataID) {   
			return ((TempMetadataID)groupID).isVirtual();
		}	
		return this.actualMetadata.isVirtualGroup(groupID);
	}

    /** 
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#hasMaterialization(java.lang.Object)
     * @since 4.2
     */
    public boolean hasMaterialization(Object groupID)
        throws MetaMatrixComponentException, QueryMetadataException {
                
        // check if any dynamic materialization tables are defined
        if (this.materializationTables != null && this.materializationTables.containsKey(groupID)) {
            return true;
        }
        
        if(groupID instanceof TempMetadataID) {                         
            return false;
        }   
                
        return this.actualMetadata.hasMaterialization(groupID);
    }
    
    /** 
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getMaterialization(java.lang.Object)
     * @since 4.2
     */
    public Object getMaterialization(Object groupID) 
        throws MetaMatrixComponentException, QueryMetadataException {
        
        // check if any dynamic materialization tables are defined
        if (this.materializationTables != null && this.materializationTables.containsKey(groupID)) {
            return this.materializationTables.get(groupID);
        }
        
        if(groupID instanceof TempMetadataID) {                         
            return null;
        }   

        return this.actualMetadata.getMaterialization(groupID);
    }
    
    /** 
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getMaterializationStage(java.lang.Object)
     * @since 4.2
     */
    public Object getMaterializationStage(Object groupID) 
        throws MetaMatrixComponentException, QueryMetadataException {
        
        if(groupID instanceof TempMetadataID) {                         
            return null;
        }   
        
        // we do not care about the dynamic materialization tables here as they are loaded dynamically.
        return this.actualMetadata.getMaterializationStage(groupID);
    }
    
    public boolean isVirtualModel(Object modelID)
        throws MetaMatrixComponentException, QueryMetadataException {

        if(modelID.equals(TEMP_MODEL)) {                         
            return true;
        }    
        return this.actualMetadata.isVirtualModel(modelID);
    }

	// --------------------- Implement OptimizerMetadata -------------------

	public boolean elementSupports(Object elementID, int supportConstant)
        throws MetaMatrixComponentException, QueryMetadataException {
		
        if (elementID instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)elementID;
            
            switch(supportConstant) {
                case SupportConstants.Element.SEARCHABLE_LIKE:   return true;
                case SupportConstants.Element.SEARCHABLE_COMPARE:return true;
                case SupportConstants.Element.SELECT:            return true;
            }
            
            // If this is a temp table column or real metadata is unknown, return hard-coded values
            elementID = id.getOriginalMetadataID();
            if(elementID == null || id.isTempTable()) {
                switch(supportConstant) {
                    case SupportConstants.Element.NULL:              return true;
                    case SupportConstants.Element.UPDATE:            return true;
                    case SupportConstants.Element.SIGNED:            return true;
                }
                
                return false;
            }
        }
        
        return this.actualMetadata.elementSupports(elementID, supportConstant);
	}

    /**
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getIndexesInGroup(java.lang.Object)
     */
    public Collection getIndexesInGroup(Object groupID)
        throws MetaMatrixComponentException, QueryMetadataException {
        if(groupID instanceof TempMetadataID) {
            return Collections.EMPTY_LIST;
        }
        return this.actualMetadata.getIndexesInGroup(groupID);   
    }

    public Collection getUniqueKeysInGroup(Object groupID) 
        throws MetaMatrixComponentException, QueryMetadataException {
        
        if(groupID instanceof TempMetadataID) {
            return Collections.EMPTY_LIST;
        }
        return this.actualMetadata.getUniqueKeysInGroup(groupID);   
    }
    
    public Collection getForeignKeysInGroup(Object groupID) 
        throws MetaMatrixComponentException, QueryMetadataException {
        
        if(groupID instanceof TempMetadataID) {
            return Collections.EMPTY_LIST;
        }
        return this.actualMetadata.getForeignKeysInGroup(groupID);   
    }    

    /**
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getElementIDsInIndex(java.lang.Object)
     */
    public List getElementIDsInIndex(Object index)
        throws MetaMatrixComponentException, QueryMetadataException {
        return this.actualMetadata.getElementIDsInIndex(index);   
    }
    
    public List getElementIDsInKey(Object keyID) 
        throws MetaMatrixComponentException, QueryMetadataException {
        
        return this.actualMetadata.getElementIDsInKey(keyID);   
    }    

    public boolean groupSupports(Object groupID, int groupConstant)
        throws MetaMatrixComponentException, QueryMetadataException {
            
        if(groupID instanceof TempMetadataID){
            return true;            
        }
        
        return this.actualMetadata.groupSupports(groupID, groupConstant);
    }
    
    public MappingNode getMappingNode(Object groupID)
        throws MetaMatrixComponentException, QueryMetadataException {
            
        return this.actualMetadata.getMappingNode(groupID);
    }   

    public boolean isXMLGroup(Object groupID)
        throws MetaMatrixComponentException, QueryMetadataException {

        if(groupID instanceof TempMetadataID) {
            return false;
        }
        return this.actualMetadata.isXMLGroup(groupID);
    }

    /**
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getVirtualDatabaseName()
     */
    public String getVirtualDatabaseName() 
        throws MetaMatrixComponentException, QueryMetadataException {
            
        return this.actualMetadata.getVirtualDatabaseName();
    }

	/**
	 * @see com.metamatrix.query.metadata.QueryMetadataInterface#getAccessPatternsInGroup(Object)
	 */
	public Collection getAccessPatternsInGroup(Object groupID)
		throws MetaMatrixComponentException, QueryMetadataException {
            
        if(groupID instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)groupID;
            
            return id.getAccessPatterns();
        }
        return this.actualMetadata.getAccessPatternsInGroup(groupID);            
	}

	/**
	 * @see com.metamatrix.query.metadata.QueryMetadataInterface#getElementIDsInAccessPattern(Object)
	 */
	public List getElementIDsInAccessPattern(Object accessPattern)
		throws MetaMatrixComponentException, QueryMetadataException {
        
        if (accessPattern instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)accessPattern;
            if (id.getElements() != null) {
                return id.getElements();
            }
            return Collections.EMPTY_LIST;
        }
        
        return this.actualMetadata.getElementIDsInAccessPattern(accessPattern);
	}

	public Collection getXMLTempGroups(Object groupID) 
        throws MetaMatrixComponentException, QueryMetadataException{
    	
        if(groupID instanceof TempMetadataID) {
            return Collections.EMPTY_SET;
        }
        return this.actualMetadata.getXMLTempGroups(groupID);    
    }
    
    public int getCardinality(Object groupID) 
    	throws MetaMatrixComponentException, QueryMetadataException{
    	
        if(groupID instanceof TempMetadataID) {	
           return ((TempMetadataID)groupID).getCardinality(); 
        }
        return this.actualMetadata.getCardinality(groupID);    
    }

    /* 
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getXMLSchemas(java.lang.Object)
     */
    public List getXMLSchemas(Object groupID) throws MetaMatrixComponentException, QueryMetadataException {
        if(groupID instanceof TempMetadataID) {
            return Collections.EMPTY_LIST;
        }
        return this.actualMetadata.getXMLSchemas(groupID);
    }

    /* 
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getExtensionProperties(java.lang.Object)
     */
    public Properties getExtensionProperties(Object metadataID)
        throws MetaMatrixComponentException, QueryMetadataException {
            
        if(metadataID instanceof TempMetadataID) {
            return null;
        }
        return actualMetadata.getExtensionProperties(metadataID);
    }

    /* (non-Javadoc)
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getElementLength(java.lang.Object)
     */
    public int getElementLength(Object elementID) throws MetaMatrixComponentException, QueryMetadataException {
        if (elementID instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)elementID;
            Object origElementID = id.getOriginalMetadataID();
            if (origElementID == null) {
                String type = getElementType(elementID);
                if(type.equals(DataTypeManager.DefaultDataTypes.STRING)) {
                    return 255;
                }
                return 10;
            } 
            elementID = origElementID;
        }
        
        return actualMetadata.getElementLength(elementID);
    }
    
    public int getPosition(Object elementID) throws MetaMatrixComponentException, QueryMetadataException {
        if (elementID instanceof TempMetadataID) {
            String elementName = ((TempMetadataID)elementID).getID();
            String groupName = elementName.substring(0, elementName.lastIndexOf(SEPARATOR));
            TempMetadataID groupID = this.tempStore.getTempGroupID(groupName);
            List elements = groupID.getElements();
            return elements.indexOf(elementID);
        }
        return actualMetadata.getPosition(elementID);
    }

    public int getPrecision(Object elementID) throws MetaMatrixComponentException, QueryMetadataException {
        if (elementID instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)elementID;
            elementID = id.getOriginalMetadataID();
            if (elementID == null) {
                return 0;
            }
        }
        return actualMetadata.getPrecision(elementID);
    }

    public int getRadix(Object elementID) throws MetaMatrixComponentException, QueryMetadataException {
        if (elementID instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)elementID;
            elementID = id.getOriginalMetadataID();
            if (elementID == null) {
                return 0;
            }
        }
        return actualMetadata.getRadix(elementID);
    }

    public int getScale(Object elementID) throws MetaMatrixComponentException, QueryMetadataException {
        if (elementID instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)elementID;
            elementID = id.getOriginalMetadataID();
            if (elementID == null) {
                return 0;
            }
        }
        return actualMetadata.getScale(elementID);
    }        

    /**
     * Get the native type name for the element. 
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getNativeType(java.lang.Object)
     * @since 4.2
     */
    public String getNativeType(Object elementID) throws MetaMatrixComponentException,
    											QueryMetadataException {
        if (elementID instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)elementID;
            elementID = id.getOriginalMetadataID();
            if (elementID == null) {
                return ""; //$NON-NLS-1$
            }
        }
        
        return actualMetadata.getNativeType(elementID);
    }

	/*
	 * @see com.metamatrix.query.metadata.QueryMetadataInterface#isProcedureInputElement(java.lang.Object)
	 */
	public boolean isProcedure(Object elementID) throws MetaMatrixComponentException, QueryMetadataException {
        if(elementID instanceof TempMetadataID) {
            Object oid = ((TempMetadataID) elementID).getOriginalMetadataID();
            if (oid != null) {
            	return actualMetadata.isProcedure(oid);
            }
        	return false; 
        }
        
        return actualMetadata.isProcedure(elementID);
	}
    
    /** 
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getModeledType(java.lang.Object)
     * @since 5.0
     */
    public String getModeledType(Object elementID) throws MetaMatrixComponentException,
                                                  QueryMetadataException {
        
        if (elementID instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)elementID;
            elementID = id.getOriginalMetadataID();
            if (elementID == null) {
                return null; 
            }
        }
        
        return actualMetadata.getModeledType(elementID);
    }   
    
    /** 
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getModeledBaseType(java.lang.Object)
     * @since 5.0
     */
    public String getModeledBaseType(Object elementID) throws MetaMatrixComponentException,
                                                  QueryMetadataException {
        
        if (elementID instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)elementID;
            elementID = id.getOriginalMetadataID();
            if (elementID == null) {
                return null; 
            }
        }
        
        return actualMetadata.getModeledBaseType(elementID);
    }   
    
    /** 
     * @see com.metamatrix.query.metadata.QueryMetadataInterface#getModeledPrimitiveType(java.lang.Object)
     * @since 5.0
     */
    public String getModeledPrimitiveType(Object elementID) throws MetaMatrixComponentException,
                                                  QueryMetadataException {
        
        if (elementID instanceof TempMetadataID) {
            TempMetadataID id = (TempMetadataID)elementID;
            elementID = id.getOriginalMetadataID();
            if (elementID == null) {
                return null; 
            }
        }
        
        return actualMetadata.getModeledPrimitiveType(elementID);
    }   
    
    public boolean isTemporaryTable(Object groupID) throws MetaMatrixComponentException, QueryMetadataException {
        if(groupID instanceof TempMetadataID) {
            return ((TempMetadataID)groupID).isTempTable();
        }
        //return this.metadata.isTemporaryGroup(groupID);
        return false;
    }
    
    @Override
    public Object addToMetadataCache(Object metadataID, String key, Object value)
    		throws MetaMatrixComponentException, QueryMetadataException {
    	if (metadataID instanceof TempMetadataID) {
    		TempMetadataID tid = (TempMetadataID)metadataID;
    		return tid.setProperty(key, value);
    	}
    	
    	return this.actualMetadata.addToMetadataCache(metadataID, key, value);
    }
    
    @Override
    public Object getFromMetadataCache(Object metadataID, String key)
    		throws MetaMatrixComponentException, QueryMetadataException {
    	if (metadataID instanceof TempMetadataID) {
    		TempMetadataID tid = (TempMetadataID)metadataID;
    		return tid.getProperty(key);
    	}
    	
    	return this.actualMetadata.getFromMetadataCache(metadataID, key);
    }
    
    @Override
    public boolean isScalarGroup(Object groupID)
    		throws MetaMatrixComponentException, QueryMetadataException {
    	if (groupID instanceof TempMetadataID) {
    		TempMetadataID tid = (TempMetadataID)groupID;
    		return tid.isScalarGroup();
    	}
    	
    	return this.actualMetadata.isScalarGroup(groupID);
    }

}
