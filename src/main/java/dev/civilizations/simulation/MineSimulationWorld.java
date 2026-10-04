package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineSupportFrame;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal deterministic 3D voxel fixture for mine simulation and visualization.
 *
 * <p>This is deliberately not a Hytale world model: no chunks, pathfinding, physics, lighting,
 * block assets or collision. It only records the semantic cells needed to execute and inspect the
 * Civ miner workflow.</p>
 */
public final class MineSimulationWorld {

    public enum Cell {
        SOLID,
        AIR,
        SUPPORT_POST,
        SUPPORT_BEAM
    }

    private final MineSegment segment;
    private final Map<BlockPosition, Cell> overrides = new LinkedHashMap<>();
    private final Bounds bounds;

    /** Creates the smallest possible rock volume containing exactly the segment. */
    public MineSimulationWorld(MineSegment segment) {
        this(segment, boundsFor(segment));
    }

    /**
     * Creates a larger deterministic rock volume around the segment.
     *
     * <p>Every cell inside {@code bounds} is SOLID until explicitly excavated. This lets viewer
     * scenarios model a mountain/underground mass without changing MinerJob itself.</p>
     */
    public MineSimulationWorld(MineSegment segment, Bounds bounds) {
        this.segment = Objects.requireNonNull(segment, "segment");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        for (BlockPosition block : segment.blocks()) {
            if (!bounds.contains(block)) {
                throw new IllegalArgumentException("Mine simulation bounds must contain the entire segment.");
            }
        }
    }

    public MineSegment segment() {
        return segment;
    }

    public Cell get(BlockPosition position) {
        Objects.requireNonNull(position, "position");
        if (!bounds.contains(position)) {
            throw new IllegalArgumentException("Position outside mine simulation bounds: " + position);
        }
        return overrides.getOrDefault(position, Cell.SOLID);
    }

    public void breakBlock(BlockPosition position) {
        Objects.requireNonNull(position, "position");
        if (!bounds.contains(position)) {
            throw new IllegalArgumentException("Cannot break block outside mine simulation bounds: " + position);
        }
        overrides.put(position, Cell.AIR);
    }

    public void placeSupport(MineSegment currentSegment, int depth) {
        Objects.requireNonNull(currentSegment, "currentSegment");
        for (MineSupportFrame.Cell cell : MineSupportFrame.cells(currentSegment, depth)) {
            if (!bounds.contains(cell.position())) {
                throw new IllegalArgumentException("Support outside mine simulation bounds: " + cell.position());
            }
            overrides.put(
                cell.position(),
                cell.part() == MineSupportFrame.Part.POST ? Cell.SUPPORT_POST : Cell.SUPPORT_BEAM
            );
        }
    }

    public Bounds bounds() {
        return bounds;
    }

    public Snapshot snapshot() {
        Map<BlockPosition, Cell> cells = new LinkedHashMap<>();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    BlockPosition position = new BlockPosition(x, y, z);
                    cells.put(position, get(position));
                }
            }
        }
        return new Snapshot(bounds, Map.copyOf(cells));
    }

    private static Bounds boundsFor(MineSegment segment) {
        int minX = segment.blocks().stream().mapToInt(BlockPosition::x).min().orElseThrow();
        int maxX = segment.blocks().stream().mapToInt(BlockPosition::x).max().orElseThrow();
        int minY = segment.blocks().stream().mapToInt(BlockPosition::y).min().orElseThrow();
        int maxY = segment.blocks().stream().mapToInt(BlockPosition::y).max().orElseThrow();
        int minZ = segment.blocks().stream().mapToInt(BlockPosition::z).min().orElseThrow();
        int maxZ = segment.blocks().stream().mapToInt(BlockPosition::z).max().orElseThrow();
        return new Bounds(minX, maxX, minY, maxY, minZ, maxZ);
    }

    public record Snapshot(Bounds bounds, Map<BlockPosition, Cell> cells) {
        public Snapshot {
            Objects.requireNonNull(bounds, "bounds");
            cells = Map.copyOf(cells);
        }

        public Cell get(BlockPosition position) {
            if (!bounds.contains(position)) {
                return null;
            }
            return cells.getOrDefault(position, Cell.SOLID);
        }
    }

    public record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        public Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("Invalid mine simulation bounds.");
            }
        }

        public boolean contains(BlockPosition position) {
            return position.x() >= minX && position.x() <= maxX
                && position.y() >= minY && position.y() <= maxY
                && position.z() >= minZ && position.z() <= maxZ;
        }

        public int width() {
            return maxX - minX + 1;
        }

        public int height() {
            return maxY - minY + 1;
        }

        public int depth() {
            return maxZ - minZ + 1;
        }
    }
}
