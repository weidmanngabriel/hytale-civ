package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.TreeSet;
import org.joml.Vector3i;

/**
 * Read-only sapling palette of the native storage marker. One exemplar unlocks its
 * species indefinitely; stack counts never weight the selection and are not consumed.
 */
public final class NativeForesterSaplings {
    private static final String PREFIX = "Plant_Sapling_";
    private final NativeBuildingStorage storage = new NativeBuildingStorage();

    public List<String> available(World world, Vector3i storageMarker) {
        var chest = storage.findContainer(world, storageMarker);
        if (chest == null || chest.getItemContainer() == null) return List.of();
        return available(chest.getItemContainer());
    }

    public static List<String> available(ItemContainer container) {
        Objects.requireNonNull(container, "container");
        var unique = new TreeSet<String>();
        for (short slot=0; slot<container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty() || stack.getQuantity() <= 0) continue;
            String block = stack.getBlockKey();
            if (block != null && block.startsWith(PREFIX)) {
                BlockType type = BlockType.getAssetMap().getAsset(block);
                if (type != null && type.getFarming() != null) unique.add(block);
            }
        }
        return List.copyOf(unique);
    }

    /** Deterministic with a seeded Random; uniform per available species, not per item count. */
    public static String choose(List<String> species, Random random) {
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(random, "random");
        if (species.isEmpty()) return null;
        return species.get(random.nextInt(species.size()));
    }
}
