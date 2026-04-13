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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;

@SuppressWarnings("nls")
public class MCPHttpStreamContractTests {

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("\"requestId\":\"([^\"]+)\"");

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
    public void postInitializeReturnsEnvelope() throws IOException {
        HttpResult result = sendRequest("POST", "{\"id\":\"req-1\",\"method\":\"initialize\"}");

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"ok\":true"));
        assertTrue(result.body.contains("\"timestamp\":\""));
        assertTrue(result.body.contains("\"data\":{"));
        assertTrue(result.body.contains("\"error\":null"));
        assertTrue(result.body.contains("\"method\":\"initialize\""));

        Matcher matcher = REQUEST_ID_PATTERN.matcher(result.body);
        assertTrue(matcher.find());
        assertFalse(matcher.group(1).isBlank());
    }

    @Test
    public void nonPostIsRejected() throws IOException {
        HttpResult result = sendRequest("GET", null);

        assertEquals(405, result.statusCode);
        assertTrue(result.body.contains("\"ok\":false"));
        assertTrue(result.body.contains("\"code\":\"MCP-PROTOCOL-UNSUPPORTED-HTTP-METHOD\""));
    }

    @Test
    public void readMethodIsRejectedInPhaseOne() throws IOException {
        HttpResult result = sendRequest("POST", "{\"id\":\"req-2\",\"method\":\"READ-model\"}");

        assertEquals(400, result.statusCode);
        assertTrue(result.body.contains("\"ok\":false"));
        assertTrue(result.body.contains("\"code\":\"MCP-PROTOCOL-UNSUPPORTED-METHOD\""));
    }

    private HttpResult sendRequest(String method, String body) throws IOException {
        URL url = new URL("http://127.0.0.1:" + port + "/mcp");
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
