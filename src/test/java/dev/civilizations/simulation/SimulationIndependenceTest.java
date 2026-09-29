package dev.civilizations.simulation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationIndependenceTest {

    @Test
    void simulationSourcesDoNotImportHytaleApi() throws IOException {
        Path simulationSource = Path.of(
            "src",
            "main",
            "java",
            "dev",
            "civilizations",
            "simulation"
        );

        try (var files = Files.walk(simulationSource)) {
            List<Path> offenders = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(this::containsHytaleImport)
                .toList();

            assertTrue(
                offenders.isEmpty(),
                () -> "Simulation runtime must stay Hytale-independent. Offenders: " + offenders
            );
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
