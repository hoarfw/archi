/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.transport;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;
import com.archimatetool.mcp.bootstrap.MCPServerState;
import com.archimatetool.mcp.bootstrap.MCPServerState.Lifecycle;
import com.archimatetool.mcp.contract.MCPErrorCode;
import com.archimatetool.mcp.contract.MCPErrorResponseFactory;
import com.archimatetool.mcp.contract.MCPErrorResponseFactory.ErrorResponse;
import com.archimatetool.mcp.contract.MCPExportRequest;
import com.archimatetool.mcp.contract.MCPQueryRequest;
import com.archimatetool.mcp.contract.MCPQueryValidationException;
import com.archimatetool.mcp.contract.MCPRequest;
import com.archimatetool.mcp.contract.MCPResponseEnvelope;
import com.archimatetool.mcp.contract.MCPStructuredErrorException;
import com.archimatetool.mcp.contract.MCPWriteRequest;
import com.archimatetool.mcp.export.ViewExportHandler;
import com.archimatetool.mcp.observability.RequestContext;
import com.archimatetool.mcp.read.MCPQueryDispatcher;
import com.archimatetool.mcp.write.MCPWriteDispatcher;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Basic HTTP Stream handler for MCP endpoint.
 */
@SuppressWarnings("nls")
public class MCPStreamHandler implements HttpHandler {

    private static final Set<String> SUPPORTED_METHODS = Set.of("initialize", "ping", "query", "exportView", "write");

    private final MCPQueryDispatcher queryDispatcher = new MCPQueryDispatcher();
    private final ViewExportHandler viewExportHandler = new ViewExportHandler();
    private final MCPWriteDispatcher writeDispatcher = new MCPWriteDispatcher();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        RequestContext context = RequestContext.create();
        RequestContext.bind(context);

        try {
            if(!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                ErrorResponse error = MCPErrorResponseFactory.failure(context,
                        MCPErrorCode.PROTOCOL_UNSUPPORTED_HTTP_METHOD,
                        "Only POST is supported on /mcp",
                        MCPErrorResponseFactory.details("endpoint", "/mcp", "method", exchange.getRequestMethod()));
                writeJson(exchange, error.getStatusCode(), error.getBody());
                log(IStatus.WARNING, "MCP request rejected requestId=" + context.getRequestId() + " code="
                        + error.getErrorCode().getCode() + " reason=method-not-allowed", null);
                return;
            }

            MCPServerState state = MCPServerBootstrap.getState();
            if(state.getLifecycle() != Lifecycle.RUNNING) {
                ErrorResponse error = MCPErrorResponseFactory.startupFailure(context, state);
                writeJson(exchange, error.getStatusCode(), error.getBody());
                log(IStatus.WARNING, "MCP request rejected requestId=" + context.getRequestId() + " code="
                        + error.getErrorCode().getCode() + " reason=startup-state-" + state.getLifecycleId(), null);
                return;
            }

            String rawRequest;
            try {
                rawRequest = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            }
            catch(IOException ex) {
                ErrorResponse error = MCPErrorResponseFactory.failure(context,
                        MCPErrorCode.NETWORK_IO_FAILURE,
                        null,
                        MCPErrorResponseFactory.details("endpoint", "/mcp", "stage", "read-body"));
                writeJson(exchange, error.getStatusCode(), error.getBody());
                log(IStatus.WARNING, "MCP request failed requestId=" + context.getRequestId() + " code="
                        + error.getErrorCode().getCode() + " reason=network-io", ex);
                return;
            }

            MCPRequest request = null;
            try {
                request = MCPRequest.parse(rawRequest);
            }
            catch(IllegalArgumentException ex) {
                ErrorResponse error = MCPErrorResponseFactory.failure(context,
                        MCPErrorCode.PROTOCOL_BAD_REQUEST,
                        null,
                        MCPErrorResponseFactory.details("endpoint", "/mcp", "reason", ex.getMessage()));
                writeJson(exchange, error.getStatusCode(), error.getBody());
                log(IStatus.WARNING, "MCP request rejected requestId=" + context.getRequestId() + " code="
                        + error.getErrorCode().getCode() + " reason=bad-request", ex);
                return;
            }

            if(!SUPPORTED_METHODS.contains(request.getMethod())) {
                ErrorResponse error = MCPErrorResponseFactory.failure(context,
                        MCPErrorCode.PROTOCOL_UNSUPPORTED_METHOD,
                        null,
                        MCPErrorResponseFactory.details("endpoint", "/mcp", "method", request.getMethod()));
                writeJson(exchange, error.getStatusCode(), error.getBody());
                log(IStatus.WARNING, "MCP request rejected requestId=" + context.getRequestId() + " code="
                        + error.getErrorCode().getCode() + " reason=unsupported-method method=" + request.getMethod(), null);
                return;
            }

            Object responseData = switch(request.getMethod()) {
                case "initialize", "ping" -> createPhaseOneResponse(request.getMethod());
                case "query" -> dispatchQuery(request);
                case "exportView" -> dispatchExport(request);
                case "write" -> dispatchWrite(request);
                default -> throw new IllegalStateException("Unhandled method: " + request.getMethod());
            };

            writeJson(exchange, 200, MCPResponseEnvelope.success(context, responseData).toJson());
            log(IStatus.INFO, "MCP request served requestId=" + context.getRequestId() + " method=" + request.getMethod(), null);
        }
        catch(MCPStructuredErrorException ex) {
            String requestMethod = resolveMethod(ex.getErrorCode());
            Map<String, String> details = new LinkedHashMap<>(ex.getDetails());
            details.putIfAbsent("endpoint", "/mcp");
            details.putIfAbsent("method", requestMethod);
            details.putIfAbsent("reason", ex.getMessage());
            ErrorResponse error = MCPErrorResponseFactory.failure(context,
                    ex.getErrorCode(),
                    ex.getMessage(),
                    details,
                    ex.getRetryable());
            writeJson(exchange, error.getStatusCode(), error.getBody());
            log(IStatus.WARNING, "MCP request failed requestId=" + context.getRequestId() + " code="
                    + error.getErrorCode().getCode() + " reason=structured-error method=" + requestMethod, ex);
        }
        catch(MCPQueryValidationException ex) {
            String requestMethod = resolveMethod(ex);
            ErrorResponse error = MCPErrorResponseFactory.failure(context,
                    ex.getErrorCode(),
                    ex.getMessage(),
                    MCPErrorResponseFactory.details("endpoint", "/mcp", "method", requestMethod, "reason", ex.getMessage()));
            writeJson(exchange, error.getStatusCode(), error.getBody());
            log(IStatus.WARNING, "MCP request rejected requestId=" + context.getRequestId() + " code="
                    + error.getErrorCode().getCode() + " reason=request-validation method=" + requestMethod, ex);
        }
        catch(RuntimeException ex) {
            ErrorResponse error = MCPErrorResponseFactory.failure(context,
                    MCPErrorCode.INTERNAL_UNEXPECTED,
                    null,
                    MCPErrorResponseFactory.details("endpoint", "/mcp"));
            writeJson(exchange, error.getStatusCode(), error.getBody());
            log(IStatus.ERROR, "MCP request failed requestId=" + context.getRequestId() + " code="
                    + error.getErrorCode().getCode() + " reason=unexpected-error", ex);
        }
        finally {
            RequestContext.clear();
        }
    }

    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        // 0 length enables chunked transfer for HTTP/1.1 and keeps stream semantics.
        exchange.sendResponseHeaders(statusCode, 0);
        try(OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
            outputStream.flush();
        }
    }

    private static void log(int severity, String message, Throwable throwable) {
        Bundle bundle = FrameworkUtil.getBundle(MCPStreamHandler.class);
        if(bundle == null) {
            if(throwable == null) {
                System.err.println(message);
            }
            else {
                System.err.println(message + " - " + throwable.getMessage());
            }
            return;
        }

        ILog.of(bundle).log(new Status(severity, bundle.getSymbolicName(), message, throwable));
    }

    private Object dispatchQuery(MCPRequest request) {
        MCPQueryRequest queryRequest = MCPQueryRequest.from(request);
        return queryDispatcher.dispatch(queryRequest);
    }

    private Object dispatchExport(MCPRequest request) {
        MCPExportRequest exportRequest = MCPExportRequest.from(request);
        return viewExportHandler.handle(exportRequest);
    }

    private Object dispatchWrite(MCPRequest request) {
        MCPWriteRequest writeRequest = MCPWriteRequest.from(request);
        return writeDispatcher.dispatch(writeRequest);
    }

    private Object createPhaseOneResponse(String method) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("method", method);
        data.put("mode", "single-result");
        data.put("phase", "1");
        return data;
    }

    private String resolveMethod(MCPQueryValidationException exception) {
        return resolveMethod(exception.getErrorCode());
    }

    private String resolveMethod(MCPErrorCode errorCode) {
        if(errorCode.getCategory().equals("export")) {
            return "exportView";
        }

        if(errorCode.getCategory().equals("write")) {
            return "write";
        }

        return "query";
    }
}
