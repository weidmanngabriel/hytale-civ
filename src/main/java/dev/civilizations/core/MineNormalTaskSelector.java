package dev.civilizations.core;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Shared selection rule for normal mine work that currently includes tunnel fronts and rooms. */
public final class MineNormalTaskSelector {

    private MineNormalTaskSelector() {
    }

    public static Candidate select(List<Candidate> candidates, BlockPosition workerPosition) {
        if (candidates == null || workerPosition == null) {
            throw new IllegalArgumentException("Mine normal task selection inputs must not be null.");
        }
        List<Candidate> available = candidates.stream()
            .filter(candidate -> candidate.workerCount() < candidate.capacity())
            .toList();
        if (available.isEmpty()) return null;

        List<Candidate> active = available.stream().filter(candidate -> candidate.workerCount() > 0).toList();
        List<Candidate> pool = active.isEmpty() ? available : active;

        return pool.stream()
            .min(Comparator
                .comparingInt((Candidate candidate) -> -candidate.priority())
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

    public enum Kind {
        TUNNEL_FRONT,
        ROOM
    }
}
