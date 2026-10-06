package dev.civilizations.core;

import java.util.Set;
import java.util.UUID;

/** Known-safe semantic navigation point. Hytale still owns local pathfinding between anchors. */
public record MineNavigationAnchor(
    UUID id,
    UUID tunnelId,
    BlockPosition position,
    Type type,
    Set<UUID> connectedAnchorIds
) {
    public MineNavigationAnchor {
        if (id == null || tunnelId == null || position == null || type == null || connectedAnchorIds == null) {
            throw new IllegalArgumentException("Mine navigation-anchor fields must not be null.");
        }
        connectedAnchorIds = Set.copyOf(connectedAnchorIds);
        if (connectedAnchorIds.contains(id)) {
            throw new IllegalArgumentException("Navigation anchor cannot connect to itself.");
        }
    }

    public enum Type {
        REGULAR,
        JUNCTION,
        ROOM,
        BRIDGE_START,
        BRIDGE_END,
        STRONG_TURN,
        HEIGHT_TRANSITION,
        WORK_FRONT
    }
}
