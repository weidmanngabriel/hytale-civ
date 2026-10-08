package dev.civilizations.core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared selection and aging rule for all normal mine work. */
public final class MineNormalTaskSelector {

    public static final int MAX_NORMAL_PRIORITY = 9;

    private MineNormalTaskSelector() {
    }

    /** True when executable mine work exists but every task is at worker capacity. */
    public static boolean allWorkAtCapacity(List<Candidate> candidates) {
        if (candidates == null) throw new IllegalArgumentException("Mine candidates must not be null.");
        return !candidates.isEmpty() && candidates.stream()
            .allMatch(candidate -> candidate.workerCount() >= candidate.capacity());
    }

    public static Candidate select(List<Candidate> candidates, BlockPosition workerPosition) {
        return selectWithAging(candidates, workerPosition, Map.of()).selected();
    }

    public static Selection selectWithAging(
        List<Candidate> candidates,
        BlockPosition workerPosition,
        Map<UUID, Integer> priorityBonuses
    ) {
        if (candidates == null || workerPosition == null || priorityBonuses == null) {
            throw new IllegalArgumentException("Mine normal task selection inputs must not be null.");
        }

        List<Candidate> available = candidates.stream()
            .filter(candidate -> candidate.workerCount() < candidate.capacity())
            .toList();
        if (available.isEmpty()) {
            return new Selection(null, Map.copyOf(priorityBonuses), false);
        }

        List<Candidate> active = available.stream()
            .filter(candidate -> candidate.workerCount() > 0)
            .toList();
        if (!active.isEmpty()) {
            Candidate selected = choose(active, workerPosition, priorityBonuses);
            return new Selection(selected, Map.copyOf(priorityBonuses), false);
        }

        Candidate selected = choose(available, workerPosition, priorityBonuses);
        LinkedHashMap<UUID, Integer> updated = new LinkedHashMap<>(priorityBonuses);
        for (Candidate candidate : available) {
            if (selected != null && candidate.id().equals(selected.id())) continue;
            int currentBonus = updated.getOrDefault(candidate.id(), 0);
            int maxBonus = Math.max(0, MAX_NORMAL_PRIORITY - candidate.priority());
            if (currentBonus < maxBonus) updated.put(candidate.id(), currentBonus + 1);
        }
        return new Selection(selected, Map.copyOf(updated), true);
    }

    public static int effectivePriority(Candidate candidate, Map<UUID, Integer> priorityBonuses) {
        if (candidate == null || priorityBonuses == null) {
            throw new IllegalArgumentException("Mine priority inputs must not be null.");
        }
        return Math.min(
            MAX_NORMAL_PRIORITY,
            candidate.priority() + Math.max(0, priorityBonuses.getOrDefault(candidate.id(), 0))
        );
    }

    private static Candidate choose(
        List<Candidate> candidates,
        BlockPosition workerPosition,
        Map<UUID, Integer> priorityBonuses
    ) {
        return candidates.stream()
            .min(Comparator
                .comparingInt((Candidate candidate) -> -effectivePriority(candidate, priorityBonuses))
                .thenComparingDouble(candidate -> distanceSquared(workerPosition, candidate.position()))
                .thenComparing(candidate -> candidate.id().toString()))
            .orElse(null);
    }

    private static double distanceSquared(BlockPosition a, BlockPosition b) {
        long dx = (long) a.x() - b.x();
        long dy = (long) a.y() - b.y();
        long dz = (long) a.z() - b.z();
        return (double) dx * dx + (double) dy * dy + (double) dz * dz;
    }

    public record Candidate(
        UUID id,
        Kind kind,
        int priority,
        int workerCount,
        int capacity,
        BlockPosition position
    ) {
        public Candidate {
            if (id == null || kind == null || position == null || priority < 1 || priority > 9
                || workerCount < 0 || capacity <= 0) {
                throw new IllegalArgumentException("Mine normal task candidate is invalid.");
            }
        }
    }

    public record Selection(
        Candidate selected,
        Map<UUID, Integer> updatedPriorityBonuses,
        boolean openedWaitingWork
    ) {
        public Selection {
            if (updatedPriorityBonuses == null) {
                throw new IllegalArgumentException("Mine aging state must not be null.");
            }
            updatedPriorityBonuses = Map.copyOf(updatedPriorityBonuses);
        }
    }

    public enum Kind {
        TUNNEL_FRONT,
        ROOM,
        INFRASTRUCTURE
    }
}
