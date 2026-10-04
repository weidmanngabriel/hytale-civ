package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MineSupportNativeSupportValuesContractTest {

    @Test
    void appliesNativeSupportValuesBeforePlacingMineSupportPrefab() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );

        String supportValuesCall =
            "BlockSelectionSupportUtil.applySupportValues(selection)";
        String placementCall = "selection.placeNoReturn(";

        int supportValuesIndex = source.indexOf(supportValuesCall);
        int placementIndex = source.indexOf(placementCall);

        assertTrue(supportValuesIndex >= 0, "mine support placement must apply native support values");
        assertTrue(placementIndex >= 0, "mine support placement call must remain present");
        assertTrue(
            supportValuesIndex < placementIndex,
            "native support values must be applied before the prefab is placed"
        );
    }
}
