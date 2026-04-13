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
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.archimatetool.mcp.contract.MCPQueryRequest;
import com.archimatetool.mcp.read.mapper.RelationshipQueryResultMapper;
import com.archimatetool.mcp.read.mapper.ViewConnectionContextMapper;
import com.archimatetool.model.IArchimateRelationship;

/**
 * Handles `queryType=relationships` requests.
 */
@SuppressWarnings("nls")
public class RelationshipQueryHandler {

    private final UIThreadReadExecutor executor;
    private final OpenModelResolver modelResolver;
    private final ConceptQuerySupport conceptQuerySupport;
    private final RelationshipQueryResultMapper relationshipMapper;
    private final ViewConnectionContextMapper viewContextMapper;

    public RelationshipQueryHandler(UIThreadReadExecutor executor, OpenModelResolver modelResolver,
            ConceptQuerySupport conceptQuerySupport, RelationshipQueryResultMapper relationshipMapper,
            ViewConnectionContextMapper viewContextMapper) {
        this.executor = executor;
        this.modelResolver = modelResolver;
        this.conceptQuerySupport = conceptQuerySupport;
        this.relationshipMapper = relationshipMapper;
        this.viewContextMapper = viewContextMapper;
    }

    public Object handle(MCPQueryRequest request) {
        return executor.execute(() -> {
            var model = modelResolver.resolve(request.getModelId());
            QueryExecutionContext context = QueryExecutionContext.create(model);
            QueryResultPage<Map<String, Object>> page = conceptQuerySupport.query(createRequestView(request),
                    collectRelationships(context, request),
                    relationship -> relationshipMapper.map(relationship),
                    relationship -> viewContextMapper.map(context, relationship));

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

    private Collection<IArchimateRelationship> collectRelationships(QueryExecutionContext context, MCPQueryRequest request) {
        Set<String> sourceIds = getIdFilter(request.getFilters(), "sourceIds");
        Set<String> targetIds = getIdFilter(request.getFilters(), "targetIds");
        Collection<IArchimateRelationship> relationships = new LinkedHashSet<>();

        for(Object object : context.getObjectIdMap().values()) {
            if(object instanceof IArchimateRelationship relationship && matchesEndpoints(relationship, sourceIds, targetIds)) {
                relationships.add(relationship);
            }
        }

        return new ArrayList<>(relationships);
    }

    private boolean matchesEndpoints(IArchimateRelationship relationship, Set<String> sourceIds, Set<String> targetIds) {
        if(relationship == null) {
            return false;
        }

        String sourceId = relationship.getSource() == null ? null : relationship.getSource().getId();
        if(!sourceIds.isEmpty() && (sourceId == null || !sourceIds.contains(sourceId))) {
            return false;
        }

        String targetId = relationship.getTarget() == null ? null : relationship.getTarget().getId();
        return targetIds.isEmpty() || (targetId != null && targetIds.contains(targetId));
    }

    @SuppressWarnings("unchecked")
    private Set<String> getIdFilter(Map<String, Object> filters, String key) {
        Object value = filters.get(key);
        if(value == null) {
            return Set.of();
        }

        return Set.copyOf((List<String>)value);
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
