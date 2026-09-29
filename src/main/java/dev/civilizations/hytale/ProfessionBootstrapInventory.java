package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
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
        ItemContainer inventory = preferredInventory(ref);
        if (inventory != null) {
            inventory.addItemStack(new ItemStack(WHEAT_SEED_ITEM_ID, FARMER_SEED_COUNT), true, true, true);
        }
    }

    public void leaveFarmer(Ref<EntityStore> ref) {
        removeFrom(ref, InventoryComponent.Storage.getComponentType());
        removeFrom(ref, InventoryComponent.Hotbar.getComponentType());
        removeFrom(ref, InventoryComponent.Backpack.getComponentType());
    }

    private ItemContainer preferredInventory(Ref<EntityStore> ref) {
        InventoryComponent component = ref.getStore().getComponent(ref, InventoryComponent.Storage.getComponentType());
        if (component == null) {
            component = ref.getStore().getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        }
        if (component == null) {
            component = ref.getStore().getComponent(ref, InventoryComponent.Backpack.getComponentType());
        }
        return component == null ? null : component.getInventory();
    }

    private <T extends InventoryComponent> void removeFrom(
        Ref<EntityStore> ref,
        com.hypixel.hytale.component.ComponentType<EntityStore, T> type
    ) {
        T component = ref.getStore().getComponent(ref, type);
        if (component == null || component.getInventory() == null) return;
        component.getInventory().removeItemStack(
            new ItemStack(WHEAT_SEED_ITEM_ID, FARMER_SEED_COUNT),
            true,
            true
        );
    }
}
