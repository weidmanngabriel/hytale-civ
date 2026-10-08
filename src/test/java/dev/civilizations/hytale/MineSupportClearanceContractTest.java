package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineSupportClearanceContractTest {
    @Test
    void supportsRequireBufferedWalkingClearanceAndShallowFoundation() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/dev/civilizations/hytale/MineInfrastructurePlacementResolver.java"
        ));
        assertTrue(source.contains("private static final int SUPPORT_VERTICAL_SCAN = 3;"));
        assertTrue(source.contains("supportClearanceSafe(geometry, resolved)"));
        assertTrue(source.contains("core.contains(new BlockPosition(at.x() + dx, at.y(), at.z() + dz))"));
        assertTrue(source.contains("for (int dx = -1; dx <= 1; dx += 2)"));
        assertTrue(source.contains("for (int dz = -1; dz <= 1; dz += 2)"));
        assertTrue(source.contains("if (!edge)"));
    }
}
