package dev.civilizations.hytale;

import com.hypixel.hytale.math.Axis;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineTuning;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void supportRotationMapsPrefabFrameOntoTunnelFaceInEveryDirection() {
        for (MineDirection direction : MineDirection.values()) {
            MineSegment segment = segment(direction)
                .withProgress(4 * MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS);
            BlockPosition origin = segment.supportOrigin(4);
            Set<BlockPosition> actual = new HashSet<>();
            for (int y = 0; y < MineTuning.TUNNEL_HEIGHT_BLOCKS; y++) {
                for (int width = 0; width < MineTuning.TUNNEL_WIDTH_BLOCKS; width++) {
                    boolean topBeam = y == MineTuning.TUNNEL_HEIGHT_BLOCKS - 1;
                    boolean sidePost = y < MineTuning.TUNNEL_HEIGHT_BLOCKS - 1
                        && (width == 0 || width == MineTuning.TUNNEL_WIDTH_BLOCKS - 1);
                    if (!topBeam && !sidePost) continue;
                    Vector3i local = new Vector3i(0, y, width);
                    Axis.Y.rotate(local, MinerWorkSystem.supportRotationDegrees(direction));
                    actual.add(new BlockPosition(
                        origin.x() + local.x,
                        origin.y() + local.y,
                        origin.z() + local.z
                    ));
                }
            }
            assertEquals(expectedFrame(segment, 4), actual, direction.name());
        }
    }

    @Test
    void dueSupportBlocksAreIgnoredOnlyByTunnelReconciliation() {
        MineSegment segment = segment(MineDirection.EAST)
            .withProgress(4 * MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS);
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int faceStart = 3 * faceSize;
        assertEquals(0, segment.supportsPlaced());
        assertTrue(MinerWorkSystem.isExpectedSupportCell(segment, faceStart, "Wood_Fir_Branch_Long"));
        assertTrue(MinerWorkSystem.isExpectedSupportCell(
            segment,
            faceStart + 3 * MineTuning.TUNNEL_WIDTH_BLOCKS + 2,
            "Wood_Fir_Trunk"
        ));
        assertFalse(MinerWorkSystem.isExpectedSupportCell(
            segment,
            faceStart + MineTuning.TUNNEL_WIDTH_BLOCKS + 1,
            "Wood_Fir_Branch_Long"
        ));
    }

    @Test
    void endpointSupportIsExpectedBeforeFixedJunctionForFourBlockMainTunnel() {
        MineSegment segment = MineSegment.reserved(
            UUID.randomUUID(), UUID.randomUUID(), null,
            new BlockPosition(0, 0, 0), MineDirection.EAST, 4
        ).withProgress(64);
        int faceStart = 3 * 16;
        assertTrue(MinerWorkSystem.isExpectedSupportCell(
            segment, faceStart, "Wood_Fir_Branch_Long"
        ));
    }

    @Test
    void supportIdsMatchRuntimeCaseWithoutTreatingStoneAsSupport() {
        assertTrue(MinerWorkSystem.supportBlockIdMatches("Wood_Fir_Trunk", "wood_fir_trunk"));
        assertTrue(MinerWorkSystem.supportBlockIdMatches("Wood_Fir_Branch_Long", "wood_fir_branch_long"));
        assertFalse(MinerWorkSystem.supportBlockIdMatches("Wood_Fir_Trunk", "Stone"));
    }

    @Test
    void supportBlocksRemainNormalMiningTargets() throws Exception {
        String source = Files.readString(
            Path.of("src", "main", "java", "dev", "civilizations", "hytale", "MinerWorkSystem.java")
        );
        int advanceStart = source.indexOf("private MineSegment advanceOneBlock");
        int supportPlacementStart = source.indexOf("private MineSegment placeDueSupports", advanceStart);
        String advanceOneBlock = source.substring(advanceStart, supportPlacementStart);
        assertTrue(advanceOneBlock.contains("BlockHarvestUtils.performBlockBreak"));
        assertFalse(advanceOneBlock.contains("isExpectedSupportBlock"));
        assertFalse(advanceOneBlock.contains("isExpectedSupportCell"));
    }

    @Test
    void supportRotationUsesDiscreteHytaleDegrees() {
        assertEquals(0, MinerWorkSystem.supportRotationDegrees(MineDirection.EAST));
        assertEquals(90, MinerWorkSystem.supportRotationDegrees(MineDirection.NORTH));
        assertEquals(180, MinerWorkSystem.supportRotationDegrees(MineDirection.WEST));
        assertEquals(270, MinerWorkSystem.supportRotationDegrees(MineDirection.SOUTH));
    }

    private static MineSegment segment(MineDirection direction) {
        return MineSegment.reserved(
            UUID.randomUUID(), UUID.randomUUID(), null,
            new BlockPosition(20, 7, 30), direction, 8
        );
    }

    private static Set<BlockPosition> expectedFrame(MineSegment segment, int depth) {
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int faceStart = (depth - 1) * faceSize;
        Set<BlockPosition> result = new HashSet<>();
        for (int y = 0; y < MineTuning.TUNNEL_HEIGHT_BLOCKS; y++) {
            for (int width = 0; width < MineTuning.TUNNEL_WIDTH_BLOCKS; width++) {
                boolean topBeam = y == MineTuning.TUNNEL_HEIGHT_BLOCKS - 1;
                boolean sidePost = y < MineTuning.TUNNEL_HEIGHT_BLOCKS - 1
                    && (width == 0 || width == MineTuning.TUNNEL_WIDTH_BLOCKS - 1);
                if (!topBeam && !sidePost) continue;
                result.add(segment.blockAtIndex(faceStart + y * MineTuning.TUNNEL_WIDTH_BLOCKS + width));
            }
        }
        return result;
    }
}
