package dev.civilizations.simulation.prefab;

import dev.civilizations.core.BlockPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Small A* reachability oracle for simplified prefab geometry.
 *
 * <p>This deliberately does not model Hytale navigation. It answers only whether a two-block-tall
 * test agent has a plausible block path through the authored geometry.</p>
 */
public final class PrefabAStarPathfinder {

    private static final int[][] HORIZONTAL = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    public List<BlockPosition> findPath(
        PrefabSimulationModel model,
        BlockPosition start,
        BlockPosition goal
    ) {
        if (!model.isWalkableFeet(start) || !model.isWalkableFeet(goal)) {
            return List.of();
        }

        PrefabSimulationModel.Bounds searchBounds = model.blockBounds().expand(4, 3);
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<BlockPosition, Double> cost = new HashMap<>();
        Map<BlockPosition, BlockPosition> previous = new HashMap<>();
        Set<BlockPosition> closed = new HashSet<>();

        cost.put(start, 0.0);
        open.add(new Node(start, heuristic(start, goal)));

        while (!open.isEmpty()) {
            BlockPosition current = open.poll().position();
            if (!closed.add(current)) {
                continue;
            }
            if (current.equals(goal)) {
                return reconstruct(previous, goal);
            }

            for (BlockPosition neighbor : neighbors(model, current, searchBounds)) {
                if (closed.contains(neighbor)) {
                    continue;
                }
                double nextCost = cost.get(current) + moveCost(current, neighbor);
                if (nextCost >= cost.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    continue;
                }
                cost.put(neighbor, nextCost);
                previous.put(neighbor, current);
                open.add(new Node(neighbor, nextCost + heuristic(neighbor, goal)));
            }
        }
        return List.of();
    }

    private static List<BlockPosition> neighbors(
        PrefabSimulationModel model,
        BlockPosition current,
        PrefabSimulationModel.Bounds bounds
    ) {
        List<BlockPosition> result = new ArrayList<>(12);
        for (int[] offset : HORIZONTAL) {
            int x = current.x() + offset[0];
            int z = current.z() + offset[1];
            for (int y : new int[] {current.y(), current.y() + 1, current.y() - 1}) {
                BlockPosition candidate = new BlockPosition(x, y, z);
                if (bounds.contains(candidate) && model.isWalkableFeet(candidate)) {
                    result.add(candidate);
                    break;
                }
            }
        }
        return result;
    }

    private static double moveCost(BlockPosition from, BlockPosition to) {
        return from.y() == to.y() ? 1.0 : 1.25;
    }

    private static double heuristic(BlockPosition from, BlockPosition to) {
        return Math.abs(from.x() - to.x())
            + Math.abs(from.z() - to.z())
            + 1.25 * Math.abs(from.y() - to.y());
    }

    private static List<BlockPosition> reconstruct(
        Map<BlockPosition, BlockPosition> previous,
        BlockPosition goal
    ) {
        List<BlockPosition> reversed = new ArrayList<>();
        BlockPosition current = goal;
        while (current != null) {
            reversed.add(current);
            current = previous.get(current);
        }
        List<BlockPosition> path = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            path.add(reversed.get(i));
        }
        return List.copyOf(path);
    }

    private record Node(BlockPosition position, double score) {
    }
}
