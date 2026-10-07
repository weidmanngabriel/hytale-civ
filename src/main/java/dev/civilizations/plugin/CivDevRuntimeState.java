package dev.civilizations.plugin;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Tracks only entities created through civdev so reset cannot touch player-owned world state. */
final class CivDevRuntimeState {
    private final Set<UUID> spawned = new LinkedHashSet<>();

    synchronized void trackSpawn(UUID uuid) {
        if (uuid != null) {
            spawned.add(uuid);
        }
    }

    synchronized Set<UUID> spawnedSnapshot() {
        return Set.copyOf(spawned);
    }

    synchronized void forget(UUID uuid) {
        spawned.remove(uuid);
    }

    synchronized void clear() {
        spawned.clear();
    }
}
