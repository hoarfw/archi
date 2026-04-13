/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.write;

import java.util.Map;

import com.archimatetool.mcp.contract.MCPErrorCode;
import com.archimatetool.mcp.contract.MCPStructuredErrorException;
import com.archimatetool.mcp.contract.MCPWriteRequest;

/**
 * Write-operation router for MCP write requests.
 */
@SuppressWarnings("nls")
public class MCPWriteDispatcher {

    private final UIThreadWriteExecutor executor;
    private final WritePreflightValidator preflightValidator;
    private final CreateElementHandler createElementHandler;
    private final AddElementToViewHandler addElementToViewHandler;
    private final CreateRelationshipHandler createRelationshipHandler;
    private final ConnectRelationshipInViewHandler connectRelationshipInViewHandler;

    public MCPWriteDispatcher() {
        this(new UIThreadWriteExecutor(), new WritePreflightValidator(), new CreateElementHandler(), new AddElementToViewHandler(),
                new CreateRelationshipHandler(), new ConnectRelationshipInViewHandler());
    }

    MCPWriteDispatcher(UIThreadWriteExecutor executor, WritePreflightValidator preflightValidator,
            CreateElementHandler createElementHandler, AddElementToViewHandler addElementToViewHandler,
            CreateRelationshipHandler createRelationshipHandler,
            ConnectRelationshipInViewHandler connectRelationshipInViewHandler) {
        this.executor = executor;
        this.preflightValidator = preflightValidator;
        this.createElementHandler = createElementHandler;
        this.addElementToViewHandler = addElementToViewHandler;
        this.createRelationshipHandler = createRelationshipHandler;
        this.connectRelationshipInViewHandler = connectRelationshipInViewHandler;
    }

    public Object dispatch(MCPWriteRequest request) {
        return executor.execute(() -> {
            WriteExecutionContext context = preflightValidator.validate(request);
            return switch(request.getOperation()) {
                case CREATE_ELEMENT -> createElementHandler.handle(context);
                case ADD_ELEMENT_TO_VIEW -> addElementToViewHandler.handle(context);
                case CREATE_RELATIONSHIP -> createRelationshipHandler.handle(context);
                case CONNECT_RELATIONSHIP_IN_VIEW -> connectRelationshipInViewHandler.handle(context);
                default -> throw new MCPStructuredErrorException(MCPErrorCode.WRITE_OPERATION_NOT_IMPLEMENTED,
                        "write operation not implemented yet: " + request.getOperation().getValue(),
                        Map.of("operation", request.getOperation().getValue(), "modelId", context.getModel().getId()),
                        Boolean.FALSE);
            };
        });
    }
}
