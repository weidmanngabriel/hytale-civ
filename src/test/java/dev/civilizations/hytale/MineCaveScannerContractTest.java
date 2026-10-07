package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineCaveScannerContractTest {

    @Test
    void caveScannerUsesLoadedWorldDataWithoutReplacingNavigation() throws Exception {
        String scanner = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MineCaveScanner.java")
        );
        String work = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );

        assertTrue(scanner.contains("getChunkIfLoaded"));
        assertFalse(scanner.contains("getChunk("));
        assertTrue(scanner.contains("MineCavePolicy.classify"));
        assertTrue(scanner.contains("excludedMine.contains"));
        assertTrue(scanner.contains("getFluidId"));
        assertTrue(scanner.contains("Fluid.hasEffect") || scanner.contains("fluid.hasEffect"));

        assertTrue(work.contains("MineCaveScanner.scan"));
        assertTrue(work.contains("LARGE_NATURAL_CHAMBER"));
        assertTrue(work.contains("NATURAL_INTEGRATED"));
        assertTrue(work.contains("hasSafeOppositeLanding"));
    }
}
