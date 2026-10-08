package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinePlacementExcavationGuardTest {
    @Test
    void blocksPlacementOneBlockAwayFromSolidPendingExcavation() {
        BlockPosition solid = new BlockPosition(10, 64, 21);
        assertTrue(MinePlacementExcavationGuard.conflicts(
            new BlockPosition(11, 64, 21), List.of(solid), b -> true));
        assertTrue(MinePlacementExcavationGuard.conflicts(
            new BlockPosition(10, 65, 22), List.of(solid), b -> true));
    }

    @Test
    void completedExcavationAndTwoBlockClearanceAreAllowed() {
        BlockPosition former = new BlockPosition(10, 64, 21);
        assertFalse(MinePlacementExcavationGuard.conflicts(
            new BlockPosition(11, 64, 21), List.of(former), b -> false));
        assertFalse(MinePlacementExcavationGuard.conflicts(
            new BlockPosition(12, 64, 21), List.of(former), b -> true));
    }
}
