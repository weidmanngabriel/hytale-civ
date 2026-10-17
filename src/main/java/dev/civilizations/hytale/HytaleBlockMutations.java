package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Native 0.7.2 block mutations through loaded chunk-section references.
 * Must run on the world thread; does not load missing world sections.
 */
public final class HytaleBlockMutations {
    private HytaleBlockMutations() {}

    public static boolean setBlock(World world, int x, int y, int z, int blockId) {
        if (world == null) return false;
        Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) return false;
        BlockType type = BlockType.getAssetMap().getAssetOrDefault(blockId, BlockType.UNKNOWN);
        if (type == null || type == BlockType.UNKNOWN) return false;

        Integer existing = HytaleLoadedBlocks.blockId(world, x, y, z);
        if (existing == null) return false;
        if (existing == blockId) return true;

        // BlockOperations handles native component, heightmap and neighboring block updates.
        boolean changed = BlockOperations.setBlock(
            world.getChunkStore(), section,
            x, y, z, blockId, type, 0, 0, 0
        );
        Integer after = HytaleLoadedBlocks.blockId(world, x, y, z);
        return changed && after != null && after == blockId;
    }

    public static boolean setBlock(World world, int x, int y, int z, BlockType type) {
        if (type == null || type.getId() == null) return false;
        int id = BlockType.getAssetMap().getIndex(type.getId());
        if (id < 0) return false;
        return setBlock(world, x, y, z, id);
    }
}
