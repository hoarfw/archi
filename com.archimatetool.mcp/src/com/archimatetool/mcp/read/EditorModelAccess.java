/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.read;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

import com.archimatetool.model.IArchimateModel;

/**
 * Reads open-model state from the editor bundle without introducing a bundle cycle.
 */
@SuppressWarnings("nls")
public class EditorModelAccess {

    private static final String EDITOR_BUNDLE_ID = "com.archimatetool.editor";
    private static final String EDITOR_MANAGER_CLASS = "com.archimatetool.editor.model.IEditorModelManager";

    public List<IArchimateModel> getOpenModels() {
        Object manager = getEditorModelManager();
        Object value = invoke(manager, "getModels");

        if(!(value instanceof List<?> models)) {
            throw new IllegalStateException("Editor model manager returned an invalid models collection");
        }

        List<IArchimateModel> result = new ArrayList<>(models.size());
        for(Object model : models) {
            result.add((IArchimateModel)model);
        }
        return result;
    }

    public boolean isModelDirty(IArchimateModel model) {
        Object manager = getEditorModelManager();
        Object value = invoke(manager, "isModelDirty", IArchimateModel.class, model);
        if(value instanceof Boolean dirty) {
            return dirty.booleanValue();
        }

        throw new IllegalStateException("Editor model manager returned an invalid dirty-state value");
    }

    private Object getEditorModelManager() {
        try {
            Bundle bundle = Platform.getBundle(EDITOR_BUNDLE_ID);
            if(bundle == null) {
                throw new IllegalStateException("Required bundle is not available: " + EDITOR_BUNDLE_ID);
            }

            Class<?> managerClass = bundle.loadClass(EDITOR_MANAGER_CLASS);
            return managerClass.getField("INSTANCE").get(null);
        }
        catch(ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to access editor model manager", ex);
        }
    }

    private Object invoke(Object target, String methodName) {
        return invoke(target, methodName, new Class<?>[0]);
    }

    private Object invoke(Object target, String methodName, Class<?> parameterType, Object argument) {
        return invoke(target, methodName, new Class<?>[] { parameterType }, argument);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, arguments);
        }
        catch(ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to invoke editor model manager method: " + methodName, ex);
        }
    }
}
