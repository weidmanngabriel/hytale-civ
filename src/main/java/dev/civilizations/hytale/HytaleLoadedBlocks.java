package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Read-only loaded-world access via 0.7.2 chunk section ECS components.
 * This helper does not load or create sections, or mutate the world.
 * Callers must use it on the appropriate world thread.
 */
public final class HytaleLoadedBlocks {
    private HytaleLoadedBlocks() {}

    /** Loaded column, without requesting any new chunk load. */
    public static WorldChunk loadedChunk(World world, long index) {
        return world.getChunkStore().getChunkComponent(index, WorldChunk.getComponentType());
    }

    private static Ref<ChunkStore> sectionRef(World world, int x, int y, int z) {
        return world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
    }

    public static BlockSection blockSection(World world, int x, int y, int z) {
        Ref<ChunkStore> ref = sectionRef(world, x, y, z);
        if (ref == null || !ref.isValid()) return null;
        return world.getChunkStore().getStore().getComponent(ref, BlockSection.getComponentType());
    }

    public static Integer blockId(World world, int x, int y, int z) {
        BlockSection section = blockSection(world, x, y, z);
        return section == null ? null : section.get(x, y, z);
    }

    public static BlockType blockType(World world, int x, int y, int z) {
        Integer id = blockId(world, x, y, z);
        return id == null ? null : BlockType.getAssetMap().getAssetOrDefault(id, BlockType.UNKNOWN);
    }

    public static Integer filler(World world, int x, int y, int z) {
        BlockSection section = blockSection(world, x, y, z);
        return section == null ? null : section.getFiller(x, y, z);
    }

    public static Integer supportValue(World world, int x, int y, int z) {
        Ref<ChunkStore> ref = sectionRef(world, x, y, z);
        if (ref == null || !ref.isValid()) return null;
        BlockPhysics physics = world.getChunkStore().getStore().getComponent(ref, BlockPhysics.getComponentType());
        return physics == null ? 0 : physics.get(x, y, z);
    }

    public static Integer rotationIndex(World world, int x, int y, int z) {
        BlockSection section = blockSection(world, x, y, z);
        return section == null ? null : section.getRotationIndex(x, y, z);
    }

    public static Integer fluidLevel(World world, int x, int y, int z) {
        Ref<ChunkStore> ref = sectionRef(world, x, y, z);
        if (ref == null || !ref.isValid()) return null;
        FluidSection fluids = world.getChunkStore().getStore().getComponent(ref, FluidSection.getComponentType());
        return fluids == null ? 0 : Byte.toUnsignedInt(fluids.getFluidLevel(x, y, z));
    }

    public static Integer fluidId(World world, int x, int y, int z) {
        Ref<ChunkStore> ref = sectionRef(world, x, y, z);
        if (ref == null || !ref.isValid()) return null;
        FluidSection fluids = world.getChunkStore().getStore().getComponent(ref, FluidSection.getComponentType());
        return fluids == null ? Fluid.EMPTY_ID : fluids.getFluidId(x, y, z);
    }
}
