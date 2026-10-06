package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BuildingOrientationTest {

    @Test
    void rotatesPositionsAroundPrefabAnchor() {
        BlockPosition anchorNorth = new BlockPosition(10, 7, 9);
        int anchorX = 10;
        int anchorZ = 10;

        assertEquals(new BlockPosition(10, 7, 9),
            BuildingOrientation.NORTH.rotateAround(anchorNorth, anchorX, anchorZ));
        assertEquals(new BlockPosition(11, 7, 10),
            BuildingOrientation.EAST.rotateAround(anchorNorth, anchorX, anchorZ));
        assertEquals(new BlockPosition(10, 7, 11),
            BuildingOrientation.SOUTH.rotateAround(anchorNorth, anchorX, anchorZ));
        assertEquals(new BlockPosition(9, 7, 10),
            BuildingOrientation.WEST.rotateAround(anchorNorth, anchorX, anchorZ));
    }

    @Test
    void rotatesMineHeadingsWithSameConvention() {
        assertEquals(MineHeading.NORTH, BuildingOrientation.NORTH.rotate(MineHeading.NORTH));
        assertEquals(MineHeading.EAST, BuildingOrientation.EAST.rotate(MineHeading.NORTH));
        assertEquals(MineHeading.SOUTH, BuildingOrientation.SOUTH.rotate(MineHeading.NORTH));
        assertEquals(MineHeading.WEST, BuildingOrientation.WEST.rotate(MineHeading.NORTH));
        assertEquals(MineHeading.SOUTH_EAST, BuildingOrientation.EAST.rotate(MineHeading.NORTH_EAST));
    }
}
