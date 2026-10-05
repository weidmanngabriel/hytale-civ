package dev.civilizations;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MineTunnelPortalAssetValidationTest {

    private static final Path MINE_DIR = Path.of(
        "asset-pack/Server/Prefabs/Civilizations/Mine"
    );

    @Test
    void allMinePhasesKeepTimberPortalInsideAndStoneAtExcavationRow() throws Exception {
        for (String fileName : new String[] {"Mine_01.prefab.json", "Mine_02.prefab.json", "Mine_03.prefab.json"}) {
            String prefab = Files.readString(MINE_DIR.resolve(fileName));

            for (int x : new int[] {-3, 2}) {
                for (int y = 1; y <= 4; y++) {
                    assertEquals("Wood_Fir_Trunk", blockName(prefab, x, y, 8), fileName + " timber side");
                    assertEquals("Rock_Stone", blockName(prefab, x, y, 9), fileName + " stone side");
                }
            }

            for (int x : new int[] {-2, -1, 0, 1}) {
                assertEquals("Wood_Fir_Trunk", blockName(prefab, x, 5, 8), fileName + " timber top");
                assertEquals("Rock_Stone", blockName(prefab, x, 5, 9), fileName + " stone top");
            }
        }
    }

    @Test
    void lanternsMoveWithPortalOnlyForMineTwoAndThree() throws Exception {
        String mineOne = Files.readString(MINE_DIR.resolve("Mine_01.prefab.json"));
        String mineTwo = Files.readString(MINE_DIR.resolve("Mine_02.prefab.json"));
        String mineThree = Files.readString(MINE_DIR.resolve("Mine_03.prefab.json"));

        for (int x : new int[] {-2, 1}) {
            assertEquals("Empty", blockName(mineOne, x, 4, 8));
            assertEquals("Empty", blockName(mineOne, x, 4, 9));

            assertEquals("Deco_Lantern_Ceiling", blockName(mineTwo, x, 4, 8));
            assertEquals("Empty", blockName(mineTwo, x, 4, 9));

            assertEquals("Deco_Lantern_Ceiling", blockName(mineThree, x, 4, 8));
            assertEquals("Empty", blockName(mineThree, x, 4, 9));
        }
    }

    private static String blockName(String prefab, int x, int y, int z) {
        Pattern block = Pattern.compile(
            "\\{\\s*\\\"x\\\"\\s*:\\s*" + x
                + ",\\s*\\\"y\\\"\\s*:\\s*" + y
                + ",\\s*\\\"z\\\"\\s*:\\s*" + z
                + ",\\s*\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            Pattern.DOTALL
        );
        Matcher matcher = block.matcher(prefab);
        assertNotNull(matcher, "Matcher must exist");
        if (!matcher.find()) {
            throw new AssertionError("Missing block at " + x + "," + y + "," + z);
        }
        return matcher.group(1);
    }
}
