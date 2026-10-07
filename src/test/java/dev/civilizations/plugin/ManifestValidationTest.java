package dev.civilizations.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void manifestContainsRequiredPluginMetadata() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/manifest.json")) {
            assertNotNull(stream, "manifest.json must be present on the runtime classpath");

            JsonNode manifest = objectMapper.readTree(stream);

            assertEquals("Civilizations", manifest.path("Group").asText());
            assertEquals("HytaleCiv", manifest.path("Name").asText());
            assertEquals(System.getProperty("projectVersion"), manifest.path("Version").asText());
            assertEquals("dev.civilizations.plugin.CivilizationsPlugin", manifest.path("Main").asText());
            assertEquals("^0.6.0", manifest.path("ServerVersion").asText());
            assertTrue(manifest.path("Authors").isArray());
            assertFalse(manifest.path("Authors").isEmpty());
            assertTrue(manifest.path("Dependencies").isObject());
            assertTrue(manifest.path("OptionalDependencies").isObject());
            assertTrue(manifest.path("LoadBefore").isObject());
            assertFalse(manifest.path("DisabledByDefault").asBoolean());
        }
    }
}
