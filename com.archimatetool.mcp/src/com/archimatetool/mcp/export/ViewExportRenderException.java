/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.export;

/**
 * Export render-stage failure.
 */
@SuppressWarnings("nls")
public class ViewExportRenderException extends IllegalStateException {
    private static final long serialVersionUID = -7803545611743204815L;

    public ViewExportRenderException(String message, Throwable cause) {
        super(message, cause);
    }

    public ViewExportRenderException(String message) {
        super(message);
    }
}
