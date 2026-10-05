package dev.civilizations.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CivMineDebugCommandTest {

    @Test
    void extractsPositionalMineLogCategoriesFromFullCommandInput() {
        assertEquals(
            "PLANNING,GEOMETRY",
            CivMineDebugCommand.mineLogCategoriesArgument(
                "civdebug mine logs on PLANNING,GEOMETRY"
            )
        );
    }

    @Test
    void acceptsParserInputContainingOnlyTheExtraCategoryToken() {
        assertEquals(
            "PLANNING,GEOMETRY",
            CivMineDebugCommand.mineLogCategoriesArgument("PLANNING,GEOMETRY")
        );
    }

    @Test
    void noCategoryArgumentStillMeansEnableAll() {
        assertNull(CivMineDebugCommand.mineLogCategoriesArgument("civdebug mine logs on"));
    }
}
