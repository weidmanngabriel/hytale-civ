package dev.civilizations.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmPrefabValidationTest {

    private static final Path PREFAB_DIR = Path.of(
        "asset-pack", "Server", "Prefabs", "Civilizations", "Farm"
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void farmPrefabHasEmptyNativeStorageAndSemanticMarkers() throws Exception {
        JsonNode prefab = read("Farm_01.prefab.json");
        assertCommonPrefabHeader(prefab);

        JsonNode blocks = prefab.path("blocks");
        assertEquals(236, blocks.size());

        boolean foundEmptyChest = false;
        for (JsonNode block : blocks) {
            JsonNode container = block.path("components")
                .path("Components")
                .path("ItemContainerBlock")
                .path("ItemContainer");
            if (!container.isMissingNode()) {
                assertEquals(18, container.path("Capacity").asInt());
                assertTrue(container.path("Items").isObject());
                assertEquals(0, container.path("Items").size());
                foundEmptyChest = true;
            }
        }
        assertTrue(foundEmptyChest);

        JsonNode entities = prefab.path("entities");
        assertEquals(3, entities.size());
        assertMarker(entities, "civ_farm_building", "farm", "building_bounds");
        assertMarker(entities, "civ_farm_workplace", "farm", "workplace_access");
        assertMarker(entities, "civ_farm_output_storage", "farm", "output_storage");
    }

    @Test
    void fieldPrefabIsSeparateTilledFieldWithFieldMarker() throws Exception {
        JsonNode prefab = read("Field_01.prefab.json");
        assertCommonPrefabHeader(prefab);

        JsonNode blocks = prefab.path("blocks");
        assertEquals(36, blocks.size());

        Set<String> coordinates = new HashSet<>();
        for (JsonNode block : blocks) {
            assertTrue(coordinates.add(
                block.path("x").asInt() + ":"
                    + block.path("y").asInt() + ":"
                    + block.path("z").asInt()
            ));
            assertTrue(block.path("name").asText().contains("Soil_Dirt_Tilled"));
            assertEquals(0, block.path("y").asInt());
        }

        JsonNode entities = prefab.path("entities");
        assertEquals(1, entities.size());
        assertMarker(entities, "civ_farm_field", "farm", "field");
    }

    private JsonNode read(String fileName) throws Exception {
        Path path = PREFAB_DIR.resolve(fileName);
        assertTrue(Files.isRegularFile(path));
        return objectMapper.readTree(Files.readString(path));
    }

    private static void assertCommonPrefabHeader(JsonNode prefab) {
        assertEquals(8, prefab.path("version").asInt());
        assertEquals(11, prefab.path("blockIdVersion").asInt());
        assertEquals(0, prefab.path("anchorX").asInt());
        assertEquals(0, prefab.path("anchorY").asInt());
        assertEquals(0, prefab.path("anchorZ").asInt());
    }

    private static void assertMarker(
        JsonNode entities,
        String expectedName,
        String expectedBuilding,
        String expectedType
    ) {
        boolean found = false;
        for (JsonNode entity : entities) {
            JsonNode trigger = entity.path("Components").path("TriggerVolume");
            if (!expectedName.equals(trigger.path("Name").asText())) {
                continue;
            }
            assertEquals("Box", trigger.path("Shape").path("Type").asText());
            assertTrue(trigger.path("Enabled").asBoolean());
            assertEquals("Npc", trigger.path("TargetTypes").get(0).asText());
            assertEquals(expectedBuilding, trigger.path("Tags").path("civ.building").asText());
            assertEquals(expectedType, trigger.path("Tags").path("civ.type").asText());
            assertFalse(trigger.path("Tags").has("civ.access"));
            found = true;
        }
        assertTrue(found, "Missing trigger marker " + expectedName);
    }
}
