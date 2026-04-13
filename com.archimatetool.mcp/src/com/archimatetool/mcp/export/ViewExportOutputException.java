/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.export;

/**
 * Export output-stage failure with a stable stage label.
 */
@SuppressWarnings("nls")
public class ViewExportOutputException extends IllegalStateException {
    private static final long serialVersionUID = -8068129169443963340L;

    private final String stage;

    private ViewExportOutputException(String stage, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
    }

    public static ViewExportOutputException tempFileFailure(Throwable cause) {
        return new ViewExportOutputException("temp-file", "Failed to create temp image file", cause);
    }

    public String getStage() {
        return stage;
    }
}
