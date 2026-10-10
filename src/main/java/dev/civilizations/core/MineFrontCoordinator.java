package dev.civilizations.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Hytale-independent runtime coordination for miners sharing one excavation front.
 *
 * <p>The mine/task layer owns which front is selected. This coordinator only enforces the V1
 * tunnel-front capacity and short-lived block claims inside that selected front.</p>
 */
public final class MineFrontCoordinator<W> {

    public static final int NORMAL_TUNNEL_FRONT_CAPACITY = 2;

    private final Map<UUID, FrontState<W>> fronts = new HashMap<>();

    public synchronized boolean tryJoin(UUID frontId, W worker) {
        return tryJoin(frontId, worker, NORMAL_TUNNEL_FRONT_CAPACITY);
    }

    public static int capacityFor(MineTunnel.Kind kind) {
        return kind == MineTunnel.Kind.MAIN ? 3 : NORMAL_TUNNEL_FRONT_CAPACITY;
    }

    public synchronized boolean tryJoin(UUID frontId, W worker, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Front capacity must be positive.");
        Objects.requireNonNull(frontId, "frontId");
        Objects.requireNonNull(worker, "worker");
        FrontState<W> state = fronts.computeIfAbsent(frontId, ignored -> new FrontState<>());
        if (state.workers.contains(worker)) return true;
        if (state.workers.size() >= capacity) return false;
        state.workers.add(worker);
        return true;
    }

    public synchronized BlockPosition claimNext(
        UUID frontId,
        W worker,
        List<BlockPosition> candidates,
        Predicate<BlockPosition> available
    ) {
        return claimNext(frontId, worker, NORMAL_TUNNEL_FRONT_CAPACITY, candidates, available);
    }

    public synchronized BlockPosition claimNext(
        UUID frontId,
        W worker,
        int capacity,
        List<BlockPosition> candidates,
        Predicate<BlockPosition> available
    ) {
        Objects.requireNonNull(frontId, "frontId");
        Objects.requireNonNull(worker, "worker");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(available, "available");

        FrontState<W> state = fronts.computeIfAbsent(frontId, ignored -> new FrontState<>());
        if (!state.workers.contains(worker) && !tryJoin(frontId, worker, capacity)) return null;

        BlockPosition existing = state.workerClaims.get(worker);
        if (existing != null) return existing;

        for (BlockPosition candidate : candidates) {
            if (state.blockOwners.containsKey(candidate) || !available.test(candidate)) continue;
            state.workerClaims.put(worker, candidate);
            state.blockOwners.put(candidate, worker);
            return candidate;
        }
        return null;
    }

    public synchronized void completeClaim(UUID frontId, W worker, BlockPosition block) {
        FrontState<W> state = fronts.get(frontId);
        if (state == null) return;
        BlockPosition claimed = state.workerClaims.get(worker);
        if (claimed == null || !claimed.equals(block)) return;
        state.workerClaims.remove(worker);
        state.blockOwners.remove(block, worker);
        cleanup(frontId, state);
    }

    public synchronized void releaseWorker(W worker) {
        if (worker == null) return;
        List<UUID> emptyFronts = new ArrayList<>();
        for (Map.Entry<UUID, FrontState<W>> entry : fronts.entrySet()) {
            FrontState<W> state = entry.getValue();
            BlockPosition claim = state.workerClaims.remove(worker);
            if (claim != null) state.blockOwners.remove(claim, worker);
            state.workers.remove(worker);
            if (state.workers.isEmpty() && state.workerClaims.isEmpty()) emptyFronts.add(entry.getKey());
        }
        for (UUID frontId : emptyFronts) fronts.remove(frontId);
    }

    /** Releases all transient capacity and block ownership after one front work unit completes. */
    public synchronized void releaseFront(UUID frontId) {
        if (frontId != null) fronts.remove(frontId);
    }

    /** A member remains eligible to claim another block at a full front. */
    public synchronized boolean containsWorker(UUID frontId, W worker) {
        FrontState<W> state = fronts.get(frontId);
        return state != null && state.workers.contains(worker);
    }

    public synchronized int workerCount(UUID frontId) {
        FrontState<W> state = fronts.get(frontId);
        return state == null ? 0 : state.workers.size();
    }

    public synchronized BlockPosition claimOf(UUID frontId, W worker) {
        FrontState<W> state = fronts.get(frontId);
        return state == null ? null : state.workerClaims.get(worker);
    }

    private void cleanup(UUID frontId, FrontState<W> state) {
        if (state.workers.isEmpty() && state.workerClaims.isEmpty()) fronts.remove(frontId);
    }

    private static final class FrontState<W> {
        private final Set<W> workers = new HashSet<>();
        private final Map<W, BlockPosition> workerClaims = new HashMap<>();
        private final Map<BlockPosition, W> blockOwners = new HashMap<>();
    }
}
