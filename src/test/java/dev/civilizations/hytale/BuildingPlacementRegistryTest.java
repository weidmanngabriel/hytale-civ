package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuildingPlacementRegistryTest {

    @Test
    void activeReservationProtectsConstructionFootprint() {
        BuildingPlacementRegistry registry = new BuildingPlacementRegistry();
        UUID worldId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        PrefabPlacementService.PlacementFootprint footprint =
            new PrefabPlacementService.PlacementFootprint(10, 20, 12, 22, 64);

        registry.reserve(worldId, siteId, footprint);

        assertTrue(registry.isProtected(worldId, new BlockPosition(11, 80, 21)));
        assertFalse(registry.isProtected(worldId, new BlockPosition(13, 64, 21)));

        registry.release(worldId, siteId);
        assertFalse(registry.isProtected(worldId, new BlockPosition(11, 64, 21)));
    }

    @Test
    void restoreWorldClearsTransientReservations() {
        BuildingPlacementRegistry registry = new BuildingPlacementRegistry();
        UUID worldId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        PrefabPlacementService.PlacementFootprint footprint =
            new PrefabPlacementService.PlacementFootprint(0, 0, 2, 2, 64);

        registry.reserve(worldId, siteId, footprint);
        assertTrue(registry.overlaps(worldId, footprint));

        registry.restoreWorld(worldId, List.of());

        assertFalse(registry.overlaps(worldId, footprint));
        assertTrue(registry.buildings(worldId).isEmpty());
    }
    @Test
    void demolitionCleanupReleasesOnlyOrphanedOverlappingReservations() {
        BuildingPlacementRegistry registry = new BuildingPlacementRegistry();
        UUID worldId = UUID.randomUUID();
        UUID orphanId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();
        UUID farId = UUID.randomUUID();
        registry.reserve(worldId, orphanId, new PrefabPlacementService.PlacementFootprint(10, 10, 12, 12, 64));
        registry.reserve(worldId, liveId, new PrefabPlacementService.PlacementFootprint(11, 11, 13, 13, 64));
        registry.reserve(worldId, farId, new PrefabPlacementService.PlacementFootprint(30, 30, 32, 32, 64));

        List<UUID> removed = registry.releaseOrphanedReservationsOverlapping(
            worldId,
            new BuildingBounds(9, 60, 9, 14, 70, 14),
            java.util.Set.of(liveId)
        );

        assertTrue(removed.contains(orphanId));
        assertFalse(removed.contains(liveId));
        assertFalse(registry.isProtected(worldId, new BlockPosition(10, 64, 10)));
        assertTrue(registry.isProtected(worldId, new BlockPosition(12, 64, 12)));
        assertTrue(registry.isProtected(worldId, new BlockPosition(31, 64, 31)));
    }

}
