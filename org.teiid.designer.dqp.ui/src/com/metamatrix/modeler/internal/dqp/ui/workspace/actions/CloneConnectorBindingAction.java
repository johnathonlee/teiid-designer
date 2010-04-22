/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package com.metamatrix.modeler.internal.dqp.ui.workspace.actions;

import java.util.Properties;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.teiid.designer.runtime.Connector;
import com.metamatrix.modeler.dqp.ui.DqpUiConstants;
import com.metamatrix.modeler.dqp.ui.DqpUiPlugin;
import com.metamatrix.modeler.internal.dqp.ui.workspace.dialogs.CloneConnectorBindingDialog;
import com.metamatrix.ui.internal.util.UiUtil;

/**
 * @since 5.0
 */
public final class CloneConnectorBindingAction extends RuntimeAction {

    /**
     * @since 5.0
     */
    public CloneConnectorBindingAction() {
        super(DqpUiConstants.UTIL.getString("CloneConnectorBindingAction.label")); //$NON-NLS-1$
    }

    /**
     * @see org.eclipse.jface.action.IAction#run()
     * @since 5.0
     */
    @Override
    public void run() {
        final Connector connector = (Connector)getSelectedObject();

        if (connector != null) {
            try {
                String clonedConnectorName = getAdmin().ensureUniqueConnectorName(connector.getName());

                CloneConnectorBindingDialog dialog = new CloneConnectorBindingDialog(UiUtil.getWorkbenchShellOnlyIfUiThread(),
                                                                                     clonedConnectorName, connector.getType(),
                                                                                     connector.getProperties()) {

                    /**
                     * @see com.metamatrix.ui.internal.widget.ExtendedTitleAreaDialog#close()
                     * @since 5.5.3
                     */
                    @Override
                    public boolean close() {
                        if (getReturnCode() == Window.OK) {
                            String name = getConnectorName();
                            Properties properties = getConnectorProperties();

                            // System.out.println("  NewConnectorBindingAction.run() ADD BINDING = " + newBinding.getName());
                            try {
                                getAdmin().addConnector(name, connector.getType(), properties);
                            } catch (Exception error) {
                                DqpUiPlugin.showErrorDialog(getShell(), error);
                                return false;
                            }
                        }
                        return super.close();
                    }
                };

                dialog.open();
            } catch (final Exception error) {
                UiUtil.runInSwtThread(new Runnable() {

                    public void run() {
                        DqpUiPlugin.showErrorDialog(Display.getCurrent().getActiveShell(), error);
                    }
                }, false);
            }
        }
    }

    /**
     * @see com.metamatrix.modeler.internal.dqp.ui.workspace.actions.RuntimeAction#setEnablement()
     * @since 5.0
     */
    @Override
    protected void setEnablement() {
        boolean result = false;
        if (!isMultiSelection() && !isEmptySelection()) {
            Object selectedObject = getSelectedObject();
            if (selectedObject instanceof Connector) {
                result = true;
            }
        }

        setEnabled(result);
    }
}
