/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.read;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable page descriptor for sorted query results.
 */
public final class QueryResultPage<T> {

    private final List<T> items;
    private final int total;
    private final int offset;
    private final int limit;
    private final boolean hasMore;

    public QueryResultPage(List<T> items, int total, int offset, int limit, boolean hasMore) {
        if(items == null) {
            throw new IllegalArgumentException("items must not be null");
        }

        this.items = List.copyOf(new ArrayList<>(items));
        this.total = total;
        this.offset = offset;
        this.limit = limit;
        this.hasMore = hasMore;
    }

    public List<T> getItems() {
        return items;
    }

    public int getTotal() {
        return total;
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public boolean hasMore() {
        return hasMore;
    }
}
