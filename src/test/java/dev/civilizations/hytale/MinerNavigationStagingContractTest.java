package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineTuning;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinerNavigationStagingContractTest {

    @Test
    void connectorGateComesBeforeSegmentResolutionAndResetsAfterManualMove() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
        );

        int connectorGate = source.indexOf("if (!runtime.reachedConnector)");
        int segmentResolution = source.indexOf("MineSegment segment = resolveSegment");

        assertTrue(connectorGate >= 0, "miner must gate autonomous work on the tunnel connector");
        assertTrue(segmentResolution > connectorGate,
            "segment selection must happen only after the connector gate");
        assertTrue(source.contains("reachedConnector = false;"),
            "manual interruption/reset must require the connector again");
    }

    @Test
    void turnStagingTargetLiesInsideTheSharedFourByFourJunction() {
        MineSegment parent = MineSegment.reserved(
            UUID.fromString("00000000-0000-0000-0000-000000000701"),
            UUID.fromString("00000000-0000-0000-0000-000000000702"),
            null,
            new BlockPosition(10, 20, 30),
            MineDirection.NORTH,
            8
        );
        MineSegment child = MineSegment.reserved(
            UUID.fromString("00000000-0000-0000-0000-000000000703"),
            parent.mineId(),
            parent.id(),
            parent.nextStart(MineDirection.EAST),
            MineDirection.EAST,
            7
        );

        Vector3d target = MinerWorkSystem.turnStagingTarget(child);

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        for (int depth = 0; depth < MineTuning.TUNNEL_WIDTH_BLOCKS; depth++) {
            for (int width = 0; width < MineTuning.TUNNEL_WIDTH_BLOCKS; width++) {
                BlockPosition block = child.blockAtIndex(depth * faceSize + width);
                minX = Math.min(minX, block.x());
                maxX = Math.max(maxX, block.x() + 1.0);
                minZ = Math.min(minZ, block.z());
                maxZ = Math.max(maxZ, block.z() + 1.0);
            }
        }

        assertTrue(target.x > minX && target.x < maxX);
        assertTrue(target.z > minZ && target.z < maxZ);
        assertTrue(Math.abs(target.y - child.start().y()) < 1.0e-9);
    }
}
