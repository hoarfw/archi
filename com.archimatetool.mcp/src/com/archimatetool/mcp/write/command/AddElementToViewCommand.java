/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.write.command;

import org.eclipse.gef.commands.Command;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;

/**
 * Headless-safe command for adding an existing element to a diagram with explicit bounds.
 */
@SuppressWarnings("nls")
public class AddElementToViewCommand extends Command {

    private final IDiagramModel view;
    private final IArchimateElement element;
    private final IDiagramModelArchimateObject diagramObject;

    public AddElementToViewCommand(IDiagramModel view, IArchimateElement element, int x, int y, int width, int height) {
        this.view = view;
        this.element = element;
        this.diagramObject = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
        this.diagramObject.setArchimateElement(element);
        this.diagramObject.setBounds(x, y, width, height);
        setLabel("MCP add element to view");
    }

    @Override
    public boolean canExecute() {
        return view != null && element != null;
    }

    @Override
    public void execute() {
        redo();
    }

    @Override
    public void redo() {
        if(!view.getChildren().contains(diagramObject)) {
            view.getChildren().add(diagramObject);
        }
    }

    @Override
    public void undo() {
        view.getChildren().remove(diagramObject);
    }

    public IDiagramModelArchimateObject getDiagramObject() {
        return diagramObject;
    }
}
