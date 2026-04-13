/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.write.command;

import org.eclipse.gef.commands.Command;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;

/**
 * Headless-safe command for adding a new element to its default model folder.
 */
@SuppressWarnings("nls")
public class CreateElementCommand extends Command {

    private final IArchimateModel model;
    private final IArchimateElement element;
    private final IFolder folder;

    public CreateElementCommand(IArchimateModel model, IArchimateElement element) {
        this.model = model;
        this.element = element;
        this.folder = model.getDefaultFolderForObject(element);
        setLabel("MCP create element");
    }

    @Override
    public boolean canExecute() {
        return model != null && element != null && folder != null;
    }

    @Override
    public void execute() {
        redo();
    }

    @Override
    public void redo() {
        if(!folder.getElements().contains(element)) {
            folder.getElements().add(element);
        }
    }

    @Override
    public void undo() {
        folder.getElements().remove(element);
    }
}
