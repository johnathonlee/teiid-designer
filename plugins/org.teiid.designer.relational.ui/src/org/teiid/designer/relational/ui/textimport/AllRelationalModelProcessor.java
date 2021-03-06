/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.teiid.designer.relational.ui.textimport;

import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.teiid.designer.core.ModelerCore;
import org.teiid.designer.core.types.DatatypeConstants;
import org.teiid.designer.jdbc.relational.impl.RelationalModelProcessorImpl;
import org.teiid.designer.metamodels.relational.RelationalFactory;
import org.teiid.designer.metamodels.relational.util.RelationalTypeMapping;


/**
 * @since 8.0
 */
public class AllRelationalModelProcessor extends RelationalModelProcessorImpl {
    private static final String VARCHAR2_TYPE_NAME = "VARCHAR2"; //$NON-NLS-1$
    private static final String NVARCHAR2_TYPE_NAME = "NVARCHAR2"; //$NON-NLS-1$
    private static final String TIMESTAMP_TYPE_NAME = "TIMESTAMP("; //$NON-NLS-1$

    /**
     * Construct an instance of OracleModelProcessor.
     * 
     * @param factory
     */
    public AllRelationalModelProcessor( final RelationalFactory factory,
                                        final RelationalTypeMapping mapping ) {
        super(factory, mapping);
        setDatatypeManager(ModelerCore.getWorkspaceDatatypeManager());
    }

    /**
     * Find the type given the supplied information. This method is called by the various <code>create*</code> methods, and is
     * currently implemented to use {@link #findType(int, int, List)} when a numeric type and {@link #findType(String, List)} (by
     * name) for other types.
     * 
     * @param type
     * @param typeName
     * @return
     */
    @Override
    protected EObject findType( final int jdbcType,
                                final String typeName,
                                final int length,
                                final int precision,
                                final int scale,
                                final List problems ) {

        EObject result = null;

        // Oracle 9i introduced the "timestamp" type name (with type=1111, or OTHER)
        if (typeName.startsWith(TIMESTAMP_TYPE_NAME)) {
            result = findBuiltinType(DatatypeConstants.BuiltInNames.TIMESTAMP, problems);
        }
        if (result != null) {
            return result;
        }

        return super.findType(jdbcType, typeName, length, precision, scale, problems);
    }

    /**
     * Overrides the method to find a type simply by name. This method converts some Oracle-specific (non-numeric) types to
     * standard names, and then simply delegates to the superclass. Find the datatype by name.
     * 
     * @param jdbcTypeName the name of the JDBC (or DBMS) type
     * @param problems the list if {@link IStatus} into which problems and warnings are to be placed; never null
     * @return the datatype that is able to represent data with the supplied criteria, or null if no datatype could be found
     * @see org.teiid.designer.jdbc.relational.impl.RelationalModelProcessorImpl#findType(java.lang.String, java.util.List)
     */
    @Override
    protected EObject findType( final String jdbcTypeName,
                                final List problems ) {
        String standardName = jdbcTypeName;
        if (VARCHAR2_TYPE_NAME.equalsIgnoreCase(jdbcTypeName) || NVARCHAR2_TYPE_NAME.equalsIgnoreCase(jdbcTypeName)) {
            standardName = RelationalTypeMapping.SQL_TYPE_NAMES.VARCHAR;
        }
        return super.findType(standardName, problems);
    }

    @Override
    protected boolean isFixedLength( final int type,
                                     final String typeName ) {
        if (NVARCHAR2_TYPE_NAME.equalsIgnoreCase(typeName)) {
            return false;
        }
        return super.isFixedLength(type, typeName);
    }
}
