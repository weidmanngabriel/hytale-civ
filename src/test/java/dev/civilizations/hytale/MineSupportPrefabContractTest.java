package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineSupportPrefabContractTest {

    @Test
    void supportPrefabRemainsAvailableForDedicatedInfrastructureWork() {
        Path supportPrefab = Path.of(
            "asset-pack", "Server", "Prefabs", "Civilizations", "Mine", "Mine_Support_01.prefab.json"
        );
        assertTrue(Files.isRegularFile(supportPrefab), supportPrefab.toString());
    }

    @Test
    void supportPrefabUsesAnchorAlignedFourWidePlane() throws Exception {
        String prefab = Files.readString(
            Path.of("asset-pack", "Server", "Prefabs", "Civilizations", "Mine", "Mine_Support_01.prefab.json")
        );
        String compactPrefab = prefab.replaceAll("\\s+", "");
        assertTrue(compactPrefab.contains("\"anchorX\":0"));
        assertTrue(compactPrefab.contains("\"anchorY\":0"));
        assertTrue(compactPrefab.contains("\"anchorZ\":0"));
        assertFalse(compactPrefab.contains("\"z\":-1"));
        assertTrue(compactPrefab.contains("\"z\":0"));
        assertTrue(compactPrefab.contains("\"z\":3"));
        assertTrue(compactPrefab.contains("\"fluids\":[]"));
    }
}
