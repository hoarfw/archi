/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.editor.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.mcp.contract.MCPErrorCode;
import com.archimatetool.mcp.contract.MCPExportRequest;
import com.archimatetool.mcp.contract.MCPRequest;
import com.archimatetool.mcp.contract.MCPStructuredErrorException;
import com.archimatetool.mcp.export.RenderedViewExport;
import com.archimatetool.mcp.export.ViewExportHandler;
import com.archimatetool.mcp.export.ViewExportJobGuard;
import com.archimatetool.mcp.export.ViewExportOutputException;
import com.archimatetool.mcp.export.ViewExportRenderer;
import com.archimatetool.mcp.export.ViewExportResponseBuilder;
import com.archimatetool.mcp.export.TempImageStore;

@SuppressWarnings("nls")
public class MCPViewExportReliabilityTests {

    private final ExecutorService testExecutor = Executors.newCachedThreadPool();

    @AfterEach
    public void shutdownExecutor() {
        testExecutor.shutdownNow();
    }

    @Test
    public void concurrentSecondExportFailsBusyAndIsRetryable() throws Exception {
        CountDownLatch renderStarted = new CountDownLatch(1);
        CountDownLatch releaseRender = new CountDownLatch(1);
        ViewExportJobGuard guard = new ViewExportJobGuard();
        ViewExportHandler handler = new ViewExportHandler(new BlockingRenderer(renderStarted, releaseRender), new StubResponseBuilder(),
                TempImageStore.shared(), guard, testExecutor, 2_000L);
        MCPExportRequest request = exportRequest("busy-view");

        Future<Object> firstCall = testExecutor.submit(() -> handler.handle(request));
        assertTrue(renderStarted.await(1, TimeUnit.SECONDS));
        assertTrue(guard.isActive());

        Future<MCPStructuredErrorException> secondCall = testExecutor.submit(() -> {
            try {
                handler.handle(request);
                return null;
            }
            catch(MCPStructuredErrorException ex) {
                return ex;
            }
        });

        MCPStructuredErrorException error = secondCall.get(1, TimeUnit.SECONDS);
        assertNotNull(error);
        assertEquals(MCPErrorCode.EXPORT_BUSY, error.getErrorCode());
        assertEquals(Boolean.TRUE, error.getRetryable());
        assertEquals("guard", error.getDetails().get("stage"));

        releaseRender.countDown();
        assertTrue(firstCall.get(1, TimeUnit.SECONDS) instanceof Map);
        assertTrue(waitForInactive(guard));
    }

    @Test
    public void timedOutExportReturnsTimeoutWithoutRetryable() {
        CountDownLatch renderStarted = new CountDownLatch(1);
        CountDownLatch releaseRender = new CountDownLatch(1);
        ViewExportJobGuard guard = new ViewExportJobGuard();
        ViewExportHandler handler = new ViewExportHandler(new BlockingRenderer(renderStarted, releaseRender), new StubResponseBuilder(),
                TempImageStore.shared(), guard, testExecutor, 25L);

        MCPStructuredErrorException error = null;
        try {
            handler.handle(exportRequest("timeout-view"));
        }
        catch(MCPStructuredErrorException ex) {
            error = ex;
        }
        finally {
            releaseRender.countDown();
        }

        assertNotNull(error);
        assertEquals(MCPErrorCode.EXPORT_TIMEOUT, error.getErrorCode());
        assertNull(error.getRetryable());
        assertEquals("timeout", error.getDetails().get("stage"));
    }

    @Test
    public void renderFailureIsStructuredAndNotRetryable() {
        ViewExportHandler handler = new ViewExportHandler(new FailingRenderer(new IllegalStateException("broken figure")),
                new StubResponseBuilder(), TempImageStore.shared(), new ViewExportJobGuard(), testExecutor, 200L);

        MCPStructuredErrorException error = captureStructuredError(() -> handler.handle(exportRequest("render-view")));

        assertNotNull(error);
        assertEquals(MCPErrorCode.EXPORT_RENDER_FAILED, error.getErrorCode());
        assertEquals(Boolean.FALSE, error.getRetryable());
        assertEquals("render", error.getDetails().get("stage"));
    }

    @Test
    public void tempFileFailureIsStructuredAndRetryable() {
        ViewExportHandler handler = new ViewExportHandler(new InstantRenderer(), new FailingResponseBuilder(),
                TempImageStore.shared(), new ViewExportJobGuard(), testExecutor, 200L);

        MCPStructuredErrorException error = captureStructuredError(() -> handler.handle(exportRequest("temp-file-view")));

        assertNotNull(error);
        assertEquals(MCPErrorCode.EXPORT_TEMP_FILE_FAILED, error.getErrorCode());
        assertEquals(Boolean.TRUE, error.getRetryable());
        assertEquals("temp-file", error.getDetails().get("stage"));
    }

    private boolean waitForInactive(ViewExportJobGuard guard) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while(System.nanoTime() < deadline) {
            if(!guard.isActive()) {
                return true;
            }
            Thread.sleep(10L);
        }
        return !guard.isActive();
    }

    private MCPStructuredErrorException captureStructuredError(ThrowingRunnable runnable) {
        try {
            runnable.run();
            return null;
        }
        catch(MCPStructuredErrorException ex) {
            return ex;
        }
        catch(Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private MCPExportRequest exportRequest(String viewId) {
        return MCPExportRequest.from(MCPRequest.parse("""
                {
                  "id":"req-reliability",
                  "method":"exportView",
                  "params":{"viewId":"%s"}
                }
                """.formatted(viewId)));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class BlockingRenderer extends ViewExportRenderer {
        private final CountDownLatch started;
        private final CountDownLatch release;

        private BlockingRenderer(CountDownLatch started, CountDownLatch release) {
            this.started = started;
            this.release = release;
        }

        @Override
        public RenderedViewExport render(MCPExportRequest request) {
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return renderedExport(request.getViewId());
        }
    }

    private static final class InstantRenderer extends ViewExportRenderer {
        @Override
        public RenderedViewExport render(MCPExportRequest request) {
            return renderedExport(request.getViewId());
        }
    }

    private static final class FailingRenderer extends ViewExportRenderer {
        private final RuntimeException failure;

        private FailingRenderer(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public RenderedViewExport render(MCPExportRequest request) {
            throw failure;
        }
    }

    private static final class StubResponseBuilder extends ViewExportResponseBuilder {
        @Override
        public Object build(RenderedViewExport renderedViewExport, TempImageStore.ExportLease exportLease) {
            return Map.of("viewId", renderedViewExport.getViewId());
        }
    }

    private static final class FailingResponseBuilder extends ViewExportResponseBuilder {
        @Override
        public Object build(RenderedViewExport renderedViewExport, TempImageStore.ExportLease exportLease) {
            throw ViewExportOutputException.tempFileFailure(new IllegalStateException("disk full"));
        }
    }

    private static RenderedViewExport renderedExport(String viewId) {
        return new RenderedViewExport("model-1", "Model", viewId, "View", 1.0d,
                new ImageData(16, 16, 24, new PaletteData(0xFF0000, 0x00FF00, 0x0000FF)));
    }
}
