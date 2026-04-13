/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.read;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.mcp.bootstrap.MCPReadTelemetry;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.util.ArchimateModelUtils;

/**
 * Per-request read substrate that caches live-model indexes inside the UI thread.
 */
@SuppressWarnings("nls")
public final class QueryExecutionContext {

    private final IArchimateModel model;
    private final List<IDiagramModel> diagramModels;
    private final Map<String, EObject> objectIdMap;
    private final Map<String, IDiagramModel> diagramIdMap;

    private QueryExecutionContext(IArchimateModel model, List<IDiagramModel> diagramModels, Map<String, EObject> objectIdMap,
            Map<String, IDiagramModel> diagramIdMap) {
        this.model = model;
        this.diagramModels = diagramModels;
        this.objectIdMap = objectIdMap;
        this.diagramIdMap = diagramIdMap;
    }

    public static QueryExecutionContext create(IArchimateModel model) {
        if(model == null) {
            throw new IllegalArgumentException("model must not be null");
        }

        MCPReadTelemetry.recordContextBuild(UIThreadReadExecutor.isInUiSafeRuntime(), Thread.currentThread().getName(), model.getId());

        List<IDiagramModel> diagramModels = List.copyOf(model.getDiagramModels());
        Map<String, EObject> objectIdMap = Collections.unmodifiableMap(new LinkedHashMap<>(ArchimateModelUtils.getObjectIDMap(model)));
        Map<String, IDiagramModel> diagramIdMap = new LinkedHashMap<>();

        for(IDiagramModel diagramModel : diagramModels) {
            if(diagramModel.getId() != null) {
                diagramIdMap.put(diagramModel.getId(), diagramModel);
            }
        }

        return new QueryExecutionContext(model, diagramModels, objectIdMap,
                Collections.unmodifiableMap(new LinkedHashMap<>(diagramIdMap)));
    }

    public IArchimateModel getModel() {
        return model;
    }

    public List<IDiagramModel> getDiagramModels() {
        return diagramModels;
    }

    public Map<String, EObject> getObjectIdMap() {
        return objectIdMap;
    }

    public Map<String, IDiagramModel> getDiagramIdMap() {
        return diagramIdMap;
    }
}
