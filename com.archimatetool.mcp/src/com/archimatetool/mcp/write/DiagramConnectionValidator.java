/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.write;

import java.util.Map;

import com.archimatetool.mcp.contract.MCPErrorCode;
import com.archimatetool.mcp.contract.MCPStructuredErrorException;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;

/**
 * Validates whether an existing relationship can be projected into a given view.
 */
@SuppressWarnings("nls")
public class DiagramConnectionValidator {

    public void validate(IDiagramModel view, IArchimateRelationship relationship, IDiagramModelArchimateObject sourceObject,
            IDiagramModelArchimateObject targetObject) {
        if(sourceObject.getDiagramModel() != view) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_OBJECT_NOT_FOUND,
                    "sourceObjectId not found in view: " + sourceObject.getId(),
                    Map.of("field", "sourceObjectId", "sourceObjectId", sourceObject.getId(), "viewId", view.getId()),
                    Boolean.FALSE);
        }

        if(targetObject.getDiagramModel() != view) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_OBJECT_NOT_FOUND,
                    "targetObjectId not found in view: " + targetObject.getId(),
                    Map.of("field", "targetObjectId", "targetObjectId", targetObject.getId(), "viewId", view.getId()),
                    Boolean.FALSE);
        }

        if(sourceObject.getArchimateElement() != relationship.getSource()) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "sourceObjectId does not match relationship source: " + sourceObject.getId(),
                    Map.of("field", "sourceObjectId", "sourceObjectId", sourceObject.getId(), "relationshipId",
                            relationship.getId()),
                    Boolean.FALSE);
        }

        if(targetObject.getArchimateElement() != relationship.getTarget()) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "targetObjectId does not match relationship target: " + targetObject.getId(),
                    Map.of("field", "targetObjectId", "targetObjectId", targetObject.getId(), "relationshipId",
                            relationship.getId()),
                    Boolean.FALSE);
        }
    }
}
