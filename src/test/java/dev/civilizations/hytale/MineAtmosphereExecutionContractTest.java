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

        assertTrue(work.contains("MineNormalTaskSelector.selectWithAging"));
        assertTrue(work.contains("MineNormalTaskSelector.Kind.INFRASTRUCTURE"));
        assertTrue(work.contains("selectMandatoryInfrastructureTask"));
        assertFalse(work.contains("selectInfrastructureTask(world, mine, minePlan, position, workerKey, runtime, false)"));
        int mandatorySelection = work.indexOf(
            "selectMandatoryInfrastructureTask(world, mine, minePlan, position, workerKey, runtime)"
        );
        int normalInfrastructureExecution = work.indexOf(
            "if (currentInfrastructure != null)",
            mandatorySelection
        );
        assertTrue(mandatorySelection >= 0);
        assertTrue(normalInfrastructureExecution > mandatorySelection,
            "priority-10 selection must run before continuing normal infrastructure/decor work");

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
