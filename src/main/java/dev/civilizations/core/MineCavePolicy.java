package dev.civilizations.core;

/** Tunable semantic classification for locally observed natural cave space. */
public final class MineCavePolicy {

    public static final int SCAN_HORIZONTAL_RADIUS = 12;
    public static final int SCAN_VERTICAL_RADIUS = 8;
    public static final int MAX_SCANNED_EMPTY_BLOCKS = 2048;

    public static final int LARGE_MIN_EMPTY_BLOCKS = 160;
    public static final int LARGE_MIN_USABLE_FLOOR_BLOCKS = 24;
    public static final int LARGE_MIN_HORIZONTAL_SPAN = 8;
    public static final int LARGE_MIN_HEIGHT = 4;

    public static final int NATURAL_CHAMBER_DEDUP_RADIUS = 18;

    private MineCavePolicy() {
    }

    public static MineCaveObservation.Status classify(
        int emptyBlocks,
        int usableFloorBlocks,
        int spanX,
        int spanY,
        int spanZ,
        boolean complete
    ) {
        if (emptyBlocks <= 0) return MineCaveObservation.Status.NONE;

        int horizontalSpan = Math.max(spanX, spanZ);
        boolean large = emptyBlocks >= LARGE_MIN_EMPTY_BLOCKS
            && usableFloorBlocks >= LARGE_MIN_USABLE_FLOOR_BLOCKS
            && horizontalSpan >= LARGE_MIN_HORIZONTAL_SPAN
            && spanY >= LARGE_MIN_HEIGHT;

        if (large) return MineCaveObservation.Status.LARGE;
        return complete ? MineCaveObservation.Status.SMALL : MineCaveObservation.Status.INCOMPLETE;
    }

    public static boolean sameNaturalChamber(BlockPosition a, BlockPosition b) {
        long dx = (long) a.x() - b.x();
        long dy = (long) a.y() - b.y();
        long dz = (long) a.z() - b.z();
        long radius = NATURAL_CHAMBER_DEDUP_RADIUS;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
