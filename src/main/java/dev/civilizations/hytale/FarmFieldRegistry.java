package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry for completed farm fields. The work target comes from the
 * prefab's semantic field trigger marker, not from prefab geometry.
 */
public final class FarmFieldRegistry {
    private final Map<UUID, FieldSite> fields = new ConcurrentHashMap<>();

    public FieldSite registerField(
        UUID worldId,
        Vector3i workMarker,
        PrefabPlacementService.PlacementFootprint footprint
    ) {
        FieldSite site = new FieldSite(
            UUID.randomUUID(),
            worldId,
            new Vector3d(workMarker.x + 0.5, workMarker.y, workMarker.z + 0.5),
            footprint
        );
        fields.put(site.id(), site);
        return site;
    }

    public FieldSite nearestField(UUID worldId, BlockPosition origin) {
        if (worldId == null || origin == null) return null;
        return fields.values().stream()
            .filter(field -> field.worldId().equals(worldId))
            .min(Comparator.comparingDouble(field -> field.distanceSquared(origin)))
            .orElse(null);
    }

    public record FieldSite(
        UUID id,
        UUID worldId,
        Vector3d workTarget,
        PrefabPlacementService.PlacementFootprint footprint
    ) {
        public FieldSite {
            workTarget = new Vector3d(workTarget);
        }

        @Override
        public Vector3d workTarget() {
            return new Vector3d(workTarget);
        }

        private double distanceSquared(BlockPosition origin) {
            double dx = workTarget.x - (origin.x() + 0.5);
            double dz = workTarget.z - (origin.z() + 0.5);
            return dx * dx + dz * dz;
        }
    }
}
