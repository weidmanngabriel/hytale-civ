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
    void farmPrefabHasExpectedStructureAndEntrance() throws Exception {
        assertTrue(Files.isRegularFile(FARM_PREFAB), "Farm prefab must be packaged in the Asset Pack");

        JsonNode prefab = objectMapper.readTree(Files.readString(FARM_PREFAB));
        assertEquals(8, prefab.path("version").asInt());
        assertEquals(11, prefab.path("blockIdVersion").asInt());
        assertEquals(0, prefab.path("anchorX").asInt());
        assertEquals(0, prefab.path("anchorY").asInt());
        assertEquals(0, prefab.path("anchorZ").asInt());

        JsonNode blocks = prefab.path("blocks");
        assertTrue(blocks.isArray());
        assertTrue(blocks.size() > 150, "Farm should be a real visible building, not a marker block");

        Set<String> coordinates = new HashSet<>();
        boolean hasEntranceThreshold = false;
        boolean hasDoor = false;
        boolean hasRoof = false;
        boolean hasCropBed = false;

        for (JsonNode block : blocks) {
            String coordinate = block.path("x").asInt()
                + ":" + block.path("y").asInt()
                + ":" + block.path("z").asInt();
            assertTrue(coordinates.add(coordinate), "Duplicate prefab coordinate: " + coordinate);

            int x = block.path("x").asInt();
            int y = block.path("y").asInt();
            int z = block.path("z").asInt();
            String name = block.path("name").asText();

            hasEntranceThreshold |= x == 0 && y == 0 && z == 0
                && name.equals("Rock_Stone_Cobble");
            hasDoor |= x == 0 && y == 1 && z == 0
                && name.equals("Furniture_Village_Door");
            hasRoof |= name.equals("Wood_Darkwood_Roof_Flat");
            hasCropBed |= name.equals("Soil_Dirt") && x >= 6;
        }

        assertTrue(hasEntranceThreshold);
        assertTrue(hasDoor);
        assertTrue(hasRoof);
        assertTrue(hasCropBed);
        assertFalse(coordinates.isEmpty());
    }
}
