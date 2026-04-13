/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.read.mapper;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IDiagramModelReference;

/**
 * Projects live diagram models into the READ-02 summary DTO.
 */
@SuppressWarnings("nls")
public class ViewSummaryMapper {

    public Map<String, Object> map(IDiagramModel diagramModel) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("viewId", diagramModel.getId());
        summary.put("name", diagramModel.getName());
        summary.put("type", diagramModel.eClass().getName());
        summary.put("documentation", diagramModel.getDocumentation());
        summary.put("propertyCount", Integer.valueOf(diagramModel.getProperties().size()));
        summary.put("childCount", Integer.valueOf(countDiagramObjects(diagramModel)));
        summary.put("hasViewReference", Boolean.valueOf(hasViewReference(diagramModel)));
        return summary;
    }

    private int countDiagramObjects(IDiagramModel diagramModel) {
        int count = 0;

        for(Iterator<EObject> iter = diagramModel.eAllContents(); iter.hasNext();) {
            if(iter.next() instanceof IDiagramModelObject) {
                count++;
            }
        }

        return count;
    }

    private boolean hasViewReference(IDiagramModel diagramModel) {
        if(diagramModel.getArchimateModel() == null) {
            return false;
        }

        for(Iterator<EObject> iter = diagramModel.getArchimateModel().getFolder(FolderType.DIAGRAMS).eAllContents(); iter.hasNext();) {
            EObject object = iter.next();
            if(object instanceof IDiagramModelReference reference && reference.getReferencedModel() == diagramModel) {
                return true;
            }
        }

        return false;
    }
}
