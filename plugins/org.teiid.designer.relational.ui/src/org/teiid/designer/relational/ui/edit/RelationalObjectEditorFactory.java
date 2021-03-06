/*
 * JBoss, Home of Professional Open Source.
*
* See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
*
* See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
*/
package org.teiid.designer.relational.ui.edit;

import org.eclipse.core.resources.IFile;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Composite;
import org.teiid.designer.relational.model.RelationalIndex;
import org.teiid.designer.relational.model.RelationalProcedure;
import org.teiid.designer.relational.model.RelationalReference;
import org.teiid.designer.relational.model.RelationalTable;
import org.teiid.designer.relational.model.RelationalView;
import org.teiid.designer.relational.ui.Messages;

/**
 * Factory class designed to provide generic editor panels for specific relational object types
 * 
 * @since 8.0
 */
public class RelationalObjectEditorFactory {

	/**
	 * @param statusListener the status listener for the dialog
	 * @param parent the parent UI panel
	 * @param relationalObject the object to edit
	 * @param modelFile the raw model file
	 * @return the editor panel
	 */
	public static RelationalEditorPanel getEditorPanel( IDialogStatusListener statusListener, 
														Composite parent, 
														RelationalReference relationalObject, 
														IFile modelFile ) {
		if( relationalObject instanceof RelationalTable ) {
			return new RelationalTableEditorPanel(parent, (RelationalTable)relationalObject, modelFile,statusListener);
		} else if( relationalObject instanceof RelationalProcedure ) {
			return new RelationalProcedureEditorPanel(parent, (RelationalProcedure)relationalObject, modelFile,statusListener);
		} else if( relationalObject instanceof RelationalIndex ) {
			return new RelationalIndexEditorPanel(parent, (RelationalIndex)relationalObject, modelFile,statusListener);
		}
		
		return null;
	}
	
	/**
	 * @param relationalObject the object to edit
	 * @return the initial edit dialog message
	 */
	public static String getInitialMessage(RelationalReference relationalObject) {
		if( relationalObject instanceof RelationalView ) {
			return Messages.createRelationalViewInitialMessage;
		} else if( relationalObject instanceof RelationalTable ) {
        	return Messages.createRelationalTableInitialMessage;
        } else if( relationalObject instanceof RelationalProcedure ) {
        	return Messages.createRelationalProcedureInitialMessage;
        } else if( relationalObject instanceof RelationalIndex ) {
        	return Messages.createRelationalIndexInitialMessage;
        }
        
        return NLS.bind(Messages.unsupportedObjectType, relationalObject.getClass().toString());
	}
	
	/**
	 * @param relationalObject the object to edit
	 * @return the initial edit dialog message
	 */
	public static String getDialogTitle(RelationalReference relationalObject) {
		if( relationalObject instanceof RelationalView ) {
			return Messages.createRelationalViewTitle;
		} else if( relationalObject instanceof RelationalTable ) {
        	return Messages.createRelationalTableTitle;
        } else if( relationalObject instanceof RelationalProcedure ) {
        	return Messages.createRelationalProcedureTitle;
        } else if( relationalObject instanceof RelationalIndex ) {
        	return Messages.createRelationalIndexTitle;
        }
        
        return NLS.bind(Messages.unsupportedObjectType, relationalObject.getClass().toString());
	}
	
	/**
	 * @param relationalObject the object to edit
	 * @return the initial edit dialog message
	 */
	public static String getHelpText(RelationalReference relationalObject) {
		if( relationalObject instanceof RelationalView ) {
			return Messages.createRelationalViewHelpText;
		} else if( relationalObject instanceof RelationalTable ) {
        	return Messages.createRelationalTableHelpText;
        } else if( relationalObject instanceof RelationalProcedure ) {
        	return Messages.createRelationalProcedureHelpText;
        } else if( relationalObject instanceof RelationalIndex ) {
        	return Messages.createRelationalIndexHelpText;
        }
        
        return NLS.bind(Messages.unsupportedObjectType, relationalObject.getClass().toString());
	}
}
