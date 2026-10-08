package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineInfrastructureAvailabilityTest {
    @Test
    void descendingStairWaitsUntilLowerSliceWasExcavated() {
        // Transition 35 -> 36. At active slice 36 the lower slice is still solid.
        assertFalse(MineInfrastructureAvailability.isAvailable(
            MineInfrastructureTask.Type.BUILD_STEP, 35, 36, 36, false
        ));
        // After finishing lower slice 36, the front advances to 37.
        assertTrue(MineInfrastructureAvailability.isAvailable(
            MineInfrastructureTask.Type.BUILD_STEP, 35, 36, 37, false
        ));
    }

    @Test
    void ascendingStairAlsoWaitsForBothSidesOfTransition() {
        assertFalse(MineInfrastructureAvailability.isAvailable(
            MineInfrastructureTask.Type.BUILD_STEP, 10, 11, 11, false
        ));
        assertTrue(MineInfrastructureAvailability.isAvailable(
            MineInfrastructureTask.Type.BUILD_STEP, 10, 11, 12, false
        ));
        assertTrue(MineInfrastructureAvailability.isAvailable(
            MineInfrastructureTask.Type.BUILD_STEP, 10, 11, 11, true
        ));
    }

    @Test
    void bridgesStillBlockUnexcavatedGapsAndOptionalWorkIsUnaffected() {
        assertTrue(MineInfrastructureAvailability.isAvailable(
            MineInfrastructureTask.Type.BUILD_BRIDGE, 4, 5, 4, false
        ));
        assertTrue(MineInfrastructureAvailability.isAvailable(
            MineInfrastructureTask.Type.BUILD_BRIDGE, 4, 5, 5, false
        ));
        assertFalse(MineInfrastructureAvailability.isAvailable(
            MineInfrastructureTask.Type.BUILD_BRIDGE, 4, 5, 6, false
        ));
        assertFalse(MineInfrastructureAvailability.isAvailable(
            MineInfrastructureTask.Type.BUILD_BRIDGE, 4, 5, 4, true
        ));
        assertTrue(MineInfrastructureAvailability.isAvailable(
            MineInfrastructureTask.Type.BUILD_SUPPORT, 4, 4, 5, false
        ));
    }
}
