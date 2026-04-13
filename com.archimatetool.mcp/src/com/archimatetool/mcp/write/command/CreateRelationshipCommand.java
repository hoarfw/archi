/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.write.command;

import org.eclipse.gef.commands.Command;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IFolder;

/**
 * Headless-safe command for adding a relationship to its default model folder.
 */
@SuppressWarnings("nls")
public class CreateRelationshipCommand extends Command {

    private final IArchimateModel model;
    private final IArchimateRelationship relationship;
    private final IFolder folder;

    public CreateRelationshipCommand(IArchimateModel model, IArchimateRelationship relationship) {
        this.model = model;
        this.relationship = relationship;
        this.folder = model.getDefaultFolderForObject(relationship);
        setLabel("MCP create relationship");
    }

    @Override
    public boolean canExecute() {
        return model != null && relationship != null && folder != null;
    }

    @Override
    public void execute() {
        redo();
    }

    @Override
    public void redo() {
        if(!folder.getElements().contains(relationship)) {
            folder.getElements().add(relationship);
        }
    }

    @Override
    public void undo() {
        folder.getElements().remove(relationship);
    }
}
