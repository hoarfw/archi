/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.contract;

/**
 * Supported MCP read query types.
 */
@SuppressWarnings("nls")
public enum MCPQueryType {

    MODEL("model", false),
    VIEWS("views", true),
    ELEMENTS("elements", true),
    RELATIONSHIPS("relationships", true);

    private final String value;
    private final boolean listQuery;

    MCPQueryType(String value, boolean listQuery) {
        this.value = value;
        this.listQuery = listQuery;
    }

    public String getValue() {
        return value;
    }

    public boolean isListQuery() {
        return listQuery;
    }

    public static MCPQueryType fromValue(String value) {
        for(MCPQueryType queryType : values()) {
            if(queryType.value.equals(value)) {
                return queryType;
            }
        }

        throw new MCPQueryValidationException(MCPErrorCode.PROTOCOL_UNSUPPORTED_QUERY_TYPE, "unknown queryType: " + value);
    }
}
