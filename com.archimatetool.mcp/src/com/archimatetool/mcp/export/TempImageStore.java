/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Tracks temporary exported images and cleans them up by TTL or lifecycle stop.
 */
@SuppressWarnings("nls")
public class TempImageStore {

    static final long DEFAULT_TTL_MILLIS = 5 * 60 * 1000L;

    private static final TempImageStore SHARED = new TempImageStore(Path.of(System.getProperty("java.io.tmpdir")),
            System::currentTimeMillis, DEFAULT_TTL_MILLIS);

    private final Path directory;
    private final LongSupplier clock;
    private final long ttlMillis;
    private final Map<String, TrackedImage> trackedImages = new LinkedHashMap<>();

    public TempImageStore(Path directory, LongSupplier clock, long ttlMillis) {
        this.directory = directory;
        this.clock = clock;
        this.ttlMillis = ttlMillis;
    }

    public static TempImageStore shared() {
        return SHARED;
    }

    public synchronized ExportLease openLease() {
        sweepExpired();
        long now = clock.getAsLong();
        return new ExportLease(this, UUID.randomUUID().toString(), now + ttlMillis);
    }

    public synchronized String writePng(ExportLease lease, byte[] pngBytes) throws IOException {
        Path path = Files.createTempFile(directory, "archi-mcp-view-", ".png");
        Files.write(path, pngBytes);

        long expiresAt = lease.isTimedOut() ? clock.getAsLong() : lease.getExpiresAt();
        trackedImages.put(lease.getId(), new TrackedImage(path, expiresAt));
        return path.toUri().toString();
    }

    public synchronized int sweepExpired() {
        long now = clock.getAsLong();
        int removed = 0;
        Iterator<Map.Entry<String, TrackedImage>> iterator = trackedImages.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<String, TrackedImage> entry = iterator.next();
            if(entry.getValue().expiresAt > now) {
                continue;
            }

            deleteQuietly(entry.getValue().path);
            iterator.remove();
            removed++;
        }

        return removed;
    }

    public synchronized int cleanupAll() {
        int removed = 0;
        Iterator<Map.Entry<String, TrackedImage>> iterator = trackedImages.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<String, TrackedImage> entry = iterator.next();
            deleteQuietly(entry.getValue().path);
            iterator.remove();
            removed++;
        }
        return removed;
    }

    public synchronized int trackedCount() {
        return trackedImages.size();
    }

    public synchronized boolean contains(Path path) {
        for(TrackedImage trackedImage : trackedImages.values()) {
            if(trackedImage.path.equals(path)) {
                return true;
            }
        }
        return false;
    }

    synchronized void markTimedOut(String leaseId) {
        TrackedImage trackedImage = trackedImages.get(leaseId);
        if(trackedImage != null) {
            trackedImage.expiresAt = clock.getAsLong();
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        }
        catch(IOException ex) {
            // Best-effort cleanup only.
        }
    }

    public static final class ExportLease {
        private final TempImageStore store;
        private final String id;
        private final long expiresAt;
        private boolean timedOut;

        private ExportLease(TempImageStore store, String id, long expiresAt) {
            this.store = store;
            this.id = id;
            this.expiresAt = expiresAt;
        }

        public synchronized void markTimedOut() {
            timedOut = true;
            store.markTimedOut(id);
        }

        public synchronized boolean isTimedOut() {
            return timedOut;
        }

        String getId() {
            return id;
        }

        long getExpiresAt() {
            return expiresAt;
        }
    }

    private static final class TrackedImage {
        private final Path path;
        private long expiresAt;

        private TrackedImage(Path path, long expiresAt) {
            this.path = path;
            this.expiresAt = expiresAt;
        }
    }
}
