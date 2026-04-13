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
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.mcp.write.command.AddElementToViewCommand;

/**
 * Handles {@code addElementToView}.
 */
@SuppressWarnings("nls")
public class AddElementToViewHandler {

    private static final int DEFAULT_WIDTH = 120;
    private static final int DEFAULT_HEIGHT = 55;

    private final ElementTypeCatalog typeCatalog;

    public AddElementToViewHandler() {
        this(new ElementTypeCatalog());
    }

    AddElementToViewHandler(ElementTypeCatalog typeCatalog) {
        this.typeCatalog = typeCatalog;
    }

    public Object handle(WriteExecutionContext context) {
        MCPWriteRequest request = context.getRequest();
        IDiagramModel view = context.getReference("viewId", IDiagramModel.class);
        if(view == null) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "viewId must be a non-empty string",
                    Map.of("field", "viewId"),
                    Boolean.FALSE);
        }

        IArchimateElement element = context.getReference("elementId", IArchimateElement.class);
        if(element == null) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "elementId must be a non-empty string",
                    Map.of("field", "elementId"),
                    Boolean.FALSE);
        }

        IDiagramModelArchimateObject existingObject = findExistingObject(view, element.getId());
        if(existingObject != null) {
            return createResponse(request, context, view, element, existingObject);
        }

        MCPWriteRequest.Bounds bounds = request.getBounds();
        if(bounds == null) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "bounds must be an object",
                    Map.of("field", "bounds"),
                    Boolean.FALSE);
        }

        int width = bounds.getWidth() == null ? DEFAULT_WIDTH : bounds.getWidth().intValue();
        int height = bounds.getHeight() == null ? DEFAULT_HEIGHT : bounds.getHeight().intValue();

        AddElementToViewCommand command = new AddElementToViewCommand(view, element, (int)bounds.getX(), (int)bounds.getY(), width,
                height);
        context.getCommandStack().execute(command);

        return createResponse(request, context, view, element, command.getDiagramObject());
    }

    private IDiagramModelArchimateObject findExistingObject(IDiagramModel view, String elementId) {
        for(IDiagramModelObject child : view.getChildren()) {
            if(child instanceof IDiagramModelArchimateObject object && object.getArchimateElement() != null
                    && elementId.equals(object.getArchimateElement().getId())) {
                return object;
            }
        }

        return null;
    }

    private Object createResponse(MCPWriteRequest request, WriteExecutionContext context, IDiagramModel view, IArchimateElement element,
            IDiagramModelArchimateObject object) {
        Map<String, Object> modelData = new LinkedHashMap<>();
        modelData.put("modelId", context.getModel().getId());
        modelData.put("name", context.getModel().getName());

        Map<String, Object> viewData = new LinkedHashMap<>();
        viewData.put("viewId", view.getId());
        viewData.put("name", view.getName());

        Map<String, Object> elementData = new LinkedHashMap<>();
        elementData.put("elementId", element.getId());
        elementData.put("type", typeCatalog.getTypeId(element));
        elementData.put("name", element.getName());

        Map<String, Object> objectData = new LinkedHashMap<>();
        objectData.put("objectId", object.getId());
        objectData.put("x", Integer.valueOf(object.getBounds().getX()));
        objectData.put("y", Integer.valueOf(object.getBounds().getY()));
        objectData.put("width", Integer.valueOf(object.getBounds().getWidth()));
        objectData.put("height", Integer.valueOf(object.getBounds().getHeight()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("method", "write");
        response.put("operation", request.getOperation().getValue());
        response.put("model", modelData);
        response.put("view", viewData);
        response.put("element", elementData);
        response.put("object", objectData);
        return response;
    }
}
