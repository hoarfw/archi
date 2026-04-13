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
import com.archimatetool.model.IAssociationRelationship;
import com.archimatetool.model.IBusinessActor;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IServingRelationship;
import com.archimatetool.testingtools.ArchimateTestModel;

@SuppressWarnings("nls")
public class MCPRelationshipReadIntegrationTests {

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
    public void queryRelationshipsReturnsConceptEndpointsAndObjectConnectionContext() throws IOException {
        RelationshipFixture fixture = createRelationshipFixture();

        HttpResult result = sendRequest("""
                {
                  "id":"req-relationships-main",
                  "method":"query",
                  "params":{
                    "queryType":"relationships",
                    "modelId":"%s",
                    "filters":{"text":"Alpha serves app"}
                  }
                }
                """.formatted(fixture.model.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"queryType\":\"relationships\""));
        assertTrue(result.body.contains("\"relationshipId\":\"" + fixture.serving.getId() + "\""));
        assertTrue(result.body.contains("\"type\":\"serving-relationship\""));
        assertTrue(result.body.contains("\"source\":{\"id\":\"" + fixture.alpha.getId() + "\""));
        assertTrue(result.body.contains("\"target\":{\"id\":\"" + fixture.app.getId() + "\""));
        assertTrue(result.body.contains("\"key\":\"strength\""));
        assertTrue(result.body.contains("\"value\":\"primary\""));
        assertTrue(result.body.contains("\"connectionId\":\"" + fixture.mainServingConnection.getId() + "\""));
        assertTrue(result.body.contains("\"connectionId\":\"" + fixture.secondaryServingConnection.getId() + "\""));
        assertTrue(result.body.contains("\"kind\":\"object\""));
        assertTrue(result.body.contains("\"refId\":\"" + fixture.alphaMainObject.getId() + "\""));
        assertTrue(result.body.contains("\"conceptId\":\"" + fixture.alpha.getId() + "\""));
        assertTrue(result.body.contains("\"startX\":12"));
        assertTrue(result.body.contains("\"endY\":24"));
    }

    @Test
    public void queryRelationshipsAppliesSourceTargetFilteringAndViewTrimming() throws IOException {
        RelationshipFixture fixture = createRelationshipFixture();

        HttpResult result = sendRequest("""
                {
                  "id":"req-relationships-filtered",
                  "method":"query",
                  "params":{
                    "queryType":"relationships",
                    "modelId":"%s",
                    "filters":{
                      "sourceIds":["%s"],
                      "targetIds":["%s"],
                      "viewIds":["%s"]
                    }
                  }
                }
                """.formatted(fixture.model.getId(), fixture.alpha.getId(), fixture.app.getId(), fixture.secondaryView.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"relationshipId\":\"" + fixture.serving.getId() + "\""));
        assertTrue(result.body.contains("\"total\":1"));
        assertTrue(result.body.contains("\"viewId\":\"" + fixture.secondaryView.getId() + "\""));
        assertTrue(result.body.contains("\"connectionId\":\"" + fixture.secondaryServingConnection.getId() + "\""));
        assertFalse(result.body.contains("\"connectionId\":\"" + fixture.mainServingConnection.getId() + "\""));
        assertFalse(result.body.contains("\"relationshipId\":\"" + fixture.assignment.getId() + "\""));
    }

    @Test
    public void queryRelationshipsReturnsConnectionToConnectionRefs() throws IOException {
        RelationshipFixture fixture = createRelationshipFixture();

        HttpResult result = sendRequest("""
                {
                  "id":"req-relationships-connection-ref",
                  "method":"query",
                  "params":{
                    "queryType":"relationships",
                    "modelId":"%s",
                    "filters":{"text":"Connection trace"}
                  }
                }
                """.formatted(fixture.model.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"relationshipId\":\"" + fixture.trace.getId() + "\""));
        assertTrue(result.body.contains("\"source\":{\"id\":\"" + fixture.serving.getId() + "\""));
        assertTrue(result.body.contains("\"target\":{\"id\":\"" + fixture.assignment.getId() + "\""));
        assertTrue(result.body.contains("\"connectionId\":\"" + fixture.traceConnection.getId() + "\""));
        assertTrue(result.body.contains("\"kind\":\"connection\""));
        assertTrue(result.body.contains("\"refId\":\"" + fixture.mainServingConnection.getId() + "\""));
        assertTrue(result.body.contains("\"refId\":\"" + fixture.assignmentConnection.getId() + "\""));
        assertTrue(result.body.contains("\"conceptId\":\"" + fixture.serving.getId() + "\""));
        assertTrue(result.body.contains("\"conceptId\":\"" + fixture.assignment.getId() + "\""));
    }

    @Test
    public void queryRelationshipsSortsByIdBeforePaging() throws IOException {
        RelationshipFixture fixture = createRelationshipFixture();

        HttpResult fullResult = sendRequest("""
                {
                  "id":"req-relationships-full-order",
                  "method":"query",
                  "params":{
                    "queryType":"relationships",
                    "modelId":"%s",
                    "offset":0,
                    "limit":10
                  }
                }
                """.formatted(fixture.model.getId()));
        List<String> orderedIds = extractRelationshipIds(fullResult.body);

        HttpResult result = sendRequest("""
                {
                  "id":"req-relationships-paging",
                  "method":"query",
                  "params":{
                    "queryType":"relationships",
                    "modelId":"%s",
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
        assertTrue(result.body.contains("\"relationshipId\":\"" + orderedIds.get(1) + "\""));
        assertFalse(result.body.contains("\"relationshipId\":\"" + orderedIds.get(0) + "\""));
    }

    private RelationshipFixture createRelationshipFixture() {
        IArchimateModel model = IEditorModelManager.INSTANCE.createNewModel();
        model.setName("Relationship Query Model");
        createdModels.add(model);

        IDiagramModel mainView = model.getDiagramModels().get(0);
        mainView.setName("Main Relationship View");

        IDiagramModel secondaryView = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        secondaryView.setName("Secondary Relationship View");
        model.getDefaultFolderForObject(secondaryView).getElements().add(secondaryView);

        IBusinessActor alpha = IArchimateFactory.eINSTANCE.createBusinessActor();
        alpha.setName("Alpha Actor");
        model.getDefaultFolderForObject(alpha).getElements().add(alpha);

        IBusinessActor beta = IArchimateFactory.eINSTANCE.createBusinessActor();
        beta.setName("Beta Actor");
        model.getDefaultFolderForObject(beta).getElements().add(beta);

        IApplicationComponent app = IArchimateFactory.eINSTANCE.createApplicationComponent();
        app.setName("Shared App");
        model.getDefaultFolderForObject(app).getElements().add(app);

        IServingRelationship serving = IArchimateFactory.eINSTANCE.createServingRelationship();
        serving.setName("Alpha serves app");
        serving.getProperties().add(IArchimateFactory.eINSTANCE.createProperty("strength", "primary"));
        serving.connect(alpha, app);
        model.getDefaultFolderForObject(serving).getElements().add(serving);

        IAssociationRelationship assignment = IArchimateFactory.eINSTANCE.createAssociationRelationship();
        assignment.setName("Beta assigned app");
        assignment.connect(beta, app);
        model.getDefaultFolderForObject(assignment).getElements().add(assignment);

        IAssociationRelationship trace = IArchimateFactory.eINSTANCE.createAssociationRelationship();
        trace.setName("Connection trace");
        model.getDefaultFolderForObject(trace).getElements().add(trace);

        IDiagramModelArchimateObject alphaMainObject = ArchimateTestModel.createDiagramModelArchimateObject(alpha);
        alphaMainObject.setBounds(40, 40, 120, 55);
        mainView.getChildren().add(alphaMainObject);

        IDiagramModelArchimateObject betaMainObject = ArchimateTestModel.createDiagramModelArchimateObject(beta);
        betaMainObject.setBounds(40, 220, 120, 55);
        mainView.getChildren().add(betaMainObject);

        IDiagramModelArchimateObject appMainObject = ArchimateTestModel.createDiagramModelArchimateObject(app);
        appMainObject.setBounds(320, 120, 140, 60);
        mainView.getChildren().add(appMainObject);

        IDiagramModelArchimateConnection mainServingConnection = ArchimateTestModel.createDiagramModelArchimateConnection(serving);
        mainServingConnection.connect(alphaMainObject, appMainObject);
        mainServingConnection.getBendpoints().add(createBendpoint(12, 18, 20, 24));

        IDiagramModelArchimateConnection assignmentConnection = ArchimateTestModel.createDiagramModelArchimateConnection(assignment);
        assignmentConnection.connect(betaMainObject, appMainObject);
        assignmentConnection.getBendpoints().add(createBendpoint(5, 10, 15, 20));

        IDiagramModelArchimateConnection traceConnection = ArchimateTestModel.createDiagramModelArchimateConnection(trace);
        traceConnection.connect(mainServingConnection, assignmentConnection);
        traceConnection.getBendpoints().add(createBendpoint(3, 6, 9, 12));

        IDiagramModelArchimateObject alphaSecondaryObject = ArchimateTestModel.createDiagramModelArchimateObject(alpha);
        alphaSecondaryObject.setBounds(50, 50, 120, 55);
        secondaryView.getChildren().add(alphaSecondaryObject);

        IDiagramModelArchimateObject appSecondaryObject = ArchimateTestModel.createDiagramModelArchimateObject(app);
        appSecondaryObject.setBounds(280, 50, 140, 60);
        secondaryView.getChildren().add(appSecondaryObject);

        IDiagramModelArchimateConnection secondaryServingConnection = ArchimateTestModel.createDiagramModelArchimateConnection(serving);
        secondaryServingConnection.connect(alphaSecondaryObject, appSecondaryObject);

        return new RelationshipFixture(model, alpha, beta, app, serving, assignment, trace, mainView, secondaryView,
                alphaMainObject, mainServingConnection, assignmentConnection, traceConnection, secondaryServingConnection);
    }

    private IDiagramModelBendpoint createBendpoint(int startX, int startY, int endX, int endY) {
        IDiagramModelBendpoint bendpoint = IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
        bendpoint.setStartX(startX);
        bendpoint.setStartY(startY);
        bendpoint.setEndX(endX);
        bendpoint.setEndY(endY);
        return bendpoint;
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

    private List<String> extractRelationshipIds(String body) {
        Pattern pattern = Pattern.compile("\"relationshipId\":\"([^\"]+)\"");
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

    private record RelationshipFixture(IArchimateModel model, IBusinessActor alpha, IBusinessActor beta,
            IApplicationComponent app, IServingRelationship serving, IAssociationRelationship assignment,
            IAssociationRelationship trace, IDiagramModel mainView, IDiagramModel secondaryView,
            IDiagramModelArchimateObject alphaMainObject, IDiagramModelArchimateConnection mainServingConnection,
            IDiagramModelArchimateConnection assignmentConnection, IDiagramModelArchimateConnection traceConnection,
            IDiagramModelArchimateConnection secondaryServingConnection) {
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
