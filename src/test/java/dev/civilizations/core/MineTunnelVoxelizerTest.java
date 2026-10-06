package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineTunnelVoxelizerTest {

    private static final BlockPosition ORIGIN = new BlockPosition(0, 80, 0);

    @Test
    void sameLayerTwoPlanProducesExactlyTheSameVoxelGeometry() {
        MineTunnelPath path = MinePathPlanner.plan(
            MineTunnel.Kind.MAIN, ORIGIN, MineHeading.NORTH_EAST, 180, 12345L
        );

        assertEquals(MineTunnelVoxelizer.voxelize(path), MineTunnelVoxelizer.voxelize(path));
    }

    @Test
    void navigationCoreIsAlwaysExcavatedAndSixNeighborConnected() {
        for (long seed = 0; seed < 100; seed++) {
            MineTunnelPath path = MinePathPlanner.plan(
                MineTunnel.Kind.MAIN, ORIGIN, MineHeading.NORTH, 220, seed
            );
            MineTunnelGeometry geometry = MineTunnelVoxelizer.voxelize(path);

            assertTrue(geometry.excavationBlocks().containsAll(geometry.navigationCoreBlocks()));
            assertConnected(geometry.navigationCoreBlocks());
        }
    }

    @Test
    void branchGeometryKeepsPlannedThreeToFiveBlockDimensions() {
        MineTunnelPath path = MinePathPlanner.plan(
            MineTunnel.Kind.BRANCH, ORIGIN, MineHeading.EAST, 160, 9988L
        );
        MineTunnelGeometry geometry = MineTunnelVoxelizer.voxelize(path);

        for (MineTunnelGeometry.Slice slice : geometry.slices()) {
            assertTrue(slice.widthBlocks() >= 3 && slice.widthBlocks() <= 5);
            assertTrue(slice.heightBlocks() >= 3 && slice.heightBlocks() <= 5);
            assertTrue(slice.excavationBlocks().containsAll(slice.navigationCoreBlocks()));
        }
    }

    @Test
    void mainGeometryKeepsPlannedSixToEightBlockDimensions() {
        MineTunnelPath path = MinePathPlanner.plan(
            MineTunnel.Kind.MAIN, ORIGIN, MineHeading.SOUTH_EAST, 180, 778899L
        );
        MineTunnelGeometry geometry = MineTunnelVoxelizer.voxelize(path);

        for (MineTunnelGeometry.Slice slice : geometry.slices()) {
            assertTrue(slice.widthBlocks() >= 6 && slice.widthBlocks() <= 8);
            assertTrue(slice.heightBlocks() >= 6 && slice.heightBlocks() <= 8);
        }
    }

    @Test
    void naturalizationAddsRealWallOrCeilingVolumeWithoutChangingTheSafeCore() {
        MineTunnelPath path = MinePathPlanner.plan(
            MineTunnel.Kind.MAIN, ORIGIN, MineHeading.NORTH, 120, 77L
        );
        MineTunnelGeometry geometry = MineTunnelVoxelizer.voxelize(path);

        int structuralMaximum = geometry.slices().stream()
            .mapToInt(slice -> slice.widthBlocks() * slice.heightBlocks())
            .sum();

        assertTrue(geometry.excavationBlocks().size() > geometry.navigationCoreBlocks().size());
        assertTrue(
            geometry.slices().stream().anyMatch(slice ->
                slice.excavationBlocks().size() > slice.widthBlocks() * slice.heightBlocks()),
            "Expected deterministic fixture to contain at least one organic connected cutout."
        );
        assertTrue(structuralMaximum > 0);
    }

    @Test
    void roundedVerticalChangesBecomeExplicitSingleBlockStepTransitions() {
        MineTunnelPath path = MinePathPlanner.plan(
            MineTunnel.Kind.MAIN, ORIGIN, MineHeading.NORTH, 300, 42L
        );
        MineTunnelGeometry geometry = MineTunnelVoxelizer.voxelize(path);

        assertFalse(geometry.stepTransitions().isEmpty());
        for (MineTunnelGeometry.StepTransition step : geometry.stepTransitions()) {
            assertEquals(1, Math.abs(step.toFloorCenter().y() - step.fromFloorCenter().y()));
            assertEquals(step.fromSliceIndex() + 1, step.toSliceIndex());
        }
    }

    @Test
    void consecutiveSlicesNeverLoseTheGuaranteedCoreAcrossCurvesAndHeightChanges() {
        MineTunnelPath path = MinePathPlanner.plan(
            MineTunnel.Kind.MAIN, ORIGIN, MineHeading.NORTH_WEST, 500, 20261006L
        );
        MineTunnelGeometry geometry = MineTunnelVoxelizer.voxelize(path);

        for (int i = 1; i < geometry.slices().size(); i++) {
            Set<BlockPosition> pair = new HashSet<>(geometry.slices().get(i - 1).navigationCoreBlocks());
            pair.addAll(geometry.slices().get(i).navigationCoreBlocks());
            assertConnected(pair);
        }
    }

    private static void assertConnected(Set<BlockPosition> blocks) {
        assertFalse(blocks.isEmpty());
        Set<BlockPosition> remaining = new HashSet<>(blocks);
        ArrayDeque<BlockPosition> queue = new ArrayDeque<>();
        BlockPosition first = remaining.iterator().next();
        remaining.remove(first);
        queue.add(first);

        while (!queue.isEmpty()) {
            BlockPosition current = queue.removeFirst();
            for (BlockPosition neighbor : neighbors(current)) {
                if (remaining.remove(neighbor)) queue.addLast(neighbor);
            }
        }
        assertTrue(remaining.isEmpty(), "Navigation core must form one six-neighbor-connected volume.");
    }

    private static List<BlockPosition> neighbors(BlockPosition position) {
        return List.of(
            new BlockPosition(position.x() + 1, position.y(), position.z()),
            new BlockPosition(position.x() - 1, position.y(), position.z()),
            new BlockPosition(position.x(), position.y() + 1, position.z()),
            new BlockPosition(position.x(), position.y() - 1, position.z()),
            new BlockPosition(position.x(), position.y(), position.z() + 1),
            new BlockPosition(position.x(), position.y(), position.z() - 1)
        );
    }
}
