/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.read.mapper;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.mcp.read.QueryExecutionContext;
import com.archimatetool.mcp.read.EditorModelAccess;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;

/**
 * Projects a live Archi model into the READ-01 summary DTO.
 */
@SuppressWarnings("nls")
public class ModelSummaryMapper {

    private final EditorModelAccess editorModelAccess = new EditorModelAccess();

    public Map<String, Object> map(QueryExecutionContext context) {
        IArchimateModel model = context.getModel();
        File file = model.getFile();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("modelId", model.getId());
        summary.put("name", model.getName());
        summary.put("filePath", file == null ? null : file.getAbsolutePath());
        summary.put("isFileBacked", Boolean.valueOf(file != null));
        summary.put("isDirty", Boolean.valueOf(editorModelAccess.isModelDirty(model)));
        summary.put("version", model.getVersion());
        summary.put("purpose", model.getPurpose());
        summary.put("viewCount", Integer.valueOf(context.getDiagramModels().size()));
        summary.put("elementCount", Integer.valueOf(countObjects(model, IArchimateElement.class)));
        summary.put("relationshipCount", Integer.valueOf(countObjects(model, IArchimateRelationship.class)));
        return summary;
    }

    private int countObjects(IArchimateModel model, Class<?> type) {
        int count = 0;

        for(Iterator<EObject> iter = model.eAllContents(); iter.hasNext();) {
            if(type.isInstance(iter.next())) {
                count++;
            }
        }

        return count;
    }
}
