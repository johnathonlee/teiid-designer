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

package com.metamatrix.query.optimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.teiid.connector.api.SourceSystemFunctions;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.api.exception.MetaMatrixException;
import com.metamatrix.api.exception.query.QueryParserException;
import com.metamatrix.api.exception.query.QueryPlannerException;
import com.metamatrix.api.exception.query.QueryResolverException;
import com.metamatrix.api.exception.query.QueryValidatorException;
import com.metamatrix.common.types.DataTypeManager;
import com.metamatrix.core.MetaMatrixRuntimeException;
import com.metamatrix.query.analysis.AnalysisRecord;
import com.metamatrix.query.mapping.relational.QueryNode;
import com.metamatrix.query.metadata.QueryMetadataInterface;
import com.metamatrix.query.optimizer.capabilities.BasicSourceCapabilities;
import com.metamatrix.query.optimizer.capabilities.CapabilitiesFinder;
import com.metamatrix.query.optimizer.capabilities.DefaultCapabilitiesFinder;
import com.metamatrix.query.optimizer.capabilities.FakeCapabilitiesFinder;
import com.metamatrix.query.optimizer.capabilities.SourceCapabilities;
import com.metamatrix.query.optimizer.capabilities.SourceCapabilities.Capability;
import com.metamatrix.query.optimizer.relational.AliasGenerator;
import com.metamatrix.query.optimizer.relational.rules.CapabilitiesUtil;
import com.metamatrix.query.optimizer.relational.rules.NewCalculateCostUtil;
import com.metamatrix.query.parser.QueryParser;
import com.metamatrix.query.processor.ProcessorPlan;
import com.metamatrix.query.processor.relational.AccessNode;
import com.metamatrix.query.processor.relational.DependentAccessNode;
import com.metamatrix.query.processor.relational.GroupingNode;
import com.metamatrix.query.processor.relational.JoinNode;
import com.metamatrix.query.processor.relational.JoinStrategy;
import com.metamatrix.query.processor.relational.MergeJoinStrategy;
import com.metamatrix.query.processor.relational.NestedLoopJoinStrategy;
import com.metamatrix.query.processor.relational.NullNode;
import com.metamatrix.query.processor.relational.PartitionedSortJoin;
import com.metamatrix.query.processor.relational.PlanExecutionNode;
import com.metamatrix.query.processor.relational.ProjectIntoNode;
import com.metamatrix.query.processor.relational.ProjectNode;
import com.metamatrix.query.processor.relational.RelationalNode;
import com.metamatrix.query.processor.relational.RelationalPlan;
import com.metamatrix.query.processor.relational.SelectNode;
import com.metamatrix.query.processor.relational.SortNode;
import com.metamatrix.query.processor.relational.UnionAllNode;
import com.metamatrix.query.processor.relational.SortUtility.Mode;
import com.metamatrix.query.resolver.QueryResolver;
import com.metamatrix.query.resolver.util.BindVariableVisitor;
import com.metamatrix.query.rewriter.QueryRewriter;
import com.metamatrix.query.sql.lang.Command;
import com.metamatrix.query.sql.symbol.GroupSymbol;
import com.metamatrix.query.sql.visitor.GroupCollectorVisitor;
import com.metamatrix.query.sql.visitor.ValueIteratorProviderCollectorVisitor;
import com.metamatrix.query.unittest.FakeMetadataFacade;
import com.metamatrix.query.unittest.FakeMetadataFactory;
import com.metamatrix.query.unittest.FakeMetadataObject;
import com.metamatrix.query.unittest.FakeMetadataStore;
import com.metamatrix.query.util.CommandContext;
import com.metamatrix.query.validator.Validator;
import com.metamatrix.query.validator.ValidatorReport;

public class TestOptimizer extends TestCase {

    public interface DependentJoin {}
    public interface DependentSelectNode {}
    public interface DependentProjectNode {}
    public interface DupRemoveNode {}
    public interface DupRemoveSortNode {}
    
    public static final int[] FULL_PUSHDOWN = new int[] {
                                            1,      // Access
                                            0,      // DependentAccess
                                            0,      // DependentSelect
                                            0,      // DependentProject
                                            0,      // DupRemove
                                            0,      // Grouping
                                            0,      // NestedLoopJoinStrategy
                                            0,      // MergeJoinStrategy
                                            0,      // Null
                                            0,      // PlanExecution
                                            0,      // Project
                                            0,      // Select
                                            0,      // Sort
                                            0       // UnionAll
                                        };
    
    public enum ComparisonMode { EXACT_COMMAND_STRING, CORRECTED_COMMAND_STRING, FAILED_PLANNING }
    
    public static final boolean SHOULD_SUCCEED = true;
    public static final boolean SHOULD_FAIL = false;

	// ################################## FRAMEWORK ################################
	
	public TestOptimizer(String name) { 
		super(name);
	}	

	// ################################## TEST HELPERS ################################

    public static BasicSourceCapabilities getTypicalCapabilities() {        
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SELECT_DISTINCT, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);    
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);    
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, true); 
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.CRITERIA_BETWEEN, true);    
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);    
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);    
        caps.setCapabilitySupport(Capability.CRITERIA_LIKE, true);    
        caps.setCapabilitySupport(Capability.CRITERIA_LIKE_ESCAPE, true);    
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);    
        caps.setCapabilitySupport(Capability.CRITERIA_ISNULL, true);    
        caps.setCapabilitySupport(Capability.CRITERIA_OR, true);    
        caps.setCapabilitySupport(Capability.CRITERIA_NOT, true);    
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);    
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY_UNRELATED, true);
        
        // set typical max set size
        caps.setSourceProperty(Capability.MAX_IN_CRITERIA_SIZE, new Integer(1000));
        return caps;    
    }
    
    public static CapabilitiesFinder getGenericFinder(boolean supportsJoins) {
    	final BasicSourceCapabilities caps = getTypicalCapabilities();
    	if (!supportsJoins) {
	    	caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, false);
		    caps.setCapabilitySupport(Capability.QUERY_ORDERBY, false);
    	}
        CapabilitiesFinder finder = new CapabilitiesFinder() {
            public SourceCapabilities findCapabilities(String modelName) throws MetaMatrixComponentException {
                return caps;
            }
        };
        return finder;
    }
    
    public static CapabilitiesFinder getGenericFinder() {
    	return getGenericFinder(true);
    }

	public static ProcessorPlan helpPlan(String sql, QueryMetadataInterface md, String[] expectedAtomic) {
		return helpPlan(sql, md, null, getGenericFinder(), expectedAtomic, SHOULD_SUCCEED);
	}
	
	public static ProcessorPlan helpPlan(String sql,
			FakeMetadataFacade md, String[] expected,
			CapabilitiesFinder capFinder,
			ComparisonMode mode) throws QueryParserException, QueryResolverException, QueryValidatorException, MetaMatrixComponentException {
		return helpPlan(sql, md, null, capFinder, expected, mode);
	}    
	
	public static ProcessorPlan helpPlan(String sql, QueryMetadataInterface md, String[] expectedAtomic, ComparisonMode mode) throws QueryParserException, QueryResolverException, QueryValidatorException, MetaMatrixComponentException {
        return helpPlan(sql, md, null, getGenericFinder(), expectedAtomic, mode);
    }
	
    public static ProcessorPlan helpPlan(String sql, QueryMetadataInterface md, List bindings, CapabilitiesFinder capFinder, String[] expectedAtomic, boolean shouldSucceed) {
        Command command;
        try {
            command = helpGetCommand(sql, md, bindings);
        } catch (MetaMatrixException err) {
            throw new MetaMatrixRuntimeException(err);
        }

        return helpPlanCommand(command, md, capFinder, expectedAtomic, shouldSucceed ? ComparisonMode.CORRECTED_COMMAND_STRING : ComparisonMode.FAILED_PLANNING);
    } 
    
    public static ProcessorPlan helpPlan(String sql, QueryMetadataInterface md, List bindings, CapabilitiesFinder capFinder, String[] expectedAtomic, ComparisonMode mode) throws QueryParserException, QueryResolverException, QueryValidatorException, MetaMatrixComponentException {
        Command command = helpGetCommand(sql, md, bindings);

        return helpPlanCommand(command, md, capFinder, expectedAtomic, mode);
    } 

    
    public static Command helpGetCommand(String sql, QueryMetadataInterface md, List bindings) throws QueryParserException, QueryResolverException, MetaMatrixComponentException, QueryValidatorException { 
		if(DEBUG) System.out.println("\n####################################\n" + sql);	 //$NON-NLS-1$
		Command command = QueryParser.getQueryParser().parseCommand(sql);
		
		// resolve
		QueryResolver.resolveCommand(command, md);
        
        ValidatorReport repo = Validator.validate(command, md);

        Collection failures = new ArrayList();
        repo.collectInvalidObjects(failures);
        if (failures.size() > 0){
            fail("Exception during validation (" + repo); //$NON-NLS-1$
        }
        
        // bind variables
        if(bindings != null) {
            BindVariableVisitor.bindReferences(command, bindings, md);
        }                       	

		// rewrite
		command = QueryRewriter.rewrite(command, null, md, new CommandContext());

        return command;
    }

    public static ProcessorPlan helpPlanCommand(Command command, QueryMetadataInterface md, CapabilitiesFinder capFinder, String[] expectedAtomic, ComparisonMode mode) {
        if (capFinder == null){
            capFinder = getGenericFinder();
        }
        
        // Collect atomic queries
        ProcessorPlan plan = getPlan(command, md, capFinder, mode != ComparisonMode.FAILED_PLANNING);
               
        if (mode == ComparisonMode.CORRECTED_COMMAND_STRING) {
            checkAtomicQueries(expectedAtomic, plan, md, capFinder);
        } else if (mode == ComparisonMode.EXACT_COMMAND_STRING) {
            checkAtomicQueries(expectedAtomic, plan);
        }

        return plan;
    }
    
    public static void checkAtomicQueries(String[] expectedAtomic,
                                          ProcessorPlan plan) {
       Set actualQueries = getAtomicQueries(plan);
       
       if (actualQueries.size() != 1 || expectedAtomic.length != 1) {
           // Compare atomic queries
           HashSet<String> expectedQueries = new HashSet<String>(Arrays.asList(expectedAtomic));
           assertEquals("Did not get expected atomic queries: ", expectedQueries, actualQueries); //$NON-NLS-1$
       } else {
           assertEquals("Did not get expected atomic query: ", expectedAtomic[0], (String)actualQueries.iterator().next()); //$NON-NLS-1$
       }
   }

    public static void checkAtomicQueries(String[] expectedAtomic,
                                           ProcessorPlan plan, QueryMetadataInterface md, CapabilitiesFinder capFinder) {
        Set actualQueries = getAtomicQueries(plan);
        
        HashSet<String> expectedQueries = new HashSet<String>();
        
        // Compare atomic queries
        for (int i = 0; i < expectedAtomic.length; i++) {
            final String sql = expectedAtomic[i];
            Command command;
            try {
                command = helpGetCommand(sql, md, null);
                Collection groups = GroupCollectorVisitor.getGroupsIgnoreInlineViews(command, false);
                final GroupSymbol symbol = (GroupSymbol)groups.iterator().next();
                Object modelId = md.getModelID(symbol.getMetadataID());
                boolean supportsGroupAliases = CapabilitiesUtil.supportsGroupAliases(modelId, md, capFinder);
                command.acceptVisitor(new AliasGenerator(supportsGroupAliases));
                expectedQueries.add(command.toString());
            } catch (Exception err) {
                throw new RuntimeException(err);
            }
        } 
        
        assertEquals("Did not get expected atomic queries: ", expectedQueries, actualQueries); //$NON-NLS-1$
    }
    
    public static ProcessorPlan getPlan(Command command, QueryMetadataInterface md, CapabilitiesFinder capFinder, boolean shouldSucceed) {
		// plan
		ProcessorPlan plan = null;
        AnalysisRecord analysisRecord = new AnalysisRecord(false, false, DEBUG);
		if (shouldSucceed) {
			try {
				//do planning
				plan = QueryOptimizer.optimizePlan(command, md, null, capFinder, analysisRecord, new CommandContext());

			} catch (Throwable e) {
				throw new MetaMatrixRuntimeException(e);
			} finally {
                if(DEBUG) {
                    System.out.println(analysisRecord.getDebugLog());
                }
			}
		} else {
			Exception exception = null;
			try {
				//do planning
				QueryOptimizer.optimizePlan(command, md, null, capFinder, analysisRecord, null);

			} catch (QueryPlannerException e) {
				exception = e;
			} catch (MetaMatrixComponentException e) {
				exception = e;
			} catch (Throwable e) {
                throw new MetaMatrixRuntimeException(e);
            } finally {
                if(DEBUG) {
                    System.out.println(analysisRecord.getDebugLog());
                }
			}
			assertNotNull("Expected exception but did not get one.", exception); //$NON-NLS-1$
			return null;
		}
        
        assertNotNull("Output elements are null", plan.getOutputElements()); //$NON-NLS-1$
        		
		if(DEBUG) System.out.println("\n" + plan);	 //$NON-NLS-1$
		        
		return plan;
	}
    
    public static Set getAtomicQueries(ProcessorPlan plan) {
        Iterator atomicQueries = getAtomicCommands(plan).iterator();
        
        Set stringQueries = new HashSet();
        
        while (atomicQueries.hasNext()) {
           stringQueries.add(atomicQueries.next().toString());
        }
        
        return stringQueries;
    }
    
    public static Set getAtomicCommands(ProcessorPlan plan) {
        Set atomicQueries = new HashSet();        
        if(plan instanceof RelationalPlan) {
            getAtomicCommands( ((RelationalPlan)plan).getRootNode(), atomicQueries );    
        }   
        return atomicQueries;        
    }
    
    private static void getAtomicCommands(RelationalNode node, Set atomicQueries) {
        if(node instanceof AccessNode) {
            AccessNode accessNode = (AccessNode) node;
            atomicQueries.add( accessNode.getCommand());   
        } 
        
        // Recurse through children
        RelationalNode[] children = node.getChildren();
        for(int i=0; i<children.length; i++) {
            if(children[i] != null) {
                getAtomicCommands(children[i], atomicQueries);
            } else {
                break;
            }
        }
    }
    
    // Counts are (mostly) alphabetical:
    //   Access, DependentAccess, DependentSelect, DependentProject, DupRemove, Grouping, NestedLoopJoinStrategy, Null, PlanExecution, Project, Select, Sort, UnionAll
    private static final Class[] COUNT_TYPES = new Class[] {
        AccessNode.class,
        DependentAccessNode.class,
        DependentSelectNode.class,
        DependentProjectNode.class,
        DupRemoveNode.class,
        GroupingNode.class,
        NestedLoopJoinStrategy.class,
        MergeJoinStrategy.class,
        NullNode.class,
        PlanExecutionNode.class,
        ProjectNode.class,
        SelectNode.class,
        SortNode.class,
        UnionAllNode.class
    };
            
    public static void checkNodeTypes(ProcessorPlan root, int[] expectedCounts) {
        checkNodeTypes(root, expectedCounts, COUNT_TYPES);
    }    
    
    public static void checkNodeTypes(ProcessorPlan root, int[] expectedCounts, Class[] types) {
        if(! (root instanceof RelationalPlan)) {
            return;
        }
                
        int[] actualCounts = new int[types.length];
        collectCounts(((RelationalPlan)root).getRootNode(), actualCounts, types);

        for(int i=0; i<expectedCounts.length; i++) {
            assertEquals("Did not find the correct number of nodes for type " + types[i], //$NON-NLS-1$
                        expectedCounts[i], actualCounts[i]);
        }
    }    
    
    /**
     * Method collectCounts.
     * @param relationalNode
     * @return int[]
     */
    public static void collectCounts(RelationalNode relationalNode, int[] counts, Class<?>[] types) {
        Class<?> nodeType = relationalNode.getClass();
        if(nodeType.equals(JoinNode.class)) {
        	JoinStrategy strategy = ((JoinNode)relationalNode).getJoinStrategy();
            if (strategy instanceof NestedLoopJoinStrategy) {
                updateCounts(NestedLoopJoinStrategy.class, counts, types);
            } else if (strategy instanceof MergeJoinStrategy) {
                updateCounts(MergeJoinStrategy.class, counts, types);
                if (strategy instanceof PartitionedSortJoin) {
                    updateCounts(PartitionedSortJoin.class, counts, types);
                } 
            } 
            if (((JoinNode)relationalNode).isDependent()) {
                updateCounts(DependentJoin.class, counts, types);
            }
        }else if (nodeType.equals(ProjectNode.class)){
        	if (ValueIteratorProviderCollectorVisitor.getValueIteratorProviders(((ProjectNode)relationalNode).getSelectSymbols()).isEmpty()) {
        		updateCounts(ProjectNode.class, counts, types);
        	} else {
        		updateCounts(DependentProjectNode.class, counts, types);
        	}
        }else if (nodeType.equals(SelectNode.class)){
        	if (ValueIteratorProviderCollectorVisitor.getValueIteratorProviders(((SelectNode)relationalNode).getCriteria()).isEmpty()) {
        		updateCounts(SelectNode.class, counts, types);
        	} else {
        		updateCounts(DependentSelectNode.class, counts, types);
        	}
        } else if (nodeType.equals(SortNode.class)) {
        	Mode mode = ((SortNode)relationalNode).getMode();
        	switch(mode) {
        	case DUP_REMOVE:
                updateCounts(DupRemoveNode.class, counts, types);
        		break;
        	case DUP_REMOVE_SORT:
                updateCounts(DupRemoveSortNode.class, counts, types);
        		break;
        	case SORT:
                updateCounts(SortNode.class, counts, types);
        		break;
        	}
        } else {
            updateCounts(nodeType, counts, types);
        }
        
        RelationalNode[] children = relationalNode.getChildren();
        for(int i=0; i<children.length; i++) {
            if(children[i] != null) {
                collectCounts(children[i], counts, types);
            } else {
                break;
            }
        }
    }         
    
    private static void updateCounts(Class nodeClass, int[] counts, Class[] types) {
        for(int i=0; i<types.length; i++) {
            if(types[i].equals(nodeClass)) {
                counts[i] = counts[i] + 1;
                return;
            }    
        }
    }    
    
    public static void checkDependentJoinCount(ProcessorPlan plan, int expectedCount) {
        checkNodeTypes(plan, new int[] {expectedCount}, new Class[] {DependentJoin.class});
    }
                
    public static void checkSubPlanCount(ProcessorPlan plan, int expectedCount) {
        assertEquals("Checking plan count", expectedCount, plan.getChildPlans().size()); //$NON-NLS-1$        
    }

	public static FakeMetadataFacade example1() {
		// Create models
		FakeMetadataObject pm1 = FakeMetadataFactory.createPhysicalModel("pm1"); //$NON-NLS-1$
        FakeMetadataObject pm2 = FakeMetadataFactory.createPhysicalModel("pm2"); //$NON-NLS-1$
		FakeMetadataObject vm1 = FakeMetadataFactory.createVirtualModel("vm1");	 //$NON-NLS-1$

		// Create physical groups
		FakeMetadataObject pm1g1 = FakeMetadataFactory.createPhysicalGroup("pm1.g1", pm1); //$NON-NLS-1$
		FakeMetadataObject pm1g2 = FakeMetadataFactory.createPhysicalGroup("pm1.g2", pm1); //$NON-NLS-1$
		FakeMetadataObject pm1g3 = FakeMetadataFactory.createPhysicalGroup("pm1.g3", pm1); //$NON-NLS-1$
        FakeMetadataObject pm1g4 = FakeMetadataFactory.createPhysicalGroup("pm1.g4", pm1); //$NON-NLS-1$
        FakeMetadataObject pm1g5 = FakeMetadataFactory.createPhysicalGroup("pm1.g5", pm1); //$NON-NLS-1$
        FakeMetadataObject pm1g6 = FakeMetadataFactory.createPhysicalGroup("pm1.g6", pm1); //$NON-NLS-1$
        FakeMetadataObject pm1g7 = FakeMetadataFactory.createPhysicalGroup("pm1.g7", pm1); //$NON-NLS-1$
        FakeMetadataObject pm1g8 = FakeMetadataFactory.createPhysicalGroup("pm1.g8", pm1); //$NON-NLS-1$
        FakeMetadataObject pm2g1 = FakeMetadataFactory.createPhysicalGroup("pm2.g1", pm2); //$NON-NLS-1$
        FakeMetadataObject pm2g2 = FakeMetadataFactory.createPhysicalGroup("pm2.g2", pm2); //$NON-NLS-1$
        FakeMetadataObject pm2g3 = FakeMetadataFactory.createPhysicalGroup("pm2.g3", pm2); //$NON-NLS-1$
				
		// Create physical elements
		List pm1g1e = FakeMetadataFactory.createElements(pm1g1, 
			new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
		List pm1g2e = FakeMetadataFactory.createElements(pm1g2, 
			new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
		List pm1g3e = FakeMetadataFactory.createElements(pm1g3, 
			new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
        List pm1g4e = FakeMetadataFactory.createElements(pm1g4, 
            new String[] { "e1" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.STRING });
        List pm1g5e = FakeMetadataFactory.createElements(pm1g5, 
            new String[] { "e1" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.STRING });
        List pm1g6e = FakeMetadataFactory.createElements(pm1g6, 
            new String[] { "e1" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.STRING });
        List pm1g7e = FakeMetadataFactory.createElements(pm1g7, 
            new String[] { "e1" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.STRING });
        List pm1g8e = FakeMetadataFactory.createElements(pm1g8, 
            new String[] { "e1" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.STRING });
        List pm2g1e = FakeMetadataFactory.createElements(pm2g1, 
            new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
        List pm2g2e = FakeMetadataFactory.createElements(pm2g2, 
            new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
        List pm2g3e = FakeMetadataFactory.createElements(pm2g3, 
            new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });

		// Create virtual groups
		QueryNode vm1g1n1 = new QueryNode("vm1.g1", "SELECT * FROM pm1.g1"); //$NON-NLS-1$ //$NON-NLS-2$
		FakeMetadataObject vm1g1 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.g1", vm1, vm1g1n1); //$NON-NLS-1$

		QueryNode vm1g2n1 = new QueryNode("vm1.g2", "SELECT * FROM pm1.g1"); //$NON-NLS-1$ //$NON-NLS-2$
		FakeMetadataObject vm1g2 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.g2", vm1, vm1g2n1); //$NON-NLS-1$

		//defect 8096
		QueryNode vm1sub1n1 = new QueryNode("vm1.sub1", "SELECT * FROM vm1.g1 WHERE e1 IN (SELECT e1 FROM vm1.g3)"); //$NON-NLS-1$ //$NON-NLS-2$
		FakeMetadataObject vm1sub1 = FakeMetadataFactory.createVirtualGroup("vm1.sub1", vm1, vm1sub1n1); //$NON-NLS-1$

		QueryNode vm1g3n1 = new QueryNode("vm1.g3", "SELECT * FROM pm1.g2"); //$NON-NLS-1$ //$NON-NLS-2$
		FakeMetadataObject vm1g3 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.g3", vm1, vm1g3n1); //$NON-NLS-1$

        QueryNode vm1g4n1 = new QueryNode("vm1.g4", "SELECT pm1.g1.e1, pm1.g2.e1 FROM pm1.g1, pm1.g2 WHERE pm1.g1.e1=pm1.g2.e1"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1g4 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.g4", vm1, vm1g4n1); //$NON-NLS-1$

        QueryNode vm1g5n1 = new QueryNode("vm1.g5", "SELECT DISTINCT pm1.g1.e1 FROM pm1.g1 ORDER BY pm1.g1.e1"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1g5 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.g5", vm1, vm1g5n1); //$NON-NLS-1$

        QueryNode vm1g6n1 = new QueryNode("vm1.g6", "SELECT e1, convert(e2, string), 3 as e3, ((e2+e4)/3) as e4 FROM pm1.g1"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1g6 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.g6", vm1, vm1g6n1); //$NON-NLS-1$

		QueryNode vm1u1n1 = new QueryNode("vm1.u1", "SELECT * FROM pm1.g1 UNION SELECT * FROM pm1.g2 UNION ALL SELECT * FROM pm1.g3"); //$NON-NLS-1$ //$NON-NLS-2$
		FakeMetadataObject vm1u1 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.u1", vm1, vm1u1n1); //$NON-NLS-1$

		QueryNode vm1u2n1 = new QueryNode("vm1.u2", "SELECT * FROM pm1.g1 UNION SELECT * FROM pm1.g2"); //$NON-NLS-1$ //$NON-NLS-2$
		FakeMetadataObject vm1u2 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.u2", vm1, vm1u2n1); //$NON-NLS-1$

		QueryNode vm1u3n1 = new QueryNode("vm1.u3", "SELECT e1 FROM pm1.g1 UNION SELECT convert(e2, string) as x FROM pm1.g2"); //$NON-NLS-1$ //$NON-NLS-2$
		FakeMetadataObject vm1u3 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.u3", vm1, vm1u3n1); //$NON-NLS-1$

        QueryNode vm1u4n1 = new QueryNode("vm1.u4", "SELECT concat(e1, 'x') as v1 FROM pm1.g1 UNION ALL SELECT e1 FROM pm1.g2"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1u4 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.u4", vm1, vm1u4n1); //$NON-NLS-1$

        QueryNode vm1u5n1 = new QueryNode("vm1.u5", "SELECT concat(e1, 'x') as v1 FROM pm1.g1 UNION ALL SELECT concat('a', e1) FROM pm1.g2"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1u5 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.u5", vm1, vm1u5n1); //$NON-NLS-1$

        QueryNode vm1u6n1 = new QueryNode("vm1.u6", "SELECT x1.e1 AS elem, 'xyz' AS const FROM pm1.g1 AS x1"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1u6 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.u6", vm1, vm1u6n1); //$NON-NLS-1$

        QueryNode vm1u7n1 = new QueryNode("vm1.u7", "SELECT 's1' AS const, e1 FROM pm1.g1 UNION ALL SELECT 's2', e1 FROM pm1.g2"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1u7 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.u7", vm1, vm1u7n1); //$NON-NLS-1$

        QueryNode vm1u8n1 = new QueryNode("vm1.u8", "SELECT const, e1 FROM vm1.u7 UNION ALL SELECT 's3', e1 FROM pm1.g3"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1u8 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.u8", vm1, vm1u8n1); //$NON-NLS-1$

        QueryNode vm1u9n1 = new QueryNode("vm1.u9", "SELECT e1 as a, e1 as b FROM pm1.g1 UNION ALL SELECT e1, e1 FROM pm1.g2"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1u9 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.u9", vm1, vm1u9n1); //$NON-NLS-1$

        QueryNode vm1a1n1 = new QueryNode("vm1.a1", "SELECT e1, SUM(e2) AS sum_e2 FROM pm1.g1 GROUP BY e1"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1a1 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.a1", vm1, vm1a1n1); //$NON-NLS-1$
        
        QueryNode vm1a2n1 = new QueryNode("vm1.a2", "SELECT e1, SUM(e2) AS sum_e2 FROM pm1.g1 GROUP BY e1 HAVING SUM(e2) > 5"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1a2 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.a2", vm1, vm1a2n1); //$NON-NLS-1$

        QueryNode vm1a3n1 = new QueryNode("vm1.a3", "SELECT SUM(e2) AS sum_e2 FROM pm1.g1"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1a3 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.a3", vm1, vm1a3n1); //$NON-NLS-1$
        
        QueryNode vm1a4n1 = new QueryNode("vm1.a4", "SELECT COUNT(*) FROM pm1.g1"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1a4 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.a4", vm1, vm1a4n1); //$NON-NLS-1$

        QueryNode vm1a5n1 = new QueryNode("vm1.a5", "SELECT vm1.a4.count FROM vm1.a4 UNION ALL SELECT COUNT(*) FROM pm1.g1"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1a5 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.a5", vm1, vm1a5n1); //$NON-NLS-1$

        QueryNode vm1a6n1 = new QueryNode("vm1.a6", "SELECT COUNT(*) FROM vm1.u2"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1a6 = FakeMetadataFactory.createUpdatableVirtualGroup("vm1.a6", vm1, vm1a6n1); //$NON-NLS-1$
        
        QueryNode vm1g7n1 = new QueryNode("vm1.g7", "select DECODESTRING(e1, 'S,Pay,P,Rec') as e1, e2 FROM pm1.g1"); //$NON-NLS-1$ //$NON-NLS-2$
        FakeMetadataObject vm1g7 = FakeMetadataFactory.createVirtualGroup("vm1.g7", vm1, vm1g7n1); //$NON-NLS-1$
        
		// Create virtual elements
		List vm1g1e = FakeMetadataFactory.createElements(vm1g1, 
			new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
		List vm1g2e = FakeMetadataFactory.createElements(vm1g2, 
			new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
		List vm1g3e = FakeMetadataFactory.createElements(vm1g3, 
			new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
		//for defect 8096
		List vm1sub1e = FakeMetadataFactory.createElements(vm1sub1, 
			new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
        List vm1g4e = FakeMetadataFactory.createElements(vm1g4, 
            new String[] { "e1", "e1" }, //$NON-NLS-1$ //$NON-NLS-2$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
        List vm1g5e = FakeMetadataFactory.createElements(vm1g5, 
            new String[] { "e1" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.STRING});
        List vm1g6e = FakeMetadataFactory.createElements(vm1g6, 
            new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.DOUBLE });
        List vm1g7e = FakeMetadataFactory.createElements(vm1g7, 
            new String[] { "e1", "e2"}, //$NON-NLS-1$ //$NON-NLS-2$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER});
        List vm1u1e = FakeMetadataFactory.createElements(vm1u1, 
			new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
		List vm1u2e = FakeMetadataFactory.createElements(vm1u2, 
			new String[] { "e1", "e2", "e3", "e4" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER, DataTypeManager.DefaultDataTypes.BOOLEAN, DataTypeManager.DefaultDataTypes.DOUBLE });
		List vm1u3e = FakeMetadataFactory.createElements(vm1u3, 
			new String[] { "e1" }, //$NON-NLS-1$
			new String[] { DataTypeManager.DefaultDataTypes.STRING });
        List vm1u4e = FakeMetadataFactory.createElements(vm1u4, 
            new String[] { "v1" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.STRING });
        List vm1u5e = FakeMetadataFactory.createElements(vm1u5, 
            new String[] { "v1" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.STRING });
        List vm1u6e = FakeMetadataFactory.createElements(vm1u6, 
            new String[] { "elem", "const" }, //$NON-NLS-1$ //$NON-NLS-2$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
        List vm1u7e = FakeMetadataFactory.createElements(vm1u7, 
            new String[] { "const", "e1" }, //$NON-NLS-1$ //$NON-NLS-2$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
        List vm1u8e = FakeMetadataFactory.createElements(vm1u8, 
            new String[] { "const", "e1" }, //$NON-NLS-1$ //$NON-NLS-2$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
        List vm1u9e = FakeMetadataFactory.createElements(vm1u9, 
            new String[] { "a", "b" }, //$NON-NLS-1$ //$NON-NLS-2$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.STRING });
        List vm1a1e = FakeMetadataFactory.createElements(vm1a1, 
            new String[] { "e1", "sum_e2" }, //$NON-NLS-1$ //$NON-NLS-2$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER });
        List vm1a2e = FakeMetadataFactory.createElements(vm1a2, 
            new String[] { "e1", "sum_e2" }, //$NON-NLS-1$ //$NON-NLS-2$
            new String[] { DataTypeManager.DefaultDataTypes.STRING, DataTypeManager.DefaultDataTypes.INTEGER });
        List vm1a3e = FakeMetadataFactory.createElements(vm1a3, 
            new String[] { "sum_e2" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER });
        List vm1a4e = FakeMetadataFactory.createElements(vm1a4, 
            new String[] { "count" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER });
        List vm1a5e = FakeMetadataFactory.createElements(vm1a5, 
            new String[] { "count" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER });
        List vm1a6e = FakeMetadataFactory.createElements(vm1a6, 
            new String[] { "count" }, //$NON-NLS-1$
            new String[] { DataTypeManager.DefaultDataTypes.INTEGER });
			
		// Add all objects to the store
		FakeMetadataStore store = new FakeMetadataStore();
		store.addObject(pm1);
		store.addObject(pm1g1);		
		store.addObjects(pm1g1e);
		store.addObject(pm1g2);		
		store.addObjects(pm1g2e);
 		store.addObject(pm1g3);		
		store.addObjects(pm1g3e);
        store.addObject(pm1g4);     
        store.addObjects(pm1g4e);
        store.addObject(pm1g5);     
        store.addObjects(pm1g5e);
        store.addObject(pm1g6);     
        store.addObjects(pm1g6e);
        store.addObject(vm1g7);
        store.addObjects(vm1g7e);
        store.addObject(pm1g7);     
        store.addObjects(pm1g7e);
        store.addObject(pm1g8);     
        store.addObjects(pm1g8e);
        
        store.addObject(pm2);
        store.addObject(pm2g1);     
        store.addObjects(pm2g1e);
        store.addObject(pm2g2);     
        store.addObjects(pm2g2e);
        store.addObject(pm2g3);     
        store.addObjects(pm2g3e);
       	
		store.addObject(vm1);
		store.addObject(vm1g1);
		store.addObjects(vm1g1e);
		store.addObject(vm1g2);
		store.addObjects(vm1g2e);
		store.addObject(vm1g3);
		store.addObjects(vm1g3e);

		//for defect 8096
		store.addObject(vm1sub1);
		store.addObjects(vm1sub1e);
		
        store.addObject(vm1g4);
        store.addObjects(vm1g4e);
        store.addObject(vm1g5);
        store.addObjects(vm1g5e);
        store.addObject(vm1g6);
        store.addObjects(vm1g6e);
		store.addObject(vm1u1);
		store.addObjects(vm1u1e);
		store.addObject(vm1u2);
		store.addObjects(vm1u2e);
		store.addObject(vm1u3);
		store.addObjects(vm1u3e);
        store.addObject(vm1u4);
        store.addObjects(vm1u4e);
        store.addObject(vm1u5);
        store.addObjects(vm1u5e);
        store.addObject(vm1u6);
        store.addObjects(vm1u6e);
        store.addObject(vm1u7);
        store.addObjects(vm1u7e);
        store.addObject(vm1u8);
        store.addObjects(vm1u8e);
        store.addObject(vm1u9);
        store.addObjects(vm1u9e);
        store.addObject(vm1a1);
        store.addObjects(vm1a1e);   
        store.addObject(vm1a2);
        store.addObjects(vm1a2e);   
        store.addObject(vm1a3);
        store.addObjects(vm1a3e);   
        store.addObject(vm1a4);
        store.addObjects(vm1a4e);   
        store.addObject(vm1a5);
        store.addObjects(vm1a5e);   
        store.addObject(vm1a6);
        store.addObjects(vm1a6e);   
						
		// Create the facade from the store
		return new FakeMetadataFacade(store);
	}	
				
	// ################################## ACTUAL TESTS ################################

	/**
	 * Test defect 8096 - query a virtual group with subquery of another virtual group 
	 */
	public void testVirtualSubqueryINClause_8096() { 
		helpPlan("SELECT * FROM vm1.sub1", example1(), //$NON-NLS-1$
			new String[] {"SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1"} ); //$NON-NLS-1$
	}

	public void testQueryPhysical() { 
		ProcessorPlan plan = helpPlan("SELECT pm1.g1.e1, e2, pm1.g1.e3 as a, e4 as b FROM pm1.g1", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
			new String[] {"SELECT pm1.g1.e1, e2, pm1.g1.e3, e4 FROM pm1.g1"} ); //$NON-NLS-1$

        checkNodeTypes(plan, FULL_PUSHDOWN);    
        
        checkSubPlanCount(plan, 0);
	}
    
	public void testSelectStarPhysical() { 
		ProcessorPlan plan = helpPlan("SELECT * FROM pm1.g1", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
			new String[] { "SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1"} ); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
	}

	public void testQuerySingleSourceVirtual() { 
		ProcessorPlan plan = helpPlan("SELECT * FROM vm1.g1", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
			new String[] { "SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1"} ); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
	}
	
	public void testQueryMultiSourceVirtual() { 
		ProcessorPlan plan = helpPlan("SELECT * FROM vm1.g2", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
			new String[] { "SELECT g_0.e1, g_0.e2, g_1.e3, g_1.e4 FROM pm1.g1 AS g_0, pm1.g2 AS g_1 WHERE g_0.e1 = g_1.e1"} ); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
	}

	public void testPhysicalVirtualJoinWithCriteria() throws Exception { 
		ProcessorPlan plan = helpPlan("SELECT vm1.g2.e1 from vm1.g2, pm1.g3 where vm1.g2.e1=pm1.g3.e1 and vm1.g2.e2 > 0", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
			new String[] { "SELECT g_0.e1 FROM pm1.g1 AS g_0, pm1.g2 AS g_1, pm1.g3 AS g_2 WHERE (g_0.e1 = g_1.e1) AND (g_0.e1 = g_2.e1) AND (g_0.e2 > 0)" }, ComparisonMode.EXACT_COMMAND_STRING ); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
	}
    
    public void testQueryWithExpression() { 
        helpPlan("SELECT e4 FROM pm3.g1 WHERE e4 < convert('2001-11-01 10:30:40.42', timestamp)", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
			new String[] { "SELECT e4 FROM pm3.g1 WHERE e4 < {ts'2001-11-01 10:30:40.42'}"} ); //$NON-NLS-1$
    }
    
    public void testInsert() { 
        helpPlan("Insert into pm1.g1 (pm1.g1.e1, pm1.g1.e2) values (\"MyString\", 1)", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
			new String[] { "INSERT INTO pm1.g1 (pm1.g1.e1, pm1.g1.e2) VALUES ('MyString', 1)"} ); //$NON-NLS-1$
    }
    
    public void testUpdate1() { 
      	helpPlan("Update pm1.g1 Set pm1.g1.e1= LTRIM(\"MyString\"), pm1.g1.e2= 1 where pm1.g1.e3= \"true\"", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
			new String[] { "UPDATE pm1.g1 SET pm1.g1.e1 = 'MyString', pm1.g1.e2 = 1 WHERE pm1.g1.e3 = TRUE"} ); //$NON-NLS-1$
  	}
  	
    public void testUpdate2() { 
        helpPlan("Update pm1.g1 Set pm1.g1.e1= LTRIM(\"MyString\"), pm1.g1.e2= 1 where pm1.g1.e2= convert(pm1.g1.e4, integer)", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
			new String[] { "UPDATE pm1.g1 SET pm1.g1.e1 = 'MyString', pm1.g1.e2 = 1 WHERE pm1.g1.e2 = convert(pm1.g1.e4, integer)"} ); //$NON-NLS-1$
    }
    
    public void testDelete() { 
    	helpPlan("Delete from pm1.g1 where pm1.g1.e1 = cast(pm1.g1.e2 AS string)", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
			new String[] { "DELETE FROM pm1.g1 WHERE pm1.g1.e1 = cast(pm1.g1.e2 AS string)"} ); //$NON-NLS-1$
  	}

	// ############################# TESTS ON EXAMPLE 1 ############################
	
    public void testCopyInAcrossJoin() throws Exception {
        ProcessorPlan plan = helpPlan("select pm1.g1.e1, pm2.g2.e1 from pm1.g1, pm2.g2 where pm1.g1.e1=pm2.g2.e1 and pm1.g1.e1 IN ('a', 'b')", example1(), //$NON-NLS-1$
            new String[] { "SELECT g_0.e1 AS c_0 FROM pm2.g2 AS g_0 WHERE g_0.e1 IN ('a', 'b') ORDER BY c_0", //$NON-NLS-1$
        				   "SELECT g_0.e1 AS c_0 FROM pm1.g1 AS g_0 WHERE g_0.e1 IN ('a', 'b') ORDER BY c_0" }, ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$
        
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }

    public void testCopyMatchAcrossJoin() throws Exception {
        helpPlan("select pm1.g1.e1, pm2.g2.e1 from pm1.g1, pm2.g2 where pm1.g1.e1=pm2.g2.e1 and pm1.g1.e1 LIKE '%1'", example1(), //$NON-NLS-1$
            new String[] { "SELECT g_0.e1 AS c_0 FROM pm1.g1 AS g_0 WHERE g_0.e1 LIKE '%1' ORDER BY c_0", //$NON-NLS-1$
        					"SELECT g_0.e1 AS c_0 FROM pm2.g2 AS g_0 WHERE g_0.e1 LIKE '%1' ORDER BY c_0" }, ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$
    }
 
    public void testCopyOrAcrossJoin() throws Exception {
        helpPlan("select pm1.g1.e1, pm1.g2.e1 from pm1.g1, pm1.g2 where pm1.g1.e1=pm1.g2.e1 and (pm1.g1.e1 = 'abc' OR pm1.g1.e1 = 'def')", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1 WHERE (pm1.g1.e1 = 'abc') OR (pm1.g1.e1 = 'def')", //$NON-NLS-1$
                            "SELECT pm1.g2.e1 FROM pm1.g2 WHERE (pm1.g2.e1 = 'abc') OR (pm1.g2.e1 = 'def')" }, getGenericFinder(false), ComparisonMode.CORRECTED_COMMAND_STRING); //$NON-NLS-1$
    }
 
    public void testCopyMultiElementCritAcrossJoin() throws Exception {
        helpPlan("select pm1.g1.e1, pm1.g2.e1 from pm1.g1, pm1.g2 where pm1.g1.e1=pm1.g2.e1 and pm1.g1.e2=pm1.g2.e2 and (pm1.g1.e1 = 'abc' OR pm1.g1.e2 = 5)", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm1.g2.e1, pm1.g2.e2 FROM pm1.g2 WHERE (pm1.g2.e1 = 'abc') OR (pm1.g2.e2 = 5)", "SELECT pm1.g1.e1, pm1.g1.e2 FROM pm1.g1 WHERE (pm1.g1.e1 = 'abc') OR (pm1.g1.e2 = 5)" }, getGenericFinder(false), ComparisonMode.CORRECTED_COMMAND_STRING); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    public void testCantCopyAcrossJoin1() throws Exception {
        helpPlan("select pm1.g1.e1, pm1.g2.e1 from pm1.g1, pm1.g2 where pm1.g1.e1=pm1.g2.e1 and concat(pm1.g1.e1, pm1.g1.e2) = 'abc'", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm1.g1.e1, pm1.g1.e2 FROM pm1.g1", //$NON-NLS-1$
                            "SELECT pm1.g2.e1 FROM pm1.g2" }, getGenericFinder(false), ComparisonMode.CORRECTED_COMMAND_STRING); //$NON-NLS-1$
    }

    public void testCantCopyAcrossJoin2() throws Exception {
        helpPlan("select pm1.g1.e1, pm1.g2.e1 from pm1.g1, pm1.g2 where pm1.g1.e1=pm1.g2.e1 and (pm1.g1.e1 = 'abc' OR pm1.g1.e2 = 5)", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1 WHERE (pm1.g1.e1 = 'abc') OR (pm1.g1.e2 = 5)", //$NON-NLS-1$
                            "SELECT pm1.g2.e1 FROM pm1.g2" }, getGenericFinder(false), ComparisonMode.CORRECTED_COMMAND_STRING); //$NON-NLS-1$
    }

    public void testPushingCriteriaThroughFrame1() { 
    	helpPlan("select * from vm1.g1, vm1.g2 where vm1.g1.e1='abc' and vm1.g1.e1=vm1.g2.e1", example1(), //$NON-NLS-1$
			new String[] { "SELECT g1__1.e1, g1__1.e2, g1__1.e3, g1__1.e4 FROM pm1.g1 AS g1__1 WHERE g1__1.e1 = 'abc'", //$NON-NLS-1$
							"SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 WHERE pm1.g1.e1 = 'abc'" } ); //$NON-NLS-1$
  	}

    public void testPushingCriteriaThroughFrame2() throws Exception { 
    	helpPlan("select * from vm1.g1, vm1.g3 where vm1.g1.e1='abc' and vm1.g1.e1=vm1.g3.e1", example1(), //$NON-NLS-1$
			new String[] { "SELECT pm1.g2.e1, pm1.g2.e2, pm1.g2.e3, pm1.g2.e4 FROM pm1.g2 WHERE pm1.g2.e1 = 'abc'",  //$NON-NLS-1$
							"SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 WHERE pm1.g1.e1 = 'abc'" }, getGenericFinder(false), ComparisonMode.CORRECTED_COMMAND_STRING ); //$NON-NLS-1$
  	}

    public void testPushingCriteriaThroughFrame3() { 
    	helpPlan("select * from vm1.g1, vm1.g2, vm1.g1 as a where vm1.g1.e1='abc' and vm1.g1.e1=vm1.g2.e1 and vm1.g1.e1=a.e1", example1(), //$NON-NLS-1$
			new String[] { "SELECT g1__1.e1, g1__1.e2, g1__1.e3, g1__1.e4 FROM pm1.g1 AS g1__1 WHERE g1__1.e1 = 'abc'", //$NON-NLS-1$
							"SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 WHERE pm1.g1.e1 = 'abc'", //$NON-NLS-1$
							"SELECT g1__2.e1, g1__2.e2, g1__2.e3, g1__2.e4 FROM pm1.g1 AS g1__2 WHERE g1__2.e1 = 'abc'" } ); //$NON-NLS-1$
  	}

    public void testPushingCriteriaThroughUnion1() { 
    	helpPlan("select e1 from vm1.u1 where e1='abc'", example1(), //$NON-NLS-1$
			new String[] { "SELECT pm1.g3.e1, pm1.g3.e2, pm1.g3.e3, pm1.g3.e4 FROM pm1.g3 WHERE pm1.g3.e1 = 'abc'", //$NON-NLS-1$
							"SELECT DISTINCT pm1.g2.e1, pm1.g2.e2, pm1.g2.e3, pm1.g2.e4 FROM pm1.g2 WHERE pm1.g2.e1 = 'abc'", //$NON-NLS-1$
							"SELECT DISTINCT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 WHERE pm1.g1.e1 = 'abc'" } ); //$NON-NLS-1$
  	}

    public void testPushingCriteriaThroughUnion2() { 
    	helpPlan("select e1 from vm1.u2 where e1='abc'", example1(), //$NON-NLS-1$
			new String[] { "SELECT DISTINCT pm1.g2.e1, pm1.g2.e2, pm1.g2.e3, pm1.g2.e4 FROM pm1.g2 WHERE pm1.g2.e1 = 'abc'", //$NON-NLS-1$
							"SELECT DISTINCT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 WHERE pm1.g1.e1 = 'abc'" } ); //$NON-NLS-1$
  	}

    public void testPushingCriteriaThroughUnion3() { 
    	helpPlan("select e1 from vm1.u1 where e1='abc' and e2=5", example1(), //$NON-NLS-1$
			new String[] { "SELECT pm1.g3.e1, pm1.g3.e2, pm1.g3.e3, pm1.g3.e4 FROM pm1.g3 WHERE (pm1.g3.e1 = 'abc') AND (pm1.g3.e2 = 5)", //$NON-NLS-1$
							"SELECT DISTINCT pm1.g2.e1, pm1.g2.e2, pm1.g2.e3, pm1.g2.e4 FROM pm1.g2 WHERE (pm1.g2.e1 = 'abc') AND (pm1.g2.e2 = 5)", //$NON-NLS-1$
							"SELECT DISTINCT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 WHERE (pm1.g1.e1 = 'abc') AND (pm1.g1.e2 = 5)" } ); //$NON-NLS-1$
  	}

    public void testPushingCriteriaThroughUnion4() { 
    	helpPlan("select e1 from vm1.u1 where e1='abc' or e2=5", example1(), //$NON-NLS-1$
			new String[] { "SELECT pm1.g3.e1, pm1.g3.e2, pm1.g3.e3, pm1.g3.e4 FROM pm1.g3 WHERE (pm1.g3.e1 = 'abc') OR (pm1.g3.e2 = 5)", //$NON-NLS-1$
							"SELECT DISTINCT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 WHERE (pm1.g1.e1 = 'abc') OR (pm1.g1.e2 = 5)", //$NON-NLS-1$
							"SELECT DISTINCT pm1.g2.e1, pm1.g2.e2, pm1.g2.e3, pm1.g2.e4 FROM pm1.g2 WHERE (pm1.g2.e1 = 'abc') OR (pm1.g2.e2 = 5)" } ); //$NON-NLS-1$
  	}

	// expression in a subquery of the union
    public void testPushingCriteriaThroughUnion5() { 
    	helpPlan("select e1 from vm1.u3 where e1='abc'", example1(), //$NON-NLS-1$
			new String[] { "SELECT DISTINCT e1 FROM pm1.g1 WHERE e1 = 'abc'" } ); //$NON-NLS-1$
  	}

    /** defect #4956 */
    public void testPushCriteriaThroughUnion6() { 
        helpPlan("select v1 from vm1.u4 where vm1.u4.v1='x'", example1(), //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1", //$NON-NLS-1$
                            "SELECT e1 FROM pm1.g2 WHERE e1 = 'x'" } ); //$NON-NLS-1$
    }

    public void testPushCriteriaThroughUnion7() { 
        helpPlan("select v1 from vm1.u5 where vm1.u5.v1='x'", example1(), //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1", //$NON-NLS-1$
                            "SELECT e1 FROM pm1.g2" } ); //$NON-NLS-1$
    }

    public void testPushCriteriaThroughUnion8() { 
        helpPlan("select v1 from vm1.u5 where length(v1) > 0", example1(), //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1", //$NON-NLS-1$
                            "SELECT e1 FROM pm1.g2" } ); //$NON-NLS-1$
    }
    
    public void testPushCriteriaThroughUnion11() {
        helpPlan("select * from vm1.u8 where const = 's3' or e1 is null", example1(), //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g3", //$NON-NLS-1$
                            "SELECT e1 FROM pm1.g2 WHERE e1 IS NULL", //$NON-NLS-1$
                            "SELECT e1 FROM pm1.g1 WHERE e1 IS NULL" } );     //$NON-NLS-1$
    }

    public void testPushCriteriaThroughUnion12() {
        helpPlan("select * from vm1.u8 where const = 's1' or e1 is null", example1(), //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g3 WHERE e1 IS NULL", //$NON-NLS-1$
                            "SELECT e1 FROM pm1.g2 WHERE e1 IS NULL", //$NON-NLS-1$
                            "SELECT e1 FROM pm1.g1" } );     //$NON-NLS-1$
    }

    /** defect #4997 */
    public void testCountStarNoRows() { 
        ProcessorPlan plan = helpPlan("select count(*) from vm1.u4", example1(), //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g2",  //$NON-NLS-1$
                            "SELECT e1 FROM pm1.g1" } ); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            2,      // Project
            0,      // Select
            0,      // Sort
            1       // UnionAll
        }); 
    }

    public void testPushingCriteriaWithCopy() { 
    	ProcessorPlan plan = helpPlan("select vm1.u1.e1 from vm1.u1, pm1.g1 where vm1.u1.e1='abc' and vm1.u1.e1=pm1.g1.e1", example1(), //$NON-NLS-1$
			new String[] { "SELECT pm1.g1.e1 FROM pm1.g1 WHERE pm1.g1.e1 = 'abc'", //$NON-NLS-1$
                            "SELECT pm1.g3.e1, pm1.g3.e2, pm1.g3.e3, pm1.g3.e4 FROM pm1.g3 WHERE pm1.g3.e1 = 'abc'", //$NON-NLS-1$
							"SELECT DISTINCT pm1.g2.e1, pm1.g2.e2, pm1.g2.e3, pm1.g2.e4 FROM pm1.g2 WHERE pm1.g2.e1 = 'abc'", //$NON-NLS-1$
							"SELECT DISTINCT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 WHERE pm1.g1.e1 = 'abc'" } ); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            4,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            1,      // DupRemove
            0,      // Grouping
            1,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            2       // UnionAll
        }); 
  	}

    public void testVirtualGroupWithAliasedElement() {
        helpPlan("select elem FROM vm1.u6 where elem='abc' and const='xyz'", example1(), //$NON-NLS-1$
            new String[] { "SELECT x1.e1 FROM pm1.g1 AS x1 WHERE x1.e1 = 'abc'" } );     //$NON-NLS-1$
    }

    public void testPushThroughGroup1() {
        helpPlan("select * FROM vm1.a1 WHERE e1 = 'x'", example1(), //$NON-NLS-1$
            new String[] { "SELECT e1, e2 FROM pm1.g1 WHERE e1 = 'x'" } );     //$NON-NLS-1$
    }
 
    public void testPushThroughGroup2() {
        helpPlan("select * FROM vm1.a2 WHERE e1 = 'x'", example1(), //$NON-NLS-1$
            new String[] { "SELECT e1, e2 FROM pm1.g1 WHERE e1 = 'x'" } );     //$NON-NLS-1$
    }

    public void testPushThroughGroup3() {
        helpPlan("select * FROM vm1.a3 WHERE sum_e2 > 0", example1(), //$NON-NLS-1$
            new String[] { "SELECT e2 FROM pm1.g1" } );     //$NON-NLS-1$
    }

    public void testPushMultiGroupCriteria() { 
        ProcessorPlan plan = helpPlan("select pm2.g1.e1 from pm2.g1, pm2.g2 where pm2.g1.e1 = pm2.g2.e1 and (pm2.g1.e2 = 1 OR pm2.g2.e2 = 2)", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm2.g1.e1 FROM pm2.g1, pm2.g2 WHERE (pm2.g1.e1 = pm2.g2.e1) AND ((pm2.g1.e2 = 1) OR (pm2.g2.e2 = 2))" } ); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }   

    public void testSimpleCrossJoin1() throws Exception {
        helpPlan("select pm1.g1.e1 FROM pm1.g1, pm1.g2", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1", //$NON-NLS-1$
                "SELECT pm1.g2.e1 FROM pm1.g2" }, new DefaultCapabilitiesFinder(), ComparisonMode.EXACT_COMMAND_STRING );     //$NON-NLS-1$
    }

    public void testSimpleCrossJoin2() {
        helpPlan("select pm2.g1.e1 FROM pm2.g1, pm2.g2", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm2.g1.e1 FROM pm2.g1, pm2.g2"} ); //$NON-NLS-1$
               
    }

    public void testSimpleCrossJoin3() {
        helpPlan("select pm2.g1.e1 FROM pm2.g1 CROSS JOIN pm2.g2", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm2.g1.e1 FROM pm2.g1, pm2.g2"} ); //$NON-NLS-1$
               
    }

    public void testMultiSourceCrossJoin() throws Exception {
        helpPlan("select pm1.g1.e1 FROM pm1.g1, pm1.g2, pm1.g3", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1", //$NON-NLS-1$
                "SELECT pm1.g2.e1 FROM pm1.g2", //$NON-NLS-1$
                "SELECT pm1.g3.e1 FROM pm1.g3" }, new DefaultCapabilitiesFinder(), ComparisonMode.EXACT_COMMAND_STRING );     //$NON-NLS-1$
    }

    public void testSingleSourceCrossJoin() {
        helpPlan("select pm2.g1.e1 FROM pm2.g1, pm2.g2, pm2.g3", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm2.g1.e1 FROM pm2.g1, pm2.g2, pm2.g3"} ); //$NON-NLS-1$
    }

    public void testSelfJoins() {
        helpPlan("select pm2.g1.e1 FROM pm2.g1 JOIN pm2.g1 AS x ON pm2.g1.e1=x.e1", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm2.g1.e1 FROM pm2.g1 order by e1", //$NON-NLS-1$
                "SELECT x.e1 FROM pm2.g1 AS x order by e1" } );     //$NON-NLS-1$
    }

    public void testDefect5282_1() {
        helpPlan("select * FROM vm1.a4 WHERE vm1.a4.count > 0", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1" } );     //$NON-NLS-1$
    }

    public void testDefect5282_2() {
        helpPlan("select count(*) FROM vm1.a4", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1" } );     //$NON-NLS-1$
    }

    public void testDefect5282_3() {
        helpPlan("select * FROM vm1.a5 WHERE vm1.a5.count > 0", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1" } );     //$NON-NLS-1$
    }
    
    public void testDepJoinHintBaseline() throws Exception {
        ProcessorPlan plan = helpPlan("select * FROM vm1.g4", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1", //$NON-NLS-1$
                            "SELECT pm1.g2.e1 FROM pm1.g2" }, new DefaultCapabilitiesFinder(), ComparisonMode.EXACT_COMMAND_STRING ); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }
    
    public void testDefect6425_1() {
        helpPlan("select * from vm1.u9", example1(), //$NON-NLS-1$
            new String[] { "SELECT e1, e1 FROM pm1.g1", //$NON-NLS-1$
                            "SELECT e1, e1 FROM pm1.g2" } );     //$NON-NLS-1$
    }

    public void testDefect6425_2() {
        helpPlan("select count(*) from vm1.u9", example1(), //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1", //$NON-NLS-1$
                            "SELECT e1 FROM pm1.g2" } );     //$NON-NLS-1$
    }
    
    public void testPushMatchCritWithReference() {
        List bindings = new ArrayList();
        bindings.add("pm1.g2.e1"); //$NON-NLS-1$
        helpPlan("select e1 FROM pm1.g1 WHERE e1 LIKE ?", example1(), bindings, null,  //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1 WHERE e1 LIKE ?" }, true ); //$NON-NLS-1$
    }
    
    public void testDefect6517() {
        helpPlan("select count(*) from vm1.g5", example1(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT pm1.g1.e1 FROM pm1.g1" });     //$NON-NLS-1$
    }
    
    public void testDefect5283() {        
        helpPlan("select * from vm1.a6", example1(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1", //$NON-NLS-1$
                            "SELECT DISTINCT pm1.g2.e1, pm1.g2.e2, pm1.g2.e3, pm1.g2.e4 FROM pm1.g2" } ); //$NON-NLS-1$
    }
    
    public void testManyJoinsOverThreshold() throws Exception {
        long begin = System.currentTimeMillis();
        helpPlan("SELECT pm1.g1.e1 FROM pm1.g1, pm1.g2, pm1.g3, pm1.g4, pm1.g5, pm1.g6, pm1.g7, pm1.g8, pm1.g1 AS x, pm1.g2 AS y WHERE pm1.g1.e1 = pm1.g2.e1 AND pm1.g2.e1 = pm1.g3.e1 AND pm1.g3.e1 = pm1.g4.e1 AND pm1.g4.e1 = pm1.g5.e1 AND pm1.g5.e1=pm1.g6.e1 AND pm1.g6.e1=pm1.g7.e1 AND pm1.g7.e1=pm1.g8.e1", //$NON-NLS-1$
            example1(), 
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1", //$NON-NLS-1$
                            "SELECT pm1.g2.e1 FROM pm1.g2",  //$NON-NLS-1$
                            "SELECT pm1.g3.e1 FROM pm1.g3",  //$NON-NLS-1$
                            "SELECT pm1.g4.e1 FROM pm1.g4", //$NON-NLS-1$
                            "SELECT pm1.g5.e1 FROM pm1.g5",  //$NON-NLS-1$
                            "SELECT pm1.g6.e1 FROM pm1.g6",  //$NON-NLS-1$
                            "SELECT pm1.g7.e1 FROM pm1.g7", //$NON-NLS-1$
                            "SELECT pm1.g8.e1 FROM pm1.g8", //$NON-NLS-1$
                            "SELECT x.e1 FROM pm1.g1 AS x", //$NON-NLS-1$
                            "SELECT y.e1 FROM pm1.g2 AS y" }, new DefaultCapabilitiesFinder(), ComparisonMode.CORRECTED_COMMAND_STRING ); //$NON-NLS-1$
                            
        long elapsed = System.currentTimeMillis() - begin;   
        assertTrue("Did not plan many join query in reasonable time frame: " + elapsed + " ms", elapsed < 4000); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    public void testAggregateWithoutGroupBy() {
        ProcessorPlan plan = helpPlan("select count(e2) from pm1.g1", example1(), //$NON-NLS-1$
            new String[] { "SELECT e2 FROM pm1.g1" } );         //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }
    
    public void testHavingWithoutGroupBy() {
        ProcessorPlan plan = helpPlan("select count(e2) from pm1.g1 HAVING count(e2) > 0", example1(), //$NON-NLS-1$
            new String[] { "SELECT e2 FROM pm1.g1" } );         //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }

    public void testHavingAndGroupBy() {
        ProcessorPlan plan = helpPlan("select e1, count(e2) from pm1.g1 group by e1 having count(e2) > 0 and sum(e2) > 0", example1(), //$NON-NLS-1$
            new String[] { "SELECT e1, e2 FROM pm1.g1" } );         //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }
    
    public void testAllJoinsInSingleClause() throws Exception {
        ProcessorPlan plan = helpPlan("select pm1.g1.e1 FROM pm1.g1 join (pm1.g2 right outer join pm1.g3 on pm1.g2.e1=pm1.g3.e1) on pm1.g1.e1=pm1.g3.e1", example1(),  //$NON-NLS-1$
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1",  //$NON-NLS-1$
                            "SELECT pm1.g2.e1 FROM pm1.g2", //$NON-NLS-1$
                            "SELECT pm1.g3.e1 FROM pm1.g3" }, new DefaultCapabilitiesFinder(), ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            3,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            2,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }

    public void testSelectCountStarFalseCriteria() {
        ProcessorPlan plan = helpPlan("Select count(*) from pm1.g1 where 1=0", example1(),  //$NON-NLS-1$
            new String[] { });
        checkNodeTypes(plan, new int[] {
            0,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            1,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }

    public void testSubquery1() {
        ProcessorPlan plan = helpPlan("Select e1 from (select e1 FROM pm1.g1) AS x", example1(),  //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testSubquery2() {
        ProcessorPlan plan = helpPlan("Select e1, a from (select e1 FROM pm1.g1) AS x, (select e1 as a FROM pm1.g2) AS y WHERE x.e1=y.a", example1(),  //$NON-NLS-1$
            new String[] { "SELECT g_0.e1, g_1.e1 FROM pm1.g1 AS g_0, pm1.g2 AS g_1 WHERE g_0.e1 = g_1.e1" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

	public void testSubquery3() {
		ProcessorPlan plan = helpPlan("Select e1 from (select e1 FROM pm1.g1) AS x WHERE x.e1 = 'a'", example1(), //$NON-NLS-1$
			new String[] { "SELECT e1 FROM pm1.g1 WHERE e1 = 'a'" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

	public void testSubquery4() {
		ProcessorPlan plan = helpPlan("Select e1 from (select e1 FROM pm1.g1 WHERE e1 = 'a') AS x", example1(), //$NON-NLS-1$
			new String[] { "SELECT e1 FROM pm1.g1 WHERE e1 = 'a'" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testSubqueryInClause1() {
        ProcessorPlan plan = helpPlan("Select e1 from pm1.g1 where e1 in (select e1 FROM pm2.g1)", example1(),  //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            1,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }

    public void testCompareSubquery1() {
        ProcessorPlan plan = helpPlan("Select e1 from pm1.g1 where e1 < any (select e1 FROM pm2.g1)", example1(),  //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            1,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }

    public void testCompareSubquery2() {
        ProcessorPlan plan = helpPlan("Select e1 from pm1.g1 where e1 <= some (select e1 FROM pm2.g1)", example1(),  //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            1,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }

    public void testCompareSubquery3() {
        ProcessorPlan plan = helpPlan("Select e1 from pm1.g1 where e1 >= all (select e1 FROM pm2.g1)", example1(),  //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            1,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }

    public void testCompareSubquery4() {
        ProcessorPlan plan = helpPlan("Select e1 from pm1.g1 where e1 > (select e1 FROM pm2.g1 where e2 = 13)", example1(),  //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            1,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }

    public void testExistsSubquery1() {
        ProcessorPlan plan = helpPlan("Select e1 from pm1.g1 where exists (select e1 FROM pm2.g1)", example1(),  //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            1,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }
    
    public void testScalarSubquery1() {
        ProcessorPlan plan = helpPlan("Select e1, (select e1 FROM pm2.g1 where e1 = 'x') from pm1.g1", example1(),  //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            1,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }    

    public void testScalarSubquery2() {
        ProcessorPlan plan = helpPlan("Select e1, (select e1 FROM pm2.g1 where e1 = 'x') as X from pm1.g1", example1(),  //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            1,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    } 
    
    public void testTempGroup() {
        ProcessorPlan plan = helpPlan("select e1 from tm1.g1 where e1 = 'x'", FakeMetadataFactory.example1Cached(),  //$NON-NLS-1$
            new String[] { "SELECT e1 FROM tm1.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }
    
    public void testNotPushDistinct() throws Exception {
        ProcessorPlan plan = helpPlan("select distinct e1 from pm1.g1", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1" }, new DefaultCapabilitiesFinder(), ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            1,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }

    public void testPushDistinct() {
        ProcessorPlan plan = helpPlan("select distinct e1 from pm3.g1", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT e1 FROM pm3.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testPushDistinctSort() {
        ProcessorPlan plan = helpPlan("select distinct e1 from pm3.g1 order by e1", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT e1 FROM pm3.g1 ORDER BY e1" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testPushDistinctWithCriteria() {
        ProcessorPlan plan = helpPlan("select distinct e1 from pm3.g1 where e1 = 'x'", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT e1 FROM pm3.g1 WHERE e1 = 'x'" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testPushDistinctVirtual1() {
        ProcessorPlan plan = helpPlan("select * from vm1.g12", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testPushDistinctVirtual2() {
        ProcessorPlan plan = helpPlan("select DISTINCT * from vm1.g12", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testPushDistinctVirtual3() {
        ProcessorPlan plan = helpPlan("select DISTINCT * from vm1.g12 ORDER BY e1", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1 ORDER BY pm3.g1.e1" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testPushDistinctVirtual4() {
        ProcessorPlan plan = helpPlan("select * from vm1.g13", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testPushDistinctVirtual5() {
        ProcessorPlan plan = helpPlan("select DISTINCT * from vm1.g13", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testPushDistinctVirtual6() {
        ProcessorPlan plan = helpPlan("select DISTINCT * from vm1.g13 ORDER BY e1", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1 ORDER BY pm3.g1.e1" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testPushDistinctVirtual7() {
        ProcessorPlan plan = helpPlan("select * from vm1.g14", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testPushDistinctVirtual8() {
        ProcessorPlan plan = helpPlan("select DISTINCT * from vm1.g14", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testPushDistinctVirtual9() {
        ProcessorPlan plan = helpPlan("select DISTINCT * from vm1.g14 ORDER BY e1", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT pm3.g1.e1, pm3.g1.e2, pm3.g1.e3, pm3.g1.e4 FROM pm3.g1 ORDER BY pm3.g1.e1" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }
 
    /**
     * Defect #7819
     */
    public void testPushDistinctWithExpressions() {
        ProcessorPlan plan = helpPlan("SELECT DISTINCT * FROM vm1.g15", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT e1, e2 FROM pm3.g1" }); //$NON-NLS-1$
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            1,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }

    public void testNestedSubquery() {
        ProcessorPlan plan = helpPlan("SELECT IntKey, LongNum FROM (SELECT IntKey, LongNum FROM (SELECT IntKey, LongNum, DoubleNum FROM BQT2.SmallA ) AS x ) AS y ORDER BY IntKey", FakeMetadataFactory.exampleBQTCached(), //$NON-NLS-1$
            new String[] { "SELECT IntKey, LongNum FROM BQT2.SmallA order by intkey" }); //$NON-NLS-1$

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }

    /** Tests a user's order by is pushed to the source */
    public void testPushOrderBy() {
        ProcessorPlan plan = helpPlan("SELECT pm3.g1.e1 FROM pm3.g1 ORDER BY e1", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT pm3.g1.e1 FROM pm3.g1 ORDER BY pm3.g1.e1"}); //$NON-NLS-1$

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }
    
    /** Tests an order by is not pushed to source due to join */
    public void testDontPushOrderByWithJoin() {
        ProcessorPlan plan = helpPlan("SELECT pm3.g1.e1, pm3.g1.e2 FROM pm3.g1 INNER JOIN pm2.g2 ON pm3.g1.e1 = pm2.g2.e1 ORDER BY pm3.g1.e2", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT pm3.g1.e1, pm3.g1.e2 FROM pm3.g1 ORDER BY pm3.g1.e1", //$NON-NLS-1$
                           "SELECT pm2.g2.e1 FROM pm2.g2 ORDER BY pm2.g2.e1"}); //$NON-NLS-1$

        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            1,      // Sort
            0       // UnionAll
        });                                
    }    

    /** 
     * Tests that user's order by gets pushed to the source, but query
     * transformation order by is discarded 
     */
    public void testPushOrderByThroughFrame() {
        ProcessorPlan plan = helpPlan("SELECT e1, e2 FROM vm1.g14 ORDER BY e2", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT pm3.g1.e1, pm3.g1.e2 FROM pm3.g1 ORDER BY pm3.g1.e2"}); //$NON-NLS-1$

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }
    
    /** 
     * Tests that query transformation order by is discarded by
     */
    public void testPushOrderByThroughFrame2() {
        ProcessorPlan plan = helpPlan("SELECT e1, e2 FROM vm1.g1 ORDER BY e2", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT pm1.g1.e1, pm1.g1.e2 FROM pm1.g1 order by e2"}); //$NON-NLS-1$

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }    

    /** 
     * Tests that query transformation order by is discarded by
     * user order by, and that user order by is discarded because
     * of the function in the query transformation 
     */
    public void testPushOrderByThroughFrame3() {
        ProcessorPlan plan = helpPlan("SELECT e, e2 FROM vm1.g16 ORDER BY e2", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT e1, e2 FROM pm3.g1"}); //$NON-NLS-1$

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            1,      // Sort
            0       // UnionAll
        });                                    
    } 

    /** 
     * Tests that a user's order by does not get pushed to the source
     * if there is a UNION in the query transformation 
     */
    public void testPushOrderByThroughFrame4_Union() {
        ProcessorPlan plan = helpPlan("SELECT e1, e2 FROM vm1.g17 ORDER BY e1", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT pm3.g1.e1, pm3.g1.e2 FROM pm3.g1", //$NON-NLS-1$
                           "SELECT pm3.g2.e1, pm3.g2.e2 FROM pm3.g2"}); //$NON-NLS-1$

        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            1,      // Sort
            1       // UnionAll
        });                                    
    }

    /** Tests outer join defect #7945 - see also defect #10050*/
    public void testOuterJoinDefect7945() {
        ProcessorPlan plan = helpPlan(
            "SELECT BQT1.SmallA.IntKey AS SmallA_IntKey, BQT2.MediumB.IntKey AS MediumB_IntKey, BQT3.MediumB.IntKey AS MediumC_IntKey " +  //$NON-NLS-1$
            "FROM (BQT1.SmallA RIGHT OUTER JOIN BQT2.MediumB ON BQT1.SmallA.IntKey = BQT2.MediumB.IntKey) " +  //$NON-NLS-1$
            "RIGHT OUTER JOIN BQT3.MediumB ON BQT2.MediumB.IntKey = BQT3.MediumB.IntKey " +   //$NON-NLS-1$
            "WHERE BQT3.MediumB.IntKey < 1500",  //$NON-NLS-1$
            FakeMetadataFactory.exampleBQTCached(),
            new String[] { 
                "SELECT BQT3.MediumB.IntKey FROM BQT3.MediumB WHERE BQT3.MediumB.IntKey < 1500 order by intkey", //$NON-NLS-1$
                "SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA WHERE BQT1.SmallA.IntKey < 1500 order by intkey", //$NON-NLS-1$
                "SELECT BQT2.MediumB.IntKey FROM BQT2.MediumB WHERE BQT2.MediumB.IntKey < 1500 order by intkey" }); //$NON-NLS-1$
                
        checkNodeTypes(plan, new int[] {
            3,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            2,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }    

    /** Tests outer join defect #7945 */
    public void testFunctionSimplification1() {
        ProcessorPlan plan = helpPlan(
            "SELECT x FROM vm1.g18 WHERE x = 92.0",   //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            new String[] { 
                "SELECT e4 FROM pm1.g1 WHERE e4 = 0.92" }); //$NON-NLS-1$

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }    
            
    public void testCantPushJoin1() {
        ProcessorPlan plan = helpPlan(
            "SELECT a.e1, b.e2 FROM pm1.g1 a, pm1.g2 b WHERE a.e1 = b.e1",  //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, TestOptimizer.getGenericFinder(false),
            new String[] {"SELECT a.e1 FROM pm1.g1 AS a", "SELECT b.e1, b.e2 FROM pm1.g2 AS b"}, //$NON-NLS-1$ //$NON-NLS-2$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }

    public void testCantPushJoin2() {
        ProcessorPlan plan = helpPlan(
            "SELECT a.e1, b.e2 FROM pm1.g1 a, pm1.g2 b, pm2.g1 c WHERE a.e1 = b.e1 AND b.e1 = c.e1",  //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, TestOptimizer.getGenericFinder(false),
            new String[] {"SELECT a.e1 FROM pm1.g1 AS a",  //$NON-NLS-1$
                           "SELECT b.e1, b.e2 FROM pm1.g2 AS b", //$NON-NLS-1$
                           "SELECT c.e1 FROM pm2.g1 AS c"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            3,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            2,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }
    
    public void testPushSelfJoin1() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT a.e1, b.e2 FROM pm1.g1 a, pm1.g1 b WHERE a.e1 = b.e1",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT a.e1, b.e2 FROM pm1.g1 AS a, pm1.g1 AS b WHERE a.e1 = b.e1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }

    public void testPushSelfJoin2() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT a.e1 AS x, concat(a.e2, b.e2) AS y FROM pm1.g1 a, pm1.g1 b WHERE a.e1 = b.e1",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT a.e1, a.e2, b.e2 FROM pm1.g1 AS a, pm1.g1 AS b WHERE a.e1 = b.e1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }
        
    public void testPushOuterJoin1() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT pm1.g1.e1 FROM pm1.g1 RIGHT OUTER JOIN pm1.g2 ON pm1.g1.e1 = pm1.g2.e1",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT pm1.g1.e1 FROM pm1.g2 LEFT OUTER JOIN pm1.g1 ON pm1.g1.e1 = pm1.g2.e1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }

    public void testPushOuterJoin2() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT pm1.g1.e1 FROM pm1.g1 RIGHT OUTER JOIN pm1.g2 ON pm1.g1.e1 = pm1.g2.e1",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT pm1.g1.e1 FROM pm1.g1", "SELECT pm1.g2.e1 FROM pm1.g2"}, //$NON-NLS-1$ //$NON-NLS-2$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }

    // With join expression that can't be pushed
    public void testPushOuterJoin3() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT pm1.g1.e1 FROM pm1.g1 RIGHT OUTER JOIN pm1.g2 ON pm1.g1.e1 = pm1.g2.e1 || 'x'",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT pm1.g1.e1 FROM pm1.g1", "SELECT pm1.g2.e1 FROM pm1.g2"}, //$NON-NLS-1$ //$NON-NLS-2$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            2,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
                               
    }

    public void testPushGroupBy1() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT e1, e2 as x FROM pm1.g1 GROUP BY e1, e2",  //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT e1, e2 FROM pm1.g1 GROUP BY e1, e2"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
                               
    }

    public void testPushGroupBy2() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT e1, max(e2) as x FROM pm1.g1 GROUP BY e1",  //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT e1, MAX(e2) FROM pm1.g1 GROUP BY e1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
                               
    }

    public void testPushGroupBy3() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, false);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT e1, e2 as x FROM pm1.g1 GROUP BY e1, e2",  //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT e1, e2 FROM pm1.g1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
                               
    }

    public void testPushGroupBy4() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT x+2 AS y FROM (SELECT e1, max(e2) as x FROM pm1.g1 GROUP BY e1) AS z",  //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT MAX(e2) FROM pm1.g1 GROUP BY e1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
                               
    }
    
    public void testPushHaving1() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT e1 FROM pm1.g1 GROUP BY e1 HAVING MAX(e1) = 'zzz'",  //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT e1 FROM pm1.g1 GROUP BY e1 HAVING MAX(e1) = 'zzz'"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, FULL_PUSHDOWN);                                                                  
    }

    public void testPushHaving2() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, false);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT e1 FROM pm1.g1 GROUP BY e1 HAVING MAX(e1) = 'zzz'",  //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT e1 FROM pm1.g1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });                                                                  
    }

    public void testPushHaving3() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, false);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT e1 FROM pm1.g1 GROUP BY e1 HAVING MAX(e1) = 'zzz'",  //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT e1 FROM pm1.g1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });                                                                  
    }

    public void testPushAggregate1() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT MAX(e1) FROM pm1.g1",  //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT MAX(e1) FROM pm1.g1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, FULL_PUSHDOWN);                                                                  
    }
    
    public void testPushAggregate2() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT MAX(e1) FROM pm1.g1 GROUP BY e1", //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT MAX(e1) FROM pm1.g1 GROUP BY e1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, FULL_PUSHDOWN);                                                                  
    }

    public void testPushAggregate3() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT e2, MAX(e1) FROM pm1.g1 GROUP BY e2", //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT e2, MAX(e1) FROM pm1.g1 GROUP BY e2"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, FULL_PUSHDOWN);                                                                  
    }

    public void testPushAggregate4() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT e2, MAX(e1) FROM pm1.g1 GROUP BY e2 HAVING COUNT(e1) > 0", //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT e2, MAX(e1) FROM pm1.g1 GROUP BY e2 HAVING COUNT(e1) > 0"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, FULL_PUSHDOWN);                                                                  
    }

    /**
     * Can't push aggs due to not being able to push COUNT in the HAVING clause.
     */
    public void testPushAggregate5() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT, false);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT e2, MAX(e1) FROM pm1.g1 GROUP BY e2 HAVING COUNT(e1) > 0", //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT e2, e1 FROM pm1.g1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });                                                                  
    }

    /**
     * Can't push aggs due to not being able to push function inside the aggregate
     */
    public void testPushAggregate6() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT COUNT(length(e1)) FROM pm1.g1", //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT e1 FROM pm1.g1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                                                  
    }

    /**
     * Can't push aggs due to not being able to push function inside having
     */
    public void testPushAggregate7() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
         
        ProcessorPlan plan = helpPlan(
            "SELECT COUNT(*) FROM pm1.g1 GROUP BY e1 HAVING length(e1) > 0", //$NON-NLS-1$
            FakeMetadataFactory.example1Cached(),
            null, capFinder,
            new String[] {"SELECT e1 FROM pm1.g1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });                                                                  
    }

    /**
     * BQT query that is failing
     */
    public void testPushAggregate8() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SELECT_EXPRESSION, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_CORRELATED, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_SCALAR, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
         
        String sqlIn =          
            "SELECT intkey FROM bqt1.smalla AS sa WHERE (sa.intkey = 46) AND " + //$NON-NLS-1$
            "(sa.stringkey IN (46)) AND (sa.datevalue = (" + //$NON-NLS-1$
            "SELECT MAX(sa.datevalue) FROM bqt1.smalla AS sb " + //$NON-NLS-1$
            "WHERE (sb.intkey = sa.intkey) AND (sa.stringkey = sb.stringkey) ))"; //$NON-NLS-1$

        String sqlOut = "SELECT intkey FROM bqt1.smalla AS sa WHERE (sa.intkey = 46) AND (sa.stringkey = '46') AND (sa.datevalue = (SELECT sa.datevalue FROM bqt1.smalla AS sb WHERE (sb.intkey = sa.intkey) AND (sb.stringkey = sa.stringkey)))"; //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sqlIn, 
            FakeMetadataFactory.exampleBQTCached(),
            null, capFinder,
            new String[] {sqlOut}, 
            SHOULD_SUCCEED );
    
        checkNodeTypes(plan, FULL_PUSHDOWN);                                                                  
    }
    
    public void testQueryManyJoin() throws Exception { 
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
        ProcessorPlan plan = helpPlan("SELECT pm1.g1.e1 FROM pm1.g1 JOIN ((pm1.g2 JOIN pm1.g3 ON pm1.g2.e1=pm1.g3.e1) JOIN pm1.g4 ON pm1.g3.e1=pm1.g4.e1) ON pm1.g1.e1=pm1.g4.e1",  //$NON-NLS-1$
            metadata,
            new String[] { "SELECT g_0.e1 FROM pm1.g1 AS g_0, pm1.g2 AS g_1, pm1.g3 AS g_2, pm1.g4 AS g_3 WHERE (g_1.e1 = g_2.e1) AND (g_2.e1 = g_3.e1) AND (g_0.e1 = g_3.e1)"}, ComparisonMode.EXACT_COMMAND_STRING ); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }
    
    public void testPushSelectDistinct() { 
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
        ProcessorPlan plan = helpPlan("SELECT DISTINCT e1 FROM pm3.g1",  //$NON-NLS-1$
            metadata,
            new String[] { "SELECT DISTINCT e1 FROM pm3.g1"} ); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN);         
    }

    public void testPushFunctionInCriteria1() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setFunctionSupport(SourceSystemFunctions.UCASE, true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT e1 FROM pm1.g1 WHERE upper(e1) = 'X'",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT e1 FROM pm1.g1 WHERE ucase(e1) = 'X'"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }

    public void testPushFunctionInSelect1() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setFunctionSupport(SourceSystemFunctions.UCASE, true); //$NON-NLS-1$
        caps.setFunctionSupport(SourceSystemFunctions.LCASE, true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT lower(e1) FROM pm1.g1 WHERE upper(e1) = 'X'",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT lower(e1) FROM pm1.g1 WHERE ucase(e1) = 'X'"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }

    public void testPushFunctionInSelect2() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setFunctionSupport(SourceSystemFunctions.UCASE, true); //$NON-NLS-1$
        caps.setFunctionSupport(SourceSystemFunctions.LCASE, true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT lower(e1), upper(e1), e2 FROM pm1.g1 WHERE upper(e1) = 'X'",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT lower(e1), upper(e1), e2 FROM pm1.g1 WHERE ucase(e1) = 'X'"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }
    
    public void testPushFunctionInSelect3() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setFunctionSupport(SourceSystemFunctions.UCASE, true); //$NON-NLS-1$
        caps.setFunctionSupport(SourceSystemFunctions.LCASE, false); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT lower(e1), upper(e1) FROM pm1.g1 WHERE upper(e1) = 'X'",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT e1 FROM pm1.g1 WHERE ucase(e1) = 'X'"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }    

    public void testPushFunctionInSelect4() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setFunctionSupport(SourceSystemFunctions.UCASE, true); //$NON-NLS-1$
        caps.setFunctionSupport(SourceSystemFunctions.LCASE, true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT x FROM (SELECT lower(e1) AS x, upper(e1) AS y FROM pm1.g1 WHERE upper(e1) = 'X') AS z",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT lcase(e1) FROM pm1.g1 WHERE ucase(e1) = 'X'"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }    

    public void testPushFunctionInSelect5() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setFunctionSupport(SourceSystemFunctions.UCASE, true); //$NON-NLS-1$
        caps.setFunctionSupport(SourceSystemFunctions.LCASE, true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT y, e, x FROM (SELECT lower(e1) AS x, upper(e1) AS y, 5 as z, e1 AS e FROM pm1.g1 WHERE upper(e1) = 'X') AS w",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT ucase(e1), e1, lcase(e1) FROM pm1.g1 WHERE ucase(e1) = 'X'"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }    

    public void testPushFunctionInSelect6_defect_10081() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setFunctionSupport("upper", true); //$NON-NLS-1$
        caps.setFunctionSupport("lower", false); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT upper(lower(e1)) FROM pm1.g1",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT e1 FROM pm1.g1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }
            
    public void testPushFunctionInSelectWithOrderBy1() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setFunctionSupport(SourceSystemFunctions.UCASE, true); //$NON-NLS-1$
        caps.setFunctionSupport(SourceSystemFunctions.LCASE, true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT e1, lower(e1) FROM pm1.g1 WHERE upper(e1) = 'X' ORDER BY e1",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT e1, lcase(e1) FROM pm1.g1 WHERE ucase(e1) = 'X' ORDER BY e1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }

    /** defect 13336 */
    public void testPushFunctionInSelectWithOrderBy1a() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setFunctionSupport(SourceSystemFunctions.UCASE, true); //$NON-NLS-1$
        caps.setFunctionSupport(SourceSystemFunctions.LCASE, true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT e1, lower(e1) AS x FROM pm1.g1 WHERE upper(e1) = 'X' ORDER BY x",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT e1, lcase(e1) AS x FROM pm1.g1 WHERE ucase(e1) = 'X' ORDER BY x"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }    
    
    /** defect 13336 */
    public void testPushFunctionInSelectWithOrderBy2() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setFunctionSupport(SourceSystemFunctions.UCASE, true); //$NON-NLS-1$
        caps.setFunctionSupport(SourceSystemFunctions.LCASE, true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT e1, x FROM (SELECT e1, lower(e1) AS x FROM pm1.g1 WHERE upper(e1) = 'X') AS z ORDER BY x",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT e1, lcase(e1) AS EXPR FROM pm1.g1 WHERE ucase(e1) = 'X' ORDER BY EXPR"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }
    
    public void testPushFunctionInJoin1() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setFunctionSupport(SourceSystemFunctions.UCASE, true); //$NON-NLS-1$
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT pm1.g1.e1, pm1.g2.e3 FROM pm1.g1, pm1.g2 WHERE pm1.g1.e1 = convert(pm1.g2.e2, string) AND upper(pm1.g1.e1) = 'X'",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT pm1.g1.e1, pm1.g2.e3 FROM pm1.g1, pm1.g2 WHERE (pm1.g1.e1 = convert(pm1.g2.e2, string)) AND (ucase(pm1.g1.e1) = 'X') AND (ucase(convert(pm1.g2.e2, string)) = 'X')"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }    

    public void testPushFunctionInJoin2() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setFunctionSupport(SourceSystemFunctions.UCASE, true); //$NON-NLS-1$
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT pm1.g1.e1, pm1.g2.e3 FROM pm1.g1, pm1.g2, pm1.g3 WHERE pm1.g1.e1 = convert(pm1.g2.e2, string) AND pm1.g1.e1 = concat(pm1.g3.e1, 'a') AND upper(pm1.g1.e1) = 'X'",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT pm1.g1.e1, pm1.g2.e3 FROM pm1.g1, pm1.g2 WHERE (pm1.g1.e1 = convert(pm1.g2.e2, string)) AND (ucase(pm1.g1.e1) = 'X') AND (ucase(convert(pm1.g2.e2, string)) = 'X')", //$NON-NLS-1$
                    "SELECT pm1.g3.e1 FROM pm1.g3"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            2,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }    

    public void testPushFunctionInJoin3() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setFunctionSupport(SourceSystemFunctions.UCASE, true); //$NON-NLS-1$
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT pm1.g1.e1, pm1.g2.e3 FROM pm1.g1, pm1.g2, (SELECT e1 AS x FROM pm1.g3) AS g WHERE pm1.g1.e1 = convert(pm1.g2.e2, string) AND pm1.g1.e1 = concat(g.x, 'a') AND upper(pm1.g1.e1) = 'X'",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT pm1.g1.e1, pm1.g2.e3 FROM pm1.g1, pm1.g2 WHERE (pm1.g1.e1 = convert(pm1.g2.e2, string)) AND (ucase(pm1.g1.e1) = 'X') AND (ucase(convert(pm1.g2.e2, string)) = 'X')", //$NON-NLS-1$
                    "SELECT e1 FROM pm1.g3"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            2,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }    

    public void testUnionOverFunctions() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT StringCol AS E " +  //$NON-NLS-1$
            "FROM (SELECT CONVERT(BQT1.SmallA.IntNum, string) AS StringCol, BQT1.SmallA.IntNum AS IntCol FROM BQT1.SmallA " + //$NON-NLS-1$
            "UNION ALL SELECT BQT1.SmallB.StringNum, CONVERT(BQT1.SmallB.StringNum, integer) FROM BQT1.SmallB) AS x",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT CONVERT(BQT1.SmallA.IntNum, string) FROM BQT1.SmallA", //$NON-NLS-1$
                    "SELECT BQT1.SmallB.StringNum FROM BQT1.SmallB"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            1       // UnionAll
        });                                    
    }    

    public void testDefect9827() { 
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        ProcessorPlan plan = helpPlan("SELECT intkey, c FROM (SELECT DISTINCT b.intkey, b.intnum, a.stringkey AS c FROM bqt1.smalla AS a, bqt1.smallb AS b WHERE a.INTKEY = b.INTKEY) AS x ORDER BY x.intkey", metadata, //$NON-NLS-1$
            new String[] {"SELECT DISTINCT b.intkey, b.intnum, a.stringkey FROM bqt1.smalla AS a, bqt1.smallb AS b WHERE a.INTKEY = b.INTKEY"} ); //$NON-NLS-1$

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            1,      // Sort
            0       // UnionAll
        });                                    
    }

    /**
     * This tests that a criteria with no elements is not pushed down,
     * but instead is cleaned up properly later
     * See defect 9865
     */
    public void testCrossJoinNoElementCriteriaOptimization2() {
        ProcessorPlan plan = helpPlan("select Y.e1, Y.e2 FROM vm1.g1 X, vm1.g1 Y where {b'true'} = {b'true'}", example1(),  //$NON-NLS-1$
            new String[]{"SELECT g1__1.e1 FROM pm1.g1 AS g1__1", "SELECT pm1.g1.e1, pm1.g1.e2 FROM pm1.g1"}); //$NON-NLS-1$ //$NON-NLS-2$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            1,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }

    /**
     * <p>This tests that a SELECT node with no groups is not pushed down without the capability to have a subquery in the where clause.
     */
    public void testCrossJoinNoElementCriteriaOptimization3() {
        ProcessorPlan plan = helpPlan("select Y.e1, Y.e2 FROM vm1.g1 X, vm1.g1 Y where {b'true'} in (select e3 FROM vm1.g1)", example1(),  //$NON-NLS-1$
            new String[]{"SELECT g1__1.e1 FROM pm1.g1 AS g1__1", "SELECT pm1.g1.e1, pm1.g1.e2 FROM pm1.g1"}); //$NON-NLS-1$ //$NON-NLS-2$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            1,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            1,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }

    /**
     * <p>This tests that a SELECT node with no groups is pushed down.
     */
    public void testCrossJoinNoElementCriteriaOptimization4() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN_SUBQUERY, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
        
        ProcessorPlan plan = helpPlan("select Y.e1, Y.e2 FROM vm1.g1 X, vm1.g1 Y where {b'true'} in (select e3 FROM vm1.g1)", example1(), null, capFinder,  //$NON-NLS-1$
            new String[]{"SELECT g1__1.e1 FROM pm1.g1 AS g1__1 WHERE TRUE IN (SELECT pm1.g1.e3 FROM pm1.g1)", "SELECT pm1.g1.e1, pm1.g1.e2 FROM pm1.g1"}, true); //$NON-NLS-1$ //$NON-NLS-2$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            1,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }
    
    /**
     * Criteria should be copied across this join
     */
    public void testCopyCriteriaWithOuterJoin_defect10050(){
        
        ProcessorPlan plan = helpPlan("select pm2.g1.e1, pm2.g2.e1 from pm2.g1 left outer join pm2.g2 on pm2.g1.e1=pm2.g2.e1 where pm2.g1.e1 IN ('a', 'b')", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm2.g1.e1, pm2.g2.e1 FROM pm2.g1 LEFT OUTER JOIN pm2.g2 ON pm2.g1.e1 = pm2.g2.e1 AND pm2.g2.e1 IN ('a', 'b') WHERE pm2.g1.e1 IN ('a', 'b')" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN);         
    }

    /**
     * Criteria should be copied across this join
     */
    public void testCopyCriteriaWithOuterJoin2_defect10050(){
        
        ProcessorPlan plan = helpPlan("select pm2.g1.e1, pm2.g2.e1 from pm2.g1 left outer join pm2.g2 on pm2.g1.e1=pm2.g2.e1 and pm2.g1.e2=pm2.g2.e2 where pm2.g1.e1 = 'a' and pm2.g1.e2 = 1", example1(), //$NON-NLS-1$
            new String[] { "SELECT g_0.e1, g_1.e1 FROM pm2.g1 AS g_0 LEFT OUTER JOIN pm2.g2 AS g_1 ON g_0.e1 = g_1.e1 AND g_0.e2 = g_1.e2 AND g_1.e2 = 1 AND g_1.e1 = 'a' WHERE (g_0.e1 = 'a') AND (g_0.e2 = 1)" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN);         
    }

    /**
     * See also case 2912.
     */
    public void testCopyCriteriaWithOuterJoin5_defect10050(){
        
        ProcessorPlan plan = helpPlan(
            "select pm2.g1.e1, pm2.g2.e1, pm2.g3.e1 from ( (pm2.g1 right outer join pm2.g2 on pm2.g1.e1=pm2.g2.e1) right outer join pm2.g3 on pm2.g2.e1=pm2.g3.e1) where pm2.g3.e1 = 'a'", example1(), //$NON-NLS-1$
            new String[] { "SELECT g_2.e1, g_1.e1, g_0.e1 FROM pm2.g3 AS g_0 LEFT OUTER JOIN (pm2.g2 AS g_1 LEFT OUTER JOIN pm2.g1 AS g_2 ON g_2.e1 = g_1.e1 AND g_2.e1 = 'a') ON g_1.e1 = g_0.e1 AND g_1.e1 = 'a' WHERE g_0.e1 = 'a'" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN);         
    } 
    
    /**
     * 
     */
    public void testCopyCriteriaWithOuterJoin6_defect10050(){
        
        ProcessorPlan plan = helpPlan("select pm2.g1.e1, pm2.g2.e1 from pm2.g1 left outer join pm2.g2 on pm2.g2.e1=pm2.g1.e1 where pm2.g1.e1 IN ('a', 'b')", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm2.g1.e1, pm2.g2.e1 FROM pm2.g1 LEFT OUTER JOIN pm2.g2 ON pm2.g2.e1 = pm2.g1.e1 AND pm2.g2.e1 IN ('a', 'b') WHERE pm2.g1.e1 IN ('a', 'b')" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN);         
    }

    /**
     * Same as previous test, only right outer join
     */
    public void testCopyCriteriaWithOuterJoin7_defect10050(){
        
        ProcessorPlan plan = helpPlan("select pm2.g1.e1, pm2.g2.e1 from pm2.g1 right outer join pm2.g2 on pm2.g2.e1=pm2.g1.e1 where pm2.g2.e1 IN ('a', 'b')", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm2.g1.e1, pm2.g2.e1 FROM pm2.g2 LEFT OUTER JOIN pm2.g1 ON pm2.g2.e1 = pm2.g1.e1 AND pm2.g1.e1 IN ('a', 'b') WHERE pm2.g2.e1 IN ('a', 'b')" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN);         
    }         
    
    public void testCleanCriteria(){
        
        ProcessorPlan plan = helpPlan("select pm2.g1.e1, pm2.g2.e1 from pm2.g1, pm2.g2 where pm2.g1.e1=pm2.g2.e1 and pm2.g1.e2 IN (1, 2)", example1(), //$NON-NLS-1$
            new String[] { "SELECT pm2.g1.e1, pm2.g2.e1 FROM pm2.g1, pm2.g2 WHERE (pm2.g1.e1 = pm2.g2.e1) AND (pm2.g1.e2 IN (1, 2))" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN);         
    }

    public void testCleanCriteria2(){
        
        ProcessorPlan plan = helpPlan("select pm2.g1.e1, pm2.g2.e1 from pm2.g1, pm2.g2 where pm2.g1.e1=pm2.g2.e1 and pm2.g1.e1 = 'a'", example1(), //$NON-NLS-1$
            new String[] { "SELECT g_0.e1, g_1.e1 FROM pm2.g1 AS g_0, pm2.g2 AS g_1 WHERE (g_0.e1 = g_1.e1) AND (g_0.e1 = 'a') AND (g_1.e1 = 'a')" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN);         
    }

    public void testCleanCriteria3(){
        
        ProcessorPlan plan = helpPlan("select pm2.g1.e1, pm2.g2.e1 from pm2.g1 inner join pm2.g2 on pm2.g1.e1=pm2.g2.e1 where pm2.g1.e1 = 'a'", example1(), //$NON-NLS-1$
            new String[] { "SELECT g_0.e1, g_1.e1 FROM pm2.g1 AS g_0, pm2.g2 AS g_1 WHERE (g_0.e1 = g_1.e1) AND (g_0.e1 = 'a') AND (g_1.e1 = 'a')" }); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN);         
    }
    
    
    public void testPushSubqueryInWhereClause1() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_IN_SUBQUERY, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("Select e1 from pm1.g1 where e1 in (select e1 FROM pm1.g2)", example1(),  //$NON-NLS-1$
            null, capFinder,
            new String[] { "SELECT e1 FROM pm1.g1 WHERE e1 IN (SELECT e1 FROM pm1.g2)"}, SHOULD_SUCCEED ); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    public void testPushSubqueryInWhereClause2() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_IN_SUBQUERY, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("Select e1 from pm1.g1 where e1 in (select max(e1) FROM pm1.g2)", example1(),  //$NON-NLS-1$
            null, capFinder,
            new String[] { "SELECT e1 FROM pm1.g1 WHERE e1 IN (SELECT MAX(e1) FROM pm1.g2)" }, SHOULD_SUCCEED); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }

    /**
     * Check that subquery is pushed if the subquery selects a function that is pushed
     */
    public void testPushSubqueryInWhereClause3() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN_SUBQUERY, true);
        caps.setFunctionSupport("ltrim", true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
        capFinder.addCapabilities("pm2", new BasicSourceCapabilities()); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("Select e1 from pm1.g1 where e1 in (SELECT ltrim(e1) FROM pm1.g2)", FakeMetadataFactory.example1Cached(),  //$NON-NLS-1$
            null, capFinder,
            new String[] { "SELECT e1 FROM pm1.g1 WHERE e1 IN (SELECT ltrim(e1) FROM pm1.g2)" }, SHOULD_SUCCEED); //$NON-NLS-1$ 
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }   

    /**
     * Check that subquery is pushed if the subquery selects an aliased function that is pushed
     */
    public void testPushSubqueryInWhereClause4() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN_SUBQUERY, true);
        caps.setFunctionSupport("ltrim", true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
        capFinder.addCapabilities("pm2", new BasicSourceCapabilities()); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("Select e1 from pm1.g1 where e1 in (SELECT ltrim(e1) as m FROM pm1.g2)", FakeMetadataFactory.example1Cached(),  //$NON-NLS-1$
            null, capFinder,
            new String[] { "SELECT e1 FROM pm1.g1 WHERE e1 IN (SELECT ltrim(e1) FROM pm1.g2)" }, SHOULD_SUCCEED); //$NON-NLS-1$ 

        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }   

    /** Case 1456, defect 10492*/
    public void testAliasingDefect1(){
        // Create query
        String sql = "SELECT e1 FROM vm1.g1 X WHERE e2 = (SELECT MAX(e2) FROM vm1.g1 Y WHERE X.e1 = Y.e1)";//$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_CORRELATED, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_SCALAR, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql, FakeMetadataFactory.example1Cached(),  
            null, capFinder,
            new String[] { "SELECT g1__1.e1 FROM pm1.g1 AS g1__1 WHERE g1__1.e2 = (SELECT MAX(pm1.g1.e2) FROM pm1.g1 WHERE pm1.g1.e1 = g1__1.e1)" }, SHOULD_SUCCEED); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }   

    /** Case 1456, defect 10492*/
    public void testAliasingDefect2(){
        // Create query
        String sql = "SELECT X.e1 FROM vm1.g1 X, vm1.g1 Z WHERE X.e2 = (SELECT MAX(e2) FROM vm1.g1 Y WHERE X.e1 = Y.e1 AND Y.e2 = Z.e2) AND X.e1 = Z.e1";//$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_CORRELATED, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_SCALAR, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
    
        ProcessorPlan plan = helpPlan(sql, metadata,  
            null, capFinder,
            new String[] { "SELECT g1__1.e1 FROM pm1.g1 AS g1__1, pm1.g1 AS g1__2 WHERE (g1__1.e2 = (SELECT MAX(pm1.g1.e2) FROM pm1.g1 WHERE (pm1.g1.e1 = g1__1.e1) AND (pm1.g1.e2 = g1__2.e2))) AND (g1__1.e1 = g1__2.e1)" }, SHOULD_SUCCEED); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }   

    /** Case 1456, defect 10492*/
    public void testAliasingDefect3(){
        // Create query
        String sql = "SELECT X.e1 FROM pm1.g2, vm1.g1 X WHERE X.e2 = ANY (SELECT MAX(e2) FROM vm1.g1 Y WHERE X.e1 = Y.e1) AND X.e1 = pm1.g2.e1";//$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_QUANTIFIED_SOME, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_CORRELATED, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_SCALAR, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
    
        ProcessorPlan plan = helpPlan(sql, metadata,  
            null, capFinder,
            new String[] { "SELECT g_1.e1 FROM pm1.g2 AS g_0, pm1.g1 AS g_1 WHERE (g_1.e1 = g_0.e1) AND (g_1.e2 = SOME (SELECT MAX(g_2.e2) FROM pm1.g1 AS g_2 WHERE g_2.e1 = g_1.e1))" }, SHOULD_SUCCEED); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }
        
    /** Should use merge join since neither access node is "strong" - order by's pushed to source */
    public void testUseMergeJoin3(){
        // Create query
        String sql = "SELECT pm1.g1.e1 FROM pm1.g1, pm1.g2 WHERE pm1.g1.e1 = pm1.g2.e1";//$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
        FakeMetadataObject g1 = metadata.getStore().findObject("pm1.g1", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g1.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 1));
    
        ProcessorPlan plan = helpPlan(sql, metadata,  
            null, capFinder,
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1 ORDER BY pm1.g1.e1", "SELECT pm1.g2.e1 FROM pm1.g2 ORDER BY pm1.g2.e1" }, SHOULD_SUCCEED); //$NON-NLS-1$ //$NON-NLS-2$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });         
    }    

    /** Model supports order by, should be pushed to the source */
    public void testUseMergeJoin4(){
        // Create query
        String sql = "SELECT pm1.g1.e1 FROM pm1.g1, pm1.g2 WHERE pm1.g1.e1 = pm1.g2.e1";//$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
        FakeMetadataObject g1 = metadata.getStore().findObject("pm1.g1", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g1.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 500));
        FakeMetadataObject g2 = metadata.getStore().findObject("pm1.g2", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g2.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 1000));
    
        ProcessorPlan plan = helpPlan(sql, metadata,  
            null, capFinder,
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1 ORDER BY pm1.g1.e1", "SELECT pm1.g2.e1 FROM pm1.g2 ORDER BY pm1.g2.e1" }, SHOULD_SUCCEED); //$NON-NLS-1$ //$NON-NLS-2$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });         
    } 

    /** Should use merge join, since costs are not known, neither access node is "strong" */
    public void testUseMergeJoin5_CostsNotKnown(){
        // Create query
        String sql = "SELECT pm1.g1.e1 FROM pm1.g1, pm1.g2 WHERE pm1.g1.e1 = pm1.g2.e1";//$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
    
        ProcessorPlan plan = helpPlan(sql, metadata,  
            null, capFinder,
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1", "SELECT pm1.g2.e1 FROM pm1.g2" }, SHOULD_SUCCEED); //$NON-NLS-1$ //$NON-NLS-2$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });         
    }
    
    /** one side of join supports order by, the other doesn't*/
    public void testUseMergeJoin7(){
        // Create query
        String sql = "SELECT pm1.g1.e1 FROM pm1.g1, pm2.g2 WHERE pm1.g1.e1 = pm2.g2.e1";//$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
        caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        capFinder.addCapabilities("pm2", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
        FakeMetadataObject g1 = metadata.getStore().findObject("pm1.g1", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g1.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 500));
        FakeMetadataObject g2 = metadata.getStore().findObject("pm2.g2", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g2.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 1000));
    
        ProcessorPlan plan = helpPlan(sql, metadata,  
            null, capFinder,
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1 ORDER BY pm1.g1.e1", "SELECT pm2.g2.e1 FROM pm2.g2" }, SHOULD_SUCCEED); //$NON-NLS-1$ //$NON-NLS-2$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });         
    }     

    /** reverse of testUseMergeJoin7 */
    public void testUseMergeJoin7a(){
        // Create query
        String sql = "SELECT pm1.g1.e1 FROM pm1.g1, pm2.g2 WHERE pm1.g1.e1 = pm2.g2.e1";//$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
        caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        capFinder.addCapabilities("pm2", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
        FakeMetadataObject g1 = metadata.getStore().findObject("pm1.g1", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g1.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 500));
        FakeMetadataObject g2 = metadata.getStore().findObject("pm2.g2", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g2.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 1000));
    
        ProcessorPlan plan = helpPlan(sql, metadata,  
            null, capFinder,
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1", "SELECT pm2.g2.e1 FROM pm2.g2 ORDER BY pm2.g2.e1" }, SHOULD_SUCCEED); //$NON-NLS-1$ //$NON-NLS-2$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });         
    }   

    /** function on one side of join should prevent order by from being pushed down*/
    public void testUseMergeJoin8(){
        // Create query
        String sql = "SELECT pm1.g1.e1 FROM pm1.g1, pm2.g2 WHERE concat(pm1.g1.e1, 'x') = pm2.g2.e1";//$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
        capFinder.addCapabilities("pm2", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
        FakeMetadataObject model = metadata.getStore().findObject("pm1", FakeMetadataObject.MODEL); //$NON-NLS-1$
        FakeMetadataObject model2 = metadata.getStore().findObject("pm2", FakeMetadataObject.MODEL); //$NON-NLS-1$
        FakeMetadataObject g1 = metadata.getStore().findObject("pm1.g1", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g1.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 500));
        FakeMetadataObject g2 = metadata.getStore().findObject("pm2.g2", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g2.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 1000));
    
        ProcessorPlan plan = helpPlan(sql, metadata,  
            null, capFinder,
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1", "SELECT pm2.g2.e1 FROM pm2.g2 ORDER BY pm2.g2.e1" }, SHOULD_SUCCEED); //$NON-NLS-1$ //$NON-NLS-2$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            2,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });         
    }

    /** Model supports order by, functions in join criteria */
    public void testUseMergeJoin9(){
        // Create query
        String sql = "SELECT pm1.g1.e1 FROM pm1.g1, pm1.g2 WHERE concat(pm1.g1.e1, 'x') = concat(pm1.g2.e1, 'x')";//$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
        FakeMetadataObject model = metadata.getStore().findObject("pm1", FakeMetadataObject.MODEL); //$NON-NLS-1$
        FakeMetadataObject g1 = metadata.getStore().findObject("pm1.g1", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g1.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 500));
        FakeMetadataObject g2 = metadata.getStore().findObject("pm1.g2", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g2.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 1000));
    
        ProcessorPlan plan = helpPlan(sql, metadata,  
            null, capFinder,
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1", "SELECT pm1.g2.e1 FROM pm1.g2" }, SHOULD_SUCCEED); //$NON-NLS-1$ //$NON-NLS-2$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            3,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });         
    } 

    /** should be one dependent join */
    public void testMultiMergeJoin1(){
        // Create query
        String sql = "SELECT pm1.g1.e1 FROM pm1.g1, pm1.g2, pm1.g3 WHERE pm1.g1.e1 = pm1.g2.e1 AND pm1.g2.e1 = pm1.g3.e1";//$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
        FakeMetadataObject g1 = metadata.getStore().findObject("pm1.g1", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g1.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 500));
        FakeMetadataObject g2 = metadata.getStore().findObject("pm1.g2", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g2.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 1000));
        FakeMetadataObject g3 = metadata.getStore().findObject("pm1.g3", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g3.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 1000));
    
        ProcessorPlan plan = helpPlan(sql, metadata,  
            null, capFinder,
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1", "SELECT pm1.g2.e1 FROM pm1.g2", "SELECT pm1.g3.e1 FROM pm1.g3" }, SHOULD_SUCCEED); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        checkNodeTypes(plan, new int[] {
            3,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            2,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });         
    } 

    public void testLargeSetCriteria() {
        //      Create query
        String sql = "SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA INNER JOIN BQT2.SmallB ON BQT1.SmallA.IntKey = BQT2.SmallB.IntKey WHERE BQT1.SmallA.IntKey IN (1,2,3,4,5)";     //$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setSourceProperty(Capability.MAX_IN_CRITERIA_SIZE, new Integer(1));
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        capFinder.addCapabilities("BQT2", caps); //$NON-NLS-1$
        
        ProcessorPlan plan = helpPlan(sql, FakeMetadataFactory.exampleBQTCached(),  
            null, capFinder,
            new String[] { "SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA ORDER BY BQT1.SmallA.IntKey",  //$NON-NLS-1$
                            "SELECT BQT2.SmallB.IntKey FROM BQT2.SmallB ORDER BY BQT2.SmallB.IntKey" }, SHOULD_SUCCEED); //$NON-NLS-1$ 
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            2,      // Select
            0,      // Sort
            0       // UnionAll
        });            
    }
    
    public void testMergeJoin_defect11236(){
        // Create query
        String sql = "SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA, BQT1.SmallB WHERE BQT1.SmallA.IntKey = (BQT1.SmallB.IntKey + 1)";     //$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setSourceProperty(Capability.MAX_IN_CRITERIA_SIZE, new Integer(1000));
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
    
        ProcessorPlan plan = helpPlan(sql, metadata,  
            null, capFinder,
            new String[] { "SELECT BQT1.SmallB.IntKey FROM BQT1.SmallB",  //$NON-NLS-1$
                            "SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA ORDER BY BQT1.SmallA.IntKey" }, SHOULD_SUCCEED); //$NON-NLS-1$ 
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            2,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });         
    }
        
    public void testNoFrom() { 
        ProcessorPlan plan = helpPlan("SELECT 1", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] {} ); 

        checkNodeTypes(plan, new int[] {
            0,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }

    public void testINCriteria_defect10718(){
        // Create query
        String sql = "SELECT pm1.g1.e1 FROM pm1.g1, pm1.g2 WHERE pm1.g1.e1 = pm1.g2.e1";//$NON-NLS-1$

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN, false);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT, true);
        caps.setSourceProperty(Capability.MAX_IN_CRITERIA_SIZE, new Integer(1000));
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
        FakeMetadataObject g1 = metadata.getStore().findObject("pm1.g1", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g1.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST - 1));
        FakeMetadataObject g2 = metadata.getStore().findObject("pm1.g2", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g2.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 1000));
    
        ProcessorPlan plan = helpPlan(sql, metadata,  
            null, capFinder,
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1 ORDER BY pm1.g1.e1", "SELECT pm1.g2.e1 FROM pm1.g2 ORDER BY pm1.g2.e1"}, SHOULD_SUCCEED); //$NON-NLS-1$  //$NON-NLS-2$ 
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });         
    }
    
    public void testDefect10711(){
        ProcessorPlan plan = helpPlan("SELECT * from vm1.g1a as X", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] {"SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1"} ); //$NON-NLS-1$

        checkNodeTypes(plan, FULL_PUSHDOWN);         

    }
    
    // SELECT 5, SUM(IntKey) FROM BQT1.SmallA
    public void testAggregateNoGroupByWithExpression() {
        ProcessorPlan plan = helpPlan("SELECT 5, SUM(IntKey) FROM BQT1.SmallA", FakeMetadataFactory.exampleBQTCached(), //$NON-NLS-1$
            new String[] { "SELECT IntKey FROM BQT1.SmallA"  }); //$NON-NLS-1$

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            1,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }
    
    /** defect 11630 - note that the lookup function is not pushed down, it will actually be evaluated before being sent to the connector */
    public void testLookupFunction() {

        ProcessorPlan plan = helpPlan("SELECT e1 FROM pm1.g2 WHERE LOOKUP('pm1.g1','e1', 'e2', 1) IS NULL", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g2 WHERE LOOKUP('pm1.g1', 'e1', 'e2', 1) IS NULL"  }); //$NON-NLS-1$

        checkNodeTypes(plan, FULL_PUSHDOWN); 

    }
    
    /** case 5213 - note here that the lookup cannot be pushed down since it is dependent upon an element symbol*/
    public void testLookupFunction2() throws Exception {

        ProcessorPlan plan = helpPlan("SELECT e1 FROM pm1.g2 WHERE LOOKUP('pm1.g1','e1', 'e2', e2) IS NULL", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT g_0.e2, g_0.e1 FROM pm1.g2 AS g_0"  }, ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        }); 

    }
    
    /** defect 21965 */
    public void testLookupFunctionInSelect() {
        ProcessorPlan plan = helpPlan("SELECT e1, LOOKUP('pm1.g1','e1', 'e2', 1) FROM pm1.g2", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT e1 FROM pm1.g2"  }); //$NON-NLS-1$

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 

    }
    
    // SELECT * FROM (SELECT IntKey FROM BQT1.SmallA UNION ALL SELECT DISTINCT IntNum FROM BQT1.SmallA) AS x WHERE IntKey = 0
    public void testCase1649() {
        ProcessorPlan plan = helpPlan("SELECT * FROM (SELECT DISTINCT IntKey FROM BQT1.SmallA UNION ALL SELECT IntNum FROM BQT1.SmallA) AS x WHERE IntKey = 0", FakeMetadataFactory.exampleBQTCached(), //$NON-NLS-1$
            new String[] { "SELECT DISTINCT IntKey FROM BQT1.SmallA WHERE IntKey = 0", "SELECT IntNum FROM BQT1.SmallA WHERE IntNum = 0"  }); //$NON-NLS-1$ //$NON-NLS-2$

        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            1       // UnionAll
        });                                    
    }   

    public void testDefect12298() throws Exception {      
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_NOT, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);
        capFinder.addCapabilities("SystemPhysical", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(
            "SELECT VDBName PKVDB, PKGroupFullName, PKElementName, VDBName FKVDB,FKGroupFullName, FKElementName, convert(FKPosition, short) As FKPosition, FKKeyName, PKKeyName " + //$NON-NLS-1$
            "FROM System.ReferenceKeyElements WHERE PKKeyType = 'Primary' AND FKKeyType = 'Foreign' AND FKGroupFullName = \"PartsOracle.SUPPLIER_PARTS\"",           //$NON-NLS-1$
            FakeMetadataFactory.exampleSystemPhysical(), 
            null, capFinder,
            new String[] { 
                            "SELECT g_2.VDB_NM, g_1.PATH_1, g_8.ELMNT_NM, g_10.PATH_1, g_17.ELMNT_NM, g_16.POSITION, g_9.KEY_NM, g_0.KEY_NM FROM ((((SystemPhysical.RT_KY_IDXES AS g_0 INNER JOIN ((SystemPhysical.RT_GRPS AS g_1 INNER JOIN ((SystemPhysical.RT_VIRTUAL_DBS AS g_2 INNER JOIN SystemPhysical.RT_VDB_MDLS AS g_3 ON g_2.VDB_UID = g_3.VDB_UID) INNER JOIN SystemPhysical.RT_MDLS AS g_4 ON g_3.MDL_UID = g_4.MDL_UID) ON g_1.MDL_UID = g_4.MDL_UID) INNER JOIN SystemPhysical.RT_TABLE_TYPES AS g_5 ON g_1.TABLE_TYPE = g_5.TABLE_TYPE_CODE) ON g_0.GRP_UID = g_1.GRP_UID) INNER JOIN SystemPhysical.RT_KEY_TYPES AS g_6 ON g_0.KEY_TYPE = g_6.KEY_TYPE_CODE) LEFT OUTER JOIN SystemPhysical.RT_KY_IDX_ELMNTS AS g_7 ON g_7.KEY_UID = g_0.KEY_UID) INNER JOIN SystemPhysical.RT_ELMNTS AS g_8 ON g_7.ELMNT_UID = g_8.ELMNT_UID) INNER JOIN ((((SystemPhysical.RT_KY_IDXES AS g_9 INNER JOIN ((SystemPhysical.RT_GRPS AS g_10 INNER JOIN ((SystemPhysical.RT_VIRTUAL_DBS AS g_11 INNER JOIN SystemPhysical.RT_VDB_MDLS AS g_12 ON g_11.VDB_UID = g_12.VDB_UID) INNER JOIN SystemPhysical.RT_MDLS AS g_13 ON g_12.MDL_UID = g_13.MDL_UID) ON g_10.MDL_UID = g_13.MDL_UID) INNER JOIN SystemPhysical.RT_TABLE_TYPES AS g_14 ON g_10.TABLE_TYPE = g_14.TABLE_TYPE_CODE) ON g_9.GRP_UID = g_10.GRP_UID) INNER JOIN SystemPhysical.RT_KEY_TYPES AS g_15 ON g_9.KEY_TYPE = g_15.KEY_TYPE_CODE) LEFT OUTER JOIN SystemPhysical.RT_KY_IDX_ELMNTS AS g_16 ON g_16.KEY_UID = g_9.KEY_UID) INNER JOIN SystemPhysical.RT_ELMNTS AS g_17 ON g_16.ELMNT_UID = g_17.ELMNT_UID) ON g_0.KEY_UID = g_9.REF_KEY_UID AND g_7.POSITION = g_16.POSITION WHERE (g_1.TABLE_TYPE <> 5) AND (g_3.VISIBILITY = 0) AND (g_5.TABLE_TYPE_CODE <> 5) AND (g_6.KEY_TYPE_NM = 'Primary') AND (g_10.TABLE_TYPE <> 5) AND (g_10.PATH_1 = 'PartsOracle.SUPPLIER_PARTS') AND (g_12.VISIBILITY = 0) AND (g_14.TABLE_TYPE_CODE <> 5) AND (g_15.KEY_TYPE_NM = 'Foreign')" //$NON-NLS-1$
                      },
                      ComparisonMode.EXACT_COMMAND_STRING);
                  checkNodeTypes(plan, new int[] {
                      1,      // Access
                      0,      // DependentAccess
                      0,      // DependentSelect
                      0,      // DependentProject
                      0,      // DupRemove
                      0,      // Grouping
                      0,      // NestedLoopJoinStrategy
                      0,      // MergeJoinStrategy
                      0,      // Null
                      0,      // PlanExecution
                      1,      // Project
                      0,      // Select
                      0,      // Sort
                      0       // UnionAll
            
        });         
    }
    
    // SELECT * FROM (SELECT IntKey a, IntNum b FROM BQT1.SmallA UNION ALL SELECT Intkey, Intkey FROM BQT1.SmallA) as x WHERE b = 0
    public void testCase1727_1() {
        ProcessorPlan plan = helpPlan("SELECT * FROM (SELECT IntKey a, IntNum b FROM BQT1.SmallA UNION ALL SELECT Intkey, Intkey FROM BQT1.SmallA) as x WHERE b = 0", FakeMetadataFactory.exampleBQTCached(), //$NON-NLS-1$
            new String[] { 
                "SELECT IntKey, IntNum FROM BQT1.SmallA WHERE IntNum = 0", //$NON-NLS-1$
                "SELECT IntKey, IntKey FROM BQT1.SmallA WHERE IntKey = 0"  }); //$NON-NLS-1$ 

        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            1       // UnionAll
        });                                    
    }

    // SELECT * FROM (SELECT IntKey a, IntNum b FROM BQT1.SmallA UNION ALL SELECT Intkey, Intkey FROM BQT1.SmallA) as x WHERE b = 0
    public void testCase1727_2() {
        ProcessorPlan plan = helpPlan("SELECT * FROM (SELECT IntKey a, IntKey b FROM BQT1.SmallA UNION ALL SELECT IntKey, IntNum FROM BQT1.SmallA) as x WHERE b = 0", FakeMetadataFactory.exampleBQTCached(), //$NON-NLS-1$
            new String[] { 
                "SELECT IntKey, IntNum FROM BQT1.SmallA WHERE IntNum = 0", //$NON-NLS-1$
                "SELECT IntKey, IntKey FROM BQT1.SmallA WHERE IntKey = 0"  }); //$NON-NLS-1$ 

        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            1       // UnionAll
        });                                    
    }
    
    public void testCountStarOverSelectDistinct() {
        ProcessorPlan plan = helpPlan("SELECT COUNT(*) FROM (SELECT DISTINCT IntNum, Intkey FROM bqt1.smalla) AS x", FakeMetadataFactory.exampleBQTCached(), //$NON-NLS-1$
                                      new String[] { 
                                          "SELECT DISTINCT IntNum, Intkey FROM bqt1.smalla" }); //$NON-NLS-1$ 

        checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        1,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
        });                                    
    }

    //virtual group with two elements. One selectable, one not
    public void testVirtualGroup1() {
        ProcessorPlan plan = helpPlan("select e2 from vm1.g35", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
            new String[] { "SELECT e2 FROM pm1.g1" } ); //$NON-NLS-1$

        checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);     
    }
    
    public void testBQT9500_126() {
        String sql = "SELECT IntKey, LongNum, expr FROM (SELECT IntKey, LongNum, concat(LongNum, 'abc') FROM BQT2.SmallA ) AS x ORDER BY IntKey"; //$NON-NLS-1$
        ProcessorPlan plan = helpPlan(sql, FakeMetadataFactory.exampleBQTCached(), 
                                      new String[] { 
                                          "SELECT IntKey, LongNum FROM BQT2.SmallA" }); //$NON-NLS-1$ 

        checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        1,      // Sort
                                        0       // UnionAll
        });                                    
        
    }
    
    public void helpTestUnionPushdown(boolean queryHasOrderBy, boolean hasUnionCapability, boolean hasUnionOrderByCapability) {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, hasUnionCapability);
        caps.setCapabilitySupport((Capability.QUERY_ORDERBY), hasUnionOrderByCapability);
        caps.setCapabilitySupport((Capability.QUERY_SET_ORDER_BY), hasUnionOrderByCapability);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        String sqlUnion = "SELECT IntKey FROM BQT1.SmallA UNION ALL SELECT IntKey FROM BQT1.SmallB";//$NON-NLS-1$
        String sqlOrderBy = sqlUnion + " ORDER BY IntKey"; //$NON-NLS-1$
        String sql = null;
        if(queryHasOrderBy) {
            sql = sqlOrderBy;
        } else {
            sql = sqlUnion;
        }
            
        String[] expectedSql = null;
        if(hasUnionCapability) {
            if(queryHasOrderBy && hasUnionOrderByCapability) {
                expectedSql = new String[] {sqlOrderBy };
            } else {
                expectedSql = new String[] {sqlUnion };
            }            
        } else {
            expectedSql = new String[] { "SELECT IntKey FROM BQT1.SmallA", "SELECT IntKey FROM BQT1.SmallB" };  //$NON-NLS-1$//$NON-NLS-2$
        }
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        ProcessorPlan plan = helpPlan(sql, metadata, 
                                      null, capFinder, expectedSql, SHOULD_SUCCEED);  

        int accessCount = hasUnionCapability ? 1 : 2;  
        int projectCount = 0;
        int sortCount = 0;
        if(queryHasOrderBy && ! (hasUnionCapability && hasUnionOrderByCapability)) { 
            sortCount = 1;    
        }
        int unionCount = hasUnionCapability ? 0 : 1;
        
        
        
        checkNodeTypes(plan, new int[] {
                                        accessCount,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        projectCount,      // Project
                                        0,      // Select
                                        sortCount,      // Sort
                                        unionCount       // UnionAll
        });                                                   
    }
    
    /**
     * Query has union but no order by and no capabilities.
     */
    public void testUnionPushdown1() {
        helpTestUnionPushdown(false, false, false);
    }

    /**
     * Query has union but no order by and only union capability.
     */
    public void testUnionPushdown2() {
        helpTestUnionPushdown(false, true, false);
    }

    /**
     * Query has union with order by and no capabilities.
     */
    public void testUnionPushdown3() {
        helpTestUnionPushdown(true, false, false);
    }

    /**
     * Query has union with order by and just union capability.
     */
    public void testUnionPushdown4() {
        helpTestUnionPushdown(true, true, false);
    }

    /**
     * Query has union with order by and both capabilities.
     */
    public void testUnionPushdown5() {
        helpTestUnionPushdown(true, true, true);
    }

    public void testUnionPushdownWithSelectNoFrom() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, false);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT 1 UNION ALL SELECT 2", FakeMetadataFactory.exampleBQTCached(),  //$NON-NLS-1$
                                      null, capFinder, new String[] {}, SHOULD_SUCCEED);  

        checkNodeTypes(plan, new int[] {
                                        0,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        2,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        1       // UnionAll
        });                                                   
    }

    public void testUnionPushdownWithSelectNoFromFirstBranch() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, false);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT 1 UNION ALL SELECT IntKey FROM BQT1.SmallA", FakeMetadataFactory.exampleBQTCached(),  //$NON-NLS-1$
                                      null, capFinder, new String[] {"SELECT IntKey FROM BQT1.SmallA"}, SHOULD_SUCCEED);   //$NON-NLS-1$

        checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        1       // UnionAll
        });                                                   
    }

    public void testUnionPushdownWithSelectNoFromSecondBranch() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, false);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT IntKey FROM BQT1.SmallA UNION ALL SELECT 1", FakeMetadataFactory.exampleBQTCached(),  //$NON-NLS-1$
                                      null, capFinder, new String[] {"SELECT IntKey FROM BQT1.SmallA"}, SHOULD_SUCCEED);   //$NON-NLS-1$

        checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        1       // UnionAll
        });                                                   
    }
    
    public void testUnionPushdownMultipleBranches() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, false);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT IntKey FROM BQT1.SmallA UNION ALL SELECT IntKey FROM BQT1.SmallB UNION ALL SELECT IntKey FROM BQT1.SmallA", FakeMetadataFactory.exampleBQTCached(),  //$NON-NLS-1$
                                      null, capFinder, 
                                      new String[] {"SELECT IntKey FROM BQT1.SmallA UNION ALL SELECT IntKey FROM BQT1.SmallB UNION ALL SELECT IntKey FROM BQT1.SmallA"},  //$NON-NLS-1$ 
                                      SHOULD_SUCCEED);   

        checkNodeTypes(plan, FULL_PUSHDOWN);                                                   
    }
    
    public void testUnionPushdownMultipleBranchesMixedModels1() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, false);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        capFinder.addCapabilities("BQT2", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT IntKey FROM BQT1.SmallA UNION ALL SELECT IntKey FROM BQT1.SmallB UNION ALL SELECT IntKey FROM BQT2.SmallA", FakeMetadataFactory.exampleBQTCached(),  //$NON-NLS-1$
                                      null, capFinder, 
                                      new String[] {"SELECT IntKey FROM BQT1.SmallA UNION ALL SELECT IntKey FROM BQT1.SmallB", "SELECT IntKey FROM BQT2.SmallA"},  //$NON-NLS-1$ //$NON-NLS-2$
                                      SHOULD_SUCCEED);   

        checkNodeTypes(plan, new int[] {
                                        2,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        0,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        1       // UnionAll
        });                                                   
    }
    
    public void testUnionPushdownMultipleBranchesNoDupRemoval() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, false);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT IntKey FROM BQT1.SmallA UNION SELECT IntKey FROM BQT1.SmallB UNION SELECT IntKey FROM BQT1.SmallA", FakeMetadataFactory.exampleBQTCached(),  //$NON-NLS-1$
                                      null, capFinder, 
                                      new String[] {"SELECT IntKey FROM BQT1.SmallA UNION SELECT IntKey FROM BQT1.SmallB UNION SELECT IntKey FROM BQT1.SmallA"},  //$NON-NLS-1$ 
                                      SHOULD_SUCCEED);   

        checkNodeTypes(plan, FULL_PUSHDOWN);                                                   
    }
    
    public void testAggregateOverUnionPushdown() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, false);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT COUNT(*) FROM (SELECT IntKey FROM BQT1.SmallA UNION SELECT IntKey FROM BQT1.SmallB) AS x", FakeMetadataFactory.exampleBQTCached(),  //$NON-NLS-1$
                                      null, capFinder, 
                                      new String[] {"SELECT IntKey FROM BQT1.SmallA UNION SELECT IntKey FROM BQT1.SmallB"},  //$NON-NLS-1$ 
                                      SHOULD_SUCCEED);   

        checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        1,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
        });                                                   
    }

    public void testUnionPushdownWithFunctionsAndAliases() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, false);
        caps.setFunctionSupport("+", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT IntKey+2, StringKey AS x FROM BQT1.SmallA UNION SELECT IntKey, StringKey FROM BQT1.SmallB", FakeMetadataFactory.exampleBQTCached(),  //$NON-NLS-1$
                                      null, capFinder, 
                                      new String[] {"SELECT (IntKey + 2), StringKey AS x FROM BQT1.SmallA UNION SELECT IntKey, StringKey FROM BQT1.SmallB"},  //$NON-NLS-1$ 
                                      SHOULD_SUCCEED);   

        checkNodeTypes(plan, FULL_PUSHDOWN);                                                   
    }
    
    public void testUnionPushdownWithInternalOrderBy() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, false);
        caps.setFunctionSupport("+", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("(SELECT IntKey FROM BQT1.SmallA ORDER BY IntKey) UNION ALL SELECT IntKey FROM BQT1.SmallB", FakeMetadataFactory.exampleBQTCached(),  //$NON-NLS-1$
                                      null, capFinder, 
                                      new String[] {"SELECT IntKey FROM BQT1.SmallA UNION ALL SELECT IntKey FROM BQT1.SmallB"},  //$NON-NLS-1$ 
                                      SHOULD_SUCCEED);   

        checkNodeTypes(plan, FULL_PUSHDOWN);                                                   
    }    

    public void testUnionPushdownWithInternalDistinct() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setCapabilitySupport(Capability.QUERY_SELECT_DISTINCT, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, false);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();

        ProcessorPlan plan = helpPlan("SELECT DISTINCT IntKey FROM BQT1.SmallA UNION ALL SELECT IntKey FROM BQT1.SmallB", metadata,  //$NON-NLS-1$
                                      null, capFinder, 
                                      new String[] {"SELECT DISTINCT IntKey FROM BQT1.SmallA UNION ALL SELECT IntKey FROM BQT1.SmallB"},  //$NON-NLS-1$ 
                                      SHOULD_SUCCEED);   

        checkNodeTypes(plan, FULL_PUSHDOWN);                                                   
    } 
    
    public void testUnionNoAllPushdownInInlineView() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, false);
        caps.setFunctionSupport("+", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT x FROM (SELECT IntKey+2, StringKey AS x FROM BQT1.SmallA UNION SELECT IntKey, StringKey FROM BQT1.SmallB) AS g", FakeMetadataFactory.exampleBQTCached(),  //$NON-NLS-1$
                                      null, capFinder, 
                                      new String[] {"SELECT (IntKey + 2), StringKey AS x FROM BQT1.SmallA UNION SELECT IntKey, StringKey FROM BQT1.SmallB"},  //$NON-NLS-1$ 
                                      SHOULD_SUCCEED);   

        checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
        });                                                   
    }

    public void testUnionAllPushdownInInlineView() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, false);
        caps.setFunctionSupport("+", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT x FROM (SELECT IntKey+2, StringKey AS x FROM BQT1.SmallA UNION ALL SELECT IntKey, StringKey FROM BQT1.SmallB) AS g", FakeMetadataFactory.exampleBQTCached(),  //$NON-NLS-1$
                                      null, capFinder, 
                                      new String[] {"SELECT StringKey AS x FROM BQT1.SmallA UNION ALL SELECT StringKey FROM BQT1.SmallB"},  //$NON-NLS-1$ 
                                      SHOULD_SUCCEED);   

        checkNodeTypes(plan, FULL_PUSHDOWN);                                                   
    }

    public void testUnionAllPushdownVirtualGroup() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, true);
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT * FROM vm1.g4", FakeMetadataFactory.example1Cached(),  //$NON-NLS-1$
                                      null, capFinder, 
                                      new String[] {"SELECT e1 FROM pm1.g1 UNION ALL SELECT convert(e2, string) FROM pm1.g2"},  //$NON-NLS-1$ 
                                      SHOULD_SUCCEED);   

        checkNodeTypes(plan, FULL_PUSHDOWN);                                                   
    }

    public void testUnionAllPushdownVirtualGroup2() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, true);
        capFinder.addCapabilities("pm3", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT e2 FROM vm1.g17", FakeMetadataFactory.example1Cached(),  //$NON-NLS-1$
                                      null, capFinder, 
                                      new String[] {"SELECT pm3.g1.e2 FROM pm3.g1 UNION ALL SELECT pm3.g2.e2 FROM pm3.g2"},  //$NON-NLS-1$ 
                                      SHOULD_SUCCEED);   

        checkNodeTypes(plan, FULL_PUSHDOWN);                                                   
    }

    public void testUnionAllPushdownVirtualGroup3() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT * FROM (SELECT intkey, 5 FROM BQT1.SmallA UNION ALL SELECT intnum, 10 FROM bqt1.smalla) AS x",  //$NON-NLS-1$
                                      FakeMetadataFactory.exampleBQTCached(),  
                                      null, capFinder, 
                                      new String[] {"SELECT intkey FROM BQT1.SmallA", "SELECT IntNum FROM bqt1.smalla"},  //$NON-NLS-1$ //$NON-NLS-2$
                                      SHOULD_SUCCEED);   

        checkNodeTypes(plan, new int[] {
                                        2,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        2,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        1       // UnionAll
        });                                                   
    }

    // Allow pushing literals
    public void testUnionAllPushdownVirtualGroup4() {        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, true);
        caps.setCapabilitySupport(Capability.QUERY_SELECT_EXPRESSION, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT * FROM (SELECT intkey, 5 FROM BQT1.SmallA UNION ALL SELECT intnum, 10 FROM bqt1.smalla) AS x",  //$NON-NLS-1$
                                      FakeMetadataFactory.exampleBQT(),  
                                      null, capFinder, 
                                      new String[] {"SELECT intkey, 5 FROM BQT1.SmallA UNION ALL SELECT IntNum, 10 FROM bqt1.smalla"},  //$NON-NLS-1$ 
                                      SHOULD_SUCCEED);   

        checkNodeTypes(plan, FULL_PUSHDOWN);                                                   
    }

    public void testPushCaseInSelect() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_CASE, true);
        caps.setCapabilitySupport(Capability.QUERY_SEARCHED_CASE, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
         
        ProcessorPlan plan = helpPlan(
            "SELECT CASE WHEN e1 = 'a' THEN 10 ELSE 0 END FROM pm1.g1",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT CASE WHEN e1 = 'a' THEN 10 ELSE 0 END FROM pm1.g1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);                                    
    }
    
    public void testCantPushCaseInSelectWithFunction() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_CASE, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
         
        ProcessorPlan plan = helpPlan(
            "SELECT CASE e1 WHEN 'a' THEN 10 ELSE (e2+0) END FROM pm1.g1",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT e1, e2 FROM pm1.g1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }

    public void testPushSearchedCaseInSelect() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_SEARCHED_CASE, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
         
        ProcessorPlan plan = helpPlan(
            "SELECT CASE WHEN e1 = 'a' THEN 10 ELSE 0 END FROM pm1.g1",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT CASE WHEN e1 = 'a' THEN 10 ELSE 0 END FROM pm1.g1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);                                    
    }

    public void testCantPushSearchedCaseInSelectWithFunction() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_SEARCHED_CASE, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.example1();
         
        ProcessorPlan plan = helpPlan(
            "SELECT CASE WHEN e1 = 'a' THEN 10 ELSE (e2+0) END FROM pm1.g1",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT e1, e2 FROM pm1.g1"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }

    public void testPushdownFunctionNotEvaluated() {
        FakeFunctionMetadataSource.setupFunctionLibrary();
        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);
        caps.setCapabilitySupport(Capability.CRITERIA_NOT, true);
        caps.setFunctionSupport("xyz", true);         //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT e1 FROM pm1.g1 WHERE xyz() > 0",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT e1 FROM pm1.g1 WHERE xyz() > 0"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);                                    
    }

    public void testNoSourceQuery() {
        ProcessorPlan plan = helpPlan("SELECT * FROM (select parsetimestamp(x,'yyyy-MM-dd') as c1 from (select '2004-10-20' as x) as y) as z " +//$NON-NLS-1$ 
                                      "WHERE c1= '2004-10-20 00:00:00.0'", FakeMetadataFactory.exampleBQTCached(), //$NON-NLS-1$
            new String[] {  }); 

        checkNodeTypes(plan, new int[] {
            0,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            2,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }
    
    /** defect 14510 */
    public void testDefect14510LookupFunction() {

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_SEARCHED_CASE, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQT();
        FakeMetadataObject g1 = metadata.getStore().findObject("BQT1.SmallA", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g1.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST - 1));
        FakeMetadataObject g2 = metadata.getStore().findObject("BQT1.SmallB", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g2.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 1000));
         
        ProcessorPlan plan = helpPlan(
            "SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA, BQT1.SmallB WHERE (BQT1.SmallA.IntKey = lookup('BQT1.SmallB', 'IntKey', 'StringKey', BQT1.SmallB.StringKey)) AND (BQT1.SmallA.IntKey = 1)",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA WHERE BQT1.SmallA.IntKey = 1", "SELECT BQT1.SmallB.StringKey FROM BQT1.SmallB"}, //$NON-NLS-1$ //$NON-NLS-2$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            1,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });             
    }    

    /** defect 14510 */
    public void testDefect14510LookupFunction2() {

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_SEARCHED_CASE, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setSourceProperty(Capability.MAX_IN_CRITERIA_SIZE, new Integer(1000));
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQT();
        FakeMetadataObject g1 = metadata.getStore().findObject("BQT1.SmallA", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g1.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST - 1));
        FakeMetadataObject g2 = metadata.getStore().findObject("BQT1.MediumB", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g2.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 1000));
         
        ProcessorPlan plan = helpPlan(
            "SELECT BQT1.SmallA.IntKey, BQT1.MediumB.IntKey FROM BQT1.SmallA LEFT OUTER JOIN BQT1.MediumB ON BQT1.SmallA.IntKey = lookup('BQT1.MediumB', 'IntKey', 'StringKey', BQT1.MediumB.StringKey)",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA", "SELECT BQT1.MediumB.StringKey, BQT1.MediumB.IntKey FROM BQT1.MediumB"}, //$NON-NLS-1$ //$NON-NLS-2$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            2,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });
    }

    /** defect 14510 */
    public void testDefect14510LookupFunction3() {

        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_SEARCHED_CASE, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setSourceProperty(Capability.MAX_IN_CRITERIA_SIZE, new Integer(1000));
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQT();
        FakeMetadataObject g1 = metadata.getStore().findObject("BQT1.SmallA", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g1.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST - 1));
        FakeMetadataObject g2 = metadata.getStore().findObject("BQT1.MediumB", FakeMetadataObject.GROUP); //$NON-NLS-1$
        g2.putProperty(FakeMetadataObject.Props.CARDINALITY, new Integer(NewCalculateCostUtil.DEFAULT_STRONG_COST + 1000));
         
        ProcessorPlan plan = helpPlan(
            "SELECT BQT1.SmallA.IntKey, BQT1.MediumB.IntKey FROM BQT1.MediumB RIGHT OUTER JOIN BQT1.SmallA ON BQT1.SmallA.IntKey = lookup('BQT1.MediumB', 'IntKey', 'StringKey',BQT1.MediumB.StringKey)",  //$NON-NLS-1$          
            metadata,
            null, capFinder,
            new String[] {"SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA", "SELECT BQT1.MediumB.StringKey, BQT1.MediumB.IntKey FROM BQT1.MediumB"}, //$NON-NLS-1$ //$NON-NLS-2$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            2,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });
    }    
    
    public void testCase2125() throws Exception {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);
        caps.setCapabilitySupport(Capability.CRITERIA_NOT, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_AVG, false);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_SUM, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);        
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_SCALAR, true);        
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_CORRELATED, true);        
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        String sql = "SELECT OD.IntKEy, P.IntKEy, O.IntKey " +  //$NON-NLS-1$
            "FROM (bqt1.smalla AS OD INNER JOIN bqt1.smallb AS P ON OD.StringKey = P.StringKey) " +  //$NON-NLS-1$
            "INNER JOIN bqt1.mediuma AS O ON O.IntKey = OD.IntKey " +  //$NON-NLS-1$
            "WHERE (OD.IntNum > (SELECT SUM(IntNum) FROM bqt1.smalla)) AND " +  //$NON-NLS-1$
            "(P.longnum > (SELECT AVG(LongNum) FROM bqt1.smallb WHERE bqt1.smallb.datevalue = O.datevalue))"; //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      FakeMetadataFactory.exampleBQTCached(),
                                      null, capFinder,
                                      new String[] {"SELECT g_1.longnum, g_2.datevalue, g_0.IntKEy, g_1.IntKEy, g_2.IntKey FROM bqt1.smalla AS g_0, bqt1.smallb AS g_1, bqt1.mediuma AS g_2 WHERE (g_0.StringKey = g_1.StringKey) AND (g_2.IntKey = g_0.IntKey) AND (g_0.IntNum > (SELECT SUM(g_3.IntNum) FROM bqt1.smalla AS g_3))"}, //$NON-NLS-1$ 
                                      ComparisonMode.EXACT_COMMAND_STRING );

        checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        1,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
                                    });
    }
    
    public void testPushdownLiteralInSelectUnderAggregate() {  
        String sql = "SELECT COUNT(*) FROM (SELECT '' AS y, a.IntKey FROM BQT1.SmallA a union all select '', b.intkey from bqt1.smallb b) AS x"; //$NON-NLS-1$

        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_SELECT_EXPRESSION, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT_STAR, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      FakeMetadataFactory.exampleBQTCached(),
                                      null, capFinder,
                                      new String[] {"SELECT '' AS y FROM BQT1.SmallA AS a UNION ALL SELECT '' FROM bqt1.smallb AS b"}, //$NON-NLS-1$ 
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        1,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
                                    });
    }

    public void testPushdownLiteralInSelectUnderAggregate2() {  
        String sql = "SELECT SUM(z) FROM (SELECT '' AS y, a.IntKey as z FROM BQT1.SmallA a union all select b.stringkey, 0 from bqt1.smallb b) AS x group by z"; //$NON-NLS-1$

        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_SELECT_EXPRESSION, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT_STAR, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      FakeMetadataFactory.exampleBQTCached(),
                                      null, capFinder,
                                      new String[] {"SELECT a.IntKey AS z FROM BQT1.SmallA AS a UNION ALL SELECT 0 FROM bqt1.smallb AS b"}, //$NON-NLS-1$ 
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        1,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
                                    });
    }

    public void testPushdownLiteralInSelectUnderAggregate3() {  
        String sql = "SELECT code, SUM(ID) FROM (SELECT IntKey AS ID, '' AS Code FROM BQT1.SmallA union all select intkey, stringkey from bqt1.smallb b) AS x group by code"; //$NON-NLS-1$

        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_SELECT_EXPRESSION, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT_STAR, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      FakeMetadataFactory.exampleBQTCached(),
                                      null, capFinder,
                                      new String[] {"SELECT '' AS Code, IntKey AS ID FROM BQT1.SmallA UNION ALL SELECT stringkey, intkey FROM bqt1.smallb AS b"}, //$NON-NLS-1$ 
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        1,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
                                    });
    }

    public void testPushdownLiteralInSelectWithOrderBy() {  
        String sql = "SELECT 1, concat('a', 'b' ) AS X FROM BQT1.SmallA where intkey = 0 " +  //$NON-NLS-1$
            "UNION ALL " +  //$NON-NLS-1$
            "select 2, 'Hello2' from BQT1.SmallA where intkey = 1 order by X desc"; //$NON-NLS-1$
        
        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SELECT_EXPRESSION, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      FakeMetadataFactory.exampleBQTCached(),
                                      null, capFinder,
                                      new String[] {"SELECT 1, 'ab' AS X FROM BQT1.SmallA WHERE intkey = 0 UNION ALL SELECT 2, 'Hello2' FROM BQT1.SmallA WHERE IntKey = 1 ORDER BY X DESC"}, //$NON-NLS-1$ 
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);
    }
    
    public void testUpdateWithElement() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);        
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        String sql = "UPDATE BQT1.SmallA SET IntKey = IntKey + 1"; //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      FakeMetadataFactory.exampleBQTCached(),
                                      null, capFinder,
                                      new String[] {"UPDATE BQT1.SmallA SET IntKey = (IntKey + 1)"}, //$NON-NLS-1$ 
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);
    }            
    
    public void testCase2187() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);        
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);        
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();

        String sql = "SELECT t.intkey FROM (SELECT a.IntKey FROM bqt1.smalla a left outer join bqt1.smallb b on a.intkey=b.intkey, bqt1.smalla x) as t full outer JOIN bqt1.smallb c on t.intkey = c.intkey"; //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      metadata,
                                      null, capFinder,
                                      new String[] {"SELECT a.IntKey FROM ((bqt1.smalla AS a LEFT OUTER JOIN bqt1.smallb AS b ON a.intkey = b.intkey) CROSS JOIN bqt1.smalla AS x) FULL OUTER JOIN bqt1.smallb AS c ON a.IntKey = c.intkey"}, //$NON-NLS-1$ 
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, TestOptimizer.FULL_PUSHDOWN);
    }        
    
    public void testMultiUnionMergeVirtual() throws Exception {
        String sql = "SELECT * FROM " +  //$NON-NLS-1$
            "(SELECT IntKey, 'a' AS s FROM (SELECT intkey, stringkey from BQT1.SmallA) as a union all " +  //$NON-NLS-1$
            "select IntKey, 'b' FROM (SELECT intkey, stringkey from BQT1.SmallA) as b union all " +  //$NON-NLS-1$
            "select IntKey, 'c' FROM (SELECT intkey, stringkey from BQT1.SmallA) as c " +  //$NON-NLS-1$
            ") AS x"; //$NON-NLS-1$

        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_SELECT_EXPRESSION, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      FakeMetadataFactory.exampleBQTCached(),
                                      null, capFinder,
                                      new String[] {"(SELECT g_2.intkey AS c_0, 'a' AS c_1 FROM BQT1.SmallA AS g_2 UNION ALL SELECT g_1.IntKey AS c_0, 'b' AS c_1 FROM BQT1.SmallA AS g_1) UNION ALL SELECT g_0.IntKey AS c_0, 'c' AS c_1 FROM BQT1.SmallA AS g_0"}, //$NON-NLS-1$ 
                                      ComparisonMode.EXACT_COMMAND_STRING );

        checkNodeTypes(plan, FULL_PUSHDOWN);        
    }
    
    public void testDefect16848_groupAliasNotSupported_1() {
        String sql = "SELECT sa.intkey, sa.objectvalue FROM bqt1.smalla AS sa WHERE (sa.intkey = 46) AND (sa.stringkey IN (46)) AND (sa.datevalue = (SELECT MAX(sb.datevalue) FROM bqt1.smalla AS sb WHERE (sb.intkey = sa.intkey) AND (sa.stringkey = sb.stringkey) ))"; //$NON-NLS-1$

        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, false);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      FakeMetadataFactory.exampleBQTCached(),
                                      null, capFinder,
                                      new String[] {"SELECT bqt1.smalla.datevalue, bqt1.smalla.intkey, bqt1.smalla.stringkey, bqt1.smalla.objectvalue FROM bqt1.smalla WHERE (bqt1.smalla.intkey = 46) AND (bqt1.smalla.stringkey = '46')"}, //$NON-NLS-1$
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        1,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
                                    });   
        
        Collection subplans = plan.getChildPlans();
        assertEquals(1, subplans.size());
        ProcessorPlan subplan = (ProcessorPlan) subplans.iterator().next();
        
        // Collect atomic queries
        Set actualQueries = getAtomicQueries(subplan);
        
        // Compare atomic queries
        HashSet<String> expectedQueries = new HashSet<String>(Arrays.asList(new String[] { "SELECT bqt1.smalla.datevalue FROM bqt1.smalla WHERE (bqt1.smalla.intkey = bqt1.smalla.intkey) AND (bqt1.smalla.stringkey = bqt1.smalla.stringkey)"})); //$NON-NLS-1$
        assertEquals("Did not get expected atomic queries for subplan: ", expectedQueries, actualQueries); //$NON-NLS-1$

        checkNodeTypes(subplan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        1,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
                                    });   

    }

    public void testFunctionOfAggregate1() {
        String sql = "SELECT SUM(IntKey) + 1 AS x FROM BQT1.SmallA GROUP BY IntKey"; //$NON-NLS-1$

        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_SUM, true);
        caps.setFunctionSupport("+", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      FakeMetadataFactory.exampleBQTCached(),
                                      null, capFinder,
                                      new String[] {"SELECT (SUM(IntKey) + 1) FROM BQT1.SmallA GROUP BY IntKey"}, //$NON-NLS-1$ 
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);        
    }

    public void testFunctionOfAggregateCantPush1() {
        String sql = "SELECT SUM(IntKey) + 1 AS x FROM BQT1.SmallA GROUP BY IntKey"; //$NON-NLS-1$

        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      FakeMetadataFactory.exampleBQTCached(),
                                      null, capFinder,
                                      new String[] {"SELECT IntKey FROM BQT1.SmallA"}, //$NON-NLS-1$ 
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        1,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
                                    });        
    }

    public void testFunctionOfAggregateCantPush3() {
        String sql = "SELECT avg(intkey) * 2 FROM BQT1.SmallA "; //$NON-NLS-1$

        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      FakeMetadataFactory.exampleBQTCached(),
                                      null, capFinder,
                                      new String[] {"SELECT intkey FROM BQT1.SmallA"}, //$NON-NLS-1$ 
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
                                        1,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        1,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        0,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
                                    });        
    }

    private void helpTestCase2589NonPushdown(String sql, String[] expected) {

        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, false);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, false);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, false);
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        ProcessorPlan plan = helpPlan(sql,           
                                      metadata,
                                      null, capFinder,
                                      expected, 
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
                                        2,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // Join
                                        1,      // MergeJoin
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
                                    });            
    
    }
    
    private void helpTestCase2589(String sql, String expected) throws Exception {  
        
        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, false);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, true);
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        ProcessorPlan plan = helpPlan(sql,           
                                      metadata,
                                      null, capFinder,
                                      new String[] {expected}, 
                                      ComparisonMode.EXACT_COMMAND_STRING );

        checkNodeTypes(plan, FULL_PUSHDOWN);          
    }
    
    public void testCase2589() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN " + //$NON-NLS-1$
                     "VQT.SmallA_2589 ON MediumA.IntKey = SmallA_2589.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN BQT1.SmallA ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10'"; //$NON-NLS-1$
        
        helpTestCase2589(sql, expected);
    }     

    public void testCase2589a() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN " + //$NON-NLS-1$
                     "VQT.SmallA_2589a ON MediumA.IntKey = SmallA_2589a.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN (BQT1.SmallA INNER JOIN BQT1.SmallB ON BQT1.SmallA.IntKey = BQT1.SmallB.IntKey) ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10'"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    }    

    public void testCase2589b() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN " + //$NON-NLS-1$
                     "VQT.SmallA_2589b ON MediumA.IntKey = SmallA_2589b.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN (BQT1.SmallA INNER JOIN BQT1.SmallB ON BQT1.SmallA.StringKey = BQT1.SmallB.StringKey) ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10'"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    }     
    
    public void testCase2589c() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumB, BQT1.MediumA LEFT OUTER JOIN " + //$NON-NLS-1$
                     "VQT.SmallA_2589b ON MediumA.IntKey = SmallA_2589b.IntKey " + //$NON-NLS-1$
                     "WHERE BQT1.MediumB.IntKey = BQT1.MediumA.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumB INNER JOIN (BQT1.MediumA LEFT OUTER JOIN (BQT1.SmallA INNER JOIN BQT1.SmallB ON BQT1.SmallA.StringKey = BQT1.SmallB.StringKey) ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10') ON BQT1.MediumB.IntKey = BQT1.MediumA.IntKey"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    }    

    public void testCase2589d() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumB INNER JOIN " + //$NON-NLS-1$
                     "(BQT1.MediumA LEFT OUTER JOIN VQT.SmallA_2589b ON MediumA.IntKey = SmallA_2589b.IntKey) " + //$NON-NLS-1$
                     "ON BQT1.MediumB.IntKey = BQT1.MediumA.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumB INNER JOIN (BQT1.MediumA LEFT OUTER JOIN (BQT1.SmallA INNER JOIN BQT1.SmallB ON BQT1.SmallA.StringKey = BQT1.SmallB.StringKey) ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10') ON BQT1.MediumB.IntKey = BQT1.MediumA.IntKey"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    }     
    
    public void testCase2589e() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumB LEFT OUTER JOIN " +  //$NON-NLS-1$
                     "(BQT1.MediumA LEFT OUTER JOIN VQT.SmallA_2589b ON MediumA.IntKey = SmallA_2589b.IntKey) " +  //$NON-NLS-1$
                     "ON BQT1.MediumB.IntKey = BQT1.MediumA.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumB LEFT OUTER JOIN (BQT1.MediumA LEFT OUTER JOIN (BQT1.SmallA INNER JOIN BQT1.SmallB ON BQT1.SmallA.StringKey = BQT1.SmallB.StringKey) ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10') ON BQT1.MediumB.IntKey = BQT1.MediumA.IntKey"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    }     
    
    public void testCase2589f() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumB LEFT OUTER JOIN " + //$NON-NLS-1$
                     "(BQT1.MediumA INNER JOIN VQT.SmallA_2589b ON MediumA.IntKey = SmallA_2589b.IntKey) " + //$NON-NLS-1$
                     "ON BQT1.MediumB.IntKey = BQT1.MediumA.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumB LEFT OUTER JOIN (BQT1.MediumA INNER JOIN (BQT1.SmallA INNER JOIN BQT1.SmallB ON BQT1.SmallA.StringKey = BQT1.SmallB.StringKey) ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey) ON BQT1.MediumB.IntKey = BQT1.MediumA.IntKey AND BQT1.SmallA.StringNum = '10'";//$NON-NLS-1$";
        helpTestCase2589(sql, expected);
    }    

    public void testCase2589g() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumB LEFT OUTER JOIN " + //$NON-NLS-1$ 
                     "(BQT1.MediumA INNER JOIN VQT.SmallA_2589c ON MediumA.IntKey = SmallA_2589c.IntKey) " + //$NON-NLS-1$ 
                     "ON BQT1.MediumB.IntKey = BQT1.MediumA.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumB LEFT OUTER JOIN " + //$NON-NLS-1$ 
                     "(BQT1.MediumA INNER JOIN (BQT1.SmallA INNER JOIN BQT1.SmallB " + //$NON-NLS-1$ 
                     "ON BQT1.SmallA.StringKey = BQT1.SmallB.StringKey AND " +  //$NON-NLS-1$
                     "concat(BQT1.SmallA.StringNum, BQT1.SmallB.StringNum) = '1010') " +  //$NON-NLS-1$
                     "ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey) ON BQT1.MediumB.IntKey = BQT1.MediumA.IntKey"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    }    
    
    public void testCase2589h() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN VQT.SmallA_2589c " + //$NON-NLS-1$ 
                     "ON MediumA.IntKey = SmallA_2589c.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN (BQT1.SmallA INNER JOIN BQT1.SmallB ON BQT1.SmallA.StringKey = BQT1.SmallB.StringKey AND concat(BQT1.SmallA.StringNum, BQT1.SmallB.StringNum) = '1010') ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey"; //$NON-NLS-1$ 
        helpTestCase2589(sql, expected);
    }
    
    public void testCase2589i() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN VQT.SmallA_2589d " + //$NON-NLS-1$ 
                     "ON MediumA.IntKey = SmallA_2589d.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN (BQT1.SmallA INNER JOIN BQT1.SmallB ON BQT1.SmallA.StringKey = BQT1.SmallB.StringKey) ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10' AND BQT1.SmallA.IntNum = 10"; //$NON-NLS-1$ 
        helpTestCase2589(sql, expected);
    }
    
    /**
     * Test optimization doesn't happen if an outer join isn't involved 
     */
    public void testCase2589j() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA INNER JOIN " + //$NON-NLS-1$
                     "VQT.SmallA_2589 ON MediumA.IntKey = SmallA_2589.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA, BQT1.SmallA WHERE (BQT1.MediumA.IntKey = BQT1.SmallA.IntKey) AND (BQT1.SmallA.StringNum = '10')"; //$NON-NLS-1$
        
        helpTestCase2589(sql, expected);
    }     
    
    /**
     * Test optimization doesn't happen if an outer join isn't involved 
     */
    public void testCase2589k() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA INNER JOIN " + //$NON-NLS-1$
                     "VQT.SmallA_2589b ON MediumA.IntKey = SmallA_2589b.IntKey"; //$NON-NLS-1$

        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA, BQT1.SmallA, BQT1.SmallB WHERE (BQT1.SmallA.StringKey = BQT1.SmallB.StringKey) AND (BQT1.MediumA.IntKey = BQT1.SmallA.IntKey) AND (BQT1.SmallA.StringNum = '10')"; //$NON-NLS-1$
        
        
        helpTestCase2589(sql, expected);
    }

    /**
     * Same as testCase2589 except right outer join
     */
    public void testCase2589l() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM VQT.SmallA_2589 RIGHT OUTER JOIN " + //$NON-NLS-1$
                     "BQT1.MediumA ON MediumA.IntKey = SmallA_2589.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN BQT1.SmallA ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10'"; //$NON-NLS-1$
        
        helpTestCase2589(sql, expected);
    }     
    
    /**
     * Same as testCase2589 except full outer join - criteria "below" full outer join cannot be
     * raised into the join criteria, so basically the virtual groups cannot be merged in this test.
     */
    public void testCase2589m() {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM VQT.SmallA_2589 FULL OUTER JOIN " + //$NON-NLS-1$
                     "BQT1.MediumA ON MediumA.IntKey = SmallA_2589.IntKey"; //$NON-NLS-1$
        
        String[] expected = new String[] { 
            "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA",  //$NON-NLS-1$
            "SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA WHERE StringNum = '10'" //$NON-NLS-1$
        };
        
        helpTestCase2589NonPushdown(sql, expected);
    }       

    /**
     * Same as testCase2589b except full outer join
     */
    public void testCase2589n() {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA FULL OUTER JOIN " + //$NON-NLS-1$
                     "VQT.SmallA_2589b ON MediumA.IntKey = SmallA_2589b.IntKey"; //$NON-NLS-1$
        
        String[] expected = new String[] { 
            "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA", //$NON-NLS-1$
            "SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA, BQT1.SmallB WHERE (BQT1.SmallA.StringKey = BQT1.SmallB.StringKey) AND (BQT1.SmallA.StringNum = '10')" //$NON-NLS-1$
        };
        helpTestCase2589NonPushdown(sql, expected);

    }     
    
    /**
     * Same as testCase2589 except with two virtual layers instead of one 
     */
    public void testCase2589o() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN " + //$NON-NLS-1$
                     "VQT.SmallA_2589f ON MediumA.IntKey = SmallA_2589f.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN BQT1.SmallA ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10'"; //$NON-NLS-1$
        
        helpTestCase2589(sql, expected);
    }     

    /**
     * Same as testCase2589b except with two virtual layers instead of one 
     */
    public void testCase2589p() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN " + //$NON-NLS-1$
                     "VQT.SmallA_2589g ON MediumA.IntKey = SmallA_2589g.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN (BQT1.SmallA INNER JOIN BQT1.SmallB ON BQT1.SmallA.StringKey = BQT1.SmallB.StringKey) ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10'"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    }     

    /**
     * Test 3 frames, where top frame has outer join, middle frame has inner join, and
     * bottom frame has criteria that must be made into join criteria.  
     */
    public void testCase2589q() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN " + //$NON-NLS-1$
                     "VQT.SmallA_2589h ON MediumA.IntKey = SmallA_2589h.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN (BQT1.SmallA INNER JOIN BQT1.SmallB ON BQT1.SmallA.StringKey = BQT1.SmallB.StringKey) ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10'"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    }       

    /**
     * Similar to testCase2589b, except virtual transformation has criteria on an 
     * element from each physical table
     */
    public void testCase2589r() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN " + //$NON-NLS-1$
                     "VQT.SmallA_2589i ON MediumA.IntKey = SmallA_2589i.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN (BQT1.SmallA INNER JOIN BQT1.SmallB ON BQT1.SmallA.StringKey = BQT1.SmallB.StringKey) ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10' AND BQT1.SmallB.StringNum = '10'"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    }      

    /**
     * Test user criteria that should NOT be moved into join clause 
     */
    public void testCase2589s() throws Exception {  
        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN " + //$NON-NLS-1$
                     "VQT.SmallA_2589 ON MediumA.IntKey = SmallA_2589.IntKey " + //$NON-NLS-1$
                     "WHERE BQT1.MediumA.IntNum = 10"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN BQT1.SmallA ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10' WHERE BQT1.MediumA.IntNum = 10"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    }      

    /**
     * Test user criteria that should NOT be moved into join clause 
     */
    public void testCase2589t() throws Exception {  
        String sql = "SELECT z.IntKey FROM (SELECT IntKey FROM BQT1.MediumA WHERE BQT1.MediumA.IntNum = 10) as z " + //$NON-NLS-1$
                     "LEFT OUTER JOIN " + //$NON-NLS-1$
                     "VQT.SmallA_2589 ON z.IntKey = SmallA_2589.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN BQT1.SmallA ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10' WHERE BQT1.MediumA.IntNum = 10"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    }    

    /**
     * The above test written with an inline view instead of a virtual group.
     * This test translates to - how can this query be rewritten without subqueries such
     * that the same results are produced?  More specifically, where should the criteria
     * go - WHERE clause or FROM clause? 
     */
    public void testCase2589u() throws Exception {  
        String sql = "SELECT z.IntKey FROM (SELECT IntKey FROM BQT1.MediumA WHERE IntNum = 10) as z " + //$NON-NLS-1$
                     "LEFT OUTER JOIN " + //$NON-NLS-1$
                     "(SELECT IntKey FROM BQT1.SmallA WHERE StringNum = '10') as y " + //$NON-NLS-1$
                     "ON z.IntKey = y.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN BQT1.SmallA ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10' WHERE BQT1.MediumA.IntNum = 10"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    } 

    /**
     * Same sql as testCase2589, but the model doesn't support outer joins, so 
     * case 2589 optimization shouldn't happen. 
     */
    public void testCase2589v() {  

        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN " + //$NON-NLS-1$
        "VQT.SmallA_2589 ON MediumA.IntKey = SmallA_2589.IntKey"; //$NON-NLS-1$
        
        String expected[] = new String[] {
            "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA", //$NON-NLS-1$
            "SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA WHERE StringNum = '10'" //$NON-NLS-1$                                          
        };
        
        
        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, false);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, false);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, false);
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        ProcessorPlan plan = helpPlan(sql,           
                                      metadata,
                                      null, capFinder,
                                      expected, 
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
                                        2,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        1,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
                                    });          
    }    

    /**
     * Same as previous testCase2589v, but with full outer join. 
     */
    public void testCase2589w() {  

        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA FULL OUTER JOIN " + //$NON-NLS-1$
        "VQT.SmallA_2589 ON MediumA.IntKey = SmallA_2589.IntKey"; //$NON-NLS-1$
        
        String expected[] = new String[] {
            "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA", //$NON-NLS-1$
            "SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA WHERE StringNum = '10'" //$NON-NLS-1$                                          
        };
        
        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, false);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, false);
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        ProcessorPlan plan = helpPlan(sql,           
                                      metadata,
                                      null, capFinder,
                                      expected, 
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
                                        2,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        1,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
                                    });          
    }    

    /**
     * Test a complicated join tree involving multiple models, but with a nested
     * outer join predicate spanning only one model, and see if the case 2589
     * fix happens.  The important thing is the criteria "StringNum = '10'" needs
     * to be put in the join criteria, not the where clause, of the second atomic
     * query, because in the user query it is on the inner side of an outer join.
     */
    public void testCase2589x() throws Exception {  

        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT2.SmallA INNER JOIN " + //$NON-NLS-1$
        "(BQT1.MediumA LEFT OUTER JOIN " + //$NON-NLS-1$
        "(SELECT IntKey FROM BQT1.SmallA WHERE StringNum = '10') as y " + //$NON-NLS-1$
        "ON MediumA.IntKey = y.IntKey) " + //$NON-NLS-1$
        "ON BQT2.SmallA.IntKey = BQT1.MediumA.IntKey"; //$NON-NLS-1$
        
        String expected[] = new String[] {
            "SELECT BQT2.SmallA.IntKey FROM BQT2.SmallA", //$NON-NLS-1$
            "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN BQT1.SmallA ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10'" //$NON-NLS-1$                                          
        };
        
        
        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, false);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, true);
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        capFinder.addCapabilities("BQT2", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        ProcessorPlan plan = helpPlan(sql,           
                                      metadata,
                                      null, capFinder,
                                      expected, 
                                      ComparisonMode.EXACT_COMMAND_STRING );

        checkNodeTypes(plan, new int[] {
                                        2,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        1,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
                                    });          
    }     

    /**
     * Test two outer joins, one nested within the other, all pushable to one source, 
     * with inline views having criteria that each need to be migrated to their 
     * respective join predicate join criteria.
     * 
     * The tree below illustrates the canonical plan (plus access nodes).  'y' and
     * 'z' are two inline views.  Notice each has a SELECT node underneath - the
     * criteria represented by each of those SELECT nodes is on the inner side of
     * their respective left outer joins (LOJ).  So, each criteria needs to be
     * migrated to the join criteria.
     * 
     * <pre>
     *          LOJ
     *         /    \
     *      LOJ      SRC z
     *    /    \       |
     *  SRC    SRC y  SEL
     *  MedB    |      |
     *         SEL    ACC
     *          |      |
     *         ACC    SRC SmA
     *          |
     *         SRC MedA
     * </pre>
     * Here's a diagram of what the join plan of the resulting atomic query should
     * look like.
     * <pre>
     *          ACC 
     *           |
     *          LOJ**
     *         /    \    
     *      LOJ**    SRC       **criteria migrated to here
     *    /    \     SmA
     *  SRC    SRC  
     *  MedB   MedA           
     * </pre>
     */
    public void testCase2589y() throws Exception {  
        String sql = "SELECT L.IntKey, y.IntKey, z.IntKey " + //$NON-NLS-1$
                     "FROM (BQT1.MediumB as L LEFT OUTER JOIN " + //$NON-NLS-1$
                     "(SELECT IntKey FROM BQT1.MediumA as M WHERE M.IntNum = 4) as y ON y.IntKey = L.IntKey) " + //$NON-NLS-1$
                     "LEFT OUTER JOIN (SELECT IntKey FROM BQT1.SmallA as S WHERE S.StringNum = '10') as z " + //$NON-NLS-1$
                     "ON z.IntKey = y.IntKey"; //$NON-NLS-1$
        
        String expected = "SELECT BQT1.MediumB.IntKey, BQT1.MediumA.IntKey, BQT1.SmallA.IntKey " + //$NON-NLS-1$
                     "FROM (BQT1.MediumB LEFT OUTER JOIN " + //$NON-NLS-1$
                     "BQT1.MediumA ON BQT1.MediumA.IntKey = BQT1.MediumB.IntKey AND BQT1.MediumA.IntNum = 4) " + //$NON-NLS-1$
                     "LEFT OUTER JOIN BQT1.SmallA " + //$NON-NLS-1$
                     "ON BQT1.SmallA.IntKey = BQT1.MediumA.IntKey AND BQT1.SmallA.StringNum = '10'"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    }    
    
    /**
     * Test a complicated join tree involving multiple models, but with a nested
     * outer join predicate spanning only one model, and see if the case 2589
     * fix happens.  The important thing is the criteria "StringNum = '10'" needs
     * to be put in the join criteria, not the where clause, of the second atomic
     * query, because in the user query it is on the inner side of an outer join.
     */
    public void testCase2589z() {  

        String sql = "SELECT BQT1.MediumA.IntKey FROM BQT2.SmallA INNER JOIN " + //$NON-NLS-1$
        "(BQT1.MediumA LEFT OUTER JOIN " + //$NON-NLS-1$
        "(SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA, BQT1.SmallB " + //$NON-NLS-1$
        "WHERE BQT1.SmallA.IntKey = BQT1.SmallB.IntKey AND BQT1.SmallA.StringNum = '10') as y " + //$NON-NLS-1$
        "ON MediumA.IntKey = y.IntKey) " + //$NON-NLS-1$
        "ON BQT2.SmallA.IntKey = BQT1.MediumA.IntKey"; //$NON-NLS-1$
        
        String expected[] = new String[] {
            "SELECT BQT2.SmallA.IntKey FROM BQT2.SmallA", //$NON-NLS-1$
            "SELECT BQT1.MediumA.IntKey FROM BQT1.MediumA LEFT OUTER JOIN (BQT1.SmallA INNER JOIN BQT1.SmallB ON BQT1.SmallA.IntKey = BQT1.SmallB.IntKey) ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10'" //$NON-NLS-1$                                          
        };
        
        
        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, false);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, true);
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        capFinder.addCapabilities("BQT2", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        ProcessorPlan plan = helpPlan(sql,           
                                      metadata,
                                      null, capFinder,
                                      expected, 
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
                                        2,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // NestedLoopJoinStrategy
                                        1,      // MergeJoinStrategy
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
                                    });          
    }     

    /**
     * Union with multiple joins underneath 
     */
    public void testCase2589aa() throws Exception {  
        String sql = "SELECT * FROM (SELECT z.IntKey FROM (SELECT IntKey FROM BQT1.MediumA WHERE IntNum = 10) as z " + //$NON-NLS-1$
                     "LEFT OUTER JOIN " + //$NON-NLS-1$
                     "(SELECT IntKey FROM BQT1.SmallA WHERE StringNum = '10') as y " + //$NON-NLS-1$
                     "ON z.IntKey = y.IntKey " + //$NON-NLS-1$
                    "UNION ALL SELECT z.IntKey FROM (SELECT IntKey FROM BQT1.MediumA WHERE IntNum = 10) as z " + //$NON-NLS-1$
                    "LEFT OUTER JOIN " + //$NON-NLS-1$
                    "(SELECT IntKey FROM BQT1.SmallA WHERE StringNum = '10') as y " + //$NON-NLS-1$
                    "ON z.IntKey = y.IntKey) as x"; //$NON-NLS-1$

        String expected = "SELECT BQT1.MediumA.IntKey AS c_0 FROM BQT1.MediumA LEFT OUTER JOIN BQT1.SmallA ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10' WHERE BQT1.MediumA.IntNum = 10 UNION ALL SELECT BQT1.MediumA.IntKey AS c_0 FROM BQT1.MediumA LEFT OUTER JOIN BQT1.SmallA ON BQT1.MediumA.IntKey = BQT1.SmallA.IntKey AND BQT1.SmallA.StringNum = '10' WHERE BQT1.MediumA.IntNum = 10"; //$NON-NLS-1$
        helpTestCase2589(sql, expected);
    }

    /**
     * Since can now guarantee unique select column names, it's ok to have repeated entries in the order by clause.
     */
    public void testOrderByDuplicates() throws Exception {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);        
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);  
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();

        String sql = "SELECT intkey, x FROM (select intkey, intkey x from bqt1.smalla) z ORDER BY x, intkey"; //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      metadata,
                                      null, capFinder,
                                      new String[] {"SELECT g_0.intkey AS c_0, g_0.intkey AS c_1 FROM bqt1.smalla AS g_0 ORDER BY c_1, c_0"},  //$NON-NLS-1$
                                      ComparisonMode.EXACT_COMMAND_STRING );

        checkNodeTypes(plan, FULL_PUSHDOWN);
    }

    //Test use of OrderBy with expression
    public void testCase2507() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);        
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);  
        caps.setFunctionSupport("||", true); //$NON-NLS-1$
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();

        String sql = "SELECT vqt.smallb.a12345 FROM vqt.smallb ORDER BY vqt.smallb.a12345"; //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      metadata,
                                      null, capFinder,
                                      new String[] {"SELECT Concat(stringKey, stringNum) AS EXPR FROM BQT1.SmallA ORDER BY EXPR"},  //$NON-NLS-1$
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);
    }
    
    public void testCase2507A() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);        
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);  
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();

        String sql = "SELECT CONCAT(bqt1.smalla.stringKey, bqt1.smalla.stringNum) as EXPR, bqt1.smalla.stringKey as EXPR_1 FROM bqt1.smalla  ORDER BY EXPR, EXPR_1"; //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      metadata,
                                      null, capFinder,
                                      new String[] {"SELECT CONCAT(bqt1.smalla.stringKey, bqt1.smalla.stringNum) AS EXPR, bqt1.smalla.stringKey AS EXPR_1 FROM bqt1.smalla ORDER BY EXPR, EXPR_1"},  //$NON-NLS-1$
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);
    }  
    
    public void testCase2507B() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);        
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);  
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();

        String sql = "SELECT CONCAT(bqt1.smalla.stringKey, bqt1.smalla.stringNum), bqt1.smalla.stringKey as EXPR_1 FROM bqt1.smalla ORDER BY EXPR_1"; //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql,  
                                      metadata,
                                      null, capFinder,
                                      new String[] {"SELECT CONCAT(bqt1.smalla.stringKey, bqt1.smalla.stringNum), bqt1.smalla.stringKey AS EXPR_1 FROM bqt1.smalla ORDER BY EXPR_1"},  //$NON-NLS-1$
                                      SHOULD_SUCCEED );

        checkNodeTypes(plan, FULL_PUSHDOWN);
    }
        
    /**
     * RulePlanJoins does not initially allow the cross join push.
     * The subsequent RuleRaiseAccess does since we believe it was the intent of the user
     */
    public void testPushCrossJoins() throws Exception {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);        
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        String sql = "SELECT b1.intkey from (bqt1.SmallA a1 cross join bqt1.smalla a2 cross join bqt1.mediuma b1) " +    //$NON-NLS-1$ 
            " left outer join bqt1.mediumb b2 on b1.intkey = b2.intkey"; //$NON-NLS-1$         
        
        ProcessorPlan plan = helpPlan(sql,  
                                      metadata,
                                      null, capFinder,
                                      new String[] {"SELECT g_2.intkey FROM ((bqt1.SmallA AS g_0 CROSS JOIN bqt1.smalla AS g_1) CROSS JOIN bqt1.mediuma AS g_2) LEFT OUTER JOIN bqt1.mediumb AS g_3 ON g_2.intkey = g_3.intkey"},  //$NON-NLS-1$
                                      ComparisonMode.EXACT_COMMAND_STRING );
        
        checkNodeTypes(plan, FULL_PUSHDOWN);        
    }
    
    public void testCase3023() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);        
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);  
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        String sql = "SELECT bqt1.SmallA.intkey from (bqt1.SmallA inner join (" //$NON-NLS-1$         
            + "SELECT BAD.intkey from bqt1.SmallB as BAD left outer join bqt1.MediumB on BAD.intkey = bqt1.MediumB.intkey) as X on bqt1.SmallA.intkey = X.intkey) inner join bqt1.MediumA on X.intkey = bqt1.MediumA.intkey"; //$NON-NLS-1$
        
         helpPlan(sql,  
                                      metadata,
                                      null, capFinder,
                                      new String[] {"SELECT bqt1.SmallA.intkey FROM (bqt1.SmallA INNER JOIN (bqt1.SmallB AS BAD LEFT OUTER JOIN bqt1.MediumB ON BAD.intkey = bqt1.MediumB.intkey) ON bqt1.SmallA.intkey = BAD.intkey) INNER JOIN bqt1.MediumA ON BAD.intkey = bqt1.MediumA.intkey"},  //$NON-NLS-1$
                                      SHOULD_SUCCEED );
    }
    
    public void testCase3367() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        FakeMetadataFacade metadata = example1();

        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN_SUBQUERY, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_CORRELATED, true);
        
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
        
        ProcessorPlan plan = helpPlan("select e1 from pm1.g1 where pm1.g1.e1 IN (SELECT pm1.g2.e1 FROM pm1.g2 WHERE (pm1.g1.e1 = 2))", metadata,  //$NON-NLS-1$
                                      null, capFinder,
            new String[] { "SELECT e1 FROM pm1.g1 WHERE pm1.g1.e1 IN (SELECT pm1.g2.e1 FROM pm1.g2 WHERE pm1.g1.e1 = '2')" }, SHOULD_SUCCEED); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
          
        checkSubPlanCount(plan, 0);        
    }
    
    /*
     * Set criteria was not getting pushed down correctly when there was a self-join 
     * of a virtual table containing a join in it's transformation.  All virtual 
     * models use the same physical model pm1.
     */
    public void testCase3778() throws Exception {
    	
    	FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
        
        BasicSourceCapabilities caps = getTypicalCapabilities();
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN_SUBQUERY, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
                
        ProcessorPlan plan = helpPlan(
        		"select a.e1, b.e1 from vm2.g1 a, vm2.g1 b where a.e1 = b.e1 and a.e2 in (select e2 from vm1.g1)",  //$NON-NLS-1$
        		metadata, null, capFinder, new String[] {"SELECT g_1.e1, g_3.e1 FROM pm1.g1 AS g_0, pm1.g2 AS g_1, pm1.g1 AS g_2, pm1.g2 AS g_3 WHERE (g_2.e2 = g_3.e2) AND (g_0.e2 = g_1.e2) AND (g_1.e1 = g_3.e1) AND (g_1.e2 IN (SELECT g_4.e2 FROM pm1.g1 AS g_4))"}, ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$

        checkNodeTypes(plan, FULL_PUSHDOWN);    
        
        checkSubPlanCount(plan, 0);
    }
    
    /** 
     * Ensures that order by expressions are not repeated when multiple criteria span a merge join 
     */ 
    public void testCase3832() throws Exception { 
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder(); 
        BasicSourceCapabilities caps = new BasicSourceCapabilities(); 
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true); 
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);         
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true); 
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true); 
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);   
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$ 
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$ 
        capFinder.addCapabilities("BQT2", caps); //$NON-NLS-1$ 
         
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
         
        String sql = "select bqt1.smalla.intkey from bqt1.smalla, bqt2.smalla, bqt2.smallb where bqt1.smalla.intkey = bqt2.smalla.intkey and bqt1.smalla.intkey = bqt2.smallb.intkey and bqt2.smalla.stringkey = bqt2.smallb.stringkey"; //$NON-NLS-1$ 
         
        helpPlan(sql, 
                 metadata, 
                 null, 
                 capFinder, 
                 new String[] { 
                     "SELECT bqt2.smallb.intkey AS c_0, bqt2.smalla.intkey AS c_1 FROM bqt2.smalla, bqt2.smallb WHERE bqt2.smalla.stringkey = bqt2.smallb.stringkey ORDER BY c_0, c_1", //$NON-NLS-1$ 
                     "SELECT bqt1.smalla.intkey AS c_0 FROM bqt1.smalla ORDER BY c_0"}, //$NON-NLS-1$ 
                 ComparisonMode.EXACT_COMMAND_STRING); 
             
    } 
    
    /*
     * Functions containing exec statements should not be evaluated
     */
    public void testDefect21972() { 
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder(); 
        BasicSourceCapabilities caps = new BasicSourceCapabilities(); 
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_SCALAR, true);
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$ 
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$ 
         
        String sql = "select e1 from pm1.g1 where e1 = convert((exec pm1.sq11(1, 2)), integer)"; //$NON-NLS-1$ 
         
        helpPlan(sql, 
                 FakeMetadataFactory.example1Cached(), 
                 null, 
                 capFinder, 
                 new String[] { 
                     "SELECT e1 FROM pm1.g1"}, //$NON-NLS-1$ 
                 SHOULD_SUCCEED); 
             
    }
    
    public void testExpressionSymbolPreservation() throws Exception { 
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder(); 
        BasicSourceCapabilities caps = new BasicSourceCapabilities(); 
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true); 
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true); 
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true); 
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true); 
        caps.setCapabilitySupport(Capability.QUERY_SELECT_EXPRESSION, true); 
        capFinder.addCapabilities("BQT2", caps); //$NON-NLS-1$ 
         
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached(); 
         
        String sql = "SELECT * from (select '1' as test, intkey from bqt2.smalla) foo, (select '2' as test, intkey from bqt2.smalla) foo2 where foo.intkey = foo2.intkey"; //$NON-NLS-1$ 
         
        helpPlan(sql,  
                                      metadata, 
                                      null, capFinder, 
                                      new String[] {"SELECT '1', g_0.intkey, '2', g_1.IntKey FROM bqt2.smalla AS g_0, bqt2.smalla AS g_1 WHERE g_0.intkey = g_1.IntKey"},  //$NON-NLS-1$ 
                                      ComparisonMode.EXACT_COMMAND_STRING ); 
             
    } 
    
    public static FakeMetadataFacade createInlineViewMetadata(FakeCapabilitiesFinder capFinder) {
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();

        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_INLINE_VIEWS, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT, true);
        caps.setCapabilitySupport(Capability.QUERY_SELECT_DISTINCT, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SET_ORDER_BY, true);
        caps.setCapabilitySupport(Capability.QUERY_SEARCHED_CASE, true);
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$
        caps.setFunctionSupport("case", true); //$NON-NLS-1$
        caps.setFunctionSupport("+", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        capFinder.addCapabilities("BQT2", caps); //$NON-NLS-1$
        
        return metadata;
    }
            
    /**
     * Order by's will be added to the atomic queries
     */
    public void testCrossSourceInlineView() throws Exception {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        FakeMetadataFacade metadata = createInlineViewMetadata(capFinder);
        
        ProcessorPlan plan = helpPlan("select * from (select count(bqt1.smalla.intkey) as a, bqt1.smalla.intkey from bqt1.smalla group by bqt1.smalla.intkey) q1 inner join (select count(bqt2.smallb.intkey) as a, bqt2.smallb.intkey from bqt2.smallb group by bqt2.smallb.intkey) as q2 on q1.intkey = q2.intkey where q1.a = 1", //$NON-NLS-1$
                metadata, null, capFinder, new String[] {"SELECT v_0.c_0, v_0.c_1 FROM (SELECT g_0.intkey AS c_0, COUNT(g_0.intkey) AS c_1 FROM bqt2.smallb AS g_0 GROUP BY g_0.intkey) AS v_0 ORDER BY c_0", //$NON-NLS-1$
                                                         "SELECT v_0.c_0, v_0.c_1 FROM (SELECT g_0.intkey AS c_0, COUNT(g_0.intkey) AS c_1 FROM bqt1.smalla AS g_0 GROUP BY g_0.intkey HAVING COUNT(g_0.intkey) = 1) AS v_0 ORDER BY c_0"}, ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$

        checkNodeTypes(plan, new int[] {
                2,      // Access
                0,      // DependentAccess
                0,      // DependentSelect
                0,      // DependentProject
                0,      // DupRemove
                0,      // Grouping
                0,      // Join
                1,      // MergeJoin
                0,      // Null
                0,      // PlanExecution
                1,      // Project
                0,      // Select
                0,      // Sort
                0       // UnionAll
            });    
            
        checkSubPlanCount(plan, 0);
    }
    
    public void testAliasPreservationWithInlineView() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        FakeMetadataFacade metadata = createInlineViewMetadata(capFinder);
        
        ProcessorPlan plan = helpPlan("select q1.a + 1, q1.b from (select count(bqt1.smalla.intNum) as a, bqt1.smalla.intkey as b from bqt1.smalla group by bqt1.smalla.intNum, bqt1.smalla.intkey order by b) q1 where q1.a = 1", //$NON-NLS-1$
                metadata, null, capFinder, new String[] {"SELECT (q1.a + 1), q1.b FROM (SELECT COUNT(bqt1.smalla.intNum) AS a, bqt1.smalla.intkey AS b FROM bqt1.smalla GROUP BY bqt1.smalla.intNum, bqt1.smalla.intkey HAVING COUNT(bqt1.smalla.intNum) = 1) AS q1"}, true); //$NON-NLS-1$

        checkNodeTypes(plan, FULL_PUSHDOWN);    
            
        checkSubPlanCount(plan, 0);
    }
    
    //since this does not support convert, it should not be collapsed
    public void testBadCollapseUnion() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        String sql = "select convert(e2+1,string) from pm1.g1 union all select e1 from pm1.g2";//$NON-NLS-1$
        String[] expectedSql = new String[] {"SELECT e2 FROM pm1.g1", "SELECT e1 FROM pm1.g2"};//$NON-NLS-1$ //$NON-NLS-2$
        
        ProcessorPlan plan = helpPlan(sql, FakeMetadataFactory.example1Cached(), 
                                      null, capFinder, expectedSql, SHOULD_SUCCEED);  

        checkNodeTypes(plan, new int[] {
                                        2,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        0,      // Join
                                        0,      // MergeJoin
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        1       // UnionAll
        });                                                   
        
    }
    
    public void testCase3966() {
        ProcessorPlan plan = helpPlan("insert into vm1.g37 (e1, e2, e3, e4) values('test', 1, convert('true', boolean) , convert('12', double) )", FakeMetadataFactory.example1(), //$NON-NLS-1$
                                      new String[] {} ); 

        checkNodeTypes(plan, new int[] {
            0,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // Join
            0,      // MergeJoin
            0,      // Null
            1,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });    
                                  
        checkSubPlanCount(plan, 1); 
    }
    
    /*
     * Select literals created by runtime evaluation should not be pushed down.
     */
    public void testCase4017() {
        
        String sql = "SELECT env('soap_host') AS HOST, intkey from bqt2.smalla"; //$NON-NLS-1$
        
        helpPlan(sql,  FakeMetadataFactory.exampleBQTCached(),
                                      new String[] {"SELECT intkey FROM bqt2.smalla"});  //$NON-NLS-1$      
    }
        
    /**
     * Test of RuleCopyCriteria.  Criteria should NOT be copied across a join if the join has any other operator
     * other than an equality operator, but if the single group criteria is equality, then we can copy into a join criteria   
     */
    public void testCase4265() throws Exception {
        String sql = "SELECT X.intkey, Y.intkey FROM BQT1.SmallA X, BQT1.SmallA Y WHERE X.IntKey <> Y.IntKey and Y.IntKey = 1"; //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql, FakeMetadataFactory.exampleBQTCached(), 
                                      new String[] { 
                                          "SELECT g_0.IntKey FROM BQT1.SmallA AS g_0 WHERE g_0.IntKey <> 1",  //$NON-NLS-1$ 
                                          "SELECT g_0.IntKey FROM BQT1.SmallA AS g_0 WHERE g_0.IntKey = 1" }, ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$ 

        checkNodeTypes(plan, new int[] {
                                        2,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        1,      // Join
                                        0,      // MergeJoin
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
        });                                    
        
    }    
    
    /**
     * Test of RuleCopyCriteria.  Criteria should be copied across a join only for an equality operator in
     * the join criteria.
     */
    public void testCase4265ControlTest() throws Exception {
        String sql = "SELECT X.intkey, Y.intkey FROM BQT1.SmallA X, BQT1.SmallA Y WHERE X.IntKey = Y.IntKey and Y.IntKey = 1"; //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql, FakeMetadataFactory.exampleBQTCached(), 
                                      new String[] { 
                                          "SELECT g_0.intkey FROM BQT1.SmallA AS g_0 WHERE g_0.IntKey = 1" }, ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$ 

        checkNodeTypes(plan, new int[] {
                                        2,      // Access
                                        0,      // DependentAccess
                                        0,      // DependentSelect
                                        0,      // DependentProject
                                        0,      // DupRemove
                                        0,      // Grouping
                                        1,      // Join
                                        0,      // MergeJoin
                                        0,      // Null
                                        0,      // PlanExecution
                                        1,      // Project
                                        0,      // Select
                                        0,      // Sort
                                        0       // UnionAll
        });                                    
        
    }     
    
    /**
     * The bug was in FrameUtil.convertCriteria() method, where ExistsCriteria was not being checked for. 
     */
    public void testExistsCriteriaInSelect() {
        String sql = "select intkey, case when exists (select stringkey from bqt1.smallb) then 'nuge' end as a from vqt.smalla"; //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql, FakeMetadataFactory.exampleBQTCached(), 
                                      new String[] { 
                                          "SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA" }); //$NON-NLS-1$ 

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            1,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // Join
            0,      // MergeJoin
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                   
        
    }    
    
    /**
     * Try substituting "is not null" for "exists" criteria 
     */
    public void testScalarSubQueryInSelect() {
        String sql = "select intkey, case when (select stringkey from bqt1.smallb) is not null then 'nuge' end as a from vqt.smalla"; //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql, FakeMetadataFactory.exampleBQTCached(), 
                                      new String[] { 
                                          "SELECT BQT1.SmallA.IntKey FROM BQT1.SmallA" }); //$NON-NLS-1$ 

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            1,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // Join
            0,      // MergeJoin
            0,      // Null
            0,      // PlanExecution
            0,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });                                   
        
    }
    
    public void testCase4263() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        FakeMetadataFacade metadata = example1();
        
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_CORRELATED, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_SCALAR, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
        
        ProcessorPlan plan = helpPlan("select vm1.g1.e1 from vm1.g1 left outer join (select * from vm1.g2 as v where v.e1 = (select max(vm1.g2.e1) from vm1.g2 where v.e1 = vm1.g2.e1)) f2 on (f2.e1 = vm1.g1.e1)", metadata,  //$NON-NLS-1$
                                      null, capFinder,
            new String[] { "SELECT g1__1.e1 FROM pm1.g1 AS g1__1 LEFT OUTER JOIN pm1.g1 AS g1__2 ON g1__2.e1 = g1__1.e1 AND g1__2.e1 = (SELECT MAX(pm1.g1.e1) FROM pm1.g1 WHERE pm1.g1.e1 = g1__2.e1)" }, SHOULD_SUCCEED); //$NON-NLS-1$ 
        checkNodeTypes(plan, FULL_PUSHDOWN); 
          
        checkSubPlanCount(plan, 0);        
    }
    
    public void testCase4263b() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        FakeMetadataFacade metadata = example1();
        
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_CORRELATED, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, false);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
        capFinder.addCapabilities("pm2", caps); //$NON-NLS-1$
        
        ProcessorPlan plan = helpPlan("select vm1.g1.e1 from vm1.g1 left outer join (select * from vm1.g2 as v where v.e1 = (select max(pm2.g1.e1) from pm2.g1 where v.e1 = pm2.g1.e1)) f2 on (f2.e1 = vm1.g1.e1)", metadata,  //$NON-NLS-1$
                                      null, capFinder,
            new String[] { "SELECT pm1.g1.e1 FROM pm1.g1", "SELECT g1__1.e1 FROM pm1.g1 AS g1__1" }, SHOULD_SUCCEED); //$NON-NLS-1$ //$NON-NLS-2$
        checkNodeTypes(plan, new int[] {
            2,      // Access
            0,      // DependentAccess
            1,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // Join
            1,      // MergeJoin
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
          
        checkSubPlanCount(plan, 1);        
    }
    
    public void testCase4279() throws Exception {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        FakeMetadataFacade metadata = example1();
        
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_CORRELATED, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_CASE, true);
        caps.setCapabilitySupport(Capability.QUERY_SEARCHED_CASE, true);
        
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
        
        ProcessorPlan plan = helpPlan("select * from (select v1.e1, v2.e1 as e1_1, v1.e2, v2.e2 as e2_2 from (select * from vm1.g7 where vm1.g7.e2 = 1) v1 left outer join (select * from vm1.g7 where vm1.g7.e2 = 1) v2 on v1.e2 = v2.e2) as v3 where v3.e2 = 1", metadata,  //$NON-NLS-1$
                                      null, capFinder,
            new String[] { "SELECT CASE WHEN g_0.e1 = 'S' THEN 'Pay' WHEN g_0.e1 = 'P' THEN 'Rec' ELSE g_0.e1 END, CASE WHEN g_1.e1 = 'S' THEN 'Pay' WHEN g_1.e1 = 'P' THEN 'Rec' ELSE g_1.e1 END, g_0.e2, g_1.e2 FROM pm1.g1 AS g_0 LEFT OUTER JOIN pm1.g1 AS g_1 ON g_0.e2 = g_1.e2 AND g_1.e2 = 1 WHERE g_0.e2 = 1" }, ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$
        checkNodeTypes(plan, FULL_PUSHDOWN); 
          
        checkSubPlanCount(plan, 0);        
    }
    
    public void testCase4312() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SELECT_EXPRESSION, true);
        caps.setFunctionSupport("+", true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        helpPlan("select ? + 1, pm1.g1.e1 AS EXPR_1 FROM pm1.g1", example1(), null, capFinder, //$NON-NLS-1$
                 new String[] {
                     "SELECT (? + 1) AS expr, pm1.g1.e1 FROM pm1.g1"}, true); //$NON-NLS-1$

    }    
    
    public void testCase2507_2(){

        String sql = "SELECT a FROM (SELECT concat(BQT1.SmallA.StringKey, BQT1.SmallA.StringNum) as a " +  //$NON-NLS-1$
                     "FROM BQT1.SmallA, BQT1.SmallB WHERE SmallA.IntKey = SmallB.IntKey) as X ORDER BY X.a"; //$NON-NLS-1$

        String expected = "SELECT concat(BQT1.SmallA.StringKey, BQT1.SmallA.StringNum) AS EXPR " +  //$NON-NLS-1$
                     "FROM BQT1.SmallA, BQT1.SmallB WHERE BQT1.SmallA.IntKey = BQT1.SmallB.IntKey ORDER BY EXPR";  //$NON-NLS-1$

        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        ProcessorPlan plan = TestOptimizer.helpPlan(sql,         
                                      metadata,
                                      null, capFinder,
                                      new String[] {expected},
                                      TestOptimizer.SHOULD_SUCCEED );

        TestOptimizer.checkNodeTypes(plan, FULL_PUSHDOWN);
    } 
    
    private void helpTestCase2430and2507(String sql, String expected) {
        
        // TEST PLANNING
        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_ORDERED, true);        
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER_FULL, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);  
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        caps.setFunctionSupport("+", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();

        ProcessorPlan plan = TestOptimizer.helpPlan(sql,        
                                      metadata,
                                      null, capFinder,
                                      new String[] {expected},  
                                      TestOptimizer.SHOULD_SUCCEED );

        TestOptimizer.checkNodeTypes(plan, FULL_PUSHDOWN);
    }
    
    //Test use of OrderBy with Alias
    public void testCase2430D() {
        String sql = "SELECT bqt1.smalla.longnum + bqt1.smalla.longnum as c1234567890123456789012345678901234567890, " + //$NON-NLS-1$
                     "bqt1.smalla.doublenum as EXPR FROM bqt1.smalla ORDER BY c1234567890123456789012345678901234567890, EXPR "; //$NON-NLS-1$

        String expected = "SELECT (bqt1.smalla.longnum + bqt1.smalla.longnum) AS c1234567890123456789012345678901234567890, bqt1.smalla.doublenum AS EXPR " + //$NON-NLS-1$
                     "FROM bqt1.smalla ORDER BY c1234567890123456789012345678901234567890, EXPR"; //$NON-NLS-1$
        helpTestCase2430and2507(sql, expected);
    }     

    /*
     * If expressionsymbol comparison would ignore expression names then this should just select a single column,
     * but for now it will select 2.
     */
    public void testCase2430E() {
        String sql = "SELECT CONCAT(bqt1.smalla.stringKey, bqt1.smalla.stringNum) as c1234567890123456789012345678901234567890, " + //$NON-NLS-1$
                     "CONCAT(bqt1.smalla.stringKey, bqt1.smalla.stringNum) AS EXPR FROM bqt1.smalla ORDER BY c1234567890123456789012345678901234567890, EXPR "; //$NON-NLS-1$

        String expected = "SELECT CONCAT(bqt1.smalla.stringKey, bqt1.smalla.stringNum) AS c1234567890123456789012345678901234567890, CONCAT(bqt1.smalla.stringKey, bqt1.smalla.stringNum) " + //$NON-NLS-1$
                     "FROM bqt1.smalla ORDER BY c1234567890123456789012345678901234567890"; //$NON-NLS-1$
        helpTestCase2430and2507(sql, expected);
    }     
    
    public void testCase2430G() {
        String sql = "SELECT CONCAT(bqt1.smalla.stringKey, bqt1.smalla.stringNum) as c1234567890123456789012345678901234567890, " + //$NON-NLS-1$
                     "CONCAT(bqt1.smalla.stringKey, bqt1.smalla.stringNum) AS EXPR FROM bqt1.smalla ORDER BY c1234567890123456789012345678901234567890"; //$NON-NLS-1$

        String expected = "SELECT CONCAT(bqt1.smalla.stringKey, bqt1.smalla.stringNum) AS c1234567890123456789012345678901234567890, CONCAT(bqt1.smalla.stringKey, bqt1.smalla.stringNum) " + //$NON-NLS-1$
                     "FROM bqt1.smalla ORDER BY c1234567890123456789012345678901234567890"; //$NON-NLS-1$
        helpTestCase2430and2507(sql, expected);
    }  
    
    public void testCase2507_1(){

        String sql = "SELECT a FROM (SELECT concat(BQT1.SmallA.StringKey, BQT1.SmallA.StringNum) as a " +  //$NON-NLS-1$
                     "FROM BQT1.SmallA) as X ORDER BY X.a"; //$NON-NLS-1$

        String expected = "SELECT concat(BQT1.SmallA.StringKey, BQT1.SmallA.StringNum) AS EXPR FROM BQT1.SmallA ORDER BY EXPR";  //$NON-NLS-1$

        // Plan query
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setFunctionSupport("concat", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();

        ProcessorPlan plan = TestOptimizer.helpPlan(sql,         
                                      metadata,
                                      null, capFinder,
                                      new String[] {expected},
                                      TestOptimizer.SHOULD_SUCCEED );

        TestOptimizer.checkNodeTypes(plan, FULL_PUSHDOWN);        
    }       
    
    /**
     * This is taken from testPushCorrelatedSubquery1.  However this subquery is not expected to be pushed down since the correlated
     * reference expression cannot be evaluated by the source.
     */
    public void testDefect23614() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setCapabilitySupport(Capability.CRITERIA_QUANTIFIED_ALL, true);
        caps.setCapabilitySupport(Capability.CRITERIA_QUANTIFIED_SOME, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN, true);
        caps.setCapabilitySupport(Capability.CRITERIA_IN_SUBQUERY, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_SCALAR, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_CORRELATED, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        ProcessorPlan plan = helpPlan("SELECT intkey FROM bqt1.smalla AS n WHERE intkey = (SELECT MAX(intkey) FROM bqt1.smallb AS s WHERE s.stringkey = concat(n.stringkey, 'a') )", FakeMetadataFactory.exampleBQTCached(),  //$NON-NLS-1$
            null, capFinder,
            new String[] { "SELECT intkey, n.stringkey FROM bqt1.smalla AS n" }, SHOULD_SUCCEED); //$NON-NLS-1$ 
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            1,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
        checkSubPlanCount(plan, 1);
    }   
    
    /**
     * Normally the following queries would plan as if they were federated, but setting the connector_id source property
     * allows them to be planned as if they were the same source. 
     */
    public void testSameConnector() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_OUTER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setSourceProperty(Capability.CONNECTOR_ID, "1"); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        capFinder.addCapabilities("BQT2", caps); //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        ProcessorPlan plan = helpPlan(
            "SELECT A.IntKey, B.IntKey FROM BQT1.SmallA A LEFT OUTER JOIN BQT2.MediumB B ON A.IntKey = B.IntKey",  //$NON-NLS-1$
            metadata, null, capFinder,
            new String[] { 
              "SELECT A.IntKey, B.IntKey FROM BQT1.SmallA AS A LEFT OUTER JOIN BQT2.MediumB AS B ON A.IntKey = B.IntKey"},  //$NON-NLS-1$
              true); 
                
        checkNodeTypes(plan, FULL_PUSHDOWN);
        
        plan = helpPlan(
              "SELECT A.IntKey FROM BQT1.SmallA A UNION select B.intkey from BQT2.MediumB B",  //$NON-NLS-1$
              metadata, null, capFinder,
              new String[] { 
                "SELECT A.IntKey FROM BQT1.SmallA AS A UNION SELECT B.intkey FROM BQT2.MediumB AS B"},  //$NON-NLS-1$
                true); 
                  
        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }
        
    /**
     * Test changes to RuleCollapseSource for removing aliases 
     */
    public void testCase4898() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setCapabilitySupport(Capability.QUERY_SELECT_EXPRESSION, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        ProcessorPlan plan = helpPlan(
              "SELECT 'a' as A FROM BQT1.SmallA A UNION select 'b' as B from BQT1.MediumB B",  //$NON-NLS-1$
              metadata, null, capFinder,
              new String[] { 
                "SELECT 'a' AS A FROM BQT1.SmallA AS A UNION SELECT 'b' FROM BQT1.MediumB AS B"},  //$NON-NLS-1$
                true); 
                  
        checkNodeTypes(plan, FULL_PUSHDOWN);                                    
    }
    
    public void testDefect13971() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SELECT_DISTINCT, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        String sql = "select b from (select distinct booleanvalue b, intkey from bqt1.smalla) as x"; //$NON-NLS-1$
        
        // Plan query
        ProcessorPlan plan = helpPlan(sql, metadata, null, capFinder, new String[] {"SELECT DISTINCT booleanvalue, intkey FROM bqt1.smalla"}, SHOULD_SUCCEED); //$NON-NLS-1$

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        });
    }
    
    /**
     * Ensures that aliases are not stripped from projected symbols if they might conflict with an order by element
     */
    public void testCase5067() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
        
        String sql = "SELECT a.intkey as stringkey, b.stringkey as key2 from bqt1.smalla a, bqt1.smallb b where a.intkey = b.intkey order by stringkey"; //$NON-NLS-1$ 
         
        // Plan query 
        ProcessorPlan plan = helpPlan(sql, metadata, null, capFinder, new String[] {"SELECT a.intkey AS stringkey, b.stringkey AS key2 FROM bqt1.smalla AS a, bqt1.smallb AS b WHERE a.intkey = b.intkey ORDER BY stringkey"}, TestOptimizer.SHOULD_SUCCEED); //$NON-NLS-1$ 
 
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }
        
    public void testDontPushConvertObject() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT intkey from bqt1.smalla WHERE stringkey = convert(objectvalue, string)",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT stringkey, objectvalue, intkey FROM bqt1.smalla"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }
    
    public void testDontPushConvertClobToString() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = new BasicSourceCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_COMPARE_EQ, true);
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$
        capFinder.addCapabilities("LOB", caps); //$NON-NLS-1$

        // Add join capability to pm1
        FakeMetadataFacade metadata = FakeMetadataFactory.exampleBQTCached();
         
        ProcessorPlan plan = helpPlan(
            "SELECT ClobValue from LOB.LobTbl WHERE convert(ClobValue, string) = ?",  //$NON-NLS-1$
            metadata,
            null, capFinder,
            new String[] {"SELECT ClobValue FROM LOB.LobTbl"}, //$NON-NLS-1$
            SHOULD_SUCCEED );

        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        });                                    
    }
    
    public void testSelectIntoWithDistinct() throws Exception {
        String sql = "select distinct e1 into #temp from pm1.g1"; //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
        
        ProcessorPlan plan = helpPlan(sql, metadata, new String[] {"SELECT DISTINCT g_0.e1 FROM pm1.g1 AS g_0"}, ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$
        
        checkNodeTypes(plan, FULL_PUSHDOWN); 
        
        checkNodeTypes(plan, new int[] {1}, new Class[] {ProjectIntoNode.class});
    }
    
    /**
     * previously the subqueries were being pushed too far and then not having the appropriate correlated references
     */
    public void testCorrelatedSubqueryOverJoin() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_INNER, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_SUBQUERIES_CORRELATED, true);
        caps.setCapabilitySupport(Capability.CRITERIA_EXISTS, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
        
        String sql = "select pm1.g1.e1 from pm1.g1, (select * from pm1.g2) y where (pm1.g1.e1 = y.e1) and exists (select e2 from pm1.g2 where e1 = y.e1) and exists (select e3 from pm1.g2 where e1 = y.e1)"; //$NON-NLS-1$
        
        ProcessorPlan plan = helpPlan(sql, metadata, null, capFinder, new String[] {"SELECT pm1.g1.e1 FROM pm1.g1, pm1.g2 AS g2__1 WHERE (pm1.g1.e1 = g2__1.e1) AND (EXISTS (SELECT e2 FROM pm1.g2 WHERE e1 = g2__1.e1)) AND (EXISTS (SELECT e3 FROM pm1.g2 WHERE e1 = g2__1.e1))"}, TestOptimizer.SHOULD_SUCCEED); //$NON-NLS-1$ 
        
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }
    
    /**
     * see testSimpleCrossJoin3
     */
    public void testMaxFromGroups() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setSourceProperty(Capability.MAX_QUERY_FROM_GROUPS, new Integer(1));
        capFinder.addCapabilities("pm2", caps); //$NON-NLS-1$
        
        helpPlan("select pm2.g1.e1 FROM pm2.g1 CROSS JOIN pm2.g2", example1(), null, capFinder, //$NON-NLS-1$
            new String[] { "SELECT pm2.g1.e1 FROM pm2.g1", "SELECT pm2.g2.e1 FROM pm2.g2"}, true ); //$NON-NLS-1$ //$NON-NLS-2$
               
    }
    
    public void testCase6249() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_COUNT_STAR, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_JOIN_SELFJOIN, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_GROUP_ALIAS, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_INLINE_VIEWS, true);
        caps.setCapabilitySupport(Capability.QUERY_FUNCTIONS_IN_GROUP_BY, true);
        caps.setCapabilitySupport(Capability.QUERY_UNION, true);
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        capFinder.addCapabilities("BQT2", TestOptimizer.getTypicalCapabilities()); //$NON-NLS-1$
        
        String sql = "select count(*) from (select intkey from bqt1.smalla union all select intkey from bqt1.smallb) as a"; //$NON-NLS-1$
        
        ProcessorPlan plan = helpPlan(sql, FakeMetadataFactory.exampleBQT(), null, capFinder, 
                                      new String[] {"SELECT COUNT(*) FROM (SELECT intkey FROM bqt1.smalla UNION ALL SELECT intkey FROM bqt1.smallb) AS a"}, TestOptimizer.SHOULD_SUCCEED); //$NON-NLS-1$ 
        
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }
        
    public void testCase6181() throws Exception {
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
        
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_MAX, true);
        caps.setCapabilitySupport(Capability.QUERY_FUNCTIONS_IN_GROUP_BY, true);
        caps.setCapabilitySupport(Capability.QUERY_CASE, true);
        caps.setCapabilitySupport(Capability.QUERY_SEARCHED_CASE, true);
        caps.setCapabilitySupport(Capability.QUERY_FROM_INLINE_VIEWS, true);
        caps.setCapabilitySupport(Capability.QUERY_SELECT_EXPRESSION, true);
        caps.setCapabilitySupport(Capability.QUERY_ORDERBY, true);
        caps.setFunctionSupport("convert", true); //$NON-NLS-1$
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
        
        String sql = "select a.e1 from (select 1 e1) a, (select e1, 1 as a, x from (select e1, CASE WHEN e1 = 'a' THEN e2 ELSE e3 END as x from pm1.g2) y group by e1, x) b where a.e1 = b.x"; //$NON-NLS-1$
        
        ProcessorPlan plan = helpPlan(sql, metadata, null, capFinder, 
                                      new String[] {"SELECT v_1.c_0 FROM (SELECT v_0.c_1 AS c_0 FROM (SELECT g_0.e1 AS c_0, CASE WHEN g_0.e1 = 'a' THEN g_0.e2 ELSE g_0.e3 END AS c_1 FROM pm1.g2 AS g_0 WHERE CASE WHEN g_0.e1 = 'a' THEN g_0.e2 ELSE g_0.e3 END IN (<dependent values>)) AS v_0 GROUP BY v_0.c_0, v_0.c_1) AS v_1 ORDER BY c_0"}, ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$ 
        
        checkNodeTypes(plan, new int[] {
            0,      // Access
            1,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            1,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            2,      // Project
            0,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }
    
    public void testCase6325() {
        String sql = "select e1 into #temp from pm4.g1 where e1='1'"; //$NON-NLS-1$
        
        FakeMetadataFacade metadata = FakeMetadataFactory.example1Cached();
        
        ProcessorPlan plan = helpPlan(sql, metadata, new String[] {"SELECT e1 FROM pm4.g1 WHERE e1 = '1'"}); //$NON-NLS-1$
        
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }
    
    public void testAliasCreationWithInlineView() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        FakeMetadataFacade metadata = createInlineViewMetadata(capFinder);
        
        ProcessorPlan plan = helpPlan("select a, b from (select distinct count(intNum) a, count(stringKey), bqt1.smalla.intkey as b from bqt1.smalla group by bqt1.smalla.intkey) q1 order by q1.a", //$NON-NLS-1$
                metadata, null, capFinder, new String[] {"SELECT a, b FROM (SELECT DISTINCT COUNT(intNum) AS a, COUNT(stringKey) AS count1, bqt1.smalla.intkey AS b FROM bqt1.smalla GROUP BY bqt1.smalla.intkey) AS q1 ORDER BY a"}, true); //$NON-NLS-1$

        checkNodeTypes(plan, FULL_PUSHDOWN);    
            
        checkSubPlanCount(plan, 0);
    }
    
    public void testCase6364() {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_FUNCTIONS_IN_GROUP_BY, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_SUM, true);
        caps.setFunctionSupport("+", true); //$NON-NLS-1$
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$
        
        String sql = "select * from (SELECT 1+ SUM(intnum) AS s FROM bqt1.smalla) a WHERE a.s>10"; //$NON-NLS-1$
       
        ProcessorPlan plan = helpPlan(sql, FakeMetadataFactory.exampleBQTCached(), null, capFinder, 
                                      new String[] {"SELECT (1 + SUM(intnum)) FROM bqt1.smalla HAVING SUM(intnum) > 9"}, TestOptimizer.SHOULD_SUCCEED); //$NON-NLS-1$ 
        
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }
    
    public void testExceptPushdown() throws Exception {
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_EXCEPT, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$
        
        String sql = "select e1 from pm1.g1 except select e1 from pm1.g2"; //$NON-NLS-1$
       
        ProcessorPlan plan = helpPlan(sql, FakeMetadataFactory.example1Cached(), null, capFinder, 
                                      new String[] {"SELECT g_1.e1 AS c_0 FROM pm1.g1 AS g_1 EXCEPT SELECT g_0.e1 AS c_0 FROM pm1.g2 AS g_0"}, TestOptimizer.ComparisonMode.EXACT_COMMAND_STRING); //$NON-NLS-1$ 
        
        checkNodeTypes(plan, FULL_PUSHDOWN); 
    }    
   
    public void testCase6597() { 
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.CRITERIA_LIKE, true);
        caps.setCapabilitySupport(Capability.CRITERIA_NOT, false);
        capFinder.addCapabilities("BQT1", caps); //$NON-NLS-1$

        // Create query 
        String sql = "select IntKey from bqt1.smalla where stringkey not like '2%'"; //$NON-NLS-1$

        ProcessorPlan plan = helpPlan(sql, FakeMetadataFactory.exampleBQTCached(), null, capFinder, 
                                      new String[] {"SELECT stringkey, IntKey FROM bqt1.smalla"}, TestOptimizer.SHOULD_SUCCEED); //$NON-NLS-1$
        
        checkNodeTypes(plan, new int[] {
            1,      // Access
            0,      // DependentAccess
            0,      // DependentSelect
            0,      // DependentProject
            0,      // DupRemove
            0,      // Grouping
            0,      // NestedLoopJoinStrategy
            0,      // MergeJoinStrategy
            0,      // Null
            0,      // PlanExecution
            1,      // Project
            1,      // Select
            0,      // Sort
            0       // UnionAll
        }); 
    }
    
    public void testCopyCriteriaWithIsNull() {
    	String sql = "select * from (select a.intnum, a.intkey y, b.intkey from bqt1.smalla a, bqt2.smalla b where a.intkey = b.intkey) x where intkey is null"; //$NON-NLS-1$
    	
    	helpPlan(sql, FakeMetadataFactory.exampleBQT(), new String[] {});
    }
    
    /**
     * Test <code>QueryOptimizer</code>'s ability to plan a fully-pushed-down 
     * query containing a <code>BETWEEN</code> comparison in the queries 
     * <code>WHERE</code> statement.
     * <p>
     * For example:
     * <p>
     * SELECT * FROM pm1.g1 WHERE e2 BETWEEN 1 AND 2
     */
    public void testBetween() { 
        helpPlan("select * from pm1.g1 where e2 between 1 and 2", FakeMetadataFactory.example1Cached(), //$NON-NLS-1$
    			new String[] { "SELECT pm1.g1.e1, pm1.g1.e2, pm1.g1.e3, pm1.g1.e4 FROM pm1.g1 WHERE (e2 >= 1) AND (e2 <= 2)"} ); //$NON-NLS-1$
    }

    /**
     * Test <code>QueryOptimizer</code>'s ability to plan a fully-pushed-down 
     * query containing a <code>CASE</code> expression in which a 
     * <code>BETWEEN</code> comparison is used in the queries 
     * <code>SELECT</code> statement.
     * <p>
     * For example:
     * <p>
     * SELECT CASE WHEN e2 BETWEEN 3 AND 5 THEN e2 ELSE -1 END FROM pm1.g1
     */
    public void testBetweenInCase() { 
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SEARCHED_CASE, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        helpPlan("select case when e2 between 3 and 5 then e2 else -1 end from pm1.g1", //$NON-NLS-1$ 
        		FakeMetadataFactory.example1Cached(), null, capFinder, 
    			new String[] { "SELECT CASE WHEN (e2 >= 3) AND (e2 <= 5) THEN e2 ELSE -1 END FROM pm1.g1"},  //$NON-NLS-1$
    			TestOptimizer.SHOULD_SUCCEED);
    }

    /**
     * Test <code>QueryOptimizer</code>'s ability to plan a fully-pushed-down 
     * query containing an aggregate SUM with a <code>CASE</code> expression 
     * in which a <code>BETWEEN</code> comparison is used in the queries 
     * <code>SELECT</code> statement.
     * <p>
     * For example:
     * <p>
     * SELECT SUM(CASE WHEN e2 BETWEEN 3 AND 5 THEN e2 ELSE -1 END) FROM pm1.g1
     */
    public void testBetweenInCaseInSum() { 
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SEARCHED_CASE, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_SUM, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        helpPlan("select sum(case when e2 between 3 and 5 then e2 else -1 end) from pm1.g1", //$NON-NLS-1$ 
        		FakeMetadataFactory.example1Cached(), null, capFinder, 
    			new String[] { "SELECT SUM(CASE WHEN (e2 >= 3) AND (e2 <= 5) THEN e2 ELSE -1 END) FROM pm1.g1"},  //$NON-NLS-1$
    			TestOptimizer.SHOULD_SUCCEED);
    }

    /**
     * Test <code>QueryOptimizer</code>'s ability to plan a fully-pushed-down 
     * query containing an aggregate SUM with a <code>CASE</code> expression 
     * in which a <code>BETWEEN</code> comparison is used in the queries 
     * <code>SELECT</code> statement and a GROUP BY is specified.
     * <p>
     * For example:
     * <p>
     * SELECT e1, SUM(CASE WHEN e2 BETWEEN 3 AND 5 THEN e2 ELSE -1 END) 
     * FROM pm1.g1 GROUP BY e1
     */
    public void testBetweenInCaseInSumWithGroupBy() { 
        FakeCapabilitiesFinder capFinder = new FakeCapabilitiesFinder();
        BasicSourceCapabilities caps = TestOptimizer.getTypicalCapabilities();
        caps.setCapabilitySupport(Capability.QUERY_SEARCHED_CASE, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES, true);
        caps.setCapabilitySupport(Capability.QUERY_AGGREGATES_SUM, true);
        capFinder.addCapabilities("pm1", caps); //$NON-NLS-1$

        helpPlan("select sum(case when e2 between 3 and 5 then e2 else -1 end) from pm1.g1 group by e1", //$NON-NLS-1$ 
        		FakeMetadataFactory.example1Cached(), null, capFinder, 
    			new String[] { "SELECT SUM(CASE WHEN (e2 >= 3) AND (e2 <= 5) THEN e2 ELSE -1 END) FROM pm1.g1 GROUP BY e1"},  //$NON-NLS-1$
    			TestOptimizer.SHOULD_SUCCEED);
    }

	private static final boolean DEBUG = false;

}
