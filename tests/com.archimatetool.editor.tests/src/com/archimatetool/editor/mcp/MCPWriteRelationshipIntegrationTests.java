/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.editor.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import org.eclipse.gef.commands.CommandStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.util.ArchimateModelUtils;

@SuppressWarnings("nls")
public class MCPWriteRelationshipIntegrationTests {

    private static final Pattern RELATIONSHIP_ID_PATTERN = Pattern.compile("\"relationshipId\":\"([^\"]+)\"");
    private static final Pattern CONNECTION_ID_PATTERN = Pattern.compile("\"connectionId\":\"([^\"]+)\"");

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
    public void createRelationshipCreatesWhitelistedTypeAndSupportsUndoRedo() throws IOException {
        IArchimateModel model = createOpenModel("Write Relationship Model");
        IArchimateElement source = createElement(model, "Source");
        IArchimateElement target = createElement(model, "Target");

        HttpResult result = sendRequest("""
                {
                  "id":"req-create-relationship",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "operation":"createRelationship",
                    "payload":{
                      "relationshipType":"assignment-relationship",
                      "sourceElementId":"%s",
                      "targetElementId":"%s",
                      "name":"Assigned"
                    }
                  }
                }
                """.formatted(model.getId(), source.getId(), target.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"operation\":\"createRelationship\""));
        assertTrue(result.body.contains("\"type\":\"assignment-relationship\""));
        String relationshipId = extract(RELATIONSHIP_ID_PATTERN, result.body);
        IArchimateRelationship relationship = (IArchimateRelationship)ArchimateModelUtils.getObjectByID(model, relationshipId);
        assertNotNull(relationship);
        assertEquals(source, relationship.getSource());
        assertEquals(target, relationship.getTarget());
        assertEquals("Assigned", relationship.getName());

        CommandStack commandStack = (CommandStack)model.getAdapter(CommandStack.class);
        commandStack.undo();
        assertNull(ArchimateModelUtils.getObjectByID(model, relationshipId));

        commandStack.redo();
        assertNotNull(ArchimateModelUtils.getObjectByID(model, relationshipId));
    }

    @Test
    public void createRelationshipRejectsUnsupportedTypeWithoutMutatingModel() throws IOException {
        IArchimateModel model = createOpenModel("Write Invalid Relationship Model");
        IArchimateElement source = createElement(model, "Source");
        IArchimateElement target = createElement(model, "Target");

        HttpResult result = sendRequest("""
                {
                  "id":"req-create-invalid-relationship",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "operation":"createRelationship",
                    "payload":{
                      "relationshipType":"unknown-relationship",
                      "sourceElementId":"%s",
                      "targetElementId":"%s"
                    }
                  }
                }
                """.formatted(model.getId(), source.getId(), target.getId()));

        assertEquals(400, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"MCP-WRITE-BAD-REQUEST\""));
        assertTrue(result.body.contains("\"reason\":\"unsupported relationship type: unknown-relationship\""));
        assertEquals(0, countRelationships(model));
    }

    @Test
    public void connectRelationshipInViewCreatesConnectionAndSupportsUndoRedo() throws IOException {
        IArchimateModel model = createOpenModel("Write Connect Model");
        IArchimateElement source = createElement(model, "Source");
        IArchimateElement target = createElement(model, "Target");
        IDiagramModelArchimateObject sourceObject = placeInView(model, source, 20, 30);
        IDiagramModelArchimateObject targetObject = placeInView(model, target, 200, 30);
        IArchimateRelationship relationship = createRelationship(model, source, target);

        HttpResult result = sendRequest("""
                {
                  "id":"req-connect-relationship",
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
                """.formatted(model.getId(), model.getDiagramModels().get(0).getId(), relationship.getId(), sourceObject.getId(),
                        targetObject.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"operation\":\"connectRelationshipInView\""));
        String connectionId = extract(CONNECTION_ID_PATTERN, result.body);
        IDiagramModelArchimateConnection connection = (IDiagramModelArchimateConnection)ArchimateModelUtils.getObjectByID(model,
                connectionId);
        assertNotNull(connection);
        assertEquals(relationship, connection.getArchimateRelationship());
        assertEquals(sourceObject, connection.getSource());
        assertEquals(targetObject, connection.getTarget());

        CommandStack commandStack = (CommandStack)model.getAdapter(CommandStack.class);
        commandStack.undo();
        assertNull(ArchimateModelUtils.getObjectByID(model, connectionId));

        commandStack.redo();
        assertNotNull(ArchimateModelUtils.getObjectByID(model, connectionId));
    }

    @Test
    public void connectRelationshipInViewIsIdempotentForExistingConnection() throws IOException {
        IArchimateModel model = createOpenModel("Write Connect Idempotent Model");
        IArchimateElement source = createElement(model, "Source");
        IArchimateElement target = createElement(model, "Target");
        IDiagramModelArchimateObject sourceObject = placeInView(model, source, 20, 30);
        IDiagramModelArchimateObject targetObject = placeInView(model, target, 200, 30);
        IArchimateRelationship relationship = createRelationship(model, source, target);

        String request = """
                {
                  "id":"req-connect-idempotent",
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
                """.formatted(model.getId(), model.getDiagramModels().get(0).getId(), relationship.getId(), sourceObject.getId(),
                        targetObject.getId());

        HttpResult first = sendRequest(request);
        HttpResult second = sendRequest(request);

        assertEquals(200, first.statusCode);
        assertEquals(200, second.statusCode);
        assertEquals(extract(CONNECTION_ID_PATTERN, first.body), extract(CONNECTION_ID_PATTERN, second.body));
        assertEquals(1, relationship.getReferencingDiagramConnections().size());
    }

    @Test
    public void connectRelationshipInViewRejectsObjectsOutsideTargetView() throws IOException {
        IArchimateModel model = createOpenModel("Write Connect View Mismatch");
        var secondView = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        secondView.setName("Secondary View");
        model.getDefaultFolderForObject(secondView).getElements().add(secondView);

        IArchimateElement source = createElement(model, "Source");
        IArchimateElement target = createElement(model, "Target");
        IDiagramModelArchimateObject sourceObject = placeInView(model, source, 20, 30);
        IDiagramModelArchimateObject targetObject = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
        targetObject.setArchimateElement(target);
        targetObject.setBounds(200, 30, 120, 55);
        secondView.getChildren().add(targetObject);
        IArchimateRelationship relationship = createRelationship(model, source, target);

        HttpResult result = sendRequest("""
                {
                  "id":"req-connect-view-mismatch",
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
                """.formatted(model.getId(), model.getDiagramModels().get(0).getId(), relationship.getId(), sourceObject.getId(),
                        targetObject.getId()));

        assertEquals(404, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"MCP-WRITE-OBJECT-NOT-FOUND\""));
        assertTrue(result.body.contains("\"reason\":\"targetObjectId not found in view: " + targetObject.getId() + "\""));
    }

    private IArchimateElement createElement(IArchimateModel model, String name) {
        IArchimateElement element = IArchimateFactory.eINSTANCE.createBusinessActor();
        element.setName(name);
        model.getDefaultFolderForObject(element).getElements().add(element);
        return element;
    }

    private IDiagramModelArchimateObject placeInView(IArchimateModel model, IArchimateElement element, int x, int y) {
        IDiagramModelArchimateObject object = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
        object.setArchimateElement(element);
        object.setBounds(x, y, 120, 55);
        model.getDiagramModels().get(0).getChildren().add(object);
        return object;
    }

    private IArchimateRelationship createRelationship(IArchimateModel model, IArchimateElement source, IArchimateElement target) {
        IArchimateRelationship relationship = IArchimateFactory.eINSTANCE.createAssignmentRelationship();
        relationship.setSource(source);
        relationship.setTarget(target);
        model.getDefaultFolderForObject(relationship).getElements().add(relationship);
        return relationship;
    }

    private int countRelationships(IArchimateModel model) {
        int count = 0;
        for(java.util.Iterator<org.eclipse.emf.ecore.EObject> it = model.eAllContents(); it.hasNext();) {
            if(it.next() instanceof IArchimateRelationship) {
                count++;
            }
        }
        return count;
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
