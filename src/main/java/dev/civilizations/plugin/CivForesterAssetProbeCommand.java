package dev.civilizations.plugin;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import dev.civilizations.hytale.NativeForesterSaplings;
import java.util.Locale;

/**
 * Read-only pinned-Hytale native asset discovery before enabling NPC sapling placement.
 * It deliberately does not fake sapling growth or mutate a real player world.
 */
final class CivForesterAssetProbeCommand extends CommandBase {
    CivForesterAssetProbeCommand() {
        super("civforesterassetprobe", "Inspect native Hytale saplings for the forestry integration.");
        requireNoPermission();
    }

    @Override protected void executeSync(CommandContext context) {
        System.out.println("CIV_FORESTER_ASSET_PROBE_STARTED");
        var assets = BlockType.getAssetMap().getAssetMap();
        var candidates = assets.entrySet().stream()
            .filter(entry -> {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                return key.contains("sapling") || key.contains("seedling");
            })
            .sorted(java.util.Map.Entry.comparingByKey())
            .toList();
        for (var entry : candidates) {
            BlockType block = entry.getValue();
            System.out.println("CIV_FORESTER_ASSET key=" + entry.getKey()
                + " farming=" + (block.getFarming() != null)
                + " indexed=" + BlockType.getAssetMap().getIndex(entry.getKey()));
        }
        SimpleItemContainer inventory = new SimpleItemContainer((short) 5);
        String[] sample = {"Plant_Sapling_Oak", "Plant_Sapling_Birch", "Plant_Sapling_Spruce"};
        int[] amounts = {1, 5, 20};
        for(int i = 0; i < sample.length; i++) {
            ItemStack stack = new ItemStack(sample[i], amounts[i]);
            System.out.println("CIV_FORESTER_ITEM_TEST item=" + stack.getItemId()
                + " blockKey=" + stack.getBlockKey() + " valid=" + stack.isValid());
            inventory.setItemStackForSlot((short) i, stack);
        }
        var species = NativeForesterSaplings.available(inventory);
        System.out.println("CIV_FORESTER_CHEST_PALETTE=" + species
            + " amounts=" + inventory.getItemStack((short)0).getQuantity() + ","
            + inventory.getItemStack((short)1).getQuantity() + ","
            + inventory.getItemStack((short)2).getQuantity());
        if (species.size() != 3
            || inventory.getItemStack((short)0).getQuantity() != 1
            || inventory.getItemStack((short)1).getQuantity() != 5
            || inventory.getItemStack((short)2).getQuantity() != 20) {
            System.out.println("CIV_FORESTER_ASSET_PROBE_FAIL native chest species lookup mismatch");
        }
        System.out.println("CIV_FORESTER_ASSET_CANDIDATES=" + candidates.size());
        if (candidates.isEmpty()) {
            System.out.println("CIV_FORESTER_ASSET_PROBE_FAIL no sapling block assets registered");
        } else {
            System.out.println("CIV_FORESTER_ASSET_PROBE_PASS");
        }
        HytaleServer.get().shutdownServer();
    }
}
