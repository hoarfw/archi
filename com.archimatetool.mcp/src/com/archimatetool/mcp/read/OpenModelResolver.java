/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.read;

import java.util.List;

import com.archimatetool.mcp.contract.MCPErrorCode;
import com.archimatetool.mcp.contract.MCPQueryValidationException;
import com.archimatetool.model.IArchimateModel;

/**
 * Resolves the target model from the set of currently opened models.
 */
@SuppressWarnings("nls")
public class OpenModelResolver {

    private final EditorModelAccess editorModelAccess = new EditorModelAccess();

    public IArchimateModel resolve(String modelId) {
        List<IArchimateModel> models = editorModelAccess.getOpenModels();

        if(models.isEmpty()) {
            throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY, "no open models");
        }

        if(modelId == null) {
            if(models.size() == 1) {
                return models.get(0);
            }

            throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY,
                    "modelId is required when multiple models are open");
        }

        for(IArchimateModel model : models) {
            if(modelId.equals(model.getId())) {
                return model;
            }
        }

        throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY, "modelId not found: " + modelId);
    }
}
