/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 * See the included NOTICE file for GPLv3 terms that apply to this code.
 */
package app.morphe.extension.instagram.patches.dm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** A bounded set of actually viewed media, with an independent expiry for each item. */
final class PendingVisualReads {
    private final LinkedHashMap<String, Entry> items = new LinkedHashMap<>();
    private final int capacity;
    private final long lifetime;

    private static final class Entry {
        final Object[] request;
        final long capturedAt;
        Entry(Object[] request, long capturedAt) {
            this.request = request;
            this.capturedAt = capturedAt;
        }
    }

    /** Snapshot token used to consume only the entry that actually succeeded. */
    static final class Request {
        private final String item;
        private final Entry entry;

        private Request(String item, Entry entry) {
            this.item = item;
            this.entry = entry;
        }

        Object media() {
            return entry.request[0];
        }

        Object controller() {
            return entry.request[1];
        }
    }

    PendingVisualReads(int capacity, long lifetime) {
        this.capacity = capacity;
        this.lifetime = lifetime;
    }

    synchronized void put(String item, Object media, Object controller, long now) {
        if (item == null || item.isEmpty() || media == null || controller == null) return;
        items.entrySet().removeIf(e -> now - e.getValue().capturedAt > lifetime);
        items.remove(item);
        items.put(item, new Entry(new Object[] {media, controller}, now));
        while (items.size() > capacity) items.remove(items.keySet().iterator().next());
    }

    synchronized List<Request> requests(long now) {
        items.entrySet().removeIf(e -> now - e.getValue().capturedAt > lifetime);
        List<Request> result = new ArrayList<>();
        for (java.util.Map.Entry<String, Entry> item : items.entrySet()) {
            result.add(new Request(item.getKey(), item.getValue()));
        }
        return result;
    }

    /** A newer capture with the same item id must survive completion of an older snapshot. */
    synchronized boolean remove(Request request) {
        if (request == null || items.get(request.item) != request.entry) return false;
        items.remove(request.item);
        return true;
    }
}
