package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlocksUtil;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.civilizations.core.BlockPosition;
import org.joml.Vector3i;

import java.util.Arrays;
import java.util.Locale;

/**
 * Reuses the player-like block-operation path that previously stabilized mine support beams.
 *
 * <p>WorldChunk#setBlock alone does not apply the placed-block physics metadata needed by fir
 * trunk/branch blocks. This helper performs BlockOperations placement, optional Deco marking and
 * connected-block neighbour notification in the same order verified by the former V0 miner.</p>
 */
public final class MineBlockPlacement {

    private static final int PLAYER_PLACE_FLAGS = 256;

    private MineBlockPlacement() {
    }

    public static boolean place(
        World world,
        BlockPosition position,
        String blockId,
        RotationTuple rotation,
        BlockPosition placedAgainst,
        boolean markDeco
    ) {
        return placeDetailed(world, position, blockId, rotation, placedAgainst, markDeco).success();
    }

    public static PlacementResult placeDetailed(
        World world,
        BlockPosition position,
        String blockId,
        RotationTuple rotation,
        BlockPosition placedAgainst,
        boolean markDeco
    ) {
        if (world == null || position == null || blockId == null || rotation == null) {
            return PlacementResult.failed(FailureReason.INVALID_INPUT);
        }

        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) {
            return PlacementResult.failed(FailureReason.BLOCK_ASSET_NOT_FOUND);
        }
        int blockIndex = BlockType.getAssetMap().getIndex(blockId);
        if (blockIndex <= 0) {
            return PlacementResult.failed(FailureReason.INVALID_BLOCK_INDEX);
        }

        WorldChunk chunk = world.getChunkIfLoaded(
            ChunkUtil.indexChunkFromBlock(position.x(), position.z())
        );
        if (chunk == null || chunk.getReference() == null) {
            return PlacementResult.failed(FailureReason.CHUNK_NOT_LOADED);
        }

        BlockType existing = chunk.getBlockType(position.x(), position.y(), position.z());
        if (existing != null && existing != BlockType.EMPTY
            && existing.getId() != null && existing.getId().equals(blockId)) {
            if (markDeco && blockType.canBePlacedAsDeco()) markDeco(world, position);
            return PlacementResult.success(Outcome.ALREADY_PRESENT);
        }
        if (existing != null && existing != BlockType.EMPTY
            && existing.getMaterial() != com.hypixel.hytale.protocol.BlockMaterial.Empty) {
            return new PlacementResult(
                false,
                null,
                FailureReason.TARGET_OCCUPIED,
                existing.getId()
            );
        }

        BlockSection blockSection = chunk.getBlockChunk().getSectionAtBlockY(position.y());
        if (blockSection == null) {
            return PlacementResult.failed(FailureReason.BLOCK_SECTION_UNAVAILABLE);
        }

        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkComponents = chunkStore.getStore();
        Ref<ChunkStore> chunkRef = chunk.getReference();

        boolean placed = BlockOperations.setBlock(
            chunkStore,
            chunkRef,
            position.x(),
            position.y(),
            position.z(),
            blockIndex,
            blockType,
            rotation.index(),
            0,
            PLAYER_PLACE_FLAGS
        );
        if (!placed) {
            return PlacementResult.failed(FailureReason.SET_BLOCK_REJECTED);
        }

        if (markDeco && blockType.canBePlacedAsDeco()) {
            BlockPhysics.markDeco(
                chunkComponents,
                chunkRef,
                position.x(),
                position.y(),
                position.z()
            );
        }

        BlockPosition support = placedAgainst == null
            ? new BlockPosition(position.x(), position.y() - 1, position.z())
            : placedAgainst;
        ConnectedBlocksUtil.setConnectedBlockAndNotifyNeighbors(
            chunkStore,
            blockIndex,
            rotation,
            new Vector3i(support.x(), support.y(), support.z()),
            new Vector3i(position.x(), position.y(), position.z()),
            chunkRef,
            blockSection
        );
        return PlacementResult.success(Outcome.PLACED);
    }

    public enum Outcome {
        PLACED,
        ALREADY_PRESENT
    }

    public enum FailureReason {
        INVALID_INPUT,
        BLOCK_ASSET_NOT_FOUND,
        INVALID_BLOCK_INDEX,
        CHUNK_NOT_LOADED,
        TARGET_OCCUPIED,
        BLOCK_SECTION_UNAVAILABLE,
        SET_BLOCK_REJECTED
    }

    public record PlacementResult(
        boolean success,
        Outcome outcome,
        FailureReason failureReason,
        String existingBlockId
    ) {
        public static PlacementResult success(Outcome outcome) {
            return new PlacementResult(true, outcome, null, null);
        }

        public static PlacementResult failed(FailureReason reason) {
            return new PlacementResult(false, null, reason, null);
        }
    }

    public static boolean isDeco(World world, BlockPosition position) {
        if (world == null || position == null) return false;
        WorldChunk chunk = world.getChunkIfLoaded(
            ChunkUtil.indexChunkFromBlock(position.x(), position.z())
        );
        if (chunk == null || chunk.getReference() == null) return false;
        BlockPhysics physics = world.getChunkStore().getStore().getComponent(
            chunk.getReference(),
            BlockPhysics.getComponentType()
        );
        return physics != null && physics.isDeco(position.x(), position.y(), position.z());
    }

    private static void markDeco(World world, BlockPosition position) {
        WorldChunk chunk = world.getChunkIfLoaded(
            ChunkUtil.indexChunkFromBlock(position.x(), position.z())
        );
        if (chunk == null || chunk.getReference() == null) return;
        BlockPhysics.markDeco(
            world.getChunkStore().getStore(),
            chunk.getReference(),
            position.x(),
            position.y(),
            position.z()
        );
    }

    /**
     * Resolves a block asset without hard-coding display-name punctuation from Assets.zip.
     * Exact IDs are preferred; otherwise all requested fragments must occur in the loaded ID.
     */
    public static String resolveAsset(String[] preferredIds, String... requiredFragments) {
        if (preferredIds != null) {
            for (String preferred : preferredIds) {
                if (preferred != null && BlockType.getAssetMap().getAsset(preferred) != null) {
                    return preferred;
                }
            }
        }

        String[] fragments = Arrays.stream(requiredFragments == null ? new String[0] : requiredFragments)
            .filter(fragment -> fragment != null && !fragment.isBlank())
            .map(fragment -> fragment.toLowerCase(Locale.ROOT))
            .toArray(String[]::new);

        int max = BlockType.getAssetMap().getNextIndex();
        for (int index = 1; index < max; index++) {
            BlockType candidate = BlockType.getAssetMap().getAsset(index);
            if (candidate == null || candidate.getId() == null) continue;
            String id = candidate.getId().toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String fragment : fragments) {
                if (!id.contains(fragment)) {
                    matches = false;
                    break;
                }
            }
            if (matches) return candidate.getId();
        }
        return null;
    }
}
