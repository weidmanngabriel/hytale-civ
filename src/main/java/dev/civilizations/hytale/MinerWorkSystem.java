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
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
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
import dev.civilizations.core.MinePathPlanner;
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
    private final MineFrontCoordinator<CivUnitRegistry.UnitKey> frontCoordinator = new MineFrontCoordinator<>();
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
        this(unitRegistry, activityRegistry, buildingRegistry, tunnelRegistry, MineDecisionSink.NONE);
    }

    public MinerWorkSystem(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        BuildingPlacementRegistry buildingRegistry,
        MineTunnelRegistry tunnelRegistry,
        MineDecisionSink decisionSink
    ) {
        super(TICK_INTERVAL_SECONDS);
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
        this.buildingRegistry = buildingRegistry;
        this.tunnelRegistry = tunnelRegistry;
        this.decisionSink = decisionSink == null ? MineDecisionSink.NONE : decisionSink;
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
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            frontCoordinator.releaseWorker(workerKey);
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
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            frontCoordinator.releaseWorker(workerKey);
            releaseInfrastructureReservation(workerKey, runtime);
            runtime.clearAssignment();
            return;
        }
        if (!mine.id().equals(runtime.mineId) || mine.phase() != runtime.minePhase) {
            stopMiningAnimation(ref, store, runtime);
            stopBuildingAnimation(ref, store, runtime);
            frontCoordinator.releaseWorker(workerKey);
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
        advanceAlreadyExcavatedSlices(world, mine, minePlan);
        refreshBridgeTasks(world, minePlan);

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
            runtime.clearFrontAssignment();
            executeInfrastructure(
                world, mine, minePlan, mandatoryInfrastructure, ref, store, position, workerKey, runtime
            );
            return;
        }

        if (runtime.frontId != null && frontCoordinator.workerCount(runtime.frontId) == 0) {
            runtime.clearWorkAssignment();
        }

        RuntimeFrontPlan plan = runtime.frontId == null ? null : minePlan.fronts.get(runtime.frontId);
        MineWorkFront front = plan == null ? null : currentFront(worldId, mine.id(), plan.frontId);
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
            runtime.clearWorkAssignment();
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
            blockFront(world, mine, plan, front);
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
        workers.remove(key);
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
                persistedFront.state() == MineWorkFront.State.COMPLETE
            ));
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
            planned.network().mainTunnelId(), fronts, infrastructureTasks
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
            while (!plan.complete && sliceComplete(world, plan.slices.get(plan.sliceIndex))) {
                completeCurrentSlice(world, mine, plan);
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

    private void blockFront(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        RuntimeFrontPlan plan,
        MineWorkFront front
    ) {
        MineNetwork network = tunnelRegistry.networkForMine(world.getWorldConfig().getUuid(), mine.id());
        if (network == null) return;
        MineWorkFront blocked = new MineWorkFront(
            front.id(), front.tunnelId(), front.position(), MineWorkFront.State.BLOCKED
        );
        tunnelRegistry.putNetwork(world, network.withWorkFront(blocked));
        frontCoordinator.releaseFront(plan.frontId);
        decisionSink.record(
            mine.id(), front.id(), MineDecisionCategory.PLANNING, "FRONT_ABANDONED",
            "reason", "UNSAFE_OR_UNBREAKABLE_SLICE",
            "slice", plan.sliceIndex
        );
    }

    private boolean frontExecutable(World world, RuntimeMinePlan minePlan, RuntimeFrontPlan plan) {
        if (plan.complete || hasPendingMandatoryInfrastructure(minePlan, plan)) return false;
        if (plan.tunnelKind == MineTunnel.Kind.MAIN || plan.sliceIndex > 0) return true;
        BlockType start = loadedBlockType(world, plan.slices.getFirst().floorCenter());
        return start != null && isEmpty(start);
    }

    private MineWorkFront currentFront(UUID worldId, UUID mineId, UUID frontId) {
        MineNetwork network = tunnelRegistry.networkForMine(worldId, mineId);
        return network == null ? null : workFrontById(network, frontId);
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

    private static final class RuntimeMinePlan {
        private final UUID mainTunnelId;
        private final Map<UUID, RuntimeFrontPlan> fronts;
        private final Map<UUID, RuntimeInfrastructureTask> infrastructureTasks;

        private RuntimeMinePlan(
            UUID mainTunnelId,
            Map<UUID, RuntimeFrontPlan> fronts,
            Map<UUID, RuntimeInfrastructureTask> infrastructureTasks
        ) {
            this.mainTunnelId = mainTunnelId;
            this.fronts = new LinkedHashMap<>(fronts);
            this.infrastructureTasks = new LinkedHashMap<>(infrastructureTasks);
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

        private RuntimeFrontPlan(
            UUID frontId,
            UUID tunnelId,
            MineTunnel.Kind tunnelKind,
            List<MineTunnelGeometry.Slice> slices,
            List<List<BlockPosition>> orderedBlocks,
            int sliceIndex,
            boolean complete
        ) {
            this.frontId = frontId;
            this.tunnelId = tunnelId;
            this.tunnelKind = tunnelKind;
            this.slices = List.copyOf(slices);
            this.orderedBlocks = List.copyOf(orderedBlocks);
            this.sliceIndex = sliceIndex;
            this.complete = complete;
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
            clearInfrastructureAssignment();
            workElapsed = 0.0;
        }

        private void clearFrontAssignment() {
            frontId = null;
            sliceIndex = -1;
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
