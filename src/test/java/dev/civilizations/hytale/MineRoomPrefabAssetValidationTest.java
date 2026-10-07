package dev.civilizations.hytale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineRoomPrefabAssetValidationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> ALLOWED_BLOCKS =
        Set.of("Rock_Stone");

    @Test
    void threeLayerSixTestPrefabsAreSmallReplaceableAndUseKnownBlocks() throws Exception {
        List<String> files = List.of(
            "Small_Niche_01.prefab.json",
            "Material_Storage_01.prefab.json",
            "Rest_Accommodation_01.prefab.json"
        );

        for (String file : files) {
            Path path = Path.of("asset-pack/Server/Prefabs/Civilizations/Mine/Rooms", file);
            assertTrue(Files.exists(path), file);
            JsonNode root = JSON.readTree(Files.readString(path));
            assertEquals(8, root.path("version").asInt());
            assertEquals(0, root.path("anchorX").asInt());
            assertEquals(0, root.path("anchorY").asInt());
            assertEquals(0, root.path("anchorZ").asInt());
            assertTrue(root.path("fluids").isArray());
            assertEquals(0, root.path("fluids").size());
            assertTrue(root.path("blocks").isArray());
            assertFalse(root.path("blocks").isEmpty());
            for (JsonNode block : root.path("blocks")) {
                assertTrue(ALLOWED_BLOCKS.contains(block.path("name").asText()),
                    () -> file + " uses unexpected test block " + block.path("name").asText());
                assertTrue(block.path("y").asInt() >= 0 && block.path("y").asInt() <= 2);
                assertTrue(Math.abs(block.path("x").asInt()) <= 3);
                assertTrue(Math.abs(block.path("z").asInt()) <= 3);
            }
        }
    }
}
