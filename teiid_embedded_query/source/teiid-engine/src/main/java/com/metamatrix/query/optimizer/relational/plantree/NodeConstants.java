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

package com.metamatrix.query.optimizer.relational.plantree;

public final class NodeConstants {

    // Can't instantiate
    private NodeConstants() { }

    /** Types of nodes */
    public static final class Types {
        private Types() {}

        static final int NO_TYPE = 0;
        public static final int ACCESS = 2<<0;
        public static final int DUP_REMOVE = 2<<1;
        public static final int JOIN = 2<<2;
        public static final int PROJECT = 2<<3;
        public static final int SELECT = 2<<4;
        public static final int SORT = 2<<5;
        public static final int SOURCE = 2<<6;
        public static final int GROUP = 2<<7;
        public static final int SET_OP = 2<<8;
        public static final int NULL = 2<<9;
        public static final int TUPLE_LIMIT = 2<<10;
    }

    /**
     * Convert a type code into a type string.
     * @param type Type code, as defined in NodeConstants.Types
     * @return String representation for code
     */
    public static final String getNodeTypeString(int type) {
        switch(type) {
            case NodeConstants.Types.ACCESS:        return "Access"; //$NON-NLS-1$
            case NodeConstants.Types.DUP_REMOVE:    return "DupRemoval"; //$NON-NLS-1$
            case NodeConstants.Types.JOIN:          return "Join"; //$NON-NLS-1$
            case NodeConstants.Types.PROJECT:       return "Project"; //$NON-NLS-1$
            case NodeConstants.Types.SELECT:        return "Select"; //$NON-NLS-1$
            case NodeConstants.Types.SORT:          return "Sort"; //$NON-NLS-1$
            case NodeConstants.Types.SOURCE:        return "Source"; //$NON-NLS-1$
            case NodeConstants.Types.GROUP:         return "Group"; //$NON-NLS-1$
            case NodeConstants.Types.SET_OP:        return "SetOperation"; //$NON-NLS-1$
            case NodeConstants.Types.NULL:          return "Null"; //$NON-NLS-1$
            case NodeConstants.Types.TUPLE_LIMIT:   return "TupleLimit"; //$NON-NLS-1$
            default:                                return "Unknown: " + type; //$NON-NLS-1$
        }
    }

    /** Property names for type-specific node properties */
    public enum Info {
        ATOMIC_REQUEST,      // Command
        MODEL_ID,            // Object (model ID)
        PROCEDURE_CRITERIA,
        PROCEDURE_INPUTS,
        PROCEDURE_DEFAULTS,
        
        // Set operation properties 
        SET_OPERATION,      // SetOperation
        USE_ALL,            // Boolean

        // Join node properties
        JOIN_CRITERIA,      // List <CompareCriteria>
        JOIN_TYPE,          // JoinType
        JOIN_STRATEGY,      // JoinStrategyType
        LEFT_EXPRESSIONS,   // List <SingleElementSymbol> 
        RIGHT_EXPRESSIONS,  // List <SingleElementSymbol> 
        DEPENDENT_VALUE_SOURCE, // String
        NON_EQUI_JOIN_CRITERIA,      // List <CompareCriteria>
        SORT_LEFT,  // SortOption
        SORT_RIGHT,     // SortOption
        IS_OPTIONAL,          // Boolean
        IS_LEFT_DISTINCT, 	// Boolean
        IS_RIGHT_DISTINCT, 	// Boolean

        // Project node properties
        PROJECT_COLS,       // List <SingleElementSymbol>
        INTO_GROUP,         // GroupSymbol

        // Select node properties
        SELECT_CRITERIA,    // Criteria
        IS_HAVING,          // Boolean
        //phantom nodes represent the previous position of criteria that has been pushed across a source, group, or union node.
        //phantom nodes are used by RuleCopyCriteria and removed by RuleCleanCriteria.
        IS_PHANTOM,         // Boolean
        IS_COPIED,           // Boolean - used in CopyCriteria to mark which selects have already been copied
        IS_PUSHED,           // true if this node has already been pushed
        IS_DEPENDENT_SET, // Boolean - only used with dependent joins

        // Sort node properties
        ORDER_TYPES,        // List <Boolean>
        SORT_ORDER,         // List <SingleElementSymbol>
        UNRELATED_SORT,     // Boolean
        IS_DUP_REMOVAL,		// Boolean

        // Source node properties
        SYMBOL_MAP,         // SymbolMap
        VIRTUAL_COMMAND,    // Command
        MAKE_DEP,           // ??? List of Groups ???
        PROCESSOR_PLAN,     // ProcessorPlan for non-relational sub plan
        NESTED_COMMAND,     // Command for nested processor plan
        MAKE_NOT_DEP,       // Source should not be dependent
        INLINE_VIEW,        // If the source node represents an inline view
        
        // Group node properties
        GROUP_COLS,         // List <SingleElementSymbol>

        // Special constant used in converting plan to process for all nodes
        OUTPUT_COLS,        // List <SingleElementSymbol>

        // Plan Node Cost Estimate Constants
        EST_SET_SIZE,        // Integer represents the estimated set size this node would produce for a sibling node as the indenpendent node in a dependent join scenario
        EST_DEP_CARDINALITY, // Float value that represents the estimated cardinality (amount of rows) produced by this node as the dependent node in a dependent join scenario
        EST_DEP_JOIN_COST,   // Float value that represents the estimated cost of a dependent join (the join strategy for this could be Nested Loop or Merge)
        EST_JOIN_COST,       // Float value that represents the estimated cost of a merge join (the join strategy for this could be Nested Loop or Merge)
        EST_CARDINALITY,     // Float represents the estimated cardinality (amount of rows) produced by this node
        EST_SELECTIVITY,     // Float that represents the selectivity of a criteria node 
        
        // Tuple limit and offset
        MAX_TUPLE_LIMIT,     // Expression that evaluates to the max number of tuples generated
        OFFSET_TUPLE_COUNT,  // Expression that evaluates to the tuple offset of the starting tuple

        // Common AP Information
        ACCESS_PATTERNS,     // Collection <List <Object element ID> >
        ACCESS_PATTERN_USED, // List <Object element ID>
        REQUIRED_ACCESS_PATTERN_GROUPS
    }
}