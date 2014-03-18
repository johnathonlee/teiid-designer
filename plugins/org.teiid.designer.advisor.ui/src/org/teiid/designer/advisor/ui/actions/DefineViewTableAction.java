/*
 * JBoss, Home of Professional Open Source.
*
* See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
*
* See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
*/
package org.teiid.designer.advisor.ui.actions;

import java.util.Properties;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IWorkbenchWindow;
import org.teiid.designer.advisor.ui.AdvisorUiConstants;
import org.teiid.designer.advisor.ui.AdvisorUiPlugin;
import org.teiid.designer.transformation.ui.editors.DefineViewTableDialog;
import org.teiid.designer.ui.viewsupport.DesignerProperties;
import org.teiid.designer.ui.viewsupport.DesignerPropertiesUtil;
import org.teiid.designer.ui.viewsupport.IPropertiesContext;
import org.teiid.designer.ui.viewsupport.ModelerUiViewUtils;


/**
 *
 */
public class DefineViewTableAction extends Action implements AdvisorUiConstants {

    private Properties designerProperties;

    /**
     * Construct an instance of NewModelAction.
     */
    public DefineViewTableAction() {
        super();
        setText("Define View Table"); //$NON-NLS-1$
        setToolTipText("Define View Table Tooltip"); //$NON-NLS-1$
        setImageDescriptor(AdvisorUiPlugin.getDefault().getImageDescriptor(Images.NEW_VIRTUAL_TABLE_ICON));

    }
    
    /**
     * @param properties the designer properties
     */
    public DefineViewTableAction( Properties properties ) {
        this();
        this.designerProperties = properties;
    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.action.IAction#run()
     */
    @Override
	public void run() {
    	boolean tempProperties = false;
    	if( this.designerProperties == null ) {
    		tempProperties = true;
    		this.designerProperties = new DesignerProperties("unknown");
    	}
        if( !ModelerUiViewUtils.workspaceHasOpenModelProjects() ) {
        	IProject newProject = ModelerUiViewUtils.queryUserToCreateModelProject();
        	
        	if( newProject != null ) {
        		DesignerPropertiesUtil.setProjectName(designerProperties, newProject.getName());
        	} else {
        		DesignerPropertiesUtil.setProjectStatus(this.designerProperties, IPropertiesContext.NO_OPEN_PROJECT);
        		return;
        	}
        }
    	
    	final IWorkbenchWindow iww = AdvisorUiPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow();
    	
		DefineViewTableDialog sdDialog = new DefineViewTableDialog(iww.getShell(), this.designerProperties);

		sdDialog.open();

		if (sdDialog.getReturnCode() == Window.OK) {
			// Should do nothing since the user can select or create new project or do nothing
			// and the property for the Project will be set in the "designerProperties"
		}
		
		if( tempProperties ) {
			this.designerProperties = null;
		}
    }
}
