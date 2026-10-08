package dev.civilizations.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic, compact rules for successive main-tunnel generations.
 * Does not persist or inspect excavated blocks.
 */
public final class MineGenerationPolicy {
    public static final int MIN_FLOOR_Y = 10;
    public static final long REFRESH_INTERVAL_MILLIS = 10 * 60 * 1000L;

    private MineGenerationPolicy() {
    }

    /** The first main tunnel remains the original network root. */
    public static UUID tunnelId(UUID mineId, int generation) {
        if (mineId == null || generation < 0) throw new IllegalArgumentException("Invalid mine generation");
        return UUID.nameUUIDFromBytes(
            ("civ-mine-generation:" + mineId + ":" + generation)
                .getBytes(StandardCharsets.UTF_8)
        );
    }

    /** Fan successive excavations into different directions around the original entrance. */
    public static MineHeading heading(MineHeading entranceHeading, int generation) {
        if (entranceHeading == null || generation < 0)
            throw new IllegalArgumentException("Invalid entrance heading or generation");
        int rotated = Math.floorMod(entranceHeading.ordinal() + generation * 2,
            MineHeading.values().length);
        return MineHeading.values()[rotated];
    }

    /** The first point at the floor limit is the final point; never dig below it. */
    public static MineTunnelPath capAtMinimumY(MineTunnelPath original) {
        if (original == null) throw new IllegalArgumentException("Path required");
        List<MinePathPoint> points = original.points();
        ArrayList<MinePathPoint> capped = new ArrayList<>();
        for (MinePathPoint point : points) {
            if (Math.round(point.y()) < MIN_FLOOR_Y) break;
            capped.add(point);
            if (Math.round(point.y()) == MIN_FLOOR_Y) break;
        }
        if (capped.isEmpty()) throw new IllegalArgumentException("Origin below minimum mine floor");
        return new MineTunnelPath(original.tunnelKind(), original.seed(), capped, original.phases());
    }

    public static boolean reachedMinimumY(MineTunnelPath path) {
        return path.points().stream().anyMatch(point -> Math.round(point.y()) == MIN_FLOOR_Y);
    }

    public static boolean due(long lastRefreshMillis, long nowMillis) {
        return nowMillis >= lastRefreshMillis && nowMillis - lastRefreshMillis >= REFRESH_INTERVAL_MILLIS;
    }
}
