/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.teiid.designer.transformation.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.eclipse.emf.ecore.EObject;
import org.teiid.core.designer.id.ObjectID;
import org.teiid.designer.core.ModelerCore;
import org.teiid.designer.core.metamodel.aspect.AspectManager;
import org.teiid.designer.core.metamodel.aspect.sql.SqlAspect;
import org.teiid.designer.query.AbstractLanguageVisitor;
import org.teiid.designer.query.IQueryService;
import org.teiid.designer.query.sql.lang.ILanguageObject;
import org.teiid.designer.query.sql.symbol.IElementSymbol;
import org.teiid.designer.query.sql.symbol.IGroupSymbol;
import org.teiid.designer.query.sql.symbol.ISymbol;


/**
 * This visitor update the language objects in the Command being visited by replacing
 * the names of language objects which point to the old EObjects in the map with
 * the names of the corresponding new objects.   
 * @since 8.0
 */
public class UpdateLanguageObjectNameVisitor extends AbstractLanguageVisitor {

    private final Map oldToNewObjects;
    private Map oldToNewNames = new HashMap();    

    /** 
     * UpdateLanguageObjectNameVisitor
     * 
     * @param oldToNewObjects Map of old EObjects to the new EObjects, references to old EObjects
     * in the query need to be replaced with new EObjects
     *
     */
    public UpdateLanguageObjectNameVisitor(final Map oldToNewObjects) {
        this.oldToNewObjects = oldToNewObjects;
    }

    /** 
     * @see IElementSymbol
     * @since 4.2
     */
    @Override
    public void visit(ILanguageObject obj) {

        if (obj instanceof IElementSymbol) {
            IElementSymbol elementSymbol = (IElementSymbol) obj;
            String fullName = elementSymbol.getShortName();
            if (elementSymbol.getGroupSymbol() != null) {
                fullName = elementSymbol.getGroupSymbol().getDefinition() + ISymbol.SEPARATOR + fullName;
                visit(elementSymbol.getGroupSymbol());
            }

            String newName = getNewName(fullName);
            if (newName != null) {
                IQueryService queryService = ModelerCore.getTeiidQueryService();
                elementSymbol.setShortName(queryService.getSymbolShortName(newName));
            }
        } 
        else if (obj instanceof IGroupSymbol) {
            IGroupSymbol groupSymbol = (IGroupSymbol) obj;
            String fullName = groupSymbol.getDefinition();

            String newName = getNewName(fullName);
            if (newName != null) {
                if (groupSymbol.getDefinition() == null) {
                    groupSymbol.setName(newName);
                } else {
                    groupSymbol.setDefinition(newName);
                }
            }
        }
    }
    
    /**
     * @param fullName
     * @return
     */
    private String getNewName( String fullName ) {
        // if the names map is not populated, navigate throught the eObjects and populate it
        // with names and uuids
        if(this.oldToNewNames.isEmpty()) {
            // for each map entry
		    for(final Iterator objIter = this.oldToNewObjects.entrySet().iterator(); objIter.hasNext();) {
		        Map.Entry mapEntry = (Map.Entry) objIter.next();
		        Object oldObj = mapEntry.getKey();
		        Object newObj = mapEntry.getValue();
		        if(oldObj != null && newObj != null && oldObj instanceof EObject && newObj instanceof EObject) {
		            EObject oldEobject = (EObject) oldObj;
		            EObject newEobject = (EObject) newObj;
		            SqlAspect sqlAspect = AspectManager.getSqlAspect(oldEobject);
		            // if it has a sql aspect (only then it has a name and can be used in sql)
		            if(sqlAspect != null) {
	                    ObjectID oldObjID = (ObjectID) sqlAspect.getObjectID(oldEobject);
	                    ObjectID newObjID = (ObjectID) sqlAspect.getObjectID(newEobject);
	                    if(oldObjID != null && newObjID != null) {
			                this.oldToNewNames.put(oldObjID.toString().toUpperCase(), newObjID.toString());		                        
	                    }
	                    String oldObjName = sqlAspect.getFullName(oldEobject);
	                    String newObjName = sqlAspect.getFullName(newEobject);
	                    if(oldObjName != null && newObjName != null) {
			                this.oldToNewNames.put(oldObjName.toUpperCase(), newObjName);		                        
	                    }
		            }
		        }
		    }
        }

        // look up new name and update symbol name
        String newName = (String) this.oldToNewNames.get(fullName.toUpperCase());
        return newName;
    }

}
