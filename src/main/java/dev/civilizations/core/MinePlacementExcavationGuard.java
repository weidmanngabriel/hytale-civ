package dev.civilizations.core;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * Prevents optional infrastructure from occupying the excavation clearance envelope.
 * The work planner supplies planned excavation blocks; the adapter supplies whether
 * these blocks are still solid in the actual world.
 */
public final class MinePlacementExcavationGuard {
    private MinePlacementExcavationGuard() {
    }

    public static boolean conflicts(
        BlockPosition placement,
        Collection<BlockPosition> excavation,
        Predicate<BlockPosition> stillSolid
    ) {
        for (BlockPosition block : excavation) {
            if (Math.abs(placement.x() - block.x()) <= 1
                && Math.abs(placement.y() - block.y()) <= 1
                && Math.abs(placement.z() - block.z()) <= 1
                && stillSolid.test(block)) {
                return true;
            }
        }
        return false;
    }
}
