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

package com.metamatrix.query.sql.visitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.metamatrix.query.sql.LanguageObject;
import com.metamatrix.query.sql.LanguageVisitor;
import com.metamatrix.query.sql.lang.BetweenCriteria;
import com.metamatrix.query.sql.lang.CompareCriteria;
import com.metamatrix.query.sql.lang.DependentSetCriteria;
import com.metamatrix.query.sql.lang.DynamicCommand;
import com.metamatrix.query.sql.lang.GroupBy;
import com.metamatrix.query.sql.lang.Insert;
import com.metamatrix.query.sql.lang.IsNullCriteria;
import com.metamatrix.query.sql.lang.Limit;
import com.metamatrix.query.sql.lang.MatchCriteria;
import com.metamatrix.query.sql.lang.OrderBy;
import com.metamatrix.query.sql.lang.SPParameter;
import com.metamatrix.query.sql.lang.Select;
import com.metamatrix.query.sql.lang.SetClause;
import com.metamatrix.query.sql.lang.SetCriteria;
import com.metamatrix.query.sql.lang.StoredProcedure;
import com.metamatrix.query.sql.lang.SubqueryCompareCriteria;
import com.metamatrix.query.sql.lang.SubquerySetCriteria;
import com.metamatrix.query.sql.navigator.PreOrderNavigator;
import com.metamatrix.query.sql.proc.AssignmentStatement;
import com.metamatrix.query.sql.symbol.AggregateSymbol;
import com.metamatrix.query.sql.symbol.AliasSymbol;
import com.metamatrix.query.sql.symbol.CaseExpression;
import com.metamatrix.query.sql.symbol.Expression;
import com.metamatrix.query.sql.symbol.ExpressionSymbol;
import com.metamatrix.query.sql.symbol.Function;
import com.metamatrix.query.sql.symbol.SearchedCaseExpression;
import com.metamatrix.query.sql.symbol.SingleElementSymbol;

/**
 * It is important to use a Post Navigator with this class, 
 * otherwise a replacement containing itself will not work
 */
public class ExpressionMappingVisitor extends LanguageVisitor {

    private Map symbolMap;

    /**
     * Constructor for ExpressionMappingVisitor.
     * @param symbolMap Map of ElementSymbol to Expression
     */
    public ExpressionMappingVisitor(Map symbolMap) {
        this.symbolMap = symbolMap;
    }
        
    protected boolean createAliases() {
    	return true;
    }
    
    public void visit(Select obj) {
        replaceSymbols(obj.getSymbols(), true);
    }

    private void replaceSymbols(List symbols, boolean alias) {
        for (int i = 0; i < symbols.size(); i++) {
            Object symbol = symbols.get(i);
            
            if (symbol instanceof SingleElementSymbol) {
                SingleElementSymbol ses = (SingleElementSymbol)symbol;
                SingleElementSymbol replacmentSymbol = null; 

                Expression expr = ses;
                if (ses instanceof ExpressionSymbol && !(ses instanceof AggregateSymbol)) {
                    expr = ((ExpressionSymbol)ses).getExpression();
                }
                
                Expression replacement = replaceExpression(expr);
                
                if (replacement instanceof SingleElementSymbol) {
                    replacmentSymbol = (SingleElementSymbol)replacement;
                } else {
                    replacmentSymbol = new ExpressionSymbol(ses.getName(), replacement);
                }
                
                if (alias && createAliases() && !replacmentSymbol.getShortCanonicalName().equals(ses.getShortCanonicalName())) {
                    replacmentSymbol = new AliasSymbol(ses.getShortName(), replacmentSymbol);
                }
                
                symbols.set(i, replacmentSymbol);
            }
        }
    }
    
    /** 
     * @see com.metamatrix.query.sql.LanguageVisitor#visit(com.metamatrix.query.sql.symbol.AliasSymbol)
     */
    public void visit(AliasSymbol obj) {
        Expression replacement = replaceExpression(obj.getSymbol());
        
        if (replacement instanceof SingleElementSymbol) {
            obj.setSymbol((SingleElementSymbol)replacement);
        } else {
            obj.setSymbol(new ExpressionSymbol(obj.getName(), replacement));
        }
    }
    
    public void visit(ExpressionSymbol expr) {
        expr.setExpression(replaceExpression(expr.getExpression()));
    }
    
    /**
     * @see com.metamatrix.query.sql.LanguageVisitor#visit(BetweenCriteria)
     */
    public void visit(BetweenCriteria obj) {
        obj.setExpression( replaceExpression(obj.getExpression()) );
        obj.setLowerExpression( replaceExpression(obj.getLowerExpression()) );
        obj.setUpperExpression( replaceExpression(obj.getUpperExpression()) );
    }
    
    public void visit(CaseExpression obj) {
        obj.setExpression(replaceExpression(obj.getExpression()));
        final int whenCount = obj.getWhenCount();
        ArrayList whens = new ArrayList(whenCount);
        ArrayList thens = new ArrayList(whenCount);
        for (int i = 0; i < whenCount; i++) {
            whens.add(replaceExpression(obj.getWhenExpression(i)));
            thens.add(replaceExpression(obj.getThenExpression(i)));
        }
        obj.setWhen(whens, thens);
        if (obj.getElseExpression() != null) {
            obj.setElseExpression(replaceExpression(obj.getElseExpression()));
        }
    }

    /**
     * @see com.metamatrix.query.sql.LanguageVisitor#visit(CompareCriteria)
     */
    public void visit(CompareCriteria obj) {
        obj.setLeftExpression( replaceExpression(obj.getLeftExpression()) );
        obj.setRightExpression( replaceExpression(obj.getRightExpression()) );
    }

    /**
     * @see com.metamatrix.query.sql.LanguageVisitor#visit(Function)
     */
    public void visit(Function obj) {
        Expression[] args = obj.getArgs();
        if(args != null && args.length > 0) {
            for(int i=0; i<args.length; i++) {
                args[i] = replaceExpression(args[i]);
            }
        }
    }

    /**
     * @see com.metamatrix.query.sql.LanguageVisitor#visit(IsNullCriteria)
     */
    public void visit(IsNullCriteria obj) {
        obj.setExpression( replaceExpression(obj.getExpression()) );
    }

    /**
     * @see com.metamatrix.query.sql.LanguageVisitor#visit(MatchCriteria)
     */
    public void visit(MatchCriteria obj) {
        obj.setLeftExpression( replaceExpression(obj.getLeftExpression()) );
        obj.setRightExpression( replaceExpression(obj.getRightExpression()) );
    }

    public void visit(SearchedCaseExpression obj) {
        int whenCount = obj.getWhenCount();
        ArrayList thens = new ArrayList(whenCount);
        for (int i = 0; i < whenCount; i++) {
            thens.add(replaceExpression(obj.getThenExpression(i)));
        }
        obj.setWhen(obj.getWhen(), thens);
        if (obj.getElseExpression() != null) {
            obj.setElseExpression(replaceExpression(obj.getElseExpression()));
        }
    }

    /**
     * @see com.metamatrix.query.sql.LanguageVisitor#visit(SetCriteria)
     */
    public void visit(SetCriteria obj) {
        obj.setExpression( replaceExpression(obj.getExpression()) );
        
        Collection newValues = new ArrayList(obj.getValues().size());        
        Iterator valueIter = obj.getValues().iterator();
        while(valueIter.hasNext()) {
            newValues.add( replaceExpression( (Expression) valueIter.next() ) );
        }
        
        obj.setValues(newValues);                    
    }

    public void visit(DependentSetCriteria obj) {
        obj.setExpression( replaceExpression(obj.getExpression()) );
    }

    /**
     * @see com.metamatrix.query.sql.LanguageVisitor#visit(com.metamatrix.query.sql.lang.SubqueryCompareCriteria)
     */
    public void visit(SubqueryCompareCriteria obj) {
        obj.setLeftExpression( replaceExpression(obj.getLeftExpression()) );
    }
    
    /**
     * @see com.metamatrix.query.sql.LanguageVisitor#visit(com.metamatrix.query.sql.lang.SubquerySetCriteria)
     */
    public void visit(SubquerySetCriteria obj) {
        obj.setExpression( replaceExpression(obj.getExpression()) );
    }    
    
    public Expression replaceExpression(Expression element) {
        Expression mapped = (Expression) this.symbolMap.get(element);
        if(mapped != null) {
            return mapped;
        }
        return element;    
    }
    
    public void visit(StoredProcedure obj) {
    	for (Iterator paramIter = obj.getInputParameters().iterator(); paramIter.hasNext();) {
			SPParameter param = (SPParameter) paramIter.next();
            Expression expr = param.getExpression();
            param.setExpression(replaceExpression(expr));
        }
    }
    
    public void visit(AggregateSymbol obj) {
    	if (obj.getExpression() != null) { //account for count(*) - TODO: clean this up
    		obj.setExpression(replaceExpression(obj.getExpression()));
    	}
    }
    
    /**
     * Swap each ElementSymbol in GroupBy (other symbols are ignored).
     * @param obj Object to remap
     */
    public void visit(GroupBy obj) {        
        replaceSymbols(obj.getSymbols(), false);
    }
    
    /**
     * Swap each SingleElementSymbol in OrderBy (other symbols are ignored).
     * @param obj Object to remap
     */
    public void visit(OrderBy obj) {
        replaceSymbols(obj.getVariables(), true);        
    }
    
    public void visit(Limit obj) {
        if (obj.getOffset() != null) {
            obj.setOffset(replaceExpression(obj.getOffset()));
        }
        obj.setRowLimit(replaceExpression(obj.getRowLimit()));
    }
       
    public void visit(DynamicCommand obj) {
        obj.setSql(replaceExpression(obj.getSql()));
        if (obj.getUsing() != null) {
	        for (SetClause clause : obj.getUsing().getClauses()) {
				visit(clause);
			}
        }
    }
    
    public void visit(SetClause obj) {
    	obj.setValue(replaceExpression(obj.getValue()));
    }
    
    /**
     * The object is modified in place, so is not returned.
     * @param obj Language object
     * @param exprMap Expression map, Expression to Expression
     */
    public static void mapExpressions(LanguageObject obj, Map exprMap) {
        if(obj == null || exprMap == null || exprMap.isEmpty()) { 
            return;
        }
        final Set reverseSet = new HashSet(exprMap.values());
        final ExpressionMappingVisitor visitor = new ExpressionMappingVisitor(exprMap);
        PreOrderNavigator pon = new PreOrderNavigator(visitor) {
        	@Override
        	protected void visitNode(LanguageObject obj) {
        		if (!(obj instanceof Expression) || !reverseSet.contains(obj)) {
            		super.visitNode(obj);
        		}
        	}
        };
        obj.acceptVisitor(pon);
    }
    
    protected void setVariableValues(Map variableValues) {
        this.symbolMap = variableValues;
    }

    protected Map getVariableValues() {
        return symbolMap;
    }    
    
    /** 
     * @see com.metamatrix.query.sql.LanguageVisitor#visit(com.metamatrix.query.sql.proc.AssignmentStatement)
     * @since 5.0
     */
    public void visit(AssignmentStatement obj) {
        if (obj.hasExpression()) {
            obj.setExpression(replaceExpression(obj.getExpression()));
        }
    }
    
    /** 
     * @see com.metamatrix.query.sql.LanguageVisitor#visit(com.metamatrix.query.sql.lang.Insert)
     * @since 5.0
     */
    public void visit(Insert obj) {
        for (int i = 0; i < obj.getValues().size(); i++) {
            obj.getValues().set(i, replaceExpression((Expression)obj.getValues().get(i)));
        }
    }
    
}
