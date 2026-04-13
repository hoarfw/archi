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

import org.eclipse.gef.commands.CommandStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;
import com.archimatetool.mcp.bootstrap.MCPWriteTelemetry;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;

@SuppressWarnings("nls")
public class MCPWriteRuntimeSafetyTests {

    private int port;
    private final List<IArchimateModel> createdModels = new ArrayList<>();

    @BeforeEach
    public void startServer() throws IOException {
        MCPServerBootstrap.stop();
        closeOpenModels();
        MCPWriteTelemetry.clear();
        port = allocatePort();
        MCPServerBootstrap.start(MCPServerBootstrap.LOOPBACK_V4, port);
    }

    @AfterEach
    public void stopServer() throws IOException {
        MCPServerBootstrap.stop();
        closeOpenModels();
        MCPWriteTelemetry.clear();
    }

    @Test
    public void writeBuildsExecutionContextOnUiThreadBeforeDispatchingOperation() throws IOException {
        IArchimateModel model = createOpenModel("Write Runtime Model");

        HttpResult result = sendRequest("""
                {
                  "id":"req-write-runtime",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "operation":"createElement",
                    "payload":{"type":"business-actor","name":"Runtime Actor"}
                  }
                }
                """.formatted(model.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"operation\":\"createElement\""));

        MCPWriteTelemetry.Snapshot snapshot = MCPWriteTelemetry.snapshot();
        assertTrue(snapshot.isExecutorInvoked());
        assertTrue(snapshot.isExecutorOnUiThread());
        assertNotNull(snapshot.getExecutorThreadName());
        assertTrue(snapshot.isContextBuilt());
        assertTrue(snapshot.isContextBuiltOnUiThread());
        assertNotNull(snapshot.getContextThreadName());
        assertEquals(model.getId(), snapshot.getResolvedModelId());
        assertTrue(snapshot.isCommandStackAvailable());
    }

    @Test
    public void writeRejectsWhenNoModelsAreOpenBeforeBuildingContext() throws IOException {
        HttpResult result = sendRequest("""
                {
                  "id":"req-write-no-model",
                  "method":"write",
                  "params":{
                    "operation":"createElement",
                    "payload":{"type":"business-actor","name":"No Model"}
                  }
                }
                """);

        assertEquals(404, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"MCP-WRITE-MODEL-NOT-FOUND\""));
        assertTrue(result.body.contains("\"reason\":\"no open models\""));

        MCPWriteTelemetry.Snapshot snapshot = MCPWriteTelemetry.snapshot();
        assertTrue(snapshot.isExecutorInvoked());
        assertFalse(snapshot.isContextBuilt());
    }

    @Test
    public void writeRejectsUnknownViewIdBeforeExecution() throws IOException {
        IArchimateModel model = createOpenModel("Write Runtime View Model");
        var actor = IArchimateFactory.eINSTANCE.createBusinessActor();
        actor.setName("View Actor");
        model.getDefaultFolderForObject(actor).getElements().add(actor);

        HttpResult result = sendRequest("""
                {
                  "id":"req-write-bad-view",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "viewId":"missing-view",
                    "operation":"addElementToView",
                    "payload":{
                      "elementId":"%s",
                      "bounds":{"x":20,"y":40,"width":120,"height":55}
                    }
                  }
                }
                """.formatted(model.getId(), actor.getId()));

        assertEquals(404, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"MCP-WRITE-VIEW-NOT-FOUND\""));
        assertTrue(result.body.contains("\"reason\":\"viewId not found: missing-view\""));

        MCPWriteTelemetry.Snapshot snapshot = MCPWriteTelemetry.snapshot();
        assertTrue(snapshot.isExecutorInvoked());
        assertFalse(snapshot.isContextBuilt());
    }

    @Test
    public void writeRejectsMissingCommandStackBeforeExecution() throws IOException {
        IArchimateModel model = createOpenModel("Write Missing Stack Model");
        model.setAdapter(CommandStack.class, null);

        HttpResult result = sendRequest("""
                {
                  "id":"req-write-missing-stack",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "operation":"createElement",
                    "payload":{"type":"business-actor","name":"No Stack"}
                  }
                }
                """.formatted(model.getId()));

        assertEquals(409, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"MCP-WRITE-COMMAND-STACK-UNAVAILABLE\""));
        assertTrue(result.body.contains("\"reason\":\"CommandStack not available for model: " + model.getId() + "\""));

        MCPWriteTelemetry.Snapshot snapshot = MCPWriteTelemetry.snapshot();
        assertTrue(snapshot.isExecutorInvoked());
        assertFalse(snapshot.isContextBuilt());
    }

    @Test
    public void writeRequiresModelIdWhenMultipleModelsAreOpen() throws IOException {
        createOpenModel("Write Runtime A");
        createOpenModel("Write Runtime B");

        HttpResult result = sendRequest("""
                {
                  "id":"req-write-ambiguous-model",
                  "method":"write",
                  "params":{
                    "operation":"createElement",
                    "payload":{"type":"business-actor","name":"Ambiguous"}
                  }
                }
                """);

        assertEquals(400, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"MCP-WRITE-BAD-REQUEST\""));
        assertTrue(result.body.contains("\"reason\":\"modelId is required when multiple models are open\""));

        MCPWriteTelemetry.Snapshot snapshot = MCPWriteTelemetry.snapshot();
        assertTrue(snapshot.isExecutorInvoked());
        assertFalse(snapshot.isContextBuilt());
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
