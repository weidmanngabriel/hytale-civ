package dev.civilizations.hytale;

import org.joml.Vector3d;

/**
 * Shared geometric arrival boundary for Hytale NPC movement targets.
 * Native Seek still owns pathfinding. Horizontal reach and vertical clearance
 * remain configurable per task; visual debug marker dimensions have no effect.
 */
public final class CivArrivalPolicy {

    private CivArrivalPolicy() {
    }

    public static boolean reached(
        Vector3d actual, Vector3d target, double horizontalRadius, double verticalTolerance
    ) {
        if (actual == null || target == null
            || !Double.isFinite(horizontalRadius) || horizontalRadius < 0
            || !Double.isFinite(verticalTolerance) || verticalTolerance < 0) {
            return false;
        }
        double dx = actual.x - target.x;
        double dz = actual.z - target.z;
        return Math.abs(actual.y - target.y) <= verticalTolerance
            && dx * dx + dz * dz <= horizontalRadius * horizontalRadius;
    }
}
