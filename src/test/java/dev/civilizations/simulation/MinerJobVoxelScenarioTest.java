package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineSupportFrame;
import dev.civilizations.core.MinerJob;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinerJobVoxelScenarioTest {

    private static final TestVoxelWorld.Bounds BOUNDS =
        new TestVoxelWorld.Bounds(8, 15, 19, 24, 21, 32);

    @Test
    void minerJobDrivesOneCompleteSupportedSegmentInTheVoxelWorld() {
        MinerJob job = new MinerJob(segment());
        TestVoxelWorld actual = new TestVoxelWorld();
        TestVoxelWorld expected = new TestVoxelWorld();
        int movementCompletions = 0;
        int blockBreaks = 0;
        int supportPlacements = 0;

        while (job.state() != MinerJob.WorkState.COMPLETE) {
            switch (job.intent()) {
                case MinerJob.MoveToFaceIntent ignored -> {
                    movementCompletions++;
                    assertTrue(job.movementArrived());
                }
                case MinerJob.BreakBlockIntent mine -> {
                    actual.set(mine.block(), TestVoxelWorld.Cell.AIR);
                    blockBreaks++;
                    assertTrue(job.blockBroken());
                }
                case MinerJob.PlaceSupportIntent support -> {
                    placeSupport(actual, support.segment(), support.depth());
                    supportPlacements++;
                    assertTrue(job.supportPlaced());
                }
                case MinerJob.SegmentCompleteIntent ignored -> throw new AssertionError(
                    "completion intent must only appear after the loop condition"
                );
            }
        }

        fill(expected, 10, 13, 20, 23, 23, 30, TestVoxelWorld.Cell.AIR);
        placeNorthGoldenSupport(expected, 27);
        placeNorthGoldenSupport(expected, 23);

        assertEquals(8, movementCompletions);
        assertEquals(128, blockBreaks);
        assertEquals(2, supportPlacements);
        assertWorld(expected, actual);
    }

    private static void placeSupport(TestVoxelWorld world, MineSegment segment, int depth) {
        for (MineSupportFrame.Cell cell : MineSupportFrame.cells(segment, depth)) {
            world.set(
                cell.position(),
                cell.part() == MineSupportFrame.Part.POST
                    ? TestVoxelWorld.Cell.SUPPORT_POST
                    : TestVoxelWorld.Cell.SUPPORT_BEAM
            );
        }
    }

    private static void placeNorthGoldenSupport(TestVoxelWorld world, int z) {
        for (int y = 20; y <= 22; y++) {
            world.set(new BlockPosition(10, y, z), TestVoxelWorld.Cell.SUPPORT_POST);
            world.set(new BlockPosition(13, y, z), TestVoxelWorld.Cell.SUPPORT_POST);
        }
        for (int x = 10; x <= 13; x++) {
            world.set(new BlockPosition(x, 23, z), TestVoxelWorld.Cell.SUPPORT_BEAM);
        }
    }

    private static void fill(
        TestVoxelWorld world,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        TestVoxelWorld.Cell cell
    ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.set(new BlockPosition(x, y, z), cell);
                }
            }
        }
    }

    private static void assertWorld(TestVoxelWorld expected, TestVoxelWorld actual) {
        Map<BlockPosition, TestVoxelWorld.Cell> expectedSnapshot = expected.snapshot(BOUNDS);
        Map<BlockPosition, TestVoxelWorld.Cell> actualSnapshot = actual.snapshot(BOUNDS);
        assertEquals(
            expectedSnapshot,
            actualSnapshot,
            () -> "complete MinerJob world diff"
                + "\nEXPECTED y=20\n" + expected.renderTopDown(BOUNDS, 20)
                + "ACTUAL y=20\n" + actual.renderTopDown(BOUNDS, 20)
        );
    }

    private static MineSegment segment() {
        return MineSegment.reserved(
            UUID.fromString("00000000-0000-0000-0000-000000000201"),
            UUID.fromString("00000000-0000-0000-0000-000000000202"),
            null,
            new BlockPosition(10, 20, 30),
            MineDirection.NORTH
        );
    }
}
