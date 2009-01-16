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

package com.metamatrix.metamodels.transformation.impl;

import java.util.Collection;

import org.eclipse.emf.common.notify.NotificationChain;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.util.InternalEList;
import org.eclipse.emf.mapping.Mapping;
import org.eclipse.emf.mapping.MappingHelper;
import org.eclipse.emf.mapping.MappingPackage;

import com.metamatrix.metamodels.transformation.FragmentMappingRoot;
import com.metamatrix.metamodels.transformation.TransformationPackage;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model object '<em><b>Fragment Mapping Root</b></em>'.
 * <!-- end-user-doc -->
 * <p>
 * </p>
 *
 * @generated
 */
public class FragmentMappingRootImpl extends TransformationMappingRootImpl implements FragmentMappingRoot {
    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    public static final String copyright = "Copyright � 2000-2005 MetaMatrix, Inc.  All rights reserved."; //$NON-NLS-1$

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    protected FragmentMappingRootImpl() {
        super();
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    protected EClass eStaticClass() {
        return TransformationPackage.eINSTANCE.getFragmentMappingRoot();
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public NotificationChain eInverseAdd(InternalEObject otherEnd, int featureID, Class baseClass, NotificationChain msgs) {
        if (featureID >= 0) {
            switch (eDerivedStructuralFeatureID(featureID, baseClass)) {
                case TransformationPackage.FRAGMENT_MAPPING_ROOT__HELPER:
                    if (helper != null)
                        msgs = ((InternalEObject)helper).eInverseRemove(this, EOPPOSITE_FEATURE_BASE - TransformationPackage.FRAGMENT_MAPPING_ROOT__HELPER, null, msgs);
                    return basicSetHelper((MappingHelper)otherEnd, msgs);
                case TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED:
                    return ((InternalEList)getNested()).basicAdd(otherEnd, msgs);
                case TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED_IN:
                    if (eContainer != null)
                        msgs = eBasicRemoveFromContainer(msgs);
                    return eBasicSetContainer(otherEnd, TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED_IN, msgs);
                default:
                    return eDynamicInverseAdd(otherEnd, featureID, baseClass, msgs);
            }
        }
        if (eContainer != null)
            msgs = eBasicRemoveFromContainer(msgs);
        return eBasicSetContainer(otherEnd, featureID, msgs);
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public NotificationChain eInverseRemove(InternalEObject otherEnd, int featureID, Class baseClass, NotificationChain msgs) {
        if (featureID >= 0) {
            switch (eDerivedStructuralFeatureID(featureID, baseClass)) {
                case TransformationPackage.FRAGMENT_MAPPING_ROOT__HELPER:
                    return basicSetHelper(null, msgs);
                case TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED:
                    return ((InternalEList)getNested()).basicRemove(otherEnd, msgs);
                case TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED_IN:
                    return eBasicSetContainer(null, TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED_IN, msgs);
                default:
                    return eDynamicInverseRemove(otherEnd, featureID, baseClass, msgs);
            }
        }
        return eBasicSetContainer(null, featureID, msgs);
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public NotificationChain eBasicRemoveFromContainer(NotificationChain msgs) {
        if (eContainerFeatureID >= 0) {
            switch (eContainerFeatureID) {
                case TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED_IN:
                    return eContainer.eInverseRemove(this, MappingPackage.MAPPING__NESTED, Mapping.class, msgs);
                default:
                    return eDynamicBasicRemoveFromContainer(msgs);
            }
        }
        return eContainer.eInverseRemove(this, EOPPOSITE_FEATURE_BASE - eContainerFeatureID, null, msgs);
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public Object eGet(EStructuralFeature eFeature, boolean resolve) {
        switch (eDerivedStructuralFeatureID(eFeature)) {
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__HELPER:
                return getHelper();
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED:
                return getNested();
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED_IN:
                return getNestedIn();
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__INPUTS:
                return getInputs();
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__OUTPUTS:
                return getOutputs();
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__TYPE_MAPPING:
                if (resolve) return getTypeMapping();
                return basicGetTypeMapping();
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__OUTPUT_READ_ONLY:
                return isOutputReadOnly() ? Boolean.TRUE : Boolean.FALSE;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__TOP_TO_BOTTOM:
                return isTopToBottom() ? Boolean.TRUE : Boolean.FALSE;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__COMMAND_STACK:
                return getCommandStack();
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__TARGET:
                if (resolve) return getTarget();
                return basicGetTarget();
        }
        return eDynamicGet(eFeature, resolve);
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public void eSet(EStructuralFeature eFeature, Object newValue) {
        switch (eDerivedStructuralFeatureID(eFeature)) {
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__HELPER:
                setHelper((MappingHelper)newValue);
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED:
                getNested().clear();
                getNested().addAll((Collection)newValue);
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED_IN:
                setNestedIn((Mapping)newValue);
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__INPUTS:
                getInputs().clear();
                getInputs().addAll((Collection)newValue);
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__OUTPUTS:
                getOutputs().clear();
                getOutputs().addAll((Collection)newValue);
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__TYPE_MAPPING:
                setTypeMapping((Mapping)newValue);
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__OUTPUT_READ_ONLY:
                setOutputReadOnly(((Boolean)newValue).booleanValue());
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__TOP_TO_BOTTOM:
                setTopToBottom(((Boolean)newValue).booleanValue());
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__COMMAND_STACK:
                setCommandStack((String)newValue);
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__TARGET:
                setTarget((EObject)newValue);
                return;
        }
        eDynamicSet(eFeature, newValue);
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public void eUnset(EStructuralFeature eFeature) {
        switch (eDerivedStructuralFeatureID(eFeature)) {
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__HELPER:
                setHelper((MappingHelper)null);
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED:
                getNested().clear();
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED_IN:
                setNestedIn((Mapping)null);
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__INPUTS:
                getInputs().clear();
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__OUTPUTS:
                getOutputs().clear();
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__TYPE_MAPPING:
                setTypeMapping((Mapping)null);
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__OUTPUT_READ_ONLY:
                setOutputReadOnly(OUTPUT_READ_ONLY_EDEFAULT);
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__TOP_TO_BOTTOM:
                setTopToBottom(TOP_TO_BOTTOM_EDEFAULT);
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__COMMAND_STACK:
                setCommandStack(COMMAND_STACK_EDEFAULT);
                return;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__TARGET:
                setTarget((EObject)null);
                return;
        }
        eDynamicUnset(eFeature);
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    @Override
    public boolean eIsSet(EStructuralFeature eFeature) {
        switch (eDerivedStructuralFeatureID(eFeature)) {
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__HELPER:
                return helper != null;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED:
                return nested != null && !nested.isEmpty();
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__NESTED_IN:
                return getNestedIn() != null;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__INPUTS:
                return inputs != null && !inputs.isEmpty();
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__OUTPUTS:
                return outputs != null && !outputs.isEmpty();
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__TYPE_MAPPING:
                return typeMapping != null;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__OUTPUT_READ_ONLY:
                return outputReadOnly != OUTPUT_READ_ONLY_EDEFAULT;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__TOP_TO_BOTTOM:
                return topToBottom != TOP_TO_BOTTOM_EDEFAULT;
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__COMMAND_STACK:
                return COMMAND_STACK_EDEFAULT == null ? commandStack != null : !COMMAND_STACK_EDEFAULT.equals(commandStack);
            case TransformationPackage.FRAGMENT_MAPPING_ROOT__TARGET:
                return target != null;
        }
        return eDynamicIsSet(eFeature);
    }

} //FragmentMappingRootImpl
