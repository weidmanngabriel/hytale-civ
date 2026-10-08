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
    void overlappingBridgeWorkIsNotCreatedWhileExistingSpanIsBeingBuilt() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );

        assertTrue(source.contains("boolean bridgeInProgress"));
        assertTrue(source.contains("existing.task.tunnelId().equals(front.tunnelId)"));
        assertTrue(source.contains("Multiple concurrent bridge spans can overlap spatially"));
        assertTrue(source.contains("existing.task.type() == MineInfrastructureTask.Type.BUILD_BRIDGE"));
        assertTrue(source.contains("if (bridgeInProgress) continue;"));
    }

    @Test
    void bridgeIsNotCreatedInsideUnexcavatedStone() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );
        assertTrue(source.contains("BlockType currentWalkCell = loadedBlockType("));
        assertTrue(source.contains("currentWalkCell == null || !isEmpty(currentWalkCell)"));
        assertTrue(source.contains("if (!floorMissing(world, front.slices.get(start)))"));
    }

    @Test
    void alreadyRestoredBridgeFloorCompletesInsteadOfAbandoning() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );
        assertTrue(source.contains("bridgeDeckComplete(world, infrastructure)"));
        assertTrue(source.contains("BRIDGE_DECK_ALREADY_COMPLETE"));
        assertTrue(source.contains("if (floor == null || isEmpty(floor)) return false;"));
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
