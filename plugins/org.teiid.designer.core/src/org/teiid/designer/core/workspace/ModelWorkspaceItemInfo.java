/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.teiid.designer.core.workspace;


/**
 * Holds cached structure and properties for a Model Workspace item.
 * Subclassed to carry properties for specific kinds of elements.
 *
 * @since 8.0
 */
public class ModelWorkspaceItemInfo {

    /**
     * Collection of handles of immediate children of this
     * object. This is an empty array if this element has
     * no children.
     */
    protected ModelWorkspaceItem[] fChildren;

    /**
     * Shared empty collection used for efficiency.
     */
    protected static ModelWorkspaceItem[] fgEmptyChildren = new ModelWorkspaceItem[]{};
    /**
     * Is the structure of this element known
     * @see ModelWorkspaceItem#isStructureKnown()
     */
    protected boolean fIsStructureKnown = false;

    /**
     * Shared empty collection used for efficiency.
     */
    static Object[] NO_NON_MODEL_RESOURCES = new Object[] {};    

    protected ModelWorkspaceItemInfo() {
        fChildren = fgEmptyChildren;
    }
    public void addChild(ModelWorkspaceItem child) {
    	if(child != null) {
	        if (fChildren == fgEmptyChildren) {
	            setChildren(new ModelWorkspaceItem[] {child});
	        } else {
	            if (!includesChild(child)) {
	                setChildren(growAndAddToArray(fChildren, child));
	            }
	        }
    	}
    }

    public ModelWorkspaceItem[] getChildren() {
        return fChildren;
    }
    /**
     * Adds the new element to a new array that contains all of the elements of the old array.
     * Returns the new array.
     */
    protected ModelWorkspaceItem[] growAndAddToArray(ModelWorkspaceItem[] array, ModelWorkspaceItem addition) {
        ModelWorkspaceItem[] old = array;
        array = new ModelWorkspaceItem[old.length + 1];
        System.arraycopy(old, 0, array, 0, old.length);
        array[old.length] = addition;
        return array;
    }
    /**
     * Returns <code>true</code> if this child is in my children collection
     */
    protected boolean includesChild(ModelWorkspaceItem child) {
        for (int i= 0; i < fChildren.length; i++) {
            if (fChildren[i].equals(child)) {
                return true;
            }
        }
        return false;
    }
    /**
     * @see ModelWorkspaceItem#isStructureKnown()
     */
    public boolean isStructureKnown() {
        return fIsStructureKnown;
    }
    /**
     * Returns an array with all the same elements as the specified array except for
     * the element to remove. Assumes that the deletion is contained in the array.
     */
    protected ModelWorkspaceItem[] removeAndShrinkArray(ModelWorkspaceItem[] array, ModelWorkspaceItem deletion) {
        ModelWorkspaceItem[] old = array;
        array = new ModelWorkspaceItem[old.length - 1];
        int j = 0;
        for (int i = 0; i < old.length; i++) {
            if (!old[i].equals(deletion)) {
                array[j] = old[i];
            } else {
                System.arraycopy(old, i + 1, array, j, old.length - (i + 1));
                return array;
            }
            j++;
        }
        return array;
    }
    public void removeChild(ModelWorkspaceItem child) {
        if (includesChild(child)) {
            setChildren(removeAndShrinkArray(fChildren, child));
        }
    }
    public void setChildren(ModelWorkspaceItem[] children) {
        fChildren = children;
    }
    /**
     * Sets whether the structure of this element known
     * @see ModelWorkspaceItem#isStructureKnown()
     */
    public void setIsStructureKnown(boolean newIsStructureKnown) {
        fIsStructureKnown = newIsStructureKnown;
    }
}
