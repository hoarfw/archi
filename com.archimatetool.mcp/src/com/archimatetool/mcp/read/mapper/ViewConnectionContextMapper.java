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
import com.archimatetool.mcp.read.QueryExecutionContext;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IConnectable;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IDiagramModelConnection;

/**
 * Maps every live diagram-connection occurrence of a relationship into READ-04 view context DTOs.
 */
@SuppressWarnings("nls")
public class ViewConnectionContextMapper {

    public List<Map<String, Object>> map(QueryExecutionContext context, IArchimateRelationship relationship) {
        List<Map<String, Object>> results = new ArrayList<>();

        for(IDiagramModelArchimateConnection connection : relationship.getReferencingDiagramConnections()) {
            IDiagramModel diagramModel = connection.getDiagramModel();
            if(diagramModel == null || !context.getDiagramIdMap().containsKey(diagramModel.getId())) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("viewId", diagramModel.getId());
            item.put("viewName", diagramModel.getName());
            item.put("connectionId", connection.getId());
            item.put("connectionType", Integer.valueOf(connection.getType()));
            item.put("bendpoints", mapBendpoints(connection.getBendpoints()));
            item.put("sourceRef", mapConnectable(connection.getSource()));
            item.put("targetRef", mapConnectable(connection.getTarget()));
            results.add(item);
        }

        return List.copyOf(results);
    }

    private List<Map<String, Object>> mapBendpoints(List<IDiagramModelBendpoint> bendpoints) {
        List<Map<String, Object>> results = new ArrayList<>(bendpoints.size());

        for(IDiagramModelBendpoint bendpoint : bendpoints) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("startX", Integer.valueOf(bendpoint.getStartX()));
            item.put("startY", Integer.valueOf(bendpoint.getStartY()));
            item.put("endX", Integer.valueOf(bendpoint.getEndX()));
            item.put("endY", Integer.valueOf(bendpoint.getEndY()));
            results.add(item);
        }

        return List.copyOf(results);
    }

    private Map<String, Object> mapConnectable(IConnectable connectable) {
        if(connectable == null) {
            return null;
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("kind", connectable instanceof IDiagramModelConnection ? "connection" : "object");
        item.put("refId", connectable.getId());
        item.put("refName", connectable.getName());

        IArchimateConcept concept = connectable instanceof IDiagramModelArchimateComponent archimateComponent
                ? archimateComponent.getArchimateConcept()
                : null;
        item.put("conceptId", concept == null ? null : concept.getId());
        item.put("conceptType", concept == null ? null : ConceptQuerySupport.toTypeToken(concept));
        item.put("conceptName", concept == null ? null : concept.getName());
        return item;
    }
}
