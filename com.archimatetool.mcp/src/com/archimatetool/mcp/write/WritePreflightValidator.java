/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.write;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.CommandStack;

import com.archimatetool.mcp.bootstrap.MCPWriteTelemetry;
import com.archimatetool.mcp.contract.MCPErrorCode;
import com.archimatetool.mcp.contract.MCPStructuredErrorException;
import com.archimatetool.mcp.contract.MCPWriteRequest;
import com.archimatetool.mcp.read.EditorModelAccess;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.util.ArchimateModelUtils;

/**
 * Resolves live model state and rejects invalid targets before any write command executes.
 */
@SuppressWarnings("nls")
public class WritePreflightValidator {

    private static final String VIEW_ID = "viewId";
    private static final String ELEMENT_ID = "elementId";
    private static final String RELATIONSHIP_ID = "relationshipId";
    private static final String SOURCE_ELEMENT_ID = "sourceElementId";
    private static final String TARGET_ELEMENT_ID = "targetElementId";
    private static final String SOURCE_OBJECT_ID = "sourceObjectId";
    private static final String TARGET_OBJECT_ID = "targetObjectId";

    private final EditorModelAccess editorModelAccess = new EditorModelAccess();

    public WriteExecutionContext validate(MCPWriteRequest request) {
        if(request == null) {
            throw failure(MCPErrorCode.WRITE_BAD_REQUEST, "missing request", Map.of("field", "request"));
        }

        IArchimateModel model = resolveModel(request.getModelId());
        CommandStack commandStack = resolveCommandStack(model);
        Map<String, EObject> resolvedReferences = new LinkedHashMap<>();

        resolveOptionalReference(model, request, resolvedReferences, VIEW_ID, IDiagramModel.class, MCPErrorCode.WRITE_VIEW_NOT_FOUND);
        resolveOptionalReference(model, request, resolvedReferences, ELEMENT_ID, IArchimateElement.class,
                MCPErrorCode.WRITE_ELEMENT_NOT_FOUND);
        resolveOptionalReference(model, request, resolvedReferences, RELATIONSHIP_ID, IArchimateRelationship.class,
                MCPErrorCode.WRITE_RELATIONSHIP_NOT_FOUND);
        resolveOptionalReference(model, request, resolvedReferences, SOURCE_ELEMENT_ID, IArchimateElement.class,
                MCPErrorCode.WRITE_ELEMENT_NOT_FOUND);
        resolveOptionalReference(model, request, resolvedReferences, TARGET_ELEMENT_ID, IArchimateElement.class,
                MCPErrorCode.WRITE_ELEMENT_NOT_FOUND);
        resolveOptionalReference(model, request, resolvedReferences, SOURCE_OBJECT_ID, IDiagramModelObject.class,
                MCPErrorCode.WRITE_OBJECT_NOT_FOUND);
        resolveOptionalReference(model, request, resolvedReferences, TARGET_OBJECT_ID, IDiagramModelObject.class,
                MCPErrorCode.WRITE_OBJECT_NOT_FOUND);

        MCPWriteTelemetry.recordContextBuild(UIThreadWriteExecutor.isInUiSafeRuntime(), Thread.currentThread().getName(),
                model.getId(), commandStack != null);

        return new WriteExecutionContext(request, model, commandStack, resolvedReferences);
    }

    private IArchimateModel resolveModel(String modelId) {
        List<IArchimateModel> models = editorModelAccess.getOpenModels();

        if(models.isEmpty()) {
            throw failure(MCPErrorCode.WRITE_MODEL_NOT_FOUND, "no open models", Map.of("field", "modelId"));
        }

        if(modelId == null) {
            if(models.size() == 1) {
                return models.get(0);
            }

            throw failure(MCPErrorCode.WRITE_BAD_REQUEST,
                    "modelId is required when multiple models are open",
                    Map.of("field", "modelId"));
        }

        for(IArchimateModel model : models) {
            if(modelId.equals(model.getId())) {
                return model;
            }
        }

        throw failure(MCPErrorCode.WRITE_MODEL_NOT_FOUND, "modelId not found: " + modelId,
                Map.of("field", "modelId", "modelId", modelId));
    }

    private CommandStack resolveCommandStack(IArchimateModel model) {
        CommandStack commandStack = (CommandStack)model.getAdapter(CommandStack.class);
        if(commandStack == null) {
            throw failure(MCPErrorCode.WRITE_COMMAND_STACK_UNAVAILABLE,
                    "CommandStack not available for model: " + model.getId(),
                    Map.of("field", "modelId", "modelId", model.getId()));
        }

        return commandStack;
    }

    private void resolveOptionalReference(IArchimateModel model, MCPWriteRequest request, Map<String, EObject> resolvedReferences,
            String key, Class<? extends EObject> expectedType, MCPErrorCode errorCode) {
        String id = request.findString(key);
        if(id == null) {
            return;
        }

        EObject value = ArchimateModelUtils.getObjectByID(model, id);
        if(value == null || !expectedType.isInstance(value)) {
            throw failure(errorCode, key + " not found: " + id,
                    Map.of("field", key, key, id, "modelId", model.getId()));
        }

        resolvedReferences.put(key, value);
    }

    private MCPStructuredErrorException failure(MCPErrorCode errorCode, String message, Map<String, String> details) {
        return new MCPStructuredErrorException(errorCode, message, details, Boolean.FALSE);
    }
}
