package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinerAnchorNavigationContractTest {

    @Test
    void adapterUsesExactEmptyBlocksNativeNavStateAndNativeRepath() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerNavigationSystem.java")
        );

        assertTrue(source.contains("blockType != BlockType.EMPTY"),
            "anchors must reject every candidate whose block type is not exactly BlockType.EMPTY");
        assertTrue(source.contains("getActiveMotionController()"),
            "navigation recovery must inspect Hytale's active native motion controller");
        assertTrue(source.contains("getNavState()"),
            "navigation recovery must use native NavState as its primary failure signal");
        assertTrue(source.contains("setForceRecomputePath(true)"),
            "the first native failure must request Hytale path recomputation");
        assertTrue(source.contains("NavState.BLOCKED") && source.contains("NavState.ABORTED"));
        assertFalse(source.contains("stallSeconds"),
            "a custom stall timer must not replace the native navigation failure signal");
    }

    @Test
    void adapterKeepsAboveGroundCommuteOutsideLongDistanceTeleport() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerNavigationSystem.java")
        );

        int undergroundGate = source.indexOf("if (!runtime.reachedConnector || !undergroundOrConnector)");
        int teleportDecision = source.indexOf("MineNavigationPolicy.shouldTeleport");

        assertTrue(undergroundGate >= 0,
            "long-distance travel must be gated until the miner reached the tunnel connector");
        assertTrue(teleportDecision > undergroundGate,
            "the 50-block teleport decision must happen only after the underground/connector gate");
        assertTrue(source.contains("new Teleport("),
            "long-distance anchor travel must use Hytale's native Teleport component");
    }

    @Test
    void tunnelMembershipUsesLayerThreeGeometryInsteadOfLegacySegments() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerNavigationSystem.java")
        );

        assertTrue(source.contains("geometryForTunnel"));
        assertTrue(source.contains("MineTunnelGeometry"));
        assertFalse(source.contains("MineSegment"));
        assertFalse(source.contains("segmentIds()"));
    }
}
