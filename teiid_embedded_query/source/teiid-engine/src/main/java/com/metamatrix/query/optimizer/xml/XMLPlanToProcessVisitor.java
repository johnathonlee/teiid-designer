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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.metamatrix.query.mapping.xml.InterceptingVisitor;
import com.metamatrix.query.mapping.xml.MappingAllNode;
import com.metamatrix.query.mapping.xml.MappingAttribute;
import com.metamatrix.query.mapping.xml.MappingBaseNode;
import com.metamatrix.query.mapping.xml.MappingChoiceNode;
import com.metamatrix.query.mapping.xml.MappingCommentNode;
import com.metamatrix.query.mapping.xml.MappingCriteriaNode;
import com.metamatrix.query.mapping.xml.MappingDocument;
import com.metamatrix.query.mapping.xml.MappingElement;
import com.metamatrix.query.mapping.xml.MappingInterceptor;
import com.metamatrix.query.mapping.xml.MappingRecursiveElement;
import com.metamatrix.query.mapping.xml.MappingSequenceNode;
import com.metamatrix.query.mapping.xml.MappingSourceNode;
import com.metamatrix.query.mapping.xml.ResultSetInfo;
import com.metamatrix.query.processor.xml.AbortProcessingInstruction;
import com.metamatrix.query.processor.xml.AddNodeInstruction;
import com.metamatrix.query.processor.xml.BlockInstruction;
import com.metamatrix.query.processor.xml.Condition;
import com.metamatrix.query.processor.xml.CriteriaCondition;
import com.metamatrix.query.processor.xml.DefaultCondition;
import com.metamatrix.query.processor.xml.EndBlockInstruction;
import com.metamatrix.query.processor.xml.EndDocumentInstruction;
import com.metamatrix.query.processor.xml.ExecSqlInstruction;
import com.metamatrix.query.processor.xml.IfInstruction;
import com.metamatrix.query.processor.xml.InitializeDocumentInstruction;
import com.metamatrix.query.processor.xml.JoinedWhileInstruction;
import com.metamatrix.query.processor.xml.ExecStagingTableInstruction;
import com.metamatrix.query.processor.xml.MoveCursorInstruction;
import com.metamatrix.query.processor.xml.MoveDocInstruction;
import com.metamatrix.query.processor.xml.ProcessorInstruction;
import com.metamatrix.query.processor.xml.Program;
import com.metamatrix.query.processor.xml.RecurseProgramCondition;
import com.metamatrix.query.processor.xml.WhileInstruction;


/** 
 * This class converts the MappingDocument to a Program which can be executed using the 
 * Query Processor
 */
public class XMLPlanToProcessVisitor implements MappingInterceptor {
    
    Stack programStack = new Stack(); 
    XMLPlannerEnvironment planEnv;
    Program originalProgram ;
    Program cleanupProgram  = new Program();

    public XMLPlanToProcessVisitor(XMLPlannerEnvironment env) {
        this.planEnv = env;
    }

    public void start(MappingDocument doc, Map context) {
        Program currentProgram = new Program();
        this.programStack.push(currentProgram);
    }
    
    public void end(MappingDocument doc, Map context) {
        // remove the current program from the stack; we no longer need this
        originalProgram=(Program)this.programStack.pop();
        
        // cleanup program will have instructions to unload the staging table.
        originalProgram.addInstructions(cleanupProgram);
    }
        
    public void start(MappingAttribute attribute, Map context){
        Program currentProgram = (Program)this.programStack.peek();
        ProcessorInstruction tagInst = TagBuilderVisitor.buildTag(attribute);
        if (tagInst != null) {
            currentProgram.addInstruction(tagInst);
        }        
    }

    public void end(MappingAttribute attribute, Map context){
    }
    
    public void start(MappingCommentNode comment, Map context){
        Program currentProgram = (Program)this.programStack.peek();
        ProcessorInstruction tagInst = TagBuilderVisitor.buildTag(comment);
        if (tagInst != null) {
            currentProgram.addInstruction(tagInst);
        }
    }
    
    public void end(MappingCommentNode comment, Map context){
        // nothing to do
    }
    
    public void start(MappingAllNode all, Map context){
        commonStart(all, context);
    }
    
    public void end(MappingAllNode all, Map context){
        commonEnd(all, context);
    }
    
    public void start(MappingChoiceNode choice, Map context){        
        IfInstruction ifInst = new IfInstruction();        
        // if an exception should be thrown as the default choice, then add a sub program to that
        if (choice.throwExceptionOnDefault()) {
            Program subProgram = new Program();
            subProgram.addInstruction(new AbortProcessingInstruction());
            DefaultCondition defCondition = new DefaultCondition(subProgram);
            ifInst.setDefaultCondition(defCondition);            
        }
        
        // to be used by the criteria nodes.
        context.put(choice, ifInst);        

        // process the node as others (if see we have not done anything for this node yet..)
        commonStart(choice, context);

        Program currentProgram = (Program)this.programStack.peek();        
        currentProgram.addInstruction(ifInst);        
    }
    
    public void end(MappingChoiceNode choice, Map context){
        commonEnd(choice, context);

        // what we put in must go..
        context.remove(choice);
    }
    
    public void start(MappingCriteriaNode node, Map context){

        // every criteria node has its own program..
        Program childProgram = new Program();
        IfInstruction ifInst = (IfInstruction)context.get(node.getParentNode());
        
        if (node.getCriteria() != null) {
            Condition condition = new CriteriaCondition(node.getCriteriaNode(), childProgram);
            ifInst.addCondition(condition);            
        }
        
        if (node.isDefault()) {
            DefaultCondition defCondition = new DefaultCondition(childProgram);
            ifInst.setDefaultCondition(defCondition);            
        }
        
        // now push the child program
        this.programStack.push(childProgram);
        
        // now call code for the common node element
        commonStart(node, context);
    }
            
    public void end(MappingCriteriaNode element, Map context){
                
        // do the common end..
        commonEnd(element, context);
        
        // pop the child program created in the begin of the node.
        this.programStack.pop();                
    }
        
    public void start(MappingSequenceNode sequence, Map context){
        commonStart(sequence, context);
    }
    
    public void end(MappingSequenceNode sequence, Map context){   
        commonEnd(sequence, context);
    }    
    
    private void startRootRecursive(MappingBaseNode node, Map context) {
        Program childProgram = new Program();
        context.put(node.getRecursionId(), childProgram);
        this.programStack.push(childProgram);        
    }

    private void endRootRecursive(MappingBaseNode node, Map context) {
        // add the recursive program to the main program.
        Program recursiveProgram = (Program)programStack.pop();
        
        // this is the main program
        Program currentProgram = (Program)this.programStack.peek();
        currentProgram.addInstructions(recursiveProgram);
        context.remove(node.getRecursionId());
        
        // defect 17575; In case of recursive node the top most element
        // name could be different from original; so take it it out, so that
        // recursive node can put what ever it needs as head.
        ProcessorInstruction firstInst = recursiveProgram.getInstructionAt(0);
        if (firstInst instanceof AddNodeInstruction) {
            recursiveProgram.removeInstructionAt(0);
        }         
    }
    
    public void start(MappingElement element, Map context){
        //commonStart(element, context);
        Program currentProgram = (Program)programStack.peek();
        
        // if we are dealing with multiple documents
        startFragment(currentProgram, element);
        
        ProcessorInstruction tagInst = TagBuilderVisitor.buildTag(element);
        currentProgram.addInstruction(tagInst);
        
        commonStart(element, context);
        
        // If there are more children under this node move the cursor down
        if (!element.getChildren().isEmpty()) {
            // update the program pointer 
            currentProgram = (Program)programStack.peek();
            currentProgram.addInstruction(new MoveDocInstruction(MoveDocInstruction.DOWN));
        }
    }

    public void end(MappingElement element, Map context){
        Program currentProgram = (Program)this.programStack.peek();
        
        // If there were more children under this node move the cursor up        
        if (!element.getChildren().isEmpty()) {
            currentProgram.addInstruction(new MoveDocInstruction(MoveDocInstruction.UP));
        }
        
        commonEnd(element, context);
        
        // update the program pointer
        currentProgram = (Program)programStack.peek();            
        
        // if we are dealing with multiple documents                        
        endFragment(currentProgram, element);        
    }
   
    public void start(MappingSourceNode node, Map context) {
        Program currentProgram = (Program)programStack.peek();
        
        commonStart(node, context);
        
        String source = node.getActualResultSetName();
        ResultSetInfo info= node.getResultSetInfo();
        
        if (info.isJoinedWithParent()) {
            //create a dependent while loop
            JoinedWhileInstruction whileInst = new JoinedWhileInstruction(source, new Integer(info.getMappingClassNumber()),
                                                                          info.getMappingClassSymbol(), node.getResultName());
            currentProgram.addInstruction(whileInst);
            
            Program childProgram = new Program();
            whileInst.setBlockProgram(childProgram);
            
            programStack.push(childProgram);
            return;
        }
        
        // Add instruction to execute relational query
        ExecSqlInstruction sqlInst = new ExecSqlInstruction(source, info);
        currentProgram.addInstruction(sqlInst);
        
        BlockInstruction blockInst = new BlockInstruction(source);
        currentProgram.addInstruction(blockInst);
        
        // Add instruction to read the first row in
        MoveCursorInstruction moveCursor = new MoveCursorInstruction(source);
        currentProgram.addInstruction(moveCursor);

        // set up while instruction, add sub Program
        WhileInstruction whileInst = new WhileInstruction(source);
        currentProgram.addInstruction(whileInst);
        
        Program childProgram = new Program();
        whileInst.setBlockProgram(childProgram);

        // when while is done close the resultset(note that the child program will
        // have all the statements. 
        EndBlockInstruction closeInst = new EndBlockInstruction(source);
        currentProgram.addInstruction(closeInst);        

        // push the new child program on to stack so that all the children use this
        // program; this must be at end of this block, as the once we exit here we 
        // are going to loop for children with new program
        programStack.push(childProgram);
    }
    
    public void end(MappingSourceNode node, Map context) {
        Program currentProgram = (Program)programStack.peek();        
        
        String source = node.getActualResultSetName();  
        ResultSetInfo info= node.getResultSetInfo();
        
        if (!info.isJoinRoot()) {
            // move to next row.
            currentProgram.addInstruction(new MoveCursorInstruction(source));
        }

        // Since each element with a source started a new program; 
        // since now we are done with children, we need to pop to current program                                    
        this.programStack.pop();
        
        commonEnd(node, context);
    }
            
    private void startFragment(Program program, MappingBaseNode element) {
        // In the case that we are returning the multiple documents; we are going to treat them as 
        // fragments, so do not add the header information
        if (element.isTagRoot()) {
            MappingDocument doc = element.getDocument();
            ProcessorInstruction header = new InitializeDocumentInstruction(doc.getDocumentEncoding(), doc.isFormatted());
            program.addInstruction(header);
        }
    }
  
    private void endFragment(Program program, MappingBaseNode element) {
        // In the case that we are returning the multiple documents; we are going to treat them as 
        // fragments, so do not add the header information        
        if (element.isTagRoot()) {
            program.addInstruction(new EndDocumentInstruction());
        }
    }

    private void commonStart(MappingBaseNode node, Map context) {
        // if this node is root of some recursive node then do below
        if (node.isRootRecursiveNode()) {
            // start recording the program.
            startRootRecursive(node, context);
        }
        
        List stagingTables = node.getStagingTables();
        for (final Iterator i = stagingTables.iterator(); i.hasNext();) {
            final String table = (String)i.next();
            Program currentProgram = (Program)programStack.peek();

            // load staging
            currentProgram.addInstruction(new ExecStagingTableInstruction(table, planEnv.getStagingTableResultsInfo(table)));
            
            // unload sttaging
            String unloadName = planEnv.unLoadResultName(table);
            cleanupProgram.addInstruction(new ExecStagingTableInstruction(unloadName, planEnv.getStagingTableResultsInfo(unloadName)));
        } // for
    }
    
    private void commonEnd(MappingBaseNode node, Map context) {
        if (node.isRootRecursiveNode()) {
            // stop recording and update the program
            endRootRecursive(node, context);            
        }
    }
    
    public void start(final MappingRecursiveElement element, Map context){
        Program currentProgram = (Program)programStack.peek();
        
        // if we are dealing with multiple documents
        startFragment(currentProgram, element);
        
        ProcessorInstruction tagInst = TagBuilderVisitor.buildTag(element);
        currentProgram.addInstruction(tagInst);
                
        // this is set by root recursive node. Note that the MappingClass on recursive
        // node is same as the source on the root recursive node.
        Program recursiveProgram = (Program)context.get(element.getMappingClass().toUpperCase());
        IfInstruction ifInst = new IfInstruction();
        RecurseProgramCondition recurseCondition = buildRecurseCondition(element, recursiveProgram);
        ifInst.addCondition(recurseCondition);
        ifInst.setDefaultCondition(new DefaultCondition(new Program()));
        
        currentProgram.addInstruction(ifInst);
    }   
    
    public void end(final MappingRecursiveElement element, Map context){
        Program currentProgram = (Program)programStack.peek();        

        // if we are dealing with multiple documents                        
        endFragment(currentProgram, element);                
    } 
    
    private static RecurseProgramCondition buildRecurseCondition(MappingRecursiveElement element, Program subProgram) {
        String criteriaString = element.getCriteria();
        RecurseProgramCondition condition = null;
        
        if (criteriaString == null || criteriaString.trim().length() == 0){
            condition = new RecurseProgramCondition(subProgram, null, element.getRecursionLimit(), element.throwExceptionOnRecurrsionLimit());
        } 
        else {
            condition = new RecurseProgramCondition(subProgram, element.getCriteriaNode(), element.getRecursionLimit(), element.throwExceptionOnRecurrsionLimit());
        }
        return condition;
    }    

    public static Program planProgram(MappingDocument doc, XMLPlannerEnvironment env) {
        XMLPlanToProcessVisitor visitor = new XMLPlanToProcessVisitor(env);
        doc.acceptVisitor(new InterceptingVisitor(visitor));
        return visitor.originalProgram;
    }
}
