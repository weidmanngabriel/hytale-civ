package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Refreshes selected building/site information in infrequent native delayed ECS sessions. */
public final class CivSelectedBuildingHudSystem extends DelayedEntitySystem<EntityStore> {

    private static final float REFRESH_INTERVAL_SECONDS = 0.75f;
    private final CivSelectedBuildingHudController hudController;

    public CivSelectedBuildingHudSystem(CivSelectedBuildingHudController hudController) {
        super(REFRESH_INTERVAL_SECONDS);
        this.hudController = hudController;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(
        float dt,
        int index,
        ArchetypeChunk<EntityStore> archetypeChunk,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player != null) hudController.refresh(player);
    }
}
