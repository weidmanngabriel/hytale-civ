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
    void rotatesCardinalDirectionsWithSameConvention() {
        assertEquals(MineDirection.NORTH, BuildingOrientation.NORTH.rotate(MineDirection.NORTH));
        assertEquals(MineDirection.EAST, BuildingOrientation.EAST.rotate(MineDirection.NORTH));
        assertEquals(MineDirection.SOUTH, BuildingOrientation.SOUTH.rotate(MineDirection.NORTH));
        assertEquals(MineDirection.WEST, BuildingOrientation.WEST.rotate(MineDirection.NORTH));
    }
}
