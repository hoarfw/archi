/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.editor.mcp;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SelectClasses({
    MCPBindAddressTests.class,
    MCPHttpStreamContractTests.class,
    MCPErrorEnvelopeTests.class,
    MCPServiceLifecycleTests.class,
    MCPQueryContractTests.class,
    MCPReadRuntimeSafetyTests.class,
    MCPViewExportContractTests.class,
    MCPViewExportIntegrationTests.class,
    MCPViewExportReliabilityTests.class,
    MCPTempImageStoreTests.class,
    MCPElementReadIntegrationTests.class,
    MCPRelationshipReadIntegrationTests.class,
    MCPReadRegressionTests.class,
    MCPWriteContractTests.class,
    MCPWriteRuntimeSafetyTests.class,
    MCPWriteElementIntegrationTests.class,
    MCPWriteRelationshipIntegrationTests.class,
    MCPWriteRegressionTests.class
})
@SuiteDisplayName("All MCP Tests")
public class AllMCPTests {
}
