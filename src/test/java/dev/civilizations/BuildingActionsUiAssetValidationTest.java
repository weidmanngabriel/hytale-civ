package dev.civilizations;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingActionsUiAssetValidationTest {

    private static final Path UI_PATH = Path.of(
        "asset-pack/Common/UI/Custom/Pages/CivBuildingActions.ui"
    );

    @Test
    void upgradeButtonDeclaresTemplateArgumentsBeforeRegularProperties() throws Exception {
        String ui = Files.readString(UI_PATH);
        int buttonStart = ui.indexOf("$C.@TextButton #UpgradeButton {");
        int buttonEnd = ui.indexOf("}\n", buttonStart);

        assertTrue(buttonStart >= 0, "Upgrade button must exist");
        assertTrue(buttonEnd > buttonStart, "Upgrade button block must be complete");

        String block = ui.substring(buttonStart, buttonEnd);
        int textArgument = block.indexOf("@Text = \"Erweitern\";");
        int visibleProperty = block.indexOf("Visible: false;");

        assertTrue(textArgument >= 0, "Upgrade button text template argument must exist");
        assertTrue(visibleProperty >= 0, "Upgrade button visibility property must exist");
        assertTrue(
            textArgument < visibleProperty,
            "Hytale CustomUI requires template arguments such as @Text before regular properties"
        );
    }
}
