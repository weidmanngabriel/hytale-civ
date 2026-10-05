package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.BuildingOrientation;
import dev.civilizations.core.BuildingTypes;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Map<UUID, Set<UUID>> upgrading = new ConcurrentHashMap<>();

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

    public synchronized boolean beginUpgrade(UUID worldId, UUID buildingId) {
        if (findStored(worldId, buildingId) == null || isUpgrading(worldId, buildingId)) {
            return false;
        }
        return upgrading.computeIfAbsent(worldId, ignored -> ConcurrentHashMap.newKeySet())
            .add(buildingId);
    }

    public void cancelUpgrade(UUID worldId, UUID buildingId) {
        if (worldId == null || buildingId == null) return;
        Set<UUID> worldUpgrades = upgrading.get(worldId);
        if (worldUpgrades == null) return;
        worldUpgrades.remove(buildingId);
        if (worldUpgrades.isEmpty()) {
            upgrading.remove(worldId, worldUpgrades);
        }
    }

    public boolean isUpgrading(UUID worldId, UUID buildingId) {
        return worldId != null && buildingId != null
            && upgrading.getOrDefault(worldId, Set.of()).contains(buildingId);
    }

    public synchronized BuildingInstance completeBuilding(
        UUID worldId,
        UUID siteId,
        String buildingType,
        PrefabPlacementService.PlacedMarker boundsMarker,
        List<PrefabPlacementService.PlacedMarker> semanticVolumes,
        PrefabPlacementService.PlacementCandidate placement
    ) {
        return completeBuilding(
            worldId,
            siteId,
            buildingType,
            boundsMarker,
            semanticVolumes,
            placement,
            BuildingOrientation.NORTH
        );
    }

    public synchronized BuildingInstance completeBuilding(
        UUID worldId,
        UUID siteId,
        String buildingType,
        PrefabPlacementService.PlacedMarker boundsMarker,
        List<PrefabPlacementService.PlacedMarker> semanticVolumes,
        PrefabPlacementService.PlacementCandidate placement,
        BuildingOrientation orientation
    ) {
        if (worldId == null || siteId == null || boundsMarker == null
            || boundsMarker.bounds() == null) {
            throw new IllegalArgumentException("Completed building requires authored bounds.");
        }
        if (orientation == null) {
            throw new IllegalArgumentException("Completed building requires an orientation.");
        }

        release(worldId, siteId);

        BuildingInstance upgradeTarget = findUpgradeTarget(worldId, buildingType, placement);
        if (upgradeTarget != null) {
            int targetPhase = BuildingTypes.nextPhase(buildingType, upgradeTarget.phase());
            if (targetPhase == 0) {
                throw new IllegalStateException("Upgrading building has no next phase.");
            }
            BuildingInstance upgraded = new BuildingInstance(
                upgradeTarget.id(),
                worldId,
                buildingType,
                targetPhase,
                boundsMarker.id(),
                boundsMarker.bounds(),
                semanticVolumes,
                upgradeTarget.orientation(),
                placement
            );
            replace(worldId, upgraded);
            cancelUpgrade(worldId, upgradeTarget.id());
            return upgraded;
        }

        BuildingInstance instance = new BuildingInstance(
            siteId,
            worldId,
            buildingType,
            1,
            boundsMarker.id(),
            boundsMarker.bounds(),
            semanticVolumes,
            orientation,
            placement
        );
        replace(worldId, instance);
        return instance;
    }

    private BuildingInstance findUpgradeTarget(
        UUID worldId,
        String buildingType,
        PrefabPlacementService.PlacementCandidate placement
    ) {
        if (placement == null || placement.footprint() == null) return null;
        Set<UUID> worldUpgrades = upgrading.getOrDefault(worldId, Set.of());
        return buildings.getOrDefault(worldId, List.of()).stream()
            .filter(building -> worldUpgrades.contains(building.id()))
            .filter(building -> building.buildingType().equals(buildingType))
            .filter(building -> building.bounds().overlapsHorizontal(
                placement.footprint().minX(),
                placement.footprint().minZ(),
                placement.footprint().maxX() + 1.0,
                placement.footprint().maxZ() + 1.0
            ))
            .findFirst()
            .orElse(null);
    }

    public synchronized BuildingInstance completeUpgrade(
        UUID worldId,
        UUID buildingId,
        int targetPhase,
        PrefabPlacementService.PlacedMarker boundsMarker,
        List<PrefabPlacementService.PlacedMarker> semanticVolumes,
        PrefabPlacementService.PlacementCandidate placement
    ) {
        BuildingInstance existing = findStored(worldId, buildingId);
        if (existing == null || !isUpgrading(worldId, buildingId)
            || boundsMarker == null || boundsMarker.bounds() == null) {
            throw new IllegalArgumentException("Upgrade requires an active building upgrade and authored bounds.");
        }
        if (BuildingTypes.nextPhase(existing.buildingType(), existing.phase()) != targetPhase) {
            throw new IllegalArgumentException("Target phase is not the next authored building phase.");
        }

        BuildingInstance upgraded = new BuildingInstance(
            existing.id(),
            worldId,
            existing.buildingType(),
            targetPhase,
            boundsMarker.id(),
            boundsMarker.bounds(),
            semanticVolumes,
            existing.orientation(),
            placement
        );
        replace(worldId, upgraded);
        cancelUpgrade(worldId, buildingId);
        return upgraded;
    }

    private void replace(UUID worldId, BuildingInstance instance) {
        List<BuildingInstance> updated =
            new ArrayList<>(buildings.getOrDefault(worldId, List.of()));
        updated.removeIf(existing -> existing.id().equals(instance.id()));
        updated.add(instance);
        buildings.put(worldId, List.copyOf(updated));
    }

    public synchronized BuildingInstance remove(UUID worldId, UUID buildingId) {
        BuildingInstance existing = findStored(worldId, buildingId);
        if (existing == null) {
            return null;
        }
        List<BuildingInstance> updated =
            new ArrayList<>(buildings.getOrDefault(worldId, List.of()));
        updated.removeIf(building -> building.id().equals(buildingId));
        if (updated.isEmpty()) {
            buildings.remove(worldId);
        } else {
            buildings.put(worldId, List.copyOf(updated));
        }
        cancelUpgrade(worldId, buildingId);
        return existing;
    }

    public synchronized void restoreWorld(
        UUID worldId,
        List<BuildingInstance> restored
    ) {
        if (worldId == null) {
            return;
        }
        reservations.remove(worldId);
        upgrading.remove(worldId);
        buildings.put(worldId, List.copyOf(restored == null ? List.of() : restored));
    }

    public List<BuildingInstance> buildings(UUID worldId) {
        return List.copyOf(buildings.getOrDefault(worldId, List.of()));
    }

    /** Picking keeps an upgrading building visible so its UI can show the construction state. */
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

    /**
     * Gameplay lookups treat an upgrading building as unavailable. Worker systems that resolve
     * a persistent workplace through this method therefore stop routing into the construction area.
     */
    public BuildingInstance find(UUID worldId, UUID buildingId) {
        BuildingInstance building = findStored(worldId, buildingId);
        if (building == null || isUpgrading(worldId, buildingId)) {
            return null;
        }
        return building;
    }

    private BuildingInstance findStored(UUID worldId, UUID buildingId) {
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
        int phase,
        String boundsVolumeId,
        BuildingBounds bounds,
        List<PrefabPlacementService.PlacedMarker> semanticVolumes,
        BuildingOrientation orientation,
        PrefabPlacementService.PlacementCandidate placement
    ) {
        public BuildingInstance {
            if (phase < 1) {
                throw new IllegalArgumentException("Building phase must be at least 1.");
            }
            if (orientation == null) {
                throw new IllegalArgumentException("Building orientation cannot be null.");
            }
            semanticVolumes = List.copyOf(semanticVolumes);
        }

        public BuildingInstance(
            UUID id,
            UUID worldId,
            String buildingType,
            int phase,
            String boundsVolumeId,
            BuildingBounds bounds,
            List<PrefabPlacementService.PlacedMarker> semanticVolumes,
            PrefabPlacementService.PlacementCandidate placement
        ) {
            this(
                id,
                worldId,
                buildingType,
                phase,
                boundsVolumeId,
                bounds,
                semanticVolumes,
                BuildingOrientation.NORTH,
                placement
            );
        }

        public BuildingInstance(
            UUID id,
            UUID worldId,
            String buildingType,
            String boundsVolumeId,
            BuildingBounds bounds,
            List<PrefabPlacementService.PlacedMarker> semanticVolumes,
            PrefabPlacementService.PlacementCandidate placement
        ) {
            this(
                id,
                worldId,
                buildingType,
                1,
                boundsVolumeId,
                bounds,
                semanticVolumes,
                BuildingOrientation.NORTH,
                placement
            );
        }

        public int workerCapacity() {
            return BuildingTypes.workerCapacity(buildingType, phase);
        }
    }
}
