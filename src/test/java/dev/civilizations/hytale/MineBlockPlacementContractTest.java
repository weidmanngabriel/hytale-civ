package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MineBlockPlacementContractTest {

    @Test
    void infrastructureUsesVerifiedPlayerLikePlacementPipeline() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MineBlockPlacement.java")
        );

        int blockOperation = source.indexOf("BlockOperations.setBlock(");
        int deco = source.indexOf("BlockPhysics.markDeco(");
        int connected = source.indexOf("ConnectedBlocksUtil.setConnectedBlockAndNotifyNeighbors(");

        assertTrue(blockOperation >= 0, "infrastructure must use BlockOperations.setBlock");
        assertTrue(deco > blockOperation, "Deco metadata must be applied after block placement");
        assertTrue(connected > deco, "connected-block notifications must follow Deco placement");
        assertTrue(source.contains("PLAYER_PLACE_FLAGS = 256"));
    }

    @Test
    void placementReportsConcreteFailureReasonsForDiagnostics() throws Exception {
        String placement = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MineBlockPlacement.java")
        );
        String miner = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );

        assertTrue(placement.contains("PlacementResult placeDetailed("));
        assertTrue(placement.contains("BLOCK_ASSET_NOT_FOUND"));
        assertTrue(placement.contains("CHUNK_NOT_LOADED"));
        assertTrue(placement.contains("TARGET_OCCUPIED"));
        assertTrue(placement.contains("BLOCK_SECTION_UNAVAILABLE"));
        assertTrue(placement.contains("SET_BLOCK_REJECTED"));

        assertTrue(miner.contains("\"BLOCK_PLACEMENT_FAILED\""));
        assertTrue(miner.contains("\"BLOCK_PLACEMENT_FAILURE_REPEATED\""));
        assertTrue(miner.contains("\"PLACEMENT_RETRY_LOOP_DETECTED\""));
        assertTrue(miner.contains("\"workTarget\", formatTarget("));
        assertTrue(miner.contains("\"minerPosition\", formatTarget(position)"));
        assertTrue(miner.contains("attempts == 3"));
    }

    @Test
    void woodcutterRejectsDecorativeBuiltWood() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/WoodcutterWorkSystem.java")
        );

        assertTrue(source.contains("MineBlockPlacement.isDeco(world, position)"));
        assertTrue(source.contains("isNaturalWoodStructureBlock"));
        assertTrue(source.contains("isNaturalTreeTrunk"));
    }
}
