package dev.civilizations.plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded in-memory diagnostic event history for MCP/civdev inspection. */
final class CivDevEventHistory {
    static final int MAX_EVENTS_PER_ENTITY = 128;

    private final Map<UUID, ArrayDeque<Event>> events = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    void record(UUID entityId, String type, Map<String, ?> details) {
        if (entityId == null || type == null || type.isBlank()) {
            return;
        }
        Map<String, Object> copied = new LinkedHashMap<>();
        if (details != null) {
            details.forEach((key, value) -> copied.put(key, value));
        }
        Event event = new Event(
            sequence.incrementAndGet(),
            System.currentTimeMillis(),
            type,
            Map.copyOf(copied)
        );
        ArrayDeque<Event> queue = events.computeIfAbsent(entityId, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(event);
            while (queue.size() > MAX_EVENTS_PER_ENTITY) {
                queue.removeFirst();
            }
        }
    }

    List<Event> snapshot(UUID entityId) {
        ArrayDeque<Event> queue = events.get(entityId);
        if (queue == null) {
            return List.of();
        }
        synchronized (queue) {
            return List.copyOf(new ArrayList<>(queue));
        }
    }

    void clear(UUID entityId) {
        if (entityId != null) {
            events.remove(entityId);
        }
    }

    record Event(long sequence, long timestampMillis, String type, Map<String, Object> details) {
    }
}
