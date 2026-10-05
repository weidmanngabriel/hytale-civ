package dev.civilizations.core;

import java.util.List;

/** Planned tunnel centerline and the coherent form phases that produced it. */
public record MineTunnelPath(
    MineTunnel.Kind tunnelKind,
    long seed,
    List<MinePathPoint> points,
    List<MineFormPhase> phases
) {
    public MineTunnelPath {
        if (tunnelKind == null || points == null || phases == null) {
            throw new IllegalArgumentException("Mine tunnel path fields must not be null.");
        }
        points = List.copyOf(points);
        phases = List.copyOf(phases);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Mine tunnel path requires at least one point.");
        }
    }
}
