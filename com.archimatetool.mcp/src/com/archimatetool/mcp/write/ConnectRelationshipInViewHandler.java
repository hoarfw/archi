/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.write;

import java.util.LinkedHashMap;
import java.util.Map;

import com.archimatetool.mcp.contract.MCPErrorCode;
import com.archimatetool.mcp.contract.MCPStructuredErrorException;
import com.archimatetool.mcp.contract.MCPWriteRequest;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.mcp.write.command.ConnectRelationshipInViewCommand;

/**
 * Handles {@code connectRelationshipInView}.
 */
@SuppressWarnings("nls")
public class ConnectRelationshipInViewHandler {

    private final RelationshipTypeCatalog typeCatalog;
    private final DiagramConnectionValidator diagramConnectionValidator;

    public ConnectRelationshipInViewHandler() {
        this(new RelationshipTypeCatalog(), new DiagramConnectionValidator());
    }

    ConnectRelationshipInViewHandler(RelationshipTypeCatalog typeCatalog, DiagramConnectionValidator diagramConnectionValidator) {
        this.typeCatalog = typeCatalog;
        this.diagramConnectionValidator = diagramConnectionValidator;
    }

    public Object handle(WriteExecutionContext context) {
        MCPWriteRequest request = context.getRequest();
        IDiagramModel view = context.getReference("viewId", IDiagramModel.class);
        IArchimateRelationship relationship = context.getReference("relationshipId", IArchimateRelationship.class);
        IDiagramModelArchimateObject sourceObject = context.getReference("sourceObjectId", IDiagramModelArchimateObject.class);
        IDiagramModelArchimateObject targetObject = context.getReference("targetObjectId", IDiagramModelArchimateObject.class);

        if(view == null) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "viewId must be a non-empty string",
                    Map.of("field", "viewId"),
                    Boolean.FALSE);
        }
        if(relationship == null) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "relationshipId must be a non-empty string",
                    Map.of("field", "relationshipId"),
                    Boolean.FALSE);
        }
        if(sourceObject == null) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "sourceObjectId must be a non-empty string",
                    Map.of("field", "sourceObjectId"),
                    Boolean.FALSE);
        }
        if(targetObject == null) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "targetObjectId must be a non-empty string",
                    Map.of("field", "targetObjectId"),
                    Boolean.FALSE);
        }

        diagramConnectionValidator.validate(view, relationship, sourceObject, targetObject);

        IDiagramModelArchimateConnection existing = findExistingConnection(relationship, view, sourceObject, targetObject);
        if(existing != null) {
            return createResponse(request, context, view, relationship, existing);
        }

        ConnectRelationshipInViewCommand command = new ConnectRelationshipInViewCommand(relationship, sourceObject, targetObject);
        context.getCommandStack().execute(command);
        return createResponse(request, context, view, relationship, command.getConnection());
    }

    private IDiagramModelArchimateConnection findExistingConnection(IArchimateRelationship relationship, IDiagramModel view,
            IDiagramModelArchimateObject sourceObject, IDiagramModelArchimateObject targetObject) {
        for(IDiagramModelArchimateConnection connection : relationship.getReferencingDiagramConnections()) {
            if(connection.getDiagramModel() == view && connection.getSource() == sourceObject && connection.getTarget() == targetObject) {
                return connection;
            }
        }

        return null;
    }

    private Object createResponse(MCPWriteRequest request, WriteExecutionContext context, IDiagramModel view,
            IArchimateRelationship relationship, IDiagramModelArchimateConnection connection) {
        Map<String, Object> modelData = new LinkedHashMap<>();
        modelData.put("modelId", context.getModel().getId());
        modelData.put("name", context.getModel().getName());

        Map<String, Object> viewData = new LinkedHashMap<>();
        viewData.put("viewId", view.getId());
        viewData.put("name", view.getName());

        Map<String, Object> relationshipData = new LinkedHashMap<>();
        relationshipData.put("relationshipId", relationship.getId());
        relationshipData.put("type", typeCatalog.getTypeId(relationship));
        relationshipData.put("sourceElementId", relationship.getSource().getId());
        relationshipData.put("targetElementId", relationship.getTarget().getId());

        Map<String, Object> connectionData = new LinkedHashMap<>();
        connectionData.put("connectionId", connection.getId());
        connectionData.put("sourceObjectId", connection.getSource().getId());
        connectionData.put("targetObjectId", connection.getTarget().getId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("method", "write");
        response.put("operation", request.getOperation().getValue());
        response.put("model", modelData);
        response.put("view", viewData);
        response.put("relationship", relationshipData);
        response.put("connection", connectionData);
        return response;
    }
}
