package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinerRoomExecutionContractTest {

    @Test
    void minerRuntimePlansExcavatesAndBuildsRoomsThroughNativeBoundaries() throws Exception {
        String work = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );
        String prefabs = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MineRoomPrefabService.java")
        );

        assertTrue(work.contains("MineRoomPlanner.plan(planned, decisionSink)"));
        assertTrue(work.contains("case ROOM -> observeRoom(task)"));
        assertTrue(work.contains("controller.tick(workerKey, dt, new NativeMinerEngine("));
        assertTrue(work.contains("MineRoomPlanner.MAX_ACTIVE_ROOMS"));
        assertTrue(work.contains("MineRoomPlanner.EXCAVATION_CAPACITY"));
        assertTrue(work.contains("MineRoomPlanner.BUILD_CAPACITY"));
        assertTrue(work.contains("BlockHarvestUtils.performBlockBreak"));
        assertTrue(work.contains("MineRoomPrefabService.placeSection"));

        assertTrue(prefabs.contains("PrefabStore.get().getAssetPrefabFromAnyPack"));
        assertTrue(prefabs.contains("HytalePrefabOrientation.blockSelectionDegrees"));
        assertTrue(prefabs.contains("selection.rotate("));
        assertTrue(prefabs.contains("section.placeNoReturn("));
    }
}
