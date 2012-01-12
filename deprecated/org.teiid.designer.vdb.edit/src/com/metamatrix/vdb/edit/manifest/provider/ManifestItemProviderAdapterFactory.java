/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package com.metamatrix.vdb.edit.manifest.provider;

import java.util.ArrayList;
import java.util.Collection;
import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.edit.provider.ChangeNotifier;
import org.eclipse.emf.edit.provider.ComposeableAdapterFactory;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.edit.provider.IChangeNotifier;
import org.eclipse.emf.edit.provider.IDisposable;
import org.eclipse.emf.edit.provider.IEditingDomainItemProvider;
import org.eclipse.emf.edit.provider.IItemLabelProvider;
import org.eclipse.emf.edit.provider.IItemPropertySource;
import org.eclipse.emf.edit.provider.INotifyChangedListener;
import org.eclipse.emf.edit.provider.IStructuredItemContentProvider;
import org.eclipse.emf.edit.provider.ITreeItemContentProvider;
import com.metamatrix.vdb.edit.manifest.util.ManifestAdapterFactory;

/**
 * This is the factory that is used to provide the interfaces needed to support Viewers.
 * The adapters generated by this factory convert EMF adapter notifications into calls to {@link #fireNotifyChanged fireNotifyChanged}.
 * The adapters also support Eclipse property sheets.
 * Note that most of the adapters are shared among multiple instances.
 * <!-- begin-user-doc -->
 * <!-- end-user-doc -->
 * @generated
 */
public class ManifestItemProviderAdapterFactory extends ManifestAdapterFactory implements ComposeableAdapterFactory, IChangeNotifier, IDisposable {
    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    public static final String copyright = "See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing."; //$NON-NLS-1$

    /**
     * This keeps track of the root adapter factory that delegates to this adapter factory.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    protected ComposedAdapterFactory parentAdapterFactory;

    /**
     * This is used to implement {@link org.eclipse.emf.edit.provider.IChangeNotifier}.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    protected IChangeNotifier changeNotifier = new ChangeNotifier();

    /**
     * This keeps track of all the supported types checked by {@link #isFactoryForType isFactoryForType}.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    protected Collection supportedTypes = new ArrayList();

    /**
     * This constructs an instance.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    public ManifestItemProviderAdapterFactory() {
        supportedTypes.add(IEditingDomainItemProvider.class);
        supportedTypes.add(IStructuredItemContentProvider.class);
        supportedTypes.add(ITreeItemContentProvider.class);
        supportedTypes.add(IItemLabelProvider.class);
        supportedTypes.add(IItemPropertySource.class);		
    }

    /**
     * This keeps track of the one adapter used for all {@link com.metamatrix.vdb.edit.manifest.VirtualDatabase} instances.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    protected VirtualDatabaseItemProvider virtualDatabaseItemProvider;

    /**
     * This creates an adapter for a {@link com.metamatrix.vdb.edit.manifest.VirtualDatabase}.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public Adapter createVirtualDatabaseAdapter() {
        if (virtualDatabaseItemProvider == null) {
            virtualDatabaseItemProvider = new VirtualDatabaseItemProvider(this);
        }

        return virtualDatabaseItemProvider;
    }

    /**
     * This keeps track of the one adapter used for all {@link com.metamatrix.vdb.edit.manifest.ModelReference} instances.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    protected ModelReferenceItemProvider modelReferenceItemProvider;

    /**
     * This creates an adapter for a {@link com.metamatrix.vdb.edit.manifest.ModelReference}.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public Adapter createModelReferenceAdapter() {
        if (modelReferenceItemProvider == null) {
            modelReferenceItemProvider = new ModelReferenceItemProvider(this);
        }

        return modelReferenceItemProvider;
    }

    /**
     * This keeps track of the one adapter used for all {@link com.metamatrix.vdb.edit.manifest.ProblemMarker} instances.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    protected ProblemMarkerItemProvider problemMarkerItemProvider;

    /**
     * This creates an adapter for a {@link com.metamatrix.vdb.edit.manifest.ProblemMarker}.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public Adapter createProblemMarkerAdapter() {
        if (problemMarkerItemProvider == null) {
            problemMarkerItemProvider = new ProblemMarkerItemProvider(this);
        }

        return problemMarkerItemProvider;
    }

    /**
     * This keeps track of the one adapter used for all {@link com.metamatrix.vdb.edit.manifest.ModelSource} instances.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    protected ModelSourceItemProvider modelSourceItemProvider;

    /**
     * This creates an adapter for a {@link com.metamatrix.vdb.edit.manifest.ModelSource}.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public Adapter createModelSourceAdapter() {
        if (modelSourceItemProvider == null) {
            modelSourceItemProvider = new ModelSourceItemProvider(this);
        }

        return modelSourceItemProvider;
    }

    /**
     * This keeps track of the one adapter used for all {@link com.metamatrix.vdb.edit.manifest.ModelSourceProperty} instances.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    protected ModelSourcePropertyItemProvider modelSourcePropertyItemProvider;

    /**
     * This creates an adapter for a {@link com.metamatrix.vdb.edit.manifest.ModelSourceProperty}.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public Adapter createModelSourcePropertyAdapter() {
        if (modelSourcePropertyItemProvider == null) {
            modelSourcePropertyItemProvider = new ModelSourcePropertyItemProvider(this);
        }

        return modelSourcePropertyItemProvider;
    }

    /**
     * This keeps track of the one adapter used for all {@link com.metamatrix.vdb.edit.manifest.WsdlOptions} instances.
     * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
     * @generated
     */
	protected WsdlOptionsItemProvider wsdlOptionsItemProvider;

    /**
     * This creates an adapter for a {@link com.metamatrix.vdb.edit.manifest.WsdlOptions}.
     * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
     * @generated
     */
	@Override
    public Adapter createWsdlOptionsAdapter() {
        if (wsdlOptionsItemProvider == null) {
            wsdlOptionsItemProvider = new WsdlOptionsItemProvider(this);
        }

        return wsdlOptionsItemProvider;
    }

    /**
     * This keeps track of the one adapter used for all {@link com.metamatrix.vdb.edit.manifest.NonModelReference} instances.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    protected NonModelReferenceItemProvider nonModelReferenceItemProvider;

    /**
     * This creates an adapter for a {@link com.metamatrix.vdb.edit.manifest.NonModelReference}.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public Adapter createNonModelReferenceAdapter() {
        if (nonModelReferenceItemProvider == null) {
            nonModelReferenceItemProvider = new NonModelReferenceItemProvider(this);
        }

        return nonModelReferenceItemProvider;
    }

    /**
     * This returns the root adapter factory that contains this factory.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    public ComposeableAdapterFactory getRootAdapterFactory() {
        return parentAdapterFactory == null ? this : parentAdapterFactory.getRootAdapterFactory();
    }

    /**
     * This sets the composed adapter factory that contains this factory.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    public void setParentAdapterFactory(ComposedAdapterFactory parentAdapterFactory) {
        this.parentAdapterFactory = parentAdapterFactory;
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public boolean isFactoryForType(Object type) {
        return supportedTypes.contains(type) || super.isFactoryForType(type);
    }

    /**
     * This implementation substitutes the factory itself as the key for the adapter.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public Adapter adapt(Notifier notifier, Object type) {
        return super.adapt(notifier, this);
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public Object adapt(Object object, Object type) {
        if (isFactoryForType(type)) {
            Object adapter = super.adapt(object, type);
            if (!(type instanceof Class) || (((Class)type).isInstance(adapter))) {
                return adapter;
            }
        }

        return null;
    }

    /**
     * This adds a listener.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    public void addListener(INotifyChangedListener notifyChangedListener) {
        changeNotifier.addListener(notifyChangedListener);
    }

    /**
     * This removes a listener.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    public void removeListener(INotifyChangedListener notifyChangedListener) {
        changeNotifier.removeListener(notifyChangedListener);
    }

    /**
     * This delegates to {@link #changeNotifier} and to {@link #parentAdapterFactory}.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    public void fireNotifyChanged(Notification notification) {
        changeNotifier.fireNotifyChanged(notification);

        if (parentAdapterFactory != null) {
            parentAdapterFactory.fireNotifyChanged(notification);
        }
    }

    /**
     * This disposes all of the item providers created by this factory. 
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    public void dispose() {
        if (virtualDatabaseItemProvider != null) virtualDatabaseItemProvider.dispose();
        if (modelReferenceItemProvider != null) modelReferenceItemProvider.dispose();
        if (problemMarkerItemProvider != null) problemMarkerItemProvider.dispose();
        if (modelSourceItemProvider != null) modelSourceItemProvider.dispose();
        if (modelSourcePropertyItemProvider != null) modelSourcePropertyItemProvider.dispose();
        if (wsdlOptionsItemProvider != null) wsdlOptionsItemProvider.dispose();
        if (nonModelReferenceItemProvider != null) nonModelReferenceItemProvider.dispose();
    }

}