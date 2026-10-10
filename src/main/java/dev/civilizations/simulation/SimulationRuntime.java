package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.ConstructionJob;
import dev.civilizations.core.FarmBuilding;
import dev.civilizations.core.InhabitantActivity;
import dev.civilizations.core.MovementIntent;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WoodcutterJob;
import dev.civilizations.core.WorkDecisionSchedule;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.simulation.world.VoxelWorld;
import dev.civilizations.simulation.world.WorldArchive;
import dev.civilizations.core.MineFrontCoordinator;
import dev.civilizations.core.MineHeading;
import dev.civilizations.core.MineNetworkGrowthPlanner;
import dev.civilizations.core.MineTunnelGeometry;
import dev.civilizations.core.MineTunnel;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Small deterministic runtime for exercising Civ Core behavior without Hytale.
 *
 * <p>This is intentionally not a second game engine. Movement is straight-line fake movement,
 * world queries operate on tiny in-memory fixtures, and metrics count gameplay work instead of
 * wall-clock CPU time. Hytale remains responsible for real navigation and world semantics.</p>
 */
public final class SimulationRuntime {

    public static final double DEFAULT_TICK_SECONDS = 0.05;
    public static final double DEFAULT_MOVE_SPEED = 4.0;
    public static final double DEFAULT_RETRY_SECONDS = 1.0;

    private static final double EPSILON = 1.0e-9;

    private final double tickSeconds;
    private final double moveSpeed;
    private final SimulationMetrics metrics = new SimulationMetrics();
    private final Map<String, Resident> residents = new LinkedHashMap<>();
    private final Map<BlockPosition, TreeState> trees = new LinkedHashMap<>();
    private final Map<String, ConstructionSiteState> constructionSites = new LinkedHashMap<>();
    private final Map<String, List<WorldPosition>> farmFields = new LinkedHashMap<>();

    private long tickCount;
    private VoxelWorld voxelWorld;
    private MineLab mineLab;

    public SimulationRuntime() {
        this(DEFAULT_TICK_SECONDS, DEFAULT_MOVE_SPEED);
    }

    public SimulationRuntime(double tickSeconds, double moveSpeed) {
        if (!(tickSeconds > 0.0) || !Double.isFinite(tickSeconds)) {
            throw new IllegalArgumentException("tickSeconds must be finite and > 0");
        }
        if (!(moveSpeed > 0.0) || !Double.isFinite(moveSpeed)) {
            throw new IllegalArgumentException("moveSpeed must be finite and > 0");
        }
        this.tickSeconds = tickSeconds;
        this.moveSpeed = moveSpeed;
    }

    /** Enables realistic pathfinding for this runtime; not used by live Hytale adapters. */
    public void setVoxelWorld(VoxelWorld world) {
        voxelWorld = Objects.requireNonNull(world);
        for (Resident resident : residents.values()) resident.route = List.of();
    }

    /** Starts deterministic Core-planned excavation against the optional imported voxel adapter. */
    public void configureMineLab(BlockPosition anchor, MineHeading heading, int length, long seed) {
        if (voxelWorld == null) throw new IllegalStateException("Mine lab requires imported voxel terrain");
        if (!voxelWorld.canStand(anchor)) throw new IllegalArgumentException("Mine anchor is not standable");
        mineLab = new MineLab(anchor, heading, length, seed);
    }

    public int excavatedMineBlocks() { return mineLab == null ? 0 : mineLab.excavated; }

    public void addMiner(String id, WorldPosition position) {
        addResident(Resident.miner(id, position));
    }

    public void addWoodcutter(String id, WorldPosition position) {
        addResident(Resident.woodcutter(id, position));
    }

    public void addConstructionWorker(String id, WorldPosition position) {
        addResident(Resident.builder(id, position));
    }

    public void addFarmer(String id, WorldPosition position, FarmBuilding farm) {
        requireAvailableResidentId(id);
        Objects.requireNonNull(farm, "farm");
        if (!farm.assignFarmer(id)) {
            throw new IllegalArgumentException("farm already has a farmer or cannot start");
        }
        residents.put(id, Resident.farmer(id, position, farm));
    }

    public void addTree(BlockPosition tree) {
        Objects.requireNonNull(tree, "tree");
        if (trees.putIfAbsent(tree, new TreeState(tree)) != null) {
            throw new IllegalArgumentException("tree already exists at " + tree);
        }
        for (Resident resident : residents.values()) {
            if (resident.profession == Profession.WOODCUTTER) {
                resident.workDecisions.requestImmediate();
            }
        }
    }

    public void addConstructionSite(String siteId, WorldPosition workPoint, int totalSteps) {
        ConstructionJob.WorkTarget target = new ConstructionJob.WorkTarget(
            siteId,
            Objects.requireNonNull(workPoint, "workPoint"),
            totalSteps
        );
        if (constructionSites.putIfAbsent(siteId, new ConstructionSiteState(target)) != null) {
            throw new IllegalArgumentException("construction site already exists: " + siteId);
        }
        for (Resident resident : residents.values()) {
            if (resident.profession == Profession.CONSTRUCTION_WORKER) {
                resident.workDecisions.requestImmediate();
            }
        }
    }

    public void addFarmField(String farmId, WorldPosition fieldPosition) {
        if (farmId == null || farmId.isBlank()) {
            throw new IllegalArgumentException("farmId cannot be blank");
        }
        Objects.requireNonNull(fieldPosition, "fieldPosition");
        farmFields.computeIfAbsent(farmId, ignored -> new ArrayList<>()).add(fieldPosition);
        for (Resident resident : residents.values()) {
            if (resident.profession == Profession.FARMER
                && resident.farm != null
                && resident.farm.id().equals(farmId)) {
                resident.fieldDecisions.requestImmediate();
            }
        }
    }

    public void orderManualMove(String residentId, WorldPosition destination) {
        resident(residentId).activity.orderManualMove(destination);
    }

    public boolean cancelManualMove(String residentId) {
        return resident(residentId).activity.cancelManualMove();
    }

    public void tick() {
        metrics.recordTick();
        tickCount++;
        for (Resident resident : residents.values()) {
            tickResident(resident);
        }
    }

    public void runTicks(long ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0");
        }
        for (long i = 0; i < ticks; i++) {
            tick();
        }
    }

    public void runForSeconds(double seconds) {
        if (seconds < 0.0 || !Double.isFinite(seconds)) {
            throw new IllegalArgumentException("seconds must be finite and >= 0");
        }
        double exactTicks = seconds / tickSeconds;
        long ticks = Math.round(exactTicks);
        if (Math.abs(exactTicks - ticks) > EPSILON) {
            throw new IllegalArgumentException("seconds must align to the fixed simulation tick");
        }
        runTicks(ticks);
    }

    public double elapsedSeconds() {
        return tickCount * tickSeconds;
    }

    public long tickCount() {
        return tickCount;
    }

    public double tickSeconds() {
        return tickSeconds;
    }

    public SimulationMetrics.Snapshot metrics() {
        return metrics.snapshot();
    }

    public WorldSnapshot worldSnapshot() {
        List<ResidentSnapshot> residentSnapshots = residents.values().stream()
            .map(this::snapshot)
            .toList();
        List<TreeSnapshot> treeSnapshots = trees.values().stream()
            .map(tree -> new TreeSnapshot(tree.position, tree.reservedBy))
            .toList();
        List<ConstructionSiteSnapshot> siteSnapshots = constructionSites.values().stream()
            .map(site -> new ConstructionSiteSnapshot(
                site.target.siteId(),
                site.target.workPoint(),
                site.target.totalSteps(),
                site.reservedBy,
                site.completed
            ))
            .toList();
        List<FarmFieldSnapshot> fieldSnapshots = farmFields.entrySet().stream()
            .flatMap(entry -> entry.getValue().stream()
                .map(position -> new FarmFieldSnapshot(entry.getKey(), position)))
            .toList();

        return new WorldSnapshot(
            tickCount,
            elapsedSeconds(),
            residentSnapshots,
            treeSnapshots,
            siteSnapshots,
            fieldSnapshots,
            metrics.snapshot()
        );
    }

    public ResidentSnapshot residentSnapshot(String residentId) {
        return snapshot(resident(residentId));
    }

    private ResidentSnapshot snapshot(Resident resident) {
        return new ResidentSnapshot(
            resident.id,
            resident.profession,
            resident.position,
            resident.movementTarget,
            stateName(resident),
            autonomousStateName(resident),
            resident.activity.manualMovementIntent() != null
        );
    }

    public boolean treeExists(BlockPosition tree) {
        return trees.containsKey(tree);
    }

    public boolean constructionCompleted(String siteId) {
        ConstructionSiteState site = constructionSites.get(siteId);
        return site != null && site.completed;
    }

    private void addResident(Resident resident) {
        requireAvailableResidentId(resident.id);
        residents.put(resident.id, resident);
    }

    private void requireAvailableResidentId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("resident id cannot be blank");
        }
        if (residents.containsKey(id)) {
            throw new IllegalArgumentException("resident already exists: " + id);
        }
    }

    private Resident resident(String id) {
        Resident resident = residents.get(id);
        if (resident == null) {
            throw new IllegalArgumentException("unknown resident: " + id);
        }
        return resident;
    }

    private void tickResident(Resident resident) {
        resident.activity.advance(tickSeconds);

        MovementIntent manual = resident.activity.manualMovementIntent();
        if (manual != null) {
            if (mineLab != null && resident.profession == Profession.MINER && !resident.minerSuspended) {
                mineLab.releaseWorker(resident.id);
                resident.minerWorkTicks = 0;
                resident.minerSuspended = true;
            }
            if (advanceMovement(resident, manual.destination())) {
                resident.activity.completeManualMove();
            }
            return;
        }

        if (manual == null) resident.minerSuspended = false;
        if (!resident.activity.autonomousWorkAllowed()) {
            clearMovement(resident);
            return;
        }

        switch (resident.profession) {
            case WOODCUTTER -> tickWoodcutter(resident);
            case CONSTRUCTION_WORKER -> tickBuilder(resident);
            case FARMER -> tickFarmer(resident);
            case MINER -> { if (mineLab == null) clearMovement(resident); else mineLab.tick(resident); }
            case SOLDIER, UNEMPLOYED -> clearMovement(resident);
        }
    }

    private void tickWoodcutter(Resident resident) {
        WoodcutterJob job = resident.woodcutterJob;
        BlockPosition targetTree = job.targetTree();
        if (targetTree != null && !trees.containsKey(targetTree)) {
            clearMovement(resident);
            job.abandonTarget();
            resident.workDecisions.requestImmediate();
        }

        WoodcutterJob.Intent intent = job.intent();
        if (intent instanceof WoodcutterJob.FindTreeIntent) {
            WorkDecisionSchedule.DecisionKind decision = resident.workDecisions.advance(tickSeconds);
            if (decision == WorkDecisionSchedule.DecisionKind.NONE) return;
            metrics.recordDecision(decision);
            metrics.recordTreeSearch();
            TreeState tree = nearestAvailableTree(resident.position);
            if (tree == null) {
                metrics.recordFailedPlan();
                clearMovement(resident);
                resident.workDecisions.scheduleRetry(DEFAULT_RETRY_SECONDS);
                return;
            }
            tree.reservedBy = resident.id;
            WorldPosition interactionPoint = interactionPoint(tree.position, resident.position);
            if (!job.assignTarget(new WoodcutterJob.WorkTarget(tree.position, interactionPoint))) {
                tree.reservedBy = null;
            }
            return;
        }
        if (intent instanceof WoodcutterJob.MoveToTreeIntent moveIntent) {
            if (advanceMovement(resident, moveIntent.movement().destination())) job.movementArrived();
            return;
        }
        if (intent instanceof WoodcutterJob.ChopTreeIntent) {
            clearMovement(resident);
            job.advanceWork(tickSeconds);
            return;
        }
        if (intent instanceof WoodcutterJob.FellTreeIntent fellIntent) {
            clearMovement(resident);
            TreeState removed = trees.remove(fellIntent.tree());
            if (removed != null) metrics.recordTreeFelled();
            job.fellingCompleted();
            resident.workDecisions.scheduleRetry(0.25);
        }
    }

    private void tickBuilder(Resident resident) {
        ConstructionJob job = resident.constructionJob;
        ConstructionJob.WorkTarget target = job.target();
        if (target != null) {
            ConstructionSiteState site = constructionSites.get(target.siteId());
            if (site == null || site.completed) {
                releaseConstructionReservation(target.siteId(), resident.id);
                clearMovement(resident);
                job.abandonTarget();
                resident.workDecisions.requestImmediate();
            }
        }

        ConstructionJob.Intent intent = job.intent();
        if (intent instanceof ConstructionJob.FindConstructionSiteIntent) {
            WorkDecisionSchedule.DecisionKind decision = resident.workDecisions.advance(tickSeconds);
            if (decision == WorkDecisionSchedule.DecisionKind.NONE) return;
            metrics.recordDecision(decision);
            metrics.recordConstructionSearch();
            ConstructionSiteState site = nearestAvailableConstructionSite(resident.position);
            if (site == null) {
                metrics.recordFailedPlan();
                clearMovement(resident);
                resident.workDecisions.scheduleRetry(DEFAULT_RETRY_SECONDS);
                return;
            }
            site.reservedBy = resident.id;
            if (!job.assignTarget(site.target)) site.reservedBy = null;
            return;
        }
        if (intent instanceof ConstructionJob.MoveToConstructionSiteIntent moveIntent) {
            if (advanceMovement(resident, moveIntent.movement().destination())) job.movementArrived();
            return;
        }
        if (intent instanceof ConstructionJob.BuildIntent) {
            clearMovement(resident);
            job.advanceWork(tickSeconds);
            return;
        }
        if (intent instanceof ConstructionJob.CompleteConstructionIntent completeIntent) {
            clearMovement(resident);
            ConstructionSiteState site = constructionSites.get(completeIntent.siteId());
            if (site != null && !site.completed) {
                site.completed = true;
                site.reservedBy = null;
                metrics.recordConstructionCompleted();
            }
            job.constructionCompleted();
            resident.workDecisions.scheduleRetry(0.25);
        }
    }

    private void tickFarmer(Resident resident) {
        FarmBuilding farm = resident.farm;
        switch (farm.workState()) {
            case WAITING_FOR_FARMER, WAITING_FOR_INPUTS -> clearMovement(resident);
            case WALKING_TO_FARM -> {
                if (advanceMovement(resident, blockCenter(farm.entranceBlock()))) farm.arriveAtFarm();
            }
            case WALKING_TO_FIELD -> tickFarmerFieldTravel(resident);
            case SOWING_FIELD -> {
                clearMovement(resident);
                resident.cropGrowthElapsedSeconds = 0.0;
                farm.sowingComplete(true);
            }
            case WAITING_FOR_GROWTH -> {
                clearMovement(resident);
                resident.cropGrowthElapsedSeconds += tickSeconds;
                if (resident.cropGrowthElapsedSeconds >= 5.0) {
                    resident.cropGrowthElapsedSeconds = 0.0;
                    farm.cropHarvested();
                }
            }
            case HARVESTING_FIELD -> {
                clearMovement(resident);
                farm.fieldCycleFinished();
            }
            case RETURNING_TO_STORAGE -> {
                if (advanceMovement(resident, blockCenter(farm.exitBlock()))) farm.arriveAtFarm();
            }
            case STORING_OUTPUT -> {
                clearMovement(resident);
                if (farm.outputStored()) {
                    metrics.recordFarmOutputStored();
                    resident.fieldDecisions.requestImmediate();
                }
            }
        }
    }

    private void tickFarmerFieldTravel(Resident resident) {
        if (resident.fieldTarget == null) {
            WorkDecisionSchedule.DecisionKind decision = resident.fieldDecisions.advance(tickSeconds);
            if (decision == WorkDecisionSchedule.DecisionKind.NONE) return;
            metrics.recordDecision(decision);
            metrics.recordFieldSearch();
            resident.fieldTarget = nearestField(resident.farm.id(), resident.position);
            if (resident.fieldTarget == null) {
                metrics.recordFailedPlan();
                clearMovement(resident);
                resident.fieldDecisions.scheduleRetry(DEFAULT_RETRY_SECONDS);
                return;
            }
        }
        if (advanceMovement(resident, resident.fieldTarget)) resident.farm.arriveAtField();
    }

    private TreeState nearestAvailableTree(WorldPosition position) {
        TreeState best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (TreeState tree : trees.values()) {
            if (tree.reservedBy != null) continue;
            double distance = distanceSquared(position, blockCenter(tree.position));
            if (distance < bestDistance) {
                best = tree;
                bestDistance = distance;
            }
        }
        return best;
    }

    private ConstructionSiteState nearestAvailableConstructionSite(WorldPosition position) {
        ConstructionSiteState best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (ConstructionSiteState site : constructionSites.values()) {
            if (site.completed || site.reservedBy != null) continue;
            double distance = distanceSquared(position, site.target.workPoint());
            if (distance < bestDistance) {
                best = site;
                bestDistance = distance;
            }
        }
        return best;
    }

    private WorldPosition nearestField(String farmId, WorldPosition position) {
        WorldPosition best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (WorldPosition field : farmFields.getOrDefault(farmId, List.of())) {
            double distance = distanceSquared(position, field);
            if (distance < bestDistance) {
                best = field;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean advanceMovement(Resident resident, WorldPosition target) {
        if (voxelWorld == null) return advanceStraightMovement(resident, target);
        BlockPosition current = blockAt(resident.position);
        BlockPosition goal = blockAt(target);
        if (!target.equals(resident.routeTarget) || resident.routeRevision != voxelWorld.revision()) {
            resident.route = voxelWorld.path(current, goal);
            resident.routeTarget = target;
            resident.routeRevision = voxelWorld.revision();
            resident.routeIndex = 0;
        }
        if (resident.route.isEmpty()) {
            resident.navigationBlocked = true;
            clearMovement(resident);
            return false;
        }
        resident.navigationBlocked = false;
        while (resident.routeIndex < resident.route.size()
            && resident.route.get(resident.routeIndex).equals(blockAt(resident.position))) {
            resident.routeIndex++;
        }
        if (resident.routeIndex < resident.route.size()) {
            BlockPosition next = resident.route.get(resident.routeIndex);
            WorldPosition waypoint = new WorldPosition(next.x() + 0.5, next.y(), next.z() + 0.5);
            if (advanceStraightMovement(resident, waypoint)) resident.routeIndex++;
            return false;
        }
        return advanceStraightMovement(resident, target);
    }

    private static BlockPosition blockAt(WorldPosition p) {
        return new BlockPosition((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()));
    }

    private boolean advanceStraightMovement(Resident resident, WorldPosition target) {
        if (!target.equals(resident.movementTarget)) {
            resident.movementTarget = target;
            metrics.recordMovementRequest();
        }
        double dx = target.x() - resident.position.x();
        double dy = target.y() - resident.position.y();
        double dz = target.z() - resident.position.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double step = moveSpeed * tickSeconds;
        if (distance <= step + EPSILON) {
            resident.position = target;
            resident.movementTarget = null;
            return true;
        }
        double scale = step / distance;
        resident.position = new WorldPosition(
            resident.position.x() + dx * scale,
            resident.position.y() + dy * scale,
            resident.position.z() + dz * scale
        );
        return false;
    }

    private static WorldPosition interactionPoint(BlockPosition tree, WorldPosition worker) {
        double centerX = tree.x() + 0.5;
        double centerZ = tree.z() + 0.5;
        double dx = worker.x() - centerX;
        double dz = worker.z() - centerZ;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new WorldPosition(centerX + (dx < 0.0 ? -1.0 : 1.0), tree.y(), centerZ);
        }
        return new WorldPosition(centerX, tree.y(), centerZ + (dz < 0.0 ? -1.0 : 1.0));
    }

    private static WorldPosition blockCenter(BlockPosition block) {
        return new WorldPosition(block.x() + 0.5, block.y(), block.z() + 0.5);
    }

    private static double distanceSquared(WorldPosition first, WorldPosition second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private void releaseConstructionReservation(String siteId, String residentId) {
        ConstructionSiteState site = constructionSites.get(siteId);
        if (site != null && residentId.equals(site.reservedBy)) site.reservedBy = null;
    }

    private static void clearMovement(Resident resident) {
        resident.movementTarget = null;
    }

    private static String stateName(Resident resident) {
        InhabitantActivity.ActivityMode activityMode = resident.activity.snapshot().mode();
        if (resident.navigationBlocked) return "NAVIGATION_BLOCKED";
        if (activityMode == InhabitantActivity.ActivityMode.MANUAL_MOVE) return "MANUAL_MOVE";
        if (activityMode == InhabitantActivity.ActivityMode.RESUME_DELAY) return "RESUME_DELAY";
        return autonomousStateName(resident);
    }

    private static String autonomousStateName(Resident resident) {
        return switch (resident.profession) {
            case WOODCUTTER -> resident.woodcutterJob.state().name();
            case CONSTRUCTION_WORKER -> resident.constructionJob.state().name();
            case FARMER -> resident.farm.workState().name();
            case MINER -> resident.minerState;
            case SOLDIER, UNEMPLOYED -> "IDLE";
        };
    }

    public record WorldSnapshot(
        long tickCount,
        double elapsedSeconds,
        List<ResidentSnapshot> residents,
        List<TreeSnapshot> trees,
        List<ConstructionSiteSnapshot> constructionSites,
        List<FarmFieldSnapshot> farmFields,
        SimulationMetrics.Snapshot metrics
    ) {
        public WorldSnapshot {
            residents = List.copyOf(residents);
            trees = List.copyOf(trees);
            constructionSites = List.copyOf(constructionSites);
            farmFields = List.copyOf(farmFields);
        }
    }

    public record ResidentSnapshot(
        String id,
        Profession profession,
        WorldPosition position,
        WorldPosition movementTarget,
        String state,
        String autonomousState,
        boolean manualMovementActive
    ) {
    }

    public record TreeSnapshot(BlockPosition position, String reservedBy) {
    }

    public record ConstructionSiteSnapshot(
        String siteId,
        WorldPosition workPoint,
        int totalSteps,
        String reservedBy,
        boolean completed
    ) {
    }

    public record FarmFieldSnapshot(String farmId, WorldPosition position) {
    }

    /**
     * Simulates engine execution of the Core-produced tunnel slices. This is an
     * integration fixture, not an alternative to Hytale's actual miner adapter.
     */
    private final class MineLab {
        private final BlockPosition home;
        private final List<MineTunnelGeometry.Slice> slices;
        private final MineFrontCoordinator<String> claims = new MineFrontCoordinator<>();
        private int sliceIndex;
        private int excavated;

        MineLab(BlockPosition home, MineHeading heading, int length, long seed) {
            this.home = home;
            var mineId = UUID.nameUUIDFromBytes(("headless-mine:" + seed).getBytes(StandardCharsets.UTF_8));
            this.slices = MineNetworkGrowthPlanner.plan(mineId, home, heading, length, 1, seed)
                .mainTunnel().geometry().slices();
        }

        void tick(Resident resident) {
            if (sliceIndex >= slices.size()) {
                resident.minerState = "RETURNING";
                WorldPosition destination = new WorldPosition(home.x() + .5, home.y(), home.z() + .5);
                if (advanceMovement(resident, destination)) {
                    resident.minerState = "COMPLETE";
                    clearMovement(resident);
                }
                return;
            }
            var slice = slices.get(sliceIndex);
            var candidates = slice.excavationBlocks().stream()
                .sorted(Comparator.comparingInt(BlockPosition::x)
                    .thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z))
                .toList();
            if (candidates.stream().anyMatch(p -> voxelWorld.material(p) == null)) {
                resident.minerState = "REGION_BOUNDARY";
                clearMovement(resident);
                return;
            }
            if (candidates.stream().allMatch(p -> voxelWorld.material(p) != WorldArchive.Material.SOLID)) {
                claims.releaseFront(frontId(sliceIndex));
                sliceIndex++;
                resident.minerState = "NEXT_SLICE";
                return;
            }
            var staging = sliceIndex == 0 ? home : slices.get(sliceIndex-1).floorCenter();
            WorldPosition workPoint = new WorldPosition(staging.x()+.5,staging.y(),staging.z()+.5);
            if (distanceSquared(resident.position, workPoint) > 2.25) {
                resident.minerState = "MOVING_TO_FRONT";
                advanceMovement(resident, workPoint);
                return;
            }
            UUID id = frontId(sliceIndex);
            if (!claims.tryJoin(id,resident.id,MineFrontCoordinator.capacityFor(MineTunnel.Kind.MAIN))) {
                resident.minerState = "WAIT_FRONT_CAPACITY";
                return;
            }
            BlockPosition block = claims.claimNext(id,resident.id,
                MineFrontCoordinator.capacityFor(MineTunnel.Kind.MAIN),candidates,
                p -> voxelWorld.material(p) == WorldArchive.Material.SOLID);
            if (block == null) {
                resident.minerState = "WAIT_BLOCK";
                return;
            }
            resident.minerState = "EXCAVATING";
            if (++resident.minerWorkTicks >= 5) {
                resident.minerWorkTicks = 0;
                voxelWorld.set(block, WorldArchive.Material.AIR);
                excavated++;
                claims.completeClaim(id,resident.id,block);
            }
        }

        void releaseWorker(String residentId) {
            claims.releaseWorker(residentId);
        }

        private UUID frontId(int index) {
            return UUID.nameUUIDFromBytes(("headless-front:" + index).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class Resident {
        private final String id;
        private final Profession profession;
        private final InhabitantActivity activity = new InhabitantActivity();
        private final WorkDecisionSchedule workDecisions = new WorkDecisionSchedule();
        private final WorkDecisionSchedule fieldDecisions = new WorkDecisionSchedule();
        private final WoodcutterJob woodcutterJob;
        private final ConstructionJob constructionJob;
        private final FarmBuilding farm;
        private WorldPosition position;
        private WorldPosition movementTarget;
        private WorldPosition fieldTarget;
        private double cropGrowthElapsedSeconds;
        private List<BlockPosition> route = List.of();
        private WorldPosition routeTarget;
        private long routeRevision = -1;
        private int routeIndex;
        private boolean navigationBlocked;
        private String minerState = "IDLE";
        private int minerWorkTicks;
        private boolean minerSuspended;

        private Resident(
            String id,
            Profession profession,
            WorldPosition position,
            WoodcutterJob woodcutterJob,
            ConstructionJob constructionJob,
            FarmBuilding farm
        ) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
            this.id = id;
            this.profession = profession;
            this.position = Objects.requireNonNull(position, "position");
            this.woodcutterJob = woodcutterJob;
            this.constructionJob = constructionJob;
            this.farm = farm;
        }

        static Resident miner(String id, WorldPosition position) {
            return new Resident(id, Profession.MINER, position, null, null, null);
        }

        static Resident woodcutter(String id, WorldPosition position) {
            return new Resident(id, Profession.WOODCUTTER, position, new WoodcutterJob(), null, null);
        }

        static Resident builder(String id, WorldPosition position) {
            return new Resident(
                id, Profession.CONSTRUCTION_WORKER, position, null, new ConstructionJob(), null
            );
        }

        static Resident farmer(String id, WorldPosition position, FarmBuilding farm) {
            return new Resident(id, Profession.FARMER, position, null, null, farm);
        }
    }

    private static final class TreeState {
        private final BlockPosition position;
        private String reservedBy;

        private TreeState(BlockPosition position) {
            this.position = position;
        }
    }

    private static final class ConstructionSiteState {
        private final ConstructionJob.WorkTarget target;
        private String reservedBy;
        private boolean completed;

        private ConstructionSiteState(ConstructionJob.WorkTarget target) {
            this.target = target;
        }
    }
}
