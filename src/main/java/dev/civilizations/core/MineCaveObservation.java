package dev.civilizations.core;

/**
 * Hytale-independent summary of one locally observed natural opening next to a planned tunnel.
 * It is evidence for cave classification, not a navigation graph.
 */
public record MineCaveObservation(
    Status status,
    BlockPosition center,
    BlockPosition min,
    BlockPosition max,
    int emptyBlocks,
    int usableFloorBlocks,
    boolean hasFluid,
    boolean hasLava
) {
    public MineCaveObservation {
        if (status == null || center == null || min == null || max == null) {
            throw new IllegalArgumentException("Mine cave observation fields must not be null.");
        }
        if (emptyBlocks < 0 || usableFloorBlocks < 0) {
            throw new IllegalArgumentException("Mine cave counts must be non-negative.");
        }
    }

    public int spanX() {
        return max.x() - min.x() + 1;
    }

    public int spanY() {
        return max.y() - min.y() + 1;
    }

    public int spanZ() {
        return max.z() - min.z() + 1;
    }

    public int horizontalSpan() {
        return Math.max(spanX(), spanZ());
    }

    public enum Status {
        NONE,
        INCOMPLETE,
        SMALL,
        LARGE
    }
}
