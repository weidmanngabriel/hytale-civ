package dev.civilizations.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.prefab.event.PrefabPasteEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Converts a native Builder Paste commit into a Civ construction-site commit.
 *
 * <p>The native Paste tool remains responsible for cursor tracking and its ghost
 * preview. For Civ-armed placements the cancellable paste-start event is stopped
 * before BlockSelection.place mutates the world, then the placement service
 * creates the persistent construction preview instead.
 */
public final class CivConstructionPlacementSystem
    extends WorldEventSystem<EntityStore, PrefabPasteEvent> {

    private final PrefabPlacementService placementService;

    public CivConstructionPlacementSystem(PrefabPlacementService placementService) {
        super(PrefabPasteEvent.class);
        this.placementService = placementService;
    }

    @Override
    public void handle(
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer,
        PrefabPasteEvent event
    ) {
        placementService.interceptConstructionPlacement(store, event);
    }
}
