package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinerNavigationStagingContractTest {

    @Test
    void connectorGateComesBeforeWorkExecutionAndResetsAfterManualMove() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );

        String core = Files.readString(Path.of("src/main/java/dev/civilizations/core/MinerWorkController.java"));
        int connectorGate = core.indexOf("State.ENTERING_CONNECTOR");
        int workExecution = core.indexOf("execute(worker, s, dt, engine, observed)");
        assertTrue(connectorGate >= 0 && workExecution > connectorGate);
        assertTrue(source.indexOf("RuntimeMinePlan minePlan = ensureRuntimePlan") < source.indexOf("controller.tick(workerKey"));
        assertTrue(source.contains("controller.interrupt(workerKey)"));
        assertTrue(core.contains("Worker s = new Worker(); s.state = State.INTERRUPTED"));
        assertTrue(source.contains("restorePosition = false;"));
    }
}
