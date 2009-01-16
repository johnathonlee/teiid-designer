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

package com.metamatrix.query.internal.ui.builder.actions;

import org.eclipse.swt.widgets.Composite;

/**
 * The <code>DeleteAction</code> class deletes expressions and criteria found in the builders.
 */
public class AndCriteriaAction extends AbstractButtonAction {

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ///////////////////////////////////////////////////////////////////////////////////////////////
    
    /**
     * Constructs a <code>DeleteAction</code>. Properties are set using the plugin's
     * <code>i18n.properties</code> file.
     * @param theButtonParent the container of the button that is created
     * @param theDeleteHandler the handler that executes the delete method
     */
    public AndCriteriaAction(Composite theButtonParent,
                        Runnable theDeleteHandler) {
        super(theButtonParent, theDeleteHandler);
    }

}
