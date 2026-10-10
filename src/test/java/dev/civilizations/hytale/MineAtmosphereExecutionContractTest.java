package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineAtmosphereExecutionContractTest {

    @Test
    void runtimeUsesUnifiedAgingAndSafeNativeDecorationPlacement() throws Exception {
        String work = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );
        String resolver = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MineInfrastructurePlacementResolver.java")
        );

        String core = Files.readString(Path.of("src/main/java/dev/civilizations/core/MinerWorkController.java"));
        assertTrue(core.contains("MineNormalTaskSelector.selectWithAging"));
        assertTrue(work.contains("MineNormalTaskSelector.Kind.INFRASTRUCTURE"));
        assertTrue(work.contains("controller.tick(workerKey, dt, new NativeMinerEngine("));
        assertFalse(work.contains("selectMandatoryInfrastructureTask"));
        assertTrue(core.indexOf("if (s.task != null)") < core.indexOf("tasks.stream().filter(Task::mandatory)"),
            "priority-10 work must wait until the current work assignment has ended");

        assertTrue(resolver.contains("case PLACE_DECORATION"));
        assertTrue(resolver.contains("slice.navigationCoreBlocks().contains"));
        assertTrue(resolver.contains("Math.abs(lateral) <= 1"));
        assertTrue(resolver.contains("HANGING_LANTERN"));
        assertTrue(resolver.contains("HANGING_CHAIN"));
        assertTrue(resolver.contains("String beamBlock = FIR_TRUNK;"));
        assertTrue(resolver.contains("Furniture_Tavern_Barrel"));
        assertTrue(resolver.contains("Furniture_Ancient_Barrel"));
        assertTrue(resolver.contains("Furniture_Crude_Chest_Small"));
        assertTrue(resolver.contains("Deco_Iron_Chain_Small"));
        assertTrue(resolver.contains("Deco_Lantern"));
        assertTrue(resolver.contains("Ore_Iron_Stone"));
        assertTrue(resolver.contains("Ore_Copper_Stone"));
        assertTrue(resolver.contains("Ore_Gold_Stone"));
        assertFalse(resolver.contains("Tool_Rack"));
        assertTrue(resolver.contains("MineBlockPlacement.resolveAsset"));
    }
}
