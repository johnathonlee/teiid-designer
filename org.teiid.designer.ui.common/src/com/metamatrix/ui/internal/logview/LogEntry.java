/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package com.metamatrix.ui.internal.logview;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.model.IWorkbenchAdapter;


/** 
 * @since 4.3
 */
public class LogEntry extends PlatformObject implements IWorkbenchAdapter {
    private ArrayList children;
    private LogEntry parent;
    private String pluginId;
    private int severity;
    private int code;
    private String date;
    private String message;
    private String stack;
    private LogSession session;

    public LogEntry() {
    }

    public LogSession getSession() {
        return session;
    }

    void setSession(LogSession session) {
        this.session = session;
    }

    public LogEntry(IStatus status) {
        processStatus(status);
    }
    public int getSeverity() {
        return severity;
    }

    public boolean isOK() {
        return severity == IStatus.OK;
    }
    public int getCode() {
        return code;
    }
    public String getPluginId() {
        return pluginId;
    }
    public String getMessage() {
        return message;
    }
    public String getStack() {
        return stack;
    }
    public String getDate() {
        return date;
    }
    public String getSeverityText() {
        return getSeverityText(severity);
    }
    public boolean hasChildren() {
        return children != null && children.size() > 0;
    }
    @Override
    public String toString() {
        return getSeverityText();
    }
    /**
     * @see IWorkbenchAdapter#getChildren(Object)
     */
    public Object[] getChildren(Object parent) {
        if (children == null)
            return new Object[0];
        return children.toArray();
    }

    /**
     * @see IWorkbenchAdapter#getImageDescriptor(Object)
     */
    public ImageDescriptor getImageDescriptor(Object arg0) {
        return null;
    }

    /**
     * @see IWorkbenchAdapter#getLabel(Object)
     */
    public String getLabel(Object obj) {
        return getSeverityText();
    }

    /**
     * @see IWorkbenchAdapter#getParent(Object)
     */
    public Object getParent(Object obj) {
        return parent;
    }

    void setParent(LogEntry parent) {
        this.parent = parent;
    }

    private String getSeverityText(int severity) {
        switch (severity) {
            case IStatus.ERROR :
                return LogViewMessages.severity_error;
            case IStatus.WARNING :
                return LogViewMessages.severity_warning;
            case IStatus.INFO :
                return LogViewMessages.severity_info;
            case IStatus.OK :
                return LogViewMessages.severity_ok;
        }
        return "?"; //$NON-NLS-1$
    }


    void processEntry(String line) {
        //!ENTRY <pluginID> <severity> <code> <date>
        //!ENTRY <pluginID> <date> if logged by the framework!!!
        StringTokenizer stok = new StringTokenizer(line, " "); //$NON-NLS-1$
        int tokenCount = stok.countTokens();        
        boolean byFrameWork = stok.countTokens() < 5;
        
        if (byFrameWork) {
            severity = 4;
            code = 0;
        }
        StringBuffer dateBuffer = new StringBuffer();
        for (int i = 0; i < tokenCount; i++) {
            String token = stok.nextToken();
            switch (i) {
                case 0:
                    break;
                case 1:
                    pluginId = token;
                    break;
                case 2:
                    if (byFrameWork) {
                        if (dateBuffer.length() > 0)
                            dateBuffer.append(" "); //$NON-NLS-1$
                        dateBuffer.append(token);
                    } else {
                        severity = parseInteger(token);
                    }
                    break;
                case 3:
                    if (byFrameWork) {
                        if (dateBuffer.length() > 0)
                            dateBuffer.append(" "); //$NON-NLS-1$
                        dateBuffer.append(token);
                    } else
                        code = parseInteger(token);
                    break;
                default:
                    if (dateBuffer.length() > 0)
                        dateBuffer.append(" "); //$NON-NLS-1$
                    dateBuffer.append(token);
            }
        }
        date = dateBuffer.toString();
    }
    
    int processSubEntry(String line) {
        //!SUBENTRY <depth> <pluginID> <severity> <code> <date>
        //!SUBENTRY  <depth> <pluginID> <date>if logged by the framework!!!
        StringTokenizer stok = new StringTokenizer(line, " "); //$NON-NLS-1$
        int tokenCount = stok.countTokens();        
        boolean byFrameWork = stok.countTokens() < 5;
        
        StringBuffer dateBuffer = new StringBuffer();
        int depth = 0;
        for (int i = 0; i < tokenCount; i++) {
            String token = stok.nextToken();
            switch (i) {
                case 0:
                    break;
                case 1:
                    depth = parseInteger(token);
                    break;
                case 2:
                    pluginId = token;
                    break;
                case 3:
                    if (byFrameWork) {
                        if (dateBuffer.length() > 0)
                            dateBuffer.append(" "); //$NON-NLS-1$
                        dateBuffer.append(token);
                    } else {
                        severity = parseInteger(token);
                    }
                    break;
                case 4:
                    if (byFrameWork) {
                        if (dateBuffer.length() > 0)
                            dateBuffer.append(" "); //$NON-NLS-1$
                        dateBuffer.append(token);
                    } else
                        code = parseInteger(token);
                    break;
                default:
                    if (dateBuffer.length() > 0)
                        dateBuffer.append(" "); //$NON-NLS-1$
                    dateBuffer.append(token);
            }
        }
        date = dateBuffer.toString();
        return depth;   
    }
    
    private int parseInteger(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    void setStack(String stack) {
        this.stack = stack;
    }
    void setMessage(String message) {
        this.message = message;
    }

    private void processStatus(IStatus status) {
        pluginId = status.getPlugin();
        severity = status.getSeverity();
        code = status.getCode();
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SS"); //$NON-NLS-1$
        date = formatter.format(new Date());
        message = status.getMessage();
        Throwable throwable = status.getException();
        if (throwable != null) {
            StringWriter swriter = new StringWriter();
            PrintWriter pwriter = new PrintWriter(swriter);
            throwable.printStackTrace(pwriter);
            pwriter.flush();
            pwriter.close();
            stack = swriter.toString();
        }
        IStatus[] schildren = status.getChildren();
        if (schildren.length > 0) {
            children = new ArrayList();
            for (int i = 0; i < schildren.length; i++) {
                LogEntry child = new LogEntry(schildren[i]);
                addChild(child);
            }
        }
    }
    void addChild(LogEntry child) {
        if (children == null)
            children = new ArrayList();
        children.add(child);
        child.setParent(this);
    }
    public void write(PrintWriter writer) {
        writer.print(getSeverityText());
        if (date != null) {
            writer.print(" "); //$NON-NLS-1$
            writer.print(getDate());
        }
        if (message != null) {
            writer.print(" "); //$NON-NLS-1$
            writer.print(getMessage());
        }
        writer.println();
        if (stack != null)
            writer.println(stack);
    }
}
