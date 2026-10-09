package dev.civilizations.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DwarvenMinePlannerTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Test
    void buildsStraightConnectedRightAngleTunnelsDeterministically() {
        BlockPosition start = new BlockPosition(200, 80, 200);
        var first = DwarvenMinePlanner.plan(ID, start, MineHeading.EAST, 160, 64, 77L);
        var again = DwarvenMinePlanner.plan(ID, start, MineHeading.EAST, 160, 64, 77L);
        assertEquals(first, again);
        assertEquals(9, first.tunnels().size());
        assertEquals(160, first.mainTunnel().path().points().size());
        for (var tunnel : first.tunnels()) {
            var points = tunnel.path().points();
            int dx = (int)(points.get(1).x() - points.get(0).x());
            int dz = (int)(points.get(1).z() - points.get(0).z());
            assertEquals(1, Math.abs(dx) + Math.abs(dz));
            assertTrue(tunnel.geometry().stepTransitions().isEmpty());
            assertTrue(tunnel.geometry().excavationBlocks()
                .containsAll(tunnel.geometry().navigationCoreBlocks()));
            for (int i = 1; i < points.size(); i++) {
                assertEquals(dx, (int)(points.get(i).x() - points.get(i - 1).x()));
                assertEquals(dz, (int)(points.get(i).z() - points.get(i - 1).z()));
                assertEquals(points.get(i - 1).y(), points.get(i).y());
            }
            if (tunnel.tunnel().kind() == MineTunnel.Kind.BRANCH) {
                assertEquals(0, dx);
                assertTrue(first.mainTunnel().geometry().navigationCoreBlocks()
                    .contains(tunnel.geometry().slices().getFirst().floorCenter()));
            }
        }
    }

    @Test
    void sharesMinerWorkplaceClassification() {
        assertTrue(MineBuildingTypes.isMine("mine"));
        assertTrue(MineBuildingTypes.isMine("dwarf_mine"));
        assertFalse(MineBuildingTypes.isMine("farm"));
        assertEquals(MineHeading.EAST, DwarvenMinePlanner.cardinal(MineHeading.NORTH_EAST));
    }
}
