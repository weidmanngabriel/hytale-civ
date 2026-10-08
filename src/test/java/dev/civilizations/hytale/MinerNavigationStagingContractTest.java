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

        int connectorGate = source.indexOf("if (!runtime.reachedConnector)");
        int planResolution = source.indexOf("RuntimeMinePlan minePlan = ensureRuntimePlan");
        int workExecution = source.indexOf("if (navigationFailures.consumeIfMatches");

        assertTrue(connectorGate >= 0, "miner must gate autonomous work on the tunnel connector");
        assertTrue(planResolution >= 0 && planResolution < connectorGate,
            "restart recovery must resolve mine geometry before the connector gate");
        assertTrue(workExecution > connectorGate,
            "task execution must start only after the connector gate");
        assertTrue(source.contains("reachedConnector = false;"),
            "manual interruption/reset must require the connector again");
    }
}
