package dev.civilizations.core;

/** Shared semantic ordering for a miner entering a mine. Physical movement remains adapter-owned. */
public final class MineWorkerRouteDecision {
    private MineWorkerRouteDecision() {}

    public enum Destination { WORKPLACE_ACCESS, TUNNEL_CONNECTOR, WORK_FRONT }

    public static Destination next(boolean reachedAccess, boolean reachedConnector) {
        if (!reachedAccess) return Destination.WORKPLACE_ACCESS;
        if (!reachedConnector) return Destination.TUNNEL_CONNECTOR;
        return Destination.WORK_FRONT;
    }
}
