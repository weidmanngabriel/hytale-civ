package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WorkDecisionSchedule;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Selects native Hytale combat targets for Civ soldiers.
 *
 * <p>Civ owns the profession, target-selection cadence and manual-order priority. Hytale owns
 * pathfinding, attack interactions, damage, HP and death. A target counts as a hostile monster in
 * this first slice when its native NPC role is hostile to players.</p>
 */
public final class SoldierWorkSystem extends EntityTickingSystem<EntityStore> {

    public static final String COMBAT_TARGET_SLOT = "CivCombatTarget";
    static final double SEARCH_RADIUS = 16.0;
    private static final double RETRY_SECONDS = 0.5;
    private static final double HOSTILITY_OVERRIDE_SECONDS = 1.5;

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final Map<CivUnitRegistry.UnitKey, WorkerRuntime> workers = new ConcurrentHashMap<>();

    public SoldierWorkSystem(CivUnitRegistry unitRegistry, CivActivityRegistry activityRegistry) {
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
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
        ArchetypeChunk<EntityStore> archetypeChunk,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            return;
        }

        CivUnitRegistry.UnitKey key = unitRegistry.keyOf(ref);
        if (!unitRegistry.isClaimed(ref) || unitRegistry.getProfession(ref) != Profession.SOLDIER) {
            WorkerRuntime previous = workers.remove(key);
            if (previous != null) {
                clearTarget(ref, store);
            }
            return;
        }

        WorkerRuntime runtime = workers.computeIfAbsent(key, ignored -> new WorkerRuntime());
        if (!activityRegistry.autonomousWorkAllowed(ref)) {
            clearTarget(ref, store);
            runtime.target = null;
            runtime.schedule.requestImmediate();
            return;
        }

        Ref<EntityStore> currentTarget = readTarget(ref, store);
        if (isUsableTarget(ref, currentTarget, store)) {
            runtime.target = currentTarget;
            keepTargetHostileToSoldier(ref, currentTarget, store);
            return;
        }

        if (currentTarget != null) {
            clearTarget(ref, store);
        }
        runtime.target = null;

        WorkDecisionSchedule.DecisionKind decision = runtime.schedule.advance(dt);
        if (decision == WorkDecisionSchedule.DecisionKind.NONE) {
            return;
        }

        Ref<EntityStore> target = findNearestHostileToPlayers(ref, store);
        if (target == null) {
            runtime.schedule.scheduleRetry(RETRY_SECONDS);
            return;
        }

        setTarget(ref, target, store);
        runtime.target = target;
        keepTargetHostileToSoldier(ref, target, store);
        runtime.schedule.scheduleRetry(RETRY_SECONDS);
    }

    public Ref<EntityStore> targetOf(Ref<EntityStore> soldier) {
        if (soldier == null || !soldier.isValid()) {
            return null;
        }
        WorkerRuntime runtime = workers.get(unitRegistry.keyOf(soldier));
        Ref<EntityStore> target = runtime == null ? null : runtime.target;
        return target != null && target.isValid() ? target : null;
    }

    public void forgetRuntime(Ref<EntityStore> ref) {
        if (ref == null) {
            return;
        }
        workers.remove(unitRegistry.keyOf(ref));
        if (ref.isValid()) {
            clearTarget(ref, ref.getStore());
        }
    }

    private Ref<EntityStore> findNearestHostileToPlayers(
        Ref<EntityStore> soldier,
        Store<EntityStore> store
    ) {
        TransformComponent soldierTransform =
            store.getComponent(soldier, TransformComponent.getComponentType());
        if (soldierTransform == null) {
            return null;
        }

        var spatial = store.getResource(NPCPlugin.get().getNpcSpatialResource());
        if (spatial == null || spatial.getSpatialStructure() == null) {
            return null;
        }

        List<Ref<EntityStore>> candidates = SpatialResource.getThreadLocalReferenceList();
        candidates.clear();
        spatial.getSpatialStructure().ordered(
            soldierTransform.getPosition(),
            SEARCH_RADIUS,
            candidates
        );

        for (Ref<EntityStore> candidate : candidates) {
            if (candidate == null || candidate.equals(soldier)) {
                continue;
            }
            if (isUsableTarget(soldier, candidate, store)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isUsableTarget(
        Ref<EntityStore> soldier,
        Ref<EntityStore> candidate,
        Store<EntityStore> store
    ) {
        if (candidate == null || !candidate.isValid() || candidate.equals(soldier)) {
            return false;
        }
        if (store.getComponent(candidate, NPCEntity.getComponentType()) == null) {
            return false;
        }

        TransformComponent soldierTransform =
            store.getComponent(soldier, TransformComponent.getComponentType());
        TransformComponent targetTransform =
            store.getComponent(candidate, TransformComponent.getComponentType());
        if (soldierTransform == null || targetTransform == null
            || soldierTransform.getPosition().distanceSquared(targetTransform.getPosition())
                > SEARCH_RADIUS * SEARCH_RADIUS) {
            return false;
        }

        EntityStatMap stats = store.getComponent(candidate, EntityStatMap.getComponentType());
        if (stats != null) {
            EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
            if (health != null && health.get() <= health.getMin()) {
                return false;
            }
        }

        WorldSupport targetWorldSupport = WorldSupport.get(candidate, store);
        return targetWorldSupport != null
            && targetWorldSupport.getDefaultPlayerAttitude() == Attitude.HOSTILE;
    }

    private static Ref<EntityStore> readTarget(
        Ref<EntityStore> soldier,
        Store<EntityStore> store
    ) {
        MarkedEntitySupport marked = MarkedEntitySupport.get(soldier, store);
        return marked == null ? null : marked.getMarkedEntityRef(COMBAT_TARGET_SLOT);
    }

    private static void setTarget(
        Ref<EntityStore> soldier,
        Ref<EntityStore> target,
        Store<EntityStore> store
    ) {
        NPCEntity npc = store.getComponent(soldier, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return;
        }
        npc.getRole().setMarkedTarget(soldier, store, COMBAT_TARGET_SLOT, target);
    }

    private static void clearTarget(Ref<EntityStore> soldier, Store<EntityStore> store) {
        setTarget(soldier, null, store);
    }

    private static void keepTargetHostileToSoldier(
        Ref<EntityStore> soldier,
        Ref<EntityStore> target,
        Store<EntityStore> store
    ) {
        WorldSupport targetWorldSupport = WorldSupport.get(target, store);
        if (targetWorldSupport != null) {
            targetWorldSupport.overrideAttitude(
                soldier,
                Attitude.HOSTILE,
                HOSTILITY_OVERRIDE_SECONDS
            );
        }
    }

    private static final class WorkerRuntime {
        private final WorkDecisionSchedule schedule = new WorkDecisionSchedule();
        private Ref<EntityStore> target;
    }
}
