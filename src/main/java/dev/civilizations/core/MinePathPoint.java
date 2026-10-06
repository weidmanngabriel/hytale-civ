package dev.civilizations.core;

/** Continuous centerline sample produced by Layer 2 planning. */
public record MinePathPoint(
    int index,
    double x,
    double y,
    double z,
    double width,
    double height,
    MineHeading planningHeading,
    double tangentAngleDegrees,
    int formPhaseIndex
) {
    public MinePathPoint {
        if (index < 0 || formPhaseIndex < 0) {
            throw new IllegalArgumentException("Mine path indices must be non-negative.");
        }
        if (width <= 0.0 || height <= 0.0) {
            throw new IllegalArgumentException("Mine path dimensions must be positive.");
        }
        if (planningHeading == null) {
            throw new IllegalArgumentException("Mine path heading must not be null.");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !Double.isFinite(width) || !Double.isFinite(height)
            || !Double.isFinite(tangentAngleDegrees)) {
            throw new IllegalArgumentException("Mine path values must be finite.");
        }
    }
}
