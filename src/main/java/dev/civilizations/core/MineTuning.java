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
    public static final int JUNCTION_LENGTH_BLOCKS = 4;
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

    /** Main supported tunnel length plus the always-reserved, support-free junction. */
    public static int totalDepthBlocks(int lengthBlocks) {
        requireValidSegmentLength(lengthBlocks);
        return lengthBlocks + JUNCTION_LENGTH_BLOCKS;
    }

    /** Number of excavation blocks in main tunnel plus its fixed 4x4x4 junction. */
    public static int blocksPerSegment(int lengthBlocks) {
        return TUNNEL_WIDTH_BLOCKS * TUNNEL_HEIGHT_BLOCKS * totalDepthBlocks(lengthBlocks);
    }

    /** Number of excavation blocks in the supported main tunnel only. */
    public static int mainTunnelBlocks(int lengthBlocks) {
        requireValidSegmentLength(lengthBlocks);
        return TUNNEL_WIDTH_BLOCKS * TUNNEL_HEIGHT_BLOCKS * lengthBlocks;
    }

    /**
     * Compatibility helper for deterministic fixtures. Production segment planning must
     * always use the explicit segment length instead of this 8-block reference overload.
     */
    public static int blocksPerSegment() {
        return blocksPerSegment(REFERENCE_SEGMENT_LENGTH_BLOCKS);
    }

    /**
     * Mining keeps the established phase-one per-block speed from the former 4x4x8 tunnel.
     * The extra junction therefore adds real excavation time instead of making blocks faster.
     */
    public static double secondsPerBlock() {
        return REFERENCE_SEGMENT_TARGET_SECONDS
            / (TUNNEL_WIDTH_BLOCKS * TUNNEL_HEIGHT_BLOCKS * REFERENCE_SEGMENT_LENGTH_BLOCKS);
    }

    /** One regular support frame every four blocks of main tunnel, including its last face. */
    public static int supportFramesForLength(int lengthBlocks) {
        requireValidSegmentLength(lengthBlocks);
        return lengthBlocks / SUPPORT_SPACING_BLOCKS;
    }
}
