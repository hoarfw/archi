/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.export;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.archimatetool.mcp.contract.MCPErrorCode;
import com.archimatetool.mcp.contract.MCPExportRequest;
import com.archimatetool.mcp.contract.MCPQueryValidationException;
import com.archimatetool.mcp.contract.MCPStructuredErrorException;

/**
 * Coordinates view export rendering and response assembly.
 */
@SuppressWarnings("nls")
public class ViewExportHandler {

    static final long DEFAULT_TIMEOUT_MILLIS = 15_000L;

    private static final ExecutorService EXPORT_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "Archi-MCP-Export-Worker");
        thread.setDaemon(true);
        return thread;
    });

    private final ViewExportRenderer renderer;
    private final ViewExportResponseBuilder responseBuilder;
    private final TempImageStore tempImageStore;
    private final ViewExportJobGuard jobGuard;
    private final ExecutorService executor;
    private final long timeoutMillis;

    public ViewExportHandler() {
        this(new ViewExportRenderer(), new ViewExportResponseBuilder(TempImageStore.shared()), TempImageStore.shared(),
                new ViewExportJobGuard(), EXPORT_EXECUTOR, DEFAULT_TIMEOUT_MILLIS);
    }

    public ViewExportHandler(ViewExportRenderer renderer, ViewExportResponseBuilder responseBuilder) {
        this(renderer, responseBuilder, TempImageStore.shared(), new ViewExportJobGuard(), EXPORT_EXECUTOR, DEFAULT_TIMEOUT_MILLIS);
    }

    public ViewExportHandler(ViewExportRenderer renderer, ViewExportResponseBuilder responseBuilder, TempImageStore tempImageStore,
            ViewExportJobGuard jobGuard, ExecutorService executor, long timeoutMillis) {
        this.renderer = renderer;
        this.responseBuilder = responseBuilder;
        this.tempImageStore = tempImageStore;
        this.jobGuard = jobGuard;
        this.executor = executor;
        this.timeoutMillis = timeoutMillis;
    }

    public Object handle(MCPExportRequest request) {
        TempImageStore.ExportLease exportLease = tempImageStore.openLease();
        ViewExportJobGuard.JobLease lease = jobGuard.acquire();
        Future<Object> future = executor.submit(() -> {
            try(lease) {
                RenderedViewExport renderedViewExport = renderer.render(request);
                return responseBuilder.build(renderedViewExport, exportLease);
            }
        });

        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        }
        catch(InterruptedException ex) {
            future.cancel(true);
            exportLease.markTimedOut();
            Thread.currentThread().interrupt();
            throw new MCPStructuredErrorException(MCPErrorCode.EXPORT_TIMEOUT,
                    "export interrupted while waiting for completion",
                    Map.of("stage", "timeout", "timeoutMs", Long.toString(timeoutMillis)),
                    null,
                    ex);
        }
        catch(TimeoutException ex) {
            future.cancel(true);
            exportLease.markTimedOut();
            throw new MCPStructuredErrorException(MCPErrorCode.EXPORT_TIMEOUT,
                    "export exceeded timeout budget of " + timeoutMillis + " ms",
                    Map.of("stage", "timeout", "timeoutMs", Long.toString(timeoutMillis)),
                    null,
                    ex);
        }
        catch(ExecutionException ex) {
            throw mapFailure(ex.getCause());
        }
    }

    private RuntimeException mapFailure(Throwable failure) {
        if(failure instanceof RuntimeException runtimeException) {
            if(runtimeException instanceof MCPQueryValidationException) {
                return runtimeException;
            }

            if(runtimeException instanceof MCPStructuredErrorException) {
                return runtimeException;
            }

            if(runtimeException instanceof ViewExportOutputException outputException) {
                return new MCPStructuredErrorException(MCPErrorCode.EXPORT_TEMP_FILE_FAILED,
                        "failed to persist exported image",
                        failureDetails(outputException.getStage(), outputException),
                        Boolean.TRUE,
                        outputException);
            }

            if(runtimeException instanceof ViewExportRenderException renderException) {
                return new MCPStructuredErrorException(MCPErrorCode.EXPORT_RENDER_FAILED,
                        "failed to render diagram view",
                        failureDetails("render", renderException),
                        Boolean.FALSE,
                        renderException);
            }

            return new MCPStructuredErrorException(MCPErrorCode.EXPORT_RENDER_FAILED,
                    "failed to render diagram view",
                    failureDetails("render", runtimeException),
                    Boolean.FALSE,
                    runtimeException);
        }

        return new MCPStructuredErrorException(MCPErrorCode.EXPORT_RENDER_FAILED,
                "failed to render diagram view",
                Map.of("stage", "render", "cause", failure == null ? "unknown" : failure.getClass().getSimpleName()),
                Boolean.FALSE,
                failure);
    }

    private Map<String, String> failureDetails(String stage, Throwable failure) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("stage", stage);
        details.put("cause", failure.getClass().getSimpleName());
        if(failure.getMessage() != null && !failure.getMessage().isBlank()) {
            details.put("causeMessage", failure.getMessage());
        }
        return details;
    }
}
