package dev.civilizations.core;

/**
 * Central balance values for the mine-worker slice.
 *
 * <p>Keep gameplay tuning here so iteration never requires changing the work-system flow.</p>
 */
public final class MineTuning {

    public static final int TUNNEL_WIDTH_BLOCKS = 4;
    public static final int TUNNEL_HEIGHT_BLOCKS = 4;
    public static final int MIN_SEGMENT_LENGTH_BLOCKS = 4;
    public static final int MAX_SEGMENT_LENGTH_BLOCKS = 12;
    public static final int REFERENCE_SEGMENT_LENGTH_BLOCKS = 8;
    public static final int SUPPORT_SPACING_BLOCKS = 4;
    public static final double REFERENCE_SEGMENT_TARGET_SECONDS = 60.0;
    public static final int STRAIGHT_WEIGHT = 60;
    public static final int LEFT_WEIGHT = 20;
    public static final int RIGHT_WEIGHT = 20;

    private MineTuning() {
    }

    public static void requireValidSegmentLength(int lengthBlocks) {
        if (lengthBlocks < MIN_SEGMENT_LENGTH_BLOCKS || lengthBlocks > MAX_SEGMENT_LENGTH_BLOCKS) {
            throw new IllegalArgumentException(
                "Mine segment length must be between " + MIN_SEGMENT_LENGTH_BLOCKS
                    + " and " + MAX_SEGMENT_LENGTH_BLOCKS + " blocks."
            );
        }
    }

    public static int blocksPerSegment(int lengthBlocks) {
        requireValidSegmentLength(lengthBlocks);
        return TUNNEL_WIDTH_BLOCKS * TUNNEL_HEIGHT_BLOCKS * lengthBlocks;
    }

    /**
     * Mining keeps the established phase-one per-block speed. Variable segment length therefore
     * changes total segment duration instead of making long segments mine faster than short ones.
     */
    public static double secondsPerBlock() {
        return REFERENCE_SEGMENT_TARGET_SECONDS
            / blocksPerSegment(REFERENCE_SEGMENT_LENGTH_BLOCKS);
    }

    /** Number of regular support frames that fit before a segment junction. */
    public static int supportFramesForLength(int lengthBlocks) {
        requireValidSegmentLength(lengthBlocks);
        return Math.max(0, (lengthBlocks - 1) / SUPPORT_SPACING_BLOCKS);
    }
}
