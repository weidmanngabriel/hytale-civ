package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.joml.Vector3i;

/**
 * Small Hytale boundary for finding a native block container near a semantic
 * prefab storage marker and inserting physical goods into it.
 */
public final class NativeBuildingStorage {

    private static final int SEARCH_RADIUS = 2;

    public boolean tryStore(World world, Vector3i marker, ItemStack stack) {
        if (world == null || marker == null || stack == null || stack.isEmpty()) return false;
        ItemContainerBlock block = findContainer(world, marker);
        if (block == null || block.getItemContainer() == null) return false;
        return block.getItemContainer().addItemStack(stack, true, true, true).succeeded();
    }

    private ItemContainerBlock findContainer(World world, Vector3i marker) {
        ItemContainerBlock best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int x = marker.x - SEARCH_RADIUS; x <= marker.x + SEARCH_RADIUS; x++) {
            for (int y = marker.y - 1; y <= marker.y + 1; y++) {
                for (int z = marker.z - SEARCH_RADIUS; z <= marker.z + SEARCH_RADIUS; z++) {
                    WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
                    if (chunk == null) continue;
                    Ref<ChunkStore> ref = chunk.getBlockComponentEntity(x, y, z);
                    if (ref == null || !ref.isValid()) continue;
                    ItemContainerBlock candidate = ref.getStore().getComponent(
                        ref, ItemContainerBlock.getComponentType()
                    );
                    if (candidate == null) continue;
                    int distance = Math.abs(x - marker.x) + Math.abs(y - marker.y) + Math.abs(z - marker.z);
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }
}
