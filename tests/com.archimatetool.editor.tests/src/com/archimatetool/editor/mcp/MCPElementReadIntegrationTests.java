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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;
import com.archimatetool.model.IApplicationComponent;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IBusinessActor;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IServingRelationship;
import com.archimatetool.testingtools.ArchimateTestModel;

@SuppressWarnings("nls")
public class MCPElementReadIntegrationTests {

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
    public void queryElementsReturnsAllViewOccurrencesAndRelationshipIds() throws IOException {
        ElementFixture fixture = createElementFixture();

        HttpResult result = sendRequest("""
                {
                  "id":"req-elements-occurrences",
                  "method":"query",
                  "params":{
                    "queryType":"elements",
                    "modelId":"%s",
                    "filters":{"text":"Alpha"}
                  }
                }
                """.formatted(fixture.model.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"queryType\":\"elements\""));
        assertTrue(result.body.contains("\"elementId\":\"" + fixture.alpha.getId() + "\""));
        assertTrue(result.body.contains("\"type\":\"business-actor\""));
        assertTrue(result.body.contains("\"sourceRelationshipIds\":[\"" + fixture.alphaServing.getId() + "\"]"));
        assertTrue(result.body.contains("\"targetRelationshipIds\":[\"" + fixture.betaServing.getId() + "\"]"));
        assertTrue(result.body.contains("\"key\":\"owner\""));
        assertTrue(result.body.contains("\"value\":\"Alice\""));
        assertTrue(result.body.contains("\"objectId\":\"" + fixture.alphaNested.getId() + "\""));
        assertTrue(result.body.contains("\"objectId\":\"" + fixture.alphaStandalone.getId() + "\""));
        assertTrue(result.body.contains("\"objectId\":\"" + fixture.alphaSecondary.getId() + "\""));
        assertEquals(2, countOccurrences(result.body, "\"viewId\":\"" + fixture.mainView.getId() + "\""));
        assertEquals(1, countOccurrences(result.body, "\"viewId\":\"" + fixture.secondaryView.getId() + "\""));
        assertTrue(result.body.contains("\"parentObjectId\":\"" + fixture.parentObject.getId() + "\""));
        assertTrue(result.body.contains("\"parentType\":\"application-component\""));
        assertTrue(result.body.contains("\"parentObjectId\":null"));
    }

    @Test
    public void queryElementsUsesDefaultSearchInNameOnly() throws IOException {
        ElementFixture fixture = createElementFixture();

        HttpResult result = sendRequest("""
                {
                  "id":"req-elements-default-search",
                  "method":"query",
                  "params":{
                    "queryType":"elements",
                    "modelId":"%s",
                    "filters":{"text":"Alpha property only"}
                  }
                }
                """.formatted(fixture.model.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"queryType\":\"elements\""));
        assertTrue(result.body.contains("\"items\":[]"));
        assertTrue(result.body.contains("\"total\":0"));
    }

    @Test
    public void queryElementsAppliesPropertyValueFilteringAndViewContextTrimming() throws IOException {
        ElementFixture fixture = createElementFixture();

        HttpResult result = sendRequest("""
                {
                  "id":"req-elements-property-view",
                  "method":"query",
                  "params":{
                    "queryType":"elements",
                    "modelId":"%s",
                    "filters":{
                      "text":"Alice",
                      "searchIn":["propertyValue"],
                      "propertyKeys":["owner"],
                      "viewIds":["%s"]
                    }
                  }
                }
                """.formatted(fixture.model.getId(), fixture.secondaryView.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"elementId\":\"" + fixture.alpha.getId() + "\""));
        assertTrue(result.body.contains("\"total\":1"));
        assertTrue(result.body.contains("\"viewId\":\"" + fixture.secondaryView.getId() + "\""));
        assertFalse(result.body.contains("\"objectId\":\"" + fixture.alphaNested.getId() + "\""));
        assertFalse(result.body.contains("\"objectId\":\"" + fixture.alphaStandalone.getId() + "\""));
    }

    @Test
    public void queryElementsSortsByIdBeforePaging() throws IOException {
        ElementFixture fixture = createElementFixture();

        HttpResult fullResult = sendRequest("""
                {
                  "id":"req-elements-full-order",
                  "method":"query",
                  "params":{
                    "queryType":"elements",
                    "modelId":"%s",
                    "filters":{"types":["business-actor"]},
                    "offset":0,
                    "limit":10
                  }
                }
                """.formatted(fixture.model.getId()));
        List<String> orderedIds = extractElementIds(fullResult.body);

        HttpResult result = sendRequest("""
                {
                  "id":"req-elements-paging",
                  "method":"query",
                  "params":{
                    "queryType":"elements",
                    "modelId":"%s",
                    "filters":{"types":["business-actor"]},
                    "offset":1,
                    "limit":1
                  }
                }
                """.formatted(fixture.model.getId()));

        assertEquals(200, fullResult.statusCode, fullResult.body);
        assertTrue(orderedIds.size() >= 2, fullResult.body);
        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"offset\":1"));
        assertTrue(result.body.contains("\"limit\":1"));
        assertTrue(result.body.contains("\"elementId\":\"" + orderedIds.get(1) + "\""));
        assertFalse(result.body.contains("\"elementId\":\"" + orderedIds.get(0) + "\""));
    }

    private ElementFixture createElementFixture() {
        IArchimateModel model = IEditorModelManager.INSTANCE.createNewModel();
        model.setName("Element Query Model");
        createdModels.add(model);

        IDiagramModel mainView = model.getDiagramModels().get(0);
        mainView.setName("Main View");

        IDiagramModel secondaryView = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        secondaryView.setName("Secondary View");
        model.getDefaultFolderForObject(secondaryView).getElements().add(secondaryView);

        IBusinessActor alpha = IArchimateFactory.eINSTANCE.createBusinessActor();
        alpha.setName("Alpha Actor");
        alpha.setDocumentation("Primary actor");
        alpha.getProperties().add(IArchimateFactory.eINSTANCE.createProperty("owner", "Alice"));
        alpha.getProperties().add(IArchimateFactory.eINSTANCE.createProperty("notes", "Alpha property only"));
        model.getDefaultFolderForObject(alpha).getElements().add(alpha);

        IBusinessActor beta = IArchimateFactory.eINSTANCE.createBusinessActor();
        beta.setName("Beta Actor");
        beta.setDocumentation("Secondary actor");
        beta.getProperties().add(IArchimateFactory.eINSTANCE.createProperty("owner", "Bob"));
        model.getDefaultFolderForObject(beta).getElements().add(beta);

        IBusinessActor gamma = IArchimateFactory.eINSTANCE.createBusinessActor();
        gamma.setName("Gamma Actor");
        model.getDefaultFolderForObject(gamma).getElements().add(gamma);

        IApplicationComponent parent = IArchimateFactory.eINSTANCE.createApplicationComponent();
        parent.setName("Parent App");
        model.getDefaultFolderForObject(parent).getElements().add(parent);

        IServingRelationship alphaServing = IArchimateFactory.eINSTANCE.createServingRelationship();
        alphaServing.connect(alpha, parent);
        model.getDefaultFolderForObject(alphaServing).getElements().add(alphaServing);

        IServingRelationship betaServing = IArchimateFactory.eINSTANCE.createServingRelationship();
        betaServing.connect(beta, alpha);
        model.getDefaultFolderForObject(betaServing).getElements().add(betaServing);

        IDiagramModelArchimateObject parentObject = ArchimateTestModel.createDiagramModelArchimateObject(parent);
        parentObject.setName("Parent Container");
        parentObject.setBounds(20, 20, 360, 260);
        mainView.getChildren().add(parentObject);

        IDiagramModelArchimateObject alphaNested = ArchimateTestModel.createDiagramModelArchimateObject(alpha);
        alphaNested.setName("Alpha Nested");
        alphaNested.setBounds(40, 50, 120, 55);
        parentObject.getChildren().add(alphaNested);

        IDiagramModelArchimateObject alphaStandalone = ArchimateTestModel.createDiagramModelArchimateObject(alpha);
        alphaStandalone.setName("Alpha Standalone");
        alphaStandalone.setBounds(460, 80, 120, 55);
        mainView.getChildren().add(alphaStandalone);

        IDiagramModelArchimateObject alphaSecondary = ArchimateTestModel.createDiagramModelArchimateObject(alpha);
        alphaSecondary.setName("Alpha Secondary");
        alphaSecondary.setBounds(110, 100, 120, 55);
        secondaryView.getChildren().add(alphaSecondary);

        IDiagramModelArchimateObject betaSecondary = ArchimateTestModel.createDiagramModelArchimateObject(beta);
        betaSecondary.setName("Beta Secondary");
        betaSecondary.setBounds(300, 100, 120, 55);
        secondaryView.getChildren().add(betaSecondary);

        IDiagramModelArchimateObject gammaSecondary = ArchimateTestModel.createDiagramModelArchimateObject(gamma);
        gammaSecondary.setName("Gamma Secondary");
        gammaSecondary.setBounds(500, 100, 120, 55);
        secondaryView.getChildren().add(gammaSecondary);

        return new ElementFixture(model, alpha, beta, gamma, parent, mainView, secondaryView, alphaServing, betaServing,
                parentObject, alphaNested, alphaStandalone, alphaSecondary);
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

    private int countOccurrences(String body, String needle) {
        int count = 0;
        int start = 0;

        while(true) {
            int index = body.indexOf(needle, start);
            if(index < 0) {
                return count;
            }

            count++;
            start = index + needle.length();
        }
    }

    private List<String> extractElementIds(String body) {
        Pattern pattern = Pattern.compile("\"elementId\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(body);
        List<String> ids = new ArrayList<>();

        while(matcher.find()) {
            ids.add(matcher.group(1));
        }

        return ids;
    }

    private void closeOpenModels() throws IOException {
        List<IArchimateModel> models = new ArrayList<>(IEditorModelManager.INSTANCE.getModels());
        createdModels.clear();

        for(IArchimateModel model : models) {
            IEditorModelManager.INSTANCE.closeModel(model, false);
        }
    }

    private record ElementFixture(IArchimateModel model, IBusinessActor alpha, IBusinessActor beta, IBusinessActor gamma,
            IApplicationComponent parent, IDiagramModel mainView, IDiagramModel secondaryView,
            IServingRelationship alphaServing, IServingRelationship betaServing, IDiagramModelArchimateObject parentObject,
            IDiagramModelArchimateObject alphaNested, IDiagramModelArchimateObject alphaStandalone,
            IDiagramModelArchimateObject alphaSecondary) {
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
