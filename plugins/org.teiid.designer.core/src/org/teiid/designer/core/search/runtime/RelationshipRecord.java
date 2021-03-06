/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.teiid.designer.core.search.runtime;

/**
 * RelationshipRecord.java
 *
 * @since 8.0
 */
public interface RelationshipRecord extends RelationshipSearchRecord {
	
	/**
	 * Returns the UUID of the relationship type
	 * @return the UUID of the relationship type
	 */
	String getTypeUUID();

	/**
	 * Returns the relationship type name
	 * @return the name of the relationship type
	 */
	String getTypeName();

	/**
	 * Return the path to the relationship resource
	 * @return the path to the relationship resource
	 * @since 4.2
	 */
	String getResourcePath();
}
