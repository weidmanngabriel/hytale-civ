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
        MovementIntent manual = resident.activity.manualMovementIntent();
        if (manual != null) {
            if (advanceMovement(resident, manual.destination())) {
                resident.activity.completeManualMove();
            }
            return;
        }

        switch (resident.profession) {
            case WOODCUTTER -> tickWoodcutter(resident);
            case CONSTRUCTION_WORKER -> tickBuilder(resident);
            case FARMER -> tickFarmer(resident);
            case UNEMPLOYED -> clearMovement(resident);
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
            WorkDecisionSchedule.DecisionKind decision =
                resident.workDecisions.advance(tickSeconds);
            if (decision == WorkDecisionSchedule.DecisionKind.NONE) {
                return;
            }
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
            if (advanceMovement(resident, moveIntent.movement().destination())) {
                job.movementArrived();
            }
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
            if (removed != null) {
                metrics.recordTreeFelled();
            }
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
            WorkDecisionSchedule.DecisionKind decision =
                resident.workDecisions.advance(tickSeconds);
            if (decision == WorkDecisionSchedule.DecisionKind.NONE) {
                return;
            }
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
            if (!job.assignTarget(site.target)) {
                site.reservedBy = null;
            }
            return;
        }

        if (intent instanceof ConstructionJob.MoveToConstructionSiteIntent moveIntent) {
            if (advanceMovement(resident, moveIntent.movement().destination())) {
                job.movementArrived();
            }
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
                if (advanceMovement(resident, blockCenter(farm.entranceBlock()))) {
                    farm.arriveAtFarm();
                }
            }
            case WALKING_TO_FIELD -> tickFarmerFieldTravel(resident);
            case WORKING_FIELD -> {
                clearMovement(resident);
                if (farm.advanceWork(tickSeconds)) {
                    resident.fieldTarget = null;
                }
            }
            case RETURNING_TO_STORAGE -> {
                if (advanceMovement(resident, blockCenter(farm.exitBlock()))) {
                    farm.arriveAtFarm();
                }
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
            WorkDecisionSchedule.DecisionKind decision =
                resident.fieldDecisions.advance(tickSeconds);
            if (decision == WorkDecisionSchedule.DecisionKind.NONE) {
                return;
            }
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

        if (advanceMovement(resident, resident.fieldTarget)) {
            resident.farm.arriveAtField();
        }
    }

    private TreeState nearestAvailableTree(WorldPosition position) {
        TreeState best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (TreeState tree : trees.values()) {
            if (tree.reservedBy != null) {
                continue;
            }
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
            if (site.completed || site.reservedBy != null) {
                continue;
            }
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

    private static WorldPosition interactionPoint(
        BlockPosition tree,
        WorldPosition worker
    ) {
        double centerX = tree.x() + 0.5;
        double centerZ = tree.z() + 0.5;
        double dx = worker.x() - centerX;
        double dz = worker.z() - centerZ;

        if (Math.abs(dx) >= Math.abs(dz)) {
            return new WorldPosition(
                centerX + (dx < 0.0 ? -1.0 : 1.0),
                tree.y(),
                centerZ
            );
        }
        return new WorldPosition(
            centerX,
            tree.y(),
            centerZ + (dz < 0.0 ? -1.0 : 1.0)
        );
    }

    private static WorldPosition blockCenter(BlockPosition block) {
        return new WorldPosition(block.x() + 0.5, block.y(), block.z() + 0.5);
    }

    private static double distanceSquared(
        WorldPosition first,
        WorldPosition second
    ) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private void releaseConstructionReservation(String siteId, String residentId) {
        ConstructionSiteState site = constructionSites.get(siteId);
        if (site != null && residentId.equals(site.reservedBy)) {
            site.reservedBy = null;
        }
    }

    private static void clearMovement(Resident resident) {
        resident.movementTarget = null;
    }

    private static String stateName(Resident resident) {
        if (resident.activity.manualMovementIntent() != null) {
            return "MANUAL_MOVE";
        }
        return autonomousStateName(resident);
    }

    private static String autonomousStateName(Resident resident) {
        return switch (resident.profession) {
            case WOODCUTTER -> resident.woodcutterJob.state().name();
            case CONSTRUCTION_WORKER -> resident.constructionJob.state().name();
            case FARMER -> resident.farm.workState().name();
            case UNEMPLOYED -> "IDLE";
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

    public record TreeSnapshot(
        BlockPosition position,
        String reservedBy
    ) {
    }

    public record ConstructionSiteSnapshot(
        String siteId,
        WorldPosition workPoint,
        int totalSteps,
        String reservedBy,
        boolean completed
    ) {
    }

    public record FarmFieldSnapshot(
        String farmId,
        WorldPosition position
    ) {
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

        private Resident(
            String id,
            Profession profession,
            WorldPosition position,
            WoodcutterJob woodcutterJob,
            ConstructionJob constructionJob,
            FarmBuilding farm
        ) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id cannot be blank");
            }
            this.id = id;
            this.profession = profession;
            this.position = Objects.requireNonNull(position, "position");
            this.woodcutterJob = woodcutterJob;
            this.constructionJob = constructionJob;
            this.farm = farm;
        }

        static Resident woodcutter(String id, WorldPosition position) {
            return new Resident(
                id,
                Profession.WOODCUTTER,
                position,
                new WoodcutterJob(),
                null,
                null
            );
        }

        static Resident builder(String id, WorldPosition position) {
            return new Resident(
                id,
                Profession.CONSTRUCTION_WORKER,
                position,
                null,
                new ConstructionJob(),
                null
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
