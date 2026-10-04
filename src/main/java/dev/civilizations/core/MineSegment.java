package dev.civilizations.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent logical section of a mine tunnel.
 *
 * <p>{@code start} is the lower-left block of the first 4x4 cutting face when looking
 * in {@code direction}. {@code lengthBlocks} describes only the supported main tunnel.
 * Every segment additionally owns a fixed 4x4x4, support-free junction immediately after
 * the main tunnel. The segment always grows horizontally and stays on one Y level.</p>
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

    public int totalDepthBlocks() {
        return MineTuning.totalDepthBlocks(lengthBlocks);
    }

    public int blockCount() {
        return blockCount(lengthBlocks);
    }

    public boolean complete() {
        return nextBlockIndex >= blockCount();
    }

    public boolean isJunctionIndex(int index) {
        if (index < 0 || index >= blockCount()) throw new IndexOutOfBoundsException(index);
        return index >= MineTuning.mainTunnelBlocks(lengthBlocks);
    }

    /** Blocks ordered one full 4x4 face at a time through main tunnel and then junction. */
    public List<BlockPosition> blocks() {
        List<BlockPosition> result = new ArrayList<>(blockCount());
        for (int depth = 0; depth < totalDepthBlocks(); depth++) {
            for (int y = 0; y < MineTuning.TUNNEL_HEIGHT_BLOCKS; y++) {
                for (int width = 0; width < MineTuning.TUNNEL_WIDTH_BLOCKS; width++) {
                    result.add(blockAt(depth, width, y));
                }
            }
        }
        return List.copyOf(result);
    }

    public List<BlockPosition> junctionBlocks() {
        List<BlockPosition> result = new ArrayList<>(
            MineTuning.TUNNEL_WIDTH_BLOCKS
                * MineTuning.TUNNEL_HEIGHT_BLOCKS
                * MineTuning.JUNCTION_LENGTH_BLOCKS
        );
        for (int depth = lengthBlocks; depth < totalDepthBlocks(); depth++) {
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

    /** Lower block coordinate at the support frame for the given 1-based main-tunnel depth. */
    public BlockPosition supportOrigin(int depth) {
        if (depth <= 0 || depth > lengthBlocks) {
            throw new IllegalArgumentException("Support depth outside main tunnel.");
        }
        int step = depth - 1;
        return new BlockPosition(
            start.x() + direction.dx() * step,
            start.y(),
            start.z() + direction.dz() * step
        );
    }

    public HorizontalBounds horizontalBounds() {
        return horizontalBounds(0, totalDepthBlocks());
    }

    /**
     * Starts a child immediately outside this segment's fully excavated 4x4x4 junction.
     * The parent and child never overlap. Straight, left and right continuations all use the
     * same reserved junction contract, leaving future drift/offsets as a start-position concern.
     */
    public BlockPosition nextStart(MineDirection nextDirection) {
        if (nextDirection == opposite(direction)) {
            throw new IllegalArgumentException("Mine segments cannot immediately reverse.");
        }

        int forwardX = direction.dx();
        int forwardZ = direction.dz();
        int rightX = -direction.dz();
        int rightZ = direction.dx();
        int junctionX = start.x() + forwardX * lengthBlocks;
        int junctionZ = start.z() + forwardZ * lengthBlocks;

        if (nextDirection == direction) {
            return new BlockPosition(
                junctionX + forwardX * MineTuning.JUNCTION_LENGTH_BLOCKS,
                start.y(),
                junctionZ + forwardZ * MineTuning.JUNCTION_LENGTH_BLOCKS
            );
        }

        if (nextDirection == direction.left()) {
            return new BlockPosition(
                junctionX - rightX,
                start.y(),
                junctionZ - rightZ
            );
        }
        if (nextDirection == direction.right()) {
            int lastJunctionDepth = MineTuning.JUNCTION_LENGTH_BLOCKS - 1;
            return new BlockPosition(
                junctionX + rightX * MineTuning.TUNNEL_WIDTH_BLOCKS + forwardX * lastJunctionDepth,
                start.y(),
                junctionZ + rightZ * MineTuning.TUNNEL_WIDTH_BLOCKS + forwardZ * lastJunctionDepth
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
