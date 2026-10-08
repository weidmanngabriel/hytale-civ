package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class MineAnchorDisplayContractTest {
    @Test void anchorsOnlyReadsConfirmedNetworkAnchors() throws Exception {
        String service = Files.readString(Path.of("src/main/java/dev/civilizations/hytale/CivMineDebugService.java"));
        String command = Files.readString(Path.of("src/main/java/dev/civilizations/plugin/CivMineDebugCommand.java"));
        assertTrue(service.contains("for (MineNavigationAnchor anchor : network.navigationAnchors())"));
        assertTrue(service.contains("withinPlayerRange(playerPosition, p.x(), p.y(), p.z())"));
        assertTrue(service.contains("SAFETY ANCHOR "));
        assertTrue(command.contains("new MarkersCommand(service)"));
        assertTrue(command.contains("service.showSafetyAnchors("));
        assertTrue(command.contains("super(\"markers\""));
        assertTrue(command.contains("super(\"anchors\""));
    }
}
