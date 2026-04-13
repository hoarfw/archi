/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.bootstrap;

/**
 * Lightweight runtime telemetry for MCP write-path tests.
 */
@SuppressWarnings("nls")
public final class MCPWriteTelemetry {

    private static Snapshot snapshot = Snapshot.empty();

    private MCPWriteTelemetry() {
    }

    public static synchronized void clear() {
        snapshot = Snapshot.empty();
    }

    public static synchronized void recordExecutorInvocation(boolean uiThread, String threadName) {
        snapshot = snapshot.withExecutor(uiThread, threadName);
    }

    public static synchronized void recordContextBuild(boolean uiThread, String threadName, String modelId,
            boolean commandStackAvailable) {
        snapshot = snapshot.withContext(uiThread, threadName, modelId, commandStackAvailable);
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
        private final boolean commandStackAvailable;

        private Snapshot(boolean executorInvoked, boolean executorOnUiThread, String executorThreadName, boolean contextBuilt,
                boolean contextBuiltOnUiThread, String contextThreadName, String resolvedModelId, boolean commandStackAvailable) {
            this.executorInvoked = executorInvoked;
            this.executorOnUiThread = executorOnUiThread;
            this.executorThreadName = executorThreadName;
            this.contextBuilt = contextBuilt;
            this.contextBuiltOnUiThread = contextBuiltOnUiThread;
            this.contextThreadName = contextThreadName;
            this.resolvedModelId = resolvedModelId;
            this.commandStackAvailable = commandStackAvailable;
        }

        private static Snapshot empty() {
            return new Snapshot(false, false, null, false, false, null, null, false);
        }

        private Snapshot withExecutor(boolean uiThread, String threadName) {
            return new Snapshot(true, uiThread, threadName, contextBuilt, contextBuiltOnUiThread, contextThreadName,
                    resolvedModelId, commandStackAvailable);
        }

        private Snapshot withContext(boolean uiThread, String threadName, String modelId, boolean hasCommandStack) {
            return new Snapshot(executorInvoked, executorOnUiThread, executorThreadName, true, uiThread, threadName, modelId,
                    hasCommandStack);
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

        public boolean isCommandStackAvailable() {
            return commandStackAvailable;
        }
    }
}
