/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.read.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.archimatetool.mcp.read.ConceptQuerySupport;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IProperty;

/**
 * Projects an Archi relationship into the READ-04 concept-level DTO.
 */
@SuppressWarnings("nls")
public class RelationshipQueryResultMapper {

    public Map<String, Object> map(IArchimateRelationship relationship) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("relationshipId", relationship.getId());
        summary.put("type", ConceptQuerySupport.toTypeToken(relationship));
        summary.put("name", relationship.getName());
        summary.put("documentation", relationship.getDocumentation());
        summary.put("properties", mapProperties(relationship.getProperties()));
        summary.put("source", mapConcept(relationship.getSource()));
        summary.put("target", mapConcept(relationship.getTarget()));
        return summary;
    }

    private List<Map<String, Object>> mapProperties(List<IProperty> properties) {
        List<Map<String, Object>> results = new ArrayList<>(properties.size());

        for(IProperty property : properties) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", property.getKey());
            item.put("value", property.getValue());
            results.add(item);
        }

        return List.copyOf(results);
    }

    private Map<String, Object> mapConcept(IArchimateConcept concept) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", concept == null ? null : concept.getId());
        item.put("type", concept == null ? null : ConceptQuerySupport.toTypeToken(concept));
        item.put("name", concept == null ? null : concept.getName());
        return item;
    }
}
