/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.export;

import org.eclipse.swt.graphics.ImageData;

/**
 * Detached export payload produced on the UI-safe render path.
 */
@SuppressWarnings("nls")
public final class RenderedViewExport {

    private final String modelId;
    private final String modelName;
    private final String viewId;
    private final String viewName;
    private final double scale;
    private final ImageData imageData;

    public RenderedViewExport(String modelId, String modelName, String viewId, String viewName, double scale, ImageData imageData) {
        this.modelId = modelId;
        this.modelName = modelName;
        this.viewId = viewId;
        this.viewName = viewName;
        this.scale = scale;
        this.imageData = imageData;
    }

    public String getModelId() {
        return modelId;
    }

    public String getModelName() {
        return modelName;
    }

    public String getViewId() {
        return viewId;
    }

    public String getViewName() {
        return viewName;
    }

    public double getScale() {
        return scale;
    }

    public ImageData getImageData() {
        return imageData;
    }
}
