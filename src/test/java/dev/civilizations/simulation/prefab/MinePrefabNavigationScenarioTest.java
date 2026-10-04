package dev.civilizations.simulation.prefab;

import dev.civilizations.core.BuildingOrientation;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineTuning;
import dev.civilizations.simulation.MineSimulationWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinePrefabNavigationScenarioTest {

    @Test
    void realMineConnectsWorkplaceToTunnelAndExcavatesOneSegment() {
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

        assertEquals(10, initial.supportPrefab().cells().size(),
            "The real Mine_Support_01 prefab currently contains ten authored blocks");
        assertNoPrefabOverlap(initial);

        scenario.runToCompletion();
        MinePrefabNavigationScenario.Snapshot complete = scenario.snapshot();

        assertEquals(MinePrefabNavigationScenario.Phase.COMPLETE, complete.phase());
        assertEquals(MineTuning.blocksPerSegment(), complete.segment().nextBlockIndex());
        assertEquals(2, complete.segment().supportsPlaced());

        long air = complete.tunnelWorld().cells().values().stream()
            .filter(cell -> cell == MineSimulationWorld.Cell.AIR)
            .count();
        long supports = complete.tunnelWorld().cells().values().stream()
            .filter(cell -> cell == MineSimulationWorld.Cell.SUPPORT_POST
                || cell == MineSimulationWorld.Cell.SUPPORT_BEAM)
            .count();
        assertEquals(20, supports);
        assertEquals(MineTuning.blocksPerSegment() - supports, air);
        assertTrue(complete.segment().complete());
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

            scenario.runToCompletion();
            assertTrue(scenario.snapshot().segment().complete(),
                "MinerJob must complete for " + orientation);
        }
    }

    private static void assertNoPrefabOverlap(MinePrefabNavigationScenario.Snapshot snapshot) {
        long overlap = snapshot.segment().blocks().stream()
            .filter(position -> snapshot.model().cellAt(position) != null)
            .count();
        assertEquals(0, overlap,
            "Initial 4x4x8 tunnel rock must never occupy a real Mine_01 prefab block");
    }
}
