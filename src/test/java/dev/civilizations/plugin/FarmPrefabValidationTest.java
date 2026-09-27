package dev.civilizations.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmPrefabValidationTest {

    private static final String ENTRANCE_MARKER = "Civ_BuildingEntrance";
    private static final Path FARM_PREFAB = Path.of(
        "asset-pack",
        "Server",
        "Prefabs",
        "Civilizations",
        "Farm",
        "Farm_01.prefab.json"
    );
    private static final Path ENTRANCE_MARKER_ASSET = Path.of(
        "asset-pack",
        "Server",
        "Item",
        "Block",
        "Blocks",
        ENTRANCE_MARKER + ".json"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void farmPrefabHasExpectedStructureAndEntranceMarker() throws Exception {
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
        boolean hasEntranceThreshold = false;
        boolean hasEntranceMarker = false;
        boolean upperDoorwayIsOpen = true;
        boolean hasRoof = false;
        boolean hasCropBed = false;

        for (JsonNode block : blocks) {
            int x = block.path("x").asInt();
            int y = block.path("y").asInt();
            int z = block.path("z").asInt();
            String name = block.path("name").asText();

            assertTrue(coordinates.add(x + ":" + y + ":" + z));

            hasEntranceThreshold |= x == 0 && y == 0 && z == 0
                && name.equals("Rock_Stone");
            hasEntranceMarker |= x == 0 && y == 1 && z == 0
                && name.equals(ENTRANCE_MARKER);
            if (x == 0 && y == 2 && z == 0) {
                upperDoorwayIsOpen = false;
            }
            hasRoof |= name.equals("Rock_Shale") && y >= 4;
            hasCropBed |= name.equals("Soil_Dirt") && x >= 6;
        }

        assertTrue(hasEntranceThreshold);
        assertTrue(hasEntranceMarker);
        assertTrue(upperDoorwayIsOpen);
        assertTrue(hasRoof);
        assertTrue(hasCropBed);
    }

    @Test
    void entranceMarkerIsEditorVisibleButNonPhysical() throws Exception {
        assertTrue(Files.isRegularFile(ENTRANCE_MARKER_ASSET));

        JsonNode marker = objectMapper.readTree(Files.readString(ENTRANCE_MARKER_ASSET));
        assertEquals("@Tech", marker.path("Group").asText());
        assertEquals("GizmoCube", marker.path("DrawType").asText());
        assertEquals("Empty", marker.path("Material").asText());
        assertEquals("Empty", marker.path("HitboxType").asText());
        assertEquals("Full", marker.path("InteractionHitboxType").asText());
    }
}
