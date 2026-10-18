package dev.civilizations.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Core-owned forester lifecycle. Engine implementations only report observable outcomes. */
public final class ForesterWorkController<W> {
    public enum Navigation { MOVING, ARRIVED, FAILED }
    public enum State { SELECTING, TRAVELLING, WAITING_FOR_STOCK, RETRY }
    public interface Engine {
        int saplingsAvailable();
        BlockPosition selectSite(Set<BlockPosition> reserved);
        boolean siteStillValid(BlockPosition target);
        Navigation navigate(BlockPosition target);
        /** Must return success only after placement and stock consumption both succeed. */
        boolean plant(BlockPosition target);
    }
    public record Snapshot(State state, BlockPosition target) {}
    private final Map<W, Snapshot> workers = new HashMap<>();
    private final Map<BlockPosition, W> claims = new HashMap<>();

    public Snapshot snapshot(W worker) {
        return workers.getOrDefault(worker, new Snapshot(State.SELECTING, null));
    }

    public void interrupt(W worker) {
        Snapshot current = workers.remove(worker);
        if (current != null && current.target() != null) claims.remove(current.target(), worker);
    }

    /** Advance one engine observation; retry scheduling is handled by the shared work scheduler. */
    public Snapshot tick(W worker, Engine engine) {
        Objects.requireNonNull(worker);
        Objects.requireNonNull(engine);
        Snapshot current = snapshot(worker);
        if (engine.saplingsAvailable() <= 0) {
            interrupt(worker);
            return store(worker, State.WAITING_FOR_STOCK, null);
        }
        BlockPosition target = current.target();
        if (target == null) {
            Set<BlockPosition> reserved = new HashSet<>(claims.keySet());
            target = engine.selectSite(Set.copyOf(reserved));
            if (target == null || reserved.contains(target)) return store(worker, State.RETRY, null);
            claims.put(target, worker);
        }
        if (!engine.siteStillValid(target)) {
            claims.remove(target, worker);
            return store(worker, State.RETRY, null);
        }
        Navigation nav = engine.navigate(target);
        if (nav == Navigation.MOVING) return store(worker, State.TRAVELLING, target);
        claims.remove(target, worker);
        if (nav == Navigation.FAILED) return store(worker, State.RETRY, null);
        return store(worker, engine.plant(target) ? State.SELECTING : State.RETRY, null);
    }

    private Snapshot store(W worker, State state, BlockPosition target) {
        Snapshot snapshot = new Snapshot(state, target);
        workers.put(worker, snapshot);
        return snapshot;
    }
}
