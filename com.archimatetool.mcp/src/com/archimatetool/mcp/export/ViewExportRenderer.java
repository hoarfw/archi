/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.export;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.osgi.framework.Bundle;

import com.archimatetool.mcp.contract.MCPErrorCode;
import com.archimatetool.mcp.contract.MCPExportRequest;
import com.archimatetool.mcp.contract.MCPQueryValidationException;
import com.archimatetool.mcp.read.OpenModelResolver;
import com.archimatetool.mcp.read.QueryExecutionContext;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;

/**
 * Renders the requested diagram view to detached SWT image data.
 */
@SuppressWarnings("nls")
public class ViewExportRenderer {

    private static final String EDITOR_BUNDLE_ID = "com.archimatetool.editor";
    private static final String DIAGRAM_UTILS_CLASS = "com.archimatetool.editor.diagram.util.DiagramUtils";
    private static final int DEFAULT_MARGIN = 10;

    private final ViewExportSwtExecutor executor;
    private final OpenModelResolver modelResolver;

    public ViewExportRenderer() {
        this(new ViewExportSwtExecutor(), new OpenModelResolver());
    }

    public ViewExportRenderer(ViewExportSwtExecutor executor, OpenModelResolver modelResolver) {
        this.executor = executor;
        this.modelResolver = modelResolver;
    }

    public RenderedViewExport render(MCPExportRequest request) {
        return executor.execute(() -> {
            IArchimateModel model = modelResolver.resolve(request.getModelId());
            QueryExecutionContext context = QueryExecutionContext.create(model);
            IDiagramModel view = context.getDiagramIdMap().get(request.getViewId());

            if(view == null) {
                throw new MCPQueryValidationException(MCPErrorCode.EXPORT_VIEW_NOT_FOUND, "viewId not found: " + request.getViewId());
            }

            Image image = createImage(view, request.getScale(), DEFAULT_MARGIN);
            try {
                ImageData imageData = image.getImageData();
                return new RenderedViewExport(model.getId(), model.getName(), view.getId(), view.getName(), request.getScale(), imageData);
            }
            finally {
                image.dispose();
            }
        });
    }

    private Image createImage(IDiagramModel view, double scale, int margin) {
        try {
            Bundle bundle = Platform.getBundle(EDITOR_BUNDLE_ID);
            if(bundle == null) {
                throw new IllegalStateException("Required bundle is not available: " + EDITOR_BUNDLE_ID);
            }

            Class<?> diagramUtilsClass = bundle.loadClass(DIAGRAM_UTILS_CLASS);
            Method method = diagramUtilsClass.getMethod("createImage", IDiagramModel.class, double.class, int.class);
            Object value = method.invoke(null, view, Double.valueOf(scale), Integer.valueOf(margin));

            if(value instanceof Image image) {
                return image;
            }

            throw new ViewExportRenderException("DiagramUtils returned an invalid image");
        }
        catch(InvocationTargetException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new ViewExportRenderException("Failed to render diagram view", cause);
        }
        catch(ReflectiveOperationException ex) {
            throw new ViewExportRenderException("Failed to access DiagramUtils", ex);
        }
    }
}
