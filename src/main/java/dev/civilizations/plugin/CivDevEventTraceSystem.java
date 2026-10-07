package dev.civilizations.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.hytale.SoldierWorkSystem;
import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Samples a compact set of Civ/NPC state and records transitions for local development diagnostics.
 */
final class CivDevEventTraceSystem extends EntityTickingSystem<EntityStore> {
    private static final double SAMPLE_SECONDS = 0.25;

    private final CivUnitRegistry units;
    private final CivActivityRegistry activities;
    private final CivDevEventHistory history;
    private final Map<CivUnitRegistry.UnitKey, Tracked> tracked = new ConcurrentHashMap<>();

    CivDevEventTraceSystem(
        CivUnitRegistry units,
        CivActivityRegistry activities,
        CivDevEventHistory history
    ) {
        this.units = units;
        this.activities = activities;
        this.history = history;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return NPCEntity.getComponentType();
    }

    @Override
    public void tick(
        float dt,
        int index,
        ArchetypeChunk<EntityStore> chunk,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            return;
        }

        CivUnitRegistry.UnitKey key = units.keyOf(ref);
        if (!units.isClaimed(ref)) {
            tracked.remove(key);
            return;
        }

        Tracked state = tracked.computeIfAbsent(key, ignored -> new Tracked());
        state.elapsed += dt;
        if (state.elapsed < SAMPLE_SECONDS) {
            return;
        }
        state.elapsed = 0.0;

        Snapshot next = snapshot(ref, store);
        if (next == null) {
            return;
        }
        Snapshot previous = state.snapshot;
        state.snapshot = next;

        if (previous == null) {
            history.record(next.uuid(), "observed", details(
                "profession", next.profession(),
                "manualMove", next.manualMove(),
                "combatTarget", next.combatTarget(),
                "health", next.health()
            ));
            return;
        }

        if (!Objects.equals(previous.profession(), next.profession())) {
            history.record(next.uuid(), "profession_changed", details(
                "from", previous.profession(), "to", next.profession()
            ));
        }
        if (previous.manualMove() != next.manualMove()) {
            history.record(next.uuid(), next.manualMove() ? "manual_move_started" : "manual_move_stopped",
                details("moveTarget", next.moveTarget()));
        } else if (next.manualMove() && !Objects.equals(previous.moveTarget(), next.moveTarget())) {
            history.record(next.uuid(), "move_target_changed", details(
                "from", previous.moveTarget(), "to", next.moveTarget()
            ));
        }
        if (!Objects.equals(previous.combatTarget(), next.combatTarget())) {
            String type = previous.combatTarget() == null
                ? "combat_target_acquired"
                : next.combatTarget() == null ? "combat_target_cleared" : "combat_target_changed";
            history.record(next.uuid(), type, details(
                "from", previous.combatTarget(), "to", next.combatTarget()
            ));
        }
        if (!Objects.equals(previous.health(), next.health())) {
            history.record(next.uuid(), "health_changed", details(
                "from", previous.health(), "to", next.health()
            ));
        }
    }

    private Snapshot snapshot(Ref<EntityStore> ref, Store<EntityStore> store) {
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return null;
        }
        UUID uuid = uuidComponent.getUuid();
        String profession = units.getProfession(ref) == null ? null : units.getProfession(ref).name();
        boolean manualMove = activities.manualMovementIntent(ref) != null;
        Vector3d moveTarget = units.getMoveTarget(ref);
        String move = moveTarget == null ? null
            : round(moveTarget.x) + "," + round(moveTarget.y) + "," + round(moveTarget.z);

        MarkedEntitySupport marked = MarkedEntitySupport.get(ref, store);
        Ref<EntityStore> target = marked == null
            ? null
            : marked.getMarkedEntityRef(SoldierWorkSystem.COMBAT_TARGET_SLOT);
        String combatTarget = entityUuid(target);

        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        Double healthValue = health == null ? null : round(health.get());

        return new Snapshot(uuid, profession, manualMove, move, combatTarget, healthValue);
    }

    private static String entityUuid(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        UUIDComponent uuid = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
        return uuid == null ? null : uuid.getUuid().toString();
    }

    private static Map<String, Object> details(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                result.put(String.valueOf(pairs[i]), pairs[i + 1]);
            }
        }
        return result;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class Tracked {
        private double elapsed;
        private Snapshot snapshot;
    }

    private record Snapshot(
        UUID uuid,
        String profession,
        boolean manualMove,
        String moveTarget,
        String combatTarget,
        Double health
    ) {
    }
}
