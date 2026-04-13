/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.contract;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.archimatetool.mcp.bootstrap.MCPServerState;
import com.archimatetool.mcp.observability.RequestContext;

/**
 * Central factory for MCP failure envelope generation.
 */
@SuppressWarnings("nls")
public final class MCPErrorResponseFactory {

    public static final class ErrorResponse {
        private final int statusCode;
        private final String body;
        private final MCPErrorCode errorCode;

        private ErrorResponse(int statusCode, String body, MCPErrorCode errorCode) {
            this.statusCode = statusCode;
            this.body = body;
            this.errorCode = errorCode;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }

        public MCPErrorCode getErrorCode() {
            return errorCode;
        }
    }

    private MCPErrorResponseFactory() {
    }

    public static ErrorResponse failure(RequestContext context, MCPErrorCode code) {
        return failure(context, code, code.getDefaultMessage(), Map.of(), null);
    }

    public static ErrorResponse failure(RequestContext context, MCPErrorCode code, String message, Map<String, String> details) {
        return failure(context, code, message, details, null);
    }

    public static ErrorResponse failure(RequestContext context, MCPErrorCode code, String message, Map<String, String> details, Boolean retryable) {
        RequestContext resolvedContext = context != null ? context : RequestContext.create();
        String resolvedMessage = message == null || message.isBlank() ? code.getDefaultMessage() : message;
        MCPErrorBody errorBody = MCPErrorBody.of(code, resolvedMessage, details, retryable);
        String body = toEnvelopeJson(resolvedContext, errorBody);
        return new ErrorResponse(code.getHttpStatus(), body, code);
    }

    public static ErrorResponse startupFailure(RequestContext context, MCPServerState state) {
        MCPErrorCode code = MCPErrorCode.fromStartupReason(state == null ? null : state.getReason());
        Map<String, String> details = new LinkedHashMap<>();
        if(state != null) {
            details.put("state", state.getLifecycleId());
            details.put("host", state.getHost());
            details.put("port", Integer.toString(state.getPort()));
            if(state.getReason() != null) {
                details.put("reason", state.getReason());
            }
        }
        return failure(context, code, code.getDefaultMessage(), details, null);
    }

    public static Map<String, String> details(String... keyValues) {
        Map<String, String> details = new LinkedHashMap<>();
        if(keyValues == null) {
            return details;
        }

        for(int i = 0; i + 1 < keyValues.length; i += 2) {
            String key = keyValues[i];
            String value = keyValues[i + 1];
            if(key != null) {
                details.put(key, value);
            }
        }
        return details;
    }

    private static String toEnvelopeJson(RequestContext context, MCPErrorBody errorBody) {
        String requestId = context == null ? "unknown" : context.getRequestId();
        String timestamp = context == null ? Instant.now().toString() : context.getTimestamp();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("ok", Boolean.FALSE);
        envelope.put("requestId", requestId);
        envelope.put("timestamp", timestamp);
        envelope.put("data", null);
        envelope.put("error", errorBody.toMap());
        return MCPJson.write(envelope);
    }
}
