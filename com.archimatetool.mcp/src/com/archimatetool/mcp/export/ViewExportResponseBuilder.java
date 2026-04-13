/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.mcp.bootstrap.MCPReadTelemetry;

/**
 * Builds the stable transport payload for exported images.
 */
@SuppressWarnings("nls")
public class ViewExportResponseBuilder {

    private final TempImageStore tempImageStore;

    public ViewExportResponseBuilder() {
        this(TempImageStore.shared());
    }

    public ViewExportResponseBuilder(TempImageStore tempImageStore) {
        this.tempImageStore = tempImageStore;
    }

    public Object build(RenderedViewExport renderedViewExport) {
        return build(renderedViewExport, tempImageStore.openLease());
    }

    public Object build(RenderedViewExport renderedViewExport, TempImageStore.ExportLease exportLease) {
        MCPReadTelemetry.recordExportAssembly(Display.getCurrent() != null, Thread.currentThread().getName());
        byte[] pngBytes = toPngBytes(renderedViewExport);
        String fileUrl = writeTempFile(exportLease, pngBytes);
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);

        Map<String, Object> modelSummary = new LinkedHashMap<>();
        modelSummary.put("modelId", renderedViewExport.getModelId());
        modelSummary.put("name", renderedViewExport.getModelName());

        Map<String, Object> viewSummary = new LinkedHashMap<>();
        viewSummary.put("viewId", renderedViewExport.getViewId());
        viewSummary.put("name", renderedViewExport.getViewName());

        Map<String, Object> imageSummary = new LinkedHashMap<>();
        imageSummary.put("format", "png");
        imageSummary.put("mimeType", "image/png");
        imageSummary.put("scale", Double.valueOf(renderedViewExport.getScale()));
        imageSummary.put("width", Integer.valueOf(renderedViewExport.getImageData().width));
        imageSummary.put("height", Integer.valueOf(renderedViewExport.getImageData().height));
        imageSummary.put("fileUrl", fileUrl);
        imageSummary.put("dataUrl", dataUrl);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("method", "exportView");
        response.put("model", modelSummary);
        response.put("view", viewSummary);
        response.put("image", imageSummary);
        return response;
    }

    private byte[] toPngBytes(RenderedViewExport renderedViewExport) {
        try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageLoader imageLoader = new ImageLoader();
            imageLoader.data = new org.eclipse.swt.graphics.ImageData[] { renderedViewExport.getImageData() };
            imageLoader.save(outputStream, SWT.IMAGE_PNG);
            return outputStream.toByteArray();
        }
        catch(IOException ex) {
            throw new IllegalStateException("Failed to encode PNG image", ex);
        }
    }

    private String writeTempFile(TempImageStore.ExportLease exportLease, byte[] pngBytes) {
        try {
            return tempImageStore.writePng(exportLease, pngBytes);
        }
        catch(IOException ex) {
            throw ViewExportOutputException.tempFileFailure(ex);
        }
    }
}
