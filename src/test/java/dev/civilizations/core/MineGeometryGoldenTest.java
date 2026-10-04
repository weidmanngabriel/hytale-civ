package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Independent golden checks for mine geometry.
 *
 * <p>The expected coordinates are deliberately expressed as explicit world-axis boxes instead
 * of calling the production geometry helpers again. These tests are the small reviewable oracle
 * for the more complex voxel scenarios.</p>
 */
final class MineGeometryGoldenTest {

    private static final BlockPosition START = new BlockPosition(10, 20, 30);

    @Test
    void straightSegmentsIncludeTheFixedFourBlockJunctionInAllDirections() {
        assertEquals(
            box(10, 13, 20, 23, 19, 30),
            new HashSet<>(segment(MineDirection.NORTH).blocks())
        );
        assertEquals(
            box(10, 21, 20, 23, 30, 33),
            new HashSet<>(segment(MineDirection.EAST).blocks())
        );
        assertEquals(
            box(7, 10, 20, 23, 30, 41),
            new HashSet<>(segment(MineDirection.SOUTH).blocks())
        );
        assertEquals(
            box(-1, 10, 20, 23, 27, 30),
            new HashSet<>(segment(MineDirection.WEST).blocks())
        );
    }

    @Test
    void northRightTurnStartsOutsideTheReservedJunction() {
        MineSegment parent = segment(MineDirection.NORTH);
        assertEquals(new BlockPosition(14, 20, 19), parent.nextStart(MineDirection.EAST));

        MineSegment child = child(parent, MineDirection.EAST);
        Set<BlockPosition> actualFloor = floor(parent);
        actualFloor.addAll(floor(child));

        Set<BlockPosition> expectedFloor = box(10, 13, 20, 20, 19, 30);
        expectedFloor.addAll(box(14, 25, 20, 20, 19, 22));
        assertEquals(expectedFloor, actualFloor);
    }

    @Test
    void northLeftTurnStartsOutsideTheReservedJunction() {
        MineSegment parent = segment(MineDirection.NORTH);
        assertEquals(new BlockPosition(9, 20, 22), parent.nextStart(MineDirection.WEST));

        MineSegment child = child(parent, MineDirection.WEST);
        Set<BlockPosition> actualFloor = floor(parent);
        actualFloor.addAll(floor(child));

        Set<BlockPosition> expectedFloor = box(10, 13, 20, 20, 19, 30);
        expectedFloor.addAll(box(-2, 9, 20, 20, 19, 22));
        assertEquals(expectedFloor, actualFloor);
    }

    @Test
    void northSupportFrameAtDepthFourMatchesIndependentGoldenCoordinates() {
        MineSegment segment = segment(MineDirection.NORTH);
        Set<MineSupportFrame.Cell> actual = new HashSet<>(MineSupportFrame.cells(segment, 4));
        Set<MineSupportFrame.Cell> expected = new HashSet<>();

        for (int y = 20; y <= 22; y++) {
            expected.add(new MineSupportFrame.Cell(
                new BlockPosition(10, y, 27), MineSupportFrame.Part.POST
            ));
            expected.add(new MineSupportFrame.Cell(
                new BlockPosition(13, y, 27), MineSupportFrame.Part.POST
            ));
        }
        for (int x = 10; x <= 13; x++) {
            expected.add(new MineSupportFrame.Cell(
                new BlockPosition(x, 23, 27), MineSupportFrame.Part.BEAM
            ));
        }

        assertEquals(expected, actual);
        assertEquals(10, actual.size());
    }

    private static Set<BlockPosition> floor(MineSegment segment) {
        Set<BlockPosition> result = new HashSet<>();
        for (BlockPosition block : segment.blocks()) {
            if (block.y() == START.y()) result.add(block);
        }
        return result;
    }

    private static Set<BlockPosition> box(
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ
    ) {
        Set<BlockPosition> result = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    result.add(new BlockPosition(x, y, z));
                }
            }
        }
        return result;
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
