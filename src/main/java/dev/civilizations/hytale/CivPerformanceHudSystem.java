package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;

/** Updates the opt-in indicator at most once a second via the native ECS scheduler. */
public final class CivPerformanceHudSystem extends DelayedEntitySystem<EntityStore> {
    private final CivPerformanceRecorder recorder;
    private final CivUnitRegistry units;
    public CivPerformanceHudSystem(CivPerformanceRecorder recorder, CivUnitRegistry units) {
        super(1.0f);
        this.recorder = recorder;
        this.units = units;
    }
    @Override public boolean isParallel(int archetypeChunkSize, int taskCount) { return false; }
    @Override public Query<EntityStore> getQuery() { return Player.getComponentType(); }
    @Override public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                               Store<EntityStore> store, CommandBuffer<EntityStore> commands) {
        Player player = chunk.getComponent(index, Player.getComponentType());
        if (player == null) return;
        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null) return;
        Map<String, Object> status = recorder.status();
        var manager = player.getHudManager();
        if (!Boolean.TRUE.equals(status.get("active"))) {
            if (manager.getCustomHud(CivPerformanceHud.KEY) != null)
                manager.removeCustomHud(playerRef, CivPerformanceHud.KEY);
            return;
        }
        int remaining = ((Number) status.get("remainingSeconds")).intValue();
        recorder.capture(store.getEntityCount(), (int) units.loadedInhabitants().stream()
            .filter(ref -> ref.getStore() == store).count());
        var existing = manager.getCustomHud(CivPerformanceHud.KEY);
        if (existing instanceof CivPerformanceHud hud) {
            hud.refresh(remaining);
        } else {
            manager.addCustomHud(playerRef, new CivPerformanceHud(playerRef, remaining));
        }
    }
}
