package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinerNativePathContractTest {

    @Test
    void keepsNormalAndMinerMovementSlotsSeparated() throws Exception {
        String role = Files.readString(
            Path.of("asset-pack/Server/NPC/Roles/Intelligent/Passive/Civ_Inhabitant.json")
        );
        String registry = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/CivUnitRegistry.java")
        );

        int normalSlot = role.indexOf("\"Slot\": \"CivMoveTarget\"");
        int minerSlot = role.indexOf("\"Slot\": \"CivMinerMoveTarget\"");
        int strictMinerPath = role.indexOf("\"UseBestPath\": false");

        assertTrue(normalSlot >= 0, "ordinary movement slot must remain in the inhabitant role");
        assertTrue(minerSlot > normalSlot, "miner movement slot must be allocated after ordinary slot");
        assertTrue(strictMinerPath > minerSlot, "miner Seek must disable best-effort partial paths");
        assertTrue(
            registry.contains("CIV_MOVE_POSITION_SLOT = 0"),
            "ordinary Java movement slot must remain aligned with role slot 0"
        );
        assertTrue(
            registry.contains("CIV_MINER_MOVE_POSITION_SLOT = 1"),
            "miner Java movement slot must remain aligned with role slot 1"
        );
    }
}
