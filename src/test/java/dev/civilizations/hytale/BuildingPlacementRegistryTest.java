package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
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
}
