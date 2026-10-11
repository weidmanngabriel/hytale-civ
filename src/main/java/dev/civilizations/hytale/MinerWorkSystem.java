package dev.civilizations.hytale;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.ShaderType;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.MineCaveObservation;
import dev.civilizations.core.MineCavePolicy;
import dev.civilizations.core.MineDecisionCategory;
import dev.civilizations.core.MineDecisionSink;
import dev.civilizations.core.MineFrontCoordinator;
import dev.civilizations.core.MineFrontTaskScheduler;
import dev.civilizations.core.MineHeading;
import dev.civilizations.core.MineInfrastructurePlanner;
import dev.civilizations.core.MineInfrastructureTask;
import dev.civilizations.core.MineInfrastructureAvailability;
import dev.civilizations.core.MinePlacementExcavationGuard;
import dev.civilizations.core.MineNetwork;
import dev.civilizations.core.MineGenerationPolicy;
import dev.civilizations.core.MineGenerationProgress;
import dev.civilizations.core.MineTunnelPath;
import dev.civilizations.core.MineTunnelVoxelizer;
import dev.civilizations.core.MinePathPoint;
import dev.civilizations.core.MineNetworkGrowthPlanner;
import dev.civilizations.core.MineBuildingTypes;
import dev.civilizations.core.DwarvenMinePlanner;
import dev.civilizations.core.DwarvenMineFinishPlan;
import dev.civilizations.core.MineNormalTaskSelector;
import dev.civilizations.core.MineObstaclePolicy;
import dev.civilizations.core.MinePathPlanner;
import dev.civilizations.core.MineRoom;
import dev.civilizations.core.MineRoomCoordinator;
import dev.civilizations.core.MineRoomGeometry;
import dev.civilizations.core.MineRoomPlanner;
import dev.civilizations.core.MineRestartPositionPolicy;
import dev.civilizations.core.MineIdleDestinationSelector;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineTunnelGeometry;
import dev.civilizations.core.MineTuning;
import dev.civilizations.core.MineWorkFront;
import dev.civilizations.core.MinerWorkController;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.core.Profession;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes planned mine work fronts with Hytale-native movement, animation and block breaking.
 * MinerWorkController owns the shared task lifecycle and transient reservations.
 * This adapter observes loaded-world geometry and executes native engine operations.
 */
public final class MinerWorkSystem extends DelayedEntitySystem<EntityStore> {

    private static final float TICK_INTERVAL_SECONDS = 0.50f;
    private static final String TYPE_TAG = "civ.type";
    private static final String BUILDING_TAG = "civ.building";
    private static final String WORKPLACE_ACCESS = "workplace_access";
    private static final String TUNNEL_CONNECTOR = "mine_tunnel_connector";
    private static final String MINE_BUILDING = "mine";
    private static final String MINING_ITEM_ANIMATIONS = "Civ_Miner_Pickaxe";
    private static final String MINING_ANIMATION = "SwingDown";
    private static final String BUILDING_ITEM_ANIMATIONS = "Civ_Construction_Hammer";
    private static final String BUILDING_ANIMATION = "Build";
    private static final double INFRASTRUCTURE_SECONDS_PER_BLOCK = 0.5;
    private static final double ROOM_BUILD_SECONDS_PER_SECTION = 1.0;
    private static final int MAX_BRIDGE_SPAN = MineBridgeSpanPolicy.MAX_SPAN;
    private static final int BRIDGE_LANDING_OVERLAP_SLICES = 3;
    // Eight compass directions, two blocks from the original work anchor (5x5 footprint).
    private static final int[][] BUILD_PROBE_OFFSETS = {
        {0, -2}, {2, -2}, {2, 0}, {2, 2}, {0, 2}, {-2, 2}, {-2, 0}, {-2, -2}
    };
    private static final int MAX_FLUID_BRIDGE_SPAN = MineBridgeSpanPolicy.MAX_SPAN;
    private static final double ARRIVAL_HORIZONTAL_DISTANCE = 1.6;
    private static final double ARRIVAL_VERTICAL_TOLERANCE = 1.0;
    private static final int MAIN_PLAN_LENGTH_BLOCKS = MinePathPlanner.FOOTPRINT_SIZE_BLOCKS;
    private static final int RUNTIME_PLANNING_TUNNEL_BUDGET = 64;
    private static final int MAIN_PLANNING_BATCH_SLICES = 24;

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final BuildingPlacementRegistry buildingRegistry;
    private final MineTunnelRegistry tunnelRegistry;
    private final MineDecisionSink decisionSink;
    private final MinerNavigationFailureRegistry navigationFailures;
    private final MineFrontCoordinator<CivUnitRegistry.UnitKey> frontCoordinator = new MineFrontCoordinator<>();
    private final MineRoomCoordinator<CivUnitRegistry.UnitKey> roomCoordinator = new MineRoomCoordinator<>();
    private final Map<CivUnitRegistry.UnitKey, WorkerRuntime> workers = new ConcurrentHashMap<>();
    private final Map<UUID, CivUnitRegistry.UnitKey> infrastructureReservations =
        new ConcurrentHashMap<>();
    private final MinerWorkController<CivUnitRegistry.UnitKey> controller =
        new MinerWorkController<>(frontCoordinator, roomCoordinator, infrastructureReservations);
    private final Map<WorldMineKey, RuntimeMinePlan> runtimePlans = new ConcurrentHashMap<>();
    private final Set<CivUnitRegistry.UnitKey> pendingRecovery = ConcurrentHashMap.newKeySet();

    public MinerWorkSystem(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        BuildingPlacementRegistry buildingRegistry,
        MineTunnelRegistry tunnelRegistry
    ) {
        this(
            unitRegistry,
            activityRegistry,
            buildingRegistry,
            tunnelRegistry,
            MineDecisionSink.NONE,
            new MinerNavigationFailureRegistry()
        );
    }

    public MinerWorkSystem(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        BuildingPlacementRegistry buildingRegistry,
        MineTunnelRegistry tunnelRegistry,
        MineDecisionSink decisionSink
    ) {
        this(
            unitRegistry,
            activityRegistry,
            buildingRegistry,
            tunnelRegistry,
            decisionSink,
            new MinerNavigationFailureRegistry()
        );
    }

    public MinerWorkSystem(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        BuildingPlacementRegistry buildingRegistry,
        MineTunnelRegistry tunnelRegistry,
        MineDecisionSink decisionSink,
        MinerNavigationFailureRegistry navigationFailures
    ) {
        super(TICK_INTERVAL_SECONDS);
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
        this.buildingRegistry = buildingRegistry;
        this.tunnelRegistry = tunnelRegistry;
        this.decisionSink = decisionSink == null ? MineDecisionSink.NONE : decisionSink;
        this.navigationFailures = navigationFailures == null
            ? new MinerNavigationFailureRegistry()
            : navigationFailures;
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
        CivUnitRegistry.UnitKey workerKey = unitRegistry.keyOf(ref);
        if (!ref.isValid() || unitRegistry.getProfession(ref) != Profession.MINER) {
            releaseWorker(workerKey, ref, store);
            return;
        }

        WorkerRuntime runtime = workers.computeIfAbsent(workerKey, ignored -> new WorkerRuntime());
        if (!activityRegistry.autonomousWorkAllowed(ref)) {
            workerTaskEnded(runtime.mineId, ref, workerKey, runtime, "MANUAL_MOVE");
            controller.interrupt(workerKey);
            navigationFailures.forget(workerKey);
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            runtime.interruptForManualMove();
            workerState(runtime.mineId, ref, workerKey, runtime, WorkerDebugState.MANUAL_CONTROL, "MANUAL_MOVE");
            return;
        }
        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        World world = store.getExternalData().getWorld();
        UUID worldId = world.getWorldConfig().getUuid();
        BuildingPlacementRegistry.BuildingInstance mine = assignedMine(ref, worldId);
        if (mine == null) {
            controller.forget(workerKey);
            navigationFailures.forget(workerKey);
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            runtime.clearAssignment();
            return;
        }
        if (pendingRecovery.remove(workerKey) || !mine.id().equals(runtime.mineId) || mine.phase() != runtime.minePhase) {
            controller.forget(workerKey);
            navigationFailures.forget(workerKey);
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            unitRegistry.clearMoveTarget(ref);
            runtime.reset(mine.id(), mine.phase());
        }
        PrefabPlacementService.PlacedMarker entrance = marker(world, mine, WORKPLACE_ACCESS);
        PrefabPlacementService.PlacedMarker connector = marker(world, mine, TUNNEL_CONNECTOR);
        if (connector == null || connector.bounds() == null) {
            controller.forget(workerKey);
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            runtime.clearWorkAssignment();
            return;
        }
        Vector3d position = transform.getPosition();
        RuntimeMinePlan minePlan = ensureRuntimePlan(world, mine, connector);
        if (minePlan == null) return;
        refreshMainPlanning(world, mine, minePlan);
        if (runtime.restorePosition && !runtime.reachedConnector && restoredInsideMine(world, minePlan, position)) {
            controller.restoredInside(workerKey);
            runtime.enteredMine = true;
            runtime.reachedConnector = true;
        }
        runtime.restorePosition = false;
        // World observations only: passability is assessed before empty slices advance.
        refreshBridgeTasks(world, mine, minePlan);
        advanceAlreadyExcavatedSlices(world, mine, minePlan);
        refreshBridgeTasks(world, mine, minePlan);
        controller.tick(workerKey, dt, new NativeMinerEngine(
            world, mine, minePlan, entrance, connector, ref, store, commandBuffer,
            position, workerKey, runtime
        ));
        } finally {
            CivPerformanceRecorder.endMeasured("miner.tick", civProfilingStarted);
        }
    }

    /**
     * Debug-only recovery of the current mine. It intentionally preserves NPC identity,
     * profession, workplace, excavated blocks and completed infrastructure.
     * Worker resets are applied on their next ECS tick, where the entity Store is available.
     */
    public RecoveryResult recover(World world, UUID mineId, boolean workersRequested, boolean frontsRequested) {
        UUID worldId = world.getWorldConfig().getUuid();
        MineNetwork network = tunnelRegistry.networkForMine(worldId, mineId);
        if (network == null) return new RecoveryResult(0, 0, 0, false);
        int reopened = 0;
        int finished = 0;
        if (frontsRequested) {
            MineNetwork next = network;
            for (MineWorkFront front : network.workFronts()) {
                if (front.state() == MineWorkFront.State.COMPLETE) {
                    finished++;
                    continue;
                }
                if (front.state() != MineWorkFront.State.ABANDONED
                    && front.state() != MineWorkFront.State.BLOCKED) continue;
                next = next.withWorkFront(new MineWorkFront(
                    front.id(), front.tunnelId(), front.position(), MineWorkFront.State.OPEN
                ));
                reopened++;
            }
            if (reopened > 0) tunnelRegistry.putNetwork(world, next);
            RuntimeMinePlan plan = runtimePlans.get(new WorldMineKey(worldId, mineId));
            if (plan != null) {
                for (RuntimeFrontPlan front : plan.fronts.values()) {
                    MineWorkFront persisted = workFrontById(next, front.frontId);
                    if (persisted != null && persisted.state() == MineWorkFront.State.OPEN) {
                        front.unavailable = false;
                    }
                }
            }
        }
        int scheduledWorkers = 0;
        if (workersRequested) {
            for (Map.Entry<CivUnitRegistry.UnitKey, WorkerRuntime> entry : workers.entrySet()) {
                if (!mineId.equals(entry.getValue().mineId)) continue;
                pendingRecovery.add(entry.getKey());
                scheduledWorkers++;
            }
            // A failed room is only a runtime limitation, not a persisted room result.
            RuntimeMinePlan plan = runtimePlans.get(new WorldMineKey(worldId, mineId));
            if (plan != null) {
                for (RuntimeRoomPlan room : plan.rooms.values()) room.unavailable = false;
                for (RuntimeInfrastructureTask infrastructure : plan.infrastructureTasks.values()) {
                    infrastructure.lastPlacementFailureKey = null;
                    infrastructure.repeatedPlacementFailures = 0;
                    infrastructure.deferredAtSlice = -1;
                    infrastructure.deferredByFrontId = null;
                }
            }
        }
        decisionSink.record(
            mineId, null, MineDecisionCategory.WORKER, "MINE_RECOVERY_REQUESTED",
            "workersScheduled", scheduledWorkers, "frontsReopened", reopened,
            "completedFrontsPreserved", finished
        );
        return new RecoveryResult(scheduledWorkers, reopened, finished, true);
    }

    public RecoveryResult recoveryStatus(UUID worldId, UUID mineId) {
        MineNetwork network = tunnelRegistry.networkForMine(worldId, mineId);
        if (network == null) return new RecoveryResult(0, 0, 0, false);
        int affectedWorkers = 0;
        for (WorkerRuntime runtime : workers.values()) {
            if (mineId.equals(runtime.mineId)) affectedWorkers++;
        }
        int recoverable = 0;
        int completed = 0;
        for (MineWorkFront front : network.workFronts()) {
            if (front.state() == MineWorkFront.State.ABANDONED
                || front.state() == MineWorkFront.State.BLOCKED) recoverable++;
            if (front.state() == MineWorkFront.State.COMPLETE) completed++;
        }
        return new RecoveryResult(affectedWorkers, recoverable, completed, true);
    }

    public record RecoveryResult(
        int workers,
        int fronts,
        int completedFrontsPreserved,
        boolean mineFound
    ) {}

    public void forgetRuntime(Ref<EntityStore> ref) {
        if (ref == null) return;
        CivUnitRegistry.UnitKey key = unitRegistry.keyOf(ref);
        controller.forget(key);
        WorkerRuntime runtime = workers.remove(key);
        if (runtime != null) releaseInfrastructureReservation(key, runtime);
        navigationFailures.forget(key);
    }

    /** Hytale executes world work; the Core owns the order, claims, time and retry limit. */
    private final class NativeMinerEngine implements MinerWorkController.Engine {
        private final World world;
        private final BuildingPlacementRegistry.BuildingInstance mine;
        private final RuntimeMinePlan plan;
        private final PrefabPlacementService.PlacedMarker entrance, connectorMarker;
        private final Ref<EntityStore> ref;
        private final Store<EntityStore> store;
        private final CommandBuffer<EntityStore> commandBuffer;
        private final Vector3d position;
        private final CivUnitRegistry.UnitKey worker;
        private final WorkerRuntime runtime;
        private List<MinerWorkController.Task> observedTasks = List.of();

        NativeMinerEngine(World world, BuildingPlacementRegistry.BuildingInstance mine,
            RuntimeMinePlan plan, PrefabPlacementService.PlacedMarker entrance,
            PrefabPlacementService.PlacedMarker connectorMarker, Ref<EntityStore> ref,
            Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
            Vector3d position, CivUnitRegistry.UnitKey worker, WorkerRuntime runtime) {
            this.world = world; this.mine = mine; this.plan = plan;
            this.entrance = entrance; this.connectorMarker = connectorMarker;
            this.ref = ref; this.store = store; this.commandBuffer = commandBuffer;
            this.position = position; this.worker = worker; this.runtime = runtime;
        }
        private WorldPosition point(Vector3d v) { return new WorldPosition(v.x, v.y, v.z); }
        private Vector3d vector(WorldPosition v) { return new Vector3d(v.x(), v.y(), v.z()); }
        @Override public WorldPosition access() {
            return entrance == null || entrance.bounds() == null ? connector()
                : point(center(entrance.bounds(), entrance.bounds().minY()));
        }
        @Override public WorldPosition connector() {
            return point(center(connectorMarker.bounds(), connectorMarker.bounds().minY()));
        }
        @Override public BlockPosition position() { return blockPosition(position); }
        @Override public MinerWorkController.Navigation navigate(WorldPosition target) {
            Vector3d nativeTarget = vector(target);
            if (navigationFailures.consumeIfMatches(worker, nativeTarget))
                return MinerWorkController.Navigation.FAILED;
            if (arrived(position, nativeTarget)) {
                var state = controller.snapshot(worker).state();
                if (state == MinerWorkController.State.ENTERING_ACCESS) runtime.enteredMine = true;
                if (state == MinerWorkController.State.ENTERING_CONNECTOR) runtime.reachedConnector = true;
                return MinerWorkController.Navigation.ARRIVED;
            }
            var state = controller.snapshot(worker).state();
            runtime.idleDestination = state == MinerWorkController.State.RETURNING_CONNECTOR
                || state == MinerWorkController.State.RETURNING_ACCESS || state == MinerWorkController.State.RESTING
                ? nativeTarget : null;
            navigateTo(ref, nativeTarget, runtime);
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            return MinerWorkController.Navigation.MOVING;
        }
        @Override public void stop() {
            unitRegistry.clearMoveTarget(ref); runtime.navigationArrived();
        }
        @Override public List<MinerWorkController.Task> tasks() {
            List<MinerWorkController.Task> tasks = new ArrayList<>();
            for (RuntimeInfrastructureTask task : plan.infrastructureTasks.values()) {
                RuntimeFrontPlan front = frontForTunnel(plan, task.task.tunnelId());
                if (!task.completed && task.task.mandatory() && front != null && !front.unavailable
                    && infrastructureAvailable(task.task, front)) {
                    tasks.add(new MinerWorkController.Task(task.task.id(), MineNormalTaskSelector.Kind.INFRASTRUCTURE,
                        10, 1, task.task.anchor(), true));
                }
            }
            for (var task : normalCandidates(world, mine, plan, position, worker, runtime)) {
                tasks.add(new MinerWorkController.Task(task.id(), task.kind(), task.priority(),
                    task.capacity(), task.position(), false));
            }
            observedTasks = List.copyOf(tasks);
            return observedTasks;
        }
        @Override public Map<UUID, Integer> priorityBonuses() {
            MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
            return network == null ? Map.of() : network.normalTaskPriorityBonuses();
        }
        @Override public void priorityBonuses(Map<UUID, Integer> bonuses) {
            MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
            if (network != null && !network.normalTaskPriorityBonuses().equals(bonuses))
                tunnelRegistry.putNetwork(world, network.withNormalTaskPriorityBonuses(bonuses));
        }
        @Override public void selected(MinerWorkController.Task task) {
            runtime.clearIdle(); runtime.clearWorkAssignment();
            switch (task.kind()) {
                case TUNNEL_FRONT -> runtime.frontId = task.id();
                case ROOM -> runtime.roomId = task.id();
                case INFRASTRUCTURE -> runtime.infrastructureTaskId = task.id();
            }
            runtime.selectedTaskKey = task.kind() + ":" + task.id();
            RuntimeInfrastructureTask infrastructure = plan.infrastructureTasks.get(task.id());
            if (infrastructure != null && infrastructure.task.decoration())
                decisionSink.record(mine.id(), task.id(), MineDecisionCategory.WORKER, "DECORATION_TASK_SELECTED",
                    "npc", workerLabel(ref,worker), "kind", infrastructure.task.decorationKind());
            for (var candidate : observedTasks) {
                RuntimeInfrastructureTask other = plan.infrastructureTasks.get(candidate.id());
                if (!candidate.id().equals(task.id()) && other != null && other.task.decoration())
                    decisionSink.record(mine.id(), candidate.id(), MineDecisionCategory.WORKER, "DECORATION_TASK_SKIPPED",
                        "reason", "AVAILABLE_NOT_SELECTED", "selectedInstead", task.id());
            }
            decisionSink.record(mine.id(), task.id(), MineDecisionCategory.WORKER, "TASK_SELECTED",
                "npc", workerLabel(ref, worker), "taskType", task.kind(), "reservation", "JOINED",
                "workers", controller.workerCount(task), "capacity", task.capacity(), "priority", task.priority());
        }
        private MinerWorkController.Work work(String revision, Vector3d target,
            MinerWorkController.Operation operation, double seconds, List<BlockPosition> blocks,
            int sections, Set<Integer> completed) {
            return new MinerWorkController.Work(MinerWorkController.Readiness.READY, revision,
                point(target), operation, seconds, blocks, sections, completed);
        }
        private MinerWorkController.Work stopped(MinerWorkController.Readiness readiness) {
            return MinerWorkController.Work.stopped(readiness);
        }
        @Override public MinerWorkController.Work observe(MinerWorkController.Task task) {
            return switch (task.kind()) {
                case TUNNEL_FRONT -> observeFront(task);
                case ROOM -> observeRoom(task);
                case INFRASTRUCTURE -> observeInfrastructure(task);
            };
        }
        private MinerWorkController.Work observeFront(MinerWorkController.Task task) {
            RuntimeFrontPlan front = plan.fronts.get(task.id());
            MineWorkFront persisted = currentFront(world.getWorldConfig().getUuid(), mine.id(), task.id());
            if (front == null || front.complete) return stopped(MinerWorkController.Readiness.COMPLETE);
            if (!MinerWorkSystem.available(persisted) || front.unavailable || front.sliceIndex >= front.unlockedSlices)
                return stopped(MinerWorkController.Readiness.DEFERRED);
            // Mandatory passability work preempts excavation only at this safety boundary.
            if (hasPendingMandatoryInfrastructure(plan, front)) return stopped(MinerWorkController.Readiness.DEFERRED);
            MineTunnelGeometry.Slice slice = front.slices.get(front.sliceIndex);
            if (containsBlockedSolid(world, mine, slice)) return stopped(MinerWorkController.Readiness.UNSAFE);
            if (sliceComplete(world, slice)) return stopped(MinerWorkController.Readiness.COMPLETE);
            runtime.sliceIndex = front.sliceIndex;
            return work("front:" + front.sliceIndex, workTarget(front, plan.mainTunnelId, connectorMarker),
                MinerWorkController.Operation.EXCAVATE, MineTuning.secondsPerBlock(),
                front.orderedBlocks.get(front.sliceIndex), 0, Set.of());
        }
        private MinerWorkController.Work observeRoom(MinerWorkController.Task task) {
            RuntimeRoomPlan roomPlan = plan.rooms.get(task.id());
            MineRoom room = currentRoom(world.getWorldConfig().getUuid(), mine.id(), task.id());
            if (roomPlan == null || room == null || roomPlan.unavailable)
                return stopped(MinerWorkController.Readiness.DEFERRED);
            if (room.terminal()) return stopped(MinerWorkController.Readiness.COMPLETE);
            if (room.state() == MineRoom.State.PLANNED) {
                room = room.beginExcavation(); persistRoom(world, mine, room);
            }
            if (room.state() == MineRoom.State.EXCAVATING) {
                int index = room.excavationWorkUnitIndex();
                if (index >= roomPlan.geometry.excavationWorkUnits().size())
                    return stopped(MinerWorkController.Readiness.COMPLETE);
                var blocks = roomPlan.geometry.excavationWorkUnits().get(index);
                if (containsBlockedSolid(world, mine, blocks)) return stopped(MinerWorkController.Readiness.UNSAFE);
                if (roomWorkUnitComplete(world, blocks)) return stopped(MinerWorkController.Readiness.COMPLETE);
                BlockPosition target = roomPlan.geometry.workTargetForUnit(index);
                return work("room:" + index, new Vector3d(target.x()+.5,target.y(),target.z()+.5),
                    MinerWorkController.Operation.EXCAVATE, MineTuning.secondsPerBlock(), blocks, 0, Set.of());
            }
            int count = MineRoomPrefabService.sectionCount(room);
            if (count <= 0) return stopped(MinerWorkController.Readiness.UNRESOLVABLE);
            if (room.completedBuildSections().size() >= count) return stopped(MinerWorkController.Readiness.COMPLETE);
            return work("build", new Vector3d(room.position().x()+.5,room.position().y(),room.position().z()+.5),
                MinerWorkController.Operation.BUILD_SECTION, ROOM_BUILD_SECONDS_PER_SECTION,
                List.of(), count, room.completedBuildSections());
        }
        private MinerWorkController.Work observeInfrastructure(MinerWorkController.Task task) {
            RuntimeInfrastructureTask infrastructure = plan.infrastructureTasks.get(task.id());
            if (infrastructure == null || infrastructure.completed) return stopped(MinerWorkController.Readiness.COMPLETE);
            RuntimeFrontPlan front = frontForTunnel(plan, infrastructure.task.tunnelId());
            if (front == null || front.unavailable) return stopped(MinerWorkController.Readiness.DEFERRED);
            if (runtime.resolvedInfrastructure == null) {
                if (infrastructure.task.decoration()) {
                    var decorationResolution = MineInfrastructurePlacementResolver.resolveDecorationDetailed(
                        world, infrastructure.task, infrastructure.tunnelKind, infrastructure.geometry);
                    runtime.resolvedInfrastructure = decorationResolution.resolvedTask();
                    if (runtime.resolvedInfrastructure == null)
                        decisionSink.record(mine.id(),task.id(),MineDecisionCategory.ADAPTER,"DECORATION_SKIPPED_RUNTIME",
                            "triedSlices", decorationResolution.triedSlices(), "reasons", decorationResolution.reasons());
                } else runtime.resolvedInfrastructure = MineInfrastructurePlacementResolver.resolve(
                    world, infrastructure.task, infrastructure.tunnelKind, infrastructure.geometry);
                runtime.infrastructurePlacementIndex = 0;
            }
            if (runtime.resolvedInfrastructure == null) {
                if (infrastructure.task.type() == MineInfrastructureTask.Type.BUILD_BRIDGE
                    && bridgeDeckComplete(world, infrastructure)) {
                    decisionSink.record(mine.id(),task.id(),MineDecisionCategory.ADAPTER,"BRIDGE_DECK_ALREADY_COMPLETE");
                    return stopped(MinerWorkController.Readiness.COMPLETE);
                }
                return stopped(MinerWorkController.Readiness.UNRESOLVABLE);
            }
            RuntimeFrontPlan blocking = blockingExcavationFront(world, plan, infrastructure, runtime);
            if (blocking != null) {
                infrastructure.deferredAtSlice = blocking.sliceIndex;
                infrastructure.deferredByFrontId = blocking.frontId;
                return stopped(MinerWorkController.Readiness.DEFERRED);
            }
            if (runtime.infrastructurePlacementIndex >= runtime.resolvedInfrastructure.placements().size()) {
                if (infrastructure.task.type() == MineInfrastructureTask.Type.BUILD_BRIDGE
                    && !bridgeDeckComplete(world, infrastructure)) return stopped(MinerWorkController.Readiness.UNRESOLVABLE);
                return stopped(MinerWorkController.Readiness.COMPLETE);
            }
            return work("placement", runtime.resolvedInfrastructure.workTarget(),
                MinerWorkController.Operation.PLACE, INFRASTRUCTURE_SECONDS_PER_BLOCK, List.of(), 0, Set.of());
        }
        @Override public boolean available(BlockPosition block) { return isAvailableWorkBlock(world, mine, block); }
        @Override public void working(MinerWorkController.Task task, MinerWorkController.Operation operation) {
            boolean digging = operation == MinerWorkController.Operation.EXCAVATE;
            if (digging) stopBuildingAnimation(ref, store, runtime);
            else stopMiningAnimation(ref, store, runtime);
            workerTaskStarted(mine.id(), ref, worker, runtime, operation.name(), task.id(),
                controller.workerCount(task), task.capacity());
            workerState(mine.id(), ref, worker, runtime, digging ? WorkerDebugState.WORKING : WorkerDebugState.BUILDING,
                "WORK_TARGET_REACHED");
            if (digging && !runtime.animationStarted) {
                AnimationUtils.playAnimation(ref, AnimationSlot.Action, MINING_ITEM_ANIMATIONS, MINING_ANIMATION, store);
                runtime.animationStarted = true;
            } else if (!digging && !runtime.buildingAnimationStarted) {
                AnimationUtils.playAnimation(ref, AnimationSlot.Action, BUILDING_ITEM_ANIMATIONS, BUILDING_ANIMATION, store);
                runtime.buildingAnimationStarted = true;
            }
        }
        @Override public MinerWorkController.Result perform(MinerWorkController.WorkIntent intent) {
            if (intent.operation() == MinerWorkController.Operation.EXCAVATE) {
                BlockPosition block = intent.block(); runtime.claimedBlock = block;
                BlockType type = loadedBlockType(world, block);
                if (type == null) return MinerWorkController.Result.RETRY;
                if (isEmpty(type)) return MinerWorkController.Result.SUCCESS;
                if (!safeBlock(world, mine, block)) return MinerWorkController.Result.UNSAFE;
                BlockHarvestUtils.performBlockBreak(ref, null,
                    List.of(new Vector3i(block.x(),block.y(),block.z())), 0, store, world.getChunkStore().getStore());
                if (!isEmpty(loadedBlockType(world, block)) && runtime.frontId != null) {
                    WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(block.x(),block.z()));
                    if (chunk == null || !safeBlock(world, mine, block)
                        || !chunk.breakBlock(block.x(),block.y(),block.z(),0,0)) return MinerWorkController.Result.RETRY;
                }
                return isEmpty(loadedBlockType(world, block))
                    ? MinerWorkController.Result.SUCCESS : MinerWorkController.Result.RETRY;
            }
            if (intent.operation() == MinerWorkController.Operation.BUILD_SECTION) {
                MineRoom room = currentRoom(world.getWorldConfig().getUuid(), mine.id(), intent.taskId());
                if (room == null) return MinerWorkController.Result.UNSAFE;
                runtime.roomBuildSection = intent.section();
                if (!MineRoomPrefabService.placeSection(world, room, intent.section(), commandBuffer))
                    return MinerWorkController.Result.RETRY;
                persistRoom(world, mine, room.completeBuildSection(intent.section(), MineRoomPrefabService.sectionCount(room)));
                return MinerWorkController.Result.SUCCESS;
            }
            RuntimeInfrastructureTask infrastructure = plan.infrastructureTasks.get(intent.taskId());
            // The complete observation is repeated immediately before every native placement.
            if (blockingExcavationFront(world, plan, infrastructure, runtime) != null)
                return MinerWorkController.Result.RETRY;
            var placement = runtime.resolvedInfrastructure.placements().get(runtime.infrastructurePlacementIndex);
            BlockType occupied = loadedBlockType(world, placement.position());
            if (infrastructure.task.type() == MineInfrastructureTask.Type.BUILD_BRIDGE
                && occupied != null && !placement.blockId().equals(occupied.getId())
                && MineBlockPlacement.isDeco(world, placement.position())) {
                WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(placement.position().x(),placement.position().z()));
                if (chunk != null && chunk.breakBlock(placement.position().x(),placement.position().y(),placement.position().z(),0,0))
                    decisionSink.record(mine.id(),intent.taskId(),MineDecisionCategory.ADAPTER,"BRIDGE_DECO_REPLACED",
                        "position", placement.position());
            }
            var result = MineBlockPlacement.placeDetailed(world, placement.position(), placement.blockId(),
                placement.rotation(), placement.placedAgainst(), placement.markDeco());
            if (!result.success()) {
                int attempts = controller.snapshot(worker).retries()+1;
                decisionSink.record(mine.id(), intent.taskId(), MineDecisionCategory.ADAPTER,
                    attempts == 1 ? "BLOCK_PLACEMENT_FAILED" : "BLOCK_PLACEMENT_FAILURE_REPEATED",
                    "npc", workerLabel(ref,worker), "position", placement.position(), "reason", result.failureReason(),
                    "repeatCount", attempts, "workTarget", formatTarget(runtime.resolvedInfrastructure.workTarget()),
                    "minerPosition", formatTarget(position));
                if (attempts == 3) decisionSink.record(mine.id(),intent.taskId(),MineDecisionCategory.ADAPTER,
                    "PLACEMENT_RETRY_LOOP_DETECTED", "repeatCount", attempts);
                return MinerWorkController.Result.RETRY;
            }
            runtime.infrastructurePlacementIndex++;
            return MinerWorkController.Result.SUCCESS;
        }
        @Override public WorldPosition retryTarget(MinerWorkController.Task task, MinerWorkController.Work work, int attempt) {
            if (work.operation() == MinerWorkController.Operation.EXCAVATE) return work.target();
            int start = task.kind() == MineNormalTaskSelector.Kind.ROOM ? runtime.roomProbeIndex : runtime.infrastructureProbeIndex;
            for (int i = start; i < BUILD_PROBE_OFFSETS.length; i++) {
                if (task.kind() == MineNormalTaskSelector.Kind.ROOM) runtime.roomProbeIndex = i+1;
                else runtime.infrastructureProbeIndex = i+1;
                Vector3d candidate = safeBuildProbe(world, vector(work.target()), BUILD_PROBE_OFFSETS[i]);
                if (candidate != null) return point(candidate);
            }
            return null;
        }
        @Override public WorldPosition restTarget() {
            MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
            if (network == null) return null;
            MineRoom selected = MineIdleDestinationSelector.select(network.rooms(), position.x,position.y,position.z,
                runtime.idleRoomId, runtime.failedIdleRooms, room -> {
                    BlockType observed = loadedBlockType(world, room.position());
                    return observed == null || observed == BlockType.EMPTY;
                });
            if (selected == null) return null;
            runtime.idleRoomId = selected.id();
            return new WorldPosition(selected.position().x()+.5, selected.position().y(),selected.position().z()+.5);
        }
        @Override public void idle(boolean atCapacity) {
            runtime.waitingForCapacity = atCapacity;
            workerState(mine.id(),ref,worker,runtime,WorkerDebugState.IDLE,atCapacity ? "WAIT_CAPACITY" : "NO_AVAILABLE_TASK");
            String fingerprint = atCapacity + ":" + plan.fronts.size() + ":" + plan.rooms.size();
            if (!fingerprint.equals(runtime.lastNoTaskFingerprint)) {
                runtime.lastNoTaskFingerprint = fingerprint;
                decisionSink.record(mine.id(),null,MineDecisionCategory.WORKER, "NO_AVAILABLE_TASK",
                    "npc",workerLabel(ref,worker), "atCapacity",atCapacity);
            }
        }
        @Override public void restFailed(WorldPosition target) {
            if (runtime.idleRoomId != null) runtime.failedIdleRooms.add(runtime.idleRoomId);
            runtime.idleRoomId = null;
        }
        @Override public void ended(MinerWorkController.Task task, MinerWorkController.End reason) {
            var disposition = MinerWorkController.disposition(task, reason);
            stopMiningAnimation(ref, store, runtime); stopBuildingAnimation(ref, store, runtime);
            workerTaskEnded(mine.id(), ref, worker, runtime, reason.name());
            if (task.kind() == MineNormalTaskSelector.Kind.TUNNEL_FRONT) {
                RuntimeFrontPlan front = plan.fronts.get(task.id());
                if (front != null && !front.complete && disposition == MinerWorkController.Disposition.ADVANCE
                    && sliceComplete(world, front.slices.get(front.sliceIndex))) completeCurrentSlice(world, mine, plan, front);
                else if (front != null && disposition == MinerWorkController.Disposition.BLOCK_FRONT) {
                    if (!scheduleNearbyRecoveryStep(world, mine, plan, front))
                        failFront(world,mine,front,currentFront(world.getWorldConfig().getUuid(),mine.id(),front.frontId),
                            MineObstaclePolicy.FailureKind.NAVIGATION_UNREACHABLE,"NATIVE_NAVIGATION_UNREACHABLE");
                } else if (front != null && disposition == MinerWorkController.Disposition.ABANDON_FRONT)
                    failFront(world,mine,front,currentFront(world.getWorldConfig().getUuid(),mine.id(),front.frontId),
                        MineObstaclePolicy.FailureKind.UNSAFE_GEOMETRY,reason.name());
            } else if (task.kind() == MineNormalTaskSelector.Kind.ROOM) {
                RuntimeRoomPlan roomPlan = plan.rooms.get(task.id());
                MineRoom room = currentRoom(world.getWorldConfig().getUuid(),mine.id(),task.id());
                if (room != null && roomPlan != null && disposition == MinerWorkController.Disposition.ADVANCE) {
                    if (room.state() == MineRoom.State.EXCAVATING)
                        completeRoomExcavationUnit(world,mine,room,roomPlan,room.excavationWorkUnitIndex(),runtime,ref,store);
                    else if (room.state() == MineRoom.State.BUILT) clearNormalTaskAge(world,mine,room.id());
                } else if (roomPlan != null && disposition == MinerWorkController.Disposition.DISABLE_ROOM) roomPlan.unavailable = true;
            } else {
                RuntimeInfrastructureTask infrastructure = plan.infrastructureTasks.get(task.id());
                if (infrastructure != null && disposition == MinerWorkController.Disposition.ADVANCE)
                    completeInfrastructureTask(world,mine,infrastructure,worker,runtime,ref,store,"COMPLETED");
                else if (infrastructure != null && disposition != MinerWorkController.Disposition.KEEP) {
                    if (disposition == MinerWorkController.Disposition.SKIP_OPTIONAL) completeInfrastructureTask(world,mine,infrastructure,worker,runtime,ref,store,"SKIPPED_"+reason);
                    else {
                        RuntimeFrontPlan front = frontForTunnel(plan,infrastructure.task.tunnelId());
                        if (front != null) failFront(world,mine,front,currentFront(world.getWorldConfig().getUuid(),mine.id(),front.frontId),
                            disposition == MinerWorkController.Disposition.BLOCK_FRONT ? MineObstaclePolicy.FailureKind.NAVIGATION_UNREACHABLE
                                : MineObstaclePolicy.FailureKind.MANDATORY_INFRASTRUCTURE_UNRESOLVABLE,reason.name());
                    }
                }
            }
            runtime.clearWorkAssignment();
        }
    }

    private void refreshBridgeTasks(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan
    ) {
        long civProfilingStarted = CivPerformanceRecorder.beginMeasured();
        try {
        MineNetwork network = tunnelRegistry.networkForMine(
            world.getWorldConfig().getUuid(), mine.id()
        );
        if (network == null) return;

        for (RuntimeFrontPlan front : minePlan.fronts.values()) {
            if (front.complete || front.unavailable) continue;

            MineWorkFront persistedFront = currentFront(
                world.getWorldConfig().getUuid(), mine.id(), front.frontId
            );
            if (persistedFront == null || !available(persistedFront)) {
                front.unavailable = true;
                continue;
            }

            if (hasFluidInNavigationCorridor(world, front.slices.get(front.sliceIndex))) {
                failFront(
                    world,
                    mine,
                    front,
                    persistedFront,
                    MineObstaclePolicy.FailureKind.HAZARDOUS_FLUID,
                    "FLUID_IN_NAVIGATION_CORRIDOR"
                );
                continue;
            }

            // Several fronts may point into the same tunnel. A bridge already being
            // built for the current slice must finish before another front inspects
            // its intermediate floor state or creates a conflicting overlapping task.
            boolean bridgeInProgress = minePlan.infrastructureTasks.values().stream()
                .anyMatch(existing -> !existing.completed
                    && existing.task.type() == MineInfrastructureTask.Type.BUILD_BRIDGE
                    && existing.task.tunnelId().equals(front.tunnelId)
                    // A single tunnel has one advancing excavation front. Multiple
                    // concurrent bridge spans can overlap spatially even when their
                    // slice indexes do not, due to diagonal cross beams.
                );
            if (bridgeInProgress) continue;

            BridgeAssessment assessment = assessBridge(world, front);
            if (assessment.abandonReason() != null) {
                failFront(
                    world,
                    mine,
                    front,
                    persistedFront,
                    assessment.hazardousFluid()
                        ? MineObstaclePolicy.FailureKind.HAZARDOUS_FLUID
                        : MineObstaclePolicy.FailureKind.UNSAFE_GEOMETRY,
                    assessment.abandonReason()
                );
                continue;
            }

            MineInfrastructureTask task = assessment.task();
            if (task == null || minePlan.infrastructureTasks.containsKey(task.id())) continue;
            minePlan.infrastructureTasks.put(
                task.id(),
                new RuntimeInfrastructureTask(
                    task,
                    front.tunnelKind,
                    geometryFor(front),
                    network.infrastructureTaskCompleted(task.id())
                )
            );
            decisionSink.record(
                mine.id(), task.id(), MineDecisionCategory.ENVIRONMENT, "INFRASTRUCTURE_CREATED",
                "type", task.type(),
                "tunnel", task.tunnelId(),
                "startSlice", task.startSliceIndex(),
                "endSlice", task.endSliceIndex(),
                "floorCenter", front.slices.get(front.sliceIndex).floorCenter(),
                "floorBlock", new BlockPosition(
                    task.anchor().x(), task.anchor().y() - 1, task.anchor().z()
                ),
                "floorBlockType", String.valueOf(loadedBlockType(world, new BlockPosition(
                    task.anchor().x(), task.anchor().y() - 1, task.anchor().z()
                )))
            );
        }
        } finally {
            CivPerformanceRecorder.endMeasured("miner.bridges", civProfilingStarted);
        }
    }

    private BridgeAssessment assessBridge(World world, RuntimeFrontPlan front) {
        int start = front.sliceIndex;
        if (start <= 0 || start >= front.slices.size() - 2) {
            return BridgeAssessment.none();
        }
        // A missing support voxel inside still-solid rock is not yet an
        // actionable bridge. Clear the authored walking cell through normal
        // excavation first; otherwise bridge beams can be placed in stone.
        BlockType currentWalkCell = loadedBlockType(
            world, front.slices.get(start).floorCenter()
        );
        if (currentWalkCell == null || !isEmpty(currentWalkCell)) {
            return BridgeAssessment.none();
        }
        if (!floorMissing(world, front.slices.get(start))) return BridgeAssessment.none();
        if (front.slices.get(start - 1).floorCenter().y()
            != front.slices.get(start).floorCenter().y()) {
            return BridgeAssessment.none();
        }
        // A one-block empty support at the authored floor can be ordinary stepped
        // terrain, not a chasm. Never turn a shallow drop into mandatory bridge work.
        if (hasShallowSolidGround(world, front.slices.get(start))) {
            return BridgeAssessment.none();
        }
        if (floorMissing(world, front.slices.get(start - 1))) {
            return BridgeAssessment.abandon("UNSAFE_GAP_WITHOUT_APPROACH", false);
        }

        int end = start;
        boolean fluid = hasFluidBelow(world, front.slices.get(start));
        boolean lava = hasLavaBelow(world, front.slices.get(start));
        while (end + 1 < front.slices.size()
            && floorMissing(world, front.slices.get(end + 1))) {
            end++;
            if (front.slices.get(end - 1).floorCenter().y()
                != front.slices.get(end).floorCenter().y()) {
                return BridgeAssessment.none();
            }
            if (hasShallowSolidGround(world, front.slices.get(end))) {
                return BridgeAssessment.none();
            }
            fluid |= hasFluidBelow(world, front.slices.get(end));
            lava |= hasLavaBelow(world, front.slices.get(end));
            if (end - start + 1 > MAX_BRIDGE_SPAN) {
                return BridgeAssessment.abandon("GAP_EXCEEDS_BRIDGE_RANGE", lava);
            }
        }

        if (lava) return BridgeAssessment.abandon("LAVA_GAP", true);

        int span = end - start + 1;
        int maxSpan = fluid ? MAX_FLUID_BRIDGE_SPAN : MAX_BRIDGE_SPAN;
        if (span <= 0 || span > maxSpan) {
            return BridgeAssessment.abandon("GAP_EXCEEDS_SAFE_BRIDGE_RANGE", false);
        }

        int landing = end + 1;
        // Bridge beams and deck follow a single walk elevation. Stepped authored
        // slices cannot be solved by this structure, even with a solid far landing.
        if (!MineBridgeTerrainPolicy.isLevelAcross(
            front.slices.stream().mapToInt(slice -> slice.floorCenter().y()).toArray(),
            Math.max(0, start - 1),
            Math.min(front.slices.size() - 1, landing + 1)
        )) {
            return BridgeAssessment.none();
        }

        if (landing >= front.slices.size()
            || !hasSafeOppositeLanding(world, front.slices.get(landing))) {
            return BridgeAssessment.abandon("GAP_WITHOUT_SAFE_LANDING", false);
        }
        if (landing + 1 >= front.slices.size()) {
            return BridgeAssessment.abandon("GAP_WITHOUT_PLANNED_CONTINUATION", false);
        }

        // Extend the actual construction footprint across both landings; only
        // empty/deco voxels are placed into, existing natural terrain stays.
        int buildStart = Math.max(1, start - BRIDGE_LANDING_OVERLAP_SLICES);
        int buildEnd = Math.min(front.slices.size() - 1,
            landing + BRIDGE_LANDING_OVERLAP_SLICES);
        return BridgeAssessment.bridge(MineInfrastructurePlanner.bridgeTask(
            front.tunnelId,
            buildStart,
            buildEnd,
            front.slices.get(start).floorCenter()
        ));
    }

    private static boolean hasSafeOppositeLanding(
        World world,
        MineTunnelGeometry.Slice slice
    ) {
        int walkY = slice.floorCenter().y();
        Set<String> safeColumns = new HashSet<>();
        Set<String> requiredColumns = new HashSet<>();

        for (BlockPosition block : slice.navigationCoreBlocks()) {
            if (block.y() != walkY) continue;
            String column = block.x() + ":" + block.z();
            if (!requiredColumns.add(column)) continue;

            BlockType walkType = loadedBlockType(world, block);
            BlockPosition below = new BlockPosition(block.x(), block.y() - 1, block.z());
            BlockType floorType = loadedBlockType(world, below);
            WorldChunk chunk = world.getChunkIfLoaded(
                ChunkUtil.indexChunkFromBlock(block.x(), block.z())
            );
            // The landing belongs to the next authored excavation slice. Its
            // walk column may still be solid rock, which the miner will clear.
            // Require an actual solid floor and no fluid, but do not require
            // the future tunnel air space to have already been excavated.
            if (walkType != null
                && floorType != null
                && !isEmpty(floorType)
                && chunk != null
                && chunk.getFluidId(block.x(), block.y(), block.z()) == Fluid.EMPTY_ID) {
                safeColumns.add(column);
            }
        }

        int required = Math.min(3, requiredColumns.size());
        return required > 0 && safeColumns.size() >= required;
    }

    private static boolean bridgeDeckComplete(
        World world, RuntimeInfrastructureTask infrastructure
    ) {
        List<MineTunnelGeometry.Slice> slices = infrastructure.geometry.slices();
        int start = infrastructure.task.startSliceIndex();
        int end = infrastructure.task.endSliceIndex();
        if (start < 0 || end < start || end >= slices.size()) return false;
        for (int index = start; index <= end; index++) {
            MineTunnelGeometry.Slice slice = slices.get(index);
            int walkY = slice.floorCenter().y();
            boolean foundWalkColumn = false;
            for (BlockPosition walk : slice.navigationCoreBlocks()) {
                if (walk.y() != walkY) continue;
                foundWalkColumn = true;
                BlockType floor = loadedBlockType(
                    world, new BlockPosition(walk.x(), walk.y() - 1, walk.z())
                );
                if (floor == null || isEmpty(floor)) return false;
            }
            if (!foundWalkColumn) return false;
        }
        return true;
    }

    private static boolean floorMissing(World world, MineTunnelGeometry.Slice slice) {
        BlockPosition center = slice.floorCenter();
        BlockPosition floor = new BlockPosition(center.x(), center.y() - 1, center.z());
        BlockType type = loadedBlockType(world, floor);
        return type != null && isEmpty(type);
    }

    private static boolean hasShallowSolidGround(
        World world,
        MineTunnelGeometry.Slice slice
    ) {
        BlockPosition center = slice.floorCenter();
        // A one- or two-block depression is terrain to navigate/excavate, not an
        // actual deep span needing a mandatory bridge.
        for (int depth = 2; depth <= 3; depth++) {
            BlockType ground = loadedBlockType(world, new BlockPosition(
                center.x(), center.y() - depth, center.z()
            ));
            if (ground == null) return true; // unknown world state is not a proven gap
            if (!isEmpty(ground)) return true;
        }
        return false;
    }

    private static boolean hasFluidBelow(World world, MineTunnelGeometry.Slice slice) {
        BlockPosition center = slice.floorCenter();
        WorldChunk chunk = world.getChunkIfLoaded(
            ChunkUtil.indexChunkFromBlock(center.x(), center.z())
        );
        if (chunk == null) return false;
        for (int depth = 1; depth <= 4; depth++) {
            if (chunk.getFluidId(center.x(), center.y() - depth, center.z()) != Fluid.EMPTY_ID) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLavaBelow(World world, MineTunnelGeometry.Slice slice) {
        BlockPosition center = slice.floorCenter();
        WorldChunk chunk = world.getChunkIfLoaded(
            ChunkUtil.indexChunkFromBlock(center.x(), center.z())
        );
        if (chunk == null) return false;
        for (int depth = 1; depth <= 4; depth++) {
            if (isLavaFluid(chunk.getFluidId(center.x(), center.y() - depth, center.z()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFluidInNavigationCorridor(
        World world,
        MineTunnelGeometry.Slice slice
    ) {
        for (BlockPosition block : slice.navigationCoreBlocks()) {
            WorldChunk chunk = world.getChunkIfLoaded(
                ChunkUtil.indexChunkFromBlock(block.x(), block.z())
            );
            if (chunk == null) continue;
            if (chunk.getFluidId(block.x(), block.y(), block.z()) != Fluid.EMPTY_ID) return true;
        }
        return false;
    }

    private static boolean isLavaFluid(int fluidId) {
        if (fluidId == Fluid.EMPTY_ID) return false;
        Fluid fluid = Fluid.getAssetMap().getAssetOrDefault(fluidId, Fluid.UNKNOWN);
        return fluid != null && fluid.hasEffect(ShaderType.Lava);
    }

    private static Vector3d safeBuildProbe(World world, Vector3d anchor, int[] offset) {
            int y = (int) Math.floor(anchor.y);
            int x = (int) Math.floor(anchor.x) + offset[0];
            int z = (int) Math.floor(anchor.z) + offset[1];
            BlockType feet = loadedBlockType(world, new BlockPosition(x, y, z));
            BlockType head = loadedBlockType(world, new BlockPosition(x, y + 1, z));
            BlockType ground = loadedBlockType(world, new BlockPosition(x, y - 1, z));
            if (feet == null || !isEmpty(feet) || head == null || !isEmpty(head)
                || ground == null || isEmpty(ground)) return null;
            WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
            if (chunk == null || chunk.getFluidId(x, y, z) != Fluid.EMPTY_ID) return null;
            return new Vector3d(x + 0.5, y, z + 0.5);
    }

    private RuntimeFrontPlan blockingExcavationFront(
        World world,
        RuntimeMinePlan minePlan,
        RuntimeInfrastructureTask infrastructure,
        WorkerRuntime runtime
    ) {
        if (infrastructure.task.mandatory() || runtime.resolvedInfrastructure == null) return null;
        for (MineInfrastructurePlacementResolver.PlacementStep placement
            : runtime.resolvedInfrastructure.placements()) {
            for (RuntimeFrontPlan front : minePlan.fronts.values()) {
                if (front.complete || front.unavailable) continue;
                // Only unfinished excavation is protected; never use authored rock walls
                // as a reason to defer infrastructure indefinitely.
                for (int i = front.sliceIndex; i < front.orderedBlocks.size(); i++) {
                    if (MinePlacementExcavationGuard.conflicts(
                        placement.position(),
                        front.orderedBlocks.get(i),
                        block -> {
                            BlockType type = loadedBlockType(world, block);
                            return type != null && !isEmpty(type);
                        }
                    )) return front;
                }
            }
        }
        return null;
    }

    private void completeInfrastructureTask(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeInfrastructureTask infrastructure,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime,
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        String outcome
    ) {
        infrastructure.completed = true;
        MineNetwork network = tunnelRegistry.networkForMine(
            world.getWorldConfig().getUuid(), mine.id()
        );
        if (network != null && !network.infrastructureTaskCompleted(infrastructure.task.id())) {
            tunnelRegistry.putNetwork(
                world, network.withInfrastructureTaskCompleted(infrastructure.task.id())
            );
        }

        workerTaskEnded(mine.id(), ref, workerKey, runtime, outcome);
        infrastructureReservations.remove(infrastructure.task.id(), workerKey);
        unitRegistry.clearMoveTarget(ref);
        stopBuildingAnimation(ref, store, runtime);
        runtime.clearInfrastructureAssignment();
        decisionSink.record(
            mine.id(), infrastructure.task.id(), MineDecisionCategory.PLANNING,
            "WORK_UNIT_COMPLETED",
            "type", infrastructure.task.type(),
            "outcome", outcome
        );
    }

    private boolean hasPendingMandatoryInfrastructure(
        RuntimeMinePlan minePlan,
        RuntimeFrontPlan front
    ) {
        for (RuntimeInfrastructureTask infrastructure : minePlan.infrastructureTasks.values()) {
            if (infrastructure.completed
                || !infrastructure.task.mandatory()
                || !infrastructure.task.tunnelId().equals(front.tunnelId)) {
                continue;
            }
            if (infrastructureAvailable(infrastructure.task, front)) return true;
        }
        return false;
    }

    private static boolean infrastructureAvailable(
        MineInfrastructureTask task,
        RuntimeFrontPlan front
    ) {
        return MineInfrastructureAvailability.isAvailable(
            task.type(),
            task.startSliceIndex(),
            task.endSliceIndex(),
            front.sliceIndex,
            front.complete
        );
    }

    private static RuntimeFrontPlan frontForTunnel(
        RuntimeMinePlan minePlan,
        UUID tunnelId
    ) {
        for (RuntimeFrontPlan front : minePlan.fronts.values()) {
            if (front.tunnelId.equals(tunnelId)) return front;
        }
        return null;
    }

    private static MineTunnelGeometry geometryFor(RuntimeFrontPlan front) {
        return front.geometry;
    }

    private static double squaredDistance(Vector3d position, BlockPosition block) {
        double dx = position.x - (block.x() + 0.5);
        double dy = position.y - block.y();
        double dz = position.z - (block.z() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private void releaseInfrastructureReservation(
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        if (runtime == null || runtime.infrastructureTaskId == null) return;
        infrastructureReservations.remove(runtime.infrastructureTaskId, workerKey);
        runtime.clearInfrastructureAssignment();
    }

    private List<MineNormalTaskSelector.Candidate> normalCandidates(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        Vector3d position,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        if (network == null) return List.of();

        int totalFronts = minePlan.fronts.size();
        int totalRooms = minePlan.rooms.size();
        int totalInfrastructure = minePlan.infrastructureTasks.size();
        Map<String, Integer> supportSkipReasons = new LinkedHashMap<>();
        Map<String, Integer> decorationSkipReasons = new LinkedHashMap<>();
        List<UUID> availableDecorations = new ArrayList<>();
        List<MineNormalTaskSelector.Candidate> candidates = new ArrayList<>();
        for (RuntimeFrontPlan candidate : minePlan.fronts.values()) {
            if (candidate.complete) continue;
            MineWorkFront front = workFrontById(network, candidate.frontId);
            if (!available(front) || !frontExecutable(world, minePlan, candidate)) continue;
            int priority = candidate.tunnelKind == MineTunnel.Kind.BRANCH
                ? MineFrontTaskScheduler.BRANCH_TUNNEL_PRIORITY
                : MineFrontTaskScheduler.MAIN_TUNNEL_PRIORITY;
            candidates.add(new MineNormalTaskSelector.Candidate(
                front.id(),
                MineNormalTaskSelector.Kind.TUNNEL_FRONT,
                priority,
                frontCoordinator.workerCount(front.id()),
                MineFrontCoordinator.capacityFor(candidate.tunnelKind),
                front.position()
            ));
        }

        int activeRooms = activeRoomCount(network);
        for (RuntimeRoomPlan roomPlan : minePlan.rooms.values()) {
            MineRoom room = roomById(network, roomPlan.roomId);
            if (!roomExecutable(world, minePlan, roomPlan, room, activeRooms)) continue;
            candidates.add(new MineNormalTaskSelector.Candidate(
                room.id(),
                MineNormalTaskSelector.Kind.ROOM,
                MineRoomPlanner.ROOM_PRIORITY,
                roomCoordinator.workerCount(room.id()),
                roomCapacity(room),
                room.position()
            ));
        }

        for (RuntimeInfrastructureTask infrastructure : minePlan.infrastructureTasks.values()) {
            boolean support = infrastructure.task.type() == MineInfrastructureTask.Type.BUILD_SUPPORT;
            boolean decoration = infrastructure.task.decoration();
            if (infrastructure.completed) {
                if (support) supportSkipReasons.merge("COMPLETED", 1, Integer::sum);
                if (decoration) decorationSkipReasons.merge("COMPLETED", 1, Integer::sum);
                continue;
            }
            if (infrastructure.task.mandatory()) {
                if (support) supportSkipReasons.merge("MANDATORY_PATH", 1, Integer::sum);
                if (decoration) decorationSkipReasons.merge("MANDATORY_PATH", 1, Integer::sum);
                continue;
            }
            RuntimeFrontPlan front = frontForTunnel(minePlan, infrastructure.task.tunnelId());
            if (front == null) {
                if (support) supportSkipReasons.merge("FRONT_MISSING", 1, Integer::sum);
                if (decoration) decorationSkipReasons.merge("FRONT_MISSING", 1, Integer::sum);
                continue;
            }
            if (front.unavailable) {
                if (support) supportSkipReasons.merge("FRONT_UNAVAILABLE", 1, Integer::sum);
                if (decoration) decorationSkipReasons.merge("FRONT_UNAVAILABLE", 1, Integer::sum);
                continue;
            }
            if (!infrastructureAvailable(infrastructure.task, front)) {
                if (support) supportSkipReasons.merge("NOT_YET_AVAILABLE", 1, Integer::sum);
                if (decoration) decorationSkipReasons.merge("NOT_YET_AVAILABLE", 1, Integer::sum);
                continue;
            }
            RuntimeFrontPlan blockingFront = minePlan.fronts.get(infrastructure.deferredByFrontId);
            if (blockingFront != null && !blockingFront.complete && !blockingFront.unavailable
                && infrastructure.deferredAtSlice >= blockingFront.sliceIndex) {
                if (support) supportSkipReasons.merge("NEAR_PENDING_EXCAVATION", 1, Integer::sum);
                if (decoration) decorationSkipReasons.merge("NEAR_PENDING_EXCAVATION", 1, Integer::sum);
                continue;
            }
            int workers = infrastructureReservations.containsKey(infrastructure.task.id()) ? 1 : 0;
            if (support && workers >= 1) {
                supportSkipReasons.merge("RESERVED", 1, Integer::sum);
            }
            if (decoration) {
                if (workers >= 1) decorationSkipReasons.merge("RESERVED", 1, Integer::sum);
                else availableDecorations.add(infrastructure.task.id());
            }
            candidates.add(new MineNormalTaskSelector.Candidate(
                infrastructure.task.id(),
                MineNormalTaskSelector.Kind.INFRASTRUCTURE,
                infrastructure.task.priority(),
                workers,
                1,
                infrastructure.task.anchor()
            ));
        }

        if (!decorationSkipReasons.isEmpty()) {
            String fingerprint = decorationSkipReasons.toString();
            if (!fingerprint.equals(runtime.lastDecorationDecisionFingerprint)) {
                runtime.lastDecorationDecisionFingerprint = fingerprint;
                decisionSink.record(mine.id(),null,MineDecisionCategory.WORKER,"DECORATION_TASK_SKIPPED",
                    "reasons",decorationSkipReasons);
            }
        }
        if (!supportSkipReasons.isEmpty()) {
            String fingerprint = supportSkipReasons.toString();
            if (!fingerprint.equals(runtime.lastSupportDecisionFingerprint)) {
                runtime.lastSupportDecisionFingerprint = fingerprint;
                decisionSink.record(mine.id(),null,MineDecisionCategory.WORKER,"BUILD_SUPPORT_TASK_SKIPPED",
                    "reasons",supportSkipReasons);
            }
        }
        return candidates;
    }

    private boolean roomExecutable(
        World world,
        RuntimeMinePlan minePlan,
        RuntimeRoomPlan roomPlan,
        MineRoom room,
        int activeRooms
    ) {
        if (room == null || room.terminal() || roomPlan.unavailable) return false;
        if (room.state() == MineRoom.State.PLANNED && activeRooms >= MineRoomPlanner.MAX_ACTIVE_ROOMS) {
            return false;
        }
        RuntimeFrontPlan tunnel = frontForTunnel(minePlan, room.tunnelId());
        if (tunnel == null || room.attachmentSliceIndex() >= tunnel.slices.size()) return false;
        if (!sliceComplete(world, tunnel.slices.get(room.attachmentSliceIndex()))) return false;
        if (room.state() == MineRoom.State.READY_TO_BUILD
            && MineRoomPrefabService.sectionCount(room) <= 0) {
            return false;
        }
        return true;
    }

    private static int activeRoomCount(MineNetwork network) {
        int count = 0;
        for (MineRoom room : network.rooms()) {
            if (room.state() == MineRoom.State.EXCAVATING
                || room.state() == MineRoom.State.READY_TO_BUILD) count++;
        }
        return count;
    }

    private static int roomCapacity(MineRoom room) {
        return room.state() == MineRoom.State.READY_TO_BUILD
            ? MineRoomPlanner.BUILD_CAPACITY
            : MineRoomPlanner.EXCAVATION_CAPACITY;
    }

    private void completeRoomExcavationUnit(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineRoom room,
        RuntimeRoomPlan plan,
        int completedUnit,
        WorkerRuntime runtime,
        Ref<EntityStore> ref,
        Store<EntityStore> store
    ) {
        MineRoom updated = room.completeExcavationUnit(plan.geometry.excavationWorkUnits().size());
        MineRoom.State nextState = updated.state();
        persistRoom(world, mine, updated);
        clearNormalTaskAge(world, mine, room.id());
        roomCoordinator.releaseRoom(room.id());
        workerTaskEnded(mine.id(), ref, unitRegistry.keyOf(ref), runtime, "WORK_UNIT_COMPLETED");
        stopMiningAnimation(ref, store, runtime);
        runtime.clearRoomAssignment();
        decisionSink.record(
            mine.id(), room.id(), MineDecisionCategory.ROOM, "WORK_UNIT_COMPLETED",
            "type", "EXCAVATE_ROOM",
            "unit", completedUnit,
            "state", nextState
        );
    }

    private static boolean roomWorkUnitComplete(World world, List<BlockPosition> workUnit) {
        for (BlockPosition block : workUnit) {
            BlockType type = loadedBlockType(world, block);
            if (type == null || !isEmpty(type)) return false;
        }
        return true;
    }

    private boolean containsBlockedSolid(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        List<BlockPosition> blocks
    ) {
        for (BlockPosition block : blocks) {
            BlockType type = loadedBlockType(world, block);
            if (type == null || isEmpty(type)) continue;
            if (!safeBlock(world, mine, block)) return true;
        }
        return false;
    }

    private void clearNormalTaskAge(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        UUID taskId
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        if (network != null && network.normalTaskPriorityBonus(taskId) > 0) {
            tunnelRegistry.putNetwork(world, network.withoutNormalTaskPriorityBonus(taskId));
        }
    }

    private void persistRoom(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineRoom room
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        if (network != null) tunnelRegistry.putNetwork(world, network.withRoom(room));
    }

    /** Debug-only immediate horizon refresh, no NPC restart or plan reset. */
    public int refreshMainPlans(World world, UUID mineId) {
        if (world == null || mineId == null) return 0;
        UUID worldId = world.getWorldConfig().getUuid();
        MineNetwork network = tunnelRegistry.networkForMine(worldId, mineId);
        if (network == null) return 0;
        RuntimeMinePlan runtime = runtimePlans.get(new WorldMineKey(worldId, mineId));
        if (runtime == null) return 0;
        long now = System.currentTimeMillis();
        int count = 0;
        for (RuntimeFrontPlan front : runtime.fronts.values()) {
            if (front.tunnelKind != MineTunnel.Kind.MAIN || front.complete) continue;
            MineGenerationProgress progress = network.progressFor(front.tunnelId);
            if (progress == null || progress.unlockedSlices() >= front.slices.size()) continue;
            int next = Math.min(front.slices.size(),
                progress.unlockedSlices() + MAIN_PLANNING_BATCH_SLICES);
            network = network.withGenerationProgress(front.tunnelId,
                progress.advance(next, now + MineGenerationPolicy.REFRESH_INTERVAL_MILLIS));
            front.unlockedSlices = next;
            count++;
        }
        if (count > 0) tunnelRegistry.putNetwork(world, network);
        return count;
    }

    private void refreshMainPlanning(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan runtime
    ) {
        long civProfilingStarted = CivPerformanceRecorder.beginMeasured();
        try {
        MineNetwork network = tunnelRegistry.networkForMine(
            world.getWorldConfig().getUuid(), mine.id()
        );
        if (network == null) return;
        long now = System.currentTimeMillis();
        for (RuntimeFrontPlan front : runtime.fronts.values()) {
            if (front.tunnelKind != MineTunnel.Kind.MAIN || front.complete) continue;
            MineGenerationProgress progress = network.progressFor(front.tunnelId);
            if (progress == null || !MineGenerationPolicy.shouldRefresh(
                front.sliceIndex, progress.unlockedSlices(), front.slices.size(),
                now, progress.nextRefreshAtMillis()
            )) continue;
            int next = Math.min(front.slices.size(),
                progress.unlockedSlices() + MAIN_PLANNING_BATCH_SLICES);
            MineGenerationProgress updated = progress.advance(
                next, now + MineGenerationPolicy.REFRESH_INTERVAL_MILLIS
            );
            network = network.withGenerationProgress(front.tunnelId, updated);
            front.unlockedSlices = next;
            world.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "Mine: " + (next - progress.unlockedSlices())
                    + " weitere Hauptstollenabschnitte freigegeben."
            ));
            decisionSink.record(
                mine.id(), front.tunnelId, MineDecisionCategory.PLANNING, "MAIN_PLAN_REFRESHED",
                "unlockedSlices", next,
                "trigger", now >= progress.nextRefreshAtMillis() ? "PERIODIC" : "LOW_REMAINING",
                "totalSlices", front.slices.size(),
                "nextRefreshAtMillis", updated.nextRefreshAtMillis()
            );
        }
        if (!network.equals(tunnelRegistry.networkForMine(
                world.getWorldConfig().getUuid(), mine.id()))) {
            tunnelRegistry.putNetwork(world, network);
        }
        } finally {
            CivPerformanceRecorder.endMeasured("miner.planning", civProfilingStarted);
        }
    }

    private static MineNetworkGrowthPlanner.Plan clippedInitialPlan(
        MineNetworkGrowthPlanner.Plan original
    ) {
        List<MineNetworkGrowthPlanner.PlannedTunnel> accepted = new ArrayList<>();
        Map<UUID, MineNetworkGrowthPlanner.PlannedTunnel> byId = new LinkedHashMap<>();
        MineNetwork network = null;
        for (MineNetworkGrowthPlanner.PlannedTunnel tunnel : original.tunnels()) {
            MineNetworkGrowthPlanner.PlannedTunnel chosen = tunnel;
            if (tunnel.tunnel().kind() == MineTunnel.Kind.MAIN) {
                MineTunnelPath path = MineGenerationPolicy.capAtMinimumY(tunnel.path());
                chosen = new MineNetworkGrowthPlanner.PlannedTunnel(
                    tunnel.tunnel(), path, MineTunnelVoxelizer.voxelize(path), false
                );
                network = MineNetwork.create(original.network().mineId(),
                    tunnel.tunnel().id(), tunnel.tunnel().origin());
            } else {
                MineNetworkGrowthPlanner.PlannedTunnel parent = byId.get(tunnel.tunnel().parentTunnelId());
                if (parent == null) continue;
                BlockPosition start = tunnel.tunnel().origin();
                boolean attached = parent.geometry().slices().stream().anyMatch(slice -> {
                    BlockPosition at = slice.floorCenter();
                    return Math.abs(at.x() - start.x()) <= 1
                        && Math.abs(at.y() - start.y()) <= 1
                        && Math.abs(at.z() - start.z()) <= 1;
                });
                if (!attached) continue;
                network = network.withTunnel(tunnel.tunnel());
            }
            accepted.add(chosen);
            byId.put(chosen.tunnel().id(), chosen);
        }
        return new MineNetworkGrowthPlanner.Plan(
            network, accepted, original.seed(), original.planningBudget()
        );
    }

    private RuntimeMinePlan ensureRuntimePlan(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        PrefabPlacementService.PlacedMarker connector
    ) {
        WorldMineKey key = new WorldMineKey(world.getWorldConfig().getUuid(), mine.id());
        RuntimeMinePlan existingRuntime = runtimePlans.get(key);
        if (existingRuntime != null) return existingRuntime;

        MineHeading heading = outwardHeading(mine.bounds(), connector.bounds());
        BlockPosition origin = initialCenterlineOrigin(connector.bounds(), heading);
        long seed = planningSeed(mine.id());
        boolean dwarven = MineBuildingTypes.isDwarven(mine.buildingType());
        MineNetworkGrowthPlanner.Plan planned = dwarven
            ? DwarvenMinePlanner.plan(
                mine.id(), origin, heading, 192, RUNTIME_PLANNING_TUNNEL_BUDGET, seed
            )
            : MineNetworkGrowthPlanner.plan(
                mine.id(), origin, heading, MAIN_PLAN_LENGTH_BLOCKS,
                RUNTIME_PLANNING_TUNNEL_BUDGET, seed, decisionSink
            );
        if (!dwarven) planned = clippedInitialPlan(planned);
        List<MineRoom> plannedRooms = MineRoomPlanner.plan(planned, decisionSink);

        MineNetwork persisted = tunnelRegistry.networkForMine(key.worldId, mine.id());
        boolean newMine = persisted == null;
        List<MineNetworkGrowthPlanner.PlannedTunnel> allTunnels = new ArrayList<>(planned.tunnels());
        if (persisted != null) {
            for (MineTunnel additional : persisted.tunnels()) {
                if (additional.kind() != MineTunnel.Kind.MAIN
                    || additional.id().equals(planned.network().mainTunnelId())) continue;
                MineGenerationProgress progress = persisted.progressFor(additional.id());
                if (progress == null) {
                    decisionSink.record(mine.id(), additional.id(),
                        MineDecisionCategory.PLANNING, "MINE_PLAN_INCOMPATIBLE",
                        "reason", "MISSING_GENERATION_PROGRESS");
                    return null;
                }
                MineTunnelPath path = MineGenerationPolicy.capAtMinimumY(MinePathPlanner.plan(
                    MineTunnel.Kind.MAIN, additional.origin(), origin,
                    progress.heading(), MAIN_PLAN_LENGTH_BLOCKS, progress.seed()
                ));
                allTunnels.add(new MineNetworkGrowthPlanner.PlannedTunnel(
                    additional, path, MineTunnelVoxelizer.voxelize(path), false
                ));
            }
        }

        Map<UUID, UUID> frontIds = new LinkedHashMap<>();
        Map<UUID, MineTunnelGeometry> geometries = new LinkedHashMap<>();
        for (MineNetworkGrowthPlanner.PlannedTunnel tunnel : allTunnels) {
            frontIds.put(tunnel.tunnel().id(), frontId(mine.id(), tunnel.tunnel().id()));
            geometries.put(tunnel.tunnel().id(), tunnel.geometry());
        }

        if (persisted == null) {
            persisted = planned.network();
        } else if (!persisted.mainTunnelId().equals(planned.network().mainTunnelId())) {
            // Never discard saved player-world work to satisfy a changed deterministic plan.
            decisionSink.record(
                mine.id(), null, MineDecisionCategory.PLANNING, "MINE_PLAN_INCOMPATIBLE",
                "reason", "MAIN_TUNNEL_ID_CHANGED",
                "persistedMain", persisted.mainTunnelId(),
                "expectedMain", planned.network().mainTunnelId()
            );
            return null;
        }

        // Merge only missing semantic tasks. Future generations may add extra tunnels,
        // rooms and fronts; they are not a reason to replace the persisted network.
        MineNetwork merged = persisted;
        for (MineNetworkGrowthPlanner.PlannedTunnel tunnel : allTunnels) {
            if (merged.tunnel(tunnel.tunnel().id()) == null) {
                merged = merged.withTunnel(tunnel.tunnel());
            }
            UUID id = frontIds.get(tunnel.tunnel().id());
            if (workFrontById(merged, id) == null) {
                MineTunnelGeometry.Slice firstSlice = tunnel.geometry().slices().getFirst();
                merged = merged.withWorkFront(new MineWorkFront(
                    id, tunnel.tunnel().id(), firstSlice.floorCenter(), MineWorkFront.State.OPEN
                ));
                decisionSink.record(
                    mine.id(), id, MineDecisionCategory.PLANNING, "FRONT_CREATED",
                    "tunnel", tunnel.tunnel().id(),
                    "kind", tunnel.tunnel().kind(),
                    "slice", 0,
                    "width", firstSlice.widthBlocks(),
                    "height", firstSlice.heightBlocks()
                );
            }
        }
        for (MineRoom room : plannedRooms) {
            if (roomById(merged, room.id()) == null) merged = merged.withRoom(room);
        }
        UUID initialMainId = planned.network().mainTunnelId();
        if (merged.progressFor(initialMainId) == null) {
            int initialUnlocked = newMine
                ? Math.min(MAIN_PLANNING_BATCH_SLICES, geometries.get(initialMainId).slices().size())
                : geometries.get(initialMainId).slices().size();
            merged = merged.withGenerationProgress(initialMainId, new MineGenerationProgress(
                initialUnlocked,
                System.currentTimeMillis() + MineGenerationPolicy.REFRESH_INTERVAL_MILLIS,
                heading, seed
            ));
        }
        if (!merged.equals(persisted)) {
            tunnelRegistry.putNetwork(world, merged);
        }
        persisted = merged;

        tunnelRegistry.putRuntimeGeometries(key.worldId, mine.id(), geometries);

        Map<UUID, RuntimeFrontPlan> fronts = new LinkedHashMap<>();
        for (MineNetworkGrowthPlanner.PlannedTunnel tunnel : allTunnels) {
            UUID id = frontIds.get(tunnel.tunnel().id());
            MineWorkFront persistedFront = workFrontById(persisted, id);
            int sliceIndex = sliceIndexForPosition(tunnel.geometry().slices(), persistedFront.position());
            if (sliceIndex < 0) sliceIndex = 0;
            fronts.put(id, new RuntimeFrontPlan(
                id,
                tunnel.tunnel().id(),
                tunnel.tunnel().kind(),
                tunnel.geometry(),
                tunnel.geometry().slices(),
                orderedBlocks(tunnel.geometry().slices()),
                sliceIndex,
                tunnel.tunnel().kind() == MineTunnel.Kind.MAIN
                    ? Math.min(tunnel.geometry().slices().size(),
                        Math.max(sliceIndex + 1, persisted.progressFor(tunnel.tunnel().id()).unlockedSlices()))
                    : tunnel.geometry().slices().size(),
                persistedFront.state() == MineWorkFront.State.COMPLETE,
                persistedFront.state() == MineWorkFront.State.BLOCKED
                    || persistedFront.state() == MineWorkFront.State.ABANDONED
            ));
        }

        Map<UUID, RuntimeRoomPlan> rooms = new LinkedHashMap<>();
        for (MineRoom plannedRoom : plannedRooms) {
            MineRoom persistedRoom = roomById(persisted, plannedRoom.id());
            if (persistedRoom == null) continue;
            MineTunnelGeometry tunnelGeometry = geometries.get(persistedRoom.tunnelId());
            if (tunnelGeometry == null) continue;
            rooms.put(
                persistedRoom.id(),
                new RuntimeRoomPlan(
                    persistedRoom.id(),
                    MineRoomGeometry.generate(persistedRoom, tunnelGeometry),
                    false
                )
            );
        }

        Map<UUID, RuntimeInfrastructureTask> infrastructureTasks = new LinkedHashMap<>();
        for (MineNetworkGrowthPlanner.PlannedTunnel tunnel : allTunnels) {
            if (dwarven) continue; // Dwarven arches are finished at excavation completion.
            for (MineInfrastructureTask task :
                MineInfrastructurePlanner.plan(
                    mine.id(),
                    tunnel.tunnel().id(),
                    tunnel.geometry(),
                    decisionSink
                )) {
                infrastructureTasks.put(
                    task.id(),
                    new RuntimeInfrastructureTask(
                        task,
                        tunnel.tunnel().kind(),
                        tunnel.geometry(),
                        persisted.infrastructureTaskCompleted(task.id())
                    )
                );
            }
        }

        RuntimeMinePlan runtime = new RuntimeMinePlan(
            planned.network().mainTunnelId(), fronts, rooms, infrastructureTasks
        );
        runtimePlans.put(key, runtime);
        return runtime;
    }

    private static boolean matchesPlan(
        MineNetwork persisted,
        MineNetwork planned,
        Map<UUID, UUID> frontIds
    ) {
        if (persisted == null || !persisted.mainTunnelId().equals(planned.mainTunnelId())) return false;
        Set<UUID> persistedTunnels = new HashSet<>();
        for (MineTunnel tunnel : persisted.tunnels()) persistedTunnels.add(tunnel.id());
        Set<UUID> plannedTunnels = new HashSet<>();
        for (MineTunnel tunnel : planned.tunnels()) plannedTunnels.add(tunnel.id());
        if (!persistedTunnels.equals(plannedTunnels)) return false;
        for (UUID frontId : frontIds.values()) {
            if (workFrontById(persisted, frontId) == null) return false;
        }
        return true;
    }

    private static UUID frontId(UUID mineId, UUID tunnelId) {
        return UUID.nameUUIDFromBytes(
            ("civ-mine-work-front:" + mineId + ":" + tunnelId).getBytes(StandardCharsets.UTF_8)
        );
    }

    private void advanceAlreadyExcavatedSlices(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan
    ) {
        for (RuntimeFrontPlan plan : minePlan.fronts.values()) {
            while (!plan.complete
                && !plan.unavailable
                && !hasPendingMandatoryInfrastructure(minePlan, plan)
                && (plan.tunnelKind != MineTunnel.Kind.MAIN || plan.sliceIndex < plan.unlockedSlices)
                && sliceComplete(world, plan.slices.get(plan.sliceIndex))) {
                completeCurrentSlice(world, mine, minePlan, plan);
                // A newly reached slice can expose a due stair/bridge on the next outer tick.
                // Stop here rather than skipping multiple semantic work boundaries at once.
                if (hasPendingMandatoryInfrastructure(minePlan, plan)) break;
            }
        }
    }

    private void completeCurrentSlice(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        RuntimeFrontPlan plan
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        if (network == null) return;
        MineWorkFront current = workFrontById(network, plan.frontId);
        if (current == null) return;

        int completedIndex = plan.sliceIndex;
        network = integrateNaturalCaveIfPresent(
            world, mine, minePlan, plan, completedIndex, network
        );

        if (MineBuildingTypes.isDwarven(mine.buildingType())) {
            // Immediately finish safe completed slices a few blocks behind the active
            // face. Stable IDs live in the same persisted infrastructure completion set.
            int latestSafe = completedIndex - 3;
            for (int index = Math.max(8, completedIndex - 20); index <= latestSafe; index++) {
                DwarvenMineFinishPlan.Feature feature =
                    DwarvenMineFinishPlan.at(plan.tunnelId, plan.geometry, index);
                if (feature == null || network.infrastructureTaskCompleted(feature.id())) continue;
                if (DwarvenMineFinishExecutor.finish(world, feature)) {
                    network = network.withInfrastructureTaskCompleted(feature.id());
                }
            }
        }

        int nextIndex = completedIndex + 1;
        MineWorkFront updated;
        if (nextIndex >= plan.slices.size()) {
            updated = new MineWorkFront(
                current.id(), current.tunnelId(), current.position(), MineWorkFront.State.COMPLETE
            );
            plan.complete = true;
        } else {
            plan.sliceIndex = nextIndex;
            updated = new MineWorkFront(
                current.id(),
                current.tunnelId(),
                plan.slices.get(nextIndex).floorCenter(),
                MineWorkFront.State.OPEN
            );
        }
        frontCoordinator.releaseFront(plan.frontId);
        network = network.withoutNormalTaskPriorityBonus(plan.frontId);
        tunnelRegistry.putNetwork(world, network.withWorkFront(updated));
        decisionSink.record(
            mine.id(), plan.frontId, MineDecisionCategory.PLANNING, "WORK_UNIT_COMPLETED",
            "slice", completedIndex,
            "nextSlice", plan.complete ? "COMPLETE" : plan.sliceIndex
        );
        if (plan.complete && plan.tunnelKind == MineTunnel.Kind.MAIN) {
            appendNextMainTunnel(world, mine, minePlan, plan);
        }
    }

    private void appendNextMainTunnel(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan plan,
        RuntimeFrontPlan completed
    ) {
        // V1 dwarven grid is bounded to its authored straight planning envelope.
        if (MineBuildingTypes.isDwarven(mine.buildingType())) return;
        UUID worldId = world.getWorldConfig().getUuid();
        MineNetwork current = tunnelRegistry.networkForMine(worldId, mine.id());
        if (current == null) return;
        RuntimeFrontPlan root = plan.fronts.values().stream()
            .filter(candidate -> candidate.tunnelId.equals(plan.mainTunnelId))
            .findFirst().orElse(null);
        if (root == null) return;
        MineGenerationProgress rootProgress = current.progressFor(root.tunnelId);
        if (rootProgress == null) return;
        BlockPosition entrance = root.slices.getFirst().floorCenter();
        BlockPosition end = completed.slices.getLast().floorCenter();
        if (entrance.y() <= MineGenerationPolicy.MIN_FLOOR_Y) {
            decisionSink.record(mine.id(), completed.tunnelId,
                MineDecisionCategory.PLANNING, "MAIN_EXTENSION_BLOCKED",
                "reason", "ENTRANCE_ALREADY_AT_MINIMUM_Y");
            world.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "Mine: Kein neuer Hauptstollen möglich – Eingang liegt auf Endtiefe Y=10."
            ));
            return;
        }
        boolean depthReached = end.y() <= MineGenerationPolicy.MIN_FLOOR_Y;
        BlockPosition nextOrigin = depthReached ? entrance : end;
        int sequence = (int) current.tunnels().stream()
            .filter(tunnel -> tunnel.kind() == MineTunnel.Kind.MAIN).count();
        if (sequence > 64) return;
        UUID newId = MineGenerationPolicy.tunnelId(mine.id(), sequence);
        if (current.tunnel(newId) != null) return;
        long seed = planningSeed(mine.id()) ^ Long.rotateLeft(
            0x9E3779B97F4A7C15L * sequence, 11
        );
        MineHeading firstHeading = depthReached
            ? MineGenerationPolicy.heading(rootProgress.heading(), sequence)
            : headingOfLastTwoSlices(completed.slices, rootProgress.heading());

        MineTunnelPath chosen = null;
        MineHeading heading = null;
        int attempts = depthReached ? MineHeading.values().length : 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            MineHeading candidateHeading = MineHeading.values()[
                (firstHeading.ordinal() + attempt) % MineHeading.values().length
            ];
            MineTunnelPath proposed = MineGenerationPolicy.capAtMinimumY(MinePathPlanner.plan(
                MineTunnel.Kind.MAIN, nextOrigin, entrance,
                candidateHeading, MAIN_PLAN_LENGTH_BLOCKS, seed
            ));
            if (!pathAvoidsExistingMine(proposed, mine, plan.fronts.values())) continue;
            chosen = proposed;
            heading = candidateHeading;
            break;
        }
        if (chosen == null) {
            decisionSink.record(mine.id(), completed.tunnelId,
                MineDecisionCategory.PLANNING, "MAIN_EXTENSION_BLOCKED",
                "reason", "NO_CLEAR_TUNNEL_DIRECTION",
                "atY", end.y());
            world.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "Mine: Kein neuer Hauptstollen möglich – keine freie Richtung gefunden."
            ));
            return;
        }

        MineTunnel additional = new MineTunnel(
            newId, MineTunnel.Kind.MAIN, null, 0, nextOrigin
        );
        MineNetwork updated = current.withTunnel(additional).withWorkFront(
            new MineWorkFront(frontId(mine.id(), newId), newId,
                new BlockPosition(
                    (int) Math.round(chosen.points().getFirst().x()),
                    (int) Math.round(chosen.points().getFirst().y()),
                    (int) Math.round(chosen.points().getFirst().z())),
                MineWorkFront.State.OPEN)
        ).withGenerationProgress(
            newId, new MineGenerationProgress(
                Math.min(MAIN_PLANNING_BATCH_SLICES, chosen.points().size()),
                System.currentTimeMillis() + MineGenerationPolicy.REFRESH_INTERVAL_MILLIS,
                heading, seed
            )
        );
        tunnelRegistry.putNetwork(world, updated);
        runtimePlans.remove(new WorldMineKey(worldId, mine.id()));
        decisionSink.record(
            mine.id(), newId, MineDecisionCategory.PLANNING, "MAIN_GENERATION_CREATED",
            "generation", sequence,
            "origin", nextOrigin,
            "heading", heading,
            "completedY", end.y()
        );
        if (depthReached) {
            world.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "Mine: Endtiefe Y=10 erreicht. Neuer Stollen wird am Eingang geplant."
            ));
        }
    }

    private static MineHeading headingOfLastTwoSlices(
        List<MineTunnelGeometry.Slice> slices,
        MineHeading fallback
    ) {
        BlockPosition last = slices.getLast().floorCenter();
        for (int i = slices.size() - 2; i >= 0; i--) {
            BlockPosition before = slices.get(i).floorCenter();
            int dx = last.x() - before.x();
            int dz = last.z() - before.z();
            if (dx == 0 && dz == 0) continue;
            double angle = Math.toDegrees(Math.atan2(dz, dx));
            MineHeading result = fallback;
            double smallest = Double.MAX_VALUE;
            for (MineHeading candidate : MineHeading.values()) {
                // Compare unit vectors to avoid angle wrapping errors.
                double error = Math.pow(candidate.unitX() - Math.cos(Math.toRadians(angle)), 2)
                    + Math.pow(candidate.unitZ() - Math.sin(Math.toRadians(angle)), 2);
                if (error < smallest) {
                    smallest = error;
                    result = candidate;
                }
            }
            return result;
        }
        return fallback;
    }

    private static boolean pathAvoidsExistingMine(
        MineTunnelPath candidate,
        BuildingPlacementRegistry.BuildingInstance mine,
        java.util.Collection<RuntimeFrontPlan> existing
    ) {
        java.util.Set<BlockPosition> centerlines = new HashSet<>();
        for (RuntimeFrontPlan front : existing) {
            for (MineTunnelGeometry.Slice slice : front.slices) {
                centerlines.add(slice.floorCenter());
            }
        }
        int index = 0;
        for (MinePathPoint point : candidate.points()) {
            BlockPosition block = new BlockPosition(
                (int) Math.round(point.x()), (int) Math.round(point.y()),
                (int) Math.round(point.z())
            );
            if (index < 16 && mine.bounds().containsBlock(block)) return false;
            if (index++ < 20) continue; // intentional attachment at the entrance or previous end
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    if (dx * dx + dz * dz > 25) continue;
                    for (int dy = -3; dy <= 3; dy++) {
                        if (centerlines.contains(new BlockPosition(
                            block.x() + dx, block.y() + dy, block.z() + dz
                        ))) return false;
                    }
                }
            }
        }
        return true;
    }

    private MineNetwork integrateNaturalCaveIfPresent(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        RuntimeFrontPlan front,
        int sliceIndex,
        MineNetwork network
    ) {
        MineTunnelGeometry.Slice slice = front.slices.get(sliceIndex);
        List<MineTunnelGeometry> geometries = minePlan.fronts.values().stream()
            .map(MinerWorkSystem::geometryFor)
            .toList();
        MineCaveObservation observation = MineCaveScanner.scan(world, slice, geometries);

        if (observation.status() == MineCaveObservation.Status.INCOMPLETE) {
            decisionSink.record(
                mine.id(), front.frontId, MineDecisionCategory.ENVIRONMENT, "CAVE_SCAN_INCOMPLETE",
                "slice", sliceIndex
            );
            return network;
        }
        if (observation.status() != MineCaveObservation.Status.LARGE) return network;

        for (MineRoom room : network.rooms()) {
            if (room.type() == MineRoom.Type.LARGE_NATURAL_CHAMBER
                && MineCavePolicy.sameNaturalChamber(room.position(), observation.center())) {
                return network;
            }
        }

        MineHeading heading = cardinalHeadingAt(front.slices, sliceIndex);
        UUID roomId = naturalChamberId(
            mine.id(), front.tunnelId, observation.center()
        );
        MineRoom chamber = new MineRoom(
            roomId,
            front.tunnelId,
            MineRoom.Type.LARGE_NATURAL_CHAMBER,
            observation.center(),
            heading,
            sliceIndex,
            MineRoom.State.NATURAL_INTEGRATED,
            0,
            Set.of()
        );
        decisionSink.record(
            mine.id(), roomId, MineDecisionCategory.ENVIRONMENT, "NATURAL_CHAMBER_INTEGRATED",
            "tunnel", front.tunnelId,
            "slice", sliceIndex,
            "emptyBlocks", observation.emptyBlocks(),
            "usableFloorBlocks", observation.usableFloorBlocks(),
            "spanX", observation.spanX(),
            "spanY", observation.spanY(),
            "spanZ", observation.spanZ(),
            "fluid", observation.hasFluid(),
            "lava", observation.hasLava()
        );
        return network.withRoom(chamber);
    }

    private static MineHeading cardinalHeadingAt(
        List<MineTunnelGeometry.Slice> slices,
        int index
    ) {
        BlockPosition before = slices.get(Math.max(0, index - 1)).floorCenter();
        BlockPosition after = slices.get(Math.min(slices.size() - 1, index + 1)).floorCenter();
        int dx = after.x() - before.x();
        int dz = after.z() - before.z();
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? MineHeading.EAST : MineHeading.WEST;
        return dz >= 0 ? MineHeading.SOUTH : MineHeading.NORTH;
    }

    private static UUID naturalChamberId(
        UUID mineId,
        UUID tunnelId,
        BlockPosition center
    ) {
        int qx = Math.floorDiv(center.x(), 8);
        int qy = Math.floorDiv(center.y(), 8);
        int qz = Math.floorDiv(center.z(), 8);
        return UUID.nameUUIDFromBytes((
            "civ-natural-chamber:" + mineId + ":" + tunnelId + ":" + qx + ":" + qy + ":" + qz
        ).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Explicit development-only recovery for old premature BUILD_STEP abandonment.
     * Never reopen arbitrary ABANDONED or BLOCKED fronts: only the main front stopped
     * at the lower slice of an unfinished authored step can be retried.
     */
    public StairRetryResult retryAbandonedMainStair(World world, UUID mineId) {
        if (world == null || mineId == null) return StairRetryResult.MINE_NOT_READY;
        UUID worldId = world.getWorldConfig().getUuid();
        WorldMineKey key = new WorldMineKey(worldId, mineId);
        RuntimeMinePlan plan = runtimePlans.get(key);
        MineNetwork network = tunnelRegistry.networkForMine(worldId, mineId);
        if (plan == null || network == null) return StairRetryResult.MINE_NOT_READY;

        for (RuntimeFrontPlan frontPlan : plan.fronts.values()) {
            if (frontPlan.tunnelKind != MineTunnel.Kind.MAIN) continue;
            MineWorkFront front = workFrontById(network, frontPlan.frontId);
            if (front == null || front.state() != MineWorkFront.State.ABANDONED) continue;

            boolean unresolvedStepAtLowerSlice = plan.infrastructureTasks.values().stream()
                .anyMatch(infrastructure -> !infrastructure.completed
                    && infrastructure.task.type() == MineInfrastructureTask.Type.BUILD_STEP
                    && infrastructure.task.tunnelId().equals(frontPlan.tunnelId)
                    && infrastructure.task.endSliceIndex() == frontPlan.sliceIndex);
            if (!MineObstaclePolicy.mayRetryAbandonedStair(front.state(), unresolvedStepAtLowerSlice)) {
                return StairRetryResult.NOT_ELIGIBLE;
            }

            MineWorkFront reopened = new MineWorkFront(
                front.id(), front.tunnelId(), front.position(), MineWorkFront.State.OPEN
            );
            tunnelRegistry.putNetwork(world, network.withWorkFront(reopened));
            frontCoordinator.releaseFront(frontPlan.frontId);
            // Recreate the regenerated runtime plan from the amended persisted front next tick.
            runtimePlans.remove(key);
            decisionSink.record(
                mineId, front.id(), MineDecisionCategory.ENVIRONMENT, "FRONT_REOPENED_DEV",
                "reason", "OLD_PREMATURE_BUILD_STEP",
                "slice", frontPlan.sliceIndex
            );
            return StairRetryResult.REOPENED;
        }
        return StairRetryResult.NOT_ELIGIBLE;
    }

    public enum StairRetryResult {
        REOPENED,
        MINE_NOT_READY,
        NOT_ELIGIBLE
    }

    private void failFront(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeFrontPlan plan,
        MineWorkFront front,
        MineObstaclePolicy.FailureKind failure,
        String reason
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        if (network == null || front == null) return;

        MineWorkFront.State state = MineObstaclePolicy.frontStateFor(failure);
        MineWorkFront failed = new MineWorkFront(
            front.id(), front.tunnelId(), front.position(), state
        );
        tunnelRegistry.putNetwork(
            world,
            network.withoutNormalTaskPriorityBonus(front.id()).withWorkFront(failed)
        );
        frontCoordinator.releaseFront(plan.frontId);
        plan.unavailable = true;
        decisionSink.record(
            mine.id(),
            front.id(),
            failure == MineObstaclePolicy.FailureKind.NAVIGATION_UNREACHABLE
                ? MineDecisionCategory.NAVIGATION
                : MineDecisionCategory.ENVIRONMENT,
            state == MineWorkFront.State.BLOCKED ? "FRONT_BLOCKED" : "FRONT_ABANDONED",
            "reason", reason,
            "failureKind", failure,
            "slice", plan.sliceIndex
        );
    }

    /**
     * Only a previously excavated authored elevation transition can become an
     * automatic repair. The normal mine steps feature stays disabled; this is
     * a bounded response to a native navigation failure, not eager stair work.
     */
    private boolean scheduleNearbyRecoveryStep(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        RuntimeFrontPlan front
    ) {
        int destinationSlice = front.sliceIndex - 1;
        if (destinationSlice < 1) return false;
        for (MineTunnelGeometry.StepTransition transition : front.geometry.stepTransitions()) {
            if (transition.toSliceIndex() != destinationSlice) continue;
            if (!sliceComplete(world, front.slices.get(transition.fromSliceIndex()))
                || !sliceComplete(world, front.slices.get(transition.toSliceIndex()))) {
                continue;
            }
            MineInfrastructureTask task = MineInfrastructurePlanner.recoveryStepTask(
                front.tunnelId, transition
            );
            if (minePlan.infrastructureTasks.containsKey(task.id())) continue;
            minePlan.infrastructureTasks.put(
                task.id(),
                new RuntimeInfrastructureTask(
                    task, front.tunnelKind, geometryFor(front), false
                )
            );
            decisionSink.record(
                mine.id(), task.id(), MineDecisionCategory.NAVIGATION,
                "NAVIGATION_STEP_REPAIR_CREATED",
                "tunnel", front.tunnelId,
                "fromSlice", transition.fromSliceIndex(),
                "toSlice", transition.toSliceIndex()
            );
            return true;
        }
        return false;
    }

    private boolean frontExecutable(World world, RuntimeMinePlan minePlan, RuntimeFrontPlan plan) {
        if (plan.complete || plan.unavailable || hasPendingMandatoryInfrastructure(minePlan, plan)) return false;
        if (plan.tunnelKind == MineTunnel.Kind.MAIN && plan.sliceIndex >= plan.unlockedSlices) return false;
        if (plan.tunnelKind == MineTunnel.Kind.MAIN || plan.sliceIndex > 0) return true;
        BlockType start = loadedBlockType(world, plan.slices.getFirst().floorCenter());
        return start != null && isEmpty(start);
    }

    private MineWorkFront currentFront(UUID worldId, UUID mineId, UUID frontId) {
        MineNetwork network = tunnelRegistry.networkForMine(worldId, mineId);
        return network == null ? null : workFrontById(network, frontId);
    }

    private MineRoom currentRoom(UUID worldId, UUID mineId, UUID roomId) {
        MineNetwork network = tunnelRegistry.networkForMine(worldId, mineId);
        return network == null ? null : roomById(network, roomId);
    }

    private static MineRoom roomById(MineNetwork network, UUID roomId) {
        return network.rooms().stream()
            .filter(room -> room.id().equals(roomId))
            .findFirst()
            .orElse(null);
    }

    private static boolean available(MineWorkFront front) {
        return front != null
            && (front.state() == MineWorkFront.State.OPEN || front.state() == MineWorkFront.State.ACTIVE);
    }

    private static MineWorkFront workFrontById(MineNetwork network, UUID frontId) {
        return network.workFronts().stream()
            .filter(front -> front.id().equals(frontId))
            .findFirst()
            .orElse(null);
    }

    private static int sliceIndexForPosition(List<MineTunnelGeometry.Slice> slices, BlockPosition position) {
        for (int i = 0; i < slices.size(); i++) {
            if (slices.get(i).floorCenter().equals(position)) return i;
        }
        return -1;
    }

    private static List<List<BlockPosition>> orderedBlocks(List<MineTunnelGeometry.Slice> slices) {
        List<List<BlockPosition>> result = new ArrayList<>(slices.size());
        for (MineTunnelGeometry.Slice slice : slices) result.add(List.copyOf(slice.excavationBlocks()));
        return List.copyOf(result);
    }

    private boolean containsBlockedSolid(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineTunnelGeometry.Slice slice
    ) {
        for (BlockPosition block : slice.excavationBlocks()) {
            BlockType type = loadedBlockType(world, block);
            if (type == null || isEmpty(type)) continue;
            if (!safeBlock(world, mine, block)) return true;
        }
        return false;
    }

    private boolean isAvailableWorkBlock(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        BlockPosition block
    ) {
        BlockType type = loadedBlockType(world, block);
        return type != null && !isEmpty(type) && safeBlock(world, mine, block);
    }

    private boolean safeBlock(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        BlockPosition block
    ) {
        for (BuildingPlacementRegistry.BuildingInstance building :
            buildingRegistry.buildings(world.getWorldConfig().getUuid())) {
            if (!building.bounds().containsBlock(block)) continue;
            // Mine corridors may intersect previously constructed mine interiors.
            // Other buildings, and the current mine's own entrance prefab, remain protected.
            if (!MineBuildingTypes.isMine(building.buildingType())
                || building.id().equals(mine.id())) return false;
        }
        // Only callers working from authored excavation blocks reach this method.
        // A tunnel excavates whatever occupies that voxel, including future rails,
        // supports, lanterns and decorations irrespective of block gathering metadata.
        return loadedBlockType(world, block) != null;
    }

    private static boolean sliceComplete(World world, MineTunnelGeometry.Slice slice) {
        for (BlockPosition block : slice.excavationBlocks()) {
            BlockType type = loadedBlockType(world, block);
            if (type == null || !isEmpty(type)) return false;
        }
        return true;
    }

    private static BlockType loadedBlockType(World world, BlockPosition block) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(block.x(), block.z()));
        return chunk == null ? null : chunk.getBlockType(block.x(), block.y(), block.z());
    }

    private static boolean isEmpty(BlockType blockType) {
        return blockType == BlockType.EMPTY
            || (blockType != null && blockType.getMaterial() == BlockMaterial.Empty);
    }

    private static Vector3d workTarget(
        RuntimeFrontPlan plan,
        UUID mainTunnelId,
        PrefabPlacementService.PlacedMarker connector
    ) {
        if (plan.sliceIndex == 0 && plan.tunnelId.equals(mainTunnelId)) {
            return center(connector.bounds(), connector.bounds().minY());
        }
        BlockPosition target = plan.sliceIndex == 0
            ? plan.slices.getFirst().floorCenter()
            : plan.slices.get(plan.sliceIndex - 1).floorCenter();
        return new Vector3d(target.x() + 0.5, target.y(), target.z() + 0.5);
    }

    private BuildingPlacementRegistry.BuildingInstance assignedMine(Ref<EntityStore> ref, UUID worldId) {
        CivInhabitantData data = unitRegistry.getInhabitantData(ref);
        if (data == null || data.workplaceId() == null || data.workplaceId().isBlank()) return null;
        try {
            BuildingPlacementRegistry.BuildingInstance building =
                buildingRegistry.find(worldId, UUID.fromString(data.workplaceId()));
            return building != null && MineBuildingTypes.isMine(building.buildingType()) ? building : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** No idle simulation: a finished accommodation is simply a preferred waiting destination. */
    private static boolean restoredInsideMine(World world, RuntimeMinePlan minePlan, Vector3d position) {
        BlockPosition feet = blockPosition(position);
        return MineRestartPositionPolicy.alreadyInsideMine(
            feet,
            loadedBlockType(world, feet) == BlockType.EMPTY,
            minePlan.fronts.values().stream().map(front -> front.geometry).toList(),
            minePlan.rooms.values().stream().map(room -> room.geometry).toList()
        );
    }

    private void navigateTo(Ref<EntityStore> ref, Vector3d target, WorkerRuntime runtime) {
        if (runtime.navigationTarget == null || runtime.navigationTarget.distanceSquared(target) > 0.0001) {
            Vector3d previous = runtime.navigationTarget == null
                ? null : new Vector3d(runtime.navigationTarget);
            runtime.navigationTarget = new Vector3d(target);
            unitRegistry.setMoveTarget(ref, target);

            CivUnitRegistry.UnitKey workerKey = unitRegistry.keyOf(ref);
            WorkerDebugState nextState = runtime.idleDestination != null
                ? WorkerDebugState.MOVING_TO_IDLE_DESTINATION
                : runtime.frontId != null || runtime.roomId != null || runtime.infrastructureTaskId != null
                    ? WorkerDebugState.MOVING_TO_TASK
                    : WorkerDebugState.MOVING_TO_MINE;
            String reason = nextState == WorkerDebugState.MOVING_TO_IDLE_DESTINATION
                ? (runtime.idleRoomId == null ? "NO_REST_ROOM" : "REST_ROOM_SELECTED")
                : nextState == WorkerDebugState.MOVING_TO_TASK
                    ? "TASK_WORK_TARGET"
                    : !runtime.enteredMine ? "WORKPLACE_ACCESS" : "MINE_CONNECTOR";
            decisionSink.record(
                runtime.mineId, null, MineDecisionCategory.WORKER, "MOVE_TARGET_CHANGED",
                "npc", workerLabel(ref, workerKey),
                "from", formatTarget(previous),
                "to", formatTarget(target),
                "reason", reason
            );
            workerState(runtime.mineId, ref, workerKey, runtime, nextState, reason);
        }
    }

    private void workerState(
        UUID mineId,
        Ref<EntityStore> ref,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime,
        WorkerDebugState next,
        String reason
    ) {
        if (mineId == null || runtime == null || next == null || next == runtime.debugState) return;
        WorkerDebugState previous = runtime.debugState;
        runtime.debugState = next;
        decisionSink.record(
            mineId, currentTaskId(runtime), MineDecisionCategory.WORKER, "STATE_CHANGED",
            "npc", workerLabel(ref, workerKey),
            "from", previous == null ? "UNKNOWN" : previous,
            "to", next,
            "reason", reason,
            "task", currentTaskType(runtime),
            "taskId", currentTaskId(runtime)
        );
    }

    private void workerTaskStarted(
        UUID mineId,
        Ref<EntityStore> ref,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime,
        String taskType,
        UUID taskId,
        int workers,
        int capacity
    ) {
        if (mineId == null || taskId == null || runtime == null) return;
        String key = taskType + ":" + taskId;
        if (key.equals(runtime.startedTaskKey)) return;
        runtime.selectedTaskKey = key;
        runtime.startedTaskKey = key;
        decisionSink.record(
            mineId, taskId, MineDecisionCategory.WORKER, "TASK_STARTED",
            "npc", workerLabel(ref, workerKey),
            "taskType", taskType,
            "reservation", "ACTIVE",
            "workers", workers,
            "capacity", capacity
        );
    }

    private void workerTaskEnded(
        UUID mineId,
        Ref<EntityStore> ref,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime,
        String reason
    ) {
        if (mineId == null || runtime == null) return;
        String taskKey = runtime.startedTaskKey != null
            ? runtime.startedTaskKey
            : runtime.selectedTaskKey;
        if (taskKey == null) return;
        UUID taskId = currentTaskId(runtime);
        decisionSink.record(
            mineId, taskId, MineDecisionCategory.WORKER, "TASK_ENDED",
            "npc", workerLabel(ref, workerKey),
            "task", taskKey,
            "started", runtime.startedTaskKey != null,
            "reason", reason
        );
        runtime.startedTaskKey = null;
        runtime.selectedTaskKey = null;
    }

    private String workerLabel(Ref<EntityStore> ref, CivUnitRegistry.UnitKey key) {
        CivInhabitantData data = ref == null ? null : unitRegistry.getInhabitantData(ref);
        String name = data == null ? "" : data.fullName().trim();
        return name.isBlank()
            ? "entity-" + key.entityIndex()
            : name.replace(' ', '_') + "#" + key.entityIndex();
    }

    private static UUID currentTaskId(WorkerRuntime runtime) {
        if (runtime == null) return null;
        if (runtime.infrastructureTaskId != null) return runtime.infrastructureTaskId;
        if (runtime.roomId != null) return runtime.roomId;
        return runtime.frontId;
    }

    private static String currentTaskType(WorkerRuntime runtime) {
        if (runtime == null) return "-";
        if (runtime.infrastructureTaskId != null) return "INFRASTRUCTURE";
        if (runtime.roomId != null) return "ROOM";
        if (runtime.frontId != null) return "EXCAVATE_FRONT";
        return "-";
    }

    private static String formatTarget(Vector3d target) {
        if (target == null) return "-";
        return String.format(
            java.util.Locale.ROOT, "(%.2f,%.2f,%.2f)", target.x, target.y, target.z
        );
    }

    private static PrefabPlacementService.PlacedMarker marker(
        World world,
        BuildingPlacementRegistry.BuildingInstance building,
        String type
    ) {
        PrefabPlacementService.PlacedMarker marker = building.semanticVolumes().stream()
            .filter(candidate -> candidate.hasTag(TYPE_TAG, type))
            .filter(candidate -> candidate.hasTag(BUILDING_TAG, MINE_BUILDING))
            .findFirst()
            .orElse(null);
        if (marker == null || marker.bounds() != null) return marker;
        TriggerVolumeManager manager = world.getEntityStore().getStore().getResource(
            TriggerVolumesPlugin.get().getManagerResourceType()
        );
        var volume = manager == null ? null : manager.getVolume(marker.id());
        if (volume == null || volume.getShape() == null || volume.getPosition() == null) return marker;
        Vector3d min = new Vector3d();
        Vector3d max = new Vector3d();
        volume.getShape().getWorldAABB(volume.getPosition(), min, max);
        return new PrefabPlacementService.PlacedMarker(
            marker.id(), marker.position(), marker.tags(),
            new BuildingBounds(min.x, min.y, min.z, max.x, max.y, max.z)
        );
    }

    private static MineHeading outwardHeading(BuildingBounds building, BuildingBounds connector) {
        double bx = (building.minX() + building.maxX()) * 0.5;
        double bz = (building.minZ() + building.maxZ()) * 0.5;
        double cx = (connector.minX() + connector.maxX()) * 0.5;
        double cz = (connector.minZ() + connector.maxZ()) * 0.5;
        double dx = cx - bx;
        double dz = cz - bz;
        if (Math.abs(dx) > Math.abs(dz)) return dx >= 0 ? MineHeading.EAST : MineHeading.WEST;
        return dz >= 0 ? MineHeading.SOUTH : MineHeading.NORTH;
    }

    static BlockPosition initialCenterlineOrigin(BuildingBounds connector, MineHeading heading) {
        int centerX = (int) Math.floor((connector.minX() + connector.maxX()) * 0.5);
        int centerZ = (int) Math.floor((connector.minZ() + connector.maxZ()) * 0.5);
        int y = (int) Math.floor(connector.minY());
        int x = heading.unitX() > 0.01
            ? (int) Math.ceil(connector.maxX())
            : heading.unitX() < -0.01 ? (int) Math.floor(connector.minX()) - 1 : centerX;
        int z = heading.unitZ() > 0.01
            ? (int) Math.ceil(connector.maxZ())
            : heading.unitZ() < -0.01 ? (int) Math.floor(connector.minZ()) - 1 : centerZ;
        return new BlockPosition(x, y, z);
    }

    private static long planningSeed(UUID mineId) {
        return mineId.getMostSignificantBits() ^ Long.rotateLeft(mineId.getLeastSignificantBits(), 23);
    }

    private static BlockPosition blockPosition(Vector3d position) {
        return new BlockPosition(
            (int) Math.floor(position.x),
            (int) Math.floor(position.y),
            (int) Math.floor(position.z)
        );
    }

    private static Vector3d center(BuildingBounds bounds, double y) {
        return new Vector3d(
            (bounds.minX() + bounds.maxX()) * 0.5,
            y,
            (bounds.minZ() + bounds.maxZ()) * 0.5
        );
    }

    private static boolean arrived(Vector3d position, Vector3d target) {
        return CivArrivalPolicy.reached(
            position, target, ARRIVAL_HORIZONTAL_DISTANCE, ARRIVAL_VERTICAL_TOLERANCE
        );
    }

    private void releaseWorker(
        CivUnitRegistry.UnitKey key,
        Ref<EntityStore> ref,
        Store<EntityStore> store
    ) {
        WorkerRuntime runtime = workers.remove(key);
        if (runtime != null) {
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            releaseInfrastructureReservation(key, runtime);
        }
        controller.forget(key);
        navigationFailures.forget(key);
    }

    private static void stopMiningAnimation(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        WorkerRuntime runtime
    ) {
        if (runtime == null || !runtime.animationStarted) return;
        if (ref != null && ref.isValid()) AnimationUtils.stopAnimation(ref, AnimationSlot.Action, store);
        runtime.animationStarted = false;
    }

    private static void stopBuildingAnimation(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        WorkerRuntime runtime
    ) {
        if (runtime == null || !runtime.buildingAnimationStarted) return;
        if (ref != null && ref.isValid()) AnimationUtils.stopAnimation(ref, AnimationSlot.Action, store);
        runtime.buildingAnimationStarted = false;
    }

    /**
     * Read-only runtime debug anchors for the current mine. This exposes already-existing worker
     * targets and semantic work anchors only; it does not create navigation or gameplay state.
     */
    public List<MineDebugAnchor> debugAnchors(UUID worldId, UUID mineId) {
        if (worldId == null || mineId == null) return List.of();
        List<MineDebugAnchor> result = new ArrayList<>();
        RuntimeMinePlan plan = runtimePlans.get(new WorldMineKey(worldId, mineId));
        MineNetwork network = tunnelRegistry.networkForMine(worldId, mineId);

        if (plan != null) {
            for (RuntimeFrontPlan front : plan.fronts.values()) {
                if (front.slices.isEmpty() || front.complete || front.unavailable) continue;
                int index = Math.max(0, Math.min(front.sliceIndex, front.slices.size() - 1));
                BlockPosition position = front.slices.get(index).floorCenter();
                MineWorkFront persisted = network == null ? null : workFrontById(network, front.frontId);
                result.add(MineDebugAnchor.atBlock(
                    "front:" + front.frontId,
                    MineDebugAnchor.Kind.FRONT,
                    position,
                    front.tunnelKind + " front · slice=" + index
                        + (persisted == null ? "" : " · " + persisted.state())
                ));
            }

            for (RuntimeInfrastructureTask infrastructure : plan.infrastructureTasks.values()) {
                if (infrastructure.completed) continue;
                RuntimeFrontPlan owner = frontForTunnel(plan, infrastructure.task.tunnelId());
                if (owner != null && owner.tunnelKind == MineTunnel.Kind.MAIN
                    && infrastructure.task.startSliceIndex() >= owner.unlockedSlices) continue;
                result.add(MineDebugAnchor.atBlock(
                    "infrastructure:" + infrastructure.task.id(),
                    MineDebugAnchor.Kind.INFRASTRUCTURE,
                    infrastructure.task.anchor(),
                    "Infrastructure · " + infrastructure.task.type()
                ));
            }
        }

        if (network != null) {
            for (MineRoom room : network.rooms()) {
                if (room.terminal()) continue;
                result.add(MineDebugAnchor.atBlock(
                    "room:" + room.id(),
                    MineDebugAnchor.Kind.ROOM,
                    room.position(),
                    "Room · " + room.type() + " · " + room.state()
                ));
            }
        }

        for (Map.Entry<CivUnitRegistry.UnitKey, WorkerRuntime> entry : workers.entrySet()) {
            WorkerRuntime runtime = entry.getValue();
            if (!mineId.equals(runtime.mineId) || runtime.navigationTarget == null) continue;
            Vector3d target = runtime.navigationTarget;
            result.add(new MineDebugAnchor(
                "navigation:" + entry.getKey().entityIndex(),
                MineDebugAnchor.Kind.NAVIGATION,
                target.x,
                target.y,
                target.z,
                "Miner " + entry.getKey().entityIndex() + " navigation target"
            ));
        }
        return List.copyOf(result);
    }

    public record MineDebugAnchor(
        String id,
        Kind kind,
        double x,
        double y,
        double z,
        String label
    ) {
        public MineDebugAnchor {
            if (id == null || id.isBlank() || kind == null || label == null || label.isBlank()) {
                throw new IllegalArgumentException("Mine debug anchor fields must not be blank.");
            }
        }

        private static MineDebugAnchor atBlock(
            String id,
            Kind kind,
            BlockPosition position,
            String label
        ) {
            return new MineDebugAnchor(
                id, kind, position.x() + 0.5, position.y() + 0.5, position.z() + 0.5, label
            );
        }

        public enum Kind {
            FRONT,
            NAVIGATION,
            ROOM,
            INFRASTRUCTURE
        }
    }

    private record WorldMineKey(UUID worldId, UUID mineId) {
    }

    private record BridgeAssessment(
        MineInfrastructureTask task,
        String abandonReason,
        boolean hazardousFluid
    ) {
        private static BridgeAssessment none() {
            return new BridgeAssessment(null, null, false);
        }

        private static BridgeAssessment bridge(MineInfrastructureTask task) {
            return new BridgeAssessment(task, null, false);
        }

        private static BridgeAssessment abandon(String reason, boolean hazardousFluid) {
            return new BridgeAssessment(null, reason, hazardousFluid);
        }
    }

    private static final class RuntimeMinePlan {
        private final UUID mainTunnelId;
        private final Map<UUID, RuntimeFrontPlan> fronts;
        private final Map<UUID, RuntimeRoomPlan> rooms;
        private final Map<UUID, RuntimeInfrastructureTask> infrastructureTasks;

        private RuntimeMinePlan(
            UUID mainTunnelId,
            Map<UUID, RuntimeFrontPlan> fronts,
            Map<UUID, RuntimeRoomPlan> rooms,
            Map<UUID, RuntimeInfrastructureTask> infrastructureTasks
        ) {
            this.mainTunnelId = mainTunnelId;
            this.fronts = new LinkedHashMap<>(fronts);
            this.rooms = new LinkedHashMap<>(rooms);
            this.infrastructureTasks = new LinkedHashMap<>(infrastructureTasks);
        }
    }

    private static final class RuntimeRoomPlan {
        private final UUID roomId;
        private final MineRoomGeometry geometry;
        private boolean unavailable;

        private RuntimeRoomPlan(UUID roomId, MineRoomGeometry geometry, boolean unavailable) {
            this.roomId = roomId;
            this.geometry = geometry;
            this.unavailable = unavailable;
        }
    }

    private static final class RuntimeFrontPlan {
        private final UUID frontId;
        private final UUID tunnelId;
        private final MineTunnel.Kind tunnelKind;
        private final MineTunnelGeometry geometry;
        private final List<MineTunnelGeometry.Slice> slices;
        private final List<List<BlockPosition>> orderedBlocks;
        private int sliceIndex;
        private int unlockedSlices;
        private boolean complete;
        private boolean unavailable;

        private RuntimeFrontPlan(
            UUID frontId,
            UUID tunnelId,
            MineTunnel.Kind tunnelKind,
            MineTunnelGeometry geometry,
            List<MineTunnelGeometry.Slice> slices,
            List<List<BlockPosition>> orderedBlocks,
            int sliceIndex,
            int unlockedSlices,
            boolean complete,
            boolean unavailable
        ) {
            this.frontId = frontId;
            this.tunnelId = tunnelId;
            this.tunnelKind = tunnelKind;
            this.geometry = geometry;
            this.slices = List.copyOf(slices);
            this.orderedBlocks = List.copyOf(orderedBlocks);
            this.sliceIndex = sliceIndex;
            this.unlockedSlices = unlockedSlices;
            this.complete = complete;
            this.unavailable = unavailable;
        }
    }

    private static final class RuntimeInfrastructureTask {
        private final MineInfrastructureTask task;
        private final MineTunnel.Kind tunnelKind;
        private final MineTunnelGeometry geometry;
        private boolean completed;
        private int deferredAtSlice = -1;
        private UUID deferredByFrontId;
        private String lastPlacementFailureKey;
        private int repeatedPlacementFailures;

        private RuntimeInfrastructureTask(
            MineInfrastructureTask task,
            MineTunnel.Kind tunnelKind,
            MineTunnelGeometry geometry,
            boolean completed
        ) {
            this.task = task;
            this.tunnelKind = tunnelKind;
            this.geometry = geometry;
            this.completed = completed;
        }
    }

    private enum WorkerDebugState {
        IDLE,
        MOVING_TO_MINE,
        MOVING_TO_TASK,
        WORKING,
        BUILDING,
        MOVING_TO_IDLE_DESTINATION,
        MANUAL_CONTROL
    }

    private static final class WorkerRuntime {
        private UUID mineId;
        private int minePhase;
        private UUID frontId;
        private UUID roomId;
        private Integer roomBuildSection;
        private int sliceIndex = -1;
        private BlockPosition claimedBlock;
        private UUID infrastructureTaskId;
        private MineInfrastructurePlacementResolver.ResolvedTask resolvedInfrastructure;
        private int infrastructurePlacementIndex;
        private int infrastructureProbeIndex;
        private int roomProbeIndex;
        private boolean enteredMine;
        private boolean reachedConnector;
        private boolean restorePosition = true;
        private boolean animationStarted;
        private boolean buildingAnimationStarted;
        private Vector3d navigationTarget;
        private UUID idleRoomId;
        private Vector3d idleDestination;
        private boolean idleReachedConnector;
        private boolean idleEntranceFailed;
        private boolean waitingForCapacity;
        private final Set<UUID> failedIdleRooms = new HashSet<>();
        private WorkerDebugState debugState;
        private String selectedTaskKey;
        private String startedTaskKey;
        private String lastNoTaskFingerprint;
        private String lastSupportDecisionFingerprint;
        private String lastDecorationDecisionFingerprint;

        private void clearIdle() {
            waitingForCapacity = false;
            idleRoomId = null;
            idleDestination = null;
            idleReachedConnector = false;
            idleEntranceFailed = false;
            failedIdleRooms.clear();
        }

        private void interruptForManualMove() {
            restorePosition = false;
            clearIdle();
            enteredMine = false;
            reachedConnector = false;
            clearWorkAssignment();
            navigationArrived();
        }

        private void clearAssignment() {
            clearWorkAssignment();
            navigationArrived();
        }

        private void clearWorkAssignment() {
            clearFrontAssignment();
            clearRoomAssignment();
            clearInfrastructureAssignment();
            selectedTaskKey = null;
            startedTaskKey = null;
        }

        private void clearFrontAssignment() {
            frontId = null;
            sliceIndex = -1;
            claimedBlock = null;
        }

        private void clearRoomAssignment() {
            roomId = null;
            roomBuildSection = null;
            roomProbeIndex = 0;
            claimedBlock = null;
        }

        private void clearInfrastructureAssignment() {
            infrastructureTaskId = null;
            resolvedInfrastructure = null;
            infrastructurePlacementIndex = 0;
            infrastructureProbeIndex = 0;
        }

        private void navigationArrived() {
            navigationTarget = null;
        }

        private void reset(UUID nextMineId, int nextMinePhase) {
            restorePosition = true;
            clearIdle();
            mineId = nextMineId;
            minePhase = nextMinePhase;
            enteredMine = false;
            reachedConnector = false;
            animationStarted = false;
            debugState = null;
            selectedTaskKey = null;
            startedTaskKey = null;
            lastNoTaskFingerprint = null;
            lastSupportDecisionFingerprint = null;
            lastDecorationDecisionFingerprint = null;
            clearAssignment();
        }
    }
}
