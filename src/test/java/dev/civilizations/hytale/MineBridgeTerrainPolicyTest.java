package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineBridgeTerrainPolicyTest {
    @Test
    void flatTunnelSpanCanBeConsideredForBridge() {
        assertTrue(MineBridgeTerrainPolicy.isLevelAcross(new int[]{15, 15, 15, 15, 15}, 0, 4));
    }

    @Test
    void staircaseAndSlopingLandingCannotBecomeBridge() {
        assertFalse(MineBridgeTerrainPolicy.isLevelAcross(new int[]{15, 15, 16, 17, 17}, 0, 4));
        assertFalse(MineBridgeTerrainPolicy.isLevelAcross(new int[]{15, 15, 15, 16}, 0, 3));
        assertFalse(MineBridgeTerrainPolicy.isLevelAcross(new int[]{17, 16, 15, 15}, 0, 3));
    }

    @Test
    void rejectsInvalidSpan() {
        assertFalse(MineBridgeTerrainPolicy.isLevelAcross(new int[]{15}, -1, 0));
        assertFalse(MineBridgeTerrainPolicy.isLevelAcross(new int[]{15}, 0, 1));
        assertFalse(MineBridgeTerrainPolicy.isLevelAcross(new int[]{15}, 1, 0));
    }
}
