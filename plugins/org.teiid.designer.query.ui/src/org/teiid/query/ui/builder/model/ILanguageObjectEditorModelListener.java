/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.teiid.query.ui.builder.model;


/**
 * The <code>ILanguageObjectEditorModelListener</code> class process changes to
 * {@link org.teiid.query.ui.builder.model.ILanguageObjectEditorModel}s.
 *
 * @since 8.0
 */
public interface ILanguageObjectEditorModelListener {
    
    /**
     * Called when a {@link org.teiid.query.ui.builder.model.ILanguageObjectEditorModel}
     * changes.
     * @param theEvent the event being processed
     */
    void modelChanged(LanguageObjectEditorModelEvent theEvent);

}
