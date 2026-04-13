/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.observability;

import java.time.Instant;
import java.util.UUID;

/**
 * Request-scoped observability context.
 */
public final class RequestContext {

    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

    private final String requestId;
    private final Instant timestamp;

    private RequestContext(String requestId, Instant timestamp) {
        this.requestId = requestId;
        this.timestamp = timestamp;
    }

    public static RequestContext create() {
        return new RequestContext(UUID.randomUUID().toString(), Instant.now());
    }

    public static void bind(RequestContext context) {
        CURRENT.set(context);
    }

    public static RequestContext current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTimestamp() {
        return timestamp.toString();
    }
}
