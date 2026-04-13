/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.editor.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;

@SuppressWarnings("nls")
public class MCPErrorEnvelopeTests {

    private int port;

    @BeforeEach
    public void startServer() throws IOException {
        MCPServerBootstrap.stop();
        port = allocatePort();
        MCPServerBootstrap.start(MCPServerBootstrap.LOOPBACK_V4, port);
    }

    @AfterEach
    public void stopServer() {
        MCPServerBootstrap.stop();
    }

    @Test
    public void healthAndMcpErrorsShareSameEnvelopeSchema() throws IOException {
        HttpResult healthResult = sendRequest("POST", "/health", "{}");
        HttpResult mcpResult = sendRequest("GET", "/mcp", null);

        assertEquals(405, healthResult.statusCode);
        assertEquals(405, mcpResult.statusCode);
        assertErrorSchema(healthResult.body);
        assertErrorSchema(mcpResult.body);
        assertTrue(healthResult.body.contains("\"code\":\"MCP-PROTOCOL-UNSUPPORTED-HTTP-METHOD\""));
        assertTrue(mcpResult.body.contains("\"code\":\"MCP-PROTOCOL-UNSUPPORTED-HTTP-METHOD\""));
    }

    @Test
    public void retryableIsOptionalAndNotRequiredForPhaseOneProtocolErrors() throws IOException {
        HttpResult result = sendRequest("POST", "/mcp", "{\"id\":\"req-3\",\"method\":");

        assertEquals(400, result.statusCode);
        assertErrorSchema(result.body);
        assertTrue(result.body.contains("\"code\":\"MCP-PROTOCOL-BAD-REQUEST\""));
        assertFalse(result.body.contains("\"retryable\""));
    }

    private void assertErrorSchema(String body) {
        assertTrue(body.contains("\"ok\":false"));
        assertTrue(body.contains("\"requestId\":\""));
        assertTrue(body.contains("\"timestamp\":\""));
        assertTrue(body.contains("\"data\":null"));
        assertTrue(body.contains("\"error\":{"));
        assertTrue(body.contains("\"code\":\""));
        assertTrue(body.contains("\"category\":\""));
        assertTrue(body.contains("\"message\":\""));
        assertTrue(body.contains("\"details\":{"));
    }

    private HttpResult sendRequest(String method, String path, String body) throws IOException {
        URL url = new URL("http://127.0.0.1:" + port + path);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        if(body != null) {
            connection.setDoOutput(true);
            try(OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int statusCode = connection.getResponseCode();
        String responseBody = readBody(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        return new HttpResult(statusCode, responseBody);
    }

    private String readBody(InputStream inputStream) throws IOException {
        if(inputStream == null) {
            return "";
        }

        try(InputStream stream = inputStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int allocatePort() throws IOException {
        try(ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class HttpResult {
        private final int statusCode;
        private final String body;

        private HttpResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}
