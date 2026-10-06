package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineTuningTest {

    @Test
    void miningSpeedKeepsTheEstablishedPerBlockRate() {
        assertEquals(60.0 / 128.0, MineTuning.secondsPerBlock(), 0.000001);
    }
}
