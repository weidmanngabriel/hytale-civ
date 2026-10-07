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
import dev.civilizations.core.MineDecisionCategory;
import dev.civilizations.core.MineDecisionSink;
import dev.civilizations.core.MineFrontCoordinator;
import dev.civilizations.core.MineFrontTaskScheduler;
import dev.civilizations.core.MineHeading;
import dev.civilizations.core.MineInfrastructurePlanner;
import dev.civilizations.core.MineInfrastructureTask;
import dev.civilizations.core.MineNetwork;
import dev.civilizations.core.MineNetworkGrowthPlanner;
import dev.civilizations.core.MineNormalTaskSelector;
import dev.civilizations.core.MineObstaclePolicy;
import dev.civilizations.core.MinePathPlanner;
import dev.civilizations.core.MineRoom;
import dev.civilizations.core.MineRoomCoordinator;
import dev.civilizations.core.MineRoomGeometry;
import dev.civilizations.core.MineRoomPlanner;
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

        RuntimeMinePlan minePlan = ensureRuntimePlan(world, mine, connector);
        if (minePlan == null) return;

        if (navigationFailures.consumeIfMatches(workerKey, runtime.navigationTarget)) {
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

        if (runtime.infrastructureTaskId != null) {
            RuntimeInfrastructureTask infrastructure =
                minePlan.infrastructureTasks.get(runtime.infrastructureTaskId);
            if (infrastructure == null || infrastructure.completed) {
                releaseInfrastructureReservation(workerKey, runtime);
                runtime.clearInfrastructureAssignment();
            } else {
                executeInfrastructure(
                    world, mine, minePlan, infrastructure, ref, store, position, workerKey, runtime
                );
                return;
            }
        }

        RuntimeInfrastructureTask mandatoryInfrastructure =
            selectInfrastructureTask(world, mine, minePlan, position, workerKey, runtime, true);
        if (mandatoryInfrastructure != null) {
            if (runtime.frontId != null) frontCoordinator.releaseWorker(workerKey);
            if (runtime.roomId != null) roomCoordinator.releaseWorker(workerKey);
            runtime.clearFrontAssignment();
            runtime.clearRoomAssignment();
            executeInfrastructure(
                world, mine, minePlan, mandatoryInfrastructure, ref, store, position, workerKey, runtime
            );
            return;
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
                selectNormalCandidate(world, mine, minePlan, position);
            if (candidate != null
                && (candidate.workerCount() > 0
                    || candidate.kind() == MineNormalTaskSelector.Kind.ROOM)) {
                if (candidate.kind() == MineNormalTaskSelector.Kind.ROOM) {
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
                } else {
                    plan = selectFront(world, mine, minePlan, position, workerKey, runtime);
                    front = plan == null ? null : currentFront(worldId, mine.id(), plan.frontId);
                }
            }
        }

        if (plan == null && runtime.frontId == null) {
            RuntimeInfrastructureTask infrastructure =
                selectInfrastructureTask(world, mine, minePlan, position, workerKey, runtime, false);
            if (infrastructure != null) {
                executeInfrastructure(
                    world, mine, minePlan, infrastructure, ref, store, position, workerKey, runtime
                );
                return;
            }
        }

        if (plan == null || plan.complete || !available(front)) {
            if (runtime.frontId != null) frontCoordinator.releaseWorker(workerKey);
            runtime.clearFrontAssignment();
            plan = selectFront(world, mine, minePlan, position, workerKey, runtime);
            front = plan == null ? null : currentFront(worldId, mine.id(), plan.frontId);
        }

        if (plan == null || front == null) {
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            return;
        }

        if (!frontCoordinator.tryJoin(plan.frontId, workerKey)) {
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
                completeCurrentSlice(world, mine, plan);
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
                mine.id(), task.id(), MineDecisionCategory.PLANNING, "INFRASTRUCTURE_CREATED",
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
        if (landing >= front.slices.size() || floorMissing(world, front.slices.get(landing))) {
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

    private RuntimeInfrastructureTask selectInfrastructureTask(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        Vector3d workerPosition,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime,
        boolean mandatoryOnly
    ) {
        RuntimeInfrastructureTask best = null;
        int bestPriority = Integer.MIN_VALUE;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (RuntimeInfrastructureTask candidate : minePlan.infrastructureTasks.values()) {
            if (candidate.completed) continue;
            if (mandatoryOnly && !candidate.task.mandatory()) continue;
            if (!mandatoryOnly && candidate.task.mandatory()) continue;

            RuntimeFrontPlan front = frontForTunnel(minePlan, candidate.task.tunnelId());
            if (front == null || front.unavailable || !infrastructureAvailable(candidate.task, front)) continue;

            CivUnitRegistry.UnitKey reserved = infrastructureReservations.get(candidate.task.id());
            if (reserved != null && !reserved.equals(workerKey)) continue;

            double distance = squaredDistance(workerPosition, candidate.task.anchor());
            int priority = candidate.task.priority();
            if (best == null
                || priority > bestPriority
                || (priority == bestPriority && distance < bestDistance)
                || (priority == bestPriority && distance == bestDistance
                    && candidate.task.id().compareTo(best.task.id()) < 0)) {
                best = candidate;
                bestPriority = priority;
                bestDistance = distance;
            }
        }

        if (best == null) return null;
        CivUnitRegistry.UnitKey existing =
            infrastructureReservations.putIfAbsent(best.task.id(), workerKey);
        if (existing != null && !existing.equals(workerKey)) return null;

        runtime.infrastructureTaskId = best.task.id();
        runtime.resolvedInfrastructure = null;
        runtime.infrastructurePlacementIndex = 0;
        runtime.workElapsed = 0.0;
        runtime.navigationArrived();
        decisionSink.record(
            mine.id(), best.task.id(), MineDecisionCategory.PLANNING, "TASK_SELECTED",
            "type", best.task.type(),
            "tunnel", best.task.tunnelId(),
            "priority", best.task.priority()
        );
        return best;
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
            runtime.resolvedInfrastructure = MineInfrastructurePlacementResolver.resolve(
                world,
                infrastructure.task,
                infrastructure.tunnelKind,
                infrastructure.geometry
            );
            runtime.infrastructurePlacementIndex = 0;
            runtime.workElapsed = 0.0;

            if (runtime.resolvedInfrastructure == null) {
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

        Vector3d target = runtime.resolvedInfrastructure.workTarget();
        if (!arrived(workerPosition, target)) {
            navigateTo(ref, target, runtime);
            stopBuildingAnimation(ref, store, runtime);
            return;
        }

        runtime.navigationArrived();
        unitRegistry.clearMoveTarget(ref);
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
            boolean placed = MineBlockPlacement.place(
                world,
                placement.position(),
                placement.blockId(),
                placement.rotation(),
                placement.placedAgainst(),
                placement.markDeco()
            );
            if (!placed) {
                // The world may have changed since shape resolution. Preserve already placed
                // blocks, re-resolve on the next work tick and confirm them idempotently.
                runtime.resolvedInfrastructure = null;
                runtime.infrastructurePlacementIndex = 0;
                runtime.workElapsed = 0.0;
                stopBuildingAnimation(ref, store, runtime);
                return;
            }
            runtime.infrastructurePlacementIndex++;
        }

        if (runtime.infrastructurePlacementIndex
            >= runtime.resolvedInfrastructure.placements().size()) {
            completeInfrastructureTask(
                world, mine, infrastructure, workerKey, runtime, ref, store, "COMPLETED"
            );
        }
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
        if (task.type() == MineInfrastructureTask.Type.BUILD_BRIDGE) {
            return !front.complete && task.startSliceIndex() == front.sliceIndex;
        }
        return front.complete || task.startSliceIndex() < front.sliceIndex;
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
        List<MineTunnelGeometry.Slice> slices = front.slices;
        Set<BlockPosition> excavation = new HashSet<>();
        Set<BlockPosition> navigation = new HashSet<>();
        for (MineTunnelGeometry.Slice slice : slices) {
            excavation.addAll(slice.excavationBlocks());
            navigation.addAll(slice.navigationCoreBlocks());
        }
        List<MineTunnelGeometry.StepTransition> steps = new ArrayList<>();
        for (int index = 1; index < slices.size(); index++) {
            BlockPosition previous = slices.get(index - 1).floorCenter();
            BlockPosition current = slices.get(index).floorCenter();
            if (Math.abs(current.y() - previous.y()) == 1) {
                steps.add(new MineTunnelGeometry.StepTransition(
                    index - 1, index, previous, current
                ));
            }
        }
        return new MineTunnelGeometry(
            front.tunnelKind,
            0L,
            slices,
            excavation,
            navigation,
            steps
        );
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
        Vector3d position
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        if (network == null) return null;

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
                MineFrontCoordinator.NORMAL_TUNNEL_FRONT_CAPACITY,
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
        return MineNormalTaskSelector.select(candidates, blockPosition(position));
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
        runtime.navigationArrived();
        decisionSink.record(
            mine.id(), roomId, MineDecisionCategory.PLANNING, "TASK_SELECTED",
            "type", room.state() == MineRoom.State.READY_TO_BUILD ? "BUILD_ROOM" : "EXCAVATE_ROOM",
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
            roomCoordinator.releaseWorker(workerKey);
            runtime.clearRoomAssignment();
            return;
        }

        stopMiningAnimation(ref, store, runtime);
        int sectionCount = MineRoomPrefabService.sectionCount(room);
        if (sectionCount <= 0) {
            plan.unavailable = true;
            roomCoordinator.releaseWorker(workerKey);
            runtime.clearRoomAssignment();
            return;
        }
        if (room.completedBuildSections().size() >= sectionCount) {
            MineRoom built = room.withState(MineRoom.State.BUILT);
            persistRoom(world, mine, built);
            roomCoordinator.releaseRoom(room.id());
            runtime.clearRoomAssignment();
            decisionSink.record(
                mine.id(), room.id(), MineDecisionCategory.PLANNING, "ROOM_BUILT",
                "roomType", room.type()
            );
            return;
        }

        if (!roomCoordinator.tryJoin(room.id(), workerKey, MineRoomPlanner.BUILD_CAPACITY)) {
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
        roomCoordinator.completeBuildSection(room.id(), workerKey, section);
        roomCoordinator.releaseWorker(workerKey);
        stopBuildingAnimation(ref, store, runtime);
        runtime.clearRoomAssignment();
        decisionSink.record(
            mine.id(), room.id(), MineDecisionCategory.PLANNING, "WORK_UNIT_COMPLETED",
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
            roomCoordinator.releaseRoom(room.id());
            stopMiningAnimation(ref, store, runtime);
            runtime.clearRoomAssignment();
            return;
        }

        if (!roomCoordinator.tryJoin(room.id(), workerKey, MineRoomPlanner.EXCAVATION_CAPACITY)) {
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
        roomCoordinator.releaseRoom(room.id());
        stopMiningAnimation(ref, store, runtime);
        runtime.clearRoomAssignment();
        decisionSink.record(
            mine.id(), room.id(), MineDecisionCategory.PLANNING, "WORK_UNIT_COMPLETED",
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

    private void persistRoom(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineRoom room
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        if (network != null) tunnelRegistry.putNetwork(world, network.withRoom(room));
    }

    private RuntimeFrontPlan selectFront(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeMinePlan minePlan,
        Vector3d position,
        CivUnitRegistry.UnitKey workerKey,
        WorkerRuntime runtime
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        if (network == null) return null;

        List<MineWorkFront> executable = new ArrayList<>();
        Map<UUID, Integer> workerCounts = new HashMap<>();
        for (RuntimeFrontPlan candidate : minePlan.fronts.values()) {
            if (candidate.complete) continue;
            MineWorkFront front = workFrontById(network, candidate.frontId);
            if (!available(front) || !frontExecutable(world, minePlan, candidate)) continue;
            executable.add(front);
            workerCounts.put(front.id(), frontCoordinator.workerCount(front.id()));
        }

        MineWorkFront selected = MineFrontTaskScheduler.select(
            network,
            executable,
            workerCounts,
            blockPosition(position)
        );
        if (selected == null || !frontCoordinator.tryJoin(selected.id(), workerKey)) return null;

        RuntimeFrontPlan selectedPlan = minePlan.fronts.get(selected.id());
        if (selectedPlan == null) {
            frontCoordinator.releaseWorker(workerKey);
            return null;
        }
        runtime.frontId = selected.id();
        runtime.sliceIndex = selectedPlan.sliceIndex;
        runtime.navigationArrived();
        decisionSink.record(
            mine.id(), selected.id(), MineDecisionCategory.PLANNING, "TASK_SELECTED",
            "tunnel", selected.tunnelId(),
            "kind", selected.tunnelId().equals(minePlan.mainTunnelId) ? "MAIN" : "BRANCH",
            "slice", selectedPlan.sliceIndex
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
            seed
        );
        List<MineRoom> plannedRooms = MineRoomPlanner.plan(planned);

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
                MineInfrastructurePlanner.plan(tunnel.tunnel().id(), tunnel.geometry())) {
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
                completeCurrentSlice(world, mine, plan);
                // A newly reached slice can expose a due stair/bridge on the next outer tick.
                // Stop here rather than skipping multiple semantic work boundaries at once.
                if (hasPendingMandatoryInfrastructure(minePlan, plan)) break;
            }
        }
    }

    private void completeCurrentSlice(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeFrontPlan plan
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        if (network == null) return;
        MineWorkFront current = workFrontById(network, plan.frontId);
        if (current == null) return;

        int completedIndex = plan.sliceIndex;
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
        tunnelRegistry.putNetwork(world, network.withWorkFront(updated));
        decisionSink.record(
            mine.id(), plan.frontId, MineDecisionCategory.PLANNING, "WORK_UNIT_COMPLETED",
            "slice", completedIndex,
            "nextSlice", plan.complete ? "COMPLETE" : plan.sliceIndex
        );
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
        tunnelRegistry.putNetwork(world, network.withWorkFront(failed));
        frontCoordinator.releaseFront(plan.frontId);
        plan.unavailable = true;
        decisionSink.record(
            mine.id(),
            front.id(),
            MineDecisionCategory.PLANNING,
            state == MineWorkFront.State.BLOCKED ? "FRONT_BLOCKED" : "FRONT_ABANDONED",
            "reason", reason,
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
            RuntimeRoomPlan room = minePlan.rooms.get(runtime.roomId);
            if (room != null) room.unavailable = true;
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

    private void navigateTo(Ref<EntityStore> ref, Vector3d target, WorkerRuntime runtime) {
        if (runtime.navigationTarget == null || runtime.navigationTarget.distanceSquared(target) > 0.0001) {
            runtime.navigationTarget = new Vector3d(target);
            unitRegistry.setMoveTarget(ref, target);
        }
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
        private final List<MineTunnelGeometry.Slice> slices;
        private final List<List<BlockPosition>> orderedBlocks;
        private int sliceIndex;
        private boolean complete;
        private boolean unavailable;

        private RuntimeFrontPlan(
            UUID frontId,
            UUID tunnelId,
            MineTunnel.Kind tunnelKind,
            List<MineTunnelGeometry.Slice> slices,
            List<List<BlockPosition>> orderedBlocks,
            int sliceIndex,
            boolean complete,
            boolean unavailable
        ) {
            this.frontId = frontId;
            this.tunnelId = tunnelId;
            this.tunnelKind = tunnelKind;
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

        private void interruptForManualMove() {
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
            mineId = nextMineId;
            minePhase = nextMinePhase;
            enteredMine = false;
            reachedConnector = false;
            animationStarted = false;
            clearAssignment();
        }
    }
}
