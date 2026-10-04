package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinerSurfaceRecoveryContractTest {

    @Test
    void recoveryOnlyRunsForAutonomousSurfaceEscapesAndUsesNativeTeleport() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerSurfaceRecoverySystem.java")
        );

        assertTrue(source.contains("!activityRegistry.autonomousWorkAllowed(ref)"),
            "manual movement and resume delay must suppress recovery");
        assertTrue(source.contains("RECOVERY_DELAY_SECONDS = 1.5"),
            "surface recovery must require a short stable escape window");
        assertTrue(source.contains("position.y < mine.bounds().minY()"),
            "temporary surface heuristic must not recover miners below the mine reference level");
        assertTrue(source.contains("insideKnownTunnel"),
            "known tunnel volumes must be excluded from recovery");
        assertTrue(source.contains("Teleport.getComponentType()"),
            "recovery must use Hytale's native Teleport ECS component");
        assertTrue(source.contains("TUNNEL_CONNECTOR"),
            "recovery target must be the mine tunnel connector");
    }
}
