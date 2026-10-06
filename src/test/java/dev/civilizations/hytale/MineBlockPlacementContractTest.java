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
    void woodcutterRejectsDecorativeBuiltWood() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/WoodcutterWorkSystem.java")
        );

        assertTrue(source.contains("MineBlockPlacement.isDeco(world, position)"));
        assertTrue(source.contains("isNaturalWoodStructureBlock"));
        assertTrue(source.contains("isNaturalTreeTrunk"));
    }
}
