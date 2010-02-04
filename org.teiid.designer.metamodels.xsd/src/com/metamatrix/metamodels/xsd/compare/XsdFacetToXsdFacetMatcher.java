/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package com.metamatrix.metamodels.xsd.compare;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xsd.XSDFacet;
import com.metamatrix.modeler.core.compare.AbstractEObjectNameMatcher;


/** 
 * @since 4.2
 */
public class XsdFacetToXsdFacetMatcher extends AbstractEObjectNameMatcher {

    /** 
     * 
     * @since 4.2
     */
    public XsdFacetToXsdFacetMatcher() {
        super();
    }

    /** 
     * @see com.metamatrix.modeler.core.compare.AbstractEObjectNameMatcher#getInputKey(org.eclipse.emf.ecore.EObject)
     * @since 4.2
     */
    @Override
    protected String getInputKey(EObject entity) {
        if(entity instanceof XSDFacet) {
            XSDFacet facet = (XSDFacet)entity;
            StringBuffer val = new StringBuffer(facet.getFacetName() );
            if(facet.getLexicalValue() != null) {
                val.append(facet.getLexicalValue() );
            }
            return val.toString();
        }
        return null;
    }

    /** 
     * @see com.metamatrix.modeler.core.compare.AbstractEObjectNameMatcher#getOutputKey(org.eclipse.emf.ecore.EObject)
     * @since 4.2
     */
    @Override
    protected String getOutputKey(EObject entity) {
        if(entity instanceof XSDFacet) {
            XSDFacet facet = (XSDFacet)entity;
            StringBuffer val = new StringBuffer(facet.getFacetName() );
            if(facet.getLexicalValue() != null) {
                val.append(facet.getLexicalValue() );
            }
            return val.toString();
        }
        return null;
    }

}
