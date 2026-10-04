package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MineTuningTest {

    @Test
    void everyMainTunnelReservesAnAdditionalFourBlockJunction() {
        assertEquals(4, MineTuning.MIN_SEGMENT_LENGTH_BLOCKS);
        assertEquals(12, MineTuning.MAX_SEGMENT_LENGTH_BLOCKS);
        assertEquals(4, MineTuning.JUNCTION_LENGTH_BLOCKS);
        assertEquals(8, MineTuning.totalDepthBlocks(4));
        assertEquals(12, MineTuning.totalDepthBlocks(8));
        assertEquals(16, MineTuning.totalDepthBlocks(12));
        assertEquals(128, MineTuning.blocksPerSegment(4));
        assertEquals(192, MineTuning.blocksPerSegment(8));
        assertEquals(256, MineTuning.blocksPerSegment(12));
        assertThrows(IllegalArgumentException.class, () -> MineTuning.blocksPerSegment(3));
        assertThrows(IllegalArgumentException.class, () -> MineTuning.blocksPerSegment(13));
    }

    @Test
    void miningSpeedKeepsTheEstablishedPerBlockRate() {
        assertEquals(60.0 / 128.0, MineTuning.secondsPerBlock(), 0.000001);
    }

    @Test
    void regularSupportFramesAppearEveryFourMainTunnelBlocksIncludingTheEnd() {
        assertEquals(1, MineTuning.supportFramesForLength(4));
        assertEquals(2, MineTuning.supportFramesForLength(8));
        assertEquals(3, MineTuning.supportFramesForLength(12));
    }
}
