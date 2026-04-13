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
 * Structured MCP request model that supports nested params payloads.
 */
@SuppressWarnings("nls")
public final class MCPRequest {

    private final String id;
    private final String method;
    private final Map<String, Object> params;

    private MCPRequest(String id, String method, Map<String, Object> params) {
        this.id = id;
        this.method = method;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    public static MCPRequest parse(String json) {
        Map<String, Object> request = MCPJson.requireObject(MCPJson.parse(json), "request body must be a JSON object");
        Object methodValue = request.get("method");

        if(!(methodValue instanceof String method) || method.isBlank()) {
            throw new IllegalArgumentException("missing method");
        }

        Object idValue = request.get("id");
        if(idValue != null && !(idValue instanceof String) && !(idValue instanceof Number)) {
            throw new IllegalArgumentException("id must be string or number");
        }

        Object paramsValue = request.get("params");
        Map<String, Object> params = paramsValue == null ? Collections.emptyMap() : MCPJson.requireObject(paramsValue, "params must be an object");

        return new MCPRequest(idValue == null ? null : String.valueOf(idValue), method, params);
    }

    public String getId() {
        return id;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
