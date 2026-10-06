package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinerObstacleExecutionContractTest {

    @Test
    void navigationFailureIsHandedBackToMinerWorkLifecycle() throws Exception {
        String navigation = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerNavigationSystem.java")
        );
        String work = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );

        assertTrue(navigation.contains("navigationFailures.report(workerKey, moveTarget)"));
        assertTrue(navigation.contains("setForceRecomputePath(true)"));
        assertTrue(work.contains("consumeIfMatches(workerKey, runtime.navigationTarget)"));
        assertTrue(work.contains("FailureKind.NAVIGATION_UNREACHABLE"));
        assertTrue(work.contains("SKIPPED_UNREACHABLE"));
    }

    @Test
    void unsafeGapsAndLavaAbandonFrontsInsteadOfAutoAdvancing() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );

        assertTrue(source.contains("hasFluidInNavigationCorridor"));
        assertTrue(source.contains("ShaderType.Lava"));
        assertTrue(source.contains("LAVA_GAP"));
        assertTrue(source.contains("GAP_EXCEEDS_SAFE_BRIDGE_RANGE"));
        assertTrue(source.contains("FailureKind.HAZARDOUS_FLUID"));
        assertTrue(source.contains("FailureKind.UNSAFE_GEOMETRY"));
    }

    @Test
    void optionalInfrastructureUsesBoundedFallbackButMandatoryWorkDoesNot() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MineInfrastructurePlacementResolver.java")
        );

        assertTrue(source.contains("candidateSliceOrder"));
        assertTrue(source.contains("task.mandatory()"));
        assertTrue(source.contains("MineObstaclePolicy.fallbackSliceOrder"));
    }

    @Test
    void mandatoryInfrastructureResolutionFailureAbandonsItsFront() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );

        assertTrue(source.contains("MANDATORY_INFRASTRUCTURE_UNRESOLVABLE"));
        assertTrue(source.contains("FailureKind.MANDATORY_INFRASTRUCTURE_UNRESOLVABLE"));
    }
}
