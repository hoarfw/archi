/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.contract;

import java.util.LinkedHashMap;
import java.util.Map;

import com.archimatetool.mcp.observability.RequestContext;

/**
 * Minimal response envelope used by MCP stream endpoint.
 */
@SuppressWarnings("nls")
public final class MCPResponseEnvelope {

    private final boolean ok;
    private final String requestId;
    private final String timestamp;
    private final Object data;
    private final MCPErrorBody error;

    private MCPResponseEnvelope(boolean ok, String requestId, String timestamp, Object data, MCPErrorBody error) {
        this.ok = ok;
        this.requestId = requestId;
        this.timestamp = timestamp;
        this.data = data;
        this.error = error;
    }

    public static MCPResponseEnvelope success(RequestContext context, Object data) {
        return new MCPResponseEnvelope(true, context.getRequestId(), context.getTimestamp(), data, null);
    }

    public static MCPResponseEnvelope failure(RequestContext context, MCPErrorBody error) {
        return new MCPResponseEnvelope(false, context.getRequestId(), context.getTimestamp(), null, error);
    }

    public String toJson() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("ok", Boolean.valueOf(ok));
        envelope.put("requestId", requestId);
        envelope.put("timestamp", timestamp);
        envelope.put("data", ok ? data : null);
        envelope.put("error", ok ? null : error.toMap());
        return MCPJson.write(envelope);
    }
}
