/*
 * JBoss, Home of Professional Open Source.
*
* See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
*
* See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
*/
package org.teiid.designer.modelgenerator.wsdl.ui.wizards.soap;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;
import org.teiid.designer.modelgenerator.wsdl.ui.Messages;
import org.teiid.language.SQLConstants;

import com.metamatrix.metamodels.relational.aspects.validation.RelationalStringNameValidator;
import com.metamatrix.modeler.core.validation.rules.StringNameValidator;
import com.metamatrix.modeler.internal.transformation.util.SqlConstants;
import com.metamatrix.modeler.modelgenerator.wsdl.model.Operation;
import com.metamatrix.modeler.modelgenerator.wsdl.ui.ModelGeneratorWsdlUiConstants;
import com.metamatrix.modeler.modelgenerator.wsdl.ui.internal.wizards.WSDLImportWizardManager;

/** This class provides state information for the create and extract procedures that will be generated during
 * WSDL import and model/procedure generation
 * 
 * There will be both Request and Response information managed by this class.
 * 
 * 
 */
public class ProcedureGenerator implements SqlConstants {
	public static final String PLUGIN_ID = ModelGeneratorWsdlUiConstants.PLUGIN_ID;
	
	private static final StringNameValidator nameValidator = new RelationalStringNameValidator(false, true);
	
	private static final String SQL_BEGIN = "CREATE VIRTUAL PROCEDURE\nBEGIN\n"; //$NON-NLS-1$
	private static final String SQL_END = "\nEND"; //$NON-NLS-1$
	private static final String REQUEST = "REQUEST"; //$NON-NLS-1$
	private static final String RESPONSE = "RESPONSE"; //$NON-NLS-1$
	private static final String REQUEST_LOWER = "request"; //$NON-NLS-1$
	private static final String RESPONSE_LOWER = "response"; //$NON-NLS-1$
	private static final String TABLE_EXEC = "TABLE(EXEC "; //$NON-NLS-1$
	private static final String XMI_EXTENSION = ".xmi";  //$NON-NLS-1$
	private static final String RESULT_LOWER = "result";  //$NON-NLS-1$
	private static final String INVOKE_SEGMENT_1 = "invoke('"; //$NON-NLS-1$
	private static final String INVOKE_SEGMENT_2 =  "', null, REQUEST.xml_out, null))"; //$NON-NLS-1$
	private static final String NULL_LOWER = "null";  //$NON-NLS-1$

	private ProcedureInfo requestInfo;
	private ProcedureInfo responseInfo;
	
	private boolean generateWrapperProcedure;
	
	private String wrapperProcedureName;
	
	private boolean overwriteExistingProcedures;
	
	private Operation operation;
	
	private String soapAction;
	
	private String viewModelName;
	
	private String namespaceURI;
	
	private String bindingType;
	
	private WSDLImportWizardManager importManager;
	
	

	public ProcedureGenerator(Operation operation, WSDLImportWizardManager importManager) {
		super();
		this.operation = operation;
		this.requestInfo = new RequestInfo(operation, this);
		this.responseInfo = new ResponseInfo(operation, this);
		this.viewModelName = operation.getBinding().getPort().getService().getName() + "View"; //$NON-NLS-1$
		this.importManager = importManager;
		this.generateWrapperProcedure = true;
		this.namespaceURI = operation.getBinding().getPort().getNamespaceURI();
		this.bindingType = operation.getBinding().getPort().getBindingType();
		this.soapAction = operation.getSOAPAction();
	}

	public WSDLImportWizardManager getImportManager() {
		return this.importManager;
	}
	
	public ProcedureInfo getRequestInfo() {
		return this.requestInfo;
	}

	public ProcedureInfo getResponseInfo() {
		return this.responseInfo;
	}

	public Operation getOperation() {
		return this.operation;
	}
	
	public void setRequestProcedureName(String name ) {
		this.requestInfo.setProcedureName(name);
	}
	
	public void setResponseProcedureName(String name ) {
		this.responseInfo.setProcedureName(name);
	}
	
	public String getRequestProcedureName() {
		return this.requestInfo.getProcedureName();
	}
	
	public String getResponseProcedureName() {
		return this.responseInfo.getProcedureName();
	}
	
	public String getViewModelName() {
		return this.viewModelName;
	}

	public void setGenerateWrapperProcedure(boolean value) {
		this.generateWrapperProcedure = value;
	}
	
	public boolean doGenerateWrapperProcedure() {
		return this.generateWrapperProcedure;
	}
	
	public void setWrapperProcedureName(String name ) {
		this.wrapperProcedureName = name;
	}
	
	public String getWrappedProcedureName() {
		if( this.wrapperProcedureName == null ) {
			this.wrapperProcedureName = getOperation().getName();
		}
		return this.wrapperProcedureName;
	}
	
	
	public void setOverwriteExistingProcedures(boolean value) {
		this.overwriteExistingProcedures = value;
	}
	
	public boolean doOverwriteExistingProcedures() {
		return this.overwriteExistingProcedures;
	}
	
	public String getSoapAction() {
		return this.soapAction;
	}
	
	public String getNamespaceURI() {
		return this.namespaceURI;
	}
	
	public String getWrapperSqlString() {
		/**
            CREATE VIRTUAL PROCEDURE
            BEGIN
                 SELECT t.* FROM 
                      TABLE(EXEC CountryInfoServiceXML.CapitalCity.create_CapitalCity(OPS.GETCAPITALCITY.countryISOCode)) 
                 AS request, 
                 TABLE(EXEC CountryInfoService.invoke('SOAP11', null, REQUEST.xml_out, null)) 
                 AS response, 
                 TABLE(EXEC CountryInfoServiceXML.CapitalCity.extract_CapitalCityResponse(RESPONSE.result)) 
                 AS t;
             END
                 
             CREATE VIRTUAL PROCEDURE
             BEGIN
                 SELECT t.* FROM 
                    TABLE(EXEC <view-model-name>.<request_procedure>(OPS.GETCAPITALCITY.countryISOCode)) 
                 AS request, 
                 	TABLE(EXEC <source-model-name>.invoke('SOAP11', null, REQUEST.xml_out, null)) 
                 AS response, 
                 	TABLE(EXEC <view-model-name>.<response_procedure>(RESPONSE.result)) 
                 AS t;
             END
		 */
		
		StringBuilder sb = new StringBuilder();
		
    	String tableAlias = "t"; //$NON-NLS-1$

    	sb.append(SQL_BEGIN);
    	// SELECT t.* FROM 
    	sb.append(TAB).append(SELECT).append(SPACE).append(tableAlias).append(DOT).append(STAR).append(SPACE).append(FROM).append(RETURN);
    	// TABLE(EXEC 
    	sb.append(TAB).append(TAB).append(TABLE_EXEC);
    	// <view-model-name>.<request_procedure>
    	sb.append(getModelNameWithoutExtension(importManager.getViewModelName()));
    	sb.append(DOT).append(getRequestProcedureName());
    	
    	// (OPS.GETCAPITALCITY.countryISOCode))
    	sb.append(L_PAREN);
    	int nColumns = this.requestInfo.getColumnInfoList().length;
    	int i=0;
    	for( ColumnInfo columnInfo : this.requestInfo.getColumnInfoList()) {
    		String name = columnInfo.getName();
    		sb.append(getParameterFullName(name));
    		
    		if(i < (nColumns-1)) {
    			sb.append(COMMA).append(SPACE);
    		}
    		i++;
    	}
    	sb.append(R_PAREN).append(RETURN);
    	
    	// AS request,
    	sb.append(TAB).append(AS).append(SPACE).append(REQUEST_LOWER).append(COMMA).append(RETURN);
    	
    	// TABLE(EXEC <source-model-name>.invoke('SOAP11', null, REQUEST.xml_out, null))
    	sb.append(TAB).append(TAB)
    		.append(TABLE_EXEC)
    		.append(getModelNameWithoutExtension(importManager.getSourceModelName())).append(DOT)
    		.append(INVOKE_SEGMENT_1).append(this.bindingType).append(INVOKE_SEGMENT_2).append(RETURN);
    	
    	// AS response,
    	sb.append(TAB).append(AS).append(SPACE).append(RESPONSE_LOWER).append(COMMA).append(RETURN);
    	
    	// TABLE(EXEC <view-model-name>.<response_procedure>(RESPONSE.result))  
    	sb.append(TAB).append(TAB)
    		.append(TABLE_EXEC)
    		.append(getModelNameWithoutExtension(importManager.getViewModelName()))
    		.append(DOT).append(getResponseProcedureName())
    		.append(L_PAREN).append(RESPONSE).append(DOT).append(RESULT_LOWER)
    		.append(R_PAREN).append(R_PAREN).append(RETURN);
    	
    	// AS t;
    	sb.append(TAB).append(AS).append(SPACE).append(tableAlias).append(SEMI_COLON).append(RETURN);
    	
    	sb.append(SQL_END);

    	
		return sb.toString();
	}
	
	private String getModelNameWithoutExtension(String modelName) {
		String name = modelName;
        if (name.endsWith(XMI_EXTENSION)) {
        	name = name.substring(0, name.lastIndexOf(XMI_EXTENSION));
        }
        return name;
	}
	
	private String getParameterFullName(String name) {
		StringBuilder builder = new StringBuilder();
		builder.append(this.getViewModelName());
		builder.append('.').append(getWrappedProcedureName()).append('.').append(convertSqlNameSegment(name));
		
		return builder.toString();
	}
	
	public String getProcedureFullName(ProcedureInfo info) {
		StringBuilder builder = new StringBuilder();
		builder.append(this.getViewModelName());
		builder.append('.').append(info.getProcedureName());
		
		return builder.toString();
	}
	
	private String getWrapperProcedureParameterName(String parameterName) {
		StringBuilder builder = new StringBuilder();
		builder.append(this.getViewModelName());
		builder.append(DOT).append(getWrappedProcedureName()).append(DOT).append(parameterName);
		
		return builder.toString();
	}
	
	public IStatus getNameStatus(String name) {
		String result = nameValidator.checkValidName(name);
		if( result != null ) {
			return new Status(IStatus.ERROR, PLUGIN_ID, NLS.bind(Messages.Error_InvalidName_0_Reason_1, name, result));
		}
		
		return Status.OK_STATUS;
	}
	
	public IStatus validate() {
		IStatus status = Status.OK_STATUS;
		// Go through objects and look for problems
		if( getWrappedProcedureName() == null) {	
			return new Status(IStatus.ERROR, PLUGIN_ID, Messages.Error_WrapperProcedureCannotBeNullOrEmpty);
		}
		
		IStatus nameStatus = getNameStatus(getWrappedProcedureName());
		if( nameStatus.getSeverity() > IStatus.INFO) {
			return nameStatus;
		}
		
		IStatus requestStatus = getRequestInfo().validate();
		if( requestStatus.getSeverity() > IStatus.INFO ) {
			return requestStatus;
		}

		IStatus responseStatus = getResponseInfo().validate();
		if( responseStatus.getSeverity() > IStatus.INFO ) {
			return responseStatus;
		}
		
		return status;
	}
	
	/**
	 * Converts any name string to a valid SQL symbol segment
	 * Basically looks to see if name is a reserved word and if so, returns the name in double-quotes
	 * 
	 * @param name
	 * @return
	 */
	public String convertSqlNameSegment(String name) {
		if( SQLConstants.isReservedWord(name) ) {
			return '\"' + name + '\"';
		}
		
		return name;
	}
	
	@SuppressWarnings("unused")
	public String getWrapperProcedureSqlString() {
		/*
    	CREATE VIRTUAL PROCEDURE
    	BEGIN
    		SELECT t.* FROM 
    			TABLE(EXEC PriceServiceView.GetPrice_request(
    					PriceServiceView.GetPrice_procedure.productID,
    					PriceServiceView.GetPrice_procedure.productName)) 
    				AS request, 
    			TABLE(EXEC PriceService.invoke('SOAP11', null, REQUEST.xml_out, null)) 
    				AS response, 
    			TABLE(EXEC PriceServiceView.GetPrice_response(RESPONSE.result)) 
    				AS t;
    	END
    	*/
		StringBuilder sb = new StringBuilder();
		
    	String tableAlias = "t"; //$NON-NLS-1$

    	sb.append(SQL_BEGIN);
    	// SELECT t.* FROM 
    	sb.append(TAB).append(SELECT).append(SPACE).append(tableAlias).append(DOT).append(STAR).append(SPACE).append(FROM).append(RETURN);

    	// Request TABLE
    	sb.append(TAB2).append(TABLE).append(L_PAREN).append(EXEC).append(SPACE);
    	sb.append(getProcedureFullName(getRequestInfo()));
    	sb.append(L_PAREN);
    	REQUEST_PARAMETERS : {
    		int i=0;
    		int nColumns = getRequestInfo().getColumnInfoList().length;
    		
    		for ( ColumnInfo columnInfo : getRequestInfo().getColumnInfoList() ) {
    			sb.append(TAB4).append(getWrapperProcedureParameterName(convertSqlNameSegment(columnInfo.getName())));
        		if(i < (nColumns-1)) {
        			sb.append(COMMA).append(SPACE).append(RETURN);
        		}
        		i++;
    		}
    	}
    	sb.append(R_PAREN);
    	sb.append(R_PAREN).append(RETURN).append(TAB4).append(AS).append(SPACE).append(REQUEST_LOWER).append(COMMA).append(RETURN);
    	
    	// Response TABLE
    	sb.append(TAB2).append(TABLE).append(L_PAREN).append(EXEC).append(SPACE);
    	sb.append(getModelNameWithoutExtension(this.importManager.getSourceModelName())).append(DOT);
    	INVOKE_CALL : { 
    		String actionStr = NULL_LOWER;
    		if( actionStr != null ) {
    			actionStr = S_QUOTE + this.soapAction + S_QUOTE;
    		}
    		sb.append(FUNCTION_INVOKE);
    		sb.append(L_PAREN).append(S_QUOTE).append(this.bindingType).append(S_QUOTE).append(COMMA).append(SPACE).append(actionStr).append(COMMA).append(SPACE); 
    		sb.append(REQUEST).append(DOT).append("xml_out").append(COMMA).append(SPACE).append(NULL_LOWER); //$NON-NLS-1$
    		sb.append(R_PAREN);
    	}
    	sb.append(R_PAREN).append(RETURN).append(TAB4).append(AS).append(SPACE).append(RESPONSE_LOWER).append(COMMA).append(RETURN);
    	
    	// Request TABLE:  [		TABLE(EXEC PriceServiceView.GetPrice_response(RESPONSE.result)) ]
    	sb.append(TAB2).append(TABLE).append(L_PAREN).append(EXEC).append(SPACE);
    	sb.append(getProcedureFullName(getResponseInfo()));
    	sb.append(L_PAREN).append(RESPONSE).append(DOT).append(RESULT_LOWER).append(R_PAREN).append(R_PAREN).append(RETURN);
    	sb.append(TAB4).append(AS).append(SPACE).append(tableAlias).append(SEMI_COLON);
    	
    	sb.append(SQL_END);
		
    	return sb.toString();
	}
}
