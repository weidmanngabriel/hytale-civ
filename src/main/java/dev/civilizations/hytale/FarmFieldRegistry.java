package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import org.joml.Vector3d;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FarmFieldRegistry {
    private final Map<UUID, FieldSite> fields = new ConcurrentHashMap<>();

    public FieldSite registerField(UUID worldId, PrefabPlacementService.PlacementFootprint footprint) {
        FieldSite site = new FieldSite(UUID.randomUUID(), worldId, footprint);
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

    public record FieldSite(UUID id, UUID worldId, PrefabPlacementService.PlacementFootprint footprint) {
        public Vector3d workTarget() {
            return new Vector3d(
                (footprint.minX() + footprint.maxX() + 1) / 2.0,
                footprint.floorY() + 1.0,
                (footprint.minZ() + footprint.maxZ() + 1) / 2.0
            );
        }

        private double distanceSquared(BlockPosition origin) {
            Vector3d target = workTarget();
            double dx = target.x - (origin.x() + 0.5);
            double dz = target.z - (origin.z() + 0.5);
            return dx * dx + dz * dz;
        }
    }
}
