package dev.civilizations.core;

/** Cardinal tunnel directions on the horizontal plane. */
public enum MineDirection {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    private final int dx;
    private final int dz;

    MineDirection(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    public MineDirection left() {
        return values()[(ordinal() + 3) % 4];
    }

    public MineDirection right() {
        return values()[(ordinal() + 1) % 4];
    }
}
