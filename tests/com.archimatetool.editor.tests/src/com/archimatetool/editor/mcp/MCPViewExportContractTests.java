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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.mcp.bootstrap.MCPReadTelemetry;
import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;

@SuppressWarnings("nls")
public class MCPViewExportContractTests {

    private int port;
    private final List<IArchimateModel> createdModels = new ArrayList<>();

    @BeforeEach
    public void startServer() throws IOException {
        MCPServerBootstrap.stop();
        closeOpenModels();
        MCPReadTelemetry.clear();
        port = allocatePort();
        MCPServerBootstrap.start(MCPServerBootstrap.LOOPBACK_V4, port);
    }

    @AfterEach
    public void stopServer() throws IOException {
        MCPServerBootstrap.stop();
        closeOpenModels();
        MCPReadTelemetry.clear();
    }

    @Test
    public void exportViewReturnsStructuredContractWithoutQueryType() throws IOException {
        IArchimateModel model = createOpenModel("Export Contract Model");
        model.getDiagramModels().get(0).setName("Contract View");

        HttpResult result = sendRequest("""
                {
                  "id":"req-export-contract",
                  "method":"exportView",
                  "params":{"viewId":"%s"}
                }
                """.formatted(model.getDiagramModels().get(0).getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"ok\":true"));
        assertTrue(result.body.contains("\"method\":\"exportView\""));
        assertTrue(result.body.contains("\"model\":{\"modelId\":\"" + model.getId() + "\""));
        assertTrue(result.body.contains("\"view\":{\"viewId\":\"" + model.getDiagramModels().get(0).getId() + "\""));
        assertTrue(result.body.contains("\"format\":\"png\""));
        assertTrue(result.body.contains("\"mimeType\":\"image/png\""));
        assertTrue(result.body.contains("\"scale\":1.0"));
        assertTrue(result.body.contains("\"fileUrl\":\"file:"));
        assertTrue(result.body.contains("\"dataUrl\":\"data:image/png;base64,"));
        assertTrue(result.body.contains("\"error\":null"));
        assertFalse(result.body.contains("\"queryType\""));
    }

    @Test
    public void exportViewRejectsMissingViewId() throws IOException {
        HttpResult result = sendRequest("""
                {
                  "id":"req-export-missing-view",
                  "method":"exportView",
                  "params":{}
                }
                """);

        assertEquals(400, result.statusCode);
        assertErrorSchema(result.body);
        assertTrue(result.body.contains("\"code\":\"MCP-EXPORT-BAD-REQUEST\""));
        assertTrue(result.body.contains("\"reason\":\"viewId must be a non-empty string\""));
    }

    @Test
    public void exportViewRejectsInvalidScale() throws IOException {
        IArchimateModel model = createOpenModel("Export Invalid Scale Model");

        HttpResult result = sendRequest("""
                {
                  "id":"req-export-invalid-scale",
                  "method":"exportView",
                  "params":{"viewId":"%s","scale":0}
                }
                """.formatted(model.getDiagramModels().get(0).getId()));

        assertEquals(400, result.statusCode);
        assertErrorSchema(result.body);
        assertTrue(result.body.contains("\"code\":\"MCP-EXPORT-BAD-REQUEST\""));
        assertTrue(result.body.contains("\"reason\":\"scale must be greater than 0\""));
    }

    @Test
    public void exportViewRejectsUnknownViewId() throws IOException {
        createOpenModel("Export Unknown View Model");

        HttpResult result = sendRequest("""
                {
                  "id":"req-export-unknown-view",
                  "method":"exportView",
                  "params":{"viewId":"missing-view"}
                }
                """);

        assertEquals(404, result.statusCode);
        assertErrorSchema(result.body);
        assertTrue(result.body.contains("\"code\":\"MCP-EXPORT-VIEW-NOT-FOUND\""));
        assertTrue(result.body.contains("\"reason\":\"viewId not found: missing-view\""));
    }

    @Test
    public void queryRemainsReachableAfterAddingExportView() throws IOException {
        IArchimateModel model = createOpenModel("Query Regression Model");
        var actor = IArchimateFactory.eINSTANCE.createBusinessActor();
        actor.setName("Regression Actor");
        model.getDefaultFolderForObject(actor).getElements().add(actor);

        HttpResult result = sendRequest("""
                {
                  "id":"req-query-regression",
                  "method":"query",
                  "params":{"queryType":"model","modelId":"%s"}
                }
                """.formatted(model.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"queryType\":\"model\""));
        assertTrue(result.body.contains("\"modelId\":\"" + model.getId() + "\""));
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
        connection.setReadTimeout(2000);
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
