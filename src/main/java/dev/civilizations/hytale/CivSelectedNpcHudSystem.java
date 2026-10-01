package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Periodically refreshes the compact NPC HUD so changing activity is reflected live. */
public final class CivSelectedNpcHudSystem extends EntityTickingSystem<EntityStore> {

    private static final float REFRESH_INTERVAL_SECONDS = 0.5f;

    private final CivSelectedNpcHudController hudController;
    private final Map<UUID, Float> elapsedByPlayer = new HashMap<>();

    public CivSelectedNpcHudSystem(CivSelectedNpcHudController hudController) {
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
        if (player == null || player.getPlayerRef() == null) {
            return;
        }

        UUID playerId = player.getPlayerRef().getUuid();
        float elapsed = elapsedByPlayer.getOrDefault(playerId, 0f) + dt;
        if (elapsed < REFRESH_INTERVAL_SECONDS) {
            elapsedByPlayer.put(playerId, elapsed);
            return;
        }

        elapsedByPlayer.put(playerId, 0f);
        hudController.refresh(player);
    }
}
