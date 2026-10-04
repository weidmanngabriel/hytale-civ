package dev.civilizations.simulation.prefab;

import dev.civilizations.core.BlockPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmPrefabNavigationScenarioTest {

    @Test
    void realFarmIsGeometricallyReachableFromOutsideToWorkplaceAndStorage() {
        FarmPrefabNavigationScenario.Snapshot scenario = FarmPrefabNavigationScenario.create();

        assertFalse(scenario.pathToWorkplace().isEmpty());
        assertFalse(scenario.pathToStorage().isEmpty());
        assertEquals(scenario.outsideStart(), scenario.pathToWorkplace().getFirst());
        assertEquals(scenario.workplace(), scenario.pathToWorkplace().getLast());
        assertEquals(scenario.workplace(), scenario.pathToStorage().getFirst());
        assertEquals(scenario.storage(), scenario.pathToStorage().getLast());
        assertTrue(
            scenario.pathToWorkplace().stream()
                .anyMatch(position -> scenario.model().cellAt(position) == PrefabSimulationModel.Cell.DOOR),
            "outside-to-workplace route should pass through an authored door cell"
        );
        assertPathIsWalkable(scenario.model(), scenario.pathToWorkplace());
        assertPathIsWalkable(scenario.model(), scenario.pathToStorage());
    }

    @Test
    void selectedTargetsReallyBelongToTheirAuthoredMarkerVolumes() {
        FarmPrefabNavigationScenario.Snapshot scenario = FarmPrefabNavigationScenario.create();

        assertTrue(scenario.model().requireMarker("workplace_access").containsFeet(scenario.workplace()));
        assertTrue(scenario.model().requireMarker("output_storage").containsFeet(scenario.storage()));
        assertTrue(scenario.model().isWalkableFeet(scenario.workplace()));
        assertTrue(scenario.model().isWalkableFeet(scenario.storage()));
    }

    private static void assertPathIsWalkable(
        PrefabSimulationModel model,
        List<BlockPosition> path
    ) {
        for (BlockPosition position : path) {
            assertTrue(model.isWalkableFeet(position), () -> "unwalkable route cell " + position);
        }
        for (int i = 1; i < path.size(); i++) {
            BlockPosition previous = path.get(i - 1);
            BlockPosition current = path.get(i);
            int horizontal = Math.abs(previous.x() - current.x())
                + Math.abs(previous.z() - current.z());
            assertEquals(1, horizontal, () -> "route must move exactly one horizontal cell");
            assertTrue(Math.abs(previous.y() - current.y()) <= 1, () -> "route step too high");
        }
    }
}
