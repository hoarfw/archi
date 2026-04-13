/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.read;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import com.archimatetool.mcp.contract.MCPQueryRequest;
import com.archimatetool.mcp.read.mapper.ElementQueryResultMapper;
import com.archimatetool.mcp.read.mapper.ViewObjectContextMapper;
import com.archimatetool.model.IArchimateElement;

/**
 * Handles `queryType=elements` requests.
 */
@SuppressWarnings("nls")
public class ElementQueryHandler {

    private final UIThreadReadExecutor executor;
    private final OpenModelResolver modelResolver;
    private final ConceptQuerySupport conceptQuerySupport;
    private final ElementQueryResultMapper elementMapper;
    private final ViewObjectContextMapper viewContextMapper;

    public ElementQueryHandler(UIThreadReadExecutor executor, OpenModelResolver modelResolver,
            ConceptQuerySupport conceptQuerySupport, ElementQueryResultMapper elementMapper,
            ViewObjectContextMapper viewContextMapper) {
        this.executor = executor;
        this.modelResolver = modelResolver;
        this.conceptQuerySupport = conceptQuerySupport;
        this.elementMapper = elementMapper;
        this.viewContextMapper = viewContextMapper;
    }

    public Object handle(MCPQueryRequest request) {
        return executor.execute(() -> {
            var model = modelResolver.resolve(request.getModelId());
            QueryExecutionContext context = QueryExecutionContext.create(model);
            QueryResultPage<Map<String, Object>> page = conceptQuerySupport.query(createRequestView(request),
                    collectElements(context),
                    element -> elementMapper.map(element),
                    element -> viewContextMapper.map(context, element));

            Map<String, Object> modelSummary = new LinkedHashMap<>();
            modelSummary.put("modelId", model.getId());
            modelSummary.put("name", model.getName());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("queryType", request.getQueryType().getValue());
            response.put("model", modelSummary);
            response.put("items", page.getItems());
            response.put("total", Integer.valueOf(page.getTotal()));
            response.put("offset", Integer.valueOf(page.getOffset()));
            response.put("limit", Integer.valueOf(page.getLimit()));
            response.put("hasMore", Boolean.valueOf(page.hasMore()));
            return response;
        });
    }

    private Collection<IArchimateElement> collectElements(QueryExecutionContext context) {
        Collection<IArchimateElement> elements = new LinkedHashSet<>();

        for(Object object : context.getObjectIdMap().values()) {
            if(object instanceof IArchimateElement element) {
                elements.add(element);
            }
        }

        return new ArrayList<>(elements);
    }

    private ConceptQuerySupport.MCPQueryRequestView createRequestView(MCPQueryRequest request) {
        return new ConceptQuerySupport.MCPQueryRequestView() {
            @Override
            public Map<String, Object> getFilters() {
                return request.getFilters();
            }

            @Override
            public int getLimit() {
                return request.getLimit();
            }

            @Override
            public int getOffset() {
                return request.getOffset();
            }
        };
    }
}
