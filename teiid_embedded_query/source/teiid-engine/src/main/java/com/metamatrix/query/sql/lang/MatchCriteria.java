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

package com.metamatrix.query.sql.lang;

import java.util.Arrays;

import com.metamatrix.api.exception.query.CriteriaEvaluationException;
import com.metamatrix.core.util.EquivalenceUtil;
import com.metamatrix.core.util.HashCodeUtil;
import com.metamatrix.query.QueryPlugin;
import com.metamatrix.query.sql.LanguageVisitor;
import com.metamatrix.query.sql.symbol.Expression;

/**
 * This class represents a criteria involving a string expression to be matched
 * against a string expression match value.  The match value may contain a few
 * special characters: % represents 0 or more characters and _ represents a single
 * match character.  The escape character can be used to escape an actual % or _ within a
 * match string. 
 */
public class MatchCriteria extends PredicateCriteria {

	/** The default wildcard character - '%' */
	public static final char WILDCARD_CHAR = '%';

	/** The default single match character - '_' */
	public static final char MATCH_CHAR = '_';

	/** The left-hand expression. */
	private Expression leftExpression;
	
	/** The right-hand expression. */
	private Expression rightExpression;
    
	/** The internal null escape character */
	public static final char NULL_ESCAPE_CHAR = 0;

	/** The escape character or '' if there is none */
	private char escapeChar = NULL_ESCAPE_CHAR;
	
    /** Negation flag. Indicates whether the criteria expression contains a NOT. */
    private boolean negated = false;
    
    /**
     * Constructs a default instance of this class.
     */
    public MatchCriteria() {}
    
    /**
     * Constructs an instance of this class from a left and right expression
     * 
     * @param leftExpression The expression to check
     * @param rightExpression The match expression
     */
    public MatchCriteria( Expression leftExpression, Expression rightExpression ) {
        setLeftExpression(leftExpression);
        setRightExpression(rightExpression);
    }

    /**
     * Constructs an instance of this class from a left and right expression
     * and an escape character
     * 
      * @param leftExpression The expression to check
     * @param rightExpression The match expression
     * @param escapeChar The escape character, to allow literal use of wildcard and single match chars
     */
    public MatchCriteria( Expression leftExpression, Expression rightExpression, char escapeChar ) {
		this(leftExpression, rightExpression);
        setEscapeChar(escapeChar);
    }

	/**
	 * Set left expression.
	 * @param expression expression
	 */
	public void setLeftExpression(Expression expression) { 
		this.leftExpression = expression;
	}
	
	/**
	 * Get left expression.
	 * @return Left expression
	 */
	public Expression getLeftExpression() {
		return this.leftExpression;
	}

	/**
	 * Set right expression.
	 * @param expression expression
	 */
	public void setRightExpression(Expression expression) { 
		this.rightExpression = expression;
	}
	
	/**
	 * Get right expression.
	 * @return right expression
	 */
	public Expression getRightExpression() {
		return this.rightExpression;
	}

	/**
	 * Get the escape character, which can be placed before the wildcard or single match
	 * character in the expression to prevent it from being used as a wildcard or single
	 * match.  The escape character must not be used elsewhere in the expression.
	 * For example, to match "35%" without activating % as a wildcard, set the escape character
	 * to '$' and use the expression "35$%".	 
	 * @return Escape character, if not set will return {@link #NULL_ESCAPE_CHAR}
	 */
	public char getEscapeChar() {
		return this.escapeChar;
	}

	/**
	 * Set the escape character which can be used when the wildcard or single
	 * character should be used literally.
	 * @param escapeChar New escape character
	 */
	public void setEscapeChar(char escapeChar) {
		this.escapeChar = escapeChar;
	}

    /**
     * Returns whether this criteria is negated.
     * @return flag indicating whether this criteria contains a NOT
     */
    public boolean isNegated() {
        return negated;
    }
    
    /**
     * Sets the negation flag for this criteria.
     * @param negationFlag true if this criteria contains a NOT; false otherwise
     */
    public void setNegated(boolean negationFlag) {
        negated = negationFlag;
    }

    public void acceptVisitor(LanguageVisitor visitor) {
        visitor.visit(this);
    }
	
	/**
	 * Get hash code.  WARNING: The hash code is based on data in the criteria.
	 * If data values are changed, the hash code will change - don't hash this
	 * object and change values.
	 * @return Hash code
	 */
	public int hashCode() {
		int hc = 0;
		hc = HashCodeUtil.hashCode(hc, getLeftExpression());
		hc = HashCodeUtil.hashCode(hc, getRightExpression());
		return hc;
	}
	
    /**
     * Override equals() method.
     * @param obj Other object
     * @return True if equal
     */
    public boolean equals(Object obj) {
    	// Use super.equals() to check obvious stuff and variable
    	if(obj == this) {
			return true;
		}
		
		if(!(obj instanceof MatchCriteria)) {
    		return false;
		}

        MatchCriteria mc = (MatchCriteria)obj;

        if (isNegated() ^ mc.isNegated()) {
            return false;
        }
        
        return getEscapeChar() == mc.getEscapeChar() &&
        EquivalenceUtil.areEqual(getLeftExpression(), mc.getLeftExpression()) &&
        EquivalenceUtil.areEqual(getRightExpression(), mc.getRightExpression());
	}

	/**
	 * Deep copy of object
	 * @return Deep copy of object
	 */
	public Object clone() {
	    Expression leftCopy = null;
	    if(getLeftExpression() != null) { 
	        leftCopy = (Expression) getLeftExpression().clone();
	    }	
	    Expression rightCopy = null;
	    if(getRightExpression() != null) { 
	        rightCopy = (Expression) getRightExpression().clone();
	    }	
	    MatchCriteria criteriaCopy = new MatchCriteria(leftCopy, rightCopy, getEscapeChar());
        criteriaCopy.setNegated(isNegated());
        return criteriaCopy;
	}
	
	/**
     * <p>Utility to convert the pattern into a different match syntax</p>
     */
	public static class PatternTranslator {
	    
	    private char[] reserved;
	    private char newEscape;
	    private String newWildCard;
	    private String newSingleMatch;

	    /**
	     * @param newWildCard replacement for %
	     * @param newSingleMatch replacement for _
	     * @param reserved sorted array of reserved chars in the new match syntax
	     * @param newEscape escape char in the new syntax
	     */
	    public PatternTranslator(String newWildCard, String newSingleMatch, char[] reserved, char newEscape) {
	        this.reserved = reserved;
	        this.newEscape = newEscape;
	        this.newSingleMatch = newSingleMatch;
	        this.newWildCard = newWildCard;
	    }
	    
	    public StringBuffer translate(String pattern, char escape) throws CriteriaEvaluationException {
	        
	        StringBuffer newPattern = new StringBuffer();
	        
	        boolean escaped = false;
	        
	        for (int i = 0; i < pattern.length(); i++) {
	            char character = pattern.charAt(i);
	            
	            if (character == escape && character != NULL_ESCAPE_CHAR) {
	                if (escaped) {
	                    appendCharacter(newPattern, character);
	                    escaped = false;
	                } else {
	                    escaped = true;
	                }
	            } else if (character == MatchCriteria.WILDCARD_CHAR) {
	                if (escaped) {
	                    appendCharacter(newPattern, character);
	                    escaped = false;
	                } else {
	                    newPattern.append(newWildCard);
	                }
	            } else if (character == MatchCriteria.MATCH_CHAR) {
	                if (escaped) {
	                    appendCharacter(newPattern, character);
	                    escaped = false;
	                } else {
	                    newPattern.append(newSingleMatch);
	                }
	            } else {
	                if (escaped) {
	                    throw new CriteriaEvaluationException(QueryPlugin.Util.getString("MatchCriteria.invalid_escape", new Object[] {pattern, new Character(escape)})); //$NON-NLS-1$
	                }
	                appendCharacter(newPattern, character);
	            }
	        }
	        
	        if (escaped) {
	            throw new CriteriaEvaluationException(QueryPlugin.Util.getString("MatchCriteria.invalid_escape", new Object[] {pattern, new Character(escape)})); //$NON-NLS-1$	
	        }
	        
	        return newPattern;
	    }
	    
	    private void appendCharacter(StringBuffer newPattern, char character) {
	        if (Arrays.binarySearch(this.reserved, character) >= 0) {
	            newPattern.append(this.newEscape);
	        } 
	        newPattern.append(character);
	    }
	    
	}
	
}  // END CLASS
