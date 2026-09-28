package dev.civilizations.hytale;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime authority for occupied Civ placement footprints.
 */
public final class BuildingPlacementRegistry {

    private final Map<UUID, List<PrefabPlacementService.PlacementFootprint>> footprints =
        new ConcurrentHashMap<>();

    public boolean overlaps(
        UUID worldId,
        PrefabPlacementService.PlacementFootprint candidate
    ) {
        if (worldId == null || candidate == null) {
            return false;
        }
        return footprints.getOrDefault(worldId, List.of()).stream()
            .anyMatch(candidate::overlaps);
    }

    public synchronized void register(
        UUID worldId,
        PrefabPlacementService.PlacementFootprint footprint
    ) {
        List<PrefabPlacementService.PlacementFootprint> existing =
            footprints.getOrDefault(worldId, List.of());
        java.util.ArrayList<PrefabPlacementService.PlacementFootprint> updated =
            new java.util.ArrayList<>(existing);
        updated.add(footprint);
        footprints.put(worldId, List.copyOf(updated));
    }
}
