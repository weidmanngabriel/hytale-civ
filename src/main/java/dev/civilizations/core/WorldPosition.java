package dev.civilizations.core;

/**
 * Engine-neutral world position used by Core movement intents.
 */
public record WorldPosition(double x, double y, double z) {

    public WorldPosition {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("world position coordinates must be finite");
        }
    }
}
