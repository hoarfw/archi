/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.read;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.archimatetool.mcp.contract.MCPQueryRequest;
import com.archimatetool.mcp.read.mapper.ViewSummaryMapper;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;

/**
 * Handles `queryType=views` requests.
 */
@SuppressWarnings("nls")
public class ViewQueryHandler {

    private final UIThreadReadExecutor executor;
    private final OpenModelResolver modelResolver;
    private final ViewSummaryMapper mapper;

    public ViewQueryHandler(UIThreadReadExecutor executor, OpenModelResolver modelResolver, ViewSummaryMapper mapper) {
        this.executor = executor;
        this.modelResolver = modelResolver;
        this.mapper = mapper;
    }

    public Object handle(MCPQueryRequest request) {
        return executor.execute(() -> {
            IArchimateModel model = modelResolver.resolve(request.getModelId());
            QueryExecutionContext context = QueryExecutionContext.create(model);
            List<IDiagramModel> diagramModels = context.getDiagramModels();
            int total = diagramModels.size();
            int start = Math.min(request.getOffset(), total);
            int end = Math.min(start + request.getLimit(), total);
            List<Object> items = new ArrayList<>(Math.max(0, end - start));

            for(int i = start; i < end; i++) {
                items.add(mapper.map(diagramModels.get(i)));
            }

            Map<String, Object> modelSummary = new LinkedHashMap<>();
            modelSummary.put("modelId", model.getId());
            modelSummary.put("name", model.getName());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("queryType", request.getQueryType().getValue());
            response.put("model", modelSummary);
            response.put("items", items);
            response.put("total", Integer.valueOf(total));
            response.put("offset", Integer.valueOf(request.getOffset()));
            response.put("limit", Integer.valueOf(request.getLimit()));
            response.put("hasMore", Boolean.valueOf(end < total));
            return response;
        });
    }
}
