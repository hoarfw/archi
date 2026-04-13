/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.editor.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.archimatetool.mcp.bootstrap.MCPServerBootstrap;

@SuppressWarnings("nls")
public class MCPBindAddressTests {

    @Test
    public void acceptLoopbackV4() {
        assertTrue(MCPServerBootstrap.isLoopbackAddress("127.0.0.1"));
    }

    @Test
    public void acceptLoopbackV6() {
        assertTrue(MCPServerBootstrap.isLoopbackAddress("::1"));
    }

    @Test
    public void rejectNonLoopbackAddresses() {
        assertFalse(MCPServerBootstrap.isLoopbackAddress("0.0.0.0"));
        assertFalse(MCPServerBootstrap.isLoopbackAddress("localhost"));
        assertFalse(MCPServerBootstrap.isLoopbackAddress("192.168.1.10"));
    }
}
