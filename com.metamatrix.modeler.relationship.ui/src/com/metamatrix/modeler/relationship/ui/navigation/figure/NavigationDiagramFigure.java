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

package com.metamatrix.modeler.relationship.ui.navigation.figure;

import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.FreeformLayer;
import org.eclipse.draw2d.FreeformLayout;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Rectangle;

import com.metamatrix.modeler.relationship.ui.navigation.part.NavigationLayoutUtil;

/**
 * @author BLaFond
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class NavigationDiagramFigure extends FreeformLayer {

	/**
	 * 
	 */
	public NavigationDiagramFigure() {
		super();
		init();
	}
	
	private void init() {
		setLayoutManager(new FreeformLayout());
		// Don't know why, but if you don't setOpaque(true), you cannot move by drag&drop!
		setOpaque(true);
		setBackgroundColor(ColorConstants.lightGray); 
	}
	
	public void layoutFigure(int viewerDiameter ) {
		// We need to move all figures here to forefront!!!!
		int numContainedItems = 0;
		List containedItems = new Vector();
        
		// This constainer should have NavigationNodeFigure type children
		// Reconcile 

		List childFigures = getChildren();
		Iterator iter = childFigures.iterator();
		Object nextObject = null;
		while( iter.hasNext() ) {
			nextObject = iter.next();
			if( nextObject instanceof NavigationNodeFigure && !containedItems.contains(nextObject) ) {
				containedItems.add(nextObject);
				numContainedItems++;
			}
		}
        
		// Now let's walk through the objects and assume that the container size is fixed.
		
		iter = containedItems.iterator();
		NavigationNodeFigure nextFigure = null;
		int nFigures = containedItems.size();
		int iFigure = 0;
		Rectangle newBounds = null;
		while( iter.hasNext() ) {
			nextFigure = (NavigationNodeFigure)iter.next();
			newBounds = NavigationLayoutUtil.getNextCircularNodePoint(nFigures, iFigure, this.getSize().width, 0, 10);
			((IFigure)nextFigure).setBounds(newBounds);
			iFigure++;
		}
	}

}
