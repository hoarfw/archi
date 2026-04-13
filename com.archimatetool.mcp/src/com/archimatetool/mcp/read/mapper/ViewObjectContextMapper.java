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

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.mcp.read.ConceptQuerySupport;
import com.archimatetool.mcp.read.QueryExecutionContext;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelObject;

/**
 * Maps every live diagram-object occurrence of an element into READ-03 view context DTOs.
 */
@SuppressWarnings("nls")
public class ViewObjectContextMapper {

    public List<Map<String, Object>> map(QueryExecutionContext context, IArchimateElement element) {
        List<Map<String, Object>> results = new ArrayList<>();

        for(IDiagramModelArchimateObject object : element.getReferencingDiagramObjects()) {
            IDiagramModel diagramModel = object.getDiagramModel();
            if(diagramModel == null || !context.getDiagramIdMap().containsKey(diagramModel.getId())) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("viewId", diagramModel.getId());
            item.put("viewName", diagramModel.getName());
            item.put("objectId", object.getId());
            item.put("objectName", object.getName());
            item.put("bounds", mapBounds(object.getBounds()));
            item.put("parentObjectId", getParentObjectId(object));
            item.put("parentType", getParentType(object));
            results.add(item);
        }

        return List.copyOf(results);
    }

    private Map<String, Object> mapBounds(IBounds bounds) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("x", Integer.valueOf(bounds == null ? 0 : bounds.getX()));
        item.put("y", Integer.valueOf(bounds == null ? 0 : bounds.getY()));
        item.put("width", Integer.valueOf(bounds == null ? 0 : bounds.getWidth()));
        item.put("height", Integer.valueOf(bounds == null ? 0 : bounds.getHeight()));
        return item;
    }

    private String getParentObjectId(IDiagramModelObject object) {
        EObject parent = object.eContainer();
        if(parent instanceof IDiagramModelObject parentObject) {
            return parentObject.getId();
        }

        return null;
    }

    private String getParentType(IDiagramModelObject object) {
        EObject parent = object.eContainer();
        if(parent instanceof IDiagramModelArchimateObject parentObject && parentObject.getArchimateElement() != null) {
            return ConceptQuerySupport.toTypeToken(parentObject.getArchimateElement());
        }

        if(parent instanceof IDiagramModelObject parentObject) {
            return ConceptQuerySupport.toTypeToken(parentObject);
        }

        return null;
    }
}
