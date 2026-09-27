package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreIndependenceTest {

    @Test
    void coreSourcesDoNotImportHytaleApi() throws IOException {
        Path coreSource = Path.of("src", "main", "java", "dev", "civilizations", "core");

        try (var files = Files.walk(coreSource)) {
            List<Path> offenders = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(this::containsHytaleImport)
                .toList();

            assertTrue(offenders.isEmpty(), () -> "Core must stay Hytale-independent. Offenders: " + offenders);
        }
    }

    private boolean containsHytaleImport(Path path) {
        try {
            return Files.readString(path).contains("import com.hypixel.hytale");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect " + path, exception);
        }
    }
}
