package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineCavePolicyTest {

    @Test
    void completeSmallOpeningStaysSmall() {
        assertEquals(
            MineCaveObservation.Status.SMALL,
            MineCavePolicy.classify(80, 16, 6, 3, 7, true)
        );
    }

    @Test
    void incompleteOpeningIsNotPrematurelyClassifiedAsSmall() {
        assertEquals(
            MineCaveObservation.Status.INCOMPLETE,
            MineCavePolicy.classify(80, 16, 6, 3, 7, false)
        );
    }

    @Test
    void sufficientlyLargeUsefulSpaceBecomesNaturalChamber() {
        assertEquals(
            MineCaveObservation.Status.LARGE,
            MineCavePolicy.classify(
                MineCavePolicy.LARGE_MIN_EMPTY_BLOCKS,
                MineCavePolicy.LARGE_MIN_USABLE_FLOOR_BLOCKS,
                MineCavePolicy.LARGE_MIN_HORIZONTAL_SPAN,
                MineCavePolicy.LARGE_MIN_HEIGHT,
                MineCavePolicy.LARGE_MIN_HORIZONTAL_SPAN,
                true
            )
        );
    }

    @Test
    void largeVolumeWithoutUsefulFloorIsNotAUsefulNaturalChamber() {
        assertEquals(
            MineCaveObservation.Status.SMALL,
            MineCavePolicy.classify(500, 2, 20, 10, 20, true)
        );
    }

    @Test
    void nearbyObservationsDeduplicateOneNaturalChamber() {
        BlockPosition center = new BlockPosition(100, 20, 100);
        assertTrue(MineCavePolicy.sameNaturalChamber(center, new BlockPosition(110, 22, 106)));
        assertFalse(MineCavePolicy.sameNaturalChamber(center, new BlockPosition(130, 20, 100)));
    }
}
