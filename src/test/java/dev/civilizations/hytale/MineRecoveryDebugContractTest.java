package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineRecoveryDebugContractTest {
    @Test
    void recoveryKeepsNpcIdentityAndFinishedMineWork() throws Exception {
        String worker = Files.readString(Path.of(
            "src/main/java/dev/civilizations/hytale/MinerWorkSystem.java"));
        String command = Files.readString(Path.of(
            "src/main/java/dev/civilizations/plugin/CivMineDebugCommand.java"));
        assertTrue(command.contains("new RecoveryAction(service, \"status\", false, false)"));
        assertTrue(command.contains("new RecoveryAction(service, \"workers\", true, false)"));
        assertTrue(command.contains("new RecoveryAction(service, \"fronts\", false, true)"));
        assertTrue(command.contains("new RecoveryAction(service, \"all\", true, true)"));
        assertTrue(worker.contains("front.state() == MineWorkFront.State.COMPLETE"));
        assertTrue(worker.contains("MineWorkFront.State.OPEN"));
        assertTrue(worker.contains("pendingRecovery.remove(workerKey)"));
        assertTrue(worker.contains("front.unavailable = false"));
        assertTrue(worker.contains("navigationFailures.forget(workerKey)"));
        assertTrue(worker.contains("unitRegistry.clearMoveTarget(ref)"));
        assertTrue(worker.contains("runtime.reset(mine.id(), mine.phase())"));
    }
}
