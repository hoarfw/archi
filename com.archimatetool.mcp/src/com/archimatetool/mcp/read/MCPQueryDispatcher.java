/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.read;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import com.archimatetool.mcp.contract.MCPQueryRequest;
import com.archimatetool.mcp.read.mapper.ElementQueryResultMapper;
import com.archimatetool.mcp.read.mapper.ModelSummaryMapper;
import com.archimatetool.mcp.read.mapper.RelationshipQueryResultMapper;
import com.archimatetool.mcp.read.mapper.ViewConnectionContextMapper;
import com.archimatetool.mcp.read.mapper.ViewObjectContextMapper;
import com.archimatetool.mcp.read.mapper.ViewSummaryMapper;

/**
 * Query-type router for MCP read requests.
 */
@SuppressWarnings("nls")
public class MCPQueryDispatcher {

    private final ModelQueryHandler modelQueryHandler;
    private final ViewQueryHandler viewQueryHandler;
    private final ElementQueryHandler elementQueryHandler;
    private final RelationshipQueryHandler relationshipQueryHandler;

    public MCPQueryDispatcher() {
        UIThreadReadExecutor executor = new UIThreadReadExecutor();
        OpenModelResolver modelResolver = new OpenModelResolver();
        ConceptQuerySupport conceptQuerySupport = new ConceptQuerySupport();
        modelQueryHandler = new ModelQueryHandler(executor, modelResolver, new ModelSummaryMapper());
        viewQueryHandler = new ViewQueryHandler(executor, modelResolver, new ViewSummaryMapper());
        elementQueryHandler = new ElementQueryHandler(executor, modelResolver, conceptQuerySupport,
                new ElementQueryResultMapper(), new ViewObjectContextMapper());
        relationshipQueryHandler = new RelationshipQueryHandler(executor, modelResolver, conceptQuerySupport,
                new RelationshipQueryResultMapper(), new ViewConnectionContextMapper());
    }

    public Object dispatch(MCPQueryRequest request) {
        return switch(request.getQueryType()) {
            case MODEL -> modelQueryHandler.handle(request);
            case VIEWS -> viewQueryHandler.handle(request);
            case ELEMENTS -> elementQueryHandler.handle(request);
            case RELATIONSHIPS -> relationshipQueryHandler.handle(request);
        };
    }

    private Object createPlaceholderListResponse(MCPQueryRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("queryType", request.getQueryType().getValue());
        response.put("model", createModelEcho(request));
        response.put("items", new ArrayList<>());
        response.put("total", Integer.valueOf(0));
        response.put("offset", Integer.valueOf(request.getOffset()));
        response.put("limit", Integer.valueOf(request.getLimit()));
        response.put("hasMore", Boolean.FALSE);
        return response;
    }

    private Object createModelEcho(MCPQueryRequest request) {
        Map<String, Object> model = new LinkedHashMap<>();
        if(request.getModelId() != null) {
            model.put("modelId", request.getModelId());
        }

        model.put("query", request.getQueryType().getValue());
        return model;
    }
}
