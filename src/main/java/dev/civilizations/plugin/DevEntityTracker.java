package dev.civilizations.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Session handles distinguish spawned entities from borrowed existing inhabitants. */
final class DevEntityTracker<T> {
    record Entry<T>(String handle, T entity, boolean owned) {}
    private final Map<String, Entry<T>> entries = new LinkedHashMap<>();

    String add(T entity, boolean owned) {
        for (var entry : entries.values()) if (entry.entity().equals(entity)) return entry.handle();
        if (entries.size() >= 64 || (owned && entries.values().stream().filter(Entry::owned).count() >= 32))
            throw new IllegalStateException("Too many tracked NPCs; reset spawned NPCs first");
        String handle = UUID.randomUUID().toString();
        entries.put(handle, new Entry<>(handle, entity, owned));
        return handle;
    }
    T get(String handle) {
        var entry = entries.get(handle);
        if (entry == null) throw new IllegalArgumentException("Unknown NPC handle");
        return entry.entity();
    }
    boolean owned(String handle) { var entry = entries.get(handle); return entry != null && entry.owned(); }
    List<Entry<T>> entries() { return List.copyOf(entries.values()); }
    void forget(String handle) { entries.remove(handle); }
    int resetOwned(Predicate<T> remove) {
        int count = 0;
        for (var entry : new ArrayList<>(entries.values())) {
            if (entry.owned() && remove.test(entry.entity())) {
                entries.remove(entry.handle()); count++;
            }
        }
        return count;
    }
}
