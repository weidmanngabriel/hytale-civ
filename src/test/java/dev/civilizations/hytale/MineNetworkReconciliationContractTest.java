package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class MineNetworkReconciliationContractTest {
    @Test
    void persistedMineIsMergedWithoutDestructiveReset() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/dev/civilizations/hytale/MinerWorkSystem.java"
        ));
        int start = source.indexOf("private RuntimeMinePlan ensureRuntimePlan(");
        int end = source.indexOf("private static boolean matchesPlan(", start);
        String reconciliation = source.substring(start, end);
        assertFalse(reconciliation.contains("tunnelRegistry.removeMine("),
            "new generations must never wipe saved mine progress during restart");
        assertTrue(reconciliation.contains("merged.withTunnel(tunnel.tunnel())"));
        assertTrue(reconciliation.contains("merged.withWorkFront(new MineWorkFront("));
        assertTrue(reconciliation.contains("merged.withRoom(room)"));
        assertTrue(reconciliation.contains("MINE_PLAN_INCOMPATIBLE"));
    }
}
