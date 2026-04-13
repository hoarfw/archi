/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.editor.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;
import com.archimatetool.mcp.bootstrap.MCPServerState;
import com.archimatetool.mcp.bootstrap.MCPServerState.Lifecycle;

@SuppressWarnings("nls")
public class MCPServiceLifecycleTests {

    @AfterEach
    public void stopServer() {
        MCPServerBootstrap.stop();
    }

    @Test
    public void startupPortConflictMapsToStableStartupCode() throws IOException {
        try(ServerSocket occupied = new ServerSocket(0)) {
            int occupiedPort = occupied.getLocalPort();

            MCPServerBootstrap.start(MCPServerBootstrap.LOOPBACK_V4, occupiedPort);

            MCPServerState state = MCPServerBootstrap.getState();
            assertEquals(Lifecycle.DEGRADED, state.getLifecycle());
            assertEquals("port-in-use", state.getReason());
        }
    }

    @Test
    public void loopbackPolicyViolationMapsToStartupCode() throws IOException {
        MCPServerBootstrap.start("0.0.0.0", allocatePort());

        MCPServerState state = MCPServerBootstrap.getState();
        assertEquals(Lifecycle.DEGRADED, state.getLifecycle());
        assertEquals("loopback-only", state.getReason());
    }

    private int allocatePort() throws IOException {
        try(ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
