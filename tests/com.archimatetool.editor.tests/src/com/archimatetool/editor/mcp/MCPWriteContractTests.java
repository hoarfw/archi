/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.editor.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;

@SuppressWarnings("nls")
public class MCPWriteContractTests {

    private int port;
    private final List<IArchimateModel> createdModels = new ArrayList<>();

    @BeforeEach
    public void startServer() throws IOException {
        MCPServerBootstrap.stop();
        closeOpenModels();
        port = allocatePort();
        MCPServerBootstrap.start(MCPServerBootstrap.LOOPBACK_V4, port);
    }

    @AfterEach
    public void stopServer() throws IOException {
        MCPServerBootstrap.stop();
        closeOpenModels();
    }

    @Test
    public void writeRejectsUnknownOperationWithStructuredError() throws IOException {
        HttpResult result = sendRequest("""
                {
                  "id":"req-write-unknown-op",
                  "method":"write",
                  "params":{"operation":"unknown","payload":{}}
                }
                """);

        assertEquals(400, result.statusCode);
        assertErrorSchema(result.body);
        assertTrue(result.body.contains("\"code\":\"MCP-WRITE-UNSUPPORTED-OPERATION\""));
        assertTrue(result.body.contains("\"reason\":\"unknown write operation: unknown\""));
    }

    @Test
    public void writeRejectsMissingPayloadWithStructuredError() throws IOException {
        HttpResult result = sendRequest("""
                {
                  "id":"req-write-missing-payload",
                  "method":"write",
                  "params":{"operation":"createElement"}
                }
                """);

        assertEquals(400, result.statusCode);
        assertErrorSchema(result.body);
        assertTrue(result.body.contains("\"code\":\"MCP-WRITE-BAD-REQUEST\""));
        assertTrue(result.body.contains("\"reason\":\"payload must be an object\""));
    }

    @Test
    public void writeRejectsInvalidBoundsWithStructuredError() throws IOException {
        HttpResult result = sendRequest("""
                {
                  "id":"req-write-invalid-bounds",
                  "method":"write",
                  "params":{
                    "operation":"addElementToView",
                    "payload":{"bounds":{"x":10,"y":"bad"}}
                  }
                }
                """);

        assertEquals(400, result.statusCode);
        assertErrorSchema(result.body);
        assertTrue(result.body.contains("\"code\":\"MCP-WRITE-BAD-REQUEST\""));
        assertTrue(result.body.contains("\"reason\":\"bounds.y must be a number\""));
    }

    @Test
    public void reservedWriteOperationRoutesThroughWriteDispatcher() throws IOException {
        IArchimateModel model = createOpenModel("Write Contract Model");

        HttpResult result = sendRequest("""
                {
                  "id":"req-write-reserved-op",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "operation":"createElement",
                    "payload":{"type":"business-actor","name":"Contract Actor"}
                  }
                }
                """.formatted(model.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"ok\":true"));
        assertTrue(result.body.contains("\"operation\":\"createElement\""));
        assertTrue(result.body.contains("\"type\":\"business-actor\""));
    }

    @Test
    public void existingMethodsRemainReachableAfterAddingWrite() throws IOException {
        IArchimateModel model = createOpenModel("Write Regression Model");
        model.getDiagramModels().get(0).setName("Write Regression View");
        var actor = IArchimateFactory.eINSTANCE.createBusinessActor();
        actor.setName("Regression Actor");
        model.getDefaultFolderForObject(actor).getElements().add(actor);

        HttpResult initialize = sendRequest("""
                {
                  "id":"req-write-initialize",
                  "method":"initialize"
                }
                """);
        HttpResult ping = sendRequest("""
                {
                  "id":"req-write-ping",
                  "method":"ping"
                }
                """);
        HttpResult query = sendRequest("""
                {
                  "id":"req-write-query",
                  "method":"query",
                  "params":{"queryType":"model","modelId":"%s"}
                }
                """.formatted(model.getId()));
        HttpResult export = sendRequest("""
                {
                  "id":"req-write-export",
                  "method":"exportView",
                  "params":{"viewId":"%s"}
                }
                """.formatted(model.getDiagramModels().get(0).getId()));

        assertEquals(200, initialize.statusCode);
        assertTrue(initialize.body.contains("\"method\":\"initialize\""));
        assertEquals(200, ping.statusCode);
        assertTrue(ping.body.contains("\"method\":\"ping\""));
        assertEquals(200, query.statusCode);
        assertTrue(query.body.contains("\"queryType\":\"model\""));
        assertEquals(200, export.statusCode);
        assertTrue(export.body.contains("\"method\":\"exportView\""));
        assertTrue(export.body.contains("\"fileUrl\":\"file:"));
    }

    private void assertErrorSchema(String body) {
        assertTrue(body.contains("\"ok\":false"));
        assertTrue(body.contains("\"requestId\":\""));
        assertTrue(body.contains("\"timestamp\":\""));
        assertTrue(body.contains("\"data\":null"));
        assertTrue(body.contains("\"error\":{"));
        assertTrue(body.contains("\"details\":{"));
    }

    private HttpResult sendRequest(String body) throws IOException {
        URL url = new URL("http://127.0.0.1:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(4000);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setDoOutput(true);

        try(OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(body.getBytes(StandardCharsets.UTF_8));
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

    private IArchimateModel createOpenModel(String name) {
        IArchimateModel model = IEditorModelManager.INSTANCE.createNewModel();
        model.setName(name);
        createdModels.add(model);
        return model;
    }

    private void closeOpenModels() throws IOException {
        List<IArchimateModel> models = new ArrayList<>(IEditorModelManager.INSTANCE.getModels());
        createdModels.clear();

        for(IArchimateModel model : models) {
            IEditorModelManager.INSTANCE.closeModel(model, false);
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
