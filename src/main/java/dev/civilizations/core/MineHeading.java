package dev.civilizations.core;

/** Eight-way horizontal planning heading for future mine centerlines. */
public enum MineHeading {
    NORTH(-90.0),
    NORTH_EAST(-45.0),
    EAST(0.0),
    SOUTH_EAST(45.0),
    SOUTH(90.0),
    SOUTH_WEST(135.0),
    WEST(180.0),
    NORTH_WEST(225.0);

    private final double angleDegrees;

    MineHeading(double angleDegrees) {
        this.angleDegrees = angleDegrees;
    }

    public double angleDegrees() {
        return angleDegrees;
    }

    public double unitX() {
        return Math.cos(Math.toRadians(angleDegrees));
    }

    public double unitZ() {
        return Math.sin(Math.toRadians(angleDegrees));
    }

    public MineHeading left45() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    public MineHeading right45() {
        return values()[(ordinal() + 1) % values().length];
    }

    public MineHeading opposite() {
        return values()[(ordinal() + 4) % values().length];
    }

    public static double shortestSignedAngleDegrees(MineHeading from, MineHeading to) {
        double delta = to.angleDegrees - from.angleDegrees;
        while (delta <= -180.0) delta += 360.0;
        while (delta > 180.0) delta -= 360.0;
        return delta;
    }
}
