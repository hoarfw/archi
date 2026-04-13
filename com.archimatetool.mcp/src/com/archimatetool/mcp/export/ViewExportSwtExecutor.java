/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.export;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.mcp.bootstrap.MCPReadTelemetry;

/**
 * Executes diagram rendering on a stable SWT-capable thread.
 */
@SuppressWarnings("nls")
public class ViewExportSwtExecutor {

    private static final HeadlessDisplayExecutor HEADLESS_EXECUTOR = new HeadlessDisplayExecutor();

    @FunctionalInterface
    public interface RenderOperation<T> {
        T run();
    }

    public static void cleanupHeadlessDisplay() {
        HEADLESS_EXECUTOR.cleanupDisplay();
    }

    public <T> T execute(RenderOperation<T> operation) {
        if(operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }

        Display currentDisplay = Display.getCurrent();
        if(currentDisplay != null) {
            return executeOnCurrentThread(operation);
        }

        if(PlatformUI.isWorkbenchRunning()) {
            Display workbenchDisplay = PlatformUI.getWorkbench().getDisplay();
            if(workbenchDisplay == null || workbenchDisplay.isDisposed()) {
                throw new IllegalStateException("Workbench display is not available");
            }

            return executeOnDisplay(workbenchDisplay, operation);
        }

        return HEADLESS_EXECUTOR.execute(operation);
    }

    private <T> T executeOnCurrentThread(RenderOperation<T> operation) {
        MCPReadTelemetry.recordExportRender(true, Thread.currentThread().getName());
        return operation.run();
    }

    private <T> T executeOnDisplay(Display display, RenderOperation<T> operation) {
        if(display.getThread() == Thread.currentThread()) {
            return executeOnCurrentThread(operation);
        }

        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();

        display.syncExec(() -> {
            try {
                result.set(executeOnCurrentThread(operation));
            }
            catch(RuntimeException ex) {
                failure.set(ex);
            }
        });

        if(failure.get() != null) {
            throw failure.get();
        }

        return result.get();
    }

    private static final class HeadlessDisplayExecutor {

        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Archi-MCP-Export-SWT");
            thread.setDaemon(true);
            return thread;
        });
        private volatile Display ownedDisplay;

        private <T> T execute(RenderOperation<T> operation) {
            Future<T> future = executor.submit(() -> {
                Display display = ensureDisplay();
                if(display.getThread() != Thread.currentThread()) {
                    throw new IllegalStateException("SWT display is owned by a different thread");
                }

                MCPReadTelemetry.recordExportRender(true, Thread.currentThread().getName());
                return operation.run();
            });

            try {
                return future.get();
            }
            catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while rendering exported view", ex);
            }
            catch(ExecutionException ex) {
                Throwable cause = ex.getCause();
                if(cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if(cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Failed to render exported view", cause);
            }
        }

        private Display ensureDisplay() {
            Display currentDisplay = Display.getCurrent();
            if(currentDisplay != null) {
                ownedDisplay = currentDisplay;
                return currentDisplay;
            }

            Display display = Display.getDefault();
            if(display == null || display.isDisposed()) {
                throw new IllegalStateException("SWT display is not available for export rendering");
            }

            if(display.getThread() == Thread.currentThread()) {
                ownedDisplay = display;
            }

            return display;
        }

        private void cleanupDisplay() {
            Future<?> future = executor.submit(() -> {
                Display display = ownedDisplay;
                if(display != null && !display.isDisposed()) {
                    display.dispose();
                }
                ownedDisplay = null;
            });

            try {
                future.get();
            }
            catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while cleaning up export SWT display", ex);
            }
            catch(ExecutionException ex) {
                Throwable cause = ex.getCause();
                if(cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if(cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Failed to clean up export SWT display", cause);
            }
        }
    }
}
