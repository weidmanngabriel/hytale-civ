package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineDecorationDiagnosticsContractTest {

    @Test
    void decorationSkipDiagnosticsCoverSelectorAndWorldResolutionReasons() throws Exception {
        String resolver = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MineInfrastructurePlacementResolver.java")
        );
        String work = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );

        assertTrue(resolver.contains("DecorationResolution resolveDecorationDetailed("));
        assertTrue(resolver.contains("\"NAVIGATION_CORE_CONFLICT\""));
        assertTrue(resolver.contains("\"TARGET_OCCUPIED\""));
        assertTrue(resolver.contains("\"MISSING_FLOOR_SUPPORT\""));
        assertTrue(resolver.contains("\"MISSING_CEILING_SUPPORT\""));
        assertTrue(resolver.contains("\"CHAIN_ASSET_MISSING\""));
        assertTrue(resolver.contains("\"LANTERN_ASSET_MISSING\""));

        assertTrue(work.contains("\"DECORATION_TASK_SELECTED\""));
        assertTrue(work.contains("\"DECORATION_TASK_SKIPPED\""));
        assertTrue(work.contains("\"AVAILABLE_NOT_SELECTED\""));
        assertTrue(work.contains("\"DECORATION_SKIPPED_RUNTIME\""));
        assertTrue(work.contains("\"triedSlices\", decorationResolution.triedSlices()"));
        assertTrue(work.contains("\"reasons\", decorationResolution.reasons()"));
    }
}
