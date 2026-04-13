/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.editor.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
public class MCPReadRuntimeSafetyTests {

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
    public void queryModelRunsThroughUiSafeRuntime() throws IOException {
        IArchimateModel model = createOpenModel("Runtime Model");

        HttpResult result = sendRequest("{\"id\":\"req-runtime-model\",\"method\":\"query\",\"params\":{\"queryType\":\"model\"}}");

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"modelId\":\"" + model.getId() + "\""));

        MCPReadTelemetry.Snapshot snapshot = MCPReadTelemetry.snapshot();
        assertTrue(snapshot.isExecutorInvoked());
        assertTrue(snapshot.isExecutorOnUiThread());
        assertNotNull(snapshot.getExecutorThreadName());
        assertTrue(snapshot.isContextBuilt());
        assertTrue(snapshot.isContextBuiltOnUiThread());
        assertNotNull(snapshot.getContextThreadName());
        assertEquals(model.getId(), snapshot.getResolvedModelId());
    }

    @Test
    public void queryViewsBuildsExecutionContextOnUiThread() throws IOException {
        IArchimateModel model = createOpenModel("Runtime Views Model");
        var secondView = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        secondView.setName("Runtime View 2");
        model.getDefaultFolderForObject(secondView).getElements().add(secondView);

        HttpResult result = sendRequest("{\"id\":\"req-runtime-views\",\"method\":\"query\",\"params\":{\"queryType\":\"views\"}}");

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"total\":2"));

        MCPReadTelemetry.Snapshot snapshot = MCPReadTelemetry.snapshot();
        assertTrue(snapshot.isExecutorInvoked());
        assertTrue(snapshot.isExecutorOnUiThread());
        assertTrue(snapshot.isContextBuilt());
        assertTrue(snapshot.isContextBuiltOnUiThread());
        assertEquals(model.getId(), snapshot.getResolvedModelId());
    }

    @Test
    public void multipleModelsRequireModelId() throws IOException {
        createOpenModel("Runtime A");
        createOpenModel("Runtime B");

        HttpResult result = sendRequest("{\"id\":\"req-runtime-ambiguous\",\"method\":\"query\",\"params\":{\"queryType\":\"views\"}}");

        assertEquals(400, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"MCP-PROTOCOL-BAD-QUERY\""));
        assertTrue(result.body.contains("\"reason\":\"modelId is required when multiple models are open\""));

        MCPReadTelemetry.Snapshot snapshot = MCPReadTelemetry.snapshot();
        assertTrue(snapshot.isExecutorInvoked());
        assertFalse(snapshot.isContextBuilt());
    }

    @Test
    public void unknownModelIdReturnsStructuredBadQuery() throws IOException {
        createOpenModel("Runtime Single");

        HttpResult result = sendRequest("""
                {
                  "id":"req-runtime-missing-model",
                  "method":"query",
                  "params":{"queryType":"model","modelId":"missing-model"}
                }
                """);

        assertEquals(400, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"MCP-PROTOCOL-BAD-QUERY\""));
        assertTrue(result.body.contains("\"reason\":\"modelId not found: missing-model\""));
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
