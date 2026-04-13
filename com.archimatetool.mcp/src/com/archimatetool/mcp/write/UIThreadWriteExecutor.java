/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.write;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.mcp.bootstrap.MCPWriteTelemetry;

/**
 * Runs live model writes on the SWT UI thread.
 */
@SuppressWarnings("nls")
public class UIThreadWriteExecutor {

    private static final ThreadLocal<Boolean> UI_SAFE_RUNTIME = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @FunctionalInterface
    public interface WriteOperation<T> {
        T run();
    }

    public <T> T execute(WriteOperation<T> operation) {
        if(operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }

        Display currentDisplay = Display.getCurrent();
        if(currentDisplay != null) {
            return executeOnCurrentThread(operation);
        }

        Display display = getWorkbenchDisplay();
        if(display == null || display.isDisposed()) {
            return executeOnCurrentThread(operation);
        }

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

    private <T> T executeOnCurrentThread(WriteOperation<T> operation) {
        Boolean previous = UI_SAFE_RUNTIME.get();
        UI_SAFE_RUNTIME.set(Boolean.TRUE);
        MCPWriteTelemetry.recordExecutorInvocation(true, Thread.currentThread().getName());

        try {
            return operation.run();
        }
        finally {
            UI_SAFE_RUNTIME.set(previous);
        }
    }

    private Display getWorkbenchDisplay() {
        if(!PlatformUI.isWorkbenchRunning()) {
            return null;
        }

        return PlatformUI.getWorkbench().getDisplay();
    }

    public static boolean isInUiSafeRuntime() {
        return Boolean.TRUE.equals(UI_SAFE_RUNTIME.get());
    }
}
