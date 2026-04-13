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
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.mcp.write.command.CreateRelationshipCommand;

/**
 * Handles {@code createRelationship}.
 */
@SuppressWarnings("nls")
public class CreateRelationshipHandler {

    private final RelationshipTypeCatalog typeCatalog;

    public CreateRelationshipHandler() {
        this(new RelationshipTypeCatalog());
    }

    CreateRelationshipHandler(RelationshipTypeCatalog typeCatalog) {
        this.typeCatalog = typeCatalog;
    }

    public Object handle(WriteExecutionContext context) {
        MCPWriteRequest request = context.getRequest();
        IArchimateElement source = context.getReference("sourceElementId", IArchimateElement.class);
        IArchimateElement target = context.getReference("targetElementId", IArchimateElement.class);
        if(source == null) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "sourceElementId must be a non-empty string",
                    Map.of("field", "sourceElementId"),
                    Boolean.FALSE);
        }
        if(target == null) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "targetElementId must be a non-empty string",
                    Map.of("field", "targetElementId"),
                    Boolean.FALSE);
        }

        String relationshipType = request.findString("relationshipType");
        if(relationshipType == null) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "relationshipType must be a non-empty string",
                    Map.of("field", "relationshipType"),
                    Boolean.FALSE);
        }

        IArchimateRelationship relationship = typeCatalog.create(relationshipType);
        relationship.setSource(source);
        relationship.setTarget(target);

        String name = request.findString("name");
        if(name != null) {
            relationship.setName(name);
        }

        String documentation = request.findString("documentation");
        if(documentation != null) {
            relationship.setDocumentation(documentation);
        }

        CreateRelationshipCommand command = new CreateRelationshipCommand(context.getModel(), relationship);
        context.getCommandStack().execute(command);

        Map<String, Object> relationshipData = new LinkedHashMap<>();
        relationshipData.put("relationshipId", relationship.getId());
        relationshipData.put("type", typeCatalog.getTypeId(relationship));
        relationshipData.put("name", relationship.getName());
        relationshipData.put("sourceElementId", source.getId());
        relationshipData.put("targetElementId", target.getId());

        Map<String, Object> modelData = new LinkedHashMap<>();
        modelData.put("modelId", context.getModel().getId());
        modelData.put("name", context.getModel().getName());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("method", "write");
        response.put("operation", request.getOperation().getValue());
        response.put("model", modelData);
        response.put("relationship", relationshipData);
        return response;
    }
}
