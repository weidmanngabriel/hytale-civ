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
    Status status,
    int nextBlockIndex,
    int supportsPlaced
) {
    public MineSegment {
        if (id == null || mineId == null || start == null || direction == null || status == null) {
            throw new IllegalArgumentException("Mine segment fields must not be null.");
        }
        if (nextBlockIndex < 0 || nextBlockIndex > MineTuning.blocksPerSegment()) {
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
        return new MineSegment(id, mineId, parentId, start, direction, Status.RESERVED, 0, 0);
    }

    public MineSegment withStatus(Status nextStatus) {
        return new MineSegment(
            id, mineId, parentId, start, direction, nextStatus, nextBlockIndex, supportsPlaced
        );
    }

    public MineSegment withProgress(int nextIndex) {
        return new MineSegment(
            id, mineId, parentId, start, direction, status, nextIndex, supportsPlaced
        );
    }

    public MineSegment withSupportsPlaced(int count) {
        return new MineSegment(
            id, mineId, parentId, start, direction, status, nextBlockIndex, count
        );
    }

    public boolean complete() {
        return nextBlockIndex >= MineTuning.blocksPerSegment();
    }

    /** Blocks ordered one full 4x4 face at a time from the entrance towards the tunnel end. */
    public List<BlockPosition> blocks() {
        List<BlockPosition> result = new ArrayList<>(MineTuning.blocksPerSegment());
        for (int depth = 0; depth < MineTuning.SEGMENT_LENGTH_BLOCKS; depth++) {
            for (int y = 0; y < MineTuning.TUNNEL_HEIGHT_BLOCKS; y++) {
                for (int width = 0; width < MineTuning.TUNNEL_WIDTH_BLOCKS; width++) {
                    result.add(blockAt(depth, width, y));
                }
            }
        }
        return List.copyOf(result);
    }

    public BlockPosition blockAtIndex(int index) {
        if (index < 0 || index >= MineTuning.blocksPerSegment()) {
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
        if (depth <= 0 || depth > MineTuning.SEGMENT_LENGTH_BLOCKS) {
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
        List<BlockPosition> floor = new ArrayList<>();
        for (int depth = 0; depth < MineTuning.SEGMENT_LENGTH_BLOCKS; depth++) {
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

    /**
     * Starts the next segment. A straight segment starts immediately after the end face.
     * A turn reuses the last four tunnel cells as its junction, so the new side opening is
     * naturally four blocks wide. Collision checks may therefore ignore overlap with parent.
     */
    public BlockPosition nextStart(MineDirection nextDirection) {
        if (nextDirection == opposite(direction)) {
            throw new IllegalArgumentException("Mine segments cannot immediately reverse.");
        }
        HorizontalBounds b = horizontalBounds();
        if (nextDirection == direction) {
            return switch (direction) {
                case NORTH -> new BlockPosition(b.minX(), start.y(), b.minZ() - 1);
                case SOUTH -> new BlockPosition(b.minX(), start.y(), b.maxZ() + 1);
                case EAST -> new BlockPosition(b.maxX() + 1, start.y(), b.minZ());
                case WEST -> new BlockPosition(b.minX() - 1, start.y(), b.minZ());
            };
        }

        return switch (nextDirection) {
            case EAST -> new BlockPosition(
                b.maxX() + 1,
                start.y(),
                direction == MineDirection.SOUTH ? b.maxZ() - 3 : b.minZ()
            );
            case WEST -> new BlockPosition(
                b.minX() - 1,
                start.y(),
                direction == MineDirection.SOUTH ? b.maxZ() - 3 : b.minZ()
            );
            case SOUTH -> new BlockPosition(
                direction == MineDirection.EAST ? b.maxX() - 3 : b.minX(),
                start.y(),
                b.maxZ() + 1
            );
            case NORTH -> new BlockPosition(
                direction == MineDirection.EAST ? b.maxX() - 3 : b.minX(),
                start.y(),
                b.minZ() - 1
            );
        };
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
