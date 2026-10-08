package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.MineHeading;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinerWorkFrontExecutionTest {

    @Test
    void initialCenterlineStartsOutsideConnectorEdge() {
        BuildingBounds connector = new BuildingBounds(10, 20, 30, 14, 24, 36);

        assertEquals(new BlockPosition(14, 20, 33),
            MinerWorkSystem.initialCenterlineOrigin(connector, MineHeading.EAST));
        assertEquals(new BlockPosition(9, 20, 33),
            MinerWorkSystem.initialCenterlineOrigin(connector, MineHeading.WEST));
        assertEquals(new BlockPosition(12, 20, 36),
            MinerWorkSystem.initialCenterlineOrigin(connector, MineHeading.SOUTH));
        assertEquals(new BlockPosition(12, 20, 29),
            MinerWorkSystem.initialCenterlineOrigin(connector, MineHeading.NORTH));
    }

    @Test
    void runtimePlansMultipleTunnelFrontsAndDelegatesSelectionToUnifiedCoreScheduler() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );

        assertTrue(source.contains("RUNTIME_PLANNING_TUNNEL_BUDGET"));
        assertTrue(source.contains("planned.tunnels()"));
        assertTrue(source.contains("MineNormalTaskSelector.selectWithAging"));
        assertTrue(source.contains("MineNormalTaskSelector.Kind.TUNNEL_FRONT"));
        assertTrue(source.contains("MineNormalTaskSelector.Kind.INFRASTRUCTURE"));
        assertTrue(source.contains("putRuntimeGeometries"));
        assertFalse(source.contains("V1_PLANNING_TUNNEL_BUDGET = 1"));
        assertFalse(source.contains("planned.mainTunnel().geometry()"));
    }

    @Test
    void passabilityPlanningRunsBeforeAlreadyEmptyCaveSlicesAdvance() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );

        int bridgeRefresh = source.indexOf("refreshBridgeTasks(world, mine, minePlan);");
        int autoAdvance = source.indexOf("advanceAlreadyExcavatedSlices(world, mine, minePlan);");

        assertTrue(bridgeRefresh >= 0);
        assertTrue(autoAdvance > bridgeRefresh,
            "bridge detection must happen before naturally empty slices can auto-advance");
        assertTrue(source.contains("!hasPendingMandatoryInfrastructure(minePlan, plan)"));
    }

    @Test
    void completedSliceReopensFrontForFreshTaskSelection() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );

        assertTrue(source.contains("MineWorkFront.State.OPEN"));
        assertTrue(source.contains("frontCoordinator.releaseFront(plan.frontId)"));
        assertTrue(source.contains("runtime.clearWorkAssignment()"));
    }
}
