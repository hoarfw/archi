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
 * Structured write request routed through {@code method=write}.
 */
@SuppressWarnings("nls")
public final class MCPWriteRequest {

    private final String modelId;
    private final MCPWriteOperation operation;
    private final Map<String, Object> params;
    private final Map<String, Object> payload;
    private final Bounds bounds;

    private MCPWriteRequest(String modelId, MCPWriteOperation operation, Map<String, Object> params, Map<String, Object> payload,
            Bounds bounds) {
        this.modelId = modelId;
        this.operation = operation;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        this.payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        this.bounds = bounds;
    }

    public static MCPWriteRequest from(MCPRequest request) {
        if(request == null) {
            throw badRequest("missing request", "request");
        }

        Map<String, Object> params = request.getParams();
        MCPWriteOperation operation = MCPWriteOperation.fromValue(params.get("operation"));
        String modelId = optionalString(params.get("modelId"), "modelId");
        Map<String, Object> payload = requireObject(params.get("payload"), "payload");
        Bounds bounds = Bounds.from(payload.get("bounds"));

        return new MCPWriteRequest(modelId, operation, params, payload, bounds);
    }

    public String getModelId() {
        return modelId;
    }

    public MCPWriteOperation getOperation() {
        return operation;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Bounds getBounds() {
        return bounds;
    }

    public String findString(String fieldName) {
        String value = optionalString(params.get(fieldName), fieldName);
        if(value != null) {
            return value;
        }

        return optionalString(payload.get(fieldName), fieldName);
    }

    private static Map<String, Object> requireObject(Object value, String fieldName) {
        if(value == null) {
            throw badRequest(fieldName + " must be an object", fieldName);
        }

        try {
            return MCPJson.requireObject(value, fieldName + " must be an object");
        }
        catch(IllegalArgumentException ex) {
            throw badRequest(ex.getMessage(), fieldName);
        }
    }

    private static String optionalString(Object value, String fieldName) {
        if(value == null) {
            return null;
        }

        if(!(value instanceof String text) || text.isBlank()) {
            throw badRequest(fieldName + " must be a non-empty string", fieldName);
        }

        return text;
    }

    private static MCPStructuredErrorException badRequest(String message, String fieldName) {
        return new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                message,
                Map.of("field", fieldName),
                Boolean.FALSE);
    }

    public static final class Bounds {
        private final double x;
        private final double y;
        private final Double width;
        private final Double height;

        private Bounds(double x, double y, Double width, Double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public static Bounds from(Object value) {
            if(value == null) {
                return null;
            }

            Map<String, Object> bounds;
            try {
                bounds = MCPJson.requireObject(value, "bounds must be an object");
            }
            catch(IllegalArgumentException ex) {
                throw badRequest(ex.getMessage(), "bounds");
            }

            double x = requireFiniteNumber(bounds.get("x"), "bounds.x");
            double y = requireFiniteNumber(bounds.get("y"), "bounds.y");
            Double width = optionalPositiveNumber(bounds.get("width"), "bounds.width");
            Double height = optionalPositiveNumber(bounds.get("height"), "bounds.height");

            return new Bounds(x, y, width, height);
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public Double getWidth() {
            return width;
        }

        public Double getHeight() {
            return height;
        }

        private static double requireFiniteNumber(Object value, String fieldName) {
            if(!(value instanceof Number number)) {
                throw badRequest(fieldName + " must be a number", fieldName);
            }

            double result = number.doubleValue();
            if(!Double.isFinite(result)) {
                throw badRequest(fieldName + " must be a finite number", fieldName);
            }

            return result;
        }

        private static Double optionalPositiveNumber(Object value, String fieldName) {
            if(value == null) {
                return null;
            }

            double result = requireFiniteNumber(value, fieldName);
            if(result <= 0d) {
                throw badRequest(fieldName + " must be greater than 0", fieldName);
            }

            return Double.valueOf(result);
        }
    }
}
