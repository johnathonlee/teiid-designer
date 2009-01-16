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

package com.metamatrix.ui.internal.logview;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

import com.metamatrix.ui.internal.PluginImages;

/**
 * @since 4.3
 */
public class LogViewLabelProvider extends LabelProvider implements
                                                       ITableLabelProvider {

    private Image infoImage;
    private Image okImage;
    private Image errorImage;
    private Image warningImage;
    private Image errorWithStackImage;

    public LogViewLabelProvider() {
        errorImage = PluginImages.DESC_ERROR_ST_OBJ.createImage();
        warningImage = PluginImages.DESC_WARNING_ST_OBJ.createImage();
        infoImage = PluginImages.DESC_INFO_ST_OBJ.createImage();
        okImage = PluginImages.DESC_OK_ST_OBJ.createImage();
        errorWithStackImage = PluginImages.DESC_ERROR_STACK_OBJ.createImage();
    }

    @Override
    public void dispose() {
        errorImage.dispose();
        infoImage.dispose();
        okImage.dispose();
        warningImage.dispose();
        errorWithStackImage.dispose();
        super.dispose();
    }

    public Image getColumnImage(Object element,
                                int columnIndex) {
        LogEntry entry = (LogEntry)element;
        if (columnIndex == 0) {
            switch (entry.getSeverity()) {
                case IStatus.INFO:
                    return infoImage;
                case IStatus.OK:
                    return okImage;
                case IStatus.WARNING:
                    return warningImage;
                default:
                    return (entry.getStack() == null ? errorImage : errorWithStackImage);
            }
        }
        return null;
    }

    public String getColumnText(Object element,
                                int columnIndex) {
        LogEntry entry = (LogEntry)element;
        switch (columnIndex) {
            case 0:
                return entry.getMessage() != null ? entry.getMessage() : ""; //$NON-NLS-1$
            case 1:
                return entry.getPluginId() != null ? entry.getPluginId() : ""; //$NON-NLS-1$
            case 2:
                return entry.getDate() != null ? entry.getDate() : ""; //$NON-NLS-1$
        }
        return ""; //$NON-NLS-1$
    }
}
