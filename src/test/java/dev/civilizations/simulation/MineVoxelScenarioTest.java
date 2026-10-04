package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineSupportFrame;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MineVoxelScenarioTest {

    private static final BlockPosition START = new BlockPosition(10, 20, 30);
    private static final TestVoxelWorld.Bounds STRAIGHT_BOUNDS =
        new TestVoxelWorld.Bounds(8, 15, 19, 24, 21, 32);
    private static final TestVoxelWorld.Bounds TURN_BOUNDS =
        new TestVoxelWorld.Bounds(5, 19, 19, 24, 21, 32);

    @Test
    void northSegmentCanBeValidatedFaceByFaceInAThreeDimensionalWorld() {
        MineSegment segment = segment(MineDirection.NORTH);
        TestVoxelWorld actual = new TestVoxelWorld();
        TestVoxelWorld expected = new TestVoxelWorld();

        excavate(actual, segment, 0, 16);
        fill(expected, 10, 13, 20, 23, 30, 30, TestVoxelWorld.Cell.AIR);
        assertWorld(expected, actual, STRAIGHT_BOUNDS, 20, "first 4x4 face");

        excavate(actual, segment, 16, 64);
        placeSupport(actual, segment, 4);
        fill(expected, 10, 13, 20, 23, 27, 30, TestVoxelWorld.Cell.AIR);
        placeNorthGoldenSupport(expected, 27);
        assertWorld(expected, actual, STRAIGHT_BOUNDS, 20, "first four tunnel depths");

        excavate(actual, segment, 64, 128);
        placeSupport(actual, segment, 8);
        fill(expected, 10, 13, 20, 23, 23, 30, TestVoxelWorld.Cell.AIR);
        placeNorthGoldenSupport(expected, 27);
        placeNorthGoldenSupport(expected, 23);
        assertWorld(expected, actual, STRAIGHT_BOUNDS, 20, "complete supported segment");
    }

    @Test
    void rightTurnProducesTheExpectedThreeDimensionalWorldDiff() {
        MineSegment parent = segment(MineDirection.NORTH);
        MineSegment child = child(parent, MineDirection.EAST);
        TestVoxelWorld actual = new TestVoxelWorld();
        TestVoxelWorld expected = new TestVoxelWorld();

        excavate(actual, parent, 0, parent.blocks().size());
        excavate(actual, child, 0, child.blocks().size());

        fill(expected, 10, 13, 20, 23, 23, 30, TestVoxelWorld.Cell.AIR);
        fill(expected, 10, 17, 20, 23, 23, 26, TestVoxelWorld.Cell.AIR);
        assertWorld(expected, actual, TURN_BOUNDS, 20, "north -> east turn");
    }

    @Test
    void leftTurnProducesTheExpectedThreeDimensionalWorldDiff() {
        MineSegment parent = segment(MineDirection.NORTH);
        MineSegment child = child(parent, MineDirection.WEST);
        TestVoxelWorld actual = new TestVoxelWorld();
        TestVoxelWorld expected = new TestVoxelWorld();

        excavate(actual, parent, 0, parent.blocks().size());
        excavate(actual, child, 0, child.blocks().size());

        fill(expected, 10, 13, 20, 23, 23, 30, TestVoxelWorld.Cell.AIR);
        fill(expected, 6, 13, 20, 23, 23, 26, TestVoxelWorld.Cell.AIR);
        assertWorld(expected, actual, TURN_BOUNDS, 20, "north -> west turn");
    }

    private static void excavate(
        TestVoxelWorld world,
        MineSegment segment,
        int fromInclusive,
        int toExclusive
    ) {
        for (int index = fromInclusive; index < toExclusive; index++) {
            world.set(segment.blockAtIndex(index), TestVoxelWorld.Cell.AIR);
        }
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

    private static void assertWorld(
        TestVoxelWorld expected,
        TestVoxelWorld actual,
        TestVoxelWorld.Bounds bounds,
        int diagnosticY,
        String stage
    ) {
        Map<BlockPosition, TestVoxelWorld.Cell> expectedSnapshot = expected.snapshot(bounds);
        Map<BlockPosition, TestVoxelWorld.Cell> actualSnapshot = actual.snapshot(bounds);
        assertEquals(
            expectedSnapshot,
            actualSnapshot,
            () -> stage
                + "\nEXPECTED y=" + diagnosticY + "\n" + expected.renderTopDown(bounds, diagnosticY)
                + "ACTUAL y=" + diagnosticY + "\n" + actual.renderTopDown(bounds, diagnosticY)
        );
    }

    private static MineSegment child(MineSegment parent, MineDirection direction) {
        return MineSegment.reserved(
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            parent.mineId(),
            parent.id(),
            parent.nextStart(direction),
            direction
        );
    }

    private static MineSegment segment(MineDirection direction) {
        return MineSegment.reserved(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            null,
            START,
            direction
        );
    }
}
