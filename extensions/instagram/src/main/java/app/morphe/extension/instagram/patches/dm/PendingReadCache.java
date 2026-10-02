/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 * See the included NOTICE file for GPLv3 terms that apply to this code.
 */
package app.morphe.extension.instagram.patches.dm;

import java.util.LinkedHashMap;

/** Keeps only recent native read requests, isolated by the exact login session. */
final class PendingReadCache<T> {
    private final LinkedHashMap<String, Entry<T>> entries = new LinkedHashMap<>();
    private final int capacity;
    private final long lifetime;
    private Object session;

    private static final class Entry<T> {
        final T value;
        final long timestamp, capturedAt;
        Entry(T value, long timestamp, long capturedAt) {
            this.value = value;
            this.timestamp = timestamp;
            this.capturedAt = capturedAt;
        }
    }

    PendingReadCache(int capacity, long lifetime) {
        this.capacity = capacity;
        this.lifetime = lifetime;
    }

    synchronized void put(Object session, String thread, long timestamp, long now, T value) {
        if (session == null || thread == null || thread.isEmpty() || value == null) return;
        if (this.session != session) {
            entries.clear();
            this.session = session;
        }
        entries.entrySet().removeIf(e -> now - e.getValue().capturedAt > lifetime);
        Entry<T> previous = entries.get(thread);
        if (previous != null && previous.timestamp > timestamp) return;
        entries.remove(thread);
        entries.put(thread, new Entry<>(value, timestamp, now));
        while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
    }

    synchronized T get(Object session, String thread, long now) {
        if (session == null || this.session != session) return null;
        Entry<T> entry = entries.get(thread);
        if (entry == null) return null;
        if (now - entry.capturedAt > lifetime) {
            entries.remove(thread);
            return null;
        }
        return entry.value;
    }

    /** Remove only the exact request that was successfully replayed. */
    synchronized boolean remove(Object session, String thread, T expected) {
        if (session == null || this.session != session || thread == null || expected == null) return false;
        Entry<T> entry = entries.get(thread);
        if (entry == null || entry.value != expected) return false;
        entries.remove(thread);
        return true;
    }
}
