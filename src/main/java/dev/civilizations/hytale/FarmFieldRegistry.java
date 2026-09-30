package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
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
        UUID buildingId,
        UUID worldId,
        String workVolumeId,
        Vector3i workMarker,
        BuildingBounds fieldBounds
    ) {
        FieldSite site = new FieldSite(
            buildingId,
            worldId,
            workVolumeId,
            new Vector3d(workMarker.x + 0.5, workMarker.y, workMarker.z + 0.5),
            fieldBounds
        );
        fields.put(site.id(), site);
        return site;
    }

    public void removeByBuildingInstance(UUID worldId, UUID buildingId) {
        if (worldId == null || buildingId == null) return;
        fields.computeIfPresent(buildingId, (id, field) ->
            field.worldId().equals(worldId) ? null : field
        );
    }

    public void clearWorld(UUID worldId) {
        if (worldId == null) return;
        fields.entrySet().removeIf(entry -> entry.getValue().worldId().equals(worldId));
    }

    public boolean isRegistered(FieldSite site) {
        return site != null && fields.get(site.id()) == site;
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
        String workVolumeId,
        Vector3d workTarget,
        BuildingBounds fieldBounds
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
