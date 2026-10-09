package dev.civilizations.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DwarvenMineFinishPlanTest {
    @Test
    void archPairsAvoidOpenNavigationLaneAndGridIntersections() {
        UUID mine = UUID.fromString("00000000-0000-0000-0000-000000000111");
        var plan = DwarvenMinePlanner.plan(mine, new BlockPosition(200, 100, 200),
            MineHeading.EAST, 160, 64, 55L);
        var main = plan.mainTunnel();
        UUID tunnelId = main.tunnel().id();
        assertNull(DwarvenMineFinishPlan.at(tunnelId, main.geometry(), 7));
        assertNull(DwarvenMineFinishPlan.at(tunnelId, main.geometry(), 32));
        var arch = DwarvenMineFinishPlan.at(tunnelId, main.geometry(), 8);
        assertNotNull(arch);
        assertEquals(8, arch.sliceIndex());
        assertFalse(main.geometry().navigationCoreBlocks().contains(arch.left()));
        assertFalse(main.geometry().navigationCoreBlocks().contains(arch.right()));
        assertEquals(arch, DwarvenMineFinishPlan.at(tunnelId, main.geometry(), 8));
        assertNotNull(DwarvenMineFinishPlan.at(tunnelId, main.geometry(), 16));
        for (var side : plan.tunnels()) {
            if (side.tunnel().kind() == MineTunnel.Kind.BRANCH) {
                var feature = DwarvenMineFinishPlan.at(side.tunnel().id(), side.geometry(), 8);
                assertNotNull(feature);
                assertFalse(side.geometry().navigationCoreBlocks().contains(feature.left()));
                assertFalse(side.geometry().navigationCoreBlocks().contains(feature.right()));
            }
        }
    }
}
