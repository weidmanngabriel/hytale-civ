package dev.civilizations.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Hytale-independent geometry of a mine support frame inside one tunnel face.
 *
 * <p>The Core owns which tunnel cells belong to the support. The Hytale adapter remains
 * responsible for mapping those semantic cells to the actual prefab and native block assets.</p>
 */
public final class MineSupportFrame {

    private MineSupportFrame() {
    }

    public static List<Cell> cells(MineSegment segment, int depth) {
        Objects.requireNonNull(segment, "segment");
        if (depth <= 0 || depth > MineTuning.SEGMENT_LENGTH_BLOCKS) {
            throw new IllegalArgumentException("Support depth outside segment.");
        }

        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int faceStart = (depth - 1) * faceSize;
        List<Cell> result = new ArrayList<>();

        for (int y = 0; y < MineTuning.TUNNEL_HEIGHT_BLOCKS; y++) {
            for (int width = 0; width < MineTuning.TUNNEL_WIDTH_BLOCKS; width++) {
                Part part = partAt(width, y).orElse(null);
                if (part == null) continue;
                result.add(new Cell(
                    segment.blockAtIndex(faceStart + y * MineTuning.TUNNEL_WIDTH_BLOCKS + width),
                    part
                ));
            }
        }

        return List.copyOf(result);
    }

    /**
     * Returns the support part expected at a segment block index when that face is on the
     * configured support cadence. Non-support faces and the open center of a support frame
     * return an empty result.
     */
    public static Optional<Part> scheduledPartAtIndex(int index) {
        if (index < 0 || index >= MineTuning.blocksPerSegment()) {
            throw new IndexOutOfBoundsException(index);
        }

        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int oneBasedDepth = index / faceSize + 1;
        if (oneBasedDepth % MineTuning.SUPPORT_SPACING_BLOCKS != 0) {
            return Optional.empty();
        }

        int inFace = index % faceSize;
        int y = inFace / MineTuning.TUNNEL_WIDTH_BLOCKS;
        int width = inFace % MineTuning.TUNNEL_WIDTH_BLOCKS;
        return partAt(width, y);
    }

    private static Optional<Part> partAt(int width, int y) {
        if (y == MineTuning.TUNNEL_HEIGHT_BLOCKS - 1) {
            return Optional.of(Part.BEAM);
        }
        if (width == 0 || width == MineTuning.TUNNEL_WIDTH_BLOCKS - 1) {
            return Optional.of(Part.POST);
        }
        return Optional.empty();
    }

    public enum Part {
        POST,
        BEAM
    }

    public record Cell(BlockPosition position, Part part) {
        public Cell {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(part, "part");
        }
    }
}
