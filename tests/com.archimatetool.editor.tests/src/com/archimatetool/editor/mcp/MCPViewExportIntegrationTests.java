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
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.mcp.bootstrap.MCPReadTelemetry;
import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateObject;

@SuppressWarnings("nls")
public class MCPViewExportIntegrationTests {

    private static final Pattern FILE_URL_PATTERN = Pattern.compile("\"fileUrl\":\"([^\"]+)\"");
    private static final Pattern DATA_URL_PATTERN = Pattern.compile("\"dataUrl\":\"([^\"]+)\"");
    private static final Pattern WIDTH_PATTERN = Pattern.compile("\"width\":(\\d+)");
    private static final Pattern HEIGHT_PATTERN = Pattern.compile("\"height\":(\\d+)");

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
    public void exportViewReturnsAccessibleFileAndDataUrlForRenderedView() throws Exception {
        IArchimateModel model = createExportModel("Integration Export Model");
        String viewId = model.getDiagramModels().get(0).getId();

        HttpResult result = sendRequest("""
                {
                  "id":"req-export-integration",
                  "method":"exportView",
                  "params":{"viewId":"%s"}
                }
                """.formatted(viewId));

        assertEquals(200, result.statusCode, result.body);
        assertTrue(result.body.contains("\"method\":\"exportView\""));
        assertTrue(result.body.contains("\"fileUrl\":\"file:"));
        assertTrue(result.body.contains("\"dataUrl\":\"data:image/png;base64,"));

        String fileUrl = extract(result.body, FILE_URL_PATTERN);
        String dataUrl = extract(result.body, DATA_URL_PATTERN);
        Path filePath = Path.of(URI.create(fileUrl));
        assertTrue(Files.exists(filePath), fileUrl);

        byte[] fileBytes = Files.readAllBytes(filePath);
        assertTrue(fileBytes.length > 8);
        assertEquals((byte)0x89, fileBytes[0]);
        assertEquals((byte)0x50, fileBytes[1]);
        assertEquals((byte)0x4e, fileBytes[2]);
        assertEquals((byte)0x47, fileBytes[3]);

        String base64Payload = dataUrl.substring("data:image/png;base64,".length());
        byte[] dataUrlBytes = Base64.getDecoder().decode(base64Payload);
        assertEquals(fileBytes.length, dataUrlBytes.length);
        assertEquals(fileBytes[0], dataUrlBytes[0]);
        assertEquals(fileBytes[1], dataUrlBytes[1]);

        MCPReadTelemetry.Snapshot snapshot = MCPReadTelemetry.snapshot();
        assertTrue(snapshot.isExportRenderRecorded());
        assertTrue(snapshot.isExportRenderOnUiThread());
        assertTrue(snapshot.isExportAssemblyRecorded());
        assertFalse(snapshot.isExportAssemblyOnUiThread());
        assertTrue(snapshot.getExportRenderThreadName().contains("Export"));
        assertTrue(snapshot.getExportAssemblyThreadName().contains("MCP"));
    }

    @Test
    public void exportViewScaleChangesReportedDimensions() throws Exception {
        IArchimateModel model = createExportModel("Scaled Export Model");
        String viewId = model.getDiagramModels().get(0).getId();

        HttpResult defaultResult = sendRequest("""
                {
                  "id":"req-export-default-scale",
                  "method":"exportView",
                  "params":{"viewId":"%s"}
                }
                """.formatted(viewId));
        HttpResult scaledResult = sendRequest("""
                {
                  "id":"req-export-scaled",
                  "method":"exportView",
                  "params":{"viewId":"%s","scale":2.0}
                }
                """.formatted(viewId));

        assertEquals(200, defaultResult.statusCode, defaultResult.body);
        assertEquals(200, scaledResult.statusCode, scaledResult.body);

        int defaultWidth = Integer.parseInt(extract(defaultResult.body, WIDTH_PATTERN));
        int defaultHeight = Integer.parseInt(extract(defaultResult.body, HEIGHT_PATTERN));
        int scaledWidth = Integer.parseInt(extract(scaledResult.body, WIDTH_PATTERN));
        int scaledHeight = Integer.parseInt(extract(scaledResult.body, HEIGHT_PATTERN));

        assertTrue(scaledWidth > defaultWidth);
        assertTrue(scaledHeight > defaultHeight);
        assertTrue(scaledResult.body.contains("\"scale\":2.0"));
        assertFalse(defaultResult.body.contains("\"scale\":2.0"));
    }

    private IArchimateModel createExportModel(String name) {
        IArchimateModel model = IEditorModelManager.INSTANCE.createNewModel();
        model.setName(name);
        createdModels.add(model);

        var actor = IArchimateFactory.eINSTANCE.createBusinessActor();
        actor.setName("Export Actor");
        model.getDefaultFolderForObject(actor).getElements().add(actor);

        IDiagramModelArchimateObject actorObject = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
        actorObject.setArchimateElement(actor);
        actorObject.setBounds(20, 20, 160, 80);
        model.getDiagramModels().get(0).getChildren().add(actorObject);
        model.getDiagramModels().get(0).setName("Export View");

        return model;
    }

    private String extract(String body, Pattern pattern) {
        Matcher matcher = pattern.matcher(body);
        assertTrue(matcher.find(), body);
        return matcher.group(1);
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
