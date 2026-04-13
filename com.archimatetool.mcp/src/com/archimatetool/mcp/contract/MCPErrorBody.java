/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified MCP error payload.
 */
@SuppressWarnings("nls")
public final class MCPErrorBody {

    private final MCPErrorCode code;
    private final String message;
    private final Map<String, String> details;
    private final Boolean retryable;

    private MCPErrorBody(MCPErrorCode code, String message, Map<String, String> details, Boolean retryable) {
        this.code = code;
        this.message = message;
        this.details = details == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
        this.retryable = retryable;
    }

    public static MCPErrorBody of(MCPErrorCode code, String message, Map<String, String> details, Boolean retryable) {
        return new MCPErrorBody(code, message, details, retryable);
    }

    public MCPErrorCode getCode() {
        return code;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code.getCode());
        result.put("category", code.getCategory());
        result.put("message", message);
        result.put("details", details);

        if(retryable != null) {
            result.put("retryable", retryable);
        }

        return result;
    }

    public String toJson() {
        return MCPJson.write(toMap());
    }
}
