/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.contract;

/**
 * Stable write operations exposed through the single MCP write endpoint.
 */
@SuppressWarnings("nls")
public enum MCPWriteOperation {

    CREATE_ELEMENT("createElement"),
    ADD_ELEMENT_TO_VIEW("addElementToView"),
    CREATE_RELATIONSHIP("createRelationship"),
    CONNECT_RELATIONSHIP_IN_VIEW("connectRelationshipInView");

    private final String value;

    MCPWriteOperation(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MCPWriteOperation fromValue(Object value) {
        if(!(value instanceof String text) || text.isBlank()) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "operation must be a non-empty string",
                    java.util.Map.of("field", "operation"),
                    Boolean.FALSE);
        }

        for(MCPWriteOperation operation : values()) {
            if(operation.value.equals(text)) {
                return operation;
            }
        }

        throw new MCPStructuredErrorException(MCPErrorCode.WRITE_UNSUPPORTED_OPERATION,
                "unknown write operation: " + text,
                java.util.Map.of("field", "operation", "operation", text),
                Boolean.FALSE);
    }
}
