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

package com.metamatrix.query.optimizer.xml;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import com.metamatrix.query.function.FunctionLibrary;
import com.metamatrix.query.sql.LanguageObject;
import com.metamatrix.query.sql.navigator.PreOrderNavigator;
import com.metamatrix.query.sql.symbol.Expression;
import com.metamatrix.query.sql.symbol.Function;
import com.metamatrix.query.sql.visitor.ExpressionMappingVisitor;

/**
 * <p>Walk a tree of language objects and replaces any usage of 'context' syntax
 * (implemented as a Function) with it's right argument.  The stripped-off
 * 'context' Functions can be gotten as a Collection afterward.</p>
 */
class ContextReplacerVisitor extends ExpressionMappingVisitor {
    
    private Collection contextFunctions = new LinkedList(); //contains Function objects
    
    /**
     * Construct a new visitor
     */
    ContextReplacerVisitor() { 
        super(Collections.EMPTY_MAP);
    }

    /**
     * Get the Collection of Function objects representing context operators
     * which were stripped out of the language object by this visitor
     * @return Collection of Function
     */
    Collection getContextFunctions(){
        return this.contextFunctions;
    }
    
    /** 
     * @see com.metamatrix.query.sql.visitor.ExpressionMappingVisitor#replaceExpression(com.metamatrix.query.sql.symbol.Expression)
     */
    public Expression replaceExpression(Expression exp) {
        if (exp instanceof Function){
            Function function = (Function)exp;
            if (function.getName().equalsIgnoreCase(FunctionLibrary.CONTEXT)){
                this.contextFunctions.add(function);
                //return 2nd argument to 'context'
                return function.getArg(1);
            }
        }
        return exp;
    }

    /**
     * Helper to quickly replace 'context'
     * @param obj Language object
     */
    static final Collection replaceContextFunctions(LanguageObject obj) {
        if (obj == null){
            return Collections.EMPTY_SET;
        }
        ContextReplacerVisitor visitor = new ContextReplacerVisitor();
        PreOrderNavigator.doVisit(obj, visitor);
        return visitor.getContextFunctions();
    }
        	
}
