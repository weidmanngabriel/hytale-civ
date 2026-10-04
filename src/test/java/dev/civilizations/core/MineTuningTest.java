package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineTuningTest {

    @Test
    void regularSupportFramesStopBeforeSegmentJunction() {
        assertEquals(8, MineTuning.SEGMENT_LENGTH_BLOCKS);
        assertEquals(4, MineTuning.SUPPORT_SPACING_BLOCKS);
        assertEquals(1, MineTuning.supportFramesPerSegment());
    }
}
