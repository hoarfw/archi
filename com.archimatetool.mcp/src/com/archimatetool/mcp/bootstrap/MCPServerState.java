/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.bootstrap;

import java.time.Instant;

/**
 * Immutable MCP service state snapshot.
 */
public final class MCPServerState {

    public enum Lifecycle {
        STARTING("starting"), //$NON-NLS-1$
        RUNNING("running"), //$NON-NLS-1$
        DEGRADED("degraded"), //$NON-NLS-1$
        STOPPED("stopped"); //$NON-NLS-1$

        private final String id;

        Lifecycle(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private final Lifecycle lifecycle;
    private final String host;
    private final int port;
    private final String reason;
    private final Instant timestamp;

    private MCPServerState(Lifecycle lifecycle, String host, int port, String reason, Instant timestamp) {
        this.lifecycle = lifecycle;
        this.host = host;
        this.port = port;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public static MCPServerState starting(String host, int port) {
        return new MCPServerState(Lifecycle.STARTING, host, port, null, Instant.now());
    }

    public static MCPServerState running(String host, int port) {
        return new MCPServerState(Lifecycle.RUNNING, host, port, null, Instant.now());
    }

    public static MCPServerState degraded(String host, int port, String reason) {
        return new MCPServerState(Lifecycle.DEGRADED, host, port, reason, Instant.now());
    }

    public static MCPServerState stopped(String host, int port, String reason) {
        return new MCPServerState(Lifecycle.STOPPED, host, port, reason, Instant.now());
    }

    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    public String getLifecycleId() {
        return lifecycle.getId();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getReason() {
        return reason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
