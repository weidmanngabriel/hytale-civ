package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineBridgeSpanPolicyTest {
    @Test
    void acceptsRequestedBridgesUpToFiftySlices() {
        for (int length : new int[]{1, 16, 30, 50}) {
            assertTrue(MineBridgeSpanPolicy.supports(length));
        }
    }

    @Test
    void rejectsLengthsBeyondLimitAndEmptySpan() {
        assertFalse(MineBridgeSpanPolicy.supports(0));
        assertFalse(MineBridgeSpanPolicy.supports(51));
        assertFalse(MineBridgeSpanPolicy.supports(-1));
    }
}
