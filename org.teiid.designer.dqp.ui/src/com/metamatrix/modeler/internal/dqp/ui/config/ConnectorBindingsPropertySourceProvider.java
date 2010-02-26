/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package com.metamatrix.modeler.internal.dqp.ui.config;

import java.util.ArrayList;
import java.util.Iterator;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.ui.views.properties.IPropertySource;
import org.eclipse.ui.views.properties.IPropertySourceProvider;
import org.teiid.adminapi.ConnectorBinding;
import org.teiid.designer.runtime.Connector;
import com.metamatrix.modeler.dqp.DqpPlugin;
import com.metamatrix.vdb.internal.edit.InternalVdbEditingContext;
import com.metamatrix.vdb.internal.runtime.model.BasicVDBModelDefn;


/** 
 * @since 4.2
 */
public class ConnectorBindingsPropertySourceProvider implements IPropertySourceProvider {
    
    private final InternalVdbEditingContext vdbContext;
    private boolean editable = false;
    private ArrayList<IPropertyChangeListener> listenerList = new ArrayList<IPropertyChangeListener>();
    private boolean showExpertProps = false;
    
    /** 
     * 
     * @since 4.2
     */
    public ConnectorBindingsPropertySourceProvider(InternalVdbEditingContext theContext) {
        this.vdbContext = theContext;
    }
    
    public void setEditable(boolean isEditable) {
        this.editable = isEditable;
    }
    
    public boolean isEditable() {
        return this.editable;
    }
    
    public void addPropertyChangeListener(IPropertyChangeListener listener) {
        if ( ! listenerList.contains(listener) ) {
            listenerList.add(listener);
        }
    }

    public void removePropertyChangeListener(IPropertyChangeListener listener) {
        listenerList.remove(listener);
    }

    void propertyChanged(Connector binding) {
        for ( Iterator<IPropertyChangeListener> iter = listenerList.iterator() ; iter.hasNext() ; ) {
            iter.next().propertyChange(null);
        }
    }
    
    /** 
     * @see org.eclipse.ui.views.properties.IPropertySourceProvider#getPropertySource(java.lang.Object)
     * @since 4.2
     */
    public IPropertySource getPropertySource(Object object) {
        if ( object instanceof BasicVDBModelDefn ) {
            Connector binding = DqpPlugin.getInstance().getVdbDefnHelper(this.vdbContext).getFirstConnector((BasicVDBModelDefn) object);
            ConnectorBindingsPropertySource source = new ConnectorBindingsPropertySource(binding);
            source.setEditable(editable);
            source.setProvider(this);
            return source;
        } else if ( object instanceof Connector ) {
            ConnectorBindingsPropertySource source = new ConnectorBindingsPropertySource((Connector) object);
            source.setEditable(editable);
            source.setProvider(this);
            return source;
        }
        return null;
    }
    
    /**
     * Sets if the expert properties should be shown or hidden. 
     * @param theShowFlag a flag indicating if the expert properties should be shown
     * @since 5.0.2
     */
    public void setShowExpertProperties(boolean theShowFlag) {
        this.showExpertProps = theShowFlag;
    }
    
    /**
     * Indicates if the expert properties are being shown. 
     * @return <code>true</code> if being shown; <code>false</code> otherwise.
     * @since 5.0.2
     */
    public boolean isShowingExpertProperties() {
        return this.showExpertProps;
    }

}
