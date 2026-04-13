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
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
public class MCPReadRegressionTests {

    private static final Pattern FILE_URL_PATTERN = Pattern.compile("\"fileUrl\":\"([^\"]+)\"");

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
    public void initializeAndPingRemainStableAfterAddingQuery() throws IOException {
        HttpResult initializeResult = sendRequest("POST", "{\"id\":\"req-init\",\"method\":\"initialize\"}");
        HttpResult pingResult = sendRequest("POST", "{\"id\":\"req-ping\",\"method\":\"ping\"}");

        assertEquals(200, initializeResult.statusCode);
        assertEquals(200, pingResult.statusCode);
        assertTrue(initializeResult.body.contains("\"method\":\"initialize\""));
        assertTrue(initializeResult.body.contains("\"phase\":\"1\""));
        assertTrue(pingResult.body.contains("\"method\":\"ping\""));
        assertTrue(pingResult.body.contains("\"phase\":\"1\""));
    }

    @Test
    public void queryRelationshipsHasSuccessPathWithoutBreakingEnvelope() throws IOException {
        IArchimateModel model = createOpenModel("Regression Query Model");
        var source = IArchimateFactory.eINSTANCE.createBusinessActor();
        source.setName("Regression Source");
        model.getDefaultFolderForObject(source).getElements().add(source);
        var target = IArchimateFactory.eINSTANCE.createApplicationComponent();
        target.setName("Regression Target");
        model.getDefaultFolderForObject(target).getElements().add(target);
        var relationship = IArchimateFactory.eINSTANCE.createAssociationRelationship();
        relationship.setName("Regression Relationship");
        relationship.connect(source, target);
        model.getDefaultFolderForObject(relationship).getElements().add(relationship);

        HttpResult result = sendRequest("POST", """
                {
                  "id":"req-relationships",
                  "method":"query",
                  "params":{"queryType":"relationships","modelId":"%s"}
                }
                """.formatted(model.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"ok\":true"));
        assertTrue(result.body.contains("\"queryType\":\"relationships\""));
        assertTrue(result.body.contains("\"relationshipId\":\"" + relationship.getId() + "\""));
        assertTrue(result.body.contains("\"source\":{\"id\":\"" + source.getId() + "\""));
        assertTrue(result.body.contains("\"target\":{\"id\":\"" + target.getId() + "\""));
        assertTrue(result.body.contains("\"error\":null"));
    }

    @Test
    public void allReadQueryTypesRemainReachableAfterRelationshipSupport() throws IOException {
        IArchimateModel model = createOpenModel("Regression Read Model");
        var actor = IArchimateFactory.eINSTANCE.createBusinessActor();
        actor.setName("Regression Actor");
        model.getDefaultFolderForObject(actor).getElements().add(actor);
        var actorView = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
        actorView.setArchimateElement(actor);
        actorView.setBounds(20, 20, 120, 55);
        model.getDiagramModels().get(0).getChildren().add(actorView);
        var app = IArchimateFactory.eINSTANCE.createApplicationComponent();
        app.setName("Regression App");
        model.getDefaultFolderForObject(app).getElements().add(app);
        var relationship = IArchimateFactory.eINSTANCE.createAssociationRelationship();
        relationship.setName("Regression Link");
        relationship.connect(actor, app);
        model.getDefaultFolderForObject(relationship).getElements().add(relationship);

        HttpResult modelResult = sendRequest("POST",
                "{\"id\":\"req-model\",\"method\":\"query\",\"params\":{\"queryType\":\"model\",\"modelId\":\"" + model.getId() + "\"}}");
        HttpResult viewsResult = sendRequest("POST",
                "{\"id\":\"req-views\",\"method\":\"query\",\"params\":{\"queryType\":\"views\",\"modelId\":\"" + model.getId() + "\"}}");
        HttpResult elementsResult = sendRequest("POST", """
                {
                  "id":"req-elements",
                  "method":"query",
                  "params":{"queryType":"elements","modelId":"%s","filters":{"text":"Regression Actor"}}
                }
                """.formatted(model.getId()));
        HttpResult relationshipsResult = sendRequest("POST", """
                {
                  "id":"req-relationships-final",
                  "method":"query",
                  "params":{"queryType":"relationships","modelId":"%s","filters":{"text":"Regression Link"}}
                }
                """.formatted(model.getId()));

        assertEquals(200, modelResult.statusCode);
        assertEquals(200, viewsResult.statusCode);
        assertEquals(200, elementsResult.statusCode);
        assertEquals(200, relationshipsResult.statusCode);
        assertTrue(modelResult.body.contains("\"queryType\":\"model\""));
        assertTrue(viewsResult.body.contains("\"queryType\":\"views\""));
        assertTrue(elementsResult.body.contains("\"queryType\":\"elements\""));
        assertTrue(relationshipsResult.body.contains("\"queryType\":\"relationships\""));
        assertTrue(relationshipsResult.body.contains("\"relationshipId\":\"" + relationship.getId() + "\""));
    }

    @Test
    public void mcpEndpointRemainsPostOnlyAfterAddingQuery() throws IOException {
        HttpResult result = sendRequest("GET", null);

        assertEquals(405, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"MCP-PROTOCOL-UNSUPPORTED-HTTP-METHOD\""));
    }

    @Test
    public void malformedJsonStillReturnsStructuredBadRequest() throws IOException {
        HttpResult result = sendRequest("POST", "{\"id\":\"req-malformed\",\"method\":\"query\"");

        assertEquals(400, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"MCP-PROTOCOL-BAD-REQUEST\""));
        assertTrue(result.body.contains("\"error\":{"));
        assertTrue(result.body.contains("\"details\":{"));
    }

    @Test
    public void unknownMethodStillReturnsStructuredUnsupportedMethod() throws IOException {
        HttpResult result = sendRequest("POST", "{\"id\":\"req-unknown-method\",\"method\":\"READ-model\"}");

        assertEquals(400, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"MCP-PROTOCOL-UNSUPPORTED-METHOD\""));
    }

    @Test
    public void stopCleanupRemovesExportedTempFilesAndReadContractsRemainStable() throws Exception {
        IArchimateModel model = createOpenModel("Regression Export Model");
        var actor = IArchimateFactory.eINSTANCE.createBusinessActor();
        actor.setName("Regression Export Actor");
        model.getDefaultFolderForObject(actor).getElements().add(actor);
        var actorView = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
        actorView.setArchimateElement(actor);
        actorView.setBounds(30, 30, 140, 60);
        model.getDiagramModels().get(0).getChildren().add(actorView);

        HttpResult exportResult = sendRequest("POST", """
                {
                  "id":"req-export-cleanup",
                  "method":"exportView",
                  "params":{"viewId":"%s"}
                }
                """.formatted(model.getDiagramModels().get(0).getId()));
        HttpResult queryResult = sendRequest("POST",
                "{\"id\":\"req-query-after-export\",\"method\":\"query\",\"params\":{\"queryType\":\"views\",\"modelId\":\""
                        + model.getId() + "\"}}");

        assertEquals(200, exportResult.statusCode);
        assertEquals(200, queryResult.statusCode);
        assertTrue(queryResult.body.contains("\"queryType\":\"views\""));

        Path imagePath = Path.of(URI.create(extract(exportResult.body, FILE_URL_PATTERN)));
        assertTrue(Files.exists(imagePath));

        MCPServerBootstrap.stop();

        assertFalse(Files.exists(imagePath));
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

    private String extract(String body, Pattern pattern) {
        Matcher matcher = pattern.matcher(body);
        assertTrue(matcher.find(), body);
        return matcher.group(1);
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
