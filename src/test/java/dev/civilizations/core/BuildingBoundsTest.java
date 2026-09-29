package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingBoundsTest {

    @Test
    void containsUsesWholeBlockCenterForPickingAndProtection() {
        BuildingBounds bounds = new BuildingBounds(-7, 0, -7, 8, 4, 8);

        assertTrue(bounds.containsBlock(new BlockPosition(-7, 0, -7)));
        assertTrue(bounds.containsBlock(new BlockPosition(7, 3, 7)));
        assertFalse(bounds.containsBlock(new BlockPosition(8, 0, 0)));
        assertFalse(bounds.containsBlock(new BlockPosition(0, 4, 0)));
    }

    @Test
    void horizontalOverlapTreatsTouchingEdgesAsNonOverlapping() {
        BuildingBounds bounds = new BuildingBounds(0, 0, 0, 10, 5, 10);

        assertTrue(bounds.overlapsHorizontal(9, 9, 12, 12));
        assertFalse(bounds.overlapsHorizontal(10, 0, 12, 2));
    }
}
