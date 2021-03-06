/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.teiid.designer.core.metamodel.aspect;

import org.eclipse.core.runtime.IPath;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.impl.EObjectImpl;
import org.teiid.designer.core.ModelerCore;

/**
 * AbstractMetamodelAspect
 *
 * @since 8.0
 */
public abstract class AbstractMetamodelAspect implements MetamodelAspect {
    private MetamodelEntity entity;
    private String id;

    public static char DELIMITER_CHAR = '.';

    //==================================================================================
    //                     I N T E R F A C E   M E T H O D S
    //==================================================================================

    /* (non-Javadoc)
     * @See org.teiid.designer.core.metamodel.aspect.MetamodelAspect#getMetamodelEntity()
     */
    @Override
	public MetamodelEntity getMetamodelEntity() {
        return entity;
    }

    /* (non-Javadoc)
     * @See org.teiid.designer.core.metamodel.aspect.MetamodelAspect#getID()
     */
    @Override
	public String getID() {
        return id;
    }

    // ==================================================================================
    //                      P U B L I C   M E T H O D S
    // ==================================================================================

    /**
     * Return the identifier for this EObject instance.
     * @param eObject
     * @return
     */
    public Object getObjectID(final EObject eObject) {
        if(eObject == null) {
            return null;
        }
        return ModelerCore.getObjectId(eObject);
    }

    /**
     * Return the identifier for the parent of this EObject instance.
     * @param eObject
     * @return
     */
    public Object getParentObjectID(final EObject eObject) {
        return (eObject != null ? getObjectID(eObject.eContainer()) : null);
    }

    /**
     * Return the relative path to the EObject within its model.
     * The path includes the name of the model that contains this
     * object.
     * @param eObject
     * @return
     */
    public IPath getPath(final EObject eObject) {
        if(eObject == null) {
            return null;
        }
        return ModelerCore.getModelEditor().getModelRelativePathIncludingModel(eObject);
    }

    /**
     * Return the fully qualified name of the metamodel entity.
     * @param eObject
     * @return The fully qualified name of the entity.
     */
    public String getFullName(final EObject eObject) {
        IPath path = getPath(eObject);
        if(path == null) {
            return null;
        }

        return path.toString().replace(IPath.SEPARATOR, DELIMITER_CHAR);
    }

    // ==================================================================================
    //                    P R O T E C T E D   M E T H O D S
    // ==================================================================================

    protected void setID(final String id){
        this.id = id;
    }

    protected void setMetamodelEntity(final MetamodelEntity entity) {
        this.entity = entity;
    }

    protected EObjectImpl getEObjectImpl(final EObject eObject) {
        if (eObject instanceof EObjectImpl) {
            return (EObjectImpl) eObject;
        }
        return null;
    }


}
