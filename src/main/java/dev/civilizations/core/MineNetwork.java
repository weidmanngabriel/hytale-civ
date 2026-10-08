package dev.civilizations.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent Civ-owned topology for one mine building.
 *
 * <p>The Hytale world remains authoritative for excavated blocks. This model stores only the
 * semantic network needed to continue mine behaviour.</p>
 */
public record MineNetwork(
    UUID mineId,
    UUID mainTunnelId,
    List<MineTunnel> tunnels,
    List<MineRoom> rooms,
    List<MineWorkFront> workFronts,
    List<MineNavigationAnchor> navigationAnchors,
    Set<UUID> completedInfrastructureTaskIds,
    Map<UUID, Integer> normalTaskPriorityBonuses
) {
    public MineNetwork {
        if (mineId == null || mainTunnelId == null || tunnels == null || rooms == null
            || workFronts == null || navigationAnchors == null || completedInfrastructureTaskIds == null
            || normalTaskPriorityBonuses == null) {
            throw new IllegalArgumentException("Mine network fields must not be null.");
        }
        tunnels = List.copyOf(tunnels);
        rooms = List.copyOf(rooms);
        workFronts = List.copyOf(workFronts);
        navigationAnchors = List.copyOf(navigationAnchors);
        completedInfrastructureTaskIds = Set.copyOf(completedInfrastructureTaskIds);
        normalTaskPriorityBonuses = Map.copyOf(normalTaskPriorityBonuses);
        for (Map.Entry<UUID, Integer> entry : normalTaskPriorityBonuses.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                || entry.getValue() <= 0 || entry.getValue() > 8) {
                throw new IllegalArgumentException("Mine task priority bonuses must be between 1 and 8.");
            }
        }
        validate(mainTunnelId, tunnels, rooms, workFronts, navigationAnchors);
    }

    public MineNetwork(
        UUID mineId,
        UUID mainTunnelId,
        List<MineTunnel> tunnels,
        List<MineRoom> rooms,
        List<MineWorkFront> workFronts,
        List<MineNavigationAnchor> navigationAnchors,
        Set<UUID> completedInfrastructureTaskIds
    ) {
        this(
            mineId, mainTunnelId, tunnels, rooms, workFronts, navigationAnchors,
            completedInfrastructureTaskIds, Map.of()
        );
    }

    public MineNetwork(
        UUID mineId,
        UUID mainTunnelId,
        List<MineTunnel> tunnels,
        List<MineRoom> rooms,
        List<MineWorkFront> workFronts,
        List<MineNavigationAnchor> navigationAnchors
    ) {
        this(mineId, mainTunnelId, tunnels, rooms, workFronts, navigationAnchors, Set.of(), Map.of());
    }

    public static MineNetwork create(UUID mineId, UUID mainTunnelId, BlockPosition origin) {
        return new MineNetwork(
            mineId,
            mainTunnelId,
            List.of(new MineTunnel(mainTunnelId, MineTunnel.Kind.MAIN, null, 0, origin)),
            List.of(),
            List.of(),
            List.of(),
            Set.of(),
            Map.of()
        );
    }

    public MineTunnel mainTunnel() {
        return tunnel(mainTunnelId);
    }

    public MineTunnel tunnel(UUID tunnelId) {
        return tunnels.stream().filter(tunnel -> tunnel.id().equals(tunnelId)).findFirst().orElse(null);
    }

    public MineNetwork withTunnel(MineTunnel tunnel) {
        ArrayList<MineTunnel> next = new ArrayList<>(tunnels);
        int index = indexOfTunnel(tunnel.id());
        if (index >= 0) next.set(index, tunnel); else next.add(tunnel);
        return new MineNetwork(mineId, mainTunnelId, next, rooms, workFronts, navigationAnchors,
            completedInfrastructureTaskIds, normalTaskPriorityBonuses);
    }

    public MineNetwork withRoom(MineRoom room) {
        return new MineNetwork(mineId, mainTunnelId, tunnels, replaceById(rooms, room, MineRoom::id),
            workFronts, navigationAnchors, completedInfrastructureTaskIds, normalTaskPriorityBonuses);
    }

    public MineNetwork withWorkFront(MineWorkFront workFront) {
        return new MineNetwork(mineId, mainTunnelId, tunnels, rooms,
            replaceById(workFronts, workFront, MineWorkFront::id), navigationAnchors,
            completedInfrastructureTaskIds, normalTaskPriorityBonuses);
    }

    public MineNetwork withNavigationAnchor(MineNavigationAnchor anchor) {
        return withNavigationAnchors(List.of(anchor));
    }

    public MineNetwork withNavigationAnchors(List<MineNavigationAnchor> anchors) {
        ArrayList<MineNavigationAnchor> next = new ArrayList<>(navigationAnchors);
        for (MineNavigationAnchor anchor : anchors) {
            boolean replaced = false;
            for (int i = 0; i < next.size(); i++) {
                if (next.get(i).id().equals(anchor.id())) {
                    next.set(i, anchor);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) next.add(anchor);
        }
        return new MineNetwork(mineId, mainTunnelId, tunnels, rooms, workFronts, next,
            completedInfrastructureTaskIds, normalTaskPriorityBonuses);
    }

    public MineNetwork withInfrastructureTaskCompleted(UUID taskId) {
        if (taskId == null || completedInfrastructureTaskIds.contains(taskId)) return this;
        Set<UUID> completed = new HashSet<>(completedInfrastructureTaskIds);
        completed.add(taskId);
        return new MineNetwork(mineId, mainTunnelId, tunnels, rooms, workFronts, navigationAnchors,
            completed, withoutPriorityBonus(normalTaskPriorityBonuses, taskId));
    }

    public MineNetwork withNormalTaskPriorityBonuses(Map<UUID, Integer> bonuses) {
        return new MineNetwork(
            mineId, mainTunnelId, tunnels, rooms, workFronts, navigationAnchors,
            completedInfrastructureTaskIds, bonuses
        );
    }

    public MineNetwork withoutNormalTaskPriorityBonus(UUID taskId) {
        if (taskId == null || !normalTaskPriorityBonuses.containsKey(taskId)) return this;
        return withNormalTaskPriorityBonuses(withoutPriorityBonus(normalTaskPriorityBonuses, taskId));
    }

    public int normalTaskPriorityBonus(UUID taskId) {
        return taskId == null ? 0 : normalTaskPriorityBonuses.getOrDefault(taskId, 0);
    }

    public boolean infrastructureTaskCompleted(UUID taskId) {
        return taskId != null && completedInfrastructureTaskIds.contains(taskId);
    }

    private static Map<UUID, Integer> withoutPriorityBonus(
        Map<UUID, Integer> bonuses,
        UUID taskId
    ) {
        if (taskId == null || !bonuses.containsKey(taskId)) return bonuses;
        Map<UUID, Integer> next = new HashMap<>(bonuses);
        next.remove(taskId);
        return Map.copyOf(next);
    }

    private int indexOfTunnel(UUID tunnelId) {
        for (int i = 0; i < tunnels.size(); i++) if (tunnels.get(i).id().equals(tunnelId)) return i;
        return -1;
    }

    private static void validate(
        UUID mainTunnelId,
        List<MineTunnel> tunnels,
        List<MineRoom> rooms,
        List<MineWorkFront> workFronts,
        List<MineNavigationAnchor> anchors
    ) {
        Map<UUID, MineTunnel> byId = uniqueMap(tunnels, MineTunnel::id, "tunnel");
        MineTunnel main = byId.get(mainTunnelId);
        if (main == null || main.kind() != MineTunnel.Kind.MAIN) {
            throw new IllegalArgumentException("Mine network requires the declared main tunnel.");
        }
        long mainCount = tunnels.stream().filter(t -> t.kind() == MineTunnel.Kind.MAIN).count();
        if (mainCount != 1) throw new IllegalArgumentException("Mine network requires exactly one main tunnel.");

        for (MineTunnel tunnel : tunnels) {
            if (tunnel.kind() == MineTunnel.Kind.BRANCH) {
                MineTunnel parent = byId.get(tunnel.parentTunnelId());
                if (parent == null) throw new IllegalArgumentException("Branch references unknown parent tunnel.");
                if (tunnel.branchDepth() != parent.branchDepth() + 1) {
                    throw new IllegalArgumentException("Branch depth must follow its parent.");
                }
                ensureAcyclic(tunnel, byId);
            }
        }

        uniqueMap(rooms, MineRoom::id, "room");
        for (MineRoom room : rooms) requireTunnel(byId, room.tunnelId(), "room");
        uniqueMap(workFronts, MineWorkFront::id, "work front");
        for (MineWorkFront front : workFronts) requireTunnel(byId, front.tunnelId(), "work front");

        Map<UUID, MineNavigationAnchor> anchorsById = uniqueMap(anchors, MineNavigationAnchor::id, "navigation anchor");
        for (MineNavigationAnchor anchor : anchors) {
            requireTunnel(byId, anchor.tunnelId(), "navigation anchor");
            for (UUID connectedId : anchor.connectedAnchorIds()) {
                if (!anchorsById.containsKey(connectedId)) {
                    throw new IllegalArgumentException("Navigation anchor references unknown connected anchor.");
                }
            }
        }
    }

    private static void ensureAcyclic(MineTunnel tunnel, Map<UUID, MineTunnel> byId) {
        Set<UUID> visited = new HashSet<>();
        MineTunnel current = tunnel;
        while (current.parentTunnelId() != null) {
            if (!visited.add(current.id())) throw new IllegalArgumentException("Mine tunnel hierarchy contains a cycle.");
            current = byId.get(current.parentTunnelId());
            if (current == null) return;
        }
    }

    private static void requireTunnel(Map<UUID, MineTunnel> tunnels, UUID tunnelId, String owner) {
        if (!tunnels.containsKey(tunnelId)) throw new IllegalArgumentException("Mine " + owner + " references unknown tunnel.");
    }

    private static <T> Map<UUID, T> uniqueMap(List<T> values, java.util.function.Function<T, UUID> id, String label) {
        Map<UUID, T> result = new HashMap<>();
        for (T value : values) {
            if (value == null || id.apply(value) == null || result.put(id.apply(value), value) != null) {
                throw new IllegalArgumentException("Mine network contains invalid or duplicate " + label + ".");
            }
        }
        return result;
    }

    private static <T> List<T> replaceById(
        List<T> values,
        T value,
        java.util.function.Function<T, UUID> id
    ) {
        ArrayList<T> next = new ArrayList<>(values);
        UUID valueId = id.apply(value);
        for (int i = 0; i < next.size(); i++) {
            if (id.apply(next.get(i)).equals(valueId)) {
                next.set(i, value);
                return List.copyOf(next);
            }
        }
        next.add(value);
        return List.copyOf(next);
    }
}
