/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.bootstrap;

/**
 * Lightweight runtime telemetry for MCP read-path tests.
 */
@SuppressWarnings("nls")
public final class MCPReadTelemetry {

    private static Snapshot snapshot = Snapshot.empty();

    private MCPReadTelemetry() {
    }

    public static synchronized void clear() {
        snapshot = Snapshot.empty();
    }

    public static synchronized void recordExecutorInvocation(boolean uiThread, String threadName) {
        snapshot = snapshot.withExecutor(uiThread, threadName);
    }

    public static synchronized void recordContextBuild(boolean uiThread, String threadName, String modelId) {
        snapshot = snapshot.withContext(uiThread, threadName, modelId);
    }

    public static synchronized void recordExportRender(boolean uiThread, String threadName) {
        snapshot = snapshot.withExportRender(uiThread, threadName);
    }

    public static synchronized void recordExportAssembly(boolean uiThread, String threadName) {
        snapshot = snapshot.withExportAssembly(uiThread, threadName);
    }

    public static synchronized Snapshot snapshot() {
        return snapshot;
    }

    public static final class Snapshot {

        private final boolean executorInvoked;
        private final boolean executorOnUiThread;
        private final String executorThreadName;
        private final boolean contextBuilt;
        private final boolean contextBuiltOnUiThread;
        private final String contextThreadName;
        private final String resolvedModelId;
        private final boolean exportRenderRecorded;
        private final boolean exportRenderOnUiThread;
        private final String exportRenderThreadName;
        private final boolean exportAssemblyRecorded;
        private final boolean exportAssemblyOnUiThread;
        private final String exportAssemblyThreadName;

        private Snapshot(boolean executorInvoked, boolean executorOnUiThread, String executorThreadName, boolean contextBuilt,
                boolean contextBuiltOnUiThread, String contextThreadName, String resolvedModelId, boolean exportRenderRecorded,
                boolean exportRenderOnUiThread, String exportRenderThreadName, boolean exportAssemblyRecorded,
                boolean exportAssemblyOnUiThread, String exportAssemblyThreadName) {
            this.executorInvoked = executorInvoked;
            this.executorOnUiThread = executorOnUiThread;
            this.executorThreadName = executorThreadName;
            this.contextBuilt = contextBuilt;
            this.contextBuiltOnUiThread = contextBuiltOnUiThread;
            this.contextThreadName = contextThreadName;
            this.resolvedModelId = resolvedModelId;
            this.exportRenderRecorded = exportRenderRecorded;
            this.exportRenderOnUiThread = exportRenderOnUiThread;
            this.exportRenderThreadName = exportRenderThreadName;
            this.exportAssemblyRecorded = exportAssemblyRecorded;
            this.exportAssemblyOnUiThread = exportAssemblyOnUiThread;
            this.exportAssemblyThreadName = exportAssemblyThreadName;
        }

        private static Snapshot empty() {
            return new Snapshot(false, false, null, false, false, null, null, false, false, null, false, false, null);
        }

        private Snapshot withExecutor(boolean uiThread, String threadName) {
            return new Snapshot(true, uiThread, threadName, contextBuilt, contextBuiltOnUiThread, contextThreadName, resolvedModelId,
                    exportRenderRecorded, exportRenderOnUiThread, exportRenderThreadName, exportAssemblyRecorded,
                    exportAssemblyOnUiThread, exportAssemblyThreadName);
        }

        private Snapshot withContext(boolean uiThread, String threadName, String modelId) {
            return new Snapshot(executorInvoked, executorOnUiThread, executorThreadName, true, uiThread, threadName, modelId,
                    exportRenderRecorded, exportRenderOnUiThread, exportRenderThreadName, exportAssemblyRecorded,
                    exportAssemblyOnUiThread, exportAssemblyThreadName);
        }

        private Snapshot withExportRender(boolean uiThread, String threadName) {
            return new Snapshot(executorInvoked, executorOnUiThread, executorThreadName, contextBuilt, contextBuiltOnUiThread,
                    contextThreadName, resolvedModelId, true, uiThread, threadName, exportAssemblyRecorded,
                    exportAssemblyOnUiThread, exportAssemblyThreadName);
        }

        private Snapshot withExportAssembly(boolean uiThread, String threadName) {
            return new Snapshot(executorInvoked, executorOnUiThread, executorThreadName, contextBuilt, contextBuiltOnUiThread,
                    contextThreadName, resolvedModelId, exportRenderRecorded, exportRenderOnUiThread, exportRenderThreadName, true,
                    uiThread, threadName);
        }

        public boolean isExecutorInvoked() {
            return executorInvoked;
        }

        public boolean isExecutorOnUiThread() {
            return executorOnUiThread;
        }

        public String getExecutorThreadName() {
            return executorThreadName;
        }

        public boolean isContextBuilt() {
            return contextBuilt;
        }

        public boolean isContextBuiltOnUiThread() {
            return contextBuiltOnUiThread;
        }

        public String getContextThreadName() {
            return contextThreadName;
        }

        public String getResolvedModelId() {
            return resolvedModelId;
        }

        public boolean isExportRenderRecorded() {
            return exportRenderRecorded;
        }

        public boolean isExportRenderOnUiThread() {
            return exportRenderOnUiThread;
        }

        public String getExportRenderThreadName() {
            return exportRenderThreadName;
        }

        public boolean isExportAssemblyRecorded() {
            return exportAssemblyRecorded;
        }

        public boolean isExportAssemblyOnUiThread() {
            return exportAssemblyOnUiThread;
        }

        public String getExportAssemblyThreadName() {
            return exportAssemblyThreadName;
        }
    }
}
