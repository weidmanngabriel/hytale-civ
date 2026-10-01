package dev.civilizations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WoodcutterPresentationAssetValidationTest {

    private static final Path ROLE_PATH = Path.of(
        "asset-pack/Server/NPC/Roles/Intelligent/Passive/Civ_Inhabitant.json"
    );
    private static final Path AXE_ANIMATIONS_PATH = Path.of(
        "asset-pack/Server/Item/Animations/Civ_Woodcutter_Axe.json"
    );

    @Test
    void civInhabitantUsesPlayerAppearanceForNativeItemAnimations() throws Exception {
        JsonNode role = new ObjectMapper().readTree(Files.readString(ROLE_PATH));
        assertEquals("Player", role.path("Appearance").asText());
    }

    @Test
    void woodcutterAxeUsesLoopingNativeSwingDown() throws Exception {
        JsonNode asset = new ObjectMapper().readTree(Files.readString(AXE_ANIMATIONS_PATH));
        JsonNode swingDown = asset.path("Animations").path("SwingDown");

        assertEquals("Axe", asset.path("Parent").asText());
        assertTrue(swingDown.path("Looping").asBoolean());
        assertEquals(0.75, swingDown.path("Speed").asDouble(), 0.0001);
        assertEquals(
            "Characters/Animations/Items/Main_Handed/Axe/Attacks/Swing_Down/Swing_Down.blockyanim",
            swingDown.path("ThirdPerson").asText()
        );
    }
}
