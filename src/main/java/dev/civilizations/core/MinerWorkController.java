package dev.civilizations.core;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** The miner lifecycle shared by native and simulated engines. No engine objects are retained. */
public final class MinerWorkController<W> {
    public enum State { ENTERING_ACCESS, ENTERING_CONNECTOR, SELECTING, MOVING_TO_WORK,
        WORKING, WAITING_CAPACITY, RETRYING, RETURNING_CONNECTOR, RETURNING_ACCESS,
        RESTING, INTERRUPTED, BLOCKED }
    public enum Navigation { MOVING, ARRIVED, FAILED }
    public enum Readiness { READY, COMPLETE, DEFERRED, UNSAFE, UNRESOLVABLE }
    public enum Operation { EXCAVATE, PLACE, BUILD_SECTION }
    public enum Result { SUCCESS, RETRY, UNSAFE }
    public enum End { UNIT_COMPLETE, DEFERRED, NAVIGATION_FAILED, UNSAFE, UNRESOLVABLE }
    public enum Disposition { ADVANCE, KEEP, BLOCK_FRONT, ABANDON_FRONT, DISABLE_ROOM, SKIP_OPTIONAL }
    public static Disposition disposition(Task task, End reason) {
        if (reason == End.UNIT_COMPLETE) return Disposition.ADVANCE;
        if (reason == End.DEFERRED) return Disposition.KEEP;
        if (task.kind() == MineNormalTaskSelector.Kind.ROOM) return Disposition.DISABLE_ROOM;
        if (task.kind() == MineNormalTaskSelector.Kind.INFRASTRUCTURE && !task.mandatory())
            return Disposition.SKIP_OPTIONAL;
        return reason == End.NAVIGATION_FAILED ? Disposition.BLOCK_FRONT : Disposition.ABANDON_FRONT;
    }
    public record Task(UUID id, MineNormalTaskSelector.Kind kind, int priority, int capacity,
                       BlockPosition position, boolean mandatory) {
        public Task {
            Objects.requireNonNull(id); Objects.requireNonNull(kind); Objects.requireNonNull(position);
            if (capacity < 1 || priority < 1 || priority > 10 || mandatory != (priority == 10))
                throw new IllegalArgumentException("Invalid mine task");
        }
    }
    /** revision identifies a semantic work unit, not every voxel/world revision. */
    public record Work(Readiness readiness, String revision, WorldPosition target, Operation operation,
                       double seconds, List<BlockPosition> blocks, int sectionCount,
                       Set<Integer> completedSections) {
        public Work {
            Objects.requireNonNull(readiness); Objects.requireNonNull(revision);
            Objects.requireNonNull(operation);
            blocks = List.copyOf(blocks); completedSections = Set.copyOf(completedSections);
            if (!Double.isFinite(seconds) || seconds <= 0 || sectionCount < 0)
                throw new IllegalArgumentException("Invalid work duration/sections");
            if (readiness == Readiness.READY) Objects.requireNonNull(target);
        }
        public static Work stopped(Readiness reason) {
            return new Work(reason, "", null, Operation.EXCAVATE, 1, List.of(), 0, Set.of());
        }
    }
    /** A synchronous request/result boundary: adapters must recheck the world before performing work. */
    public record WorkIntent(UUID taskId, String revision, Operation operation,
                             BlockPosition block, Integer section) {}
    public record Snapshot(State state, UUID taskId, String revision, BlockPosition claim,
                           Integer section, double elapsed, int retries) {}
    public interface Engine {
        WorldPosition access();
        WorldPosition connector();
        BlockPosition position();
        Navigation navigate(WorldPosition target);
        void stop();
        List<Task> tasks();
        default Map<UUID, Integer> priorityBonuses() { return Map.of(); }
        default void priorityBonuses(Map<UUID, Integer> bonuses) {}
        Work observe(Task task);
        boolean available(BlockPosition block);
        Result perform(WorkIntent intent);
        default void selected(Task task) {}
        void ended(Task task, End reason);
        default WorldPosition retryTarget(Task task, Work work, int attempt) { return work.target(); }
        default WorldPosition restTarget() { return null; }
        default void restFailed(WorldPosition target) {}
        default void working(Task task, Operation operation) {}
        default void idle(boolean atCapacity) {}
    }
    private static final int MAX_RETRIES = 8;
    private final MineFrontCoordinator<W> fronts;
    private final MineRoomCoordinator<W> rooms;
    private final Map<UUID, W> infrastructure;
    private final Map<W, Worker> workers = new HashMap<>();
    public MinerWorkController() { this(new MineFrontCoordinator<>(), new MineRoomCoordinator<>(), new HashMap<>()); }
    public MinerWorkController(MineFrontCoordinator<W> fronts, MineRoomCoordinator<W> rooms,
                               Map<UUID, W> infrastructure) {
        this.fronts = Objects.requireNonNull(fronts); this.rooms = Objects.requireNonNull(rooms);
        this.infrastructure = Objects.requireNonNull(infrastructure);
    }
    private final class Worker {
        State state = State.ENTERING_ACCESS;
        boolean entered, connected, exitConnector, returned, routeFailed;
        Task task;
        String revision;
        BlockPosition claim;
        Integer section;
        double elapsed;
        int retries;
        WorldPosition retryTarget;
    }
    public synchronized Snapshot snapshot(W worker) {
        Worker s = workers.computeIfAbsent(worker, ignored -> new Worker());
        return new Snapshot(s.state, s.task == null ? null : s.task.id(), s.revision,
            s.claim, s.section, s.elapsed, s.retries);
    }
    public synchronized int workerCount(Task task) {
        return switch (task.kind()) {
            case TUNNEL_FRONT -> fronts.workerCount(task.id());
            case ROOM -> rooms.workerCount(task.id());
            case INFRASTRUCTURE -> infrastructure.containsKey(task.id()) ? 1 : 0;
        };
    }
    /** Explicit reset/recovery releases all transient ownership; persisted progress belongs to the mine. */
    public synchronized void forget(W worker) {
        release(worker);
        workers.remove(worker);
    }
    public synchronized void interrupt(W worker) {
        release(worker);
        Worker s = new Worker(); s.state = State.INTERRUPTED; workers.put(worker, s);
    }
    public synchronized void restoredInside(W worker) {
        Worker s = workers.computeIfAbsent(worker, ignored -> new Worker());
        s.entered = true; s.connected = true;
    }
    public synchronized void tick(W worker, double dt, Engine engine) {
        Objects.requireNonNull(worker); Objects.requireNonNull(engine);
        if (!Double.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid tick duration");
        Worker s = workers.computeIfAbsent(worker, ignored -> new Worker());
        if (s.routeFailed) { s.state = State.BLOCKED; engine.stop(); return; }
        if (s.returned) {
            if (engine.tasks().isEmpty()) { s.state = State.RESTING; engine.idle(false); engine.stop(); return; }
            s.returned = false;
        }
        if (MineWorkerRouteDecision.next(s.entered, s.connected) == MineWorkerRouteDecision.Destination.WORKPLACE_ACCESS) {
            s.state = State.ENTERING_ACCESS;
            if (!travel(s, engine, engine.access())) return;
            s.entered = true;
        }
        if (MineWorkerRouteDecision.next(s.entered, s.connected) != MineWorkerRouteDecision.Destination.WORK_FRONT) {
            s.state = State.ENTERING_CONNECTOR;
            if (!travel(s, engine, engine.connector())) return;
            s.connected = true;
        }
        if (s.task != null) {
            Work observed = engine.observe(s.task);
            if (observed.readiness() != Readiness.READY) {
                end(worker, s, engine, endFor(observed.readiness()));
                return;
            }
            execute(worker, s, dt, engine, observed);
            return;
        }
        s.state = State.SELECTING;
        List<Task> tasks = List.copyOf(engine.tasks());
        Task selected = tasks.stream().filter(Task::mandatory)
            .filter(t -> workerCount(t) < t.capacity())
            .min(Comparator.comparingDouble((Task t) -> distance(engine.position(), t.position()))
                .thenComparing(Task::id)).orElse(null);
        List<MineNormalTaskSelector.Candidate> normal = tasks.stream().filter(t -> !t.mandatory())
            .map(t -> new MineNormalTaskSelector.Candidate(t.id(), t.kind(), t.priority(),
                workerCount(t), t.capacity(), t.position())).toList();
        if (selected == null) {
            var selection = MineNormalTaskSelector.selectWithAging(normal, engine.position(), engine.priorityBonuses());
            engine.priorityBonuses(selection.updatedPriorityBonuses());
            if (selection.selected() != null) {
                UUID id = selection.selected().id();
                selected = tasks.stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
            }
        }
        if (selected == null) {
            if (!tasks.isEmpty() && tasks.stream().allMatch(t -> workerCount(t) >= t.capacity())) {
                s.state = State.WAITING_CAPACITY; engine.idle(true); engine.stop(); return;
            }
            engine.idle(false); rest(s, engine); return;
        }
        if (!join(worker, selected)) { s.state = State.WAITING_CAPACITY; engine.idle(true); engine.stop(); return; }
        s.task = selected; s.exitConnector = false; s.returned = false;
        engine.selected(selected);
        Work work = engine.observe(selected);
        if (work.readiness() != Readiness.READY) end(worker, s, engine, endFor(work.readiness()));
        else execute(worker, s, dt, engine, work);
    }
    private boolean travel(Worker s, Engine e, WorldPosition target) {
        Navigation result = e.navigate(target);
        if (result == Navigation.ARRIVED) { e.stop(); return true; }
        if (result == Navigation.FAILED) { s.state = State.BLOCKED; s.routeFailed = true; e.stop(); }
        return false;
    }
    private void rest(Worker s, Engine e) {
        WorldPosition rest = e.restTarget();
        if (rest != null) {
            s.state = State.RESTING;
            Navigation result = e.navigate(rest);
            if (result == Navigation.FAILED) e.restFailed(rest);
            if (result != Navigation.MOVING) e.stop();
            return;
        }
        if (s.returned) { s.state = State.RESTING; e.stop(); return; }
        if (!s.exitConnector) {
            s.state = State.RETURNING_CONNECTOR;
            if (!travel(s, e, e.connector())) return;
            s.exitConnector = true;
        }
        s.state = State.RETURNING_ACCESS;
        if (travel(s, e, e.access())) {
            s.returned = true;
            // New work appearing after return must pass the connector again.
            s.connected = false;
            s.state = State.RESTING;
        }
    }
    private void execute(W worker, Worker s, double dt, Engine e, Work work) {
        // A completed unit or world-side failure may have released the shared task membership.
        if (!join(worker, s.task)) { end(worker, s, e, End.DEFERRED); return; }
        if (!work.revision().equals(s.revision)) {
            releaseClaims(worker, s);
            s.revision = work.revision(); s.elapsed = 0; s.retries = 0; s.retryTarget = null;
        }
        WorldPosition target = s.retryTarget == null ? work.target() : s.retryTarget;
        s.state = s.retryTarget == null ? State.MOVING_TO_WORK : State.RETRYING;
        Navigation navigation = e.navigate(target);
        if (navigation == Navigation.FAILED) { end(worker, s, e, End.NAVIGATION_FAILED); return; }
        if (navigation != Navigation.ARRIVED) return;
        e.stop(); s.state = State.WORKING; e.working(s.task, work.operation());
        s.elapsed += dt;
        while (s.elapsed + 1e-9 >= work.seconds()) {
            s.elapsed -= work.seconds();
            BlockPosition block = null;
            Integer section = null;
            if (work.operation() == Operation.EXCAVATE) {
                // Discard a claim invalidated by another worker or an external world change.
                if (s.claim != null && (!work.blocks().contains(s.claim) || !e.available(s.claim))) releaseClaims(worker, s);
                if (s.task.kind() == MineNormalTaskSelector.Kind.ROOM)
                    block = rooms.claimNextBlock(s.task.id(), worker, s.task.capacity(), work.blocks(), e::available);
                else block = MineFrontWorkDecision.choose(fronts, s.task.id(), worker,
                    s.task.capacity(), work.blocks(), e::available).block();
                s.claim = block;
                if (block == null) return;
            } else if (work.operation() == Operation.BUILD_SECTION) {
                if (s.section != null && work.completedSections().contains(s.section)) releaseClaims(worker, s);
                section = rooms.claimNextBuildSection(s.task.id(), worker, s.task.capacity(),
                    work.sectionCount(), work.completedSections());
                s.section = section;
                if (section == null) return;
            }
            var intent = new WorkIntent(s.task.id(), work.revision(), work.operation(), block, section);
            Result result = e.perform(intent);
            if (result == Result.UNSAFE) { end(worker, s, e, End.UNSAFE); return; }
            if (result == Result.RETRY) {
                if (++s.retries > MAX_RETRIES) { end(worker, s, e, End.UNRESOLVABLE); return; }
                s.retryTarget = e.retryTarget(s.task, work, s.retries);
                if (s.retryTarget == null) { end(worker, s, e, End.UNRESOLVABLE); return; }
                s.elapsed = 0; s.state = State.RETRYING; return;
            }
            releaseClaims(worker, s);
            s.retries = 0;
            if (work.operation() == Operation.EXCAVATE) s.retryTarget = null;
            Work next = e.observe(s.task);
            if (next.readiness() != Readiness.READY) { end(worker, s, e, endFor(next.readiness())); return; }
            if (!next.revision().equals(s.revision)) { end(worker, s, e, End.UNIT_COMPLETE); return; }
            work = next;
        }
    }
    private boolean join(W worker, Task task) {
        return switch (task.kind()) {
            case TUNNEL_FRONT -> fronts.tryJoin(task.id(), worker, task.capacity());
            case ROOM -> rooms.tryJoin(task.id(), worker, task.capacity());
            case INFRASTRUCTURE -> {
                W previous = infrastructure.putIfAbsent(task.id(), worker);
                yield previous == null || previous.equals(worker);
            }
        };
    }
    private void releaseClaims(W worker, Worker s) {
        if (s.task != null) {
            if (s.claim != null) {
                fronts.completeClaim(s.task.id(), worker, s.claim);
                rooms.completeBlock(s.task.id(), worker, s.claim);
            }
            if (s.section != null) rooms.completeBuildSection(s.task.id(), worker, s.section);
        }
        s.claim = null; s.section = null;
    }
    private void release(W worker) {
        fronts.releaseWorker(worker); rooms.releaseWorker(worker);
        infrastructure.entrySet().removeIf(entry -> entry.getValue().equals(worker));
    }
    private void end(W worker, Worker s, Engine e, End reason) {
        Task task = s.task;
        release(worker); s.task = null; s.claim = null; s.section = null; s.revision = null;
        s.elapsed = 0; s.retries = 0; s.retryTarget = null;
        s.state = reason == End.NAVIGATION_FAILED || reason == End.UNSAFE || reason == End.UNRESOLVABLE
            ? State.BLOCKED : State.SELECTING;
        e.stop(); e.ended(task, reason);
    }
    private static End endFor(Readiness r) {
        return switch (r) {
            case COMPLETE -> End.UNIT_COMPLETE;
            case DEFERRED -> End.DEFERRED;
            case UNSAFE -> End.UNSAFE;
            case UNRESOLVABLE -> End.UNRESOLVABLE;
            case READY -> throw new IllegalArgumentException("Ready work has not ended");
        };
    }
    private static double distance(BlockPosition a, BlockPosition b) {
        double x = (double)a.x()-b.x(), y = (double)a.y()-b.y(), z = (double)a.z()-b.z();
        return x*x+y*y+z*z;
    }
}
