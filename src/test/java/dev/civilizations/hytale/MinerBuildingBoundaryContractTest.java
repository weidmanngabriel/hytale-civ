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

        assertTrue(source.contains("direction, true"));
        assertTrue(source.contains("parent.nextStart(direction), direction, false"));
        assertTrue(source.contains("validateCandidate(world, mine, candidate, allowOwnMine)"));
        assertTrue(source.contains("return safeBlock(world, mine, block, false);"));
        assertTrue(source.contains(
            "(!allowOwnMine || !building.id().equals(mine.id()))"
        ));
    }
}
