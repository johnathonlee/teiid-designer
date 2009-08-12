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

package com.metamatrix.query.processor.relational;

import java.util.Iterator;
import java.util.List;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.MetaMatrixProcessingException;
import com.metamatrix.api.exception.query.ExpressionEvaluationException;
import com.metamatrix.common.buffer.BlockedException;
import com.metamatrix.common.buffer.TupleSourceNotFoundException;
import com.metamatrix.core.util.Assertion;
import com.metamatrix.query.rewriter.QueryRewriter;
import com.metamatrix.query.sql.lang.CompareCriteria;
import com.metamatrix.query.sql.lang.Criteria;
import com.metamatrix.query.sql.lang.IsNullCriteria;
import com.metamatrix.query.sql.symbol.ElementSymbol;
import com.metamatrix.query.sql.symbol.Expression;
import com.metamatrix.query.sql.symbol.Reference;
import com.metamatrix.query.sql.util.VariableContext;

public class DependentProcedureCriteriaProcessor extends DependentCriteriaProcessor {

    private List inputReferences;
    private List inputDefaults;
    private Criteria critInProgress;
    
    public DependentProcedureCriteriaProcessor(RelationalNode dependentNode,
                                               Criteria dependentCriteria,
                                               List references,
                                               List defaults) throws ExpressionEvaluationException, MetaMatrixComponentException {
        super(1, dependentNode, dependentCriteria);
        this.inputDefaults = defaults;
        this.inputReferences = references;
    }
    
    /**
     * @throws TupleSourceNotFoundException
     * @see com.metamatrix.query.processor.relational.PlanExecutionNode#prepareNextCommand()
     */
    protected boolean prepareNextCommand(VariableContext context) throws BlockedException,
                                          MetaMatrixComponentException, MetaMatrixProcessingException {

        if (this.critInProgress == null) {
            critInProgress = prepareCriteria();
        }
        
        for (int j = 0; j < inputReferences.size(); j++) {

            Reference ref = (Reference)inputReferences.get(j);

            context.remove(ref.getExpression());
        }
        
    	if (critInProgress == QueryRewriter.FALSE_CRITERIA) {
    		critInProgress = null;
    		consumedCriteria();
    		return false;
    	}

        boolean validRow = true;

        for (Iterator i = Criteria.separateCriteriaByAnd(critInProgress).iterator(); i.hasNext();) {
            Criteria crit = (Criteria)i.next();

            Object value = null;
            boolean nullAllowed = false;
            Reference parameter = null;

            if (crit instanceof IsNullCriteria) {
                parameter = (Reference)((IsNullCriteria)crit).getExpression();
                nullAllowed = true;
            } else if (crit instanceof CompareCriteria) {
                CompareCriteria compare = (CompareCriteria)crit;
                value = compare.getRightExpression();
                parameter = (Reference)compare.getLeftExpression();
            } else {
                Assertion.failed("Unknown predicate type"); //$NON-NLS-1$
            }

            if (value instanceof Expression) {
                value = eval.evaluate((Expression)value, null);
            }

            if (value == null && !nullAllowed) {
                validRow = false;
                break;
            }

            ElementSymbol parameterSymbol = parameter.getExpression();
            if (context.containsVariable(parameterSymbol)) {
	            Object existingValue = context.getValue(parameterSymbol);
	
	            if ((value != null && !value.equals(existingValue)) || (value == null && existingValue != null)) {
	                validRow = false;
	                break;
	            }
            }

            context.setValue(parameterSymbol, value);
        }

        critInProgress = null;
        consumedCriteria();

        if (!validRow) {
            return false;
        }

        for (int j = 0; j < inputReferences.size(); j++) {
            Object defaultValue = inputDefaults.get(j);

            Reference ref = (Reference)inputReferences.get(j);

            if (defaultValue != null && !context.containsVariable(ref.getExpression())) {
                context.setValue(ref.getExpression(), defaultValue);
            }
        }

        return true;
    }
    
}
