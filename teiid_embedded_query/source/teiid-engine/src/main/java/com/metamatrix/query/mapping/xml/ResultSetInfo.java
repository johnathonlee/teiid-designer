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

package com.metamatrix.query.mapping.xml;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.metamatrix.query.sql.lang.Command;
import com.metamatrix.query.sql.lang.Criteria;
import com.metamatrix.query.sql.lang.OrderBy;
import com.metamatrix.query.sql.symbol.ElementSymbol;

/** 
 * Represents a result set used in an XML query - this can be based on either a mapping class query
 * or a staging table.  All info about the result set is stored in this object - some is only applicable
 * for certain kinds of result sets.
 */
public class ResultSetInfo {

    private String resultSetName;
    
    // The result set command
    private Command rsCommand;
    
    // The bound references for this result set: List<Reference>
    private List boundReferences;

    // The processor plan output for the result set 
    private Object rsPlan;
    
    // Row limit, may be null if no limit
    private int userRowLimit = -1;
    
    // whether or not to throw exception on row limit
    private boolean exceptionOnRowLimit = false;
           
    private OrderBy orderBy;
    
    private Criteria criteria;
    
    private Set criteriaResultSets = new HashSet();
    
    private boolean criteriaRaised = false;
    
    private boolean stagedResult = false;
    
    //joined source node state
    private boolean joinedWithParent = false;
    private boolean joinRoot = false;
    private int mappingClassNumber = 0;
    private ElementSymbol mappingClassSymbol;
    
    public ResultSetInfo(String resultName) {
        this(resultName, false);
    }
    
    public ResultSetInfo(String resultName, boolean staged) {
        this.resultSetName = resultName;
        this.stagedResult = staged;
    }    
    
    public String getResultSetName() {
        return this.resultSetName;
    }
    
    public Command getCommand() {
        return this.rsCommand;
    }
    
    public void setCommand(Command cmd) {
        this.rsCommand = cmd;
    }
        
    public Object getPlan() {
        return rsPlan;
    }
    
    public void setPlan(Object plan) {
        this.rsPlan = plan;
    }
    
    public List getReferences() {
        return boundReferences;
    }
    
    public void setReferences(List references) {
        this.boundReferences = references;
    }
    
    public boolean hasReferences() {
        return boundReferences != null;
    }
    
    public int getUserRowLimit() {
        return userRowLimit;
    }
    
    public void setUserRowLimit(int limit, boolean throwException) {
        this.userRowLimit = limit;
        this.exceptionOnRowLimit = throwException;
    }
    
    public boolean exceptionOnRowlimit() {
        return exceptionOnRowLimit;
    }
    
    public Criteria getCriteria() {
        return this.criteria;
    }

    public void setCriteria(Criteria criteria) {
        this.criteria = criteria;
    }
    
    public OrderBy getOrderBy() {
        return this.orderBy;
    }

    public void setOrderBy(OrderBy orderBy) {
        this.orderBy = orderBy;
    }
    
    public Set getCriteriaResultSets() {
        return this.criteriaResultSets;
    }

    public void addToCriteriaResultSets(Set criteriaResultSets) {
        this.criteriaResultSets.addAll(criteriaResultSets);
    }    
    
    public boolean isCriteriaRaised() {
        return this.criteriaRaised;
    }
    
    public void setCriteriaRaised(boolean criteriaRaised) {
        this.criteriaRaised = criteriaRaised;
    }    
    
    public Object clone() {
        ResultSetInfo clone = new ResultSetInfo(this.resultSetName, this.stagedResult);
        clone.rsPlan = this.rsPlan;
        clone.boundReferences = this.boundReferences;
        clone.userRowLimit = this.userRowLimit;
        clone.exceptionOnRowLimit = this.exceptionOnRowLimit;
        clone.rsCommand = (Command)this.rsCommand.clone();
        clone.criteriaRaised = this.criteriaRaised;
        clone.joinedWithParent = this.joinedWithParent;
        clone.joinRoot = this.joinRoot;
        clone.mappingClassNumber = this.mappingClassNumber;
        clone.mappingClassSymbol = this.mappingClassSymbol;
        return clone;
    }
    
    public String toString() {
        return resultSetName + ", resultSetObject " + rsCommand; //$NON-NLS-1$
    }

    public boolean isJoinedWithParent() {
        return this.joinedWithParent;
    }

    public void setJoinedWithParent(boolean joinedWithParent) {
        this.joinedWithParent = joinedWithParent;
    }

    public boolean isJoinRoot() {
        return this.joinRoot;
    }

    public void setJoinRoot(boolean joinRoot) {
        this.joinRoot = joinRoot;
    }

    public int getMappingClassNumber() {
        return this.mappingClassNumber;
    }

    public void setMappingClassNumber(int mappingClassNumber) {
        this.mappingClassNumber = mappingClassNumber;
    }

    public ElementSymbol getMappingClassSymbol() {
        return this.mappingClassSymbol;
    }

    public void setMappingClassSymbol(ElementSymbol mappingClassSymbol) {
        this.mappingClassSymbol = mappingClassSymbol;
    }

    public boolean isStagedResult() {
        return this.stagedResult;
    }
}
