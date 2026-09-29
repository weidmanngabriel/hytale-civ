package dev.civilizations.simulation.viewer;

import dev.civilizations.core.Profession;
import dev.civilizations.simulation.SimulationRuntime;
import dev.civilizations.simulation.SimulationScenarios;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationViewerAppTest {

    @Test
    void demoScenarioExposesAllCurrentVerticalSlices() {
        SimulationRuntime runtime = SimulationScenarios.DEMO_SETTLEMENT.createRuntime();
        SimulationRuntime.WorldSnapshot snapshot = runtime.worldSnapshot();

        assertEquals(3, snapshot.residents().size());
        assertEquals(3, snapshot.trees().size());
        assertEquals(2, snapshot.constructionSites().size());
        assertEquals(1, snapshot.farmFields().size());

        assertTrue(snapshot.residents().stream()
            .anyMatch(resident -> resident.profession() == Profession.WOODCUTTER));
        assertTrue(snapshot.residents().stream()
            .anyMatch(resident -> resident.profession() == Profession.CONSTRUCTION_WORKER));
        assertTrue(snapshot.residents().stream()
            .anyMatch(resident -> resident.profession() == Profession.FARMER));
    }

    @Test
    void viewerSnapshotsRemainStableWhileRuntimeAdvances() {
        SimulationRuntime runtime = SimulationScenarios.DEMO_SETTLEMENT.createRuntime();
        SimulationRuntime.WorldSnapshot before = runtime.worldSnapshot();

        runtime.runForSeconds(1.0);

        SimulationRuntime.WorldSnapshot after = runtime.worldSnapshot();

        assertEquals(0, before.tickCount());
        assertEquals(20, after.tickCount());
        assertNotEquals(
            before.residents().getFirst().position(),
            after.residents().getFirst().position()
        );
    }
}
