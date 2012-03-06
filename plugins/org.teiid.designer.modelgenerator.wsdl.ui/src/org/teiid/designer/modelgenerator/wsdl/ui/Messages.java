/*
 * JBoss, Home of Professional Open Source.
*
* See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
*
* See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
*/
package org.teiid.designer.modelgenerator.wsdl.ui;

import org.eclipse.osgi.util.NLS;

public class Messages  extends NLS {


    public static String Add;
    public static String AddAsNewColumn;
    public static String AddAsNewElement;
    public static String AddSelectionAsNewColumn;
    public static String AddSelectionAsNewElement;
    
    public static String Browse;
    
    public static String ColumnInfo;
    public static String ColumnName;
    
    public static String DataType;
    public static String DefaultValue;
    public static String Delete;
    public static String Down;
    
    public static String Edit;
    public static String ElementName;
    public static String ElementInfo;
    
    public static String GeneratedProcedureName;
    public static String GeneratedSQLStatement;
    public static String GenerateWrapperProcedure;
    
    public static String InvalidColumnName;
    
    public static String Location;
    
    public static String Name;
    public static String New;
    
    public static String Operations;
    public static String Options;
    public static String Ordinality;
    
    public static String Path;
    public static String ProcedureDefinition;
    
    public static String Request;
    public static String Response;
    public static String RootPath;
    
    public static String SelectedOperation;
    public static String SetAsRootPath;
    
    public static String Up;
    
    public static String ImportWsdlSoapWizard_title;
    public static String ImportWsdlSoapWizard_importErrorMessage;
    public static String ImportWsdlSoapWizard_initialMessage;
    public static String ImportWsdlSoapWizard_notLicensedMessage;
    public static String ImportWsdlSoapWizard_importError_title;
    public static String ImportWsdlSoapWizard_importError_msg;
   		//=================================================================================================================================
  		// SelectWsdlPage
    public static String WsdlDefinitionPage_title;
    public static String WsdlDefinitionPage_profileLabel_text;
    public static String WsdlDefinitionPage_properties_label;

    public static String WsdlDefinitionPage_dialog_browseWorkspaceWsdl_msg;
    public static String WsdlDefinitionPage_dialog_browseWorkspaceWsdl_title;
    public static String WsdlDefinitionPage_dialog_browseFileSystemWsdl_title;
    public static String WsdlDefinitionPage_dialog_browseFileSystemWsdl_wrongFileType_msg;
    public static String WsdlDefinitionPage_dialog_browseFileSystemWsdl_wrongFileType_title;

    public static String WsdlDefinitionPage_selectionNotWsdl_msg;
    public static String WsdlDefinitionPage_pageComplete_msg;
    public static String WsdlDefinitionPage_noWsdlSelected_msg;
    public static String WsdlDefinitionPage_noWsdlSelected_workspace_msg;
    public static String WsdlDefinitionPage_invalidURLString_msg;
    public static String WsdlDefinitionPage_noURLString_msg;
    public static String WsdlDefinitionPage_urlValidNotReadable_msg;
    public static String WsdlDefinitionPage_sourceOptionsGroup_text;
    public static String WsdlDefinitionPage_wsdlLabel_text;
    public static String WsdlDefinitionPage_workspaceRadio_text;
    public static String WsdlDefinitionPage_workspaceTextField_tooltip;
    public static String WsdlDefinitionPage_workspaceBrowseButton_tooltip;
    public static String WsdlDefinitionPage_fileSystemRadio_text;
    public static String WsdlDefinitionPage_fileSystemTextField_tooltip;
    public static String WsdlDefinitionPage_fileSystemBrowseButton_tooltip;
    public static String WsdlDefinitionPage_urlRadio_text;
    public static String WsdlDefinitionPage_urlTextField_tooltip;
    public static String WsdlDefinitionPage_no_profile_match;

    public static String WsdlDefinitionPage_targetLocationGroup_text;
    public static String WsdlDefinitionPage_targetModelLabel_text;
    public static String WsdlDefinitionPage_targetModelTextField_tooltip;
    public static String WsdlDefinitionPage_targetModelBrowseButton_tooltip;
    public static String WsdlDefinitionPage_targetModelLocationTextField_tooltip;
    public static String WsdlDefinitionPage_targetModelLocationBrowseButton_tooltip;
    public static String WsdlDefinitionPage_validateWsdlButton_text;
    public static String WsdlDefinitionPage_validateWsdlButton_tooltip;
    public static String WsdlDefinitionPage_dialog_browseTargetModel_msg;
    public static String WsdlDefinitionPage_dialog_browseTargetModel_title;
    public static String WsdlDefinitionPage_dialog_wsdlValidationError_title;
    public static String WsdlDefinitionPage_dialog_wsdlValidationError_msg;

    public static String WsdlDefinitionPage_closedProjectMessage;
    public static String WsdlDefinitionPage_invalidFileMessage;
    public static String WsdlDefinitionPage_invalidFolderMessage;
    public static String WsdlDefinitionPage_missingFileMessage;
    public static String WsdlDefinitionPage_missingFolderMessage;
    public static String WsdlDefinitionPage_validateWsdl_msg;
    public static String WsdlDefinitionPage_wsdlErrorContinuation_msg;
    public static String WsdlDefinitionPage_notRelationalModelMessage;
    public static String WsdlDefinitionPage_notModelProjectMessage;
    public static String WsdlDefinitionPage_readOnlyModelMessage;
    public static String WsdlDefinitionPage_virtualModelMessage;
    public static String WsdlDefinitionPage_noModelToUpdateMessage;
    public static String WsdlDefinitionPage_select_profile;
    public static String WsdlDefinitionPage_selectSourceModelTitle;
    public static String WsdlDefinitionPage_selectSourceModelMessage;
    public static String WsdlDefinitionPage_modelStatus;
    public static String WsdlDefinitionPage_sourceModelDefinition;

    //=================================================================================================================================
    // SelectWsdlOperationsPage
    public static String WsdlOperationsPage_title;

    public static String WsdlOperationsPage_pageComplete_msg;
    public static String  WsdlOperationsPage_noSelections_msg;

    public static String WsdlOperationsPage_checkboxTreeGroup_title;
    public static String WsdlOperationsPage_detailsTextbox_title;

    public static String WsdlOperationsPage_selectAllButton_text;
    public static String WsdlOperationsPage_deselectAllButton_text;
    public static String WsdlOperationsPage_selectAllButton_tipText;
    public static String  WsdlOperationsPage_deselectAllButton_tipText;
    public static String WsdlOperationsPage_dialog_wsdlParseError_title;
    public static String WsdlOperationsPage_dialog_wsdlParseError_msg;
    
    static {
        NLS.initializeMessages("org.teiid.designer.modelgenerator.wsdl.ui.messages", Messages.class); //$NON-NLS-1$
    }

    /*
     * EXAMPLE:
     * 
     * NLS.bind(Messages.ModelDoesNotHaveConnectionInfoError, model.getFullPath()), null);
     * 
     */
}
