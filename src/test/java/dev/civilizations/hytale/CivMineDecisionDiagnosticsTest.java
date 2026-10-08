package dev.civilizations.hytale;

import dev.civilizations.core.MineDecisionCategory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CivMineDecisionDiagnosticsTest {

    @Test
    void parsesCommaSeparatedCategoriesCaseInsensitively() {
        Set<MineDecisionCategory> categories =
            CivMineDecisionDiagnostics.parseCategories("planning, ROOM, worker");

        assertEquals(
            Set.of(
                MineDecisionCategory.PLANNING,
                MineDecisionCategory.ROOM,
                MineDecisionCategory.WORKER
            ),
            categories
        );
    }

    @Test
    void blankFilterMeansAllCategories() {
        assertEquals(
            Set.of(MineDecisionCategory.values()),
            CivMineDecisionDiagnostics.parseCategories(null)
        );
    }

    @Test
    void rejectsUnknownCategory() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CivMineDecisionDiagnostics.parseCategories("PLANNING,NOPE")
        );
    }
}
