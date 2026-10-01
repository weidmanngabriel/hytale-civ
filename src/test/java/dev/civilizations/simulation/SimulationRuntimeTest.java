package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.FarmBuilding;
import dev.civilizations.core.WorldPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationRuntimeTest {

    @Test
    void idleBuildersSearchOncePerSecondInsteadOfEveryTick() {
        SimulationRuntime runtime = new SimulationRuntime();
        for (int index = 0; index < 100; index++) {
            runtime.addConstructionWorker(
                "builder-" + index,
                new WorldPosition(0.0, 0.0, 0.0)
            );
        }

        runtime.runForSeconds(60.0);

        SimulationMetrics.Snapshot metrics = runtime.metrics();
        assertEquals(1_200, metrics.ticks());
        assertEquals(6_000, metrics.decisions());
        assertEquals(100, metrics.immediateDecisions());
        assertEquals(5_900, metrics.retryDecisions());
        assertEquals(6_000, metrics.constructionSearches());
        assertEquals(6_000, metrics.failedPlans());
        assertEquals(0, metrics.movementRequests());
    }

    @Test
    void newConstructionSiteWakesWaitingBuilderImmediately() {
        SimulationRuntime runtime = new SimulationRuntime();
        runtime.addConstructionWorker(
            "builder-1",
            new WorldPosition(0.0, 0.0, 0.0)
        );
        runtime.runForSeconds(0.5);

        assertEquals(1, runtime.metrics().constructionSearches());

        runtime.addConstructionSite(
            "site-1",
            new WorldPosition(0.0, 0.0, 0.0),
            2
        );
        runtime.tick();

        assertEquals(2, runtime.metrics().constructionSearches());
        assertEquals(2, runtime.metrics().immediateDecisions());

        runtime.runForSeconds(2.2);

        assertTrue(runtime.constructionCompleted("site-1"));
        assertEquals(1, runtime.metrics().constructionsCompleted());
    }

    @Test
    void woodcutterCompletesAFullHeadlessWorkCycle() {
        SimulationRuntime runtime = new SimulationRuntime();
        BlockPosition tree = new BlockPosition(2, 0, 0);
        runtime.addTree(tree);
        runtime.addWoodcutter(
            "woodcutter-1",
            new WorldPosition(0.0, 0.0, 0.0)
        );

        runtime.runForSeconds(6.0);

        assertFalse(runtime.treeExists(tree));
        assertEquals(1, runtime.metrics().treesFelled());
        assertEquals(2, runtime.metrics().treeSearches());
        assertEquals(1, runtime.metrics().movementRequests());
    }

    @Test
    void farmerWithoutFieldUsesBoundedSearchCadence() {
        SimulationRuntime runtime = new SimulationRuntime();
        FarmBuilding farm = new FarmBuilding(
            "farm-1",
            new BlockPosition(0, 0, 0),
            new BlockPosition(0, 0, 0)
        );
        runtime.addFarmer(
            "farmer-1",
            new WorldPosition(0.5, 0.0, 0.5),
            farm
        );

        runtime.runForSeconds(60.0);

        SimulationMetrics.Snapshot metrics = runtime.metrics();
        assertEquals(60, metrics.fieldSearches());
        assertEquals(60, metrics.failedPlans());
        assertEquals(0, metrics.farmOutputsStored());
    }

    @Test
    void farmerRunsProductionWithoutSearchingForFieldEveryTick() {
        SimulationRuntime runtime = new SimulationRuntime();
        FarmBuilding farm = new FarmBuilding(
            "farm-1",
            new BlockPosition(0, 0, 0),
            new BlockPosition(0, 0, 0)
        );
        WorldPosition field = new WorldPosition(0.5, 0.0, 0.5);
        runtime.addFarmField(farm.id(), field);
        runtime.addFarmer("farmer-1", field, farm);

        runtime.runForSeconds(6.0);

        SimulationMetrics.Snapshot metrics = runtime.metrics();
        assertEquals(1, metrics.fieldSearches());
        assertEquals(1, metrics.farmOutputsStored());
        assertEquals(0, metrics.failedPlans());
    }

    @Test
    void manualMovePausesAndThenResumesAutonomousWork() {
        SimulationRuntime runtime = new SimulationRuntime();
        BlockPosition tree = new BlockPosition(8, 0, 0);
        runtime.addTree(tree);
        runtime.addWoodcutter(
            "woodcutter-1",
            new WorldPosition(0.0, 0.0, 0.0)
        );

        runtime.tick();
        runtime.runForSeconds(0.5);
        assertEquals(
            "WALKING_TO_TREE",
            runtime.residentSnapshot("woodcutter-1").autonomousState()
        );

        runtime.orderManualMove(
            "woodcutter-1",
            new WorldPosition(0.0, 0.0, 4.0)
        );
        runtime.tick();

        SimulationRuntime.ResidentSnapshot duringOrder =
            runtime.residentSnapshot("woodcutter-1");
        assertTrue(duringOrder.manualMovementActive());
        assertEquals("MANUAL_MOVE", duringOrder.state());
        assertEquals("WALKING_TO_TREE", duringOrder.autonomousState());

        runtime.runForSeconds(1.5);
        assertFalse(runtime.residentSnapshot("woodcutter-1").manualMovementActive());
        assertEquals(
            "WALKING_TO_TREE",
            runtime.residentSnapshot("woodcutter-1").autonomousState()
        );

        runtime.runForSeconds(7.0);

        assertFalse(runtime.treeExists(tree));
        assertEquals(1, runtime.metrics().treesFelled());
    }
}
