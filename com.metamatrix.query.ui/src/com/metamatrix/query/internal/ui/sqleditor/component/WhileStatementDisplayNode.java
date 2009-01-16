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

package com.metamatrix.query.internal.ui.sqleditor.component;

import java.util.ArrayList;

import com.metamatrix.query.sql.ReservedWords;
import com.metamatrix.query.sql.lang.Criteria;
import com.metamatrix.query.sql.proc.Block;
import com.metamatrix.query.sql.proc.WhileStatement;

/**
 * The <code>WhileStatementDisplayNode</code> class is used to represent 
 * an WhileStatement LanguageObject.
 */
public class WhileStatementDisplayNode extends StatementDisplayNode {
	
    ///////////////////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    ///////////////////////////////////////////////////////////////////////////

    /**
     *  WhileStatementDisplayNode constructor
     *  @param parentNode the parent DisplayNode of this.
     *  @param statement The WhileStatement language object used to construct this display node.
     */
    public WhileStatementDisplayNode(DisplayNode parentNode, WhileStatement statement) {
        this.parentNode = parentNode;
        this.languageObject = statement;
        createChildNodes();
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS
    ///////////////////////////////////////////////////////////////////////////

    /**
     *   Create the child nodes for this type of DisplayNode.  For a WhileStatementDisplayNode,
     *  there are two children - (1) The statement condition, (2) the Block.
     */
    private void createChildNodes() {
        childNodeList = new ArrayList();
        WhileStatement statement = (WhileStatement)this.getLanguageObject();

        //----------------------------------------------------
        // Statement Conditional Criteria
        //----------------------------------------------------
        Criteria criteria = statement.getCondition();
        if(criteria!=null) {
            childNodeList.add(DisplayNodeFactory.createDisplayNode(this,criteria));
        }

        //----------------------------------------------------
        // Statement Block
        //----------------------------------------------------
        Block block = statement.getBlock();
        if(block!=null) {
            childNodeList.add(DisplayNodeFactory.createDisplayNode(this,block));
        }

        //----------------------------------------------------
        // Create the Display Node List
        //----------------------------------------------------
        createDisplayNodeList();
    }

    /**
     *   Create the DisplayNode list for this type of DisplayNode.  This is a list of
     *  all the lowest level nodes for this DisplayNode.
     */
    private void createDisplayNodeList() {
        displayNodeList = new ArrayList();

        displayNodeList.add(DisplayNodeFactory.createDisplayNode(this,ReservedWords.WHILE));
        displayNodeList.add(DisplayNodeFactory.createDisplayNode(this,LTPAREN));
        
        // Add the Conditional Criteria
        DisplayNode node = (DisplayNode)childNodeList.get(0);
        if(node.hasDisplayNodes()) {
        	displayNodeList.addAll(node.getDisplayNodeList());
        } else {
        	displayNodeList.add(node);
        }
        
        displayNodeList.add(DisplayNodeFactory.createDisplayNode(this,RTPAREN));
        displayNodeList.add(DisplayNodeFactory.createDisplayNode(this,CR));

        // Add the Block 
		node = (DisplayNode)childNodeList.get(1);
        if(node.hasDisplayNodes()) {
        	displayNodeList.addAll(node.getDisplayNodeList());
        } else {
        	displayNodeList.add(node);
        }
	}    

}
