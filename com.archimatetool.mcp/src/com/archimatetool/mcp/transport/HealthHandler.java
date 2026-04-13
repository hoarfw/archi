/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.transport;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;
import com.archimatetool.mcp.bootstrap.MCPServerState;
import com.archimatetool.mcp.bootstrap.MCPServerState.Lifecycle;
import com.archimatetool.mcp.contract.MCPErrorCode;
import com.archimatetool.mcp.contract.MCPErrorResponseFactory;
import com.archimatetool.mcp.contract.MCPErrorResponseFactory.ErrorResponse;
import com.archimatetool.mcp.observability.RequestContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Basic /health endpoint for MCP service diagnostics.
 */
@SuppressWarnings("nls")
public class HealthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        RequestContext context = RequestContext.create();
        RequestContext.bind(context);

        try {
            if(!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                ErrorResponse error = MCPErrorResponseFactory.failure(context,
                        MCPErrorCode.PROTOCOL_UNSUPPORTED_HTTP_METHOD,
                        "Only GET is supported on /health",
                        MCPErrorResponseFactory.details("endpoint", "/health", "method", exchange.getRequestMethod()));
                writeJson(exchange, error.getStatusCode(), error.getBody());
                return;
            }

            MCPServerState state = MCPServerBootstrap.getState();
            if(state.getLifecycle() != Lifecycle.RUNNING) {
                ErrorResponse error = MCPErrorResponseFactory.startupFailure(context, state);
                writeJson(exchange, error.getStatusCode(), error.getBody());
                return;
            }

            String version = getBundleVersion();
            String body = "{"
                    + "\"ok\":true"
                    + ",\"state\":\"" + json(state.getLifecycleId()) + "\""
                    + ",\"host\":\"" + json(state.getHost()) + "\""
                    + ",\"port\":" + state.getPort()
                    + ",\"version\":\"" + json(version) + "\""
                    + ",\"timestamp\":\"" + state.getTimestamp() + "\""
                    + "}";
            writeJson(exchange, 200, body);
        }
        catch(RuntimeException ex) {
            ErrorResponse error = MCPErrorResponseFactory.failure(context,
                    MCPErrorCode.INTERNAL_UNEXPECTED,
                    null,
                    MCPErrorResponseFactory.details("endpoint", "/health"));
            writeJson(exchange, error.getStatusCode(), error.getBody());
        }
        finally {
            RequestContext.clear();
        }
    }

    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try(OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private String getBundleVersion() {
        Bundle bundle = FrameworkUtil.getBundle(HealthHandler.class);
        return bundle == null ? "unknown" : bundle.getVersion().toString();
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
