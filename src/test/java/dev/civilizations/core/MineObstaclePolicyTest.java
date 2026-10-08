package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class MineObstaclePolicyTest {

    @Test
    void recoveryOnlyAllowsAbandonedFrontAtUnfinishedStair() {
        assertTrue(MineObstaclePolicy.mayRetryAbandonedStair(MineWorkFront.State.ABANDONED, true));
        assertFalse(MineObstaclePolicy.mayRetryAbandonedStair(MineWorkFront.State.ABANDONED, false));
        assertFalse(MineObstaclePolicy.mayRetryAbandonedStair(MineWorkFront.State.BLOCKED, true));
        assertFalse(MineObstaclePolicy.mayRetryAbandonedStair(MineWorkFront.State.OPEN, true));
    }


    @Test
    void terminalNavigationFailureBlocksButDoesNotAbandonFront() {
        assertEquals(
            MineWorkFront.State.BLOCKED,
            MineObstaclePolicy.frontStateFor(MineObstaclePolicy.FailureKind.NAVIGATION_UNREACHABLE)
        );
    }

    @Test
    void unsafeOrUnresolvableWorkAbandonsFront() {
        assertEquals(
            MineWorkFront.State.ABANDONED,
            MineObstaclePolicy.frontStateFor(MineObstaclePolicy.FailureKind.UNSAFE_GEOMETRY)
        );
        assertEquals(
            MineWorkFront.State.ABANDONED,
            MineObstaclePolicy.frontStateFor(
                MineObstaclePolicy.FailureKind.MANDATORY_INFRASTRUCTURE_UNRESOLVABLE
            )
        );
        assertEquals(
            MineWorkFront.State.ABANDONED,
            MineObstaclePolicy.frontStateFor(MineObstaclePolicy.FailureKind.HAZARDOUS_FLUID)
        );
    }

    @Test
    void optionalInfrastructureSearchesNearestSlicesWithinThree() {
        assertEquals(
            List.of(5, 4, 6, 3, 7, 2, 8),
            MineObstaclePolicy.fallbackSliceOrder(5, 12)
        );
        assertEquals(
            List.of(0, 1, 2, 3),
            MineObstaclePolicy.fallbackSliceOrder(0, 12)
        );
    }
}
