package dev.civilizations.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Core gameplay policy for mine-front failure outcomes and bounded fallback placement.
 */
public final class MineObstaclePolicy {

    public static final int OPTIONAL_INFRASTRUCTURE_FALLBACK_RADIUS = 3;

    private MineObstaclePolicy() {
    }

    public enum FailureKind {
        NAVIGATION_UNREACHABLE,
        UNSAFE_GEOMETRY,
        MANDATORY_INFRASTRUCTURE_UNRESOLVABLE,
        HAZARDOUS_FLUID
    }

    public static MineWorkFront.State frontStateFor(FailureKind failure) {
        if (failure == null) {
            throw new IllegalArgumentException("Mine failure kind must not be null.");
        }
        return failure == FailureKind.NAVIGATION_UNREACHABLE
            ? MineWorkFront.State.BLOCKED
            : MineWorkFront.State.ABANDONED;
    }

    /**
     * Returns the preferred slice first, then nearest alternatives alternating backward/forward.
     */
    public static List<Integer> fallbackSliceOrder(int preferred, int sliceCount) {
        if (sliceCount < 0) {
            throw new IllegalArgumentException("sliceCount must not be negative.");
        }
        if (preferred < 0 || preferred >= sliceCount) return List.of();

        List<Integer> result = new ArrayList<>();
        result.add(preferred);
        for (int distance = 1; distance <= OPTIONAL_INFRASTRUCTURE_FALLBACK_RADIUS; distance++) {
            int before = preferred - distance;
            int after = preferred + distance;
            if (before >= 0) result.add(before);
            if (after < sliceCount) result.add(after);
        }
        return List.copyOf(result);
    }
}
