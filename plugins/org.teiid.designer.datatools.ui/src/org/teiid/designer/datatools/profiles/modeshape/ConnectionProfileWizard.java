package org.teiid.designer.datatools.profiles.modeshape;

import org.eclipse.datatools.connectivity.ui.wizards.ExtensibleNewConnectionProfileWizard;
import org.teiid.designer.datatools.ui.DatatoolsUiConstants;

public class ConnectionProfileWizard extends ExtensibleNewConnectionProfileWizard {

    public ConnectionProfileWizard() {
        super(new  ModeShapeProfileDetailsWizardPage("detailsPage")); //$NON-NLS-1$
		setWindowTitle(DatatoolsUiConstants.UTIL.getString(
				"ConnectionProfileWizard.ModeShape.WizardTitle")); //$NON-NLS-1$
    }
}
