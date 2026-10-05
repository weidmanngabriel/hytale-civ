package dev.civilizations.core;

/** Continuous centerline sample produced by Layer 2 planning. */
public record MinePathPoint(
    int index,
    double x,
    double y,
    double z,
    double width,
    double height,
    MineHeading heading,
    int formPhaseIndex
) {
    public MinePathPoint {
        if (index < 0 || formPhaseIndex < 0) {
            throw new IllegalArgumentException("Mine path indices must be non-negative.");
        }
        if (width <= 0.0 || height <= 0.0) {
            throw new IllegalArgumentException("Mine path dimensions must be positive.");
        }
        if (heading == null) {
            throw new IllegalArgumentException("Mine path heading must not be null.");
        }
    }
}
