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
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.util.ArchimateModelUtils;

@SuppressWarnings("nls")
public class MCPWriteElementIntegrationTests {

    private static final Pattern ELEMENT_ID_PATTERN = Pattern.compile("\"elementId\":\"([^\"]+)\"");
    private static final Pattern OBJECT_ID_PATTERN = Pattern.compile("\"objectId\":\"([^\"]+)\"");

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
    public void createElementAddsWhitelistedElementAndSupportsUndoRedo() throws IOException {
        IArchimateModel model = createOpenModel("Write Element Model");

        HttpResult result = sendRequest("""
                {
                  "id":"req-create-element",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "operation":"createElement",
                    "payload":{"type":"business-actor","name":"Created Actor","documentation":"from mcp"}
                  }
                }
                """.formatted(model.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"operation\":\"createElement\""));
        assertTrue(result.body.contains("\"type\":\"business-actor\""));
        String elementId = extract(ELEMENT_ID_PATTERN, result.body);
        IArchimateElement element = (IArchimateElement)ArchimateModelUtils.getObjectByID(model, elementId);
        assertNotNull(element);
        assertEquals("Created Actor", element.getName());
        assertEquals("from mcp", element.getDocumentation());

        CommandStack commandStack = (CommandStack)model.getAdapter(CommandStack.class);
        commandStack.undo();
        assertNull(ArchimateModelUtils.getObjectByID(model, elementId));

        commandStack.redo();
        assertNotNull(ArchimateModelUtils.getObjectByID(model, elementId));
    }

    @Test
    public void createElementRejectsUnsupportedTypeWithoutMutatingModel() throws IOException {
        IArchimateModel model = createOpenModel("Write Invalid Type Model");

        HttpResult result = sendRequest("""
                {
                  "id":"req-create-invalid-type",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "operation":"createElement",
                    "payload":{"type":"unknown-element","name":"Invalid"}
                  }
                }
                """.formatted(model.getId()));

        assertEquals(400, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"MCP-WRITE-BAD-REQUEST\""));
        assertTrue(result.body.contains("\"reason\":\"unsupported element type: unknown-element\""));
        assertEquals(0, countArchimateElements(model));
    }

    @Test
    public void addElementToViewAddsExistingElementWithBoundsAndSupportsUndoRedo() throws IOException {
        IArchimateModel model = createOpenModel("Write Add To View Model");
        IArchimateElement actor = IArchimateFactory.eINSTANCE.createBusinessActor();
        actor.setName("Placed Actor");
        model.getDefaultFolderForObject(actor).getElements().add(actor);

        HttpResult result = sendRequest("""
                {
                  "id":"req-add-element-to-view",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "viewId":"%s",
                    "operation":"addElementToView",
                    "payload":{
                      "elementId":"%s",
                      "bounds":{"x":30,"y":45,"width":140,"height":70}
                    }
                  }
                }
                """.formatted(model.getId(), model.getDiagramModels().get(0).getId(), actor.getId()));

        assertEquals(200, result.statusCode);
        assertTrue(result.body.contains("\"operation\":\"addElementToView\""));
        String objectId = extract(OBJECT_ID_PATTERN, result.body);
        IDiagramModelArchimateObject object = (IDiagramModelArchimateObject)ArchimateModelUtils.getObjectByID(model, objectId);
        assertNotNull(object);
        assertEquals(actor, object.getArchimateElement());
        assertEquals(30, object.getBounds().getX());
        assertEquals(45, object.getBounds().getY());
        assertEquals(140, object.getBounds().getWidth());
        assertEquals(70, object.getBounds().getHeight());

        CommandStack commandStack = (CommandStack)model.getAdapter(CommandStack.class);
        commandStack.undo();
        assertNull(ArchimateModelUtils.getObjectByID(model, objectId));

        commandStack.redo();
        assertNotNull(ArchimateModelUtils.getObjectByID(model, objectId));
    }

    @Test
    public void addElementToViewRequiresElementFromTargetModel() throws IOException {
        IArchimateModel targetModel = createOpenModel("Write Target Model");
        IArchimateModel foreignModel = createOpenModel("Write Foreign Model");
        IArchimateElement foreignActor = IArchimateFactory.eINSTANCE.createBusinessActor();
        foreignModel.getDefaultFolderForObject(foreignActor).getElements().add(foreignActor);

        HttpResult result = sendRequest("""
                {
                  "id":"req-add-foreign-element",
                  "method":"write",
                  "params":{
                    "modelId":"%s",
                    "viewId":"%s",
                    "operation":"addElementToView",
                    "payload":{
                      "elementId":"%s",
                      "bounds":{"x":10,"y":20}
                    }
                  }
                }
                """.formatted(targetModel.getId(), targetModel.getDiagramModels().get(0).getId(), foreignActor.getId()));

        assertEquals(404, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"MCP-WRITE-ELEMENT-NOT-FOUND\""));
        assertTrue(result.body.contains("\"reason\":\"elementId not found: " + foreignActor.getId() + "\""));
    }

    private int countArchimateElements(IArchimateModel model) {
        int count = 0;
        for(java.util.Iterator<org.eclipse.emf.ecore.EObject> it = model.eAllContents(); it.hasNext();) {
            if(it.next() instanceof IArchimateElement) {
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
