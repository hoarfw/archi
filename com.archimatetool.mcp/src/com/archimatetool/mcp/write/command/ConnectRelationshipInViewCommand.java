/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.write.command;

import org.eclipse.gef.commands.Command;

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;

/**
 * Headless-safe command for creating a diagram connection from an existing relationship.
 */
@SuppressWarnings("nls")
public class ConnectRelationshipInViewCommand extends Command {

    private final IDiagramModelArchimateConnection connection;
    private final IDiagramModelArchimateObject sourceObject;
    private final IDiagramModelArchimateObject targetObject;

    public ConnectRelationshipInViewCommand(IArchimateRelationship relationship, IDiagramModelArchimateObject sourceObject,
            IDiagramModelArchimateObject targetObject) {
        this.connection = IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
        this.connection.setArchimateRelationship(relationship);
        this.sourceObject = sourceObject;
        this.targetObject = targetObject;
        setLabel("MCP connect relationship in view");
    }

    @Override
    public boolean canExecute() {
        return connection.getArchimateRelationship() != null && sourceObject != null && targetObject != null;
    }

    @Override
    public void execute() {
        connection.connect(sourceObject, targetObject);
    }

    @Override
    public void redo() {
        connection.reconnect();
    }

    @Override
    public void undo() {
        connection.disconnect();
    }

    public IDiagramModelArchimateConnection getConnection() {
        return connection;
    }
}
