package dev.civilizations.simulation.prefab;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingOrientation;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineTuning;
import dev.civilizations.simulation.MineSimulationWorld;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinePrefabNavigationScenarioTest {

    @Test
    void realMineConnectsWorkplaceToTunnelAndExcavatesOneSegmentInsideLargerMountain() {
        MinePrefabNavigationScenario scenario = MinePrefabNavigationScenario.create();
        MinePrefabNavigationScenario.Snapshot initial = scenario.snapshot();

        assertNotNull(initial.model().requireMarker("workplace_access"));
        assertNotNull(initial.model().requireMarker("mine_tunnel_connector"));
        assertFalse(initial.pathToConnector().isEmpty());
        assertEquals(initial.workplace(), initial.pathToConnector().getFirst());
        assertEquals(initial.connector(), initial.pathToConnector().getLast());
        assertEquals(BuildingOrientation.NORTH, initial.orientation());
        assertFalse(initial.directionAuthored(),
            "The connector marker has no authored facing; direction comes from placement orientation plus geometry");

        assertFalse(initial.supportPrefab().cells().isEmpty(),
            "The real Mine_Support_01 prefab must contain authored support blocks");
        assertNoPrefabOverlap(initial);
        assertMountainExtendsBeyondSegment(initial);

        scenario.runToCompletion();
        MinePrefabNavigationScenario.Snapshot complete = scenario.snapshot();

        assertEquals(MinePrefabNavigationScenario.Phase.COMPLETE, complete.phase());
        assertEquals(MineTuning.blocksPerSegment(), complete.segment().nextBlockIndex());
        assertEquals(1, complete.segment().supportsPlaced());

        long air = complete.tunnelWorld().cells().values().stream()
            .filter(cell -> cell == MineSimulationWorld.Cell.AIR)
            .count();
        long supports = complete.tunnelWorld().cells().values().stream()
            .filter(cell -> cell == MineSimulationWorld.Cell.SUPPORT_POST
                || cell == MineSimulationWorld.Cell.SUPPORT_BEAM)
            .count();
        assertEquals(10, supports);
        assertEquals(MineTuning.blocksPerSegment() - supports, air);
        assertTrue(complete.segment().complete());

        Set<BlockPosition> segmentBlocks = new HashSet<>(complete.segment().blocks());
        BlockPosition untouchedMountain = complete.tunnelWorld().cells().keySet().stream()
            .filter(position -> !segmentBlocks.contains(position))
            .findFirst()
            .orElseThrow();
        assertEquals(MineSimulationWorld.Cell.SOLID, complete.tunnelWorld().get(untouchedMountain),
            "Rock outside the excavated segment must stay solid");
    }

    @Test
    void workerStaysOnTunnelFloorAndEveryBrokenBlockIsWithinFourBlockReach() {
        MinePrefabNavigationScenario scenario = MinePrefabNavigationScenario.create();
        int previousProgress = 0;
        int brokenBlocksObserved = 0;
        int guard = 1_000;

        while (scenario.step() && --guard > 0) {
            MinePrefabNavigationScenario.Snapshot snapshot = scenario.snapshot();
            if (snapshot.phase() != MinePrefabNavigationScenario.Phase.MINING_SEGMENT) continue;

            assertEquals(snapshot.segment().start().y(), snapshot.probe().y(),
                "Once mining starts, the worker feet must stay on tunnel floor height");

            int progress = snapshot.segment().nextBlockIndex();
            if (progress > previousProgress) {
                BlockPosition target = snapshot.lastAction();
                BlockPosition worker = snapshot.probe();
                double dx = target.x() - worker.x();
                double dy = target.y() - worker.y();
                double dz = target.z() - worker.z();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                assertTrue(distance <= MinePrefabNavigationScenario.WORK_REACH_BLOCKS + 1.0e-9,
                    "Broken block must be within configured worker reach");
                brokenBlocksObserved += progress - previousProgress;
            }
            previousProgress = progress;
        }

        assertTrue(guard > 0, "Scenario must finish without getting stuck");
        assertEquals(MineTuning.blocksPerSegment(), brokenBlocksObserved);
        assertEquals(MinePrefabNavigationScenario.Phase.COMPLETE, scenario.snapshot().phase());
    }

    @Test
    void allFourPlacementOrientationsRotateMineConnectorAndTunnelTogether() {
        MinePrefabNavigationScenario.Snapshot north = MinePrefabNavigationScenario.create(
            BuildingOrientation.NORTH
        ).snapshot();
        MineDirection authoredFacing = north.simulationDirection();

        for (BuildingOrientation orientation : BuildingOrientation.values()) {
            MinePrefabNavigationScenario scenario = MinePrefabNavigationScenario.create(orientation);
            MinePrefabNavigationScenario.Snapshot snapshot = scenario.snapshot();

            assertEquals(orientation, snapshot.orientation());
            assertEquals(orientation.rotate(authoredFacing), snapshot.simulationDirection(),
                "Tunnel direction must rotate with the placed prefab");
            assertFalse(snapshot.pathToConnector().isEmpty(),
                "Rotated workplace must still reach the rotated connector");
            assertNoPrefabOverlap(snapshot);
            assertMountainExtendsBeyondSegment(snapshot);

            scenario.runToCompletion();
            assertTrue(scenario.snapshot().segment().complete(),
                "MinerJob must complete for " + orientation);
        }
    }

    private static void assertMountainExtendsBeyondSegment(MinePrefabNavigationScenario.Snapshot snapshot) {
        MineSimulationWorld.Bounds mountain = snapshot.tunnelWorld().bounds();
        int segmentMinX = snapshot.segment().blocks().stream().mapToInt(BlockPosition::x).min().orElseThrow();
        int segmentMaxX = snapshot.segment().blocks().stream().mapToInt(BlockPosition::x).max().orElseThrow();
        int segmentMinY = snapshot.segment().blocks().stream().mapToInt(BlockPosition::y).min().orElseThrow();
        int segmentMaxY = snapshot.segment().blocks().stream().mapToInt(BlockPosition::y).max().orElseThrow();
        int segmentMinZ = snapshot.segment().blocks().stream().mapToInt(BlockPosition::z).min().orElseThrow();
        int segmentMaxZ = snapshot.segment().blocks().stream().mapToInt(BlockPosition::z).max().orElseThrow();

        assertTrue(mountain.minY() < segmentMinY);
        assertTrue(mountain.maxY() > segmentMaxY);
        assertTrue(mountain.width() > segmentMaxX - segmentMinX + 1
                || mountain.depth() > segmentMaxZ - segmentMinZ + 1,
            "Mountain must be wider/deeper than the excavated 4x4 reference segment");
        long mountainCells = (long) mountain.width() * mountain.height() * mountain.depth();
        assertTrue(mountainCells > MineTuning.blocksPerSegment(),
            "Mountain volume must contain substantially more rock than the active segment");
    }

    private static void assertNoPrefabOverlap(MinePrefabNavigationScenario.Snapshot snapshot) {
        long overlap = snapshot.segment().blocks().stream()
            .filter(position -> snapshot.model().cellAt(position) != null)
            .count();
        assertEquals(0, overlap,
            "Initial 4x4 reference tunnel rock must never occupy a real Mine_01 prefab block");
    }
}
