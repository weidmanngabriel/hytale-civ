package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineWorkFront;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivMineDebugServiceTest {

    @Test
    void snapshotByIdReturnsTheRequestedMineInsteadOfTheNearestMine() {
        UUID worldId = UUID.randomUUID();
        UUID firstMineId = UUID.randomUUID();
        UUID requestedMineId = UUID.randomUUID();
        BuildingPlacementRegistry buildings = new BuildingPlacementRegistry();
        buildings.restoreWorld(worldId, java.util.List.of(
            mine(firstMineId, worldId, 0),
            mine(requestedMineId, worldId, 10)
        ));
        CivMineDebugService service = new CivMineDebugService(buildings, new MineTunnelRegistry(null));

        CivMineDebugService.MineDebugSnapshot snapshot = service.snapshot(worldId, requestedMineId);

        assertEquals(requestedMineId, snapshot.mine().id());
        assertNull(service.snapshot(worldId, UUID.randomUUID()));
    }

    @Test
    void tunnelDebugStateComesFromSemanticWorkFront() {
        UUID tunnelId = UUID.randomUUID();
        MineTunnel tunnel = new MineTunnel(tunnelId, MineTunnel.Kind.MAIN, null, 0,
            new BlockPosition(0, 20, 0));

        CivMineDebugService.TunnelDebugSnapshot active = new CivMineDebugService.TunnelDebugSnapshot(
            tunnel,
            new MineWorkFront(UUID.randomUUID(), tunnelId, new BlockPosition(10, 20, 0),
                MineWorkFront.State.ACTIVE),
            null
        );
        CivMineDebugService.TunnelDebugSnapshot open = new CivMineDebugService.TunnelDebugSnapshot(
            tunnel,
            new MineWorkFront(UUID.randomUUID(), tunnelId, new BlockPosition(10, 20, 0),
                MineWorkFront.State.OPEN),
            null
        );

        assertTrue(active.active());
        assertFalse(active.open());
        assertFalse(open.active());
        assertTrue(open.open());
    }

    private static BuildingPlacementRegistry.BuildingInstance mine(UUID id, UUID worldId, double x) {
        return new BuildingPlacementRegistry.BuildingInstance(
            id,
            worldId,
            "mine",
            "bounds",
            new BuildingBounds(x, 0, 0, x + 4, 4, 4),
            java.util.List.of(),
            null
        );
    }
}
