/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.editor.views.tree.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;

import com.archimatetool.editor.ArchiPlugin;
import com.archimatetool.editor.preferences.IPreferenceConstants;
import com.archimatetool.editor.ui.IArchiImages;



/**
 * Toggle type-first sorting in the Model Tree.
 * 
 * @author Phillip Beauvoir
 */
public class SortModelViewAction extends Action {

    public SortModelViewAction() {
        super(Messages.SortModelViewAction_0, IAction.AS_CHECK_BOX);
        setImageDescriptor(IArchiImages.ImageFactory.getImageDescriptor(IArchiImages.ICON_SORT));
        setChecked(ArchiPlugin.getInstance().getPreferenceStore().getBoolean(IPreferenceConstants.SORT_MODEL_VIEW));
    }

    @Override
    public void run() {
        ArchiPlugin.getInstance().getPreferenceStore().setValue(IPreferenceConstants.SORT_MODEL_VIEW, isChecked());
    }
}
