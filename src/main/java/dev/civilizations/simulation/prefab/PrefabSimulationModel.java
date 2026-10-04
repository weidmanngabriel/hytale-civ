package dev.civilizations.simulation.prefab;

import dev.civilizations.core.BlockPosition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hytale-independent, deliberately simplified view of an authored prefab.
 *
 * <p>The model preserves real authored block coordinates and Civ semantic trigger volumes, but
 * collapses concrete Hytale block types into the minimum categories needed for spatial simulation.
 * It is not a replacement for Hytale block collision or navigation semantics.</p>
 */
public final class PrefabSimulationModel {

    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;
    private final Map<BlockPosition, Cell> cells;
    private final List<Marker> markers;
    private final Bounds blockBounds;

    public PrefabSimulationModel(
        int anchorX,
        int anchorY,
        int anchorZ,
        Map<BlockPosition, Cell> cells,
        List<Marker> markers
    ) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.cells = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(cells, "cells")));
        this.markers = List.copyOf(Objects.requireNonNull(markers, "markers"));
        this.blockBounds = Bounds.around(this.cells.keySet());
    }

    public int anchorX() {
        return anchorX;
    }

    public int anchorY() {
        return anchorY;
    }

    public int anchorZ() {
        return anchorZ;
    }

    public Map<BlockPosition, Cell> cells() {
        return cells;
    }

    public List<Marker> markers() {
        return markers;
    }

    public Bounds blockBounds() {
        return blockBounds;
    }

    public Cell cellAt(BlockPosition position) {
        return cells.get(position);
    }

    public boolean isPassable(BlockPosition position) {
        Cell cell = cellAt(position);
        return cell == null || cell == Cell.DOOR;
    }

    /**
     * Returns whether a simple two-block-tall test agent can stand with its feet at {@code feet}.
     *
     * <p>Missing Y=0 cells are treated as flat surrounding terrain for this simulation lab. This
     * makes the imported prefab testable in isolation without inventing a full terrain generator.</p>
     */
    public boolean isWalkableFeet(BlockPosition feet) {
        BlockPosition head = new BlockPosition(feet.x(), feet.y() + 1, feet.z());
        if (!isPassable(feet) || !isPassable(head)) {
            return false;
        }
        BlockPosition support = new BlockPosition(feet.x(), feet.y() - 1, feet.z());
        Cell supportCell = cellAt(support);
        return supportCell == Cell.SOLID
            || (support.y() == 0 && supportCell == null);
    }

    public List<BlockPosition> doorFeet() {
        List<BlockPosition> result = new ArrayList<>();
        for (Map.Entry<BlockPosition, Cell> entry : cells.entrySet()) {
            if (entry.getValue() != Cell.DOOR) {
                continue;
            }
            BlockPosition position = entry.getKey();
            BlockPosition below = new BlockPosition(position.x(), position.y() - 1, position.z());
            if (cellAt(below) != Cell.DOOR && isWalkableFeet(position)) {
                result.add(position);
            }
        }
        return List.copyOf(result);
    }

    public Marker requireMarker(String type) {
        return markers.stream()
            .filter(marker -> type.equals(marker.type()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing prefab marker civ.type=" + type));
    }

    public PrefabSimulationModel withCell(BlockPosition position, Cell cell) {
        Map<BlockPosition, Cell> changed = new LinkedHashMap<>(cells);
        if (cell == null) {
            changed.remove(position);
        } else {
            changed.put(position, cell);
        }
        return new PrefabSimulationModel(anchorX, anchorY, anchorZ, changed, markers);
    }

    public enum Cell {
        SOLID,
        DOOR
    }

    public record Marker(
        String name,
        String type,
        String building,
        Box bounds
    ) {
        public Marker {
            name = Objects.requireNonNull(name, "name");
            type = Objects.requireNonNull(type, "type");
            building = Objects.requireNonNull(building, "building");
            bounds = Objects.requireNonNull(bounds, "bounds");
        }

        public boolean containsFeet(BlockPosition position) {
            double centerX = position.x() + 0.5;
            double centerZ = position.z() + 0.5;
            return centerX >= bounds.minX() && centerX <= bounds.maxX()
                && position.y() >= bounds.minY() && position.y() < bounds.maxY()
                && centerZ >= bounds.minZ() && centerZ <= bounds.maxZ();
        }
    }

    public record Box(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        public Box {
            if (maxX < minX || maxY < minY || maxZ < minZ) {
                throw new IllegalArgumentException("Invalid box bounds");
            }
        }

        public double centerX() {
            return (minX + maxX) / 2.0;
        }

        public double centerY() {
            return (minY + maxY) / 2.0;
        }

        public double centerZ() {
            return (minZ + maxZ) / 2.0;
        }
    }

    public record Bounds(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
    ) {
        static Bounds around(Iterable<BlockPosition> positions) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            boolean found = false;
            for (BlockPosition position : positions) {
                found = true;
                minX = Math.min(minX, position.x());
                minY = Math.min(minY, position.y());
                minZ = Math.min(minZ, position.z());
                maxX = Math.max(maxX, position.x());
                maxY = Math.max(maxY, position.y());
                maxZ = Math.max(maxZ, position.z());
            }
            if (!found) {
                return new Bounds(0, 0, 0, 0, 0, 0);
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public Bounds expand(int horizontal, int vertical) {
            return new Bounds(
                minX - horizontal,
                minY - vertical,
                minZ - horizontal,
                maxX + horizontal,
                maxY + vertical,
                maxZ + horizontal
            );
        }

        public boolean contains(BlockPosition position) {
            return position.x() >= minX && position.x() <= maxX
                && position.y() >= minY && position.y() <= maxY
                && position.z() >= minZ && position.z() <= maxZ;
        }
    }
}
