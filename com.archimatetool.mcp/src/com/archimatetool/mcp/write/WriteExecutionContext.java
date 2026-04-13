/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.write;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.CommandStack;

import com.archimatetool.mcp.contract.MCPWriteRequest;
import com.archimatetool.model.IArchimateModel;

/**
 * Resolved live-model context for a write request.
 */
@SuppressWarnings("nls")
public final class WriteExecutionContext {

    private final MCPWriteRequest request;
    private final IArchimateModel model;
    private final CommandStack commandStack;
    private final Map<String, EObject> resolvedReferences;

    public WriteExecutionContext(MCPWriteRequest request, IArchimateModel model, CommandStack commandStack,
            Map<String, EObject> resolvedReferences) {
        this.request = request;
        this.model = model;
        this.commandStack = commandStack;
        this.resolvedReferences = Collections.unmodifiableMap(new LinkedHashMap<>(resolvedReferences));
    }

    public MCPWriteRequest getRequest() {
        return request;
    }

    public IArchimateModel getModel() {
        return model;
    }

    public CommandStack getCommandStack() {
        return commandStack;
    }

    public boolean hasReference(String key) {
        return resolvedReferences.containsKey(key);
    }

    public Map<String, EObject> getResolvedReferences() {
        return resolvedReferences;
    }

    public <T> T getReference(String key, Class<T> type) {
        EObject value = resolvedReferences.get(key);
        if(value == null) {
            return null;
        }

        return type.cast(value);
    }
}
