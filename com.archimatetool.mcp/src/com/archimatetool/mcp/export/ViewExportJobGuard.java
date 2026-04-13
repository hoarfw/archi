/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.export;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.archimatetool.mcp.contract.MCPErrorCode;
import com.archimatetool.mcp.contract.MCPStructuredErrorException;

/**
 * Single-flight guard for view export execution.
 */
@SuppressWarnings("nls")
public class ViewExportJobGuard {

    private final AtomicBoolean active = new AtomicBoolean(false);

    public JobLease acquire() {
        if(!active.compareAndSet(false, true)) {
            throw new MCPStructuredErrorException(MCPErrorCode.EXPORT_BUSY,
                    "another export is already in progress",
                    Map.of("stage", "guard"),
                    Boolean.TRUE);
        }

        return new JobLease(this);
    }

    public boolean isActive() {
        return active.get();
    }

    private void release() {
        active.set(false);
    }

    public static final class JobLease implements AutoCloseable {

        private final ViewExportJobGuard guard;
        private boolean released;

        private JobLease(ViewExportJobGuard guard) {
            this.guard = guard;
        }

        @Override
        public synchronized void close() {
            if(released) {
                return;
            }

            released = true;
            guard.release();
        }
    }
}
