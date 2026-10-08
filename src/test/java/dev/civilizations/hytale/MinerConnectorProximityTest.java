package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinerConnectorProximityTest {
    private static final BuildingBounds CONNECTOR =
        new BuildingBounds(10, 60, 20, 14, 63, 24);

    @Test void recognizesOriginalBoundsAndTwoBlocksBesideThem() {
        assertTrue(MinerConnectorProximity.isWithinNavigationRange(CONNECTOR, new BlockPosition(12, 61, 22)));
        assertTrue(MinerConnectorProximity.isWithinNavigationRange(CONNECTOR, new BlockPosition(8, 61, 22)));
        assertTrue(MinerConnectorProximity.isWithinNavigationRange(CONNECTOR, new BlockPosition(15, 61, 22)));
        assertTrue(MinerConnectorProximity.isWithinNavigationRange(CONNECTOR, new BlockPosition(12, 61, 18)));
        assertTrue(MinerConnectorProximity.isWithinNavigationRange(CONNECTOR, new BlockPosition(12, 61, 25)));
    }

    @Test void rejectsPositionsOutsideHorizontalMargin() {
        assertFalse(MinerConnectorProximity.isWithinNavigationRange(CONNECTOR, new BlockPosition(7, 61, 22)));
        assertFalse(MinerConnectorProximity.isWithinNavigationRange(CONNECTOR, new BlockPosition(17, 61, 22)));
    }

    @Test void verticalLimitsRemainExactlyTheOriginalConnectorLimits() {
        assertFalse(MinerConnectorProximity.isWithinNavigationRange(CONNECTOR, new BlockPosition(12, 59, 22)));
        assertFalse(MinerConnectorProximity.isWithinNavigationRange(CONNECTOR, new BlockPosition(12, 63, 22)));
        assertTrue(MinerConnectorProximity.isWithinNavigationRange(CONNECTOR, new BlockPosition(12, 62, 22)));
    }

    @Test void originalBoundsAreNotMutated() {
        MinerConnectorProximity.isWithinNavigationRange(CONNECTOR, new BlockPosition(8, 61, 22));
        assertEquals(new BuildingBounds(10, 60, 20, 14, 63, 24), CONNECTOR);
        assertFalse(CONNECTOR.containsBlock(new BlockPosition(8, 61, 22)));
    }
}
