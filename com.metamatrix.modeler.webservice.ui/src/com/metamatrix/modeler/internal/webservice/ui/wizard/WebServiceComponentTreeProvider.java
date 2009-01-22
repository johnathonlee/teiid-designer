/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package com.metamatrix.modeler.internal.webservice.ui.wizard;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import com.metamatrix.modeler.core.workspace.ModelWorkspaceException;
import com.metamatrix.modeler.core.workspace.ModelWorkspaceItem;
import com.metamatrix.modeler.core.workspace.ModelWorkspaceView;
import com.metamatrix.modeler.internal.ui.viewsupport.ModelUtilities;
import com.metamatrix.modeler.internal.webservice.ui.IInternalUiConstants;

/**
 * ModelWorkspaceTreeProvider is a basic ITreeContentProvider and ILabelProvider ModelWorkspace. It does not allow navigation into
 * a model and works only with IProject and IResource objects. This class does not perform any filtering of the workspace; use a
 * ViewFilter if you wish to do that.
 */
public class WebServiceComponentTreeProvider extends LabelProvider implements ITreeContentProvider, IInternalUiConstants {

    // private static final Object[] NO_CHILDREN = new Object[0];
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    // private ITreeContentProvider modelContentProvider = ModelObjectContentProvider.getInstance();
    ModelWorkspaceView view = new ModelWorkspaceView();
    final WorkbenchLabelProvider workbenchProvider = new WorkbenchLabelProvider();

    /**
     * Construct an instance of ModelWorkspaceTreeProvider.
     */
    public WebServiceComponentTreeProvider() {
        // this.view = new ModelWorkspaceView();
    }

    @Override
    public Image getImage( final Object node ) {
        if (node instanceof EObject) {
            return ModelUtilities.getEMFLabelProvider().getImage(node);
        }
        return workbenchProvider.getImage(((ModelWorkspaceItem)node).getResource());
    }

    @Override
    public String getText( final Object node ) {
        if (node instanceof EObject) {
            return ModelUtilities.getEMFLabelProvider().getText(node);
        }
        return workbenchProvider.getText(((ModelWorkspaceItem)node).getResource());
    }

    @Override
    public void dispose() {
    }

    public Object[] getChildren( final Object node ) {
        try {
            return view.getChildren(node);
        } catch (final ModelWorkspaceException err) {
            UTIL.log(err);
            return EMPTY_STRING_ARRAY;
        }
    }

    public Object[] getElements( final Object inputElement ) {
        return getChildren(inputElement);
    }

    public Object getParent( final Object node ) {
        return view.getParent(node);
    }

    public boolean hasChildren( final Object node ) {
        try {
            return view.hasChildren(node);
        } catch (final ModelWorkspaceException err) {
            UTIL.log(err);
            return false;
        }
    }

    public void inputChanged( final Viewer viewer,
                              final Object oldInput,
                              final Object newInput ) {
    }

}
