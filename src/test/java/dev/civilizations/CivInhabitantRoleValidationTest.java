package dev.civilizations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivInhabitantRoleValidationTest {

    private static final Path ROLE_PATH = Path.of(
        "asset-pack/Server/NPC/Roles/Intelligent/Passive/Civ_Inhabitant.json"
    );

    @Test
    void civInhabitantDeclaresSingleNativeMovementPositionSlot() throws Exception {
        JsonNode role = new ObjectMapper().readTree(Files.readString(ROLE_PATH));

        assertEquals("Generic", role.path("Type").asText());
        assertTrue(role.path("NameTranslationKey").isTextual());
        assertEquals("Walk", role.path("MotionControllerList").path(0).path("Type").asText());

        List<JsonNode> readPositionSensors = new ArrayList<>();
        collectReadPositionSensors(role, readPositionSensors);

        assertEquals(
            1,
            readPositionSensors.size(),
            "Java relies on CivMoveTarget being the role's only position slot (index 0)"
        );
        assertEquals("CivMoveTarget", readPositionSensors.getFirst().path("Slot").asText());
    }

    private static void collectReadPositionSensors(JsonNode node, List<JsonNode> result) {
        if (node.isObject()
            && "ReadPosition".equals(node.path("Type").asText())
            && node.has("Slot")) {
            result.add(node);
        }

        node.elements().forEachRemaining(child -> collectReadPositionSensors(child, result));
    }
}
