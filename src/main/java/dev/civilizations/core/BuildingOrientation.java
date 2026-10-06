package dev.civilizations.core;

/**
 * Hytale-independent cardinal orientation for placed Civ prefabs.
 *
 * <p>{@link #NORTH} means the authored prefab orientation (no rotation). The other values rotate
 * the authored local X/Z plane clockwise so an authored north-facing direction points toward the
 * named world direction.</p>
 */
public enum BuildingOrientation {
    NORTH,
    EAST,
    SOUTH,
    WEST;

    /** Rotates an authored local block position around the prefab anchor. */
    public BlockPosition rotateAround(BlockPosition position, int anchorX, int anchorZ) {
        int localX = position.x() - anchorX;
        int localZ = position.z() - anchorZ;
        int rotatedX;
        int rotatedZ;
        switch (this) {
            case NORTH -> {
                rotatedX = localX;
                rotatedZ = localZ;
            }
            case EAST -> {
                rotatedX = -localZ;
                rotatedZ = localX;
            }
            case SOUTH -> {
                rotatedX = -localX;
                rotatedZ = -localZ;
            }
            case WEST -> {
                rotatedX = localZ;
                rotatedZ = -localX;
            }
            default -> throw new IllegalStateException("Unhandled orientation " + this);
        }
        return new BlockPosition(anchorX + rotatedX, position.y(), anchorZ + rotatedZ);
    }

    /** Rotates an eight-way mine heading by the same authored-to-world transform. */
    public MineHeading rotate(MineHeading heading) {
        MineHeading result = heading;
        for (int i = 0; i < clockwiseQuarterTurns(); i++) {
            result = result.right45().right45();
        }
        return result;
    }

    /** Degrees used by Hytale's BlockSelection.rotate(Axis.Y, degrees, ...). */
    public int clockwiseDegrees() {
        return clockwiseQuarterTurns() * 90;
    }

    private int clockwiseQuarterTurns() {
        return switch (this) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
        };
    }
}
