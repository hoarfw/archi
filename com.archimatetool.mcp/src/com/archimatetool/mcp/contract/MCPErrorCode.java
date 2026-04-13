/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.contract;

/**
 * Stable MCP error codes grouped by category for machine parsing.
 */
@SuppressWarnings("nls")
public enum MCPErrorCode {

    STARTUP_NOT_RUNNING("MCP-STARTUP-NOT-RUNNING", "startup", 503, "MCP service is not running"),
    STARTUP_PORT_IN_USE("MCP-STARTUP-PORT-IN-USE", "startup", 503, "MCP startup failed because the configured port is in use"),
    STARTUP_BIND_FAILED("MCP-STARTUP-BIND-FAILED", "startup", 503, "MCP startup failed while binding to the configured socket"),
    STARTUP_LOOPBACK_REQUIRED("MCP-STARTUP-LOOPBACK-REQUIRED", "startup", 400, "MCP startup requires a loopback host"),

    NETWORK_IO_FAILURE("MCP-NETWORK-IO-FAILURE", "network", 502, "Network I/O failed while processing MCP request"),

    PROTOCOL_BAD_REQUEST("MCP-PROTOCOL-BAD-REQUEST", "protocol", 400, "Request must include a valid JSON body with method"),
    PROTOCOL_UNSUPPORTED_HTTP_METHOD("MCP-PROTOCOL-UNSUPPORTED-HTTP-METHOD", "protocol", 405, "HTTP method is not supported for this endpoint"),
    PROTOCOL_UNSUPPORTED_METHOD("MCP-PROTOCOL-UNSUPPORTED-METHOD", "protocol", 400, "MCP method is not supported in phase-1"),
    PROTOCOL_BAD_QUERY("MCP-PROTOCOL-BAD-QUERY", "protocol", 400, "MCP query params are invalid"),
    PROTOCOL_UNSUPPORTED_QUERY_TYPE("MCP-PROTOCOL-UNSUPPORTED-QUERY-TYPE", "protocol", 400, "MCP queryType is not supported"),
    EXPORT_BAD_REQUEST("MCP-EXPORT-BAD-REQUEST", "export", 400, "MCP export params are invalid"),
    EXPORT_VIEW_NOT_FOUND("MCP-EXPORT-VIEW-NOT-FOUND", "export", 404, "Requested view could not be found"),
    EXPORT_BUSY("MCP-EXPORT-BUSY", "export", 503, "Another export is already in progress"),
    EXPORT_TIMEOUT("MCP-EXPORT-TIMEOUT", "export", 504, "View export exceeded the allowed time budget"),
    EXPORT_RENDER_FAILED("MCP-EXPORT-RENDER-FAILED", "export", 500, "Failed to render the requested view"),
    EXPORT_TEMP_FILE_FAILED("MCP-EXPORT-TEMP-FILE-FAILED", "export", 500, "Failed to persist the exported image"),
    WRITE_BAD_REQUEST("MCP-WRITE-BAD-REQUEST", "write", 400, "MCP write params are invalid"),
    WRITE_UNSUPPORTED_OPERATION("MCP-WRITE-UNSUPPORTED-OPERATION", "write", 400, "MCP write operation is not supported"),
    WRITE_OPERATION_NOT_IMPLEMENTED("MCP-WRITE-OPERATION-NOT-IMPLEMENTED", "write", 501,
            "MCP write operation is not implemented yet"),
    WRITE_MODEL_NOT_FOUND("MCP-WRITE-MODEL-NOT-FOUND", "write", 404, "Requested model could not be found"),
    WRITE_VIEW_NOT_FOUND("MCP-WRITE-VIEW-NOT-FOUND", "write", 404, "Requested view could not be found"),
    WRITE_ELEMENT_NOT_FOUND("MCP-WRITE-ELEMENT-NOT-FOUND", "write", 404, "Requested element could not be found"),
    WRITE_RELATIONSHIP_NOT_FOUND("MCP-WRITE-RELATIONSHIP-NOT-FOUND", "write", 404,
            "Requested relationship could not be found"),
    WRITE_OBJECT_NOT_FOUND("MCP-WRITE-OBJECT-NOT-FOUND", "write", 404, "Requested diagram object could not be found"),
    WRITE_COMMAND_STACK_UNAVAILABLE("MCP-WRITE-COMMAND-STACK-UNAVAILABLE", "write", 409,
            "Write command stack is not available for the target model"),

    INTERNAL_UNEXPECTED("MCP-INTERNAL-UNEXPECTED", "internal", 500, "Unexpected MCP server error");

    private final String code;
    private final String category;
    private final int httpStatus;
    private final String defaultMessage;

    MCPErrorCode(String code, String category, int httpStatus, String defaultMessage) {
        this.code = code;
        this.category = category;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getCategory() {
        return category;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public static MCPErrorCode fromStartupReason(String reason) {
        if(reason == null || reason.isBlank() || "not-started".equals(reason)) {
            return STARTUP_NOT_RUNNING;
        }

        return switch(reason) {
            case "port-in-use" -> STARTUP_PORT_IN_USE;
            case "bind-failed" -> STARTUP_BIND_FAILED;
            case "loopback-only" -> STARTUP_LOOPBACK_REQUIRED;
            case "unexpected-error" -> INTERNAL_UNEXPECTED;
            default -> STARTUP_NOT_RUNNING;
        };
    }
}
