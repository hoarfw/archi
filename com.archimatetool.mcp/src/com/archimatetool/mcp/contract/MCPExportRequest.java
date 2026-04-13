/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.contract;

import java.util.Map;

/**
 * Structured export request with normalized scale bounds.
 */
@SuppressWarnings("nls")
public final class MCPExportRequest {

    public static final double DEFAULT_SCALE = 1.0d;
    public static final double MAX_SCALE = 5.0d;

    private final String modelId;
    private final String viewId;
    private final double scale;

    private MCPExportRequest(String modelId, String viewId, double scale) {
        this.modelId = modelId;
        this.viewId = viewId;
        this.scale = scale;
    }

    public static MCPExportRequest from(MCPRequest request) {
        if(request == null) {
            throw new MCPQueryValidationException(MCPErrorCode.EXPORT_BAD_REQUEST, "missing request");
        }

        Map<String, Object> params = request.getParams();
        if(params == null) {
            throw new MCPQueryValidationException(MCPErrorCode.EXPORT_BAD_REQUEST, "missing params");
        }

        String modelId = optionalString(params.get("modelId"), "modelId");
        String viewId = requiredString(params.get("viewId"), "viewId");
        double scale = normalizeScale(params.get("scale"));

        return new MCPExportRequest(modelId, viewId, scale);
    }

    public String getModelId() {
        return modelId;
    }

    public String getViewId() {
        return viewId;
    }

    public double getScale() {
        return scale;
    }

    private static String optionalString(Object value, String fieldName) {
        if(value == null) {
            return null;
        }

        return requiredString(value, fieldName);
    }

    private static String requiredString(Object value, String fieldName) {
        if(!(value instanceof String text) || text.isBlank()) {
            throw new MCPQueryValidationException(MCPErrorCode.EXPORT_BAD_REQUEST, fieldName + " must be a non-empty string");
        }

        return text;
    }

    private static double normalizeScale(Object value) {
        if(value == null) {
            return DEFAULT_SCALE;
        }

        if(!(value instanceof Number number)) {
            throw new MCPQueryValidationException(MCPErrorCode.EXPORT_BAD_REQUEST, "scale must be a number");
        }

        double scale = number.doubleValue();
        if(!Double.isFinite(scale) || scale <= 0) {
            throw new MCPQueryValidationException(MCPErrorCode.EXPORT_BAD_REQUEST, "scale must be greater than 0");
        }

        if(scale > MAX_SCALE) {
            throw new MCPQueryValidationException(MCPErrorCode.EXPORT_BAD_REQUEST,
                    "scale must be less than or equal to " + MAX_SCALE);
        }

        return scale;
    }
}
