package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Small explicit coordinate oracle for Layer-3 voxelization. */
final class MineTunnelVoxelizerGoldenTest {

    @Test
    void straightThreeSliceBranchProducesExpectedSafeVoxelBox() {
        MineTunnelPath path = new MineTunnelPath(
            MineTunnel.Kind.BRANCH,
            123L,
            List.of(
                point(0, 0.0),
                point(1, 1.0),
                point(2, 2.0)
            ),
            List.of(new MineFormPhase(
                0,
                0,
                2,
                MineHeading.EAST,
                MineHeading.EAST,
                3.0,
                3.0,
                3.0,
                3.0,
                0.0,
                0.0,
                0
            ))
        );

        MineTunnelGeometry geometry = MineTunnelVoxelizer.voxelize(path);
        Set<BlockPosition> expected = box(-1, 3, 80, 82, -1, 1);

        assertEquals(expected, geometry.navigationCoreBlocks());
        assertEquals(expected, geometry.excavationBlocks());
        assertEquals(45, expected.size());
        assertEquals(3, geometry.slices().size());
        assertEquals(List.of(), geometry.stepTransitions());
    }

    private static MinePathPoint point(int index, double x) {
        return new MinePathPoint(
            index,
            x,
            80.0,
            0.0,
            3.0,
            3.0,
            MineHeading.EAST,
            0.0,
            0
        );
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
}
