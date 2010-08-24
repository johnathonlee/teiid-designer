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

package com.metamatrix.query.internal.ui.sqleditor.component;

import static org.teiid.language.SQLConstants.Reserved.ALL;
import static org.teiid.language.SQLConstants.Reserved.AND;
import static org.teiid.language.SQLConstants.Reserved.AS;
import static org.teiid.language.SQLConstants.Reserved.BEGIN;
import static org.teiid.language.SQLConstants.Reserved.BETWEEN;
import static org.teiid.language.SQLConstants.Reserved.BREAK;
import static org.teiid.language.SQLConstants.Reserved.BY;
import static org.teiid.language.SQLConstants.Reserved.CASE;
import static org.teiid.language.SQLConstants.Reserved.CAST;
import static org.teiid.language.SQLConstants.Reserved.CONTINUE;
import static org.teiid.language.SQLConstants.Reserved.CONVERT;
import static org.teiid.language.SQLConstants.Reserved.CREATE;
import static org.teiid.language.SQLConstants.Reserved.CRITERIA;
import static org.teiid.language.SQLConstants.Reserved.CROSS;
import static org.teiid.language.SQLConstants.Reserved.DECLARE;
import static org.teiid.language.SQLConstants.Reserved.DEFAULT;
import static org.teiid.language.SQLConstants.Reserved.DELETE;
import static org.teiid.language.SQLConstants.Reserved.DESC;
import static org.teiid.language.SQLConstants.Reserved.DISTINCT;
import static org.teiid.language.SQLConstants.Reserved.DROP;
import static org.teiid.language.SQLConstants.Reserved.ELSE;
import static org.teiid.language.SQLConstants.Reserved.END;
import static org.teiid.language.SQLConstants.Reserved.ERROR;
import static org.teiid.language.SQLConstants.Reserved.ESCAPE;
import static org.teiid.language.SQLConstants.Reserved.EXEC;
import static org.teiid.language.SQLConstants.Reserved.EXECUTE;
import static org.teiid.language.SQLConstants.Reserved.EXISTS;
import static org.teiid.language.SQLConstants.Reserved.FALSE;
import static org.teiid.language.SQLConstants.Reserved.FOR;
import static org.teiid.language.SQLConstants.Reserved.FROM;
import static org.teiid.language.SQLConstants.Reserved.FULL;
import static org.teiid.language.SQLConstants.Reserved.GROUP;
import static org.teiid.language.SQLConstants.Reserved.HAS;
import static org.teiid.language.SQLConstants.Reserved.HAVING;
import static org.teiid.language.SQLConstants.Reserved.IF;
import static org.teiid.language.SQLConstants.Reserved.IN;
import static org.teiid.language.SQLConstants.Reserved.INNER;
import static org.teiid.language.SQLConstants.Reserved.INSERT;
import static org.teiid.language.SQLConstants.Reserved.INTO;
import static org.teiid.language.SQLConstants.Reserved.IS;
import static org.teiid.language.SQLConstants.Reserved.JOIN;
import static org.teiid.language.SQLConstants.Reserved.LEFT;
import static org.teiid.language.SQLConstants.Reserved.LIKE;
import static org.teiid.language.SQLConstants.Reserved.LIMIT;
import static org.teiid.language.SQLConstants.Reserved.LOCAL;
import static org.teiid.language.SQLConstants.Reserved.LOOP;
import static org.teiid.language.SQLConstants.Reserved.MAKEDEP;
import static org.teiid.language.SQLConstants.Reserved.MAKENOTDEP;
import static org.teiid.language.SQLConstants.Reserved.NOCACHE;
import static org.teiid.language.SQLConstants.Reserved.NOT;
import static org.teiid.language.SQLConstants.Reserved.NULL;
import static org.teiid.language.SQLConstants.Reserved.ON;
import static org.teiid.language.SQLConstants.Reserved.OPTION;
import static org.teiid.language.SQLConstants.Reserved.OR;
import static org.teiid.language.SQLConstants.Reserved.ORDER;
import static org.teiid.language.SQLConstants.Reserved.OUTER;
import static org.teiid.language.SQLConstants.Reserved.PRIMARY;
import static org.teiid.language.SQLConstants.Reserved.PROCEDURE;
import static org.teiid.language.SQLConstants.Reserved.RIGHT;
import static org.teiid.language.SQLConstants.Reserved.SELECT;
import static org.teiid.language.SQLConstants.Reserved.SET;
import static org.teiid.language.SQLConstants.Reserved.STRING;
import static org.teiid.language.SQLConstants.Reserved.TABLE;
import static org.teiid.language.SQLConstants.Reserved.TEMPORARY;
import static org.teiid.language.SQLConstants.Reserved.THEN;
import static org.teiid.language.SQLConstants.Reserved.TRANSLATE;
import static org.teiid.language.SQLConstants.Reserved.TRUE;
import static org.teiid.language.SQLConstants.Reserved.UNION;
import static org.teiid.language.SQLConstants.Reserved.UNKNOWN;
import static org.teiid.language.SQLConstants.Reserved.UPDATE;
import static org.teiid.language.SQLConstants.Reserved.USING;
import static org.teiid.language.SQLConstants.Reserved.VALUES;
import static org.teiid.language.SQLConstants.Reserved.VIRTUAL;
import static org.teiid.language.SQLConstants.Reserved.WHEN;
import static org.teiid.language.SQLConstants.Reserved.WHERE;
import static org.teiid.language.SQLConstants.Reserved.WHILE;
import static org.teiid.language.SQLConstants.Reserved.WITH;
import static org.teiid.language.SQLConstants.Reserved.XMLATTRIBUTES;
import static org.teiid.language.SQLConstants.Reserved.XMLELEMENT;
import static org.teiid.language.SQLConstants.Reserved.XMLFOREST;
import static org.teiid.language.SQLConstants.Reserved.XMLNAMESPACES;
import static org.teiid.language.SQLConstants.Reserved.XMLPARSE;
import static org.teiid.language.SQLConstants.Reserved.XMLSERIALIZE;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.teiid.core.types.DataTypeManager;
import org.teiid.core.util.StringUtil;
import org.teiid.language.SQLConstants;
import org.teiid.language.SQLConstants.NonReserved;
import org.teiid.language.SQLConstants.Tokens;
import org.teiid.query.sql.LanguageObject;
import org.teiid.query.sql.LanguageVisitor;
import org.teiid.query.sql.lang.AtomicCriteria;
import org.teiid.query.sql.lang.BetweenCriteria;
import org.teiid.query.sql.lang.CacheHint;
import org.teiid.query.sql.lang.CompareCriteria;
import org.teiid.query.sql.lang.CompoundCriteria;
import org.teiid.query.sql.lang.Create;
import org.teiid.query.sql.lang.Criteria;
import org.teiid.query.sql.lang.Delete;
import org.teiid.query.sql.lang.DependentSetCriteria;
import org.teiid.query.sql.lang.Drop;
import org.teiid.query.sql.lang.DynamicCommand;
import org.teiid.query.sql.lang.ExistsCriteria;
import org.teiid.query.sql.lang.ExpressionCriteria;
import org.teiid.query.sql.lang.From;
import org.teiid.query.sql.lang.FromClause;
import org.teiid.query.sql.lang.GroupBy;
import org.teiid.query.sql.lang.Insert;
import org.teiid.query.sql.lang.Into;
import org.teiid.query.sql.lang.IsNullCriteria;
import org.teiid.query.sql.lang.JoinPredicate;
import org.teiid.query.sql.lang.JoinType;
import org.teiid.query.sql.lang.Limit;
import org.teiid.query.sql.lang.MatchCriteria;
import org.teiid.query.sql.lang.NotCriteria;
import org.teiid.query.sql.lang.Option;
import org.teiid.query.sql.lang.OrderBy;
import org.teiid.query.sql.lang.OrderByItem;
import org.teiid.query.sql.lang.PredicateCriteria;
import org.teiid.query.sql.lang.Query;
import org.teiid.query.sql.lang.QueryCommand;
import org.teiid.query.sql.lang.SPParameter;
import org.teiid.query.sql.lang.Select;
import org.teiid.query.sql.lang.SetClause;
import org.teiid.query.sql.lang.SetClauseList;
import org.teiid.query.sql.lang.SetCriteria;
import org.teiid.query.sql.lang.SetQuery;
import org.teiid.query.sql.lang.StoredProcedure;
import org.teiid.query.sql.lang.SubqueryCompareCriteria;
import org.teiid.query.sql.lang.SubqueryFromClause;
import org.teiid.query.sql.lang.SubquerySetCriteria;
import org.teiid.query.sql.lang.TextTable;
import org.teiid.query.sql.lang.UnaryFromClause;
import org.teiid.query.sql.lang.Update;
import org.teiid.query.sql.lang.XMLTable;
import org.teiid.query.sql.lang.TextTable.TextColumn;
import org.teiid.query.sql.lang.XMLTable.XMLColumn;
import org.teiid.query.sql.proc.AssignmentStatement;
import org.teiid.query.sql.proc.Block;
import org.teiid.query.sql.proc.BreakStatement;
import org.teiid.query.sql.proc.CommandStatement;
import org.teiid.query.sql.proc.ContinueStatement;
import org.teiid.query.sql.proc.CreateUpdateProcedureCommand;
import org.teiid.query.sql.proc.CriteriaSelector;
import org.teiid.query.sql.proc.DeclareStatement;
import org.teiid.query.sql.proc.HasCriteria;
import org.teiid.query.sql.proc.IfStatement;
import org.teiid.query.sql.proc.LoopStatement;
import org.teiid.query.sql.proc.RaiseErrorStatement;
import org.teiid.query.sql.proc.Statement;
import org.teiid.query.sql.proc.TranslateCriteria;
import org.teiid.query.sql.proc.WhileStatement;
import org.teiid.query.sql.symbol.AggregateSymbol;
import org.teiid.query.sql.symbol.AliasSymbol;
import org.teiid.query.sql.symbol.AllInGroupSymbol;
import org.teiid.query.sql.symbol.AllSymbol;
import org.teiid.query.sql.symbol.CaseExpression;
import org.teiid.query.sql.symbol.Constant;
import org.teiid.query.sql.symbol.DerivedColumn;
import org.teiid.query.sql.symbol.ElementSymbol;
import org.teiid.query.sql.symbol.Expression;
import org.teiid.query.sql.symbol.ExpressionSymbol;
import org.teiid.query.sql.symbol.Function;
import org.teiid.query.sql.symbol.GroupSymbol;
import org.teiid.query.sql.symbol.QueryString;
import org.teiid.query.sql.symbol.Reference;
import org.teiid.query.sql.symbol.ScalarSubquery;
import org.teiid.query.sql.symbol.SearchedCaseExpression;
import org.teiid.query.sql.symbol.SelectSymbol;
import org.teiid.query.sql.symbol.SingleElementSymbol;
import org.teiid.query.sql.symbol.XMLAttributes;
import org.teiid.query.sql.symbol.XMLElement;
import org.teiid.query.sql.symbol.XMLForest;
import org.teiid.query.sql.symbol.XMLNamespaces;
import org.teiid.query.sql.symbol.XMLParse;
import org.teiid.query.sql.symbol.XMLQuery;
import org.teiid.query.sql.symbol.XMLSerialize;
import org.teiid.query.sql.symbol.XMLNamespaces.NamespaceItem;
import org.teiid.translator.SourceSystemFunctions;

/**
 * <p>
 * The SQLStringVisitor will visit a set of language objects and return the corresponding SQL string representation.
 * </p>
 */
public class SQLStringVisitor extends LanguageVisitor {

    public static final String UNDEFINED = "<undefined>"; //$NON-NLS-1$
    private static final String SPACE = " "; //$NON-NLS-1$
    private static final String BEGIN_HINT = "/*+"; //$NON-NLS-1$
    private static final String END_HINT = "*/"; //$NON-NLS-1$
    private static final char ID_ESCAPE_CHAR = '\"';

    protected StringBuilder parts = new StringBuilder();

    /**
     * Helper to quickly get the parser string for an object using the visitor.
     * 
     * @param obj Language object
     * @return String SQL String for obj
     */
    public static final String getSQLString( LanguageObject obj ) {
        if (obj == null) {
            return UNDEFINED;
        }
        SQLStringVisitor visitor = new SQLStringVisitor();
        obj.acceptVisitor(visitor);
        return visitor.getSQLString();
    }

    /**
     * Retrieve completed string from the visitor.
     * 
     * @return Complete SQL string for the visited nodes
     */
    public String getSQLString() {
        return this.parts.toString();
    }

    protected void visitNode( LanguageObject obj ) {
        if (obj == null) {
            append(UNDEFINED);
            return;
        }
        obj.acceptVisitor(this);
    }

    protected void append( Object value ) {
        this.parts.append(value);
    }

    protected void beginClause( int level ) {
        append(SPACE);
    }

    // ############ Visitor methods for language objects ####################

    @Override
    public void visit( BetweenCriteria obj ) {
        visitNode(obj.getExpression());
        append(SPACE);

        if (obj.isNegated()) {
            append(NOT);
            append(SPACE);
        }
        append(BETWEEN);
        append(SPACE);
        visitNode(obj.getLowerExpression());

        append(SPACE);
        append(AND);
        append(SPACE);
        visitNode(obj.getUpperExpression());
    }

    @Override
    public void visit( CaseExpression obj ) {
        append(CASE);
        append(SPACE);
        visitNode(obj.getExpression());
        append(SPACE);

        for (int i = 0; i < obj.getWhenCount(); i++) {
            append(WHEN);
            append(SPACE);
            visitNode(obj.getWhenExpression(i));
            append(SPACE);
            append(THEN);
            append(SPACE);
            visitNode(obj.getThenExpression(i));
            append(SPACE);
        }

        if (obj.getElseExpression() != null) {
            append(ELSE);
            append(SPACE);
            visitNode(obj.getElseExpression());
            append(SPACE);
        }
        append(END);
    }

    @Override
    public void visit( CompareCriteria obj ) {
        Expression leftExpression = obj.getLeftExpression();
        visitNode(leftExpression);
        append(SPACE);
        append(obj.getOperatorAsString());
        append(SPACE);
        Expression rightExpression = obj.getRightExpression();
        visitNode(rightExpression);
    }

    @Override
    public void visit( CompoundCriteria obj ) {
        // Get operator string
        int operator = obj.getOperator();
        String operatorStr = ""; //$NON-NLS-1$
        if (operator == CompoundCriteria.AND) {
            operatorStr = AND;
        } else if (operator == CompoundCriteria.OR) {
            operatorStr = OR;
        }

        // Get criteria
        List<Criteria> subCriteria = obj.getCriteria();

        // Build parts
        if (subCriteria.size() == 1) {
            // Special case - should really never happen, but we are tolerant
            Criteria firstChild = subCriteria.get(0);
            visitNode(firstChild);
        } else {
            // Add first criteria
            Iterator<Criteria> iter = subCriteria.iterator();

            while (iter.hasNext()) {
                // Add criteria
                Criteria crit = iter.next();
                append(Tokens.LPAREN);
                visitNode(crit);
                append(Tokens.RPAREN);

                if (iter.hasNext()) {
                    // Add connector
                    append(SPACE);
                    append(operatorStr);
                    append(SPACE);
                }
            }
        }
    }

    @Override
    public void visit( Delete obj ) {
        // add delete clause
        append(DELETE);
        append(SPACE);
        // add from clause
        append(FROM);
        append(SPACE);
        visitNode(obj.getGroup());

        // add where clause
        if (obj.getCriteria() != null) {
            beginClause(0);
            visitCriteria(WHERE, obj.getCriteria());
        }

        // Option clause
        if (obj.getOption() != null) {
            beginClause(0);
            visitNode(obj.getOption());
        }
    }

    @Override
    public void visit( DependentSetCriteria obj ) {
        visitNode(obj.getExpression());

        // operator and beginning of list
        append(SPACE);
        if (obj.isNegated()) {
            append(NOT);
            append(SPACE);
        }
        append(IN);
        append(" (<dependent values>)"); //$NON-NLS-1$
    }

    @Override
    public void visit( From obj ) {
        append(FROM);
        beginClause(1);
        registerNodes(obj.getClauses(), 0);
    }

    @Override
    public void visit( GroupBy obj ) {
        append(GROUP);
        append(SPACE);
        append(BY);
        append(SPACE);
        registerNodes(obj.getSymbols(), 0);
    }

    @Override
    public void visit( Insert obj ) {
        append(INSERT);
        append(SPACE);
        append(INTO);
        append(SPACE);
        visitNode(obj.getGroup());

        if (!obj.getVariables().isEmpty()) {
            beginClause(2);

            // Columns clause
            List vars = obj.getVariables();
            if (vars != null) {
                append("("); //$NON-NLS-1$
                registerNodes(vars, 0);
                append(")"); //$NON-NLS-1$
            }
        }
        beginClause(1);
        if (obj.getQueryExpression() != null) {
            visitNode(obj.getQueryExpression());
        } else if (obj.getTupleSource() != null) {
            append(VALUES);
            append(" (...)"); //$NON-NLS-1$
        } else if (obj.getValues() != null) {
            append(VALUES);
            beginClause(2);
            append("("); //$NON-NLS-1$
            registerNodes(obj.getValues(), 0);
            append(")"); //$NON-NLS-1$
        }

        // Option clause
        if (obj.getOption() != null) {
            beginClause(1);
            append(SPACE);
            visitNode(obj.getOption());
        }
    }

    @Override
    public void visit( Create obj ) {
        append(CREATE);
        append(SPACE);
        append(LOCAL);
        append(SPACE);
        append(TEMPORARY);
        append(SPACE);
        append(TABLE);
        append(SPACE);
        visitNode(obj.getTable());
        append(SPACE);

        // Columns clause
        List<ElementSymbol> columns = obj.getColumns();
        append("("); //$NON-NLS-1$
        Iterator<ElementSymbol> iter = columns.iterator();
        while (iter.hasNext()) {
            ElementSymbol element = iter.next();
            outputShortName(element);
            append(SPACE);
            append(DataTypeManager.getDataTypeName(element.getType()));
            if (iter.hasNext()) {
                append(", "); //$NON-NLS-1$
            }
        }
        if (!obj.getPrimaryKey().isEmpty()) {
            append(", "); //$NON-NLS-1$
            append(PRIMARY);
            append(" "); //$NON-NLS-1$
            append(NonReserved.KEY);
            append(Tokens.LPAREN);
            iter = obj.getPrimaryKey().iterator();
            while (iter.hasNext()) {
                outputShortName(iter.next());
                if (iter.hasNext()) {
                    append(", "); //$NON-NLS-1$
                }
            }
            append(Tokens.RPAREN);
        }
        append(")"); //$NON-NLS-1$
    }

    @Override
    public void visit( Drop obj ) {
        append(DROP);
        append(SPACE);
        append(TABLE);
        append(SPACE);
        visitNode(obj.getTable());
    }

    @Override
    public void visit( IsNullCriteria obj ) {
        Expression expr = obj.getExpression();
        visitNode(expr);
        append(SPACE);
        append(IS);
        append(SPACE);
        if (obj.isNegated()) {
            append(NOT);
            append(SPACE);
        }
        append(NULL);
    }

    @Override
    public void visit( JoinPredicate obj ) {
        addOptionComment(obj);

        if (obj.hasHint()) {
            append("(");//$NON-NLS-1$
        }

        // left clause
        FromClause leftClause = obj.getLeftClause();
        if (leftClause instanceof JoinPredicate && !((JoinPredicate)leftClause).hasHint()) {
            append("("); //$NON-NLS-1$
            visitNode(leftClause);
            append(")"); //$NON-NLS-1$
        } else {
            visitNode(leftClause);
        }

        // join type
        append(SPACE);
        visitNode(obj.getJoinType());
        append(SPACE);

        // right clause
        FromClause rightClause = obj.getRightClause();
        if (rightClause instanceof JoinPredicate && !((JoinPredicate)rightClause).hasHint()) {
            append("("); //$NON-NLS-1$
            visitNode(rightClause);
            append(")"); //$NON-NLS-1$
        } else {
            visitNode(rightClause);
        }

        // join criteria
        List joinCriteria = obj.getJoinCriteria();
        if (joinCriteria != null && joinCriteria.size() > 0) {
            append(SPACE);
            append(ON);
            append(SPACE);
            Iterator critIter = joinCriteria.iterator();
            while (critIter.hasNext()) {
                Criteria crit = (Criteria)critIter.next();
                if (crit instanceof PredicateCriteria || crit instanceof AtomicCriteria) {
                    visitNode(crit);
                } else {
                    append("("); //$NON-NLS-1$
                    visitNode(crit);
                    append(")"); //$NON-NLS-1$
                }

                if (critIter.hasNext()) {
                    append(SPACE);
                    append(AND);
                    append(SPACE);
                }
            }
        }

        if (obj.hasHint()) {
            append(")"); //$NON-NLS-1$
        }
        addFromClasueDepOptions(obj);
    }

    private void addFromClasueDepOptions( FromClause obj ) {
        if (obj.isMakeDep()) {
            append(SPACE);
            append(Option.MAKEDEP);
        }
        if (obj.isMakeNotDep()) {
            append(SPACE);
            append(Option.MAKENOTDEP);
        }
    }

    private void addOptionComment( FromClause obj ) {
        if (obj.isOptional()) {
            append(BEGIN_HINT);
            append(SPACE);
            append(Option.OPTIONAL);
            append(SPACE);
            append(END_HINT);
            append(SPACE);
        }
    }

    @Override
    public void visit( JoinType obj ) {
        String[] output = null;
        if (obj.equals(JoinType.JOIN_INNER)) {
            output = new String[] {INNER, SPACE, JOIN};
        } else if (obj.equals(JoinType.JOIN_CROSS)) {
            output = new String[] {CROSS, SPACE, JOIN};
        } else if (obj.equals(JoinType.JOIN_LEFT_OUTER)) {
            output = new String[] {LEFT, SPACE, OUTER, SPACE, JOIN};
        } else if (obj.equals(JoinType.JOIN_RIGHT_OUTER)) {
            output = new String[] {RIGHT, SPACE, OUTER, SPACE, JOIN};
        } else if (obj.equals(JoinType.JOIN_FULL_OUTER)) {
            output = new String[] {FULL, SPACE, OUTER, SPACE, JOIN};
        } else if (obj.equals(JoinType.JOIN_UNION)) {
            output = new String[] {UNION, SPACE, JOIN};
        } else if (obj.equals(JoinType.JOIN_SEMI)) {
            output = new String[] {"SEMI", SPACE, JOIN}; //$NON-NLS-1$
        } else if (obj.equals(JoinType.JOIN_ANTI_SEMI)) {
            output = new String[] {"ANTI SEMI", SPACE, JOIN}; //$NON-NLS-1$
        } else {
            throw new AssertionError();
        }
        for (String part : output) {
            append(part);
        }
    }

    @Override
    public void visit( MatchCriteria obj ) {
        visitNode(obj.getLeftExpression());

        append(SPACE);
        if (obj.isNegated()) {
            append(NOT);
            append(SPACE);
        }
        append(LIKE);
        append(SPACE);

        visitNode(obj.getRightExpression());

        if (obj.getEscapeChar() != MatchCriteria.NULL_ESCAPE_CHAR) {
            append(SPACE);
            append(ESCAPE);
            append(" '"); //$NON-NLS-1$
            append("" + obj.getEscapeChar()); //$NON-NLS-1$
            append("'"); //$NON-NLS-1$
        }
    }

    @Override
    public void visit( NotCriteria obj ) {
        append(NOT);
        append(" ("); //$NON-NLS-1$
        visitNode(obj.getCriteria());
        append(")"); //$NON-NLS-1$
    }

    @Override
    public void visit( Option obj ) {
        append(OPTION);

        Collection groups = obj.getDependentGroups();
        if (groups != null && groups.size() > 0) {
            append(" "); //$NON-NLS-1$
            append(MAKEDEP);
            append(" "); //$NON-NLS-1$

            Iterator iter = groups.iterator();

            while (iter.hasNext()) {
                outputDisplayName((String)iter.next());

                if (iter.hasNext()) {
                    append(", "); //$NON-NLS-1$
                }
            }
        }

        groups = obj.getNotDependentGroups();
        if (groups != null && groups.size() > 0) {
            append(" "); //$NON-NLS-1$
            append(MAKENOTDEP);
            append(" "); //$NON-NLS-1$

            Iterator iter = groups.iterator();

            while (iter.hasNext()) {
                outputDisplayName((String)iter.next());

                if (iter.hasNext()) {
                    append(", "); //$NON-NLS-1$
                }
            }
        }

        groups = obj.getNoCacheGroups();
        if (groups != null && groups.size() > 0) {
            append(" "); //$NON-NLS-1$
            append(NOCACHE);
            append(" "); //$NON-NLS-1$

            Iterator iter = groups.iterator();

            while (iter.hasNext()) {
                outputDisplayName((String)iter.next());

                if (iter.hasNext()) {
                    append(", "); //$NON-NLS-1$
                }
            }
        } else if (obj.isNoCache()) {
            append(" "); //$NON-NLS-1$
            append(NOCACHE);
        }

    }

    @Override
    public void visit( OrderBy obj ) {
        append(ORDER);
        append(SPACE);
        append(BY);
        append(SPACE);
        for (Iterator<OrderByItem> iterator = obj.getOrderByItems().iterator(); iterator.hasNext();) {
            OrderByItem item = iterator.next();
            visitNode(item);
            if (iterator.hasNext()) {
                append(", "); //$NON-NLS-1$				
            }
        }
    }

    @Override
    public void visit( OrderByItem obj ) {
        SingleElementSymbol ses = obj.getSymbol();
        if (ses instanceof AliasSymbol) {
            AliasSymbol as = (AliasSymbol)ses;
            outputDisplayName(as.getOutputName());
        } else {
            visitNode(ses);
        }
        if (!obj.isAscending()) {
            append(SPACE);
            append(DESC);
        } // Don't print default "ASC"
        if (obj.getNullOrdering() != null) {
            append(SPACE);
            append(NonReserved.NULLS);
            append(SPACE);
            append(obj.getNullOrdering().name());
        }
    }

    @Override
    public void visit( DynamicCommand obj ) {
        append(EXECUTE);
        append(SPACE);
        append(STRING);
        append(SPACE);
        visitNode(obj.getSql());

        if (obj.isAsClauseSet()) {
            beginClause(1);
            append(AS);
            append(SPACE);
            for (int i = 0; i < obj.getAsColumns().size(); i++) {
                ElementSymbol symbol = (ElementSymbol)obj.getAsColumns().get(i);
                outputShortName(symbol);
                append(SPACE);
                append(DataTypeManager.getDataTypeName(symbol.getType()));
                if (i < obj.getAsColumns().size() - 1) {
                    append(", "); //$NON-NLS-1$
                }
            }
        }

        if (obj.getIntoGroup() != null) {
            beginClause(1);
            append(INTO);
            append(SPACE);
            visitNode(obj.getIntoGroup());
        }

        if (obj.getUsing() != null && !obj.getUsing().isEmpty()) {
            beginClause(1);
            append(USING);
            append(SPACE);
            visitNode(obj.getUsing());
        }

        if (obj.getUpdatingModelCount() > 0) {
            beginClause(1);
            append(UPDATE);
            append(SPACE);
            if (obj.getUpdatingModelCount() > 1) {
                append("*"); //$NON-NLS-1$
            } else {
                append("1"); //$NON-NLS-1$
            }
        }
    }

    @Override
    public void visit( SetClauseList obj ) {
        for (Iterator<SetClause> iterator = obj.getClauses().iterator(); iterator.hasNext();) {
            SetClause clause = iterator.next();
            visitNode(clause);
            if (iterator.hasNext()) {
                append(", "); //$NON-NLS-1$
            }
        }
    }

    @Override
    public void visit( SetClause obj ) {
        ElementSymbol symbol = obj.getSymbol();
        outputShortName(symbol);
        append(" = "); //$NON-NLS-1$
        visitNode(obj.getValue());
    }

    @Override
    public void visit( Query obj ) {
        addCacheHint(obj.getCacheHint());
        visitNode(obj.getSelect());

        if (obj.getInto() != null) {
            beginClause(1);
            visitNode(obj.getInto());
        }

        if (obj.getFrom() != null) {
            beginClause(1);
            visitNode(obj.getFrom());
        }

        // Where clause
        if (obj.getCriteria() != null) {
            beginClause(1);
            visitCriteria(WHERE, obj.getCriteria());
        }

        // Group by clause
        if (obj.getGroupBy() != null) {
            beginClause(1);
            visitNode(obj.getGroupBy());
        }

        // Having clause
        if (obj.getHaving() != null) {
            beginClause(1);
            visitCriteria(HAVING, obj.getHaving());
        }

        // Order by clause
        if (obj.getOrderBy() != null) {
            beginClause(1);
            visitNode(obj.getOrderBy());
        }

        if (obj.getLimit() != null) {
            beginClause(1);
            visitNode(obj.getLimit());
        }

        // Option clause
        if (obj.getOption() != null) {
            beginClause(1);
            visitNode(obj.getOption());
        }
    }

    protected void visitCriteria( String keyWord,
                                  Criteria crit ) {
        append(keyWord);
        append(SPACE);
        visitNode(crit);
    }

    @Override
    public void visit( SearchedCaseExpression obj ) {
        append(CASE);
        for (int i = 0; i < obj.getWhenCount(); i++) {
            append(SPACE);
            append(WHEN);
            append(SPACE);
            visitNode(obj.getWhenCriteria(i));
            append(SPACE);
            append(THEN);
            append(SPACE);
            visitNode(obj.getThenExpression(i));
        }
        append(SPACE);
        if (obj.getElseExpression() != null) {
            append(ELSE);
            append(SPACE);
            visitNode(obj.getElseExpression());
            append(SPACE);
        }
        append(END);
    }

    @Override
    public void visit( Select obj ) {
        append(SELECT);
        if (obj.isDistinct()) {
            append(SPACE);
            append(DISTINCT);
        }
        beginClause(2);

        Iterator iter = obj.getSymbols().iterator();
        while (iter.hasNext()) {
            SelectSymbol symbol = (SelectSymbol)iter.next();
            visitNode(symbol);
            if (iter.hasNext()) {
                append(", "); //$NON-NLS-1$
            }
        }
    }

    @Override
    public void visit( SetCriteria obj ) {
        // variable
        visitNode(obj.getExpression());

        // operator and beginning of list
        append(SPACE);
        if (obj.isNegated()) {
            append(NOT);
            append(SPACE);
        }
        append(IN);
        append(" ("); //$NON-NLS-1$

        // value list
        Collection vals = obj.getValues();
        int size = vals.size();
        if (size == 1) {
            Iterator iter = vals.iterator();
            Expression expr = (Expression)iter.next();
            visitNode(expr);
        } else if (size > 1) {
            Iterator iter = vals.iterator();
            Expression expr = (Expression)iter.next();
            visitNode(expr);
            while (iter.hasNext()) {
                expr = (Expression)iter.next();
                append(", "); //$NON-NLS-1$
                visitNode(expr);
            }
        }
        append(")"); //$NON-NLS-1$
    }

    @Override
    public void visit( SetQuery obj ) {
        addCacheHint(obj.getCacheHint());
        QueryCommand query = obj.getLeftQuery();
        appendSetQuery(obj, query, false);

        beginClause(0);
        append(obj.getOperation());

        if (obj.isAll()) {
            append(SPACE);
            append(ALL);
        }
        beginClause(0);
        query = obj.getRightQuery();
        appendSetQuery(obj, query, true);

        if (obj.getOrderBy() != null) {
            beginClause(0);
            visitNode(obj.getOrderBy());
        }

        if (obj.getLimit() != null) {
            beginClause(0);
            visitNode(obj.getLimit());
        }

        if (obj.getOption() != null) {
            beginClause(0);
            visitNode(obj.getOption());
        }
    }

    protected void appendSetQuery( SetQuery parent,
                                   QueryCommand obj,
                                   boolean right ) {
        if (right && obj instanceof SetQuery
            && ((parent.isAll() && !((SetQuery)obj).isAll()) || parent.getOperation() != ((SetQuery)obj).getOperation())) {
            append(Tokens.LPAREN);
            visitNode(obj);
            append(Tokens.RPAREN);
        } else {
            visitNode(obj);
        }
    }

    @Override
    public void visit( StoredProcedure obj ) {
        addCacheHint(obj.getCacheHint());
        // exec clause
        append(EXEC);
        append(SPACE);
        append(obj.getProcedureName());
        append("("); //$NON-NLS-1$
        List params = obj.getInputParameters();
        if (params != null) {
            Iterator iter = params.iterator();
            while (iter.hasNext()) {
                SPParameter param = (SPParameter)iter.next();

                if (obj.displayNamedParameters()) {
                    append(escapeSinglePart(ElementSymbol.getShortName(param.getParameterSymbol().getOutputName())));
                    append(" => "); //$NON-NLS-1$
                }

                if (param.getExpression() == null) {
                    if (param.getName() != null) {
                        outputDisplayName(obj.getParamFullName(param));
                    } else {
                        append("?"); //$NON-NLS-1$
                    }
                } else {
                    boolean addParens = !obj.displayNamedParameters() && param.getExpression() instanceof CompareCriteria;
                    if (addParens) {
                        append(Tokens.LPAREN);
                    }
                    visitNode(param.getExpression());
                    if (addParens) {
                        append(Tokens.RPAREN);
                    }
                }
                if (iter.hasNext()) {
                    append(", "); //$NON-NLS-1$
                }
            }
        }
        append(")"); //$NON-NLS-1$

        // Option clause
        if (obj.getOption() != null) {
            beginClause(1);
            visitNode(obj.getOption());
        }
    }

    public void addCacheHint( CacheHint obj ) {
        if (obj == null) {
            return;
        }
        append(BEGIN_HINT);
        append(SPACE);
        append(CacheHint.CACHE);
        boolean addParens = false;
        if (obj.getPrefersMemory()) {
            append(Tokens.LPAREN);
            addParens = true;
            append(CacheHint.PREF_MEM);
        }
        if (obj.getTtl() != null) {
            if (!addParens) {
                append(Tokens.LPAREN);
                addParens = true;
            } else {
                append(SPACE);
            }
            append(CacheHint.TTL);
            append(obj.getTtl());
        }
        if (obj.isUpdatable()) {
            if (!addParens) {
                append(Tokens.LPAREN);
                addParens = true;
            } else {
                append(SPACE);
            }
            append(CacheHint.UPDATABLE);
        }
        if (addParens) {
            append(Tokens.RPAREN);
        }
        append(SPACE);
        append(END_HINT);
        beginClause(0);
    }

    @Override
    public void visit( SubqueryFromClause obj ) {
        addOptionComment(obj);
        if (obj.isTable()) {
            append(TABLE);
        }
        append("(");//$NON-NLS-1$
        visitNode(obj.getCommand());
        append(")");//$NON-NLS-1$
        append(" AS ");//$NON-NLS-1$
        append(obj.getOutputName());
        addFromClasueDepOptions(obj);
    }

    @Override
    public void visit( SubquerySetCriteria obj ) {
        // variable
        visitNode(obj.getExpression());

        // operator and beginning of list
        append(SPACE);
        if (obj.isNegated()) {
            append(NOT);
            append(SPACE);
        }
        append(IN);
        append(" ("); //$NON-NLS-1$
        visitNode(obj.getCommand());
        append(")"); //$NON-NLS-1$
    }

    @Override
    public void visit( UnaryFromClause obj ) {
        addOptionComment(obj);
        visitNode(obj.getGroup());
        addFromClasueDepOptions(obj);
    }

    @Override
    public void visit( Update obj ) {
        // Update clause
        append(UPDATE);
        append(SPACE);
        visitNode(obj.getGroup());
        beginClause(1);
        // Set clause
        append(SET);
        beginClause(2);
        visitNode(obj.getChangeList());

        // Where clause
        if (obj.getCriteria() != null) {
            beginClause(1);
            visitCriteria(WHERE, obj.getCriteria());
        }

        // Option clause
        if (obj.getOption() != null) {
            beginClause(1);
            visitNode(obj.getOption());
        }
    }

    @Override
    public void visit( Into obj ) {
        append(INTO);
        append(SPACE);
        visitNode(obj.getGroup());
    }

    // ############ Visitor methods for symbol objects ####################

    @Override
    public void visit( AggregateSymbol obj ) {
        append(obj.getAggregateFunction().name());
        append("("); //$NON-NLS-1$

        if (obj.isDistinct()) {
            append(DISTINCT);
            append(" "); //$NON-NLS-1$
        }

        if (obj.getExpression() == null) {
            append(Tokens.ALL_COLS);
        } else {
            visitNode(obj.getExpression());
        }

        if (obj.getOrderBy() != null) {
            append(SPACE);
            visitNode(obj.getOrderBy());
        }
        append(")"); //$NON-NLS-1$
    }

    @Override
    public void visit( AliasSymbol obj ) {
        visitNode(obj.getSymbol());
        append(SPACE);
        append(AS);
        append(SPACE);
        append(escapeSinglePart(obj.getOutputName()));
    }

    @Override
    public void visit( AllInGroupSymbol obj ) {
        append(obj.getName());
    }

    @Override
    public void visit( AllSymbol obj ) {
        append(obj.getName());
    }

    @Override
    public void visit( Constant obj ) {
        Class<?> type = obj.getType();
        String[] constantParts = null;
        if (obj.isMultiValued()) {
            constantParts = new String[] {"?"}; //$NON-NLS-1$
        } else if (obj.isNull()) {
            if (type.equals(DataTypeManager.DefaultDataClasses.BOOLEAN)) {
                constantParts = new String[] {UNKNOWN};
            } else {
                constantParts = new String[] {"null"}; //$NON-NLS-1$
            }
        } else {
            if (Number.class.isAssignableFrom(type)) {
                constantParts = new String[] {obj.getValue().toString()};
            } else if (type.equals(DataTypeManager.DefaultDataClasses.BOOLEAN)) {
                constantParts = new String[] {obj.getValue().equals(Boolean.TRUE) ? TRUE : FALSE};
            } else if (type.equals(DataTypeManager.DefaultDataClasses.TIMESTAMP)) {
                constantParts = new String[] {"{ts'", obj.getValue().toString(), "'}"}; //$NON-NLS-1$ //$NON-NLS-2$
            } else if (type.equals(DataTypeManager.DefaultDataClasses.TIME)) {
                constantParts = new String[] {"{t'", obj.getValue().toString(), "'}"}; //$NON-NLS-1$ //$NON-NLS-2$
            } else if (type.equals(DataTypeManager.DefaultDataClasses.DATE)) {
                constantParts = new String[] {"{d'", obj.getValue().toString(), "'}"}; //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (constantParts == null) {
                String strValue = obj.getValue().toString();
                strValue = escapeStringValue(strValue, "'"); //$NON-NLS-1$
                constantParts = new String[] {"'", strValue, "'"}; //$NON-NLS-1$ //$NON-NLS-2$
            }
        }

        for (String string : constantParts) {
            append(string);
        }
    }

    /**
     * Take a string literal and escape it as necessary. By default, this converts ' to ''.
     * 
     * @param str String literal value (unquoted), never null
     * @return Escaped string literal value
     */
    static String escapeStringValue( String str,
                                     String tick ) {
        return StringUtil.replaceAll(str, tick, tick + tick);
    }

    @Override
    public void visit( ElementSymbol obj ) {
        if (obj.getDisplayMode().equals(ElementSymbol.DisplayMode.SHORT_OUTPUT_NAME)) {
            outputShortName(obj);
            return;
        }
        String name = obj.getOutputName();
        if (obj.getDisplayMode().equals(ElementSymbol.DisplayMode.FULLY_QUALIFIED)) {
            name = obj.getName();
        }
        outputDisplayName(name);
    }

    private void outputShortName( ElementSymbol obj ) {
        outputDisplayName(SingleElementSymbol.getShortName(obj.getOutputName()));
    }

    private void outputDisplayName( String name ) {
        String[] pathParts = name.split("\\."); //$NON-NLS-1$
        for (int i = 0; i < pathParts.length; i++) {
            if (i > 0) {
                append(ElementSymbol.SEPARATOR);
            }
            append(escapeSinglePart(pathParts[i]));
        }
    }

    @Override
    public void visit( ExpressionSymbol obj ) {
        visitNode(obj.getExpression());
    }

    @Override
    public void visit( Function obj ) {
        String name = obj.getName();
        Expression[] args = obj.getArgs();
        if (obj.isImplicit()) {
            // Hide this function, which is implicit
            visitNode(args[0]);

        } else if (name.equalsIgnoreCase(CONVERT) || name.equalsIgnoreCase(CAST)) {
            append(name);
            append("("); //$NON-NLS-1$

            if (args != null && args.length > 0) {
                visitNode(args[0]);

                if (name.equalsIgnoreCase(CONVERT)) {
                    append(", "); //$NON-NLS-1$
                } else {
                    append(" "); //$NON-NLS-1$
                    append(AS);
                    append(" "); //$NON-NLS-1$
                }

                if (args.length < 2 || args[1] == null || !(args[1] instanceof Constant)) {
                    append(UNDEFINED);
                } else {
                    append(((Constant)args[1]).getValue());
                }
            }
            append(")"); //$NON-NLS-1$

        } else if (name.equals("+") || name.equals("-") || name.equals("*") || name.equals("/") || name.equals("||")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            append("("); //$NON-NLS-1$

            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    visitNode(args[i]);
                    if (i < (args.length - 1)) {
                        append(SPACE);
                        append(name);
                        append(SPACE);
                    }
                }
            }
            append(")"); //$NON-NLS-1$

        } else if (name.equalsIgnoreCase(NonReserved.TIMESTAMPADD) || name.equalsIgnoreCase(NonReserved.TIMESTAMPDIFF)) {
            append(name);
            append("("); //$NON-NLS-1$

            if (args != null && args.length > 0) {
                append(((Constant)args[0]).getValue());
                registerNodes(args, 1);
            }
            append(")"); //$NON-NLS-1$

        } else if (name.equalsIgnoreCase(SourceSystemFunctions.XMLPI)) {
            append(name);
            append("(NAME "); //$NON-NLS-1$
            outputDisplayName((String)((Constant)args[0]).getValue());
            registerNodes(args, 1);
            append(")"); //$NON-NLS-1$
        } else {
            append(name);
            append("("); //$NON-NLS-1$
            registerNodes(args, 0);
            append(")"); //$NON-NLS-1$
        }
    }

    private void registerNodes( LanguageObject[] objects,
                                int begin ) {
        registerNodes(Arrays.asList(objects), begin);
    }

    private void registerNodes( List<? extends LanguageObject> objects,
                                int begin ) {
        for (int i = begin; i < objects.size(); i++) {
            if (i > 0) {
                append(", "); //$NON-NLS-1$
            }
            visitNode(objects.get(i));
        }
    }

    @Override
    public void visit( GroupSymbol obj ) {
        String alias = null;
        String fullGroup = obj.getOutputName();
        if (obj.getOutputDefinition() != null) {
            alias = obj.getOutputName();
            fullGroup = obj.getOutputDefinition();
        }

        outputDisplayName(fullGroup);

        if (alias != null) {
            append(SPACE);
            append(AS);
            append(SPACE);
            append(escapeSinglePart(alias));
        }
    }

    @Override
    public void visit( Reference obj ) {
        if (!obj.isPositional() && obj.getExpression() != null) {
            visitNode(obj.getExpression());
        } else {
            append("?"); //$NON-NLS-1$
        }
    }

    // ############ Visitor methods for storedprocedure language objects ####################

    @Override
    public void visit( Block obj ) {
        List statements = obj.getStatements();
        // Add first clause
        append(BEGIN);
        append("\n"); //$NON-NLS-1$
        Iterator stmtIter = statements.iterator();
        while (stmtIter.hasNext()) {
            // Add each statement
            addTabs(1);
            visitNode((Statement)stmtIter.next());
            append("\n"); //$NON-NLS-1$
        }
        addTabs(0);
        append(END);
    }

    protected void addTabs( int level ) {
    }

    @Override
    public void visit( CommandStatement obj ) {
        visitNode(obj.getCommand());
        append(";"); //$NON-NLS-1$
    }

    @Override
    public void visit( CreateUpdateProcedureCommand obj ) {
        append(CREATE);
        append(SPACE);
        if (!obj.isUpdateProcedure()) {
            append(VIRTUAL);
            append(SPACE);
        }
        append(PROCEDURE);
        append("\n"); //$NON-NLS-1$
        addTabs(0);
        visitNode(obj.getBlock());
    }

    @Override
    public void visit( DeclareStatement obj ) {
        append(DECLARE);
        append(SPACE);
        append(obj.getVariableType());
        append(SPACE);
        createAssignment(obj);
    }

    /**
     * @param obj
     * @param parts
     */
    private void createAssignment( AssignmentStatement obj ) {
        visitNode(obj.getVariable());
        if (obj.getExpression() != null) {
            append(" = "); //$NON-NLS-1$
            addStatementArgument(obj.getExpression());
        }
        append(";"); //$NON-NLS-1$
    }

    private void addStatementArgument( Expression expr ) {
        if (expr instanceof ScalarSubquery) {
            visitNode(((ScalarSubquery)expr).getCommand());
        } else {
            visitNode(expr);
        }
    }

    @Override
    public void visit( IfStatement obj ) {
        append(IF);
        append("("); //$NON-NLS-1$
        visitNode(obj.getCondition());
        append(")\n"); //$NON-NLS-1$
        addTabs(0);
        visitNode(obj.getIfBlock());
        if (obj.hasElseBlock()) {
            append("\n"); //$NON-NLS-1$
            addTabs(0);
            append(ELSE);
            append("\n"); //$NON-NLS-1$
            addTabs(0);
            visitNode(obj.getElseBlock());
        }
    }

    @Override
    public void visit( AssignmentStatement obj ) {
        createAssignment(obj);
    }

    @Override
    public void visit( HasCriteria obj ) {
        append(HAS);
        append(SPACE);
        visitNode(obj.getSelector());
    }

    @Override
    public void visit( TranslateCriteria obj ) {
        append(TRANSLATE);
        append(SPACE);
        visitNode(obj.getSelector());

        if (obj.hasTranslations()) {
            append(SPACE);
            append(WITH);
            append(SPACE);
            append("("); //$NON-NLS-1$
            Iterator critIter = obj.getTranslations().iterator();

            while (critIter.hasNext()) {
                visitNode((Criteria)critIter.next());
                if (critIter.hasNext()) {
                    append(", "); //$NON-NLS-1$
                }
                if (!critIter.hasNext()) {
                    append(")"); //$NON-NLS-1$
                }
            }
        }
    }

    @Override
    public void visit( CriteriaSelector obj ) {
        int selectorType = obj.getSelectorType();

        switch (selectorType) {
            case CriteriaSelector.COMPARE_EQ:
                append("= "); //$NON-NLS-1$
                break;
            case CriteriaSelector.COMPARE_GE:
                append(">= "); //$NON-NLS-1$
                break;
            case CriteriaSelector.COMPARE_GT:
                append("> "); //$NON-NLS-1$
                break;
            case CriteriaSelector.COMPARE_LE:
                append("<= "); //$NON-NLS-1$
                break;
            case CriteriaSelector.COMPARE_LT:
                append("< "); //$NON-NLS-1$
                break;
            case CriteriaSelector.COMPARE_NE:
                append("<> "); //$NON-NLS-1$
                break;
            case CriteriaSelector.IN:
                append(IN);
                append(SPACE);
                break;
            case CriteriaSelector.IS_NULL:
                append(IS);
                append(SPACE);
                append(NULL);
                append(SPACE);
                break;
            case CriteriaSelector.LIKE:
                append(LIKE);
                append(SPACE);
                break;
            case CriteriaSelector.BETWEEN:
                append(BETWEEN);
                append(SPACE);
                break;
        }

        append(CRITERIA);
        if (obj.hasElements()) {
            append(SPACE);
            append(ON);
            append(SPACE);
            append("("); //$NON-NLS-1$

            Iterator elmtIter = obj.getElements().iterator();
            while (elmtIter.hasNext()) {
                visitNode((ElementSymbol)elmtIter.next());
                if (elmtIter.hasNext()) {
                    append(", "); //$NON-NLS-1$
                }
            }
            append(")"); //$NON-NLS-1$
        }
    }

    @Override
    public void visit( RaiseErrorStatement obj ) {
        append(ERROR);
        append(SPACE);
        addStatementArgument(obj.getExpression());
        append(";"); //$NON-NLS-1$
    }

    @Override
    public void visit( BreakStatement obj ) {
        append(BREAK);
        append(";"); //$NON-NLS-1$
    }

    @Override
    public void visit( ContinueStatement obj ) {
        append(CONTINUE);
        append(";"); //$NON-NLS-1$
    }

    @Override
    public void visit( LoopStatement obj ) {
        append(LOOP);
        append(" "); //$NON-NLS-1$
        append(ON);
        append(" ("); //$NON-NLS-1$
        visitNode(obj.getCommand());
        append(") "); //$NON-NLS-1$
        append(AS);
        append(" "); //$NON-NLS-1$
        append(obj.getCursorName());
        append("\n"); //$NON-NLS-1$
        addTabs(0);
        visitNode(obj.getBlock());
    }

    @Override
    public void visit( WhileStatement obj ) {
        append(WHILE);
        append("("); //$NON-NLS-1$
        visitNode(obj.getCondition());
        append(")\n"); //$NON-NLS-1$
        addTabs(0);
        visitNode(obj.getBlock());
    }

    @Override
    public void visit( ExistsCriteria obj ) {
        // operator and beginning of list
        append(EXISTS);
        append(" ("); //$NON-NLS-1$
        visitNode(obj.getCommand());
        append(")"); //$NON-NLS-1$
    }

    @Override
    public void visit( SubqueryCompareCriteria obj ) {
        Expression leftExpression = obj.getLeftExpression();
        visitNode(leftExpression);

        String operator = obj.getOperatorAsString();
        String quantifier = obj.getPredicateQuantifierAsString();

        // operator and beginning of list
        append(SPACE);
        append(operator);
        append(SPACE);
        append(quantifier);
        append("("); //$NON-NLS-1$
        visitNode(obj.getCommand());
        append(")"); //$NON-NLS-1$
    }

    @Override
    public void visit( ScalarSubquery obj ) {
        // operator and beginning of list
        append("("); //$NON-NLS-1$
        visitNode(obj.getCommand());
        append(")"); //$NON-NLS-1$
    }

    @Override
    public void visit( XMLAttributes obj ) {
        append(XMLATTRIBUTES);
        append("("); //$NON-NLS-1$
        registerNodes(obj.getArgs(), 0);
        append(")"); //$NON-NLS-1$
    }

    @Override
    public void visit( XMLElement obj ) {
        append(XMLELEMENT);
        append("(NAME "); //$NON-NLS-1$
        outputDisplayName(obj.getName());
        if (obj.getNamespaces() != null) {
            append(", "); //$NON-NLS-1$
            visitNode(obj.getNamespaces());
        }
        if (obj.getAttributes() != null) {
            append(", "); //$NON-NLS-1$
            visitNode(obj.getAttributes());
        }
        if (!obj.getContent().isEmpty()) {
            append(", "); //$NON-NLS-1$
        }
        registerNodes(obj.getContent(), 0);
        append(")"); //$NON-NLS-1$
    }

    @Override
    public void visit( XMLForest obj ) {
        append(XMLFOREST);
        append("("); //$NON-NLS-1$
        if (obj.getNamespaces() != null) {
            visitNode(obj.getNamespaces());
            append(", "); //$NON-NLS-1$
        }
        registerNodes(obj.getArgs(), 0);
        append(")"); //$NON-NLS-1$
    }

    @Override
    public void visit( XMLNamespaces obj ) {
        append(XMLNAMESPACES);
        append("("); //$NON-NLS-1$
        for (Iterator<NamespaceItem> items = obj.getNamespaceItems().iterator(); items.hasNext();) {
            NamespaceItem item = items.next();
            if (item.getPrefix() == null) {
                if (item.getUri() == null) {
                    append("NO DEFAULT"); //$NON-NLS-1$
                } else {
                    append("DEFAULT "); //$NON-NLS-1$
                    visitNode(new Constant(item.getUri()));
                }
            } else {
                visitNode(new Constant(item.getUri()));
                append(" AS "); //$NON-NLS-1$
                outputDisplayName(item.getPrefix());
            }
            if (items.hasNext()) {
                append(", "); //$NON-NLS-1$
            }
        }
        append(")"); //$NON-NLS-1$
    }

    @Override
    public void visit( Limit obj ) {
        append(LIMIT);
        if (obj.getOffset() != null) {
            append(SPACE);
            visitNode(obj.getOffset());
            append(","); //$NON-NLS-1$
        }
        append(SPACE);
        visitNode(obj.getRowLimit());
    }

    @Override
    public void visit( TextTable obj ) {
        append("TEXTTABLE("); //$NON-NLS-1$
        visitNode(obj.getFile());
        append(SPACE);
        append(NonReserved.COLUMNS);

        for (Iterator<TextColumn> cols = obj.getColumns().iterator(); cols.hasNext();) {
            TextColumn col = cols.next();
            append(SPACE);
            outputDisplayName(col.getName());
            append(SPACE);
            append(col.getType());
            if (col.getWidth() != null) {
                append(SPACE);
                append(NonReserved.WIDTH);
                append(SPACE);
                append(col.getWidth());
            }
            if (cols.hasNext()) {
                append(","); //$NON-NLS-1$
            }
        }
        if (obj.getDelimiter() != null) {
            append(SPACE);
            append(NonReserved.DELIMITER);
            append(SPACE);
            visitNode(new Constant(obj.getDelimiter()));
        }
        if (obj.getQuote() != null) {
            append(SPACE);
            if (obj.isEscape()) {
                append(ESCAPE);
            } else {
                append(NonReserved.QUOTE);
            }
            append(SPACE);
            visitNode(new Constant(obj.getQuote()));
        }
        if (obj.getHeader() != null) {
            append(SPACE);
            append(NonReserved.HEADER);
            if (1 != obj.getHeader()) {
                append(SPACE);
                append(obj.getHeader());
            }
        }
        if (obj.getSkip() != null) {
            append(SPACE);
            append("SKIP"); //$NON-NLS-1$
            append(SPACE);
            append(obj.getSkip());
        }
        append(")");//$NON-NLS-1$
        append(SPACE);
        append(AS);
        append(SPACE);
        outputDisplayName(obj.getName());
    }

    @Override
    public void visit( XMLTable obj ) {
        append("XMLTABLE("); //$NON-NLS-1$
        if (obj.getNamespaces() != null) {
            visitNode(obj.getNamespaces());
            append(","); //$NON-NLS-1$
            append(SPACE);
        }
        visitNode(new Constant(obj.getXquery()));
        if (!obj.getPassing().isEmpty()) {
            append(SPACE);
            append(NonReserved.PASSING);
            append(SPACE);
            registerNodes(obj.getPassing(), 0);
        }
        if (!obj.getColumns().isEmpty()) {
            append(SPACE);
            append(NonReserved.COLUMNS);
            for (Iterator<XMLColumn> cols = obj.getColumns().iterator(); cols.hasNext();) {
                XMLColumn col = cols.next();
                append(SPACE);
                outputDisplayName(col.getName());
                append(SPACE);
                if (col.isOrdinal()) {
                    append(FOR);
                    append(SPACE);
                    append(NonReserved.ORDINALITY);
                } else {
                    append(col.getType());
                    if (col.getDefaultExpression() != null) {
                        append(SPACE);
                        append(DEFAULT);
                        append(SPACE);
                        visitNode(col.getDefaultExpression());
                    }
                    if (col.getPath() != null) {
                        append(SPACE);
                        append(NonReserved.PATH);
                        append(SPACE);
                        visitNode(new Constant(col.getPath()));
                    }
                }
                if (cols.hasNext()) {
                    append(","); //$NON-NLS-1$
                }
            }
        }
        append(")");//$NON-NLS-1$
        append(SPACE);
        append(AS);
        append(SPACE);
        outputDisplayName(obj.getName());
    }

    @Override
    public void visit( XMLQuery obj ) {
        append("XMLQUERY("); //$NON-NLS-1$
        if (obj.getNamespaces() != null) {
            visitNode(obj.getNamespaces());
            append(","); //$NON-NLS-1$
            append(SPACE);
        }
        visitNode(new Constant(obj.getXquery()));
        if (!obj.getPassing().isEmpty()) {
            append(SPACE);
            append(NonReserved.PASSING);
            append(SPACE);
            registerNodes(obj.getPassing(), 0);
        }
        if (obj.getEmptyOnEmpty() != null) {
            append(SPACE);
            if (obj.getEmptyOnEmpty()) {
                append(NonReserved.EMPTY);
            } else {
                append(NULL);
            }
            append(SPACE);
            append(ON);
            append(SPACE);
            append(NonReserved.EMPTY);
        }
        append(")");//$NON-NLS-1$
    }

    @Override
    public void visit( DerivedColumn obj ) {
        visitNode(obj.getExpression());
        if (obj.getAlias() != null) {
            append(SPACE);
            append(AS);
            append(SPACE);
            outputDisplayName(obj.getAlias());
        }
    }

    @Override
    public void visit( XMLSerialize obj ) {
        append(XMLSERIALIZE);
        append(Tokens.LPAREN);
        if (obj.isDocument() != null) {
            if (obj.isDocument()) {
                append(NonReserved.DOCUMENT);
            } else {
                append(NonReserved.CONTENT);
            }
            append(SPACE);
        }
        visitNode(obj.getExpression());
        if (obj.getTypeString() != null) {
            append(SPACE);
            append(AS);
            append(SPACE);
            append(obj.getTypeString());
        }
        append(Tokens.RPAREN);
    }

    @Override
    public void visit( QueryString obj ) {
        append(NonReserved.QUERYSTRING);
        append("("); //$NON-NLS-1$
        visitNode(obj.getPath());
        if (!obj.getArgs().isEmpty()) {
            append(","); //$NON-NLS-1$
            append(SPACE);
            registerNodes(obj.getArgs(), 0);
        }
        append(")"); //$NON-NLS-1$
    }

    @Override
    public void visit( XMLParse obj ) {
        append(XMLPARSE);
        append(Tokens.LPAREN);
        if (obj.isDocument()) {
            append(NonReserved.DOCUMENT);
        } else {
            append(NonReserved.CONTENT);
        }
        append(SPACE);
        visitNode(obj.getExpression());
        if (obj.isWellFormed()) {
            append(SPACE);
            append(NonReserved.WELLFORMED);
        }
        append(Tokens.RPAREN);
    }

    @Override
    public void visit( ExpressionCriteria obj ) {
        visitNode(obj.getExpression());
    }

    public static String escapeSinglePart( String part ) {
        if (isReservedWord(part)) {
            return ID_ESCAPE_CHAR + part + ID_ESCAPE_CHAR;
        }
        boolean escape = true;
        char start = part.charAt(0);
        if (start == '#' || start == '@' || StringUtil.isLetter(start)) {
            escape = false;
            for (int i = 1; !escape && i < part.length(); i++) {
                char c = part.charAt(i);
                escape = !StringUtil.isLetterOrDigit(c) && c != '_';
            }
        }
        if (escape) {
            return ID_ESCAPE_CHAR + escapeStringValue(part, "\"") + ID_ESCAPE_CHAR; //$NON-NLS-1$
        }
        return part;
    }

    /**
     * Check whether a string is considered a reserved word or not. Subclasses may override to change definition of reserved word.
     * 
     * @param string String to check
     * @return True if reserved word
     */
    static boolean isReservedWord( String string ) {
        if (string == null) {
            return false;
        }
        return SQLConstants.isReservedWord(string);
    }

}
