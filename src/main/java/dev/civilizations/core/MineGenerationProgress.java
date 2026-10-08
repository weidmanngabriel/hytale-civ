package dev.civilizations.core;

/** Compact persisted planning state; never contains block lists. */
public record MineGenerationProgress(
    int unlockedSlices,
    long nextRefreshAtMillis,
    MineHeading heading,
    long seed
) {
    public MineGenerationProgress {
        if (unlockedSlices < 1 || nextRefreshAtMillis < 0 || heading == null) {
            throw new IllegalArgumentException("Invalid mine planning progress.");
        }
    }

    public MineGenerationProgress advance(int nextSlices, long nextAtMillis) {
        if (nextSlices < unlockedSlices) throw new IllegalArgumentException("Planning must not regress.");
        return new MineGenerationProgress(nextSlices, nextAtMillis, heading, seed);
    }
}
