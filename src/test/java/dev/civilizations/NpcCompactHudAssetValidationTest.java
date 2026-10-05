package dev.civilizations;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCompactHudAssetValidationTest {

    private static final Path HUD_PATH = Path.of(
        "asset-pack/Common/UI/Custom/Hud/CivNpcCompact.ui"
    );

    @Test
    void compactHudContainsStableDynamicFieldIdsAndBottomLeftAnchor() throws Exception {
        String ui = Files.readString(HUD_PATH);

        assertTrue(ui.contains("Left: 24"));
        assertTrue(ui.contains("Bottom: 118"));
        assertTrue(ui.contains("Width: 340"));
        assertTrue(ui.contains("Height: 190"));
        assertTrue(ui.contains("#NpcName"));
        assertTrue(ui.contains("#Profession"));
        assertTrue(ui.contains("#Activity"));
        assertTrue(ui.contains("#Experience"));
        assertTrue(ui.contains("#Hunger"));
        assertTrue(ui.contains("#Home"));
    }
}
