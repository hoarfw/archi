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
import com.archimatetool.mcp.write.command.CreateElementCommand;

/**
 * Handles {@code createElement}.
 */
@SuppressWarnings("nls")
public class CreateElementHandler {

    private final ElementTypeCatalog typeCatalog;

    public CreateElementHandler() {
        this(new ElementTypeCatalog());
    }

    CreateElementHandler(ElementTypeCatalog typeCatalog) {
        this.typeCatalog = typeCatalog;
    }

    public Object handle(WriteExecutionContext context) {
        MCPWriteRequest request = context.getRequest();
        String typeId = request.findString("type");
        if(typeId == null) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "type must be a non-empty string",
                    Map.of("field", "type"),
                    Boolean.FALSE);
        }

        IArchimateElement element = typeCatalog.create(typeId);
        String name = request.findString("name");
        if(name != null) {
            element.setName(name);
        }

        String documentation = request.findString("documentation");
        if(documentation != null) {
            element.setDocumentation(documentation);
        }

        CreateElementCommand command = new CreateElementCommand(context.getModel(), element);
        context.getCommandStack().execute(command);

        Map<String, Object> elementData = new LinkedHashMap<>();
        elementData.put("elementId", element.getId());
        elementData.put("type", typeCatalog.getTypeId(element));
        elementData.put("name", element.getName());
        elementData.put("documentation", element.getDocumentation());

        Map<String, Object> modelData = new LinkedHashMap<>();
        modelData.put("modelId", context.getModel().getId());
        modelData.put("name", context.getModel().getName());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("method", "write");
        response.put("operation", request.getOperation().getValue());
        response.put("model", modelData);
        response.put("element", elementData);
        return response;
    }
}
