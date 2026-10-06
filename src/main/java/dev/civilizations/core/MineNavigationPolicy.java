package dev.civilizations.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/**
 * Hytale-independent policy for the mine's small semantic navigation-anchor graph.
 *
 * <p>This class deliberately does not perform voxel pathfinding. It only reasons about known-safe
 * anchor points that were already confirmed by actual NPC traversal in the Hytale adapter.</p>
 */
public final class MineNavigationPolicy {

    public static final double REGULAR_ANCHOR_SPACING_BLOCKS = 10.0;
    public static final double LONG_DISTANCE_TELEPORT_BLOCKS = 50.0;

    private static final double ANCHOR_SPACING_SQUARED =
        REGULAR_ANCHOR_SPACING_BLOCKS * REGULAR_ANCHOR_SPACING_BLOCKS;
    private static final double TELEPORT_DISTANCE_SQUARED =
        LONG_DISTANCE_TELEPORT_BLOCKS * LONG_DISTANCE_TELEPORT_BLOCKS;

    private MineNavigationPolicy() {
    }

    /**
     * Regular anchors are intentionally sparse. Exactly ten blocks is allowed; anything closer is
     * rejected. Physical world safety is checked by the Hytale adapter before calling this method.
     */
    public static boolean canCreateRegularAnchor(
        BlockPosition candidate,
        Collection<MineNavigationAnchor> existingAnchors
    ) {
        if (candidate == null || existingAnchors == null) return false;
        for (MineNavigationAnchor anchor : existingAnchors) {
            if (anchor == null) continue;
            if (distanceSquared(candidate, anchor.position()) < ANCHOR_SPACING_SQUARED) return false;
        }
        return true;
    }

    /** Uses the agreed Euclidean air-line threshold. Exactly 50 blocks still means walk. */
    public static boolean shouldTeleport(BlockPosition safeReference, WorldPosition target) {
        if (safeReference == null || target == null) return false;
        return distanceSquared(safeReference, target) > TELEPORT_DISTANCE_SQUARED;
    }

    /**
     * Returns the shortest route through already-established semantic anchor links. Edge cost is
     * Euclidean distance between anchor positions. An empty list means that no safe graph route is
     * known.
     */
    public static List<UUID> shortestRoute(
        Collection<MineNavigationAnchor> anchors,
        UUID startAnchorId,
        UUID endAnchorId
    ) {
        Map<UUID, MineNavigationAnchor> byId = byId(anchors);
        if (!byId.containsKey(startAnchorId) || !byId.containsKey(endAnchorId)) return List.of();
        if (startAnchorId.equals(endAnchorId)) return List.of(startAnchorId);

        Map<UUID, Double> distances = new HashMap<>();
        Map<UUID, UUID> previous = new HashMap<>();
        PriorityQueue<RouteNode> queue = new PriorityQueue<>(Comparator
            .comparingDouble(RouteNode::distance)
            .thenComparing(node -> node.anchorId().toString()));

        distances.put(startAnchorId, 0.0);
        queue.add(new RouteNode(startAnchorId, 0.0));

        while (!queue.isEmpty()) {
            RouteNode current = queue.poll();
            double known = distances.getOrDefault(current.anchorId(), Double.POSITIVE_INFINITY);
            if (current.distance() > known) continue;
            if (current.anchorId().equals(endAnchorId)) break;

            MineNavigationAnchor currentAnchor = byId.get(current.anchorId());
            if (currentAnchor == null) continue;
            for (UUID connectedId : currentAnchor.connectedAnchorIds()) {
                MineNavigationAnchor connected = byId.get(connectedId);
                if (connected == null) continue;
                double candidateDistance = known + Math.sqrt(
                    distanceSquared(currentAnchor.position(), connected.position())
                );
                double oldDistance = distances.getOrDefault(connectedId, Double.POSITIVE_INFINITY);
                if (candidateDistance + 1e-9 < oldDistance) {
                    distances.put(connectedId, candidateDistance);
                    previous.put(connectedId, current.anchorId());
                    queue.add(new RouteNode(connectedId, candidateDistance));
                }
            }
        }

        if (!distances.containsKey(endAnchorId)) return List.of();
        LinkedList<UUID> route = new LinkedList<>();
        UUID cursor = endAnchorId;
        while (cursor != null) {
            route.addFirst(cursor);
            if (cursor.equals(startAnchorId)) return List.copyOf(route);
            cursor = previous.get(cursor);
        }
        return List.of();
    }

    /**
     * Selects the reachable safe anchor on the target tunnel that is closest by air-line to the
     * actual target. A geometrically closer but disconnected anchor is never selected.
     */
    public static MineNavigationAnchor selectTeleportAnchor(
        Collection<MineNavigationAnchor> anchors,
        UUID startAnchorId,
        UUID targetTunnelId,
        WorldPosition target
    ) {
        if (startAnchorId == null || targetTunnelId == null || target == null) return null;
        Map<UUID, MineNavigationAnchor> byId = byId(anchors);
        if (!byId.containsKey(startAnchorId)) return null;

        Set<UUID> reachable = reachableAnchorIds(byId, startAnchorId);
        return reachable.stream()
            .map(byId::get)
            .filter(anchor -> anchor != null && targetTunnelId.equals(anchor.tunnelId()))
            .min(Comparator
                .comparingDouble((MineNavigationAnchor anchor) -> distanceSquared(anchor.position(), target))
                .thenComparing(anchor -> anchor.id().toString()))
            .orElse(null);
    }

    /** Returns the closest known anchor to a position, optionally restricted to a tunnel. */
    public static MineNavigationAnchor closestAnchor(
        Collection<MineNavigationAnchor> anchors,
        UUID tunnelId,
        WorldPosition position
    ) {
        if (anchors == null || position == null) return null;
        return anchors.stream()
            .filter(anchor -> anchor != null && (tunnelId == null || tunnelId.equals(anchor.tunnelId())))
            .min(Comparator
                .comparingDouble((MineNavigationAnchor anchor) -> distanceSquared(anchor.position(), position))
                .thenComparing(anchor -> anchor.id().toString()))
            .orElse(null);
    }

    private static Set<UUID> reachableAnchorIds(
        Map<UUID, MineNavigationAnchor> byId,
        UUID startAnchorId
    ) {
        Set<UUID> visited = new HashSet<>();
        ArrayList<UUID> queue = new ArrayList<>();
        visited.add(startAnchorId);
        queue.add(startAnchorId);
        for (int index = 0; index < queue.size(); index++) {
            MineNavigationAnchor current = byId.get(queue.get(index));
            if (current == null) continue;
            for (UUID connectedId : current.connectedAnchorIds()) {
                if (byId.containsKey(connectedId) && visited.add(connectedId)) queue.add(connectedId);
            }
        }
        return visited;
    }

    private static Map<UUID, MineNavigationAnchor> byId(Collection<MineNavigationAnchor> anchors) {
        Map<UUID, MineNavigationAnchor> result = new HashMap<>();
        if (anchors == null) return result;
        for (MineNavigationAnchor anchor : anchors) {
            if (anchor != null) result.put(anchor.id(), anchor);
        }
        return result;
    }

    private static double distanceSquared(BlockPosition first, BlockPosition second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static double distanceSquared(BlockPosition first, WorldPosition second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private record RouteNode(UUID anchorId, double distance) {
    }
}
