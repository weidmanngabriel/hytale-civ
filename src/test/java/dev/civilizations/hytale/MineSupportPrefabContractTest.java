package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineSupportPrefabContractTest {

    @Test
    void runtimePrefabKeyMatchesPackRelativeJsonPathExactly() throws Exception {
        Field field = MinerWorkSystem.class.getDeclaredField("SUPPORT_PREFAB_KEY");
        field.setAccessible(true);
        String runtimeKey = (String) field.get(null);

        Path prefabsRoot = Path.of("asset-pack", "Server", "Prefabs");
        Path supportPrefab = prefabsRoot.resolve(runtimeKey);

        assertEquals(
            Path.of("Civilizations", "Mine", "Mine_Support_01.prefab.json"),
            prefabsRoot.relativize(supportPrefab)
        );
        assertTrue(Files.isRegularFile(supportPrefab), supportPrefab.toString());
    }
}
