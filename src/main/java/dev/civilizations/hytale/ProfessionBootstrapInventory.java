package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import dev.civilizations.core.Profession;

/**
 * Temporary development bootstrap for profession-specific NPC items.
 *
 * <p>This is not the future goods sourcing system. It only makes the current
 * profession slices testable until workers can source tools and recipe inputs
 * from buildings, storage, or world goods.
 */
public final class ProfessionBootstrapInventory {

    static final String WHEAT_SEED_ITEM_ID = "Plant_Seeds_Wheat";
    static final int FARMER_SEED_COUNT = 4;
    static final String WOODCUTTER_AXE_ITEM_ID = "Weapon_Axe_Iron";
    static final String MINER_PICKAXE_ITEM_ID = "Tool_Pickaxe_Iron";
    static final String CONSTRUCTION_HAMMER_ITEM_ID = "Tool_Hammer_Iron";

    public void enterProfession(Ref<EntityStore> ref, Profession profession) {
        if (profession == null) return;
        switch (profession) {
            case FARMER -> enterFarmer(ref);
            case WOODCUTTER -> enterToolProfession(ref, WOODCUTTER_AXE_ITEM_ID);
            case MINER -> enterToolProfession(ref, MINER_PICKAXE_ITEM_ID);
            case CONSTRUCTION_WORKER -> enterToolProfession(ref, CONSTRUCTION_HAMMER_ITEM_ID);
            default -> {
            }
        }
    }

    public void leaveProfession(Ref<EntityStore> ref, Profession profession) {
        if (profession == null) return;
        switch (profession) {
            case FARMER -> leaveFarmer(ref);
            case WOODCUTTER -> leaveToolProfession(ref, WOODCUTTER_AXE_ITEM_ID);
            case MINER -> leaveToolProfession(ref, MINER_PICKAXE_ITEM_ID);
            case CONSTRUCTION_WORKER -> leaveToolProfession(ref, CONSTRUCTION_HAMMER_ITEM_ID);
            default -> {
            }
        }
    }

    private void enterFarmer(Ref<EntityStore> ref) {
        ItemStack remaining = new ItemStack(WHEAT_SEED_ITEM_ID, FARMER_SEED_COUNT);
        remaining = addTo(ref, InventoryComponent.Storage.getComponentType(), remaining);
        remaining = addTo(ref, InventoryComponent.Hotbar.getComponentType(), remaining);
        addTo(ref, InventoryComponent.Backpack.getComponentType(), remaining);
    }

    private void leaveFarmer(Ref<EntityStore> ref) {
        removeFrom(ref, InventoryComponent.Storage.getComponentType(), WHEAT_SEED_ITEM_ID, FARMER_SEED_COUNT);
        removeFrom(ref, InventoryComponent.Hotbar.getComponentType(), WHEAT_SEED_ITEM_ID, FARMER_SEED_COUNT);
        removeFrom(ref, InventoryComponent.Backpack.getComponentType(), WHEAT_SEED_ITEM_ID, FARMER_SEED_COUNT);
    }

    private void enterToolProfession(Ref<EntityStore> ref, String itemId) {
        byte slot = InventoryHelper.findHotbarSlotWithItem(ref, ref.getStore(), itemId);
        if (slot < 0) {
            slot = InventoryHelper.findHotbarEmptySlot(ref, ref.getStore());
        }
        if (slot < 0) {
            return;
        }
        InventoryHelper.useItem(ref, itemId, slot, ref.getStore());
    }

    private void leaveToolProfession(Ref<EntityStore> ref, String itemId) {
        removeFrom(ref, InventoryComponent.Hotbar.getComponentType(), itemId, 1);
    }

    private <T extends InventoryComponent> ItemStack addTo(
        Ref<EntityStore> ref,
        com.hypixel.hytale.component.ComponentType<EntityStore, T> type,
        ItemStack remaining
    ) {
        if (remaining == null || remaining.getQuantity() <= 0) return null;
        T component = ref.getStore().getComponent(ref, type);
        if (component == null || component.getInventory() == null) return remaining;

        ItemStackTransaction transaction = component.getInventory().addItemStack(
            remaining,
            true,
            true,
            true
        );
        if (transaction == null) return remaining;
        return transaction.getRemainder();
    }

    private <T extends InventoryComponent> void removeFrom(
        Ref<EntityStore> ref,
        com.hypixel.hytale.component.ComponentType<EntityStore, T> type,
        String itemId,
        int count
    ) {
        T component = ref.getStore().getComponent(ref, type);
        if (component == null || component.getInventory() == null) return;
        ItemContainer inventory = component.getInventory();
        int remaining = count;
        for (short slot = 0; slot < inventory.getCapacity() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (stack == null || !itemId.equals(stack.getItemId())) continue;
            int remove = Math.min(remaining, stack.getQuantity());
            inventory.removeItemStackFromSlot(slot, remove, true, true);
            remaining -= remove;
        }
    }
}
