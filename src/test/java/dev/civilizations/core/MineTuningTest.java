package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineTuningTest {

    @Test
    void miningSpeedIsDoubledForTunnelAndRoomWork() {
        assertEquals(30.0 / 128.0, MineTuning.secondsPerBlock(), 0.000001);
    }
}
