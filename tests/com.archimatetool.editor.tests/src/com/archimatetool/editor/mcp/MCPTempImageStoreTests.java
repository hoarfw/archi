/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.editor.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.mcp.export.TempImageStore;

@SuppressWarnings("nls")
public class MCPTempImageStoreTests {

    private Path tempDirectory;

    @AfterEach
    public void cleanupDirectory() throws IOException {
        if(tempDirectory == null) {
            return;
        }

        try(var paths = Files.list(tempDirectory)) {
            paths.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                }
                catch(IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
        Files.deleteIfExists(tempDirectory);
    }

    @Test
    public void ttlSweepRemovesExpiredTrackedFiles() throws Exception {
        AtomicLong clock = new AtomicLong(1_000L);
        TempImageStore store = createStore(clock, 50L);
        TempImageStore.ExportLease lease = store.openLease();
        Path imagePath = Path.of(java.net.URI.create(store.writePng(lease, pngBytes())));

        assertTrue(Files.exists(imagePath));
        assertEquals(1, store.trackedCount());

        clock.addAndGet(100L);
        assertEquals(1, store.sweepExpired());
        assertFalse(Files.exists(imagePath));
        assertEquals(0, store.trackedCount());
    }

    @Test
    public void timedOutLateArtifactIsRemovedOnNextSweep() throws Exception {
        AtomicLong clock = new AtomicLong(2_000L);
        TempImageStore store = createStore(clock, 1_000L);
        TempImageStore.ExportLease lease = store.openLease();
        Path imagePath = Path.of(java.net.URI.create(store.writePng(lease, pngBytes())));

        lease.markTimedOut();

        assertEquals(1, store.sweepExpired());
        assertFalse(Files.exists(imagePath));
        assertEquals(0, store.trackedCount());
    }

    @Test
    public void cleanupAllRemovesEveryTrackedFile() throws Exception {
        AtomicLong clock = new AtomicLong(3_000L);
        TempImageStore store = createStore(clock, 1_000L);
        Path first = Path.of(java.net.URI.create(store.writePng(store.openLease(), pngBytes())));
        Path second = Path.of(java.net.URI.create(store.writePng(store.openLease(), pngBytes())));

        assertTrue(store.contains(first));
        assertTrue(store.contains(second));
        assertEquals(2, store.cleanupAll());
        assertFalse(Files.exists(first));
        assertFalse(Files.exists(second));
        assertEquals(0, store.trackedCount());
    }

    private TempImageStore createStore(AtomicLong clock, long ttlMillis) throws IOException {
        tempDirectory = Files.createTempDirectory("archi-mcp-temp-store-tests");
        return new TempImageStore(tempDirectory, clock::get, ttlMillis);
    }

    private byte[] pngBytes() {
        return new byte[] { (byte)0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01 };
    }
}
