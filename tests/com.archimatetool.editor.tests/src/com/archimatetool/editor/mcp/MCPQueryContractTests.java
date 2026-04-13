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
import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelReference;

@SuppressWarnings("nls")
public class MCPQueryContractTests {

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
    public void queryModelReturnsStructuredSuccessEnvelope() throws IOException {
        IArchimateModel model = createOpenModel("Contract Model");
        model.setPurpose("Read contract");

        HttpResult result = sendRequest("{\"id\":\"req-query-model\",\"method\":\"query\",\"params\":{\"queryType\":\"model\"}}");

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"ok\":true"));
        assertTrue(result.body.contains("\"data\":{"));
        assertTrue(result.body.contains("\"queryType\":\"model\""));
        assertTrue(result.body.contains("\"item\":{"));
        assertTrue(result.body.contains("\"modelId\":\"" + model.getId() + "\""));
        assertTrue(result.body.contains("\"name\":\"Contract Model\""));
        assertTrue(result.body.contains("\"filePath\":null"));
        assertTrue(result.body.contains("\"isFileBacked\":false"));
        assertTrue(result.body.contains("\"viewCount\":1"));
        assertTrue(result.body.contains("\"elementCount\":0"));
        assertTrue(result.body.contains("\"relationshipCount\":0"));
        assertTrue(result.body.contains("\"error\":null"));
    }

    @Test
    public void queryElementsAcceptsNestedFiltersAndFlatPaging() throws IOException {
        IArchimateModel model = createOpenModel("Elements Contract Model");
        var actor = IArchimateFactory.eINSTANCE.createBusinessActor();
        actor.setName("Contract Actor");
        actor.getProperties().add(IArchimateFactory.eINSTANCE.createProperty("owner", "Contract Team"));
        model.getDefaultFolderForObject(actor).getElements().add(actor);
        var actorView = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
        actorView.setArchimateElement(actor);
        actorView.setBounds(20, 20, 120, 55);
        model.getDiagramModels().get(0).getChildren().add(actorView);

        String request = """
                {
                  "id":"req-query-elements",
                  "method":"query",
                  "params":{
                    "queryType":"elements",
                    "modelId":"%s",
                    "filters":{
                      "text":"Contract",
                      "searchIn":["name","documentation"],
                      "types":["business-actor"],
                      "propertyKeys":["owner"],
                      "viewIds":["%s"]
                    },
                    "limit":25,
                    "offset":0
                  }
                }
                """.formatted(model.getId(), model.getDiagramModels().get(0).getId());

        HttpResult result = sendRequest(request);

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"queryType\":\"elements\""));
        assertTrue(result.body.contains("\"model\":{\"modelId\":\"" + model.getId() + "\",\"name\":\"Elements Contract Model\"}"));
        assertTrue(result.body.contains("\"elementId\":\"" + actor.getId() + "\""));
        assertTrue(result.body.contains("\"type\":\"business-actor\""));
        assertTrue(result.body.contains("\"total\":1"));
        assertTrue(result.body.contains("\"offset\":0"));
        assertTrue(result.body.contains("\"limit\":25"));
        assertTrue(result.body.contains("\"hasMore\":false"));
    }

    @Test
    public void queryViewsUsesDefaultPagination() throws IOException {
        IArchimateModel model = createOpenModel("Views Contract Model");
        model.getDiagramModels().get(0).setName("Primary View");
        model.getDiagramModels().get(0).setDocumentation("Primary documentation");

        var secondView = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        secondView.setName("Secondary View");
        model.getDefaultFolderForObject(secondView).getElements().add(secondView);

        IDiagramModelReference reference = IArchimateFactory.eINSTANCE.createDiagramModelReference();
        reference.setReferencedModel(model.getDiagramModels().get(0));
        secondView.getChildren().add(reference);

        HttpResult result = sendRequest("{\"id\":\"req-query-views\",\"method\":\"query\",\"params\":{\"queryType\":\"views\"}}");

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"queryType\":\"views\""));
        assertTrue(result.body.contains("\"model\":{\"modelId\":\"" + model.getId() + "\",\"name\":\"Views Contract Model\"}"));
        assertTrue(result.body.contains("\"viewId\":\"" + model.getDiagramModels().get(0).getId() + "\""));
        assertTrue(result.body.contains("\"name\":\"Primary View\""));
        assertTrue(result.body.contains("\"documentation\":\"Primary documentation\""));
        assertTrue(result.body.contains("\"hasViewReference\":true"));
        assertTrue(result.body.contains("\"total\":2"));
        assertTrue(result.body.contains("\"offset\":0"));
        assertTrue(result.body.contains("\"limit\":50"));
        assertTrue(result.body.contains("\"hasMore\":false"));
    }

    @Test
    public void missingModelIdWithMultipleOpenModelsReturnsStructuredBadQuery() throws IOException {
        createOpenModel("Model A");
        createOpenModel("Model B");

        HttpResult result = sendRequest("{\"id\":\"req-model-ambiguous\",\"method\":\"query\",\"params\":{\"queryType\":\"model\"}}");

        assertEquals(400, result.statusCode);
        assertErrorSchema(result.body);
        assertTrue(result.body.contains("\"code\":\"MCP-PROTOCOL-BAD-QUERY\""));
        assertTrue(result.body.contains("\"reason\":\"modelId is required when multiple models are open\""));
    }

    @Test
    public void missingQueryTypeReturnsStructuredBadQuery() throws IOException {
        HttpResult result = sendRequest("{\"id\":\"req-bad-query\",\"method\":\"query\",\"params\":{}}");

        assertEquals(400, result.statusCode);
        assertErrorSchema(result.body);
        assertTrue(result.body.contains("\"code\":\"MCP-PROTOCOL-BAD-QUERY\""));
    }

    @Test
    public void unknownQueryTypeReturnsStructuredUnsupportedQueryType() throws IOException {
        HttpResult result = sendRequest("{\"id\":\"req-unknown-query\",\"method\":\"query\",\"params\":{\"queryType\":\"unknown\"}}");

        assertEquals(400, result.statusCode);
        assertErrorSchema(result.body);
        assertTrue(result.body.contains("\"code\":\"MCP-PROTOCOL-UNSUPPORTED-QUERY-TYPE\""));
        assertTrue(result.body.contains("\"reason\":\"unknown queryType: unknown\""));
    }

    @Test
    public void unknownFilterFieldReturnsStructuredBadQuery() throws IOException {
        HttpResult result = sendRequest("""
                {
                  "id":"req-unknown-filter",
                  "method":"query",
                  "params":{
                    "queryType":"elements",
                    "filters":{"unknown":"value"}
                  }
                }
                """);

        assertEquals(400, result.statusCode);
        assertErrorSchema(result.body);
        assertTrue(result.body.contains("\"code\":\"MCP-PROTOCOL-BAD-QUERY\""));
        assertTrue(result.body.contains("\"reason\":\"unknown filter field: unknown\""));
    }

    @Test
    public void negativeLimitReturnsStructuredBadQuery() throws IOException {
        HttpResult result = sendRequest("""
                {
                  "id":"req-negative-limit",
                  "method":"query",
                  "params":{
                    "queryType":"relationships",
                    "limit":-1
                  }
                }
                """);

        assertEquals(400, result.statusCode);
        assertErrorSchema(result.body);
        assertTrue(result.body.contains("\"code\":\"MCP-PROTOCOL-BAD-QUERY\""));
        assertTrue(result.body.contains("\"reason\":\"limit must be a non-negative integer\""));
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
