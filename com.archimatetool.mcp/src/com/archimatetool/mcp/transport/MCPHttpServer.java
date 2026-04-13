/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.transport;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

/**
 * Factory for MCP HTTP server contexts.
 */
public final class MCPHttpServer {

    private MCPHttpServer() {
    }

    public static HttpServer create(String host, int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/mcp", new MCPStreamHandler());
        return server;
    }
}
