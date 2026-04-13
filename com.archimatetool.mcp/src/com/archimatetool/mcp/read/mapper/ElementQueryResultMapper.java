/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.read.mapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.archimatetool.mcp.read.ConceptQuerySupport;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IProperty;

/**
 * Projects an Archi element into the READ-03 concept-level DTO.
 */
@SuppressWarnings("nls")
public class ElementQueryResultMapper {

    public Map<String, Object> map(IArchimateElement element) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("elementId", element.getId());
        summary.put("type", ConceptQuerySupport.toTypeToken(element));
        summary.put("name", element.getName());
        summary.put("documentation", element.getDocumentation());
        summary.put("properties", mapProperties(element.getProperties()));
        summary.put("sourceRelationshipIds", mapRelationshipIds(element.getSourceRelationships()));
        summary.put("targetRelationshipIds", mapRelationshipIds(element.getTargetRelationships()));
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

    private List<String> mapRelationshipIds(List<IArchimateRelationship> relationships) {
        List<String> ids = new ArrayList<>(relationships.size());

        for(IArchimateRelationship relationship : relationships) {
            if(relationship.getId() != null) {
                ids.add(relationship.getId());
            }
        }

        ids.sort(Comparator.naturalOrder());
        return List.copyOf(ids);
    }
}
