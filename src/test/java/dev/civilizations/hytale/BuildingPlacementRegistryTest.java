package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuildingPlacementRegistryTest {

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
