package dev.civilizations.simulation.prefab;

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
        assertFalse(initial.directionAuthored(),
            "The navigation lab must not pretend the derived tunnel direction came from prefab orientation");

        long initialOverlaps = initial.segment().blocks().stream()
            .filter(position -> initial.model().cellAt(position) != null)
            .count();
        assertEquals(0, initialOverlaps,
            "The initial 4x4x8 tunnel rock must not overlap any authored Mine_01 block");

        assertEquals(10, initial.supportPrefab().cells().size(),
            "The real Mine_Support_01 prefab currently contains ten authored blocks");

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
}
