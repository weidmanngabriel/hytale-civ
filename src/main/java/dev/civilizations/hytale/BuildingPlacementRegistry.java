package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime authority for reserved construction footprints and completed Civ buildings.
 *
 * <p>Construction sites reserve their block-derived footprint. Once a prefab with a
 * {@code civ.type=building_bounds} volume completes, that authored volume replaces
 * the temporary footprint as the authoritative area for picking and protection.
 */
public final class BuildingPlacementRegistry {

    private final Map<UUID, Map<UUID, PrefabPlacementService.PlacementFootprint>> reservations =
        new ConcurrentHashMap<>();
    private final Map<UUID, List<BuildingInstance>> buildings = new ConcurrentHashMap<>();

    public boolean overlaps(
        UUID worldId,
        PrefabPlacementService.PlacementFootprint candidate
    ) {
        if (worldId == null || candidate == null) {
            return false;
        }

        boolean reserved = reservations.getOrDefault(worldId, Map.of()).values().stream()
            .anyMatch(candidate::overlaps);
        if (reserved) {
            return true;
        }

        return buildings.getOrDefault(worldId, List.of()).stream()
            .map(BuildingInstance::bounds)
            .anyMatch(bounds -> bounds.overlapsHorizontal(
                candidate.minX(),
                candidate.minZ(),
                candidate.maxX() + 1.0,
                candidate.maxZ() + 1.0
            ));
    }

    public void reserve(
        UUID worldId,
        UUID siteId,
        PrefabPlacementService.PlacementFootprint footprint
    ) {
        if (worldId == null || siteId == null || footprint == null) {
            return;
        }
        reservations.computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>())
            .put(siteId, footprint);
    }

    public void release(UUID worldId, UUID siteId) {
        if (worldId == null || siteId == null) {
            return;
        }
        Map<UUID, PrefabPlacementService.PlacementFootprint> worldReservations =
            reservations.get(worldId);
        if (worldReservations == null) {
            return;
        }
        worldReservations.remove(siteId);
        if (worldReservations.isEmpty()) {
            reservations.remove(worldId, worldReservations);
        }
    }

    public synchronized BuildingInstance completeBuilding(
        UUID worldId,
        UUID siteId,
        String buildingType,
        PrefabPlacementService.PlacedMarker boundsMarker,
        List<PrefabPlacementService.PlacedMarker> semanticVolumes
    ) {
        if (worldId == null || siteId == null || boundsMarker == null
            || boundsMarker.bounds() == null) {
            throw new IllegalArgumentException("Completed building requires authored bounds.");
        }

        release(worldId, siteId);
        BuildingInstance instance = new BuildingInstance(
            siteId,
            worldId,
            buildingType,
            boundsMarker.id(),
            boundsMarker.bounds(),
            semanticVolumes
        );
        List<BuildingInstance> updated =
            new ArrayList<>(buildings.getOrDefault(worldId, List.of()));
        updated.removeIf(existing -> existing.id().equals(siteId));
        updated.add(instance);
        buildings.put(worldId, List.copyOf(updated));
        return instance;
    }

    public BuildingInstance findAt(UUID worldId, Vector3i block) {
        if (worldId == null || block == null) {
            return null;
        }
        BlockPosition position = new BlockPosition(block.x, block.y, block.z);
        return buildings.getOrDefault(worldId, List.of()).stream()
            .filter(building -> building.bounds().containsBlock(position))
            .findFirst()
            .orElse(null);
    }

    public BuildingInstance find(UUID worldId, UUID buildingId) {
        if (worldId == null || buildingId == null) {
            return null;
        }
        return buildings.getOrDefault(worldId, List.of()).stream()
            .filter(building -> building.id().equals(buildingId))
            .findFirst()
            .orElse(null);
    }

    public record BuildingInstance(
        UUID id,
        UUID worldId,
        String buildingType,
        String boundsVolumeId,
        BuildingBounds bounds,
        List<PrefabPlacementService.PlacedMarker> semanticVolumes
    ) {
        public BuildingInstance {
            semanticVolumes = List.copyOf(semanticVolumes);
        }
    }
}
