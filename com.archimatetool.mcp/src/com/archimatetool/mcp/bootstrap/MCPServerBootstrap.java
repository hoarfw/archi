/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.bootstrap;

import java.io.IOException;
import java.net.BindException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.archimatetool.mcp.contract.MCPErrorCode;
import com.archimatetool.mcp.export.TempImageStore;
import com.archimatetool.mcp.export.ViewExportSwtExecutor;
import com.archimatetool.mcp.transport.MCPHttpServer;
import com.sun.net.httpserver.HttpServer;

/**
 * Bootstrap and lifecycle for the local MCP HTTP endpoint.
 */
@SuppressWarnings("nls")
public final class MCPServerBootstrap {

    public static final String HOST_PROPERTY = "archi.mcp.host";
    public static final String PORT_PROPERTY = "archi.mcp.port";
    public static final String LOOPBACK_V4 = "127.0.0.1";
    public static final String LOOPBACK_V6 = "::1";
    public static final int DEFAULT_PORT = 56010;

    private static final Object LOCK = new Object();

    private static volatile HttpServer server;
    private static volatile ExecutorService executor;
    private static volatile MCPServerState state = MCPServerState.stopped(LOOPBACK_V4, DEFAULT_PORT, "not-started");

    private MCPServerBootstrap() {
    }

    public static void start() {
        start(getConfiguredHost(), getConfiguredPort());
    }

    public static void start(String host, int port) {
        String normalizedHost = normalizeHost(host);
        int normalizedPort = normalizePort(port);

        synchronized(LOCK) {
            if(server != null) {
                return;
            }

            if(!isLoopbackAddress(normalizedHost)) {
                state = MCPServerState.degraded(normalizedHost, normalizedPort, "loopback-only");
                logStartupFailure("MCP startup rejected non-loopback host", state, null);
                return;
            }

            state = MCPServerState.starting(normalizedHost, normalizedPort);
            log(IStatus.INFO, formatStateMessage("MCP server starting", state), null);

            HttpServer createdServer = null;
            ExecutorService createdExecutor = null;
            try {
                createdServer = MCPHttpServer.create(normalizedHost, normalizedPort);

                createdExecutor = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "Archi-MCP-Server");
                    thread.setDaemon(true);
                    return thread;
                });
                createdServer.setExecutor(createdExecutor);
                createdServer.start();

                server = createdServer;
                executor = createdExecutor;
                state = MCPServerState.running(normalizedHost, normalizedPort);
                log(IStatus.INFO, formatStateMessage("MCP server started", state), null);
            }
            catch(BindException ex) {
                state = MCPServerState.degraded(normalizedHost, normalizedPort, "port-in-use");
                logStartupFailure("MCP startup degraded (port in use)", state, ex);
                cleanup(createdServer, createdExecutor);
            }
            catch(IOException ex) {
                state = MCPServerState.degraded(normalizedHost, normalizedPort, "bind-failed");
                logStartupFailure("MCP startup degraded (bind failed)", state, ex);
                cleanup(createdServer, createdExecutor);
            }
            catch(RuntimeException ex) {
                state = MCPServerState.degraded(normalizedHost, normalizedPort, "unexpected-error");
                logStartupFailure("MCP startup degraded (unexpected error)", state, ex);
                cleanup(createdServer, createdExecutor);
            }
        }
    }

    public static void stop() {
        synchronized(LOCK) {
            if(server != null) {
                server.stop(0);
                server = null;
            }

            if(executor != null) {
                executor.shutdownNow();
                executor = null;
            }

            TempImageStore.shared().cleanupAll();
            ViewExportSwtExecutor.cleanupHeadlessDisplay();
            state = MCPServerState.stopped(state.getHost(), state.getPort(), null);
            log(IStatus.INFO, formatStateMessage("MCP server stopped", state), null);
        }
    }

    public static MCPServerState getState() {
        return state;
    }

    public static boolean isLoopbackAddress(String host) {
        String value = normalizeHost(host);
        return LOOPBACK_V4.equals(value) || LOOPBACK_V6.equals(value);
    }

    private static String normalizeHost(String host) {
        if(host == null || host.isBlank()) {
            return LOOPBACK_V4;
        }
        return host.trim();
    }

    private static int normalizePort(int port) {
        return port > 0 && port <= 65535 ? port : DEFAULT_PORT;
    }

    private static String getConfiguredHost() {
        return normalizeHost(System.getProperty(HOST_PROPERTY));
    }

    private static int getConfiguredPort() {
        String value = System.getProperty(PORT_PROPERTY);
        if(value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }

        try {
            return normalizePort(Integer.parseInt(value.trim()));
        }
        catch(NumberFormatException ex) {
            return DEFAULT_PORT;
        }
    }

    private static String formatStateMessage(String prefix, MCPServerState state) {
        StringBuilder message = new StringBuilder(prefix)
                .append(" host=")
                .append(state.getHost())
                .append(" port=")
                .append(state.getPort())
                .append(" state=")
                .append(state.getLifecycleId());
        if(state.getReason() != null) {
            message.append(" reason=").append(state.getReason());
        }
        return message.toString();
    }

    private static void logStartupFailure(String prefix, MCPServerState state, Throwable throwable) {
        MCPErrorCode errorCode = MCPErrorCode.fromStartupReason(state.getReason());
        String message = formatStateMessage(prefix, state)
                + " code=" + errorCode.getCode()
                + " category=" + errorCode.getCategory();
        log(IStatus.WARNING, message, throwable);
    }

    private static void cleanup(HttpServer createdServer, ExecutorService createdExecutor) {
        if(createdServer != null) {
            createdServer.stop(0);
        }
        if(createdExecutor != null) {
            createdExecutor.shutdownNow();
        }
    }

    private static void log(int severity, String message, Throwable throwable) {
        Bundle bundle = FrameworkUtil.getBundle(MCPServerBootstrap.class);
        if(bundle == null) {
            if(throwable == null) {
                System.err.println(message);
            }
            else {
                System.err.println(message + " - " + throwable.getMessage());
            }
            return;
        }

        ILog.of(bundle).log(new Status(severity, bundle.getSymbolicName(), message, throwable));
    }
}
