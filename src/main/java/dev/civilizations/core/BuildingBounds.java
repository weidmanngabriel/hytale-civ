package dev.civilizations.core;

/**
 * Axis-aligned world-space bounds authored for a Civ building.
 *
 * <p>The Hytale adapter derives these coordinates from the native trigger volume
 * tagged as {@code civ.type=building_bounds}. Core only owns the spatial meaning.
 */
public record BuildingBounds(
    double minX,
    double minY,
    double minZ,
    double maxX,
    double maxY,
    double maxZ
) {
    public BuildingBounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Building bounds min must not exceed max.");
        }
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public boolean containsBlock(BlockPosition block) {
        if (block == null) {
            return false;
        }
        return contains(block.x() + 0.5, block.y() + 0.5, block.z() + 0.5);
    }

    public boolean overlapsHorizontal(
        double otherMinX,
        double otherMinZ,
        double otherMaxX,
        double otherMaxZ
    ) {
        return minX < otherMaxX
            && maxX > otherMinX
            && minZ < otherMaxZ
            && maxZ > otherMinZ;
    }
}
