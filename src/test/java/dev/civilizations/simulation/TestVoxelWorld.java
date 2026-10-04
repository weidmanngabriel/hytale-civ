package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny deterministic 3D world fixture for spatial tests; it intentionally has no pathfinding. */
final class TestVoxelWorld {

    enum Cell {
        SOLID,
        AIR,
        SUPPORT_POST,
        SUPPORT_BEAM
    }

    private final Map<BlockPosition, Cell> overrides = new LinkedHashMap<>();

    Cell get(BlockPosition position) {
        return overrides.getOrDefault(position, Cell.SOLID);
    }

    void set(BlockPosition position, Cell cell) {
        if (cell == Cell.SOLID) {
            overrides.remove(position);
        } else {
            overrides.put(position, cell);
        }
    }

    Map<BlockPosition, Cell> snapshot(Bounds bounds) {
        Map<BlockPosition, Cell> result = new LinkedHashMap<>();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    BlockPosition position = new BlockPosition(x, y, z);
                    result.put(position, get(position));
                }
            }
        }
        return Map.copyOf(result);
    }

    String renderTopDown(Bounds bounds, int y) {
        StringBuilder result = new StringBuilder();
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                result.append(symbol(get(new BlockPosition(x, y, z))));
            }
            result.append('\n');
        }
        return result.toString();
    }

    private static char symbol(Cell cell) {
        return switch (cell) {
            case SOLID -> '#';
            case AIR -> '.';
            case SUPPORT_POST -> '|';
            case SUPPORT_BEAM -> '=';
        };
    }

    record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("Invalid voxel bounds.");
            }
        }
    }
}
