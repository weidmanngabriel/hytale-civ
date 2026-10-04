package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinerBuildingBoundaryContractTest {

    @Test
    void onlyInitialConnectorSegmentMayIgnoreOwnMineBounds() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/dev/civilizations/hytale/MinerWorkSystem.java"
        ));

        assertTrue(source.contains("validCandidate(world, mine, initial, true)"));
        assertTrue(source.contains("validCandidate(world, mine, candidate, false)"));
        assertTrue(source.contains("return safeBlock(world, mine, block, false);"));
        assertTrue(source.contains(
            "(!allowOwnMine || !building.id().equals(mine.id()))"
        ));
    }
}
