package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinerWorkerDiagnosticsContractTest {

    @Test
    void minerLifecycleDiagnosticsCoverTaskStateIdleAndNativeNavigationTransitions() throws Exception {
        String work = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );
        String navigation = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerNavigationSystem.java")
        );

        assertTrue(work.contains("MineDecisionCategory.WORKER, \"TASK_SELECTED\""));
        assertTrue(work.contains("MineDecisionCategory.WORKER, \"TASK_STARTED\""));
        assertTrue(work.contains("MineDecisionCategory.WORKER, \"TASK_ENDED\""));
        assertTrue(work.contains("MineDecisionCategory.WORKER, \"STATE_CHANGED\""));
        assertTrue(work.contains("MineDecisionCategory.WORKER, \"MOVE_TARGET_CHANGED\""));
        assertTrue(work.contains("MineDecisionCategory.WORKER, \"NO_AVAILABLE_TASK\""));
        assertTrue(work.contains("next == runtime.debugState"));
        assertTrue(work.contains("lastNoTaskFingerprint"));

        assertTrue(navigation.contains("controller.getNavState()"));
        assertTrue(navigation.contains("MineDecisionCategory.NAVIGATION, \"REPATH_REQUESTED\""));
        assertTrue(navigation.contains("MineDecisionCategory.NAVIGATION, \"NAVIGATION_FAILED\""));
        assertTrue(navigation.contains("MineDecisionCategory.NAVIGATION, \"NAVIGATION_RECOVERY\""));
        assertTrue(navigation.contains("controller.setForceRecomputePath(true)"));
    }
}
