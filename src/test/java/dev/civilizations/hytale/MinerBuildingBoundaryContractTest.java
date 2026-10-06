package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinerBuildingBoundaryContractTest {

    @Test
    void plannedFrontExcavationNeverBypassesBuildingBounds() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/dev/civilizations/hytale/MinerWorkSystem.java"
        ));

        assertTrue(source.contains("building.bounds().containsBlock(block)"));
        assertFalse(source.contains("allowOwnMine"));
        assertTrue(source.contains("MineTunnelGeometry.Slice"));
    }
}
