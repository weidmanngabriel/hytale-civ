package dev.civilizations.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Predicate;

/** Transient coordination for miners sharing one room excavation/build task. */
public final class MineRoomCoordinator<W> {

    private final Map<UUID, RoomState<W>> rooms = new HashMap<>();

    public synchronized boolean tryJoin(UUID roomId, W worker, int capacity) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(worker, "worker");
        if (capacity <= 0) throw new IllegalArgumentException("Room capacity must be positive.");
        RoomState<W> state = rooms.computeIfAbsent(roomId, ignored -> new RoomState<>());
        if (state.workers.contains(worker)) return true;
        if (state.workers.size() >= capacity) return false;
        state.workers.add(worker);
        return true;
    }

    public synchronized BlockPosition claimNextBlock(
        UUID roomId,
        W worker,
        int capacity,
        List<BlockPosition> candidates,
        Predicate<BlockPosition> available
    ) {
        if (!tryJoin(roomId, worker, capacity)) return null;
        RoomState<W> state = rooms.get(roomId);
        BlockPosition existing = state.workerBlockClaims.get(worker);
        if (existing != null) return existing;
        for (BlockPosition candidate : candidates) {
            if (state.blockOwners.containsKey(candidate) || !available.test(candidate)) continue;
            state.workerBlockClaims.put(worker, candidate);
            state.blockOwners.put(candidate, worker);
            return candidate;
        }
        return null;
    }

    public synchronized Integer claimNextBuildSection(
        UUID roomId,
        W worker,
        int capacity,
        int sectionCount,
        Set<Integer> completedSections
    ) {
        if (!tryJoin(roomId, worker, capacity)) return null;
        RoomState<W> state = rooms.get(roomId);
        Integer existing = state.workerSectionClaims.get(worker);
        if (existing != null) return existing;
        for (int index = 0; index < sectionCount; index++) {
            if (completedSections.contains(index) || state.sectionOwners.containsKey(index)) continue;
            state.workerSectionClaims.put(worker, index);
            state.sectionOwners.put(index, worker);
            return index;
        }
        return null;
    }

    public synchronized void completeBlock(UUID roomId, W worker, BlockPosition block) {
        RoomState<W> state = rooms.get(roomId);
        if (state == null) return;
        if (block.equals(state.workerBlockClaims.get(worker))) {
            state.workerBlockClaims.remove(worker);
            state.blockOwners.remove(block, worker);
        }
        cleanup(roomId, state);
    }

    public synchronized void completeBuildSection(UUID roomId, W worker, int section) {
        RoomState<W> state = rooms.get(roomId);
        if (state == null) return;
        if (Integer.valueOf(section).equals(state.workerSectionClaims.get(worker))) {
            state.workerSectionClaims.remove(worker);
            state.sectionOwners.remove(section, worker);
        }
        cleanup(roomId, state);
    }

    public synchronized int workerCount(UUID roomId) {
        RoomState<W> state = rooms.get(roomId);
        return state == null ? 0 : state.workers.size();
    }

    public synchronized void releaseWorker(W worker) {
        if (worker == null) return;
        List<UUID> empty = new ArrayList<>();
        for (Map.Entry<UUID, RoomState<W>> entry : rooms.entrySet()) {
            RoomState<W> state = entry.getValue();
            BlockPosition block = state.workerBlockClaims.remove(worker);
            if (block != null) state.blockOwners.remove(block, worker);
            Integer section = state.workerSectionClaims.remove(worker);
            if (section != null) state.sectionOwners.remove(section, worker);
            state.workers.remove(worker);
            if (state.workers.isEmpty() && state.workerBlockClaims.isEmpty()
                && state.workerSectionClaims.isEmpty()) empty.add(entry.getKey());
        }
        for (UUID roomId : empty) rooms.remove(roomId);
    }

    public synchronized void releaseRoom(UUID roomId) {
        if (roomId != null) rooms.remove(roomId);
    }

    private void cleanup(UUID roomId, RoomState<W> state) {
        if (state.workers.isEmpty() && state.workerBlockClaims.isEmpty()
            && state.workerSectionClaims.isEmpty()) rooms.remove(roomId);
    }

    private static final class RoomState<W> {
        private final Set<W> workers = new HashSet<>();
        private final Map<W, BlockPosition> workerBlockClaims = new HashMap<>();
        private final Map<BlockPosition, W> blockOwners = new HashMap<>();
        private final Map<W, Integer> workerSectionClaims = new HashMap<>();
        private final Map<Integer, W> sectionOwners = new HashMap<>();
    }
}
