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
 * Runtime exception carrying a stable MCP error contract.
 */
@SuppressWarnings("nls")
public class MCPStructuredErrorException extends RuntimeException {
    private static final long serialVersionUID = 2114534227400703563L;

    private final MCPErrorCode errorCode;
    private final Map<String, String> details;
    private final Boolean retryable;

    public MCPStructuredErrorException(MCPErrorCode errorCode, String message, Map<String, String> details, Boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
        this.retryable = retryable;
    }

    public MCPStructuredErrorException(MCPErrorCode errorCode, String message, Map<String, String> details, Boolean retryable,
            Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
        this.retryable = retryable;
    }

    public MCPErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public Boolean getRetryable() {
        return retryable;
    }
}
