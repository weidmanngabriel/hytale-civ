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

    private static final Path FARM_PREFAB = Path.of(
        "asset-pack",
        "Server",
        "Prefabs",
        "Civilizations",
        "Farm",
        "Farm_01.prefab.json"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void farmPrefabHasExpectedStructureAndWorkplaceTriggerVolume() throws Exception {
        assertTrue(Files.isRegularFile(FARM_PREFAB));

        JsonNode prefab = objectMapper.readTree(Files.readString(FARM_PREFAB));
        assertEquals(8, prefab.path("version").asInt());
        assertEquals(11, prefab.path("blockIdVersion").asInt());
        assertEquals(0, prefab.path("anchorX").asInt());
        assertEquals(0, prefab.path("anchorY").asInt());
        assertEquals(0, prefab.path("anchorZ").asInt());

        JsonNode blocks = prefab.path("blocks");
        assertTrue(blocks.isArray());
        assertTrue(blocks.size() > 200);

        Set<String> coordinates = new HashSet<>();
        boolean hasRoof = false;
        boolean hasCropBed = false;

        for (JsonNode block : blocks) {
            int x = block.path("x").asInt();
            int y = block.path("y").asInt();
            int z = block.path("z").asInt();
            String name = block.path("name").asText();

            assertTrue(coordinates.add(x + ":" + y + ":" + z));
            assertFalse(name.equals("Civ_BuildingEntrance"));
            hasRoof |= name.equals("Rock_Shale") && y >= 4;
            hasCropBed |= name.equals("Soil_Dirt") && x >= 6;
        }

        assertTrue(hasRoof);
        assertTrue(hasCropBed);

        JsonNode entities = prefab.path("entities");
        assertTrue(entities.isArray());
        assertEquals(1, entities.size());

        JsonNode components = entities.get(0).path("Components");
        JsonNode position = components.path("Transform").path("Position");
        assertEquals(0.0, position.path("X").asDouble());
        assertEquals(1.0, position.path("Y").asDouble());
        assertEquals(-5.0, position.path("Z").asDouble());

        JsonNode trigger = components.path("TriggerVolume");
        assertEquals("Box", trigger.path("Shape").path("Type").asText());
        assertTrue(trigger.path("Enabled").asBoolean());
        assertEquals("Npc", trigger.path("TargetTypes").get(0).asText());
        assertEquals("farm", trigger.path("Tags").path("civ.building").asText());
        assertEquals("workplace_access", trigger.path("Tags").path("civ.type").asText());
        assertFalse(trigger.path("Tags").has("civ.access"));
        assertEquals("civ_farm_workplace", trigger.path("Name").asText());
    }
}
