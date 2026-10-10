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
        assertTrue(work.contains("consumeIfMatches(worker, nativeTarget)"));
        assertTrue(work.contains("FailureKind.NAVIGATION_UNREACHABLE"));
        assertTrue(work.contains("MinerWorkController.Disposition.SKIP_OPTIONAL"));
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
    void bridgeDeckAndCrossbeamsDeduplicateVoxelTargets() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MineInfrastructurePlacementResolver.java")
        );
        int begin = source.indexOf("private static ResolvedTask resolveBridge(");
        int end = source.indexOf("private static List<Integer> candidateSliceOrder(", begin);
        String bridgeResolver = source.substring(begin, end);
        assertTrue(bridgeResolver.contains("java.util.LinkedHashMap<>"));
        assertTrue(bridgeResolver.contains("placements.putIfAbsent(position, new PlacementStep("));
        assertTrue(bridgeResolver.contains("List.copyOf(placements.values())"));
    }

    @Test
    void bridgeUsesClosedDeckAndExistingBeamSpacingWithShortOuterPosts() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MineInfrastructurePlacementResolver.java")
        );
        int start = source.indexOf("private static ResolvedTask resolveBridge(");
        int end = source.indexOf("private static List<Integer> candidateSliceOrder(", start);
        String bridge = source.substring(start, end);
        assertTrue(bridge.contains("Wood_Softwood_Planks"));
        assertTrue(bridge.contains("if (deck == null) return null;"));
        assertTrue(bridge.contains("int deckY = slice.floorCenter().y() - 1;"));
        assertTrue(bridge.contains("(index - task.startSliceIndex()) % 3 == 0"));
        assertTrue(bridge.contains("for (int lateral : new int[]{-2, 2})"));
        assertTrue(bridge.contains("for (int depth = 2; depth <= 4; depth++)"));
        assertTrue(bridge.contains("if (!isEmpty(world, position)) break;"));
        assertTrue(bridge.contains("position, FIR_BRANCH, RotationTuple.NONE"));
    }

    @Test
    void bridgesExtendAcrossLandingsAndReplaceOnlyDecoCollisions() throws Exception {
        String work = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );
        String resolver = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MineInfrastructurePlacementResolver.java")
        );
        assertTrue(work.contains("BRIDGE_LANDING_OVERLAP_SLICES = 3"));
        assertTrue(work.contains("int buildStart = Math.max(1, start - BRIDGE_LANDING_OVERLAP_SLICES)"));
        assertTrue(work.contains("landing + BRIDGE_LANDING_OVERLAP_SLICES"));
        assertTrue(work.contains("MineBlockPlacement.isDeco(world, placement.position())"));
        assertTrue(work.contains("BRIDGE_DECO_REPLACED"));
        assertTrue(work.contains("hasPendingMandatoryInfrastructure(plan, front)"));
        assertTrue(work.contains("!bridgeDeckComplete(world, infrastructure)"));
        assertTrue(resolver.contains("!MineBlockPlacement.isDeco(world, position)"));
    }

    @Test
    void alreadyRestoredBridgeFloorCompletesInsteadOfAbandoning() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );
        assertTrue(source.contains("bridgeDeckComplete(world, infrastructure)"));
        assertTrue(source.contains("BRIDGE_DECK_ALREADY_COMPLETE"));
        assertTrue(source.contains("if (floor == null || isEmpty(floor)) return false;"));
        assertTrue(source.contains("slice.navigationCoreBlocks()"));
        assertTrue(source.contains("if (!foundWalkColumn) return false;"));
    }

    @Test
    void nativeNavigationFailureCanGenerateOneBoundedStairRepair() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );
        assertTrue(source.contains("scheduleNearbyRecoveryStep(world, mine, plan, front)"));
        assertTrue(source.contains("transition.toSliceIndex() != destinationSlice"));
        assertTrue(source.contains("!sliceComplete(world, front.slices.get(transition.fromSliceIndex()))"));
        assertTrue(source.contains("minePlan.infrastructureTasks.containsKey(task.id())"));
        assertTrue(source.contains("NAVIGATION_STEP_REPAIR_CREATED"));
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
