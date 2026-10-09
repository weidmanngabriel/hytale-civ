package dev.civilizations.hytale;

import com.hypixel.hytale.builtin.npccombatactionevaluator.memory.TargetMemory;
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
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WorkDecisionSchedule;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Selects native Hytale combat targets for Civ soldiers.
 *
 * <p>Civ owns the profession, target-selection cadence and manual-order priority. Hytale owns
 * pathfinding, attack interactions, damage, HP and death. A native NPC counts as a hostile monster
 * when its role is hostile to players. That matches the player's notion of a dangerous monster
 * without requiring the role to be globally hostile to every NPC.</p>
 *
 * <p>The Civ soldier role uses Hytale's Combat Action Evaluator (CAE). Civ mirrors its selected
 * target into the native {@code LockedTarget} slot and {@link TargetMemory}, then enters the native
 * {@code Combat} state. The role/CAE owns pursuit and weapon interactions from that point. When Civ
 * combat is interrupted or no valid target remains, the native target state is cleared and the
 * inhabitant returns to {@code Idle} so manual movement and other profession behavior can resume.</p>
 *
 * <p>Hostile vanilla NPCs do not all use the same combat target mechanism. Civ therefore continues
 * to bridge retaliation into both the native {@code LockedTarget}/{@code Combat} path and an
 * existing {@link TargetMemory} when available. It does not implement movement, attacks, damage or
 * death itself.</p>
 */
public final class SoldierWorkSystem extends EntityTickingSystem<EntityStore> {

    public static final String COMBAT_TARGET_SLOT = "CivCombatTarget";
    private static final String NATIVE_LOCKED_TARGET_SLOT = "LockedTarget";
    private static final String NATIVE_COMBAT_STATE = "Combat";
    private static final String NATIVE_IDLE_STATE = "Idle";
    static final double SEARCH_RADIUS = 16.0;
    private static final double RETRY_SECONDS = 0.5;

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
        long civProfilingStarted = CivPerformanceRecorder.beginMeasured();
        try {
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
            maintainNativeSoldierCombat(ref, currentTarget, store);
            maintainNativeRetaliation(currentTarget, ref, store);
            runtime.target = currentTarget;
            return;
        }

        clearNativeSoldierCombat(ref, store);
        if (currentTarget != null) {
            setCivTarget(ref, null, store);
        }
        runtime.target = null;

        WorkDecisionSchedule.DecisionKind decision = runtime.schedule.advance(dt);
        if (decision == WorkDecisionSchedule.DecisionKind.NONE) {
            return;
        }

        Ref<EntityStore> target = findNearestHostileMonster(ref, store);
        if (target == null) {
            runtime.schedule.scheduleRetry(RETRY_SECONDS);
            return;
        }

        setCivTarget(ref, target, store);
        engageNativeSoldierCombat(ref, target, store);
        engageNativeRetaliation(target, ref, store);
        runtime.target = target;
        runtime.schedule.scheduleRetry(RETRY_SECONDS);
        } finally {
            CivPerformanceRecorder.endMeasured("soldier.tick", civProfilingStarted);
        }
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

    private Ref<EntityStore> findNearestHostileMonster(
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

    private static void engageNativeSoldierCombat(
        Ref<EntityStore> soldier,
        Ref<EntityStore> hostile,
        Store<EntityStore> store
    ) {
        maintainNativeSoldierCombat(soldier, hostile, store);
        setNativeState(soldier, NATIVE_COMBAT_STATE, store);
    }

    private static void maintainNativeSoldierCombat(
        Ref<EntityStore> soldier,
        Ref<EntityStore> hostile,
        Store<EntityStore> store
    ) {
        setNativeMarkedTarget(soldier, NATIVE_LOCKED_TARGET_SLOT, hostile, store);
        rememberHostile(soldier, hostile, store);
        setNativeState(soldier, NATIVE_COMBAT_STATE, store);
    }

    private static void clearNativeSoldierCombat(
        Ref<EntityStore> soldier,
        Store<EntityStore> store
    ) {
        setNativeMarkedTarget(soldier, NATIVE_LOCKED_TARGET_SLOT, null, store);

        TargetMemory memory = store.getComponent(soldier, TargetMemory.getComponentType());
        if (memory != null) {
            memory.getKnownHostiles().clear();
            memory.getKnownHostilesList().clear();
            memory.setClosestHostile(null);
        }

        setNativeState(soldier, NATIVE_IDLE_STATE, store);
    }

    private static void engageNativeRetaliation(
        Ref<EntityStore> hostile,
        Ref<EntityStore> soldier,
        Store<EntityStore> store
    ) {
        maintainNativeRetaliation(hostile, soldier, store);
        setNativeState(hostile, NATIVE_COMBAT_STATE, store);
    }

    private static void maintainNativeRetaliation(
        Ref<EntityStore> hostile,
        Ref<EntityStore> soldier,
        Store<EntityStore> store
    ) {
        setNativeMarkedTarget(hostile, NATIVE_LOCKED_TARGET_SLOT, soldier, store);
        rememberHostile(hostile, soldier, store);
    }

    private static void rememberHostile(
        Ref<EntityStore> owner,
        Ref<EntityStore> hostile,
        Store<EntityStore> store
    ) {
        TargetMemory memory = store.getComponent(owner, TargetMemory.getComponentType());
        if (memory == null) {
            return;
        }

        float previous = memory.getKnownHostiles().put(hostile.getIndex(), memory.getRememberFor());
        if (previous <= 0.0f) {
            memory.getKnownHostilesList().add(hostile);
        }
        memory.setClosestHostile(hostile);
    }

    private static void setNativeMarkedTarget(
        Ref<EntityStore> owner,
        String slot,
        Ref<EntityStore> target,
        Store<EntityStore> store
    ) {
        NPCEntity npc = store.getComponent(owner, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return;
        }
        npc.getRole().setMarkedTarget(owner, store, slot, target);
    }

    private static void setNativeState(
        Ref<EntityStore> ref,
        String state,
        Store<EntityStore> store
    ) {
        StateSupport stateSupport = store.getComponent(ref, StateSupport.getComponentType());
        if (stateSupport == null) {
            return;
        }
        int stateIndex = stateSupport.getStateHelper().getStateIndex(state);
        if (stateIndex >= 0 && stateSupport.getStateIndex() != stateIndex) {
            stateSupport.setState(ref, state, null, store);
        }
    }

    private static Ref<EntityStore> readTarget(
        Ref<EntityStore> soldier,
        Store<EntityStore> store
    ) {
        MarkedEntitySupport marked = MarkedEntitySupport.get(soldier, store);
        return marked == null ? null : marked.getMarkedEntityRef(COMBAT_TARGET_SLOT);
    }

    private static void setCivTarget(
        Ref<EntityStore> soldier,
        Ref<EntityStore> target,
        Store<EntityStore> store
    ) {
        setNativeMarkedTarget(soldier, COMBAT_TARGET_SLOT, target, store);
    }

    private static void clearTarget(Ref<EntityStore> soldier, Store<EntityStore> store) {
        setCivTarget(soldier, null, store);
        clearNativeSoldierCombat(soldier, store);
    }

    private static final class WorkerRuntime {
        private final WorkDecisionSchedule schedule = new WorkDecisionSchedule();
        private Ref<EntityStore> target;
    }
}
