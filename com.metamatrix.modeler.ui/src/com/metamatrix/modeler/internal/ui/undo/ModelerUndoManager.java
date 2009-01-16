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

package com.metamatrix.modeler.internal.ui.undo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.jface.dialogs.MessageDialog;

import com.metamatrix.modeler.core.ModelerCore;
import com.metamatrix.modeler.core.transaction.Undoable;
import com.metamatrix.modeler.core.transaction.UndoableListener;
import com.metamatrix.modeler.core.workspace.ModelResource;
import com.metamatrix.modeler.internal.ui.viewsupport.ModelUtilities;
import com.metamatrix.modeler.ui.UiConstants;
import com.metamatrix.modeler.ui.UiPlugin;
import com.metamatrix.modeler.ui.editors.ModelEditorManager;
import com.metamatrix.modeler.ui.undo.IUndoManager;


/**
 * ModelerUndoManager is the manager of all UndoableEdits that are generated from ModelerCore.
 */
public class ModelerUndoManager implements UndoableListener, IUndoManager {

    // ============================================
    // Constants
    
    private static final String PRESENTATION_NAME = "  <<< Modeler Undo Manager >>> ";  //$NON-NLS-1$

    // ============================================
    // Static Variables

    private static ModelerUndoManager instance = new ModelerUndoManager();
    private static ArrayList listenerList = new ArrayList();

    // ============================================
    // Static Methods

    public static ModelerUndoManager getInstance() {
        return instance;
    }

    public static void addUndoListener(ModelerUndoListener listener) {
        if ( listener != null && ! listenerList.contains(listener) ) {
            listenerList.add(listener);
        }
    }

    public static void removeUndoListener(ModelerUndoListener listener) {
        listenerList.remove(listener);
    }
    
    private static void fireEvent() {
        EventObject event = new EventObject(getInstance());
        for ( Iterator iter = listenerList.iterator() ; iter.hasNext() ; ) {
            ((ModelerUndoListener) iter.next()).processEvent(event);
        }
    }
    
    /**
     * Test hook
     * 
     * @return
     */
    public static int getUndoStackSize() {
        return getInstance().undoStack.size();
    }

    /**
     * Test hook
     * 
     * @return
     */
    public static int getRedoStackSize() {
        return getInstance().redoStack.size();
    }

    // ============================================
    // Instance Variables

    protected LinkedList undoStack;
    protected LinkedList redoStack;
    private HashMap contextMap;
    private ArrayList insignificantToolkitEditIDs;
    private ArrayList insignificantToolkitEdits;
    private ArrayList ignoredToolkitEditIDs;

    private int limit = 200;

    // ============================================
    // Constructors

    /*
     * Create a ModelerUndoManager for the specified WorkspaceController
     * @param controller the WorkspaceController which provides the event channel for this class
     */
    private ModelerUndoManager() {
        undoStack = new LinkedList();
        redoStack = new LinkedList();
        contextMap = new HashMap();
        insignificantToolkitEditIDs = new ArrayList();
        insignificantToolkitEdits = new ArrayList();
        ignoredToolkitEditIDs = new ArrayList();
        
        try {
            ModelerCore.getModelContainer().addUndoableEditListener(this);
        } catch (CoreException e) {
            UiConstants.Util.log(e);
        }
        
    }

    // ============================================
    // Methods

    /**
     * Add the specified UndoableEdit to this UndoManager's undo stack.
     * 
     * @param anEdit an UndoableEdit object
     */
    public synchronized boolean addEdit(Undoable anEdit) {
        Undoable edit = anEdit;
        String newEditString = null;
        
        if( edit.isSignificant() && !edit.canUndo()) {
            newEditString = getEditString("Significant NOT Undoable received. Clearing Undoables.", edit); //$NON-NLS-1$
            discardAllEdits();
            logStacks("addEdit", newEditString);  //$NON-NLS-1$
            fireEvent();
        } else {
            if( edit.canUndo()) {
                undoStack.addFirst(edit);
                newEditString = getEditString("Added", edit); //$NON-NLS-1$
            } else {
                newEditString = getEditString("Edit received but NOT added", edit); //$NON-NLS-1$
            }
            discardAllRedoEdits();
            trimForLimit();
            
            logStacks("addEdit", newEditString);  //$NON-NLS-1$
            fireEvent();
        }

        return true;
    }

    /**
     * Add the specified UndoableEdit to this UndoManager's undo stack and override the edit's isSignificant flag to be false.
     * 
     * @param anEdit an UndoableEdit object
     */
    public synchronized boolean addEditAsNotSignificant(Undoable anEdit) {
        if( anEdit.canUndo()) {
            undoStack.addFirst(anEdit);
            insignificantToolkitEdits.add(anEdit);
            discardAllRedoEdits();
            trimForLimit();
            fireEvent();
            return true;
        }
        
        return false;
    }

    /**
     * Add the specified UndoableEdit to this UndoManager's undo stack.
     * 
     * @param anEdit an UndoableEdit object
     * @param referrableContext an object that can be used later to refer to this and other UndoableEdits
     * @see     #discardContext
     */
    public synchronized boolean addEdit( Undoable anEdit,
                                         Object referrableContext ) {
        addEdit(anEdit);
        Collection c = (Collection) contextMap.get(referrableContext);
        if ( c == null ) {
            c = new ArrayList();
            contextMap.put(referrableContext, c);
        }
        c.add(anEdit);
        trimForLimit();
        return true;
    }

    /**
     * Add the specified UndoableEdit to this UndoManager's undo stack and override the edit's isSignificant flag to be false.
     * 
     * @param anEdit an UndoableEdit object
     * @param referrableContext an object that can be used later to refer to this and other UndoableEdits
     * @see     #discardContext
     */
    public synchronized boolean addEditAsNotSignificant( Undoable anEdit,
                                                         Object referrableContext ) {
        addEditAsNotSignificant(anEdit);
        Collection c = (Collection) contextMap.get(referrableContext);
        if ( c == null ) {
            c = new ArrayList();
            contextMap.put(referrableContext, c);
        }
        c.add(anEdit);
        trimForLimit();
        return true;
    }
    
    /** 
     * @see com.metamatrix.modeler.ui.undo.IUndoManager#canUndo()
     * @since 5.5.3
     */
    public boolean canUndo() {
        Undoable edit = editToBeUndone();
        return edit != null && edit.canUndo();
    }
    
    /** 
     * @see com.metamatrix.modeler.ui.undo.IUndoManager#canRedo()
     * @since 5.5.3
     */
    public boolean canRedo() {
        Undoable edit = editToBeRedone();
        return edit != null && edit.canRedo();
    }

    /**
     * Empty out this Manager's stack of undoable and redoable edits
     * 
     * @see     #discardAllRedoEdits
     */
    public synchronized void discardAllEdits() {
        Iterator iter = undoStack.iterator();
        while ( iter.hasNext() ) {
            Undoable edit = (Undoable) iter.next();
            edit.die();
        }
        undoStack.clear();
        redoStack.clear();
        contextMap.clear();
        insignificantToolkitEditIDs.clear();
        insignificantToolkitEdits.clear();
        fireEvent();
    }
    
    /**
     * Empty out this Manager's stack of undoable and redoable edits and logs the stacks if in debug mode.
     * 
     * @see     #discardAllRedoEdits
     */
    public synchronized void clearAllEdits() {
        discardAllEdits();
        logStacks("addEdit");  //$NON-NLS-1$
    }

    /**
     * Obtain the size limit of this Manager's undo stack
     * 
     * @return the maximum number of undo edits this Manager will record
     * @see     #setLimit
     */
    public synchronized int getLimit() {
        return limit;
    }
    
    /** 
     * @see com.metamatrix.modeler.ui.undo.IUndoManager#getUndoLabel()
     * @since 5.5.3
     */
    public String getUndoLabel() {
        if (canUndo()) {
            return editToBeUndone().getUndoPresentationName();
        }

        return UiConstants.Util.getString("com.metamatrix.modeler.internal.ui.actions.UndoAction.text"); //$NON-NLS-1$
    }
    
    /** 
     * @see com.metamatrix.modeler.ui.undo.IUndoManager#getRedoLabel()
     * @since 5.5.3
     */
    public String getRedoLabel() {
        if (canRedo()) {
            return editToBeRedone().getRedoPresentationName();
        }

        return UiConstants.Util.getString("com.metamatrix.modeler.internal.ui.actions.RedoAction.text"); //$NON-NLS-1$
    }
    
    /** 
     * @see com.metamatrix.modeler.ui.undo.IUndoManager#undo(org.eclipse.core.runtime.IProgressMonitor)
     * @since 5.5.3
     */
    public void undo(IProgressMonitor monitor) {
        logStacks("undo");  //$NON-NLS-1$
        StringBuffer buf = new StringBuffer();
        Undoable edit = editToBeUndone();
        if (edit != null) {
            // Start txn
            boolean requiredStart = ModelerCore.startTxn(false,false,"Undo Edit",this); //$NON-NLS-1$
            boolean succeeded = false;
            try {
                boolean undoNext = true;
                while ( undoNext ) {
                    // pull off the most recent edit and undo it
                    edit = (Undoable) undoStack.removeFirst();
                    
                    try {
                        buf.append(getEditString("Undo", edit)); //$NON-NLS-1$
                        edit.undo();
                        // insure editors are opened for all changed resources
                        openAllEditors(edit.getResources());
                        redoStack.addFirst(edit);
                        // if the edit is not significant, keep going
                        if ( isSignificant(edit) ) {
                            undoNext = false;
                        }
                    } catch (Exception e) {
                        String message = UiConstants.Util.getString("ModelerUndoManager.undoErrorMessage", edit.toString()); //$NON-NLS-1$
                        String title = UiConstants.Util.getString("ModelerUndoManager.undoErrorTitle"); //$NON-NLS-1$
                        MessageDialog.openError(UiPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow().getShell(),
                                                title,
                                                message);
                        UiConstants.Util.log(IStatus.ERROR, e, message);
                        break;
                    }
                }
                succeeded = true;
            } finally {
                // If we start txn, commit it
                if(requiredStart) {
                    if(succeeded) {
                        ModelerCore.commitTxn();
                    } else {
                        ModelerCore.rollbackTxn();
                    }
                }
            }
            fireEvent();
        }
        
        logStacks("Post-undo", buf.toString());  //$NON-NLS-1$
    }
    
    /** 
     * @see com.metamatrix.modeler.ui.undo.IUndoManager#redo(org.eclipse.core.runtime.IProgressMonitor)
     * @since 5.5.3
     */
    public void redo(IProgressMonitor monitor) {
        logStacks("redo");  //$NON-NLS-1$

        // the redo stack operates on insignificant edits in the opposite manner as the undo
        // stack.  The first edit will always be significant (because that's where undo stops),
        // then any insignificant edits will be redone until the next significant is found, at
        // which point we stop.
        Undoable edit = editToBeRedone();
        if (edit != null) {
            // Start txn
            boolean requiredStart = ModelerCore.startTxn(false,false,"Redo Edit",this); //$NON-NLS-1$
            boolean succeeded = false;
            try {
                edit = (Undoable) redoStack.removeFirst();
                boolean redoLast = true;
                while ( redoLast ) {
                    try {
                        // redo this edit and put it on the undo stack
                        edit.redo();
                        undoStack.addFirst(edit);
                        if ( redoStack.isEmpty() ) {
                            redoLast = false;
                        } else {
                            // pull off the next edit and see if we should redo it too
                            edit = (Undoable) redoStack.getFirst();
                            if ( isSignificant(edit) ) {
                                redoLast = false;
                            } else {
                                redoStack.removeFirst();
                            }
                        }
                    } catch (Exception e) {
                        String message = UiConstants.Util.getString("ModelerUndoManager.redoErrorMessage", edit.toString()); //$NON-NLS-1$
                        String title = UiConstants.Util.getString("ModelerUndoManager.redoErrorTitle"); //$NON-NLS-1$
                        MessageDialog.openError(UiPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow().getShell(),
                                                title,
                                                message);
                        UiConstants.Util.log(IStatus.ERROR, e, message);
                        break;
                    }
        
                }
                succeeded = true;
            } finally {
                // If we start txn, commit it
                if(requiredStart) {
                    if(succeeded) {
                        ModelerCore.commitTxn();
                    } else {
                        ModelerCore.rollbackTxn();
                    }
                }
            }
            fireEvent();
        }

        logStacks("Post-redo"); //$NON-NLS-1$
    }

    /**
     * Sets the maximum size limit of this Manager's undo stack and trims the stack accordingly.
     * 
     * @param undoSizeLimit the maximum number of undo edits this Manager will record
     * @see     #getLimit
     */
    public void setLimit(int undoSizeLimit) {
        limit = undoSizeLimit;
        trimForLimit();
    }
    
    private synchronized String getEditString( String prefix,
                                               Undoable edit ) {
        String editString = null;
        if ( UiConstants.Util.isDebugEnabled("modelEditorUndoEvent") ) { //$NON-NLS-1$
            boolean trace = UiConstants.Util.isTraceEnabled("modelEditorUndoEvent"); //$NON-NLS-1$
    
            StringBuffer buf = new StringBuffer("\n   --- >>>> " + prefix + " Edit:  ");  //$NON-NLS-1$ //$NON-NLS-2$
            buf.append("<Sign?= " + isSignificant(edit) + "> ");  //$NON-NLS-1$ //$NON-NLS-2$
            buf.append("<Undo?= " + edit.canUndo() + ">");  //$NON-NLS-1$ //$NON-NLS-2$
            buf.append(" LABEL = " + edit.getPresentationName()); //$NON-NLS-1$
            buf.append(" -->> SOURCE = " + edit.getSource());  //$NON-NLS-1$
            if ( trace ) {
                buf.append('\n');
                buf.append(edit.toString());
            }
            
            editString = buf.toString();
        }
        return editString;
    }

    @Override
    public synchronized String toString() {
        boolean trace = UiConstants.Util.isTraceEnabled("modelEditorUndoEvent"); //$NON-NLS-1$

        StringBuffer buf = new StringBuffer("\n   " + PRESENTATION_NAME + "\n");  //$NON-NLS-1$  //$NON-NLS-2$ 
        buf.append("\n    === Undo Stack ===     Size = " + undoStack.size());  //$NON-NLS-1$
        Iterator iter = undoStack.iterator();
        int i = 0;
        boolean signValue = false;
        boolean doValue   = false;
        while ( iter.hasNext() ) {
            Undoable edit = (Undoable) iter.next();
            signValue = isSignificant(edit);
            doValue = edit.canUndo();
            String booleanValue = null;
            
            if (i < 10) buf.append("\n      " + (i++) + ":"); //$NON-NLS-1$  //$NON-NLS-2$
            else buf.append("\n     " + (i++) + ":"); //$NON-NLS-1$ //$NON-NLS-2$

            if (signValue) booleanValue = "T"; //$NON-NLS-1$
            else booleanValue = "F"; //$NON-NLS-1$
            buf.append("  <Sign? = " + booleanValue + ">");  //$NON-NLS-1$ //$NON-NLS-2$
            if (doValue) booleanValue = "T"; //$NON-NLS-1$
            else booleanValue = "F"; //$NON-NLS-1$
            
            buf.append("  <Undo? = " + booleanValue + ">");  //$NON-NLS-1$ //$NON-NLS-2$
            buf.append(" LABEL = " + edit.getPresentationName()); //$NON-NLS-1$
            if ( trace ) {
                buf.append('\n');
                buf.append(edit.toString());
            }
        }
        buf.append("\n\n    === Redo Stack ===     Size = " + redoStack.size());  //$NON-NLS-1$
        iter = redoStack.iterator();
        i = 0;
        while ( iter.hasNext() ) {
            Undoable edit = (Undoable) iter.next();
            signValue = isSignificant(edit);
            doValue = edit.canRedo();
            String booleanValue = null;
            
            if (i < 10) buf.append("\n      " + (i++) + ":"); //$NON-NLS-1$  //$NON-NLS-2$
            else buf.append("\n     " + (i++) + ":"); //$NON-NLS-1$ //$NON-NLS-2$

            if (signValue) booleanValue = "T"; //$NON-NLS-1$
            else booleanValue = "F"; //$NON-NLS-1$
            buf.append("  <Sign? = " + booleanValue + ">");  //$NON-NLS-1$ //$NON-NLS-2$
            if (doValue) booleanValue = "T"; //$NON-NLS-1$
            else booleanValue = "F"; //$NON-NLS-1$
            
            buf.append("  <Undo? = " + booleanValue + ">");  //$NON-NLS-1$ //$NON-NLS-2$
            buf.append(" LABEL = " + edit.getPresentationName()); //$NON-NLS-1$
            if ( trace ) {
                buf.append('\n');
                buf.append(edit.toString());
            }
        }
        buf.append("\n    -----------------------------------------------\n"); //$NON-NLS-1$
        return buf.toString();
    }

    protected void trimForLimit() {
        while ( undoStack.size() > limit ) {
            undoStack.removeLast();
        }
    }

    /**
     * Returns the the next significant edit to be undone if undo is called. May return null
     */
    protected Undoable editToBeUndone() {
        Undoable result = null;
        Iterator iter = undoStack.iterator();
        while ( iter.hasNext() ) {
            Undoable edit = (Undoable) iter.next();
            if ( isSignificant(edit) ) {
                result = edit;
                break;
            }
        }
        return result;
    }

    /**
     * Returns the the next significant edit to be redone if redo is called. May return null
     */
    protected Undoable editToBeRedone() {
        Undoable result = null;
        Iterator iter = redoStack.iterator();
        while ( iter.hasNext() ) {
            Undoable edit = (Undoable) iter.next();
            if ( isSignificant(edit) ) {
                result = edit;
                break;
            }
        }
        return result;
    }

    /**
     * Remove all Edits from this Manager's stack of undoable edits that were added using the specified context object.
     * 
     * @param referrableContext a context object used in addEdit
     * @see     #addEdit
     */
    public synchronized void discardContext(Object referrableContext) {
        Collection c = (Collection) contextMap.get(referrableContext);
        if ( c != null ) {
            undoStack.removeAll(c);
            discardAllRedoEdits();
            contextMap.remove(referrableContext);
        }
        fireEvent();
    }

    public synchronized void markNotSignificant(Object transactionID) {
        insignificantToolkitEditIDs.add(transactionID);
    }
    
    public synchronized void ignoreUndoableToolkitEdit(Object transactionID) {
        ignoredToolkitEditIDs.add(transactionID);
    }

    /**
     * Empty this Manager's stack of redoable edits.
     * 
     * @see     #discardAllEdits
     */
    public synchronized void discardAllRedoEdits() {
        redoStack.clear();
        fireEvent();
    }

    /**
     * Determine if the specified UndoableEdit is significant.
     */
    private boolean isSignificant(Undoable edit) {
        // get the edit's opinion
        boolean result = edit.isSignificant();
        if ( result ) {
            // see if it's significance has been overridden by anyone
            if ( insignificantToolkitEdits.contains(edit) ) {
                result = false;
            }
        } // if false, who are we to argue?
        return result;
    }

    private void logStacks(String actionName) {
        
        if ( UiConstants.Util.isDebugEnabled("modelEditorUndoEvent") ) { //$NON-NLS-1$
            String message = "ModelerUndoManager." + actionName + "() called\n" + this.toString(); //$NON-NLS-1$ //$NON-NLS-2$

            UiConstants.Util.print("modelEditorUndoEvent", message);  //$NON-NLS-1$
        }
    }
    
    private void logStacks( String actionName,
                            String editString ) {
        
        if ( UiConstants.Util.isDebugEnabled("modelEditorUndoEvent") ) {   //$NON-NLS-1$
            String message = "ModelerUndoManager." +                    //$NON-NLS-1$ 
                actionName + "() called\n" +                            //$NON-NLS-1$ 
                editString + "\n" + this.toString();                    //$NON-NLS-1$ 
            UiConstants.Util.print("modelEditorUndoEvent", message);  //$NON-NLS-1$
        }
        
    }


//    /**
//     * An undoable edit happened via the UndoableEditListener interface
//     */
//    public void undoableEditHappened(UndoableEditEvent e) {
//        addEdit(e.getUndoableEdit());
//    }
//
    // ========================================================
    // UndoableListener Interface



    /* (non-Javadoc)
     * @see com.metamatrix.modeler.core.transaction.UndoableListener#process(com.metamatrix.modeler.core.transaction.Undoable)
     */
    public void process(Undoable event) {
        if ( ignoredToolkitEditIDs.contains(event.getId()) ) {
            return;
        }
        if ( insignificantToolkitEditIDs.contains(event.getId()) ) {
            insignificantToolkitEdits.add(event);
        }
        addEdit(event);

    }

    
    private void openAllEditors(Collection resources) {
    	List changedResources = new ArrayList(resources);
    	Resource nextRes = null;
    	ModelResource mr = null;
    	for( Iterator iter = changedResources.iterator(); iter.hasNext(); ) {
    		nextRes = (Resource)iter.next();
				mr = ModelUtilities.getModelResource(nextRes, true);
    		if( mr != null ) {
    			ModelEditorManager.activate(mr, true);
    		}
    	}
    	
    }
}
