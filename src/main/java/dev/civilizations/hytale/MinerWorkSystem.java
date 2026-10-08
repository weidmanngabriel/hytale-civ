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
import dev.civilizations.core.MineNetworkGrowthPlanner;
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
 * The Core scheduler chooses among executable main/branch fronts; this adapter owns world checks and
 * coordinates short-lived block claims between at most two miners per tunnel front.
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
    private static final int MAX_BRIDGE_SPAN = 16;
    private static final int MAX_FLUID_BRIDGE_SPAN = 10;
    private static final double ARRIVAL_DISTANCE = 1.1;
    private static final int MAIN_PLAN_LENGTH_BLOCKS = MinePathPlanner.FOOTPRINT_SIZE_BLOCKS;
    private static final int RUNTIME_PLANNING_TUNNEL_BUDGET = 64;

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
    private final Map<WorldMineKey, RuntimeMinePlan> runtimePlans = new ConcurrentHashMap<>();

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
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        CivUnitRegistry.UnitKey workerKey = unitRegistry.keyOf(ref);
        if (!ref.isValid() || unitRegistry.getProfession(ref) != Profession.MINER) {
            releaseWorker(workerKey, ref, store);
            return;
        }

        WorkerRuntime runtime = workers.computeIfAbsent(workerKey, ignored -> new WorkerRuntime());
        if (!activityRegistry.autonomousWorkAllowed(ref)) {
            workerTaskEnded(runtime.mineId, ref, workerKey, runtime, "MANUAL_MOVE");
            workerState(runtime.mineId, ref, workerKey, runtime, WorkerDebugState.MANUAL_CONTROL, "MANUAL_MOVE");
            navigationFailures.forget(workerKey);
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            frontCoordinator.releaseWorker(workerKey);
            roomCoordinator.releaseWorker(workerKey);
            releaseInfrastructureReservation(workerKey, runtime);
            runtime.interruptForManualMove();
            return;
        }

        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        World world = store.getExternalData().getWorld();
        UUID worldId = world.getWorldConfig().getUuid();
        BuildingPlacementRegistry.BuildingInstance mine = assignedMine(ref, worldId);
        if (mine == null) {
            navigationFailures.forget(workerKey);
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            frontCoordinator.releaseWorker(workerKey);
            roomCoordinator.releaseWorker(workerKey);
            releaseInfrastructureReservation(workerKey, runtime);
            runtime.clearAssignment();
            return;
        }
        if (!mine.id().equals(runtime.mineId) || mine.phase() != runtime.minePhase) {
            navigationFailures.forget(workerKey);
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            frontCoordinator.releaseWorker(workerKey);
            roomCoordinator.releaseWorker(workerKey);
            releaseInfrastructureReservation(workerKey, runtime);
            runtime.reset(mine.id(), mine.phase());
        }

        PrefabPlacementService.PlacedMarker entrance = marker(world, mine, WORKPLACE_ACCESS);
        PrefabPlacementService.PlacedMarker connector = marker(world, mine, TUNNEL_CONNECTOR);
        if (connector == null || connector.bounds() == null) {
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            frontCoordinator.releaseWorker(workerKey);
            roomCoordinator.releaseWorker(workerKey);
            releaseInfrastructureReservation(workerKey, runtime);
            return;
        }

        Vector3d position = transform.getPosition();
        // After native LOAD the entity can already be in an excavated tunnel or finished
        // room. Rehydrate the deterministic plan before forcing the normal surface entry.
        RuntimeMinePlan minePlan = ensureRuntimePlan(world, mine, connector);
        if (minePlan == null) return;
        if (!runtime.reachedConnector && restoredInsideMine(world, minePlan, position)) {
            runtime.enteredMine = true;
            runtime.reachedConnector = true;
        }
        if (!runtime.enteredMine && entrance != null && entrance.bounds() != null) {
            Vector3d target = center(entrance.bounds(), entrance.bounds().minY());
            if (!arrived(position, target)) {
                navigateTo(ref, target, runtime);
                stopMiningAnimation(ref, store, runtime);
                return;
            }
            runtime.enteredMine = true;
            runtime.navigationArrived();
        }

        if (!runtime.reachedConnector) {
            Vector3d target = center(connector.bounds(), connector.bounds().minY());
            if (!arrived(position, target)) {
                navigateTo(ref, target, runtime);
                stopMiningAnimation(ref, store, runtime);
                return;
            }
            runtime.reachedConnector = true;
            runtime.navigationArrived();
        }

        if (navigationFailures.consumeIfMatches(workerKey, runtime.navigationTarget)) {
            if (runtime.idleDestination != null) {
                if (runtime.idleRoomId != null) runtime.failedIdleRooms.add(runtime.idleRoomId);
                else runtime.idleEntranceFailed = true;
                runtime.idleRoomId = null;
                runtime.idleDestination = null;
                runtime.navigationArrived();
                unitRegistry.clearMoveTarget(ref);
                return;
            }
            handleTerminalNavigationFailure(
                world, mine, minePlan, ref, store, workerKey, runtime
            );
            return;
        }
        // Detect passability work before treating already-empty cave slices as completed
        // excavation. Otherwise a naturally open gap could be skipped before BUILD_BRIDGE exists.
        refreshBridgeTasks(world, mine, minePlan);
        advanceAlreadyExcavatedSlices(world, mine, minePlan);
        refreshBridgeTasks(world, mine, minePlan);

        RuntimeInfrastructureTask currentInfrastructure = runtime.infrastructureTaskId == null
            ? null : minePlan.infrastructureTasks.get(runtime.infrastructureTaskId);
        if (currentInfrastructure != null
            && !currentInfrastructure.completed
            && currentInfrastructure.task.mandatory()) {
            executeInfrastructure(
                world, mine, minePlan, currentInfrastructure, ref, store, position, workerKey, runtime
            );
            return;
        }

        RuntimeInfrastructureTask mandatoryInfrastructure =
            selectMandatoryInfrastructureTask(world, mine, minePlan, position, workerKey, runtime);
        if (mandatoryInfrastructure != null) {
            if (runtime.frontId != null) frontCoordinator.releaseWorker(workerKey);
            if (runtime.roomId != null) roomCoordinator.releaseWorker(workerKey);
            runtime.clearFrontAssignment();
            runtime.clearRoomAssignment();
            // selectMandatoryInfrastructureTask established the new reservation/assignment.
            runtime.infrastructureTaskId = mandatoryInfrastructure.task.id();
            executeInfrastructure(
                world, mine, minePlan, mandatoryInfrastructure, ref, store, position, workerKey, runtime
            );
            return;
        }

        if (currentInfrastructure != null) {
            if (currentInfrastructure.completed) {
                releaseInfrastructureReservation(workerKey, runtime);
                runtime.clearInfrastructureAssignment();
            } else {
                executeInfrastructure(
                    world, mine, minePlan, currentInfrastructure, ref, store, position, workerKey, runtime
                );
                return;
            }
        } else if (runtime.infrastructureTaskId != null) {
            releaseInfrastructureReservation(workerKey, runtime);
            runtime.clearInfrastructureAssignment();
        }

        if (runtime.roomId != null && roomCoordinator.workerCount(runtime.roomId) == 0) {
            runtime.clearRoomAssignment();
        }
        RuntimeRoomPlan currentRoomPlan =
            runtime.roomId == null ? null : minePlan.rooms.get(runtime.roomId);
        MineRoom currentRoom = currentRoomPlan == null
            ? null : currentRoom(worldId, mine.id(), currentRoomPlan.roomId);
        if (currentRoomPlan != null && currentRoom != null
            && !currentRoom.terminal() && !currentRoomPlan.unavailable) {
            executeRoom(
                world, mine, minePlan, currentRoomPlan, currentRoom, ref, store, commandBuffer,
                position, workerKey, runtime
            );
            return;
        }
        if (runtime.roomId != null) {
            roomCoordinator.releaseWorker(workerKey);
            runtime.clearRoomAssignment();
        }

        if (runtime.frontId != null && frontCoordinator.workerCount(runtime.frontId) == 0) {
            runtime.clearFrontAssignment();
        }

        RuntimeFrontPlan plan = runtime.frontId == null ? null : minePlan.fronts.get(runtime.frontId);
        MineWorkFront front = plan == null ? null : currentFront(worldId, mine.id(), plan.frontId);

        if (plan == null && runtime.frontId == null) {
            MineNormalTaskSelector.Candidate candidate =
                selectNormalCandidate(world, mine, minePlan, position, workerKey, runtime);
            if (candidate != null) {
                runtime.clearIdle();
                switch (candidate.kind()) {
                    case ROOM -> {
                        RuntimeRoomPlan selectedRoomPlan =
                            selectRoom(world, mine, minePlan, candidate.id(), workerKey, runtime);
                        MineRoom selectedRoom = selectedRoomPlan == null
                            ? null : currentRoom(worldId, mine.id(), selectedRoomPlan.roomId);
                        if (selectedRoomPlan != null && selectedRoom != null) {
                            executeRoom(
                                world, mine, minePlan, selectedRoomPlan, selectedRoom, ref, store,
                                commandBuffer, position, workerKey, runtime
                            );
                            return;
                        }
                    }
                    case INFRASTRUCTURE -> {
                        RuntimeInfrastructureTask infrastructure =
                            selectInfrastructureTaskById(
                                mine, minePlan, candidate.id(), workerKey, runtime
                            );
                        if (infrastructure != null) {
                            executeInfrastructure(
                                world, mine, minePlan, infrastructure, ref, store,
                                position, workerKey, runtime
                            );
                            return;
                        }
                    }
                    case TUNNEL_FRONT -> {
                        plan = selectFrontById(
                            world, mine, minePlan, candidate.id(), workerKey, runtime
                        );
                        front = plan == null
                            ? null : currentFront(worldId, mine.id(), plan.frontId);
                    }
                }
            }
        }

        if (plan == null || plan.complete || !available(front)) {
            if (runtime.frontId != null) frontCoordinator.releaseWorker(workerKey);
            runtime.clearFrontAssignment();
            stopMiningAnimation(ref, store, runtime);
            workerTaskEnded(mine.id(), ref, workerKey, runtime,
                plan != null && plan.complete ? "TASK_COMPLETED" : "TASK_NO_LONGER_EXECUTABLE");
            workerState(
                mine.id(), ref, workerKey, runtime, WorkerDebugState.IDLE, "NO_AVAILABLE_TASK"
            );
            handleIdle(world, mine, connector, position, ref, runtime);
            return;
        }
        runtime.clearIdle();

        if (plan == null || front == null) {
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            return;
        }

        if (!frontCoordinator.tryJoin(plan.frontId, workerKey, MineFrontCoordinator.capacityFor(plan.tunnelKind))) {
            workerTaskEnded(mine.id(), ref, workerKey, runtime, "CAPACITY_UNAVAILABLE");
            runtime.clearWorkAssignment();
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            return;
        }

        if (runtime.sliceIndex != plan.sliceIndex) {
            stopMiningAnimation(ref, store, runtime);
            runtime.sliceIndex = plan.sliceIndex;
            runtime.workElapsed = 0.0;
            runtime.claimedBlock = null;
            runtime.navigationArrived();
        }

        MineTunnelGeometry.Slice slice = plan.slices.get(plan.sliceIndex);
        if (containsBlockedSolid(world, mine, slice)) {
            failFront(
                world,
                mine,
                plan,
                front,
                MineObstaclePolicy.FailureKind.UNSAFE_GEOMETRY,
                "UNSAFE_OR_UNBREAKABLE_SLICE"
            );
            workerTaskEnded(mine.id(), ref, workerKey, runtime, "UNSAFE_GEOMETRY");
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            frontCoordinator.releaseWorker(workerKey);
            runtime.clearWorkAssignment();
            return;
        }

        Vector3d workTarget = workTarget(plan, minePlan.mainTunnelId, connector);
        if (!arrived(position, workTarget)) {
            navigateTo(ref, workTarget, runtime);
            stopMiningAnimation(ref, store, runtime);
            return;
        }
        runtime.navigationArrived();
        unitRegistry.clearMoveTarget(ref);

        stopBuildingAnimation(ref, store, runtime);
        workerTaskStarted(
            mine.id(), ref, workerKey, runtime, "EXCAVATE_FRONT",
            plan.frontId, frontCoordinator.workerCount(plan.frontId),
            MineFrontCoordinator.capacityFor(plan.tunnelKind)
        );
        workerState(
            mine.id(), ref, workerKey, runtime, WorkerDebugState.WORKING, "WORK_TARGET_REACHED"
        );
        if (!runtime.animationStarted) {
            AnimationUtils.playAnimation(
                ref, AnimationSlot.Action, MINING_ITEM_ANIMATIONS, MINING_ANIMATION, store
            );
            runtime.animationStarted = true;
        }

        runtime.workElapsed += dt;
        while (runtime.workElapsed >= MineTuning.secondsPerBlock()) {
            runtime.workElapsed -= MineTuning.secondsPerBlock();
            if (!workOneBlock(world, ref, store, mine, plan, workerKey, runtime)) break;
            if (sliceComplete(world, slice)) {
                completeCurrentSlice(world, mine, minePlan, plan);
                workerTaskEnded(mine.id(), ref, workerKey, runtime, "WORK_UNIT_COMPLETED");
                stopMiningAnimation(ref, store, runtime);
                runtime.clearWorkAssignment();
                return;
            }
        }
    }

    public void forgetRuntime(Ref<EntityStore> ref) {
        if (ref == null) return;
        CivUnitRegistry.UnitKey key = unitRegistry.keyOf(ref);
        frontCoordinator.releaseWorker(key);
        roomCoordinator.releaseWorker(key);
        WorkerRuntime runtime = workers.remove(key);
        if (runtime != null) releaseInfrastructureReservation(key, runtime);
        navigationFailures.forget(key);
    }

    private void refreshBridgeTasks(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan
    ) {
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
                "endSlice", task.endSliceIndex()
            );
        }
    }

    private BridgeAssessment assessBridge(World world, RuntimeFrontPlan front) {
        int start = front.sliceIndex;
        if (start <= 0 || start >= front.slices.size() - 2) {
            return BridgeAssessment.none();
        }
        if (!floorMissing(world, front.slices.get(start))) return BridgeAssessment.none();
        if (floorMissing(world, front.slices.get(start - 1))) {
            return BridgeAssessment.abandon("UNSAFE_GAP_WITHOUT_APPROACH", false);
        }

        int end = start;
        boolean fluid = hasFluidBelow(world, front.slices.get(start));
        boolean lava = hasLavaBelow(world, front.slices.get(start));
        while (end + 1 < front.slices.size()
            && floorMissing(world, front.slices.get(end + 1))) {
            end++;
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
        if (landing >= front.slices.size()
            || !hasSafeOppositeLanding(world, front.slices.get(landing))) {
            return BridgeAssessment.abandon("GAP_WITHOUT_SAFE_LANDING", false);
        }
        if (landing + 1 >= front.slices.size()) {
            return BridgeAssessment.abandon("GAP_WITHOUT_PLANNED_CONTINUATION", false);
        }

        return BridgeAssessment.bridge(MineInfrastructurePlanner.bridgeTask(
            front.tunnelId,
            start,
            end,
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
            if (walkType != null
                && isEmpty(walkType)
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

    private static boolean floorMissing(World world, MineTunnelGeometry.Slice slice) {
        BlockPosition center = slice.floorCenter();
        BlockPosition floor = new BlockPosition(center.x(), center.y() - 1, center.z());
        BlockType type = loadedBlockType(world, floor);
        return type != null && isEmpty(type);
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

    private RuntimeInfrastructureTask selectMandatoryInfrastructureTask(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        Vector3d workerPosition,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        RuntimeInfrastructureTask best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (RuntimeInfrastructureTask candidate : minePlan.infrastructureTasks.values()) {
            if (candidate.completed || !candidate.task.mandatory()) continue;

            RuntimeFrontPlan front = frontForTunnel(minePlan, candidate.task.tunnelId());
            if (front == null || front.unavailable || !infrastructureAvailable(candidate.task, front)) {
                continue;
            }

            CivUnitRegistry.UnitKey reserved = infrastructureReservations.get(candidate.task.id());
            if (reserved != null && !reserved.equals(workerKey)) continue;

            double distance = squaredDistance(workerPosition, candidate.task.anchor());
            if (best == null
                || distance < bestDistance
                || (distance == bestDistance
                    && candidate.task.id().compareTo(best.task.id()) < 0)) {
                best = candidate;
                bestDistance = distance;
            }
        }

        if (best == null) return null;
        if (runtime.infrastructureTaskId != null
            && !runtime.infrastructureTaskId.equals(best.task.id())) {
            infrastructureReservations.remove(runtime.infrastructureTaskId, workerKey);
            runtime.clearInfrastructureAssignment();
        }
        CivUnitRegistry.UnitKey existing =
            infrastructureReservations.putIfAbsent(best.task.id(), workerKey);
        if (existing != null && !existing.equals(workerKey)) return null;

        runtime.infrastructureTaskId = best.task.id();
        runtime.selectedTaskKey = best.task.type() + ":" + best.task.id();
        runtime.resolvedInfrastructure = null;
        runtime.infrastructurePlacementIndex = 0;
        runtime.workElapsed = 0.0;
        runtime.navigationArrived();
        decisionSink.record(
            mine.id(), best.task.id(), MineDecisionCategory.WORKER, "TASK_SELECTED",
            "npc", workerLabel(null, workerKey),
            "taskType", best.task.type(),
            "reservation", "JOINED",
            "workers", 1,
            "capacity", 1,
            "tunnel", best.task.tunnelId(),
            "priority", best.task.priority()
        );
        return best;
    }

    private RuntimeInfrastructureTask selectInfrastructureTaskById(
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        UUID taskId,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        RuntimeInfrastructureTask selected = minePlan.infrastructureTasks.get(taskId);
        if (selected == null || selected.completed || selected.task.mandatory()) return null;

        CivUnitRegistry.UnitKey existing =
            infrastructureReservations.putIfAbsent(selected.task.id(), workerKey);
        if (existing != null && !existing.equals(workerKey)) return null;

        runtime.infrastructureTaskId = selected.task.id();
        runtime.selectedTaskKey = selected.task.type() + ":" + selected.task.id();
        runtime.resolvedInfrastructure = null;
        runtime.infrastructurePlacementIndex = 0;
        runtime.workElapsed = 0.0;
        runtime.navigationArrived();
        decisionSink.record(
            mine.id(), selected.task.id(), MineDecisionCategory.WORKER, "TASK_SELECTED",
            "npc", workerLabel(null, workerKey),
            "taskType", selected.task.type(),
            "reservation", "JOINED",
            "workers", 1,
            "capacity", 1,
            "tunnel", selected.task.tunnelId(),
            "priority", selected.task.priority(),
            "decoration", selected.task.decorationKind()
        );
        return selected;
    }

    private void executeInfrastructure(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        RuntimeInfrastructureTask infrastructure,
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        Vector3d workerPosition,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        stopMiningAnimation(ref, store, runtime);

        if (runtime.resolvedInfrastructure == null) {
            MineInfrastructurePlacementResolver.DecorationResolution decorationResolution = null;
            if (infrastructure.task.decoration()) {
                decorationResolution = MineInfrastructurePlacementResolver.resolveDecorationDetailed(
                    world,
                    infrastructure.task,
                    infrastructure.tunnelKind,
                    infrastructure.geometry
                );
                runtime.resolvedInfrastructure = decorationResolution.resolvedTask();
            } else {
                runtime.resolvedInfrastructure = MineInfrastructurePlacementResolver.resolve(
                    world,
                    infrastructure.task,
                    infrastructure.tunnelKind,
                    infrastructure.geometry
                );
            }
            runtime.infrastructurePlacementIndex = 0;
            runtime.workElapsed = 0.0;

            if (runtime.resolvedInfrastructure != null) {
                Vector3d resolvedTarget = runtime.resolvedInfrastructure.workTarget();
                decisionSink.record(
                    mine.id(), infrastructure.task.id(), MineDecisionCategory.WORKER,
                    "INFRASTRUCTURE_WORK_TARGET",
                    "npc", workerLabel(ref, workerKey),
                    "taskType", infrastructure.task.type(),
                    "target", formatTarget(resolvedTarget),
                    "minerPosition", formatTarget(workerPosition),
                    "distance", Math.sqrt(workerPosition.distanceSquared(resolvedTarget)),
                    "placements", runtime.resolvedInfrastructure.placements().size()
                );
            }

            if (runtime.resolvedInfrastructure == null) {
                if (decorationResolution != null) {
                    decisionSink.record(
                        mine.id(), infrastructure.task.id(), MineDecisionCategory.ADAPTER,
                        "DECORATION_SKIPPED_RUNTIME",
                        "npc", workerLabel(ref, workerKey),
                        "kind", infrastructure.task.decorationKind(),
                        "plannedSlice", infrastructure.task.startSliceIndex(),
                        "triedSlices", decorationResolution.triedSlices(),
                        "reasons", decorationResolution.reasons(),
                        "minerPosition", formatTarget(workerPosition)
                    );
                }
                decisionSink.record(
                    mine.id(), infrastructure.task.id(), MineDecisionCategory.ADAPTER,
                    "INFRASTRUCTURE_RESOLVE_FAILED",
                    "npc", workerLabel(ref, workerKey),
                    "taskType", infrastructure.task.type(),
                    "anchor", infrastructure.task.anchor(),
                    "minerPosition", formatTarget(workerPosition)
                );
                unitRegistry.clearMoveTarget(ref);
                stopBuildingAnimation(ref, store, runtime);
                if (infrastructure.task.mandatory()) {
                    RuntimeFrontPlan affected =
                        frontForTunnel(minePlan, infrastructure.task.tunnelId());
                    MineWorkFront front = affected == null ? null : currentFront(
                        world.getWorldConfig().getUuid(), mine.id(), affected.frontId
                    );
                    if (affected != null && front != null) {
                        failFront(
                            world,
                            mine,
                            affected,
                            front,
                            MineObstaclePolicy.FailureKind.MANDATORY_INFRASTRUCTURE_UNRESOLVABLE,
                            "MANDATORY_INFRASTRUCTURE_UNRESOLVABLE"
                        );
                    }
                    workerTaskEnded(
                        mine.id(), ref, workerKey, runtime, "MANDATORY_INFRASTRUCTURE_UNRESOLVABLE"
                    );
                    infrastructureReservations.remove(infrastructure.task.id(), workerKey);
                    runtime.clearInfrastructureAssignment();
                } else {
                    completeInfrastructureTask(
                        world, mine, infrastructure, workerKey, runtime, ref, store,
                        "SKIPPED_UNRESOLVABLE"
                    );
                }
                return;
            }
        }

        // Defer optional placements when the target overlaps the 1-block safety envelope
        // of still-solid authored excavation. Mandatory steps/bridges use their own policy.
        RuntimeFrontPlan blockingFront = blockingExcavationFront(world, minePlan, infrastructure, runtime);
        if (blockingFront != null) {
            deferInfrastructureNearExcavation(
                mine, infrastructure, blockingFront, ref, store, workerKey, runtime
            );
            return;
        }

        Vector3d target = runtime.resolvedInfrastructure.workTarget();
        if (!arrived(workerPosition, target)) {
            navigateTo(ref, target, runtime);
            stopBuildingAnimation(ref, store, runtime);
            return;
        }

        runtime.navigationArrived();
        unitRegistry.clearMoveTarget(ref);
        workerTaskStarted(
            mine.id(), ref, workerKey, runtime, infrastructure.task.type().name(),
            infrastructure.task.id(), 1, 1
        );
        workerState(
            mine.id(), ref, workerKey, runtime, WorkerDebugState.BUILDING, "WORK_TARGET_REACHED"
        );
        if (!runtime.buildingAnimationStarted) {
            AnimationUtils.playAnimation(
                ref, AnimationSlot.Action, BUILDING_ITEM_ANIMATIONS, BUILDING_ANIMATION, store
            );
            runtime.buildingAnimationStarted = true;
        }

        runtime.workElapsed += TICK_INTERVAL_SECONDS;
        while (runtime.workElapsed + 1.0e-9 >= INFRASTRUCTURE_SECONDS_PER_BLOCK) {
            runtime.workElapsed -= INFRASTRUCTURE_SECONDS_PER_BLOCK;
            if (runtime.infrastructurePlacementIndex
                >= runtime.resolvedInfrastructure.placements().size()) {
                completeInfrastructureTask(
                    world, mine, infrastructure, workerKey, runtime, ref, store, "COMPLETED"
                );
                return;
            }

            MineInfrastructurePlacementResolver.PlacementStep placement =
                runtime.resolvedInfrastructure.placements().get(
                    runtime.infrastructurePlacementIndex
                );
            // Recheck immediately before each native block placement: the world may
            // have changed while the miner was navigating or animating.
            blockingFront = blockingExcavationFront(world, minePlan, infrastructure, runtime);
            if (blockingFront != null) {
                deferInfrastructureNearExcavation(
                    mine, infrastructure, blockingFront, ref, store, workerKey, runtime
                );
                return;
            }
            MineBlockPlacement.PlacementResult placementResult =
                MineBlockPlacement.placeDetailed(
                    world,
                    placement.position(),
                    placement.blockId(),
                    placement.rotation(),
                    placement.placedAgainst(),
                    placement.markDeco()
                );
            if (!placementResult.success()) {
                String failureKey = placement.blockId() + "@" + placement.position()
                    + ":" + placementResult.failureReason()
                    + ":" + placementResult.existingBlockId();
                if (failureKey.equals(infrastructure.lastPlacementFailureKey)) {
                    infrastructure.repeatedPlacementFailures++;
                } else {
                    infrastructure.lastPlacementFailureKey = failureKey;
                    infrastructure.repeatedPlacementFailures = 1;
                }

                String event = infrastructure.repeatedPlacementFailures == 1
                    ? "BLOCK_PLACEMENT_FAILED"
                    : "BLOCK_PLACEMENT_FAILURE_REPEATED";
                if (infrastructure.repeatedPlacementFailures == 1
                    || infrastructure.repeatedPlacementFailures == 2
                    || infrastructure.repeatedPlacementFailures == 3
                    || infrastructure.repeatedPlacementFailures % 5 == 0) {
                    decisionSink.record(
                        mine.id(), infrastructure.task.id(), MineDecisionCategory.ADAPTER, event,
                        "npc", workerLabel(ref, workerKey),
                        "taskType", infrastructure.task.type(),
                        "blockId", placement.blockId(),
                        "position", placement.position(),
                        "placedAgainst", placement.placedAgainst(),
                        "reason", placementResult.failureReason(),
                        "existingBlockId", placementResult.existingBlockId(),
                        "repeatCount", infrastructure.repeatedPlacementFailures,
                        "workTarget", formatTarget(runtime.resolvedInfrastructure.workTarget()),
                        "minerPosition", formatTarget(workerPosition)
                    );
                }
                if (infrastructure.repeatedPlacementFailures == 3) {
                    decisionSink.record(
                        mine.id(), infrastructure.task.id(), MineDecisionCategory.ADAPTER,
                        "PLACEMENT_RETRY_LOOP_DETECTED",
                        "npc", workerLabel(ref, workerKey),
                        "taskType", infrastructure.task.type(),
                        "blockId", placement.blockId(),
                        "position", placement.position(),
                        "reason", placementResult.failureReason(),
                        "repeatCount", infrastructure.repeatedPlacementFailures
                    );
                }

                // A fixed occupied block cannot be solved by endlessly retrying placement
                // from the same position. Release optional work so miners can make progress.
                // Mandatory passability work must instead close its unsafe front.
                if (infrastructure.repeatedPlacementFailures >= 3) {
                    if (infrastructure.task.mandatory()) {
                        RuntimeFrontPlan affected =
                            frontForTunnel(minePlan, infrastructure.task.tunnelId());
                        MineWorkFront front = affected == null ? null : currentFront(
                            world.getWorldConfig().getUuid(), mine.id(), affected.frontId
                        );
                        if (affected != null && front != null) {
                            failFront(
                                world, mine, affected, front,
                                MineObstaclePolicy.FailureKind.MANDATORY_INFRASTRUCTURE_UNRESOLVABLE,
                                "MANDATORY_PLACEMENT_FAILED"
                            );
                        }
                        workerTaskEnded(
                            mine.id(), ref, workerKey, runtime, "MANDATORY_PLACEMENT_FAILED"
                        );
                        infrastructureReservations.remove(infrastructure.task.id(), workerKey);
                        unitRegistry.clearMoveTarget(ref);
                        stopBuildingAnimation(ref, store, runtime);
                        runtime.clearInfrastructureAssignment();
                    } else {
                        completeInfrastructureTask(
                            world, mine, infrastructure, workerKey, runtime, ref, store,
                            "SKIPPED_BLOCKED_POSITION"
                        );
                    }
                    return;
                }
                runtime.resolvedInfrastructure = null;
                runtime.infrastructurePlacementIndex = 0;
                runtime.workElapsed = 0.0;
                stopBuildingAnimation(ref, store, runtime);
                return;
            }
            infrastructure.lastPlacementFailureKey = null;
            infrastructure.repeatedPlacementFailures = 0;
            runtime.infrastructurePlacementIndex++;
        }

        if (runtime.infrastructurePlacementIndex
            >= runtime.resolvedInfrastructure.placements().size()) {
            completeInfrastructureTask(
                world, mine, infrastructure, workerKey, runtime, ref, store, "COMPLETED"
            );
        }
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

    private void deferInfrastructureNearExcavation(
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeInfrastructureTask infrastructure,
        RuntimeFrontPlan blockingFront,
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        infrastructure.deferredByFrontId = blockingFront.frontId;
        infrastructure.deferredAtSlice = blockingFront.sliceIndex;
        decisionSink.record(
            mine.id(), infrastructure.task.id(), MineDecisionCategory.PLANNING,
            "INFRASTRUCTURE_DELAYED",
            "npc", workerLabel(ref, workerKey),
            "taskType", infrastructure.task.type(),
            "reason", "NEAR_PENDING_EXCAVATION",
            "blockingFront", blockingFront.frontId,
            "untilSliceChanges", infrastructure.deferredAtSlice
        );
        workerTaskEnded(mine.id(), ref, workerKey, runtime, "DELAYED_NEAR_EXCAVATION");
        infrastructureReservations.remove(infrastructure.task.id(), workerKey);
        unitRegistry.clearMoveTarget(ref);
        stopBuildingAnimation(ref, store, runtime);
        runtime.clearInfrastructureAssignment();
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

    private MineNormalTaskSelector.Candidate selectNormalCandidate(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        Vector3d position,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        if (network == null) return null;

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

        MineNormalTaskSelector.Selection selection = MineNormalTaskSelector.selectWithAging(
            candidates,
            blockPosition(position),
            network.normalTaskPriorityBonuses()
        );
        if (!selection.updatedPriorityBonuses().equals(network.normalTaskPriorityBonuses())) {
            MineNetwork updated = network.withNormalTaskPriorityBonuses(
                selection.updatedPriorityBonuses()
            );
            tunnelRegistry.putNetwork(world, updated);
            network = updated;
        }

        MineNormalTaskSelector.Candidate selected = selection.selected();
        RuntimeInfrastructureTask selectedInfrastructure = selected == null
            || selected.kind() != MineNormalTaskSelector.Kind.INFRASTRUCTURE
            ? null
            : minePlan.infrastructureTasks.get(selected.id());
        if (selectedInfrastructure != null
            && selectedInfrastructure.task.type() == MineInfrastructureTask.Type.BUILD_SUPPORT) {
            runtime.lastSupportDecisionFingerprint = null;
            decisionSink.record(
                mine.id(), selected.id(), MineDecisionCategory.WORKER, "BUILD_SUPPORT_TASK_SELECTED",
                "npc", workerLabel(null, workerKey),
                "priority", selected.priority(),
                "effectivePriority", MineNormalTaskSelector.effectivePriority(
                    selected, network.normalTaskPriorityBonuses()
                ),
                "workers", selected.workerCount(),
                "capacity", selected.capacity(),
                "anchor", selected.position()
            );
        } else if (!supportSkipReasons.isEmpty()) {
            String supportFingerprint = supportSkipReasons.toString()
                + "|selected=" + (selected == null ? "-" : selected.kind() + ":" + selected.id());
            if (!supportFingerprint.equals(runtime.lastSupportDecisionFingerprint)) {
                runtime.lastSupportDecisionFingerprint = supportFingerprint;
                decisionSink.record(
                    mine.id(), null, MineDecisionCategory.WORKER, "BUILD_SUPPORT_TASK_SKIPPED",
                    "npc", workerLabel(null, workerKey),
                    "reasons", supportSkipReasons,
                    "selectedInstead", selected == null ? "-" : selected.kind() + ":" + selected.id()
                );
            }
        }
        boolean selectedDecoration = selectedInfrastructure != null
            && selectedInfrastructure.task.decoration();
        if (selectedDecoration) {
            runtime.lastDecorationDecisionFingerprint = null;
            decisionSink.record(
                mine.id(), selected.id(), MineDecisionCategory.WORKER, "DECORATION_TASK_SELECTED",
                "npc", workerLabel(null, workerKey),
                "kind", selectedInfrastructure.task.decorationKind(),
                "priority", selected.priority(),
                "effectivePriority", MineNormalTaskSelector.effectivePriority(
                    selected, network.normalTaskPriorityBonuses()
                ),
                "anchor", selected.position()
            );
        } else {
            if (!availableDecorations.isEmpty()) {
                decorationSkipReasons.merge(
                    "AVAILABLE_NOT_SELECTED", availableDecorations.size(), Integer::sum
                );
            }
            if (!decorationSkipReasons.isEmpty()) {
                String decorationFingerprint = decorationSkipReasons.toString()
                    + "|selected=" + (selected == null ? "-" : selected.kind() + ":" + selected.id());
                if (!decorationFingerprint.equals(runtime.lastDecorationDecisionFingerprint)) {
                    runtime.lastDecorationDecisionFingerprint = decorationFingerprint;
                    decisionSink.record(
                        mine.id(), null, MineDecisionCategory.WORKER, "DECORATION_TASK_SKIPPED",
                        "npc", workerLabel(null, workerKey),
                        "reasons", decorationSkipReasons,
                        "selectedInstead",
                        selected == null ? "-" : selected.kind() + ":" + selected.id()
                    );
                }
            }
        }

        runtime.waitingForCapacity = selected == null
            && MineNormalTaskSelector.allWorkAtCapacity(candidates);
        if (selected == null) {
            String fingerprint = totalFronts + ":" + totalRooms + ":" + totalInfrastructure
                + ":" + candidates.size();
            if (!fingerprint.equals(runtime.lastNoTaskFingerprint)) {
                runtime.lastNoTaskFingerprint = fingerprint;
                decisionSink.record(
                    mine.id(), null, MineDecisionCategory.WORKER, "NO_AVAILABLE_TASK",
                    "npc", workerLabel(null, workerKey),
                    "fronts", totalFronts,
                    "rooms", totalRooms,
                    "infrastructure", totalInfrastructure,
                    "executableCandidates", candidates.size()
                );
            }
        } else {
            runtime.lastNoTaskFingerprint = null;
        }
        if (selected != null) {
            decisionSink.record(
                mine.id(), selected.id(), MineDecisionCategory.PLANNING, "NORMAL_TASK_SELECTED",
                "kind", selected.kind(),
                "basePriority", selected.priority(),
                "effectivePriority", MineNormalTaskSelector.effectivePriority(
                    selected, network.normalTaskPriorityBonuses()
                ),
                "openedWaitingWork", selection.openedWaitingWork()
            );
        }
        return selected;
    }

    private RuntimeRoomPlan selectRoom(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        UUID roomId,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        RuntimeRoomPlan plan = minePlan.rooms.get(roomId);
        MineRoom room = network == null ? null : roomById(network, roomId);
        if (plan == null || room == null || !roomCoordinator.tryJoin(roomId, workerKey, roomCapacity(room))) {
            return null;
        }
        runtime.roomId = roomId;
        runtime.roomBuildSection = null;
        runtime.selectedTaskKey = (room.state() == MineRoom.State.READY_TO_BUILD
            ? "BUILD_ROOM:" : "EXCAVATE_ROOM:") + roomId;
        runtime.navigationArrived();
        decisionSink.record(
            mine.id(), roomId, MineDecisionCategory.WORKER, "TASK_SELECTED",
            "npc", workerLabel(null, workerKey),
            "taskType", room.state() == MineRoom.State.READY_TO_BUILD ? "BUILD_ROOM" : "EXCAVATE_ROOM",
            "reservation", "JOINED",
            "workers", roomCoordinator.workerCount(roomId),
            "capacity", roomCapacity(room),
            "priority", MineRoomPlanner.ROOM_PRIORITY,
            "roomType", room.type(),
            "tunnel", room.tunnelId()
        );
        return plan;
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

    private void executeRoom(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        RuntimeRoomPlan plan,
        MineRoom room,
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer,
        Vector3d workerPosition,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        if (room.state() == MineRoom.State.PLANNED) {
            room = room.withState(MineRoom.State.EXCAVATING);
            persistRoom(world, mine, room);
        }

        if (room.state() == MineRoom.State.EXCAVATING) {
            executeRoomExcavation(world, mine, plan, room, ref, store, workerPosition, workerKey, runtime);
            return;
        }

        if (room.state() != MineRoom.State.READY_TO_BUILD) {
            workerTaskEnded(mine.id(), ref, workerKey, runtime, "TASK_NO_LONGER_EXECUTABLE");
            roomCoordinator.releaseWorker(workerKey);
            runtime.clearRoomAssignment();
            return;
        }

        stopMiningAnimation(ref, store, runtime);
        int sectionCount = MineRoomPrefabService.sectionCount(room);
        if (sectionCount <= 0) {
            plan.unavailable = true;
            workerTaskEnded(mine.id(), ref, workerKey, runtime, "INVALID_ROOM_PREFAB");
            roomCoordinator.releaseWorker(workerKey);
            runtime.clearRoomAssignment();
            return;
        }
        if (room.completedBuildSections().size() >= sectionCount) {
            MineRoom built = room.withState(MineRoom.State.BUILT);
            persistRoom(world, mine, built);
            clearNormalTaskAge(world, mine, room.id());
            roomCoordinator.releaseRoom(room.id());
            runtime.clearRoomAssignment();
            decisionSink.record(
                mine.id(), room.id(), MineDecisionCategory.ROOM, "ROOM_BUILT",
                "roomType", room.type()
            );
            return;
        }

        if (!roomCoordinator.tryJoin(room.id(), workerKey, MineRoomPlanner.BUILD_CAPACITY)) {
            workerTaskEnded(mine.id(), ref, workerKey, runtime, "CAPACITY_UNAVAILABLE");
            runtime.clearRoomAssignment();
            return;
        }

        Integer section = runtime.roomBuildSection;
        if (section == null || room.completedBuildSections().contains(section)) {
            section = roomCoordinator.claimNextBuildSection(
                room.id(),
                workerKey,
                MineRoomPlanner.BUILD_CAPACITY,
                sectionCount,
                room.completedBuildSections()
            );
            runtime.roomBuildSection = section;
        }
        if (section == null) return;

        Vector3d target = new Vector3d(
            room.position().x() + 0.5,
            room.position().y(),
            room.position().z() + 0.5
        );
        if (!arrived(workerPosition, target)) {
            navigateTo(ref, target, runtime);
            stopBuildingAnimation(ref, store, runtime);
            return;
        }

        runtime.navigationArrived();
        unitRegistry.clearMoveTarget(ref);
        workerTaskStarted(
            mine.id(), ref, workerKey, runtime, "BUILD_ROOM", room.id(),
            roomCoordinator.workerCount(room.id()), MineRoomPlanner.BUILD_CAPACITY
        );
        workerState(
            mine.id(), ref, workerKey, runtime, WorkerDebugState.BUILDING, "WORK_TARGET_REACHED"
        );
        if (!runtime.buildingAnimationStarted) {
            AnimationUtils.playAnimation(
                ref, AnimationSlot.Action, BUILDING_ITEM_ANIMATIONS, BUILDING_ANIMATION, store
            );
            runtime.buildingAnimationStarted = true;
        }

        runtime.workElapsed += TICK_INTERVAL_SECONDS;
        if (runtime.workElapsed + 1.0e-9 < ROOM_BUILD_SECONDS_PER_SECTION) return;
        runtime.workElapsed = 0.0;

        if (!MineRoomPrefabService.placeSection(world, room, section, commandBuffer)) {
            plan.unavailable = true;
            workerTaskEnded(mine.id(), ref, workerKey, runtime, "ROOM_PREFAB_PLACEMENT_FAILED");
            roomCoordinator.releaseWorker(workerKey);
            stopBuildingAnimation(ref, store, runtime);
            runtime.clearRoomAssignment();
            return;
        }

        int completedCount = room.completedBuildSections().contains(section)
            ? room.completedBuildSections().size()
            : room.completedBuildSections().size() + 1;
        MineRoom.State nextState = completedCount >= sectionCount
            ? MineRoom.State.BUILT
            : MineRoom.State.READY_TO_BUILD;
        MineRoom updated = room.withBuildSectionCompleted(section, nextState);
        persistRoom(world, mine, updated);
        clearNormalTaskAge(world, mine, room.id());
        roomCoordinator.completeBuildSection(room.id(), workerKey, section);
        roomCoordinator.releaseWorker(workerKey);
        workerTaskEnded(
            mine.id(), ref, workerKey, runtime,
            nextState == MineRoom.State.BUILT ? "TASK_COMPLETED" : "WORK_UNIT_COMPLETED"
        );
        stopBuildingAnimation(ref, store, runtime);
        runtime.clearRoomAssignment();
        decisionSink.record(
            mine.id(), room.id(), MineDecisionCategory.ROOM, "WORK_UNIT_COMPLETED",
            "type", "BUILD_ROOM",
            "section", section,
            "state", nextState
        );
    }

    private void executeRoomExcavation(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeRoomPlan plan,
        MineRoom room,
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        Vector3d workerPosition,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        MineRoomGeometry geometry = plan.geometry;
        int unitIndex = room.excavationWorkUnitIndex();
        if (unitIndex >= geometry.excavationWorkUnits().size()) {
            MineRoom ready = room.withState(MineRoom.State.READY_TO_BUILD);
            persistRoom(world, mine, ready);
            clearNormalTaskAge(world, mine, room.id());
            roomCoordinator.releaseRoom(room.id());
            runtime.clearRoomAssignment();
            return;
        }

        List<BlockPosition> workUnit = geometry.excavationWorkUnits().get(unitIndex);
        if (roomWorkUnitComplete(world, workUnit)) {
            completeRoomExcavationUnit(world, mine, room, plan, unitIndex, runtime, ref, store);
            return;
        }
        if (containsBlockedSolid(world, mine, workUnit)) {
            plan.unavailable = true;
            workerTaskEnded(mine.id(), ref, workerKey, runtime, "UNSAFE_GEOMETRY");
            roomCoordinator.releaseRoom(room.id());
            stopMiningAnimation(ref, store, runtime);
            runtime.clearRoomAssignment();
            return;
        }

        if (!roomCoordinator.tryJoin(room.id(), workerKey, MineRoomPlanner.EXCAVATION_CAPACITY)) {
            workerTaskEnded(mine.id(), ref, workerKey, runtime, "CAPACITY_UNAVAILABLE");
            runtime.clearRoomAssignment();
            return;
        }

        BlockPosition work = geometry.workTargetForUnit(unitIndex);
        Vector3d target = new Vector3d(work.x() + 0.5, work.y(), work.z() + 0.5);
        if (!arrived(workerPosition, target)) {
            navigateTo(ref, target, runtime);
            stopMiningAnimation(ref, store, runtime);
            return;
        }

        runtime.navigationArrived();
        unitRegistry.clearMoveTarget(ref);
        workerTaskStarted(
            mine.id(), ref, workerKey, runtime, "EXCAVATE_ROOM", room.id(),
            roomCoordinator.workerCount(room.id()), MineRoomPlanner.EXCAVATION_CAPACITY
        );
        workerState(
            mine.id(), ref, workerKey, runtime, WorkerDebugState.WORKING, "WORK_TARGET_REACHED"
        );
        stopBuildingAnimation(ref, store, runtime);
        if (!runtime.animationStarted) {
            AnimationUtils.playAnimation(
                ref, AnimationSlot.Action, MINING_ITEM_ANIMATIONS, MINING_ANIMATION, store
            );
            runtime.animationStarted = true;
        }

        runtime.workElapsed += TICK_INTERVAL_SECONDS;
        while (runtime.workElapsed >= MineTuning.secondsPerBlock()) {
            runtime.workElapsed -= MineTuning.secondsPerBlock();
            if (!workOneRoomBlock(world, ref, store, mine, room, workUnit, workerKey, runtime)) break;
            if (roomWorkUnitComplete(world, workUnit)) {
                completeRoomExcavationUnit(world, mine, room, plan, unitIndex, runtime, ref, store);
                return;
            }
        }
    }

    private boolean workOneRoomBlock(
        World world,
        Ref<EntityStore> worker,
        Store<EntityStore> entityStore,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineRoom room,
        List<BlockPosition> workUnit,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        BlockPosition target = roomCoordinator.claimNextBlock(
            room.id(),
            workerKey,
            MineRoomPlanner.EXCAVATION_CAPACITY,
            workUnit,
            block -> isAvailableWorkBlock(world, mine, block)
        );
        runtime.claimedBlock = target;
        if (target == null) return false;

        BlockType type = loadedBlockType(world, target);
        if (type == null) return false;
        if (isEmpty(type)) {
            roomCoordinator.completeBlock(room.id(), workerKey, target);
            runtime.claimedBlock = null;
            return true;
        }
        if (!safeBlock(world, mine, target)) return false;

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        BlockHarvestUtils.performBlockBreak(
            worker,
            null,
            List.of(new Vector3i(target.x(), target.y(), target.z())),
            0,
            entityStore,
            chunkStore
        );
        if (!isEmpty(loadedBlockType(world, target))) return false;

        roomCoordinator.completeBlock(room.id(), workerKey, target);
        runtime.claimedBlock = null;
        return true;
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
        int next = completedUnit + 1;
        MineRoom.State nextState = next >= plan.geometry.excavationWorkUnits().size()
            ? MineRoom.State.READY_TO_BUILD
            : MineRoom.State.EXCAVATING;
        MineRoom updated = room.withExcavationProgress(next, nextState);
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

    private RuntimeFrontPlan selectFrontById(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        UUID frontId,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        RuntimeFrontPlan selectedPlan = minePlan.fronts.get(frontId);
        MineWorkFront selected = network == null ? null : workFrontById(network, frontId);
        if (selectedPlan == null
            || selectedPlan.complete
            || !available(selected)
            || !frontExecutable(world, minePlan, selectedPlan)
            || !frontCoordinator.tryJoin(frontId, workerKey, MineFrontCoordinator.capacityFor(selectedPlan.tunnelKind))) {
            return null;
        }

        runtime.frontId = selected.id();
        runtime.sliceIndex = selectedPlan.sliceIndex;
        runtime.selectedTaskKey = "EXCAVATE_FRONT:" + selected.id();
        runtime.navigationArrived();
        decisionSink.record(
            mine.id(), selected.id(), MineDecisionCategory.WORKER, "TASK_SELECTED",
            "npc", workerLabel(null, workerKey),
            "taskType", "EXCAVATE_FRONT",
            "reservation", "JOINED",
            "workers", frontCoordinator.workerCount(selected.id()),
            "capacity", MineFrontCoordinator.capacityFor(selectedPlan.tunnelKind),
            "tunnel", selected.tunnelId(),
            "kind", selected.tunnelId().equals(minePlan.mainTunnelId) ? "MAIN" : "BRANCH",
            "slice", selectedPlan.sliceIndex,
            "priority", MineNormalTaskSelector.effectivePriority(
                new MineNormalTaskSelector.Candidate(
                    selected.id(),
                    MineNormalTaskSelector.Kind.TUNNEL_FRONT,
                    selectedPlan.tunnelKind == MineTunnel.Kind.BRANCH
                        ? MineFrontTaskScheduler.BRANCH_TUNNEL_PRIORITY
                        : MineFrontTaskScheduler.MAIN_TUNNEL_PRIORITY,
                    frontCoordinator.workerCount(selected.id()),
                    MineFrontCoordinator.capacityFor(selectedPlan.tunnelKind),
                    selected.position()
                ),
                network.normalTaskPriorityBonuses()
            )
        );
        return selectedPlan;
    }

    private boolean workOneBlock(
        World world,
        Ref<EntityStore> worker,
        Store<EntityStore> entityStore,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeFrontPlan plan,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        BlockPosition target = frontCoordinator.claimNext(
            plan.frontId,
            workerKey,
            MineFrontCoordinator.capacityFor(plan.tunnelKind),
            plan.orderedBlocks.get(plan.sliceIndex),
            block -> isAvailableWorkBlock(world, mine, block)
        );
        runtime.claimedBlock = target;
        if (target == null) return false;

        BlockType type = loadedBlockType(world, target);
        if (type == null) return false;
        if (isEmpty(type)) {
            frontCoordinator.completeClaim(plan.frontId, workerKey, target);
            runtime.claimedBlock = null;
            return true;
        }
        if (!safeBlock(world, mine, target)) return false;

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        BlockHarvestUtils.performBlockBreak(
            worker,
            null,
            List.of(new Vector3i(target.x(), target.y(), target.z())),
            0,
            entityStore,
            chunkStore
        );
        if (!isEmpty(loadedBlockType(world, target))) return false;

        frontCoordinator.completeClaim(plan.frontId, workerKey, target);
        runtime.claimedBlock = null;
        return true;
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
        MineNetworkGrowthPlanner.Plan planned = MineNetworkGrowthPlanner.plan(
            mine.id(),
            origin,
            heading,
            MAIN_PLAN_LENGTH_BLOCKS,
            RUNTIME_PLANNING_TUNNEL_BUDGET,
            seed,
            decisionSink
        );
        List<MineRoom> plannedRooms = MineRoomPlanner.plan(planned, decisionSink);

        Map<UUID, UUID> frontIds = new LinkedHashMap<>();
        Map<UUID, MineTunnelGeometry> geometries = new LinkedHashMap<>();
        for (MineNetworkGrowthPlanner.PlannedTunnel tunnel : planned.tunnels()) {
            frontIds.put(tunnel.tunnel().id(), frontId(mine.id(), tunnel.tunnel().id()));
            geometries.put(tunnel.tunnel().id(), tunnel.geometry());
        }

        MineNetwork persisted = tunnelRegistry.networkForMine(key.worldId, mine.id());
        if (!matchesPlan(persisted, planned.network(), frontIds)) {
            if (persisted != null) {
                tunnelRegistry.removeMine(world, mine.id());
            }
            persisted = planned.network();
            for (MineRoom room : plannedRooms) persisted = persisted.withRoom(room);
            for (MineNetworkGrowthPlanner.PlannedTunnel tunnel : planned.tunnels()) {
                UUID id = frontIds.get(tunnel.tunnel().id());
                MineTunnelGeometry.Slice firstSlice = tunnel.geometry().slices().getFirst();
                MineWorkFront initialFront = new MineWorkFront(
                    id,
                    tunnel.tunnel().id(),
                    firstSlice.floorCenter(),
                    MineWorkFront.State.OPEN
                );
                persisted = persisted.withWorkFront(initialFront);
                decisionSink.record(
                    mine.id(), id, MineDecisionCategory.PLANNING, "FRONT_CREATED",
                    "tunnel", tunnel.tunnel().id(),
                    "kind", tunnel.tunnel().kind(),
                    "slice", 0,
                    "width", firstSlice.widthBlocks(),
                    "height", firstSlice.heightBlocks()
                );
            }
            tunnelRegistry.putNetwork(world, persisted);
        } else {
            boolean roomsChanged = false;
            for (MineRoom room : plannedRooms) {
                if (roomById(persisted, room.id()) == null) {
                    persisted = persisted.withRoom(room);
                    roomsChanged = true;
                }
            }
            if (roomsChanged) tunnelRegistry.putNetwork(world, persisted);
        }

        tunnelRegistry.putRuntimeGeometries(key.worldId, mine.id(), geometries);

        Map<UUID, RuntimeFrontPlan> fronts = new LinkedHashMap<>();
        for (MineNetworkGrowthPlanner.PlannedTunnel tunnel : planned.tunnels()) {
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
        for (MineNetworkGrowthPlanner.PlannedTunnel tunnel : planned.tunnels()) {
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

    private void handleTerminalNavigationFailure(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        if (runtime.roomId != null) {
            UUID failedRoomId = runtime.roomId;
            RuntimeRoomPlan room = minePlan.rooms.get(failedRoomId);
            if (room != null) room.unavailable = true;
            workerTaskEnded(
                mine.id(), ref, workerKey, runtime, "NATIVE_NAVIGATION_UNREACHABLE"
            );
            decisionSink.record(
                mine.id(), failedRoomId, MineDecisionCategory.NAVIGATION, "ROOM_UNREACHABLE",
                "reason", "NATIVE_NAVIGATION_UNREACHABLE"
            );
            clearNormalTaskAge(world, mine, failedRoomId);
            roomCoordinator.releaseWorker(workerKey);
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            runtime.clearRoomAssignment();
            runtime.navigationArrived();
            return;
        }

        RuntimeFrontPlan affected = null;
        RuntimeInfrastructureTask infrastructure = runtime.infrastructureTaskId == null
            ? null
            : minePlan.infrastructureTasks.get(runtime.infrastructureTaskId);

        if (runtime.frontId != null) {
            affected = minePlan.fronts.get(runtime.frontId);
        } else if (infrastructure != null && infrastructure.task.mandatory()) {
            affected = frontForTunnel(minePlan, infrastructure.task.tunnelId());
        }

        if (affected != null) {
            MineWorkFront front = currentFront(
                world.getWorldConfig().getUuid(), mine.id(), affected.frontId
            );
            failFront(
                world,
                mine,
                affected,
                front,
                MineObstaclePolicy.FailureKind.NAVIGATION_UNREACHABLE,
                "NATIVE_NAVIGATION_UNREACHABLE"
            );
        } else if (infrastructure != null) {
            completeInfrastructureTask(
                world,
                mine,
                infrastructure,
                workerKey,
                runtime,
                ref,
                store,
                "SKIPPED_UNREACHABLE"
            );
        }

        frontCoordinator.releaseWorker(workerKey);
        roomCoordinator.releaseWorker(workerKey);
        releaseInfrastructureReservation(workerKey, runtime);
        unitRegistry.clearMoveTarget(ref);
        stopMiningAnimation(ref, store, runtime);
        stopBuildingAnimation(ref, store, runtime);
        runtime.clearWorkAssignment();
        runtime.navigationArrived();
    }

    private boolean frontExecutable(World world, RuntimeMinePlan minePlan, RuntimeFrontPlan plan) {
        if (plan.complete || plan.unavailable || hasPendingMandatoryInfrastructure(minePlan, plan)) return false;
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
            if (building.bounds().containsBlock(block)) return false;
        }
        BlockType type = loadedBlockType(world, block);
        if (type == null || isEmpty(type)) return type != null;
        BlockGathering gathering = type.getGathering();
        BlockBreakingDropType breaking = gathering == null ? null : gathering.getBreaking();
        return breaking != null;
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
            return building != null && MINE_BUILDING.equals(building.buildingType()) ? building : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** No idle simulation: a finished accommodation is simply a preferred waiting destination. */
    private void handleIdle(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        PrefabPlacementService.PlacedMarker connector,
        Vector3d position,
        Ref<EntityStore> ref,
        WorkerRuntime runtime
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        if (network == null) return;
        CivUnitRegistry.UnitKey workerKey = unitRegistry.keyOf(ref);
        workerState(mine.id(), ref, workerKey, runtime, WorkerDebugState.IDLE, "NO_AVAILABLE_TASK");

        // All executable tasks are busy: avoid an unnecessary round trip to the entrance.
        if (runtime.waitingForCapacity && runtime.reachedConnector) {
            runtime.idleDestination = null;
            runtime.idleRoomId = null;
            unitRegistry.clearMoveTarget(ref);
            runtime.navigationArrived();
            return;
        }

        MineRoom selected = MineIdleDestinationSelector.select(
            network.rooms(), position.x, position.y, position.z,
            runtime.idleRoomId, runtime.failedIdleRooms,
            room -> { // Unloaded distant rooms are not automatically unsafe.
                BlockType observed = loadedBlockType(world, room.position());
                return observed == null || observed == BlockType.EMPTY;
            }
        );
        if (selected != null) {
            runtime.idleReachedConnector = false;
            runtime.idleRoomId = selected.id();
            Vector3d destination = new Vector3d(
                selected.position().x() + 0.5, selected.position().y(),
                selected.position().z() + 0.5
            );
            runtime.idleDestination = destination;
            if (!arrived(position, destination)) {
                navigateTo(ref, destination, runtime);
            } else {
                unitRegistry.clearMoveTarget(ref);
                runtime.navigationArrived();
            }
            return;
        }

        // Return through the same connector as ordinary mine travel, then walk to the entrance.
        runtime.idleRoomId = null;
        if (runtime.idleEntranceFailed) {
            unitRegistry.clearMoveTarget(ref);
            runtime.navigationArrived();
            return;
        }
        Vector3d tunnelExit = center(connector.bounds(), connector.bounds().minY());
        PrefabPlacementService.PlacedMarker access = marker(world, mine, WORKPLACE_ACCESS);
        if (access == null || access.bounds() == null) {
            runtime.idleDestination = tunnelExit;
            if (!arrived(position, tunnelExit)) navigateTo(ref, tunnelExit, runtime);
            else { unitRegistry.clearMoveTarget(ref); runtime.navigationArrived(); }
            return;
        }
        Vector3d outside = center(access.bounds(), access.bounds().minY());
        if (!runtime.idleReachedConnector && !arrived(position, tunnelExit)) {
            runtime.idleDestination = tunnelExit;
            navigateTo(ref, tunnelExit, runtime);
            return;
        }
        runtime.idleReachedConnector = true;
        runtime.idleDestination = outside;
        if (!arrived(position, outside)) navigateTo(ref, outside, runtime);
        else { unitRegistry.clearMoveTarget(ref); runtime.navigationArrived(); }
    }

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
        return position.distanceSquared(target) <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
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
        frontCoordinator.releaseWorker(key);
        roomCoordinator.releaseWorker(key);
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
        private boolean enteredMine;
        private boolean reachedConnector;
        private boolean animationStarted;
        private boolean buildingAnimationStarted;
        private double workElapsed;
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
            workElapsed = 0.0;
        }

        private void clearFrontAssignment() {
            frontId = null;
            sliceIndex = -1;
            claimedBlock = null;
            workElapsed = 0.0;
        }

        private void clearRoomAssignment() {
            roomId = null;
            roomBuildSection = null;
            claimedBlock = null;
            workElapsed = 0.0;
        }

        private void clearInfrastructureAssignment() {
            infrastructureTaskId = null;
            resolvedInfrastructure = null;
            infrastructurePlacementIndex = 0;
            workElapsed = 0.0;
        }

        private void navigationArrived() {
            navigationTarget = null;
        }

        private void reset(UUID nextMineId, int nextMinePhase) {
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
