package dev.civilizations.simulation;

import dev.civilizations.core.MineTuning;
import dev.civilizations.scenario.MinerBasicScenario;
import dev.civilizations.scenario.WoodcutterBasicScenario;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void sharedWoodcutterScenarioFellsTreeAndKeepsWorking() {
        SimulationRuntime first = SimulationScenarios.WOODCUTTER_BASIC.createRuntime();
        SimulationRuntime second = SimulationScenarios.WOODCUTTER_BASIC.createRuntime();

        assertNotSame(first, second);
        assertEquals(first.worldSnapshot(), second.worldSnapshot());
        assertEquals(WoodcutterBasicScenario.ID, SimulationScenarios.WOODCUTTER_BASIC.id());
        assertEquals(
            WoodcutterBasicScenario.TREE_ANCHORS.size(),
            first.worldSnapshot().trees().size()
        );

        first.runForSeconds(6.0);

        assertEquals(0, second.tickCount());
        assertEquals(
            WoodcutterBasicScenario.TREE_ANCHORS.size(),
            second.worldSnapshot().trees().size()
        );
        assertTrue(first.metrics().treesFelled() > 0);
        assertTrue(
            first.worldSnapshot().trees().size() < WoodcutterBasicScenario.TREE_ANCHORS.size(),
            "shared woodcutter fixture should lose at least one tree"
        );
        assertTrue(
            first.metrics().treeSearches() > 1,
            "woodcutter should search again after completing its first tree"
        );
    }

    @Test
    void sharedMinerScenarioCanBeSteppedToACompleteSupportedSegment() {
        SimulationRuntime runtime = SimulationScenarios.MINER_BASIC.createRuntime();
        SimulationRuntime.WorldSnapshot start = runtime.worldSnapshot();

        assertEquals(MinerBasicScenario.ID, SimulationScenarios.MINER_BASIC.id());
        assertNotNull(start.mine());
        assertEquals(0, start.mine().nextBlockIndex());
        assertEquals(0, start.mine().segment().supportsPlaced());

        runtime.runForSeconds(65.0);

        SimulationRuntime.MineSnapshot complete = runtime.worldSnapshot().mine();
        assertNotNull(complete);
        assertEquals(MineTuning.blocksPerSegment(), complete.nextBlockIndex());
        assertEquals(2, complete.segment().supportsPlaced());
        assertEquals("COMPLETE", complete.state());
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
