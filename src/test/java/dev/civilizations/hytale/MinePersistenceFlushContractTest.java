package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinePersistenceFlushContractTest {

    @Test
    void frequentMineProgressStagesResourceWithoutForcingGlobalResourceSave() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/CivMinePersistenceService.java")
        );

        assertTrue(source.contains("resource(world).setSegments"),
            "mine progress must still be staged in the native world resource");
        assertFalse(source.contains(".saveAllResources();"),
            "frequent mine progress must not force overlapping global resource saves");
    }
}
