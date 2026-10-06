package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineWorkFront;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivMineDebugServiceTest {

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
}
