package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlocksUtil;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineTuning;
import org.joml.Vector3i;

/** Native Hytale placement semantics for mine support beams. */
public final class MineSupportPhysics {

    private static final String SUPPORT_BEAM_BLOCK = "Wood_Fir_Trunk";
    private static final int SUPPORT_BEAM_PREFAB_ROTATION = 4;
    private static final int PLAYER_PLACE_FLAGS = 256;

    private MineSupportPhysics() {
    }

    /**
     * Places the four top beam cells through the same native block-operation path used by
     * normal player placement, then applies the same deco and connected-block follow-up.
     * The method name is retained for compatibility with the existing mine-support flow.
     */
    public static boolean markBeamAsDeco(World world, MineSegment segment, int depth) {
        BlockType beamType = BlockType.getAssetMap().getAsset(SUPPORT_BEAM_BLOCK);
        if (beamType == null) return false;
        int blockIndex = BlockType.getAssetMap().getIndex(SUPPORT_BEAM_BLOCK);
        if (blockIndex <= 0) return false;

        RotationTuple rotation = RotationTuple.get(SUPPORT_BEAM_PREFAB_ROTATION)
            .composeOnAxis(Axis.Y, Rotation.ofDegrees(rotationDegrees(segment.direction())));
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkComponents = chunkStore.getStore();

        for (BlockPosition block : beamBlocks(segment, depth)) {
            WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(block.x(), block.z()));
            if (chunk == null || chunk.getReference() == null) return false;
            Ref<ChunkStore> chunkRef = chunk.getReference();
            BlockSection blockSection = chunk.getBlockChunk().getSectionAtBlockY(block.y());
            if (blockSection == null) return false;

            boolean placed = BlockOperations.setBlock(
                chunkStore,
                chunkRef,
                block.x(),
                block.y(),
                block.z(),
                blockIndex,
                beamType,
                rotation.index(),
                0,
                PLAYER_PLACE_FLAGS
            );
            if (!placed) return false;

            if (beamType.canBePlacedAsDeco()) {
                BlockPhysics.markDeco(
                    chunkComponents,
                    chunkRef,
                    block.x(),
                    block.y(),
                    block.z()
                );
            }

            Vector3i target = new Vector3i(block.x(), block.y(), block.z());
            Vector3i placedAgainst = new Vector3i(block.x(), block.y() - 1, block.z());
            ConnectedBlocksUtil.setConnectedBlockAndNotifyNeighbors(
                chunkStore,
                blockIndex,
                rotation,
                placedAgainst,
                target,
                chunkRef,
                blockSection
            );
        }
        return true;
    }

    public static boolean beamIsDeco(World world, MineSegment segment, int depth) {
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        for (BlockPosition block : beamBlocks(segment, depth)) {
            WorldChunk chunk = world.getChunkIfLoaded(
                ChunkUtil.indexChunkFromBlock(block.x(), block.z())
            );
            if (chunk == null || chunk.getReference() == null) return false;
            BlockPhysics physics = chunkStore.getComponent(
                chunk.getReference(),
                BlockPhysics.getComponentType()
            );
            if (physics == null || !physics.isDeco(block.x(), block.y(), block.z())) return false;
        }
        return true;
    }

    public static java.util.List<BlockPosition> beamBlocks(MineSegment segment, int depth) {
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int faceStart = (depth - 1) * faceSize;
        int topRowOffset = (MineTuning.TUNNEL_HEIGHT_BLOCKS - 1) * MineTuning.TUNNEL_WIDTH_BLOCKS;
        java.util.ArrayList<BlockPosition> result = new java.util.ArrayList<>(MineTuning.TUNNEL_WIDTH_BLOCKS);
        for (int width = 0; width < MineTuning.TUNNEL_WIDTH_BLOCKS; width++) {
            result.add(segment.blockAtIndex(faceStart + topRowOffset + width));
        }
        return java.util.List.copyOf(result);
    }

    private static int rotationDegrees(MineDirection direction) {
        return switch (direction) {
            case EAST -> 0;
            case NORTH -> 90;
            case WEST -> 180;
            case SOUTH -> 270;
        };
    }
}
