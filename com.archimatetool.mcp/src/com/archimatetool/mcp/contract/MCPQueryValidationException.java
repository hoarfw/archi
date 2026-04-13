/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.contract;

/**
 * Query-specific validation failure with a stable MCP error code.
 */
@SuppressWarnings("nls")
public class MCPQueryValidationException extends IllegalArgumentException {
    private static final long serialVersionUID = 8237979625244152910L;

    private final MCPErrorCode errorCode;

    public MCPQueryValidationException(MCPErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MCPErrorCode getErrorCode() {
        return errorCode;
    }
}
