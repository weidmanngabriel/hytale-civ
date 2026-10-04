package dev.civilizations.core;

/**
 * Central balance values for the first mine-worker slice.
 *
 * <p>Keep gameplay tuning here so iteration never requires changing the work-system flow.</p>
 */
public final class MineTuning {

    public static final int TUNNEL_WIDTH_BLOCKS = 4;
    public static final int TUNNEL_HEIGHT_BLOCKS = 4;
    public static final int SEGMENT_LENGTH_BLOCKS = 8;
    public static final int SUPPORT_SPACING_BLOCKS = 4;
    public static final double SEGMENT_TARGET_SECONDS = 60.0;
    public static final int STRAIGHT_WEIGHT = 60;
    public static final int LEFT_WEIGHT = 20;
    public static final int RIGHT_WEIGHT = 20;

    private MineTuning() {
    }

    public static int blocksPerSegment() {
        return TUNNEL_WIDTH_BLOCKS * TUNNEL_HEIGHT_BLOCKS * SEGMENT_LENGTH_BLOCKS;
    }

    public static double secondsPerBlock() {
        return SEGMENT_TARGET_SECONDS / blocksPerSegment();
    }

    /** Number of regular support frames that fit before the segment junction. */
    public static int supportFramesPerSegment() {
        return Math.max(0, (SEGMENT_LENGTH_BLOCKS - 1) / SUPPORT_SPACING_BLOCKS);
    }
}
