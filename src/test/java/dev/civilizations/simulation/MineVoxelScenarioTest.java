package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MineVoxelScenarioTest {

    private static final BlockPosition START = new BlockPosition(10, 20, 30);

    @Test
    void northSegmentCanBeValidatedFaceByFaceInTheReusableMineWorld() {
        MineSegment segment = segment(MineDirection.NORTH);
        MineSimulationWorld actual = new MineSimulationWorld(segment);
        TestVoxelWorld expected = new TestVoxelWorld();

        excavate(actual, segment, 0, 16);
        fill(expected, 10, 13, 20, 23, 30, 30, TestVoxelWorld.Cell.AIR);
        assertWorld(expected, actual, "first 4x4 face");

        excavate(actual, segment, 16, 64);
        actual.placeSupport(segment, 4);
        fill(expected, 10, 13, 20, 23, 27, 30, TestVoxelWorld.Cell.AIR);
        placeNorthGoldenSupport(expected, 27);
        assertWorld(expected, actual, "first four tunnel depths");

        excavate(actual, segment, 64, 128);
        fill(expected, 10, 13, 20, 23, 23, 30, TestVoxelWorld.Cell.AIR);
        placeNorthGoldenSupport(expected, 27);
        assertWorld(expected, actual, "complete supported segment with clear junction");
    }

    @Test
    void rightTurnStillMatchesIndependentGoldenGeometry() {
        MineSegment parent = segment(MineDirection.NORTH);
        MineSegment child = child(parent, MineDirection.EAST);
        MineSimulationWorld parentWorld = new MineSimulationWorld(parent);
        MineSimulationWorld childWorld = new MineSimulationWorld(child);
        TestVoxelWorld expected = new TestVoxelWorld();

        excavate(parentWorld, parent, 0, parent.blocks().size());
        excavate(childWorld, child, 0, child.blocks().size());

        fill(expected, 10, 13, 20, 23, 23, 30, TestVoxelWorld.Cell.AIR);
        fill(expected, 10, 17, 20, 23, 23, 26, TestVoxelWorld.Cell.AIR);

        Map<BlockPosition, TestVoxelWorld.Cell> actual = merged(parentWorld, childWorld, expectedBounds());
        assertEquals(expected.snapshot(expectedBounds()), actual);
    }

    @Test
    void leftTurnStillMatchesIndependentGoldenGeometry() {
        MineSegment parent = segment(MineDirection.NORTH);
        MineSegment child = child(parent, MineDirection.WEST);
        MineSimulationWorld parentWorld = new MineSimulationWorld(parent);
        MineSimulationWorld childWorld = new MineSimulationWorld(child);
        TestVoxelWorld expected = new TestVoxelWorld();

        excavate(parentWorld, parent, 0, parent.blocks().size());
        excavate(childWorld, child, 0, child.blocks().size());

        fill(expected, 10, 13, 20, 23, 23, 30, TestVoxelWorld.Cell.AIR);
        fill(expected, 6, 13, 20, 23, 23, 26, TestVoxelWorld.Cell.AIR);

        Map<BlockPosition, TestVoxelWorld.Cell> actual = merged(parentWorld, childWorld, expectedBounds());
        assertEquals(expected.snapshot(expectedBounds()), actual);
    }

    private static void excavate(
        MineSimulationWorld world,
        MineSegment segment,
        int fromInclusive,
        int toExclusive
    ) {
        for (int index = fromInclusive; index < toExclusive; index++) {
            world.breakBlock(segment.blockAtIndex(index));
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
        MineSimulationWorld actual,
        String stage
    ) {
        MineSimulationWorld.Snapshot snapshot = actual.snapshot();
        TestVoxelWorld.Bounds bounds = new TestVoxelWorld.Bounds(
            snapshot.bounds().minX(), snapshot.bounds().maxX(),
            snapshot.bounds().minY(), snapshot.bounds().maxY(),
            snapshot.bounds().minZ(), snapshot.bounds().maxZ()
        );
        assertEquals(expected.snapshot(bounds), converted(snapshot), stage);
    }

    private static Map<BlockPosition, TestVoxelWorld.Cell> merged(
        MineSimulationWorld first,
        MineSimulationWorld second,
        TestVoxelWorld.Bounds bounds
    ) {
        Map<BlockPosition, TestVoxelWorld.Cell> result = new LinkedHashMap<>();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    BlockPosition p = new BlockPosition(x, y, z);
                    MineSimulationWorld.Cell a = first.get(p);
                    MineSimulationWorld.Cell b = second.get(p);
                    MineSimulationWorld.Cell chosen = a != MineSimulationWorld.Cell.SOLID ? a : b;
                    result.put(p, convert(chosen));
                }
            }
        }
        return Map.copyOf(result);
    }

    private static Map<BlockPosition, TestVoxelWorld.Cell> converted(MineSimulationWorld.Snapshot snapshot) {
        Map<BlockPosition, TestVoxelWorld.Cell> result = new LinkedHashMap<>();
        snapshot.cells().forEach((position, cell) -> result.put(position, convert(cell)));
        return Map.copyOf(result);
    }

    private static TestVoxelWorld.Cell convert(MineSimulationWorld.Cell cell) {
        return switch (cell) {
            case SOLID -> TestVoxelWorld.Cell.SOLID;
            case AIR -> TestVoxelWorld.Cell.AIR;
            case SUPPORT_POST -> TestVoxelWorld.Cell.SUPPORT_POST;
            case SUPPORT_BEAM -> TestVoxelWorld.Cell.SUPPORT_BEAM;
        };
    }

    private static TestVoxelWorld.Bounds expectedBounds() {
        return new TestVoxelWorld.Bounds(5, 19, 19, 24, 21, 32);
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
