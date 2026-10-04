package dev.civilizations.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent logical section of a mine tunnel.
 *
 * <p>{@code start} is the lower-left block of the first 4x4 cutting face when looking
 * in {@code direction}. The segment always grows horizontally and stays on one Y level.</p>
 */
public record MineSegment(
    UUID id,
    UUID mineId,
    UUID parentId,
    BlockPosition start,
    MineDirection direction,
    int lengthBlocks,
    Status status,
    int nextBlockIndex,
    int supportsPlaced
) {
    public MineSegment {
        if (id == null || mineId == null || start == null || direction == null || status == null) {
            throw new IllegalArgumentException("Mine segment fields must not be null.");
        }
        MineTuning.requireValidSegmentLength(lengthBlocks);
        if (nextBlockIndex < 0 || nextBlockIndex > blockCount(lengthBlocks)) {
            throw new IllegalArgumentException("Invalid mine segment progress.");
        }
        if (supportsPlaced < 0) {
            throw new IllegalArgumentException("Invalid support count.");
        }
    }

    /**
     * Compatibility factory for deterministic legacy fixtures. Production tunnel planning must
     * pass an explicit length.
     */
    public static MineSegment reserved(
        UUID id,
        UUID mineId,
        UUID parentId,
        BlockPosition start,
        MineDirection direction
    ) {
        return reserved(
            id,
            mineId,
            parentId,
            start,
            direction,
            MineTuning.REFERENCE_SEGMENT_LENGTH_BLOCKS
        );
    }

    public static MineSegment reserved(
        UUID id,
        UUID mineId,
        UUID parentId,
        BlockPosition start,
        MineDirection direction,
        int lengthBlocks
    ) {
        return new MineSegment(
            id,
            mineId,
            parentId,
            start,
            direction,
            lengthBlocks,
            Status.RESERVED,
            0,
            0
        );
    }

    public MineSegment withStatus(Status nextStatus) {
        return new MineSegment(
            id, mineId, parentId, start, direction, lengthBlocks, nextStatus, nextBlockIndex, supportsPlaced
        );
    }

    public MineSegment withProgress(int nextIndex) {
        return new MineSegment(
            id, mineId, parentId, start, direction, lengthBlocks, status, nextIndex, supportsPlaced
        );
    }

    public MineSegment withSupportsPlaced(int count) {
        return new MineSegment(
            id, mineId, parentId, start, direction, lengthBlocks, status, nextBlockIndex, count
        );
    }

    public int blockCount() {
        return blockCount(lengthBlocks);
    }

    public boolean complete() {
        return nextBlockIndex >= blockCount();
    }

    /** Blocks ordered one full 4x4 face at a time from the entrance towards the tunnel end. */
    public List<BlockPosition> blocks() {
        List<BlockPosition> result = new ArrayList<>(blockCount());
        for (int depth = 0; depth < lengthBlocks; depth++) {
            for (int y = 0; y < MineTuning.TUNNEL_HEIGHT_BLOCKS; y++) {
                for (int width = 0; width < MineTuning.TUNNEL_WIDTH_BLOCKS; width++) {
                    result.add(blockAt(depth, width, y));
                }
            }
        }
        return List.copyOf(result);
    }

    public BlockPosition blockAtIndex(int index) {
        if (index < 0 || index >= blockCount()) {
            throw new IndexOutOfBoundsException(index);
        }
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int depth = index / faceSize;
        int inFace = index % faceSize;
        int y = inFace / MineTuning.TUNNEL_WIDTH_BLOCKS;
        int width = inFace % MineTuning.TUNNEL_WIDTH_BLOCKS;
        return blockAt(depth, width, y);
    }

    /** Lower block coordinate at the support frame for the given 1-based tunnel depth. */
    public BlockPosition supportOrigin(int depth) {
        if (depth <= 0 || depth > lengthBlocks) {
            throw new IllegalArgumentException("Support depth outside segment.");
        }
        int step = depth - 1;
        return new BlockPosition(
            start.x() + direction.dx() * step,
            start.y(),
            start.z() + direction.dz() * step
        );
    }

    public HorizontalBounds horizontalBounds() {
        return horizontalBounds(0, lengthBlocks);
    }

    /**
     * Starts the next segment using the parent's oriented tunnel basis rather than world-axis
     * min/max bounds. Straight continuations therefore remain exactly in the same 4x4 lane.
     * A 90-degree continuation reuses the parent's final 4x4 area as a walkable junction and
     * exits through the matching left or right edge without any diagonal offset.
     */
    public BlockPosition nextStart(MineDirection nextDirection) {
        if (nextDirection == opposite(direction)) {
            throw new IllegalArgumentException("Mine segments cannot immediately reverse.");
        }

        int forwardX = direction.dx();
        int forwardZ = direction.dz();
        int rightX = -direction.dz();
        int rightZ = direction.dx();

        if (nextDirection == direction) {
            return new BlockPosition(
                start.x() + forwardX * lengthBlocks,
                start.y(),
                start.z() + forwardZ * lengthBlocks
            );
        }

        int junctionDepth = lengthBlocks - MineTuning.TUNNEL_WIDTH_BLOCKS;
        int junctionX = start.x() + forwardX * junctionDepth;
        int junctionZ = start.z() + forwardZ * junctionDepth;
        int lastWidthOffset = MineTuning.TUNNEL_WIDTH_BLOCKS - 1;

        if (nextDirection == direction.left()) {
            return new BlockPosition(
                junctionX + rightX * lastWidthOffset,
                start.y(),
                junctionZ + rightZ * lastWidthOffset
            );
        }
        if (nextDirection == direction.right()) {
            return new BlockPosition(
                junctionX + forwardX * lastWidthOffset,
                start.y(),
                junctionZ + forwardZ * lastWidthOffset
            );
        }

        throw new IllegalArgumentException("Unsupported mine continuation direction.");
    }

    private HorizontalBounds horizontalBounds(int fromDepthInclusive, int toDepthExclusive) {
        List<BlockPosition> floor = new ArrayList<>();
        for (int depth = fromDepthInclusive; depth < toDepthExclusive; depth++) {
            for (int width = 0; width < MineTuning.TUNNEL_WIDTH_BLOCKS; width++) {
                floor.add(blockAt(depth, width, 0));
            }
        }
        int minX = floor.stream().mapToInt(BlockPosition::x).min().orElseThrow();
        int minZ = floor.stream().mapToInt(BlockPosition::z).min().orElseThrow();
        int maxX = floor.stream().mapToInt(BlockPosition::x).max().orElseThrow();
        int maxZ = floor.stream().mapToInt(BlockPosition::z).max().orElseThrow();
        return new HorizontalBounds(minX, minZ, maxX, maxZ);
    }

    private BlockPosition blockAt(int depth, int width, int vertical) {
        int sideX = -direction.dz();
        int sideZ = direction.dx();
        return new BlockPosition(
            start.x() + direction.dx() * depth + sideX * width,
            start.y() + vertical,
            start.z() + direction.dz() * depth + sideZ * width
        );
    }

    private static int blockCount(int lengthBlocks) {
        return MineTuning.blocksPerSegment(lengthBlocks);
    }

    private static MineDirection opposite(MineDirection direction) {
        return switch (direction) {
            case NORTH -> MineDirection.SOUTH;
            case SOUTH -> MineDirection.NORTH;
            case EAST -> MineDirection.WEST;
            case WEST -> MineDirection.EAST;
        };
    }

    public enum Status {
        RESERVED,
        MINING,
        COMPLETE,
        BLOCKED
    }

    public record HorizontalBounds(int minX, int minZ, int maxX, int maxZ) {
    }
}
