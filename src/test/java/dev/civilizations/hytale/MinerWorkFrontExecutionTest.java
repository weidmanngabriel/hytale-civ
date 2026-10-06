package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.MineHeading;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MinerWorkFrontExecutionTest {

    @Test
    void initialCenterlineStartsOutsideConnectorEdge() {
        BuildingBounds connector = new BuildingBounds(10, 20, 30, 14, 24, 36);

        assertEquals(new BlockPosition(14, 20, 33),
            MinerWorkSystem.initialCenterlineOrigin(connector, MineHeading.EAST));
        assertEquals(new BlockPosition(9, 20, 33),
            MinerWorkSystem.initialCenterlineOrigin(connector, MineHeading.WEST));
        assertEquals(new BlockPosition(12, 20, 36),
            MinerWorkSystem.initialCenterlineOrigin(connector, MineHeading.SOUTH));
        assertEquals(new BlockPosition(12, 20, 29),
            MinerWorkSystem.initialCenterlineOrigin(connector, MineHeading.NORTH));
    }
}
