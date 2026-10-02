package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrefabConstructionOrderTest {

    @Test
    void ordinaryPrefabBuildsBottomToTop() {
        assertEquals(
            List.of(0, 1, 2, 3),
            PrefabConstructionOrder.order(List.of(3, 1, 0, 2), null)
        );
    }

    @Test
    void semanticGroundLevelBuildsSurfaceUpThenDown() {
        assertEquals(
            List.of(16, 17, 18, 19, 20, 21, 22, 23, 24, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
            PrefabConstructionOrder.order(
                List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24),
                16
            )
        );
    }

    @Test
    void emptyPrefabStaysEmpty() {
        assertEquals(List.of(), PrefabConstructionOrder.order(List.of(), 16));
    }
}
