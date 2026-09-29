package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Temporary development bootstrap for profession-specific NPC items.
 *
 * <p>This is not the future goods sourcing system. It only makes the current
 * farming slice testable until workers can source recipe inputs from buildings,
 * storage, or world goods.
 */
public final class ProfessionBootstrapInventory {

    static final String WHEAT_SEED_ITEM_ID = "Plant_Seeds_Wheat";
    static final int FARMER_SEED_COUNT = 4;

    public void enterFarmer(Ref<EntityStore> ref) {
        int remaining = FARMER_SEED_COUNT;
        remaining = addTo(ref, InventoryComponent.Storage.getComponentType(), remaining);
        remaining = addTo(ref, InventoryComponent.Hotbar.getComponentType(), remaining);
        addTo(ref, InventoryComponent.Backpack.getComponentType(), remaining);
    }

    public void leaveFarmer(Ref<EntityStore> ref) {
        removeFrom(ref, InventoryComponent.Storage.getComponentType());
        removeFrom(ref, InventoryComponent.Hotbar.getComponentType());
        removeFrom(ref, InventoryComponent.Backpack.getComponentType());
    }

    private <T extends InventoryComponent> int addTo(
        Ref<EntityStore> ref,
        com.hypixel.hytale.component.ComponentType<EntityStore, T> type,
        int remaining
    ) {
        if (remaining <= 0) return 0;
        T component = ref.getStore().getComponent(ref, type);
        if (component == null || component.getInventory() == null) return remaining;

        ItemContainer inventory = component.getInventory();
        while (remaining > 0) {
            ItemStackTransaction transaction = inventory.addItemStack(
                new ItemStack(WHEAT_SEED_ITEM_ID, 1),
                true,
                true,
                true
            );
            if (transaction == null || !transaction.succeeded()) {
                break;
            }
            ItemStack remainder = transaction.getRemainder();
            if (remainder != null && remainder.getQuantity() > 0) {
                break;
            }
            remaining--;
        }
        return remaining;
    }

    private <T extends InventoryComponent> void removeFrom(
        Ref<EntityStore> ref,
        com.hypixel.hytale.component.ComponentType<EntityStore, T> type
    ) {
        T component = ref.getStore().getComponent(ref, type);
        if (component == null || component.getInventory() == null) return;
        ItemContainer inventory = component.getInventory();
        int remaining = FARMER_SEED_COUNT;
        for (short slot = 0; slot < inventory.getCapacity() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (stack == null || !WHEAT_SEED_ITEM_ID.equals(stack.getItemId())) continue;
            int remove = Math.min(remaining, stack.getQuantity());
            inventory.removeItemStackFromSlot(slot, remove, true, true);
            remaining -= remove;
        }
    }
}
