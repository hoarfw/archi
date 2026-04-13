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
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IArchimateRelationship;

/**
 * Stable v1 whitelist for write-time relationship creation.
 */
@SuppressWarnings("nls")
public class RelationshipTypeCatalog {

    private static final Map<String, EClass> RELATIONSHIP_TYPES = createTypes();

    public IArchimateRelationship create(String typeId) {
        EClass eClass = RELATIONSHIP_TYPES.get(normalizeTypeId(typeId));
        if(eClass == null) {
            throw new MCPStructuredErrorException(MCPErrorCode.WRITE_BAD_REQUEST,
                    "unsupported relationship type: " + typeId,
                    Map.of("field", "relationshipType", "relationshipType", String.valueOf(typeId)),
                    Boolean.FALSE);
        }

        return (IArchimateRelationship)IArchimateFactory.eINSTANCE.create(eClass);
    }

    public String getTypeId(IArchimateRelationship relationship) {
        return ConceptQuerySupport.toTypeToken(relationship);
    }

    private String normalizeTypeId(String typeId) {
        return typeId == null ? "" : typeId.strip();
    }

    private static Map<String, EClass> createTypes() {
        Map<String, EClass> types = new LinkedHashMap<>();
        types.put("assignment-relationship", IArchimatePackage.eINSTANCE.getAssignmentRelationship());
        types.put("association-relationship", IArchimatePackage.eINSTANCE.getAssociationRelationship());
        types.put("serving-relationship", IArchimatePackage.eINSTANCE.getServingRelationship());
        types.put("access-relationship", IArchimatePackage.eINSTANCE.getAccessRelationship());
        types.put("realization-relationship", IArchimatePackage.eINSTANCE.getRealizationRelationship());
        types.put("flow-relationship", IArchimatePackage.eINSTANCE.getFlowRelationship());
        return Map.copyOf(types);
    }
}
