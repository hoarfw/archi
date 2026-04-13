/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.read;

import java.util.LinkedHashMap;
import java.util.Map;

import com.archimatetool.mcp.contract.MCPQueryRequest;
import com.archimatetool.mcp.read.mapper.ModelSummaryMapper;
import com.archimatetool.model.IArchimateModel;

/**
 * Handles `queryType=model` requests.
 */
@SuppressWarnings("nls")
public class ModelQueryHandler {

    private final UIThreadReadExecutor executor;
    private final OpenModelResolver modelResolver;
    private final ModelSummaryMapper mapper;

    public ModelQueryHandler(UIThreadReadExecutor executor, OpenModelResolver modelResolver, ModelSummaryMapper mapper) {
        this.executor = executor;
        this.modelResolver = modelResolver;
        this.mapper = mapper;
    }

    public Object handle(MCPQueryRequest request) {
        return executor.execute(() -> {
            IArchimateModel model = modelResolver.resolve(request.getModelId());
            QueryExecutionContext context = QueryExecutionContext.create(model);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("queryType", request.getQueryType().getValue());
            response.put("item", mapper.map(context));
            return response;
        });
    }
}
