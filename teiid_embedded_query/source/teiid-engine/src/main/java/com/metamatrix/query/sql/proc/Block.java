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

package com.metamatrix.query.sql.proc;

import java.util.*;
import com.metamatrix.core.util.HashCodeUtil;
import com.metamatrix.query.sql.*;
import com.metamatrix.core.util.EquivalenceUtil;
import com.metamatrix.query.sql.visitor.SQLStringVisitor;

/**
 * <p> This class represents a group of <code>Statement</code> objects. The
 * statements are stored on this object in the order in which they are added.</p>
 */
public class Block  implements LanguageObject {

	// list of statements on this block
	private List statements;

	/**
	 * Constructor for Block.
	 */
	public Block() {
		statements = new ArrayList();
	}

	/**
	 * Constructor for Block with a single <code>Statement</code>.
	 * @param statement The <code>Statement</code> to be added to the block
	 */
	public Block(Statement statement) {
		this();
		statements.add(statement);
	}

	/**
	 * Get all the statements contained on this block.
	 * @return A list of <code>Statement</code>s contained in this block
	 */
	public List getStatements() {
		return statements;
	}

	/**
	 * Set the statements contained on this block.
	 * @param statements A list of <code>Statement</code>s contained in this block
	 */
	public void setStatements(List statements) {
		this.statements = statements;
	}

	/**
	 * Add a <code>Statement</code> to this block.
	 * @param statement The <code>Statement</code> to be added to the block
	 */
	public void addStatement(Statement statement) {
		statements.add(statement);
	}
	
    // =========================================================================
    //                  P R O C E S S I N G     M E T H O D S
    // =========================================================================
        
    public void acceptVisitor(LanguageVisitor visitor) {
        visitor.visit(this);
    }
	
	/**
	 * Deep clone statement to produce a new identical block.
	 * @return Deep clone 
	 */
	public Object clone() {		
		Block copy = new Block();
		if(!statements.isEmpty()) {
			Iterator stmtIter = statements.iterator();
			while(stmtIter.hasNext()) {
				copy.addStatement((Statement) stmtIter.next());
			}
		}
		return copy;
	}
	
    /**
     * Compare two queries for equality.  Blocks will only evaluate to equal if
     * they are IDENTICAL: statements in the block are equal and are in the same order.
     * @param obj Other object
     * @return True if equal
     */
    public boolean equals(Object obj) {
    	// Quick same object test
    	if(this == obj) {
    		return true;
		}

		// Quick fail tests		
    	if(!(obj instanceof Block)) {
    		return false;
		}

		// Compare the statements on the block
        return EquivalenceUtil.areEqual(getStatements(), ((Block)obj).getStatements());
    }    

    /**
     * Get hashcode for block.  WARNING: This hash code relies on the hash codes of the
     * statements present in the block.  If statements are added to the block or if
     * statements on the block change the hash code will change. Hash code is only valid
     * after the block has been completely constructed.
     * @return Hash code
     */
    public int hashCode() {
    	// For speed, this hash code relies only on the hash codes of its select
    	// and criteria clauses, not on the from, order by, or option clauses
    	int myHash = 0;
    	
    	myHash = HashCodeUtil.hashCode(myHash, this.getStatements());
		if(!this.getStatements().isEmpty()) {
			Iterator stmtIter = this.getStatements().iterator();
			while(stmtIter.hasNext()) {
		    	myHash = HashCodeUtil.hashCode(myHash, stmtIter.next());
			}
		}    	
		return myHash;
	}
      
    /**
     * Returns a string representation of an instance of this class.
     * @return String representation of object
     */
    public String toString() {
    	return SQLStringVisitor.getSQLString(this);
    }

}// END CLASS
