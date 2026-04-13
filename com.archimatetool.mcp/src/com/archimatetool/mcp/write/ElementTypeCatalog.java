/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.write;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;

import com.archimatetool.mcp.contract.MCPErrorCode;
import com.archimatetool.mcp.contract.MCPStructuredErrorException;
import com.archimatetool.mcp.read.ConceptQuerySupport;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimatePackage;

/**
 * Stable v1 whitelist for write-time element creation.
 */
@SuppressWarnings("nls")
public class ElementTypeCatalog {

    private static final Map<String, EClass> ELEMENT_TYPES = createTypes();

    public IArchimateElement create(String typeId) {
        EClass eClass = ELEMENT_TYPES.get(normalizeTypeId(typeId));
        if(eClass == null) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "unsupported element type: " + typeId,
                    Map.of("field", "type", "type", String.valueOf(typeId)),
                    Boolean.FALSE);
        }

        return (IArchimateElement)IArchimateFactory.eINSTANCE.create(eClass);
    }

    public String getTypeId(IArchimateElement element) {
        return ConceptQuerySupport.toTypeToken(element);
    }

    private String normalizeTypeId(String typeId) {
        return typeId == null ? "" : typeId.strip();
    }

    private static Map<String, EClass> createTypes() {
        Map<String, EClass> types = new LinkedHashMap<>();
        types.put("business-actor", IArchimatePackage.eINSTANCE.getBusinessActor());
        types.put("business-role", IArchimatePackage.eINSTANCE.getBusinessRole());
        types.put("business-process", IArchimatePackage.eINSTANCE.getBusinessProcess());
        types.put("application-component", IArchimatePackage.eINSTANCE.getApplicationComponent());
        types.put("application-service", IArchimatePackage.eINSTANCE.getApplicationService());
        types.put("data-object", IArchimatePackage.eINSTANCE.getDataObject());
        return Map.copyOf(types);
    }
}
