package dev.civilizations.core;

/**
 * Shared semantic mine-entry state, independent of Hytale Seek or headless pathfinding.
 * The engine adapter acknowledges each destination before the next stage can begin.
 */
public final class MineWorkerEntryPolicy {
    private MineWorkerEntryPolicy() {}

    public enum Destination { WORKPLACE_ACCESS, TUNNEL_CONNECTOR, WORK_FRONT }

    public static Destination next(boolean accessMarkerExists, boolean accessReached,
                                   boolean connectorReached) {
        if (accessMarkerExists && !accessReached) return Destination.WORKPLACE_ACCESS;
        if (!connectorReached) return Destination.TUNNEL_CONNECTOR;
        return Destination.WORK_FRONT;
    }
}
