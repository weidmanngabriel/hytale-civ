package dev.civilizations.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CivMineDebugCommandTest {

    @Test
    void extractsPositionalMineLogCategoriesFromFullCommandInput() {
        assertEquals(
            "PLANNING,GEOMETRY",
            MineLogCommandInput.categoriesArgument(
                "civdebug mine logs on PLANNING,GEOMETRY"
            )
        );
    }

    @Test
    void acceptsParserInputContainingOnlyTheExtraCategoryToken() {
        assertEquals(
            "PLANNING,GEOMETRY",
            MineLogCommandInput.categoriesArgument("PLANNING,GEOMETRY")
        );
    }

    @Test
    void noCategoryArgumentStillMeansEnableAll() {
        assertNull(MineLogCommandInput.categoriesArgument("civdebug mine logs on"));
    }
}
