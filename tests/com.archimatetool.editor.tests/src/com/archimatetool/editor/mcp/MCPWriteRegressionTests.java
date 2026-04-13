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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;

@SuppressWarnings("nls")
public class MCPWriteRegressionTests {

    private static final Pattern ELEMENT_ID_PATTERN = Pattern.compile("\"elementId\":\"([^\"]+)\"");
    private static final Pattern OBJECT_ID_PATTERN = Pattern.compile("\"objectId\":\"([^\"]+)\"");
    private static final Pattern RELATIONSHIP_ID_PATTERN = Pattern.compile("\"relationshipId\":\"([^\"]+)\"");

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
    public void legacyMethodsRemainReachableAfterWritePhase() throws IOException {
        IArchimateModel model = createOpenModel("Write Regression Model");
        model.getDiagramModels().get(0).setName("Regression View");

        HttpResult initialize = sendRequest("{\"id\":\"req-reg-init\",\"method\":\"initialize\"}");
        HttpResult ping = sendRequest("{\"id\":\"req-reg-ping\",\"method\":\"ping\"}");
        HttpResult query = sendRequest("""
                {
                  "id":"req-reg-query",
                  "method":"query",
                  "params":{"queryType":"model","modelId":"%s"}
                }
                """.formatted(model.getId()));
        HttpResult export = sendRequest("""
                {
                  "id":"req-reg-export",
                  "method":"exportView",
                  "params":{"viewId":"%s"}
                }
                """.formatted(model.getDiagramModels().get(0).getId()));

        assertEquals(200, initialize.statusCode);
        assertEquals(200, ping.statusCode);
        assertEquals(200, query.statusCode);
        assertEquals(200, export.statusCode);
        assertTrue(export.body.contains("\"method\":\"exportView\""));
    }

    @Test
    public void writeElementAndRelationshipFlowCompletesThroughSingleEndpoint() throws IOException {
        IArchimateModel model = createOpenModel("Write End To End Model");
        String sourceElementId = extract(ELEMENT_ID_PATTERN, sendRequest(createElementRequest(model.getId(), "Source")).body);
        String targetElementId = extract(ELEMENT_ID_PATTERN, sendRequest(createElementRequest(model.getId(), "Target")).body);

        String sourceObjectId = extract(OBJECT_ID_PATTERN,
                sendRequest(addElementToViewRequest(model.getId(), model.getDiagramModels().get(0).getId(), sourceElementId, 20, 30)).body);
        String targetObjectId = extract(OBJECT_ID_PATTERN,
                sendRequest(addElementToViewRequest(model.getId(), model.getDiagramModels().get(0).getId(), targetElementId, 220, 30)).body);

        HttpResult relationshipResult = sendRequest("""
                {
                  "id":"req-reg-create-relationship",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "operation":"createRelationship",
                    "payload":{
                      "relationshipType":"assignment-relationship",
                      "sourceElementId":"%s",
                      "targetElementId":"%s"
                    }
                  }
                }
                """.formatted(model.getId(), sourceElementId, targetElementId));
        String relationshipId = extract(RELATIONSHIP_ID_PATTERN, relationshipResult.body);

        HttpResult connectResult = sendRequest("""
                {
                  "id":"req-reg-connect-relationship",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "viewId":"%s",
                    "operation":"connectRelationshipInView",
                    "payload":{
                      "relationshipId":"%s",
                      "sourceObjectId":"%s",
                      "targetObjectId":"%s"
                    }
                  }
                }
                """.formatted(model.getId(), model.getDiagramModels().get(0).getId(), relationshipId, sourceObjectId, targetObjectId));

        assertEquals(200, relationshipResult.statusCode);
        assertTrue(relationshipResult.body.contains("\"operation\":\"createRelationship\""));
        assertEquals(200, connectResult.statusCode);
        assertTrue(connectResult.body.contains("\"operation\":\"connectRelationshipInView\""));
        assertTrue(connectResult.body.contains("\"connection\":{"));
    }

    @Test
    public void writeErrorsRemainStructuredForUnsupportedAndMismatchedTargets() throws IOException {
        IArchimateModel model = createOpenModel("Write Error Regression");
        String sourceElementId = extract(ELEMENT_ID_PATTERN, sendRequest(createElementRequest(model.getId(), "Source")).body);
        String targetElementId = extract(ELEMENT_ID_PATTERN, sendRequest(createElementRequest(model.getId(), "Target")).body);
        String sourceObjectId = extract(OBJECT_ID_PATTERN,
                sendRequest(addElementToViewRequest(model.getId(), model.getDiagramModels().get(0).getId(), sourceElementId, 20, 30)).body);
        String targetObjectId = extract(OBJECT_ID_PATTERN,
                sendRequest(addElementToViewRequest(model.getId(), model.getDiagramModels().get(0).getId(), targetElementId, 220, 30)).body);
        String relationshipId = extract(RELATIONSHIP_ID_PATTERN, sendRequest("""
                {
                  "id":"req-reg-create-relationship-2",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "operation":"createRelationship",
                    "payload":{
                      "relationshipType":"assignment-relationship",
                      "sourceElementId":"%s",
                      "targetElementId":"%s"
                    }
                  }
                }
                """.formatted(model.getId(), sourceElementId, targetElementId)).body);

        HttpResult unsupported = sendRequest("""
                {
                  "id":"req-reg-unsupported-op",
                  "method":"write",
                  "params":{"operation":"unknown","payload":{}}
                }
                """);
        HttpResult mismatched = sendRequest("""
                {
                  "id":"req-reg-mismatched-connect",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "viewId":"%s",
                    "operation":"connectRelationshipInView",
                    "payload":{
                      "relationshipId":"%s",
                      "sourceObjectId":"%s",
                      "targetObjectId":"%s"
                    }
                  }
                }
                """.formatted(model.getId(), model.getDiagramModels().get(0).getId(), relationshipId, targetObjectId, sourceObjectId));

        assertEquals(400, unsupported.statusCode);
        assertTrue(unsupported.body.contains("\"ok\":false"));
        assertTrue(unsupported.body.contains("\"code\":\"MCP-WRITE-UNSUPPORTED-OPERATION\""));
        assertEquals(400, mismatched.statusCode);
        assertTrue(mismatched.body.contains("\"ok\":false"));
        assertTrue(mismatched.body.contains("\"code\":\"MCP-WRITE-BAD-REQUEST\""));
    }

    private String createElementRequest(String modelId, String name) {
        return """
                {
                  "id":"req-reg-create-element-%s",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "operation":"createElement",
                    "payload":{"type":"business-actor","name":"%s"}
                  }
                }
                """.formatted(name, modelId, name);
    }

    private String addElementToViewRequest(String modelId, String viewId, String elementId, int x, int y) {
        return """
                {
                  "id":"req-reg-add-%s",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "viewId":"%s",
                    "operation":"addElementToView",
                    "payload":{
                      "elementId":"%s",
                      "bounds":{"x":%d,"y":%d}
                    }
                  }
                }
                """.formatted(elementId, modelId, viewId, elementId, Integer.valueOf(x), Integer.valueOf(y));
    }

    private String extract(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        assertTrue(matcher.find(), "pattern not found: " + pattern);
        return matcher.group(1);
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
