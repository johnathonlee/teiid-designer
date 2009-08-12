/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package com.metamatrix.common.tree;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.metamatrix.common.CommonPlugin;
import com.metamatrix.common.util.ErrorMessageKeys;
import com.metamatrix.core.util.ArgCheck;

public class TreeNodeIterator implements Iterator {

    private List startingNodes;
    private TreeView view;
    private TreeNode last;
    private TreeNode next;

    public TreeNodeIterator( List startingNodes, TreeView view ) {
		ArgCheck.isNotNull(startingNodes);
		ArgCheck.isNotNull(view);
        this.startingNodes = startingNodes;
        this.view = view;
        if ( this.startingNodes.size() != 0 ) {
            this.next = (TreeNode) this.startingNodes.get(0);
        } else {
            this.next = null;
        }
        this.last = null;
    }

    public TreeNodeIterator( TreeNode startingNode, TreeView view ) {
    	ArgCheck.isNotNull(startingNode);
    	ArgCheck.isNotNull(view);
        this.startingNodes = new ArrayList(1);
        this.startingNodes.add(startingNode);
        this.view = view;
        this.next = startingNode;
        this.last = null;
    }

    public boolean hasNext() {
        return this.next != null;
    }

    public Object next() {
        return nextPreOrder(false);
    }

    protected Object nextPreOrder( boolean skipChildren ) {
        if ( this.next != null ) {
            this.last = this.next;

            if ( !skipChildren ) {
                // The next node is the first child ...
                List children = this.view.getChildren(this.last);
                if ( children != null && children.size() != 0 ) {
                    this.next = (TreeNode) children.get(0);
                    return this.last;
                }
            }

            // If no children, the next node is the next sibling of this node ...
            this.next = getNextSibling(this.last);
            if ( this.next != null ) {
                return this.last;       // found a next sibling ...
            }

            // If no next sibling, go up to the ancestor that has a next sibling
            TreeNode node = this.last;
            while ( ! isStartingNode(node) ) {
                node = this.view.getParent(node);
                // If there is no parent ...
                if ( node == null ) {
                    break;
                }
                this.next = getNextSibling(node);
                if ( this.next != null ) {
                    return this.last;
                }
            }

            // There's nothing left
            this.next = null;
            return this.last;
        }
        throw new NoSuchElementException(CommonPlugin.Util.getString(ErrorMessageKeys.TREE_ERR_0012));
    }

    public void remove() {
        throw new UnsupportedOperationException(CommonPlugin.Util.getString(ErrorMessageKeys.TREE_ERR_0013));
    }

    protected boolean isStartingNode( TreeNode node ) {
        return this.startingNodes.contains(node);
    }

    protected TreeNode getNextSibling( TreeNode node ) {
        // If the node is one of the starting nodes ...
        if ( this.startingNodes.contains(node) ) {
            int indexOfNode = this.startingNodes.indexOf(node);
            int indexOfNext = indexOfNode + 1;
            if ( indexOfNext != this.startingNodes.size() ) {
                return (TreeNode) this.startingNodes.get(indexOfNext);
            }
            return null;
        }

        TreeNode parent = this.view.getParent(node);
        if ( parent != null ) {
            List siblings = this.view.getChildren(parent);
            int indexOfNode = siblings.indexOf(node);
            int indexOfNext = indexOfNode + 1;
            if ( indexOfNext != siblings.size() ) {
                return (TreeNode) siblings.get(indexOfNext);
            }
        }
        return null;
    }

}

