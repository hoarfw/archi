/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structured query request with normalized pagination and filters.
 */
@SuppressWarnings("nls")
public final class MCPQueryRequest {

    public static final int DEFAULT_LIMIT = 50;
    public static final int DEFAULT_OFFSET = 0;

    private static final Set<String> FILTERS_NONE = Set.of();
    private static final Set<String> FILTERS_ELEMENTS = Set.of("text", "searchIn", "types", "propertyKeys", "viewIds");
    private static final Set<String> FILTERS_RELATIONSHIPS = Set.of("text", "searchIn", "types", "propertyKeys", "viewIds",
            "sourceIds", "targetIds");
    private static final Set<String> SEARCH_IN_FIELDS = Set.of("name", "documentation", "propertyValue");

    private final MCPQueryType queryType;
    private final String modelId;
    private final Map<String, Object> filters;
    private final int limit;
    private final int offset;

    private MCPQueryRequest(MCPQueryType queryType, String modelId, Map<String, Object> filters, int limit, int offset) {
        this.queryType = queryType;
        this.modelId = modelId;
        this.filters = Collections.unmodifiableMap(new LinkedHashMap<>(filters));
        this.limit = limit;
        this.offset = offset;
    }

    public static MCPQueryRequest from(MCPRequest request) {
        if(request == null) {
            throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY, "missing request");
        }

        Map<String, Object> params = request.getParams();
        if(params == null || params.isEmpty()) {
            throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY, "missing params");
        }

        Object queryTypeValue = params.get("queryType");
        if(!(queryTypeValue instanceof String queryTypeText) || queryTypeText.isBlank()) {
            throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY, "missing queryType");
        }

        MCPQueryType queryType = MCPQueryType.fromValue(queryTypeText);
        String modelId = optionalString(params.get("modelId"), "modelId");
        Map<String, Object> filters = normalizeFilters(queryType, params.get("filters"));
        int limit = normalizeNonNegativeInt(params.get("limit"), "limit", DEFAULT_LIMIT);
        int offset = normalizeNonNegativeInt(params.get("offset"), "offset", DEFAULT_OFFSET);

        return new MCPQueryRequest(queryType, modelId, filters, limit, offset);
    }

    public MCPQueryType getQueryType() {
        return queryType;
    }

    public String getModelId() {
        return modelId;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    private static Map<String, Object> normalizeFilters(MCPQueryType queryType, Object filtersValue) {
        if(filtersValue == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> rawFilters;
        try {
            rawFilters = MCPJson.requireObject(filtersValue, "filters must be an object");
        }
        catch(IllegalArgumentException ex) {
            throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY, ex.getMessage());
        }
        Set<String> allowedFilters = getAllowedFilters(queryType);
        Map<String, Object> normalized = new LinkedHashMap<>();

        for(Map.Entry<String, Object> entry : rawFilters.entrySet()) {
            String key = entry.getKey();

            if(!allowedFilters.contains(key)) {
                throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY, "unknown filter field: " + key);
            }

            normalized.put(key, normalizeFilterValue(key, entry.getValue()));
        }

        return normalized;
    }

    private static Set<String> getAllowedFilters(MCPQueryType queryType) {
        return switch(queryType) {
            case MODEL, VIEWS -> FILTERS_NONE;
            case ELEMENTS -> FILTERS_ELEMENTS;
            case RELATIONSHIPS -> FILTERS_RELATIONSHIPS;
        };
    }

    private static Object normalizeFilterValue(String key, Object value) {
        return switch(key) {
            case "text" -> requireString(value, key);
            case "searchIn" -> normalizeSearchIn(value);
            case "types", "propertyKeys", "viewIds", "sourceIds", "targetIds" -> normalizeStringList(value, key);
            default -> value;
        };
    }

    private static List<String> normalizeSearchIn(Object value) {
        List<String> fields = normalizeStringList(value, "searchIn");
        Set<String> deduplicated = new LinkedHashSet<>();

        for(String field : fields) {
            if(!SEARCH_IN_FIELDS.contains(field)) {
                throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY, "unsupported searchIn field: " + field);
            }

            deduplicated.add(field);
        }

        return List.copyOf(deduplicated);
    }

    private static List<String> normalizeStringList(Object value, String fieldName) {
        if(!(value instanceof List<?> list)) {
            throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY, fieldName + " must be an array");
        }

        List<String> result = new ArrayList<>(list.size());

        for(Object item : list) {
            if(!(item instanceof String text) || text.isBlank()) {
                throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY, fieldName + " entries must be non-empty strings");
            }

            result.add(text);
        }

        return List.copyOf(result);
    }

    private static String optionalString(Object value, String fieldName) {
        if(value == null) {
            return null;
        }

        return requireString(value, fieldName);
    }

    private static String requireString(Object value, String fieldName) {
        if(!(value instanceof String text) || text.isBlank()) {
            throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY, fieldName + " must be a non-empty string");
        }

        return text;
    }

    private static int normalizeNonNegativeInt(Object value, String fieldName, int defaultValue) {
        if(value == null) {
            return defaultValue;
        }

        if(!(value instanceof Number number)) {
            throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY, fieldName + " must be an integer");
        }

        double doubleValue = number.doubleValue();
        int intValue = number.intValue();

        if(Double.compare(doubleValue, intValue) != 0 || intValue < 0) {
            throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_BAD_QUERY, fieldName + " must be a non-negative integer");
        }

        return intValue;
    }
}
