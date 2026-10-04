package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineTuning;

/** Native Hytale block-physics metadata for placed mine support beams. */
public final class MineSupportPhysics {

    private MineSupportPhysics() {
    }

    /**
     * Marks only the four top beam cells as Hytale block-physics deco blocks.
     * This mirrors the native player-placement treatment for placeable decorative blocks;
     * it is not protection and does not make the blocks unbreakable.
     */
    public static boolean markBeamAsDeco(World world, MineSegment segment, int depth) {
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        for (BlockPosition block : beamBlocks(segment, depth)) {
            WorldChunk chunk = world.getChunkIfLoaded(
                ChunkUtil.indexChunkFromBlock(block.x(), block.z())
            );
            if (chunk == null || chunk.getReference() == null) return false;
            BlockPhysics.markDeco(
                chunkStore,
                chunk.getReference(),
                block.x(),
                block.y(),
                block.z()
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
}
