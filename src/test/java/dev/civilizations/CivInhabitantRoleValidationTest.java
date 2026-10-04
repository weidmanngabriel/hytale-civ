package dev.civilizations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivInhabitantRoleValidationTest {

    private static final Path ROLE_PATH = Path.of(
        "asset-pack/Server/NPC/Roles/Intelligent/Passive/Civ_Inhabitant.json"
    );

    @Test
    void civInhabitantDeclaresOrdinaryAndMinerNativeMovementSlots() throws Exception {
        JsonNode role = new ObjectMapper().readTree(Files.readString(ROLE_PATH));

        assertEquals("Generic", role.path("Type").asText());
        assertTrue(role.path("NameTranslationKey").isTextual());
        assertEquals("Walk", role.path("MotionControllerList").path(0).path("Type").asText());

        JsonNode idleInstructions = role.path("Instructions").path(0).path("Instructions");
        JsonNode normalMovement = idleInstructions.path(0);
        JsonNode minerMovement = idleInstructions.path(1);

        assertEquals("ReadPosition", normalMovement.path("Sensor").path("Type").asText());
        assertEquals("CivMoveTarget", normalMovement.path("Sensor").path("Slot").asText());
        assertEquals("Seek", normalMovement.path("BodyMotion").path("Type").asText());
        assertTrue(normalMovement.path("BodyMotion").path("UsePathfinder").asBoolean());
        assertFalse(
            normalMovement.path("BodyMotion").has("UseBestPath"),
            "ordinary inhabitants must keep Hytale's default Seek behavior"
        );

        assertEquals("ReadPosition", minerMovement.path("Sensor").path("Type").asText());
        assertEquals("CivMinerMoveTarget", minerMovement.path("Sensor").path("Slot").asText());
        assertEquals("Seek", minerMovement.path("BodyMotion").path("Type").asText());
        assertTrue(minerMovement.path("BodyMotion").path("UsePathfinder").asBoolean());
        assertFalse(
            minerMovement.path("BodyMotion").path("UseBestPath").asBoolean(true),
            "miners must reject Hytale best-effort partial paths"
        );
    }
}
