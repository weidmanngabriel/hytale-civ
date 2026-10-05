package dev.civilizations;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingActionsUiAssetValidationTest {

    private static final Path ACTIONS_UI_PATH = Path.of(
        "asset-pack/Common/UI/Custom/Pages/CivBuildingActions.ui"
    );
    private static final Path BUILD_MENU_UI_PATH = Path.of(
        "asset-pack/Common/UI/Custom/Pages/CivBuildingMenu.ui"
    );

    @Test
    void upgradeButtonDeclaresTemplateArgumentsBeforeRegularProperties() throws Exception {
        String ui = Files.readString(ACTIONS_UI_PATH);
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

    @Test
    void buildingActionsReserveSpaceForFutureUpgradeResources() throws Exception {
        String ui = Files.readString(ACTIONS_UI_PATH);
        assertTrue(ui.contains("Label #UpgradeRequirements"));
        assertTrue(ui.contains("Benötigte Rohstoffe: noch nicht verfügbar"));
    }

    @Test
    void debugBuildMenuExposesAllAuthoredMinePhases() throws Exception {
        String ui = Files.readString(BUILD_MENU_UI_PATH);
        assertTrue(ui.contains("Mine 1 – Kupfer"));
        assertTrue(ui.contains("Mine 2 – Eisen"));
        assertTrue(ui.contains("Mine 3 – Gold"));
    }
}
