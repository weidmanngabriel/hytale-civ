package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MinerJob;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinerJobVoxelScenarioTest {

    @Test
    void minerJobDrivesOneCompleteSupportedSegmentInTheSameVoxelWorldUsedByTheViewer() {
        MineSegment segment = segment();
        MinerJob job = new MinerJob(segment);
        MineSimulationWorld actual = new MineSimulationWorld(segment);
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
                    actual.breakBlock(mine.block());
                    blockBreaks++;
                    assertTrue(job.blockBroken());
                }
                case MinerJob.PlaceSupportIntent support -> {
                    actual.placeSupport(support.segment(), support.depth());
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

        assertEquals(8, movementCompletions);
        assertEquals(128, blockBreaks);
        assertEquals(1, supportPlacements);
        assertWorld(expected, actual);
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

    private static void assertWorld(TestVoxelWorld expected, MineSimulationWorld actual) {
        MineSimulationWorld.Snapshot actualSnapshot = actual.snapshot();
        Map<BlockPosition, TestVoxelWorld.Cell> converted = new LinkedHashMap<>();
        actualSnapshot.cells().forEach((position, cell) -> converted.put(
            position,
            switch (cell) {
                case SOLID -> TestVoxelWorld.Cell.SOLID;
                case AIR -> TestVoxelWorld.Cell.AIR;
                case SUPPORT_POST -> TestVoxelWorld.Cell.SUPPORT_POST;
                case SUPPORT_BEAM -> TestVoxelWorld.Cell.SUPPORT_BEAM;
            }
        ));
        TestVoxelWorld.Bounds bounds = new TestVoxelWorld.Bounds(
            actualSnapshot.bounds().minX(),
            actualSnapshot.bounds().maxX(),
            actualSnapshot.bounds().minY(),
            actualSnapshot.bounds().maxY(),
            actualSnapshot.bounds().minZ(),
            actualSnapshot.bounds().maxZ()
        );
        assertEquals(expected.snapshot(bounds), Map.copyOf(converted));
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
