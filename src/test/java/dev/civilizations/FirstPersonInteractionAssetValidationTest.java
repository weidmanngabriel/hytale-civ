package dev.civilizations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FirstPersonInteractionAssetValidationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void unarmedSecondaryRoutesToCivPersonActions() throws Exception {
        JsonNode unarmed = read("asset-pack/Server/Item/Unarmed/Interactions/Empty.json");
        assertEquals(
            "Root_Civ_Unarmed_Secondary",
            unarmed.path("Interactions").path("Secondary").asText()
        );

        JsonNode root = read(
            "asset-pack/Server/Item/RootInteractions/Root_Civ_Unarmed_Secondary.json"
        );
        assertEquals("Civ_OpenPersonActions", root.path("Interactions").get(0).asText());

        JsonNode interaction = read(
            "asset-pack/Server/Item/Interactions/Civ_OpenPersonActions.json"
        );
        assertEquals("OpenCustomUI", interaction.path("Type").asText());
        assertEquals("CivPersonActions", interaction.path("Page").path("Type").asText());
    }

    private static JsonNode read(String path) throws Exception {
        return JSON.readTree(Files.readString(Path.of(path)));
    }
}
