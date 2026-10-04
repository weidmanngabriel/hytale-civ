package dev.civilizations.core;

import java.util.Objects;

/**
 * Hytale-independent state machine for excavating one mine segment.
 *
 * <p>The job owns the Civ work order only: move to the current 4x4 face, request one block
 * excavation at a time, request scheduled support placement, and finish the segment. It does not
 * perform pathfinding, world access, harvesting, prefab placement, or timing. Adapters execute an
 * intent and report the result back.</p>
 */
public final class MinerJob {

    private static final int FACE_SIZE =
        MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;

    private MineSegment segment;
    private WorkState state;
    private int pendingSupportDepth;

    public MinerJob(MineSegment segment) {
        this.segment = Objects.requireNonNull(segment, "segment");
        if (segment.status() == MineSegment.Status.BLOCKED) {
            throw new IllegalArgumentException("Cannot start a blocked mine segment.");
        }
        if (segment.complete() || segment.status() == MineSegment.Status.COMPLETE) {
            this.segment = segment.withProgress(MineTuning.blocksPerSegment())
                .withStatus(MineSegment.Status.COMPLETE);
            state = WorkState.COMPLETE;
        } else {
            this.segment = segment.withStatus(MineSegment.Status.MINING);
            state = WorkState.MOVING_TO_FACE;
        }
    }

    public synchronized Intent intent() {
        return switch (state) {
            case MOVING_TO_FACE -> new MoveToFaceIntent(segment, currentDepth());
            case BREAKING_BLOCK -> new BreakBlockIntent(
                segment.blockAtIndex(segment.nextBlockIndex()),
                segment.nextBlockIndex()
            );
            case PLACING_SUPPORT -> new PlaceSupportIntent(segment, pendingSupportDepth);
            case COMPLETE -> new SegmentCompleteIntent(segment);
        };
    }

    public synchronized boolean movementArrived() {
        if (state != WorkState.MOVING_TO_FACE) return false;
        state = WorkState.BREAKING_BLOCK;
        return true;
    }

    public synchronized boolean blockBroken() {
        if (state != WorkState.BREAKING_BLOCK) return false;

        int nextIndex = segment.nextBlockIndex() + 1;
        segment = segment.withProgress(nextIndex);

        if (nextIndex % FACE_SIZE != 0) {
            return true;
        }

        int completedDepth = nextIndex / FACE_SIZE;
        if (completedDepth % MineTuning.SUPPORT_SPACING_BLOCKS == 0) {
            int expectedSupports = completedDepth / MineTuning.SUPPORT_SPACING_BLOCKS;
            if (segment.supportsPlaced() < expectedSupports) {
                pendingSupportDepth = completedDepth;
                state = WorkState.PLACING_SUPPORT;
                return true;
            }
        }

        advanceAfterCompletedFace();
        return true;
    }

    public synchronized boolean supportPlaced() {
        if (state != WorkState.PLACING_SUPPORT) return false;

        int supportNumber = pendingSupportDepth / MineTuning.SUPPORT_SPACING_BLOCKS;
        segment = segment.withSupportsPlaced(Math.max(segment.supportsPlaced(), supportNumber));
        pendingSupportDepth = 0;
        advanceAfterCompletedFace();
        return true;
    }

    public synchronized MineSegment segment() {
        return segment;
    }

    public synchronized WorkState state() {
        return state;
    }

    public synchronized int currentDepth() {
        if (segment.complete()) return MineTuning.SEGMENT_LENGTH_BLOCKS;
        return segment.nextBlockIndex() / FACE_SIZE;
    }

    private void advanceAfterCompletedFace() {
        if (segment.complete()) {
            segment = segment.withStatus(MineSegment.Status.COMPLETE);
            state = WorkState.COMPLETE;
        } else {
            state = WorkState.MOVING_TO_FACE;
        }
    }

    public sealed interface Intent
        permits MoveToFaceIntent, BreakBlockIntent, PlaceSupportIntent, SegmentCompleteIntent {
    }

    /** Zero-based tunnel depth of the 4x4 face the worker should approach. */
    public record MoveToFaceIntent(MineSegment segment, int depth) implements Intent {
        public MoveToFaceIntent {
            Objects.requireNonNull(segment, "segment");
            if (depth < 0 || depth >= MineTuning.SEGMENT_LENGTH_BLOCKS) {
                throw new IllegalArgumentException("Face depth outside segment.");
            }
        }
    }

    public record BreakBlockIntent(BlockPosition block, int blockIndex) implements Intent {
        public BreakBlockIntent {
            Objects.requireNonNull(block, "block");
            if (blockIndex < 0 || blockIndex >= MineTuning.blocksPerSegment()) {
                throw new IllegalArgumentException("Block index outside segment.");
            }
        }
    }

    /** One-based tunnel depth where the semantic support frame is due. */
    public record PlaceSupportIntent(MineSegment segment, int depth) implements Intent {
        public PlaceSupportIntent {
            Objects.requireNonNull(segment, "segment");
            if (depth <= 0 || depth > MineTuning.SEGMENT_LENGTH_BLOCKS) {
                throw new IllegalArgumentException("Support depth outside segment.");
            }
        }
    }

    public record SegmentCompleteIntent(MineSegment segment) implements Intent {
        public SegmentCompleteIntent {
            Objects.requireNonNull(segment, "segment");
            if (!segment.complete() || segment.status() != MineSegment.Status.COMPLETE) {
                throw new IllegalArgumentException("Segment is not complete.");
            }
        }
    }

    public enum WorkState {
        MOVING_TO_FACE,
        BREAKING_BLOCK,
        PLACING_SUPPORT,
        COMPLETE
    }
}
