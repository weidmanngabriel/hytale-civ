package dev.civilizations.simulation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationScenariosTest {

    @Test
    void builtInScenariosHaveUniqueIdsAndStartHeadless() {
        Set<String> ids = new HashSet<>();

        for (SimulationScenario scenario : SimulationScenarios.all()) {
            assertTrue(ids.add(scenario.id()), () -> "duplicate scenario id: " + scenario.id());

            SimulationRuntime runtime = scenario.createRuntime();
            assertEquals(0, runtime.tickCount());
            assertFalse(runtime.worldSnapshot().residents().isEmpty());
        }
    }

    @Test
    void scenarioCreatesFreshWorldStateForEveryRun() {
        SimulationRuntime first = SimulationScenarios.WOODCUTTER_BASIC.createRuntime();
        SimulationRuntime second = SimulationScenarios.WOODCUTTER_BASIC.createRuntime();

        assertNotSame(first, second);
        assertEquals(first.worldSnapshot(), second.worldSnapshot());

        first.runForSeconds(4.0);

        assertEquals(0, second.tickCount());
        assertEquals(3, second.worldSnapshot().trees().size());
        assertTrue(first.metrics().treesFelled() > 0);
    }

    @Test
    void waitingWorkersScenarioExercisesBoundedRetriesWithoutTargets() {
        SimulationRuntime runtime = SimulationScenarios.WAITING_WORKERS.createRuntime();

        runtime.runForSeconds(2.0);

        SimulationMetrics.Snapshot metrics = runtime.metrics();
        assertEquals(2, metrics.treeSearches());
        assertEquals(2, metrics.constructionSearches());
        assertEquals(2, metrics.fieldSearches());
        assertEquals(6, metrics.failedPlans());
        assertEquals(1, metrics.movementRequests());
    }

    @Test
    void farmerScenarioProducesOutput() {
        SimulationRuntime runtime = SimulationScenarios.FARMER_BASIC.createRuntime();

        runtime.runForSeconds(10.0);

        assertTrue(runtime.metrics().farmOutputsStored() > 0);
    }

    @Test
    void builderScenarioCompletesItsConstructionSite() {
        SimulationRuntime runtime = SimulationScenarios.BUILDER_BASIC.createRuntime();

        runtime.runForSeconds(10.0);

        assertTrue(runtime.constructionCompleted("house-site"));
    }
}
