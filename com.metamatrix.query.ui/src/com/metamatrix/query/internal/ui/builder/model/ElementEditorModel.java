/* ================================================================================== 
 * JBoss, Home of Professional Open Source. 
 * 
 * Copyright (c) 2000, 2009 MetaMatrix, Inc. and Red Hat, Inc. 
 * 
 * Some portions of this file may be copyrighted by other 
 * contributors and licensed to Red Hat, Inc. under one or more 
 * contributor license agreements. See the copyright.txt file in the 
 * distribution for a full listing of individual contributors. 
 * 
 * This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html 
 * ================================================================================== */

package com.metamatrix.query.internal.ui.builder.model;

import com.metamatrix.query.internal.ui.builder.util.BuilderUtils;
import com.metamatrix.query.sql.LanguageObject;
import com.metamatrix.query.sql.symbol.ElementSymbol;

/**
 * The <code>ElementEditorModel</code> class is used as a model for the
 * {@link com.metamatrix.query.internal.ui.builder.expression.ElementEditor}.
 */
public class ElementEditorModel extends AbstractLanguageObjectEditorModel {
    // /////////////////////////////////////////////////////////////////////////////////////////////
    // FIELDS
    // /////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The currently selected <code>ElementSymbol</code>.
     */
    private ElementSymbol selectedElement;

    // /////////////////////////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    // /////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs an <code>ElementEditorModel</code> with an incomplete state.
     */
    public ElementEditorModel() {
        super(ElementSymbol.class);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    // METHODS
    // /////////////////////////////////////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see com.metamatrix.query.internal.ui.builder.model.AbstractLanguageObjectEditorModel#clear()
     */
    @Override
    public void clear() {
        selectedElement = null;
        super.clear();
    }

    /**
     * Gets the current value.
     * 
     * @return the currently selected <code>ElementSymbol</code>
     * @throws IllegalStateException if the current value is not complete
     */
    public ElementSymbol getElementSymbol() {
        return (ElementSymbol)getLanguageObject();
    }

    /* (non-Javadoc)
     * @see com.metamatrix.query.internal.ui.builder.model.AbstractLanguageObjectEditorModel#getLanguageObject()
     */
    @Override
    public LanguageObject getLanguageObject() {
        return selectedElement;
    }

    /* (non-Javadoc)
     * @see com.metamatrix.query.internal.ui.builder.model.AbstractLanguageObjectEditorModel#isComplete()
     */
    @Override
    public boolean isComplete() {
        return (selectedElement != null);
    }

    /**
     * Sets the selected or current value.
     * 
     * @param theElement the element becoming the current value
     * @return <code>true</code> if selected element has changed; <code>false</code> otherwise.
     */
    public boolean selectElementSymbol( ElementSymbol theElement ) {
        boolean changed = false;

        if (selectedElement == null) {
            changed = (theElement != null);
        } else {
            changed = (theElement == null) ? true : !selectedElement.equals(theElement);
        }

        if (changed) {
            selectedElement = theElement;

            if (BuilderUtils.isEventLogging()) {
                Util.print(this, "selectElementSymbol:fireModelChanged:type=" + LanguageObjectEditorModelEvent.STATE_CHANGE); //$NON-NLS-1$
            }

            fireModelChanged(LanguageObjectEditorModelEvent.STATE_CHANGE);
        }

        return changed;
    }

    /**
     * Sets the saved value.
     * 
     * @param theElement the element being saved
     */
    private void setElementSymbol( ElementSymbol theElement ) {
        if (theElement == null) {
            clear();
        } else {
            // turn firing of event off for the selectElementSymbol method since we want the
            // event type to be SAVED.
            notifyListeners = false;

            if (selectElementSymbol(theElement)) {
                notifyListeners = true;
                fireModelChanged(LanguageObjectEditorModelEvent.SAVED);
            }

            notifyListeners = true;
        }
    }

    /* (non-Javadoc)
     * @see com.metamatrix.query.internal.ui.builder.model.AbstractLanguageObjectEditorModel#setLanguageObject(com.metamatrix.query.sql.LanguageObject)
     */
    @Override
    public void setLanguageObject( LanguageObject theLangObj ) {
        super.setLanguageObject(theLangObj);
        setElementSymbol((ElementSymbol)theLangObj);
    }

}
