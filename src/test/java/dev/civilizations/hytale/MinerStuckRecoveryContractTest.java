package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class MinerStuckRecoveryContractTest {
    @Test
    void watchdogOnlyRunsForActiveAutonomousMinerMovement() throws Exception {
        String navigation = Files.readString(Path.of(
            "src/main/java/dev/civilizations/hytale/MinerNavigationSystem.java"
        ));
        assertTrue(navigation.contains("unitRegistry.getProfession(ref) != Profession.MINER"));
        assertTrue(navigation.contains("activityRegistry.autonomousWorkAllowed(ref)"));
        assertTrue(navigation.contains("if (moveTarget == null)"));
        assertTrue(navigation.contains("STUCK_TIMEOUT_SECONDS = 10.0"));
        assertTrue(navigation.contains("STUCK_MOVEMENT_THRESHOLD_SQUARED"));
        assertTrue(navigation.contains("runtime.stuckRecoveryUsed = true"));
        assertTrue(navigation.contains("stuckRecoveryUsed = false"));
        assertTrue(navigation.contains("MineNavigationPolicy.selectTeleportAnchor("));
        assertTrue(navigation.contains("clearRecoveryAnchor(world, destination.position())"));
        assertTrue(navigation.contains("NPC_STUCK_DETECTED"));
        assertTrue(navigation.contains("NPC_STUCK_TELEPORT"));
        assertTrue(navigation.contains("NPC_STUCK_RECOVERY_FAILED"));
    }
}
