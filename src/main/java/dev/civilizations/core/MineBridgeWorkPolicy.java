package dev.civilizations.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One semantic terrain/passability policy for native miners and MineLab.
 * Adapters provide loaded-world block/fluid observations and execute BUILD_BRIDGE;
 * they never independently decide whether a front needs that mandatory task.
 */
public final class MineBridgeWorkPolicy {
    public static final int MAX_SPAN = 50;
    public static final int LANDING_OVERLAP = 3;
    private MineBridgeWorkPolicy() {}

    public enum Block { UNKNOWN, EMPTY, SOLID }
    public enum Fluid { UNKNOWN, NONE, WATER, LAVA }

    public interface Terrain {
        Block block(BlockPosition position);
        Fluid fluid(BlockPosition position);
    }

    public record Decision(MineInfrastructureTask task, String failureReason, boolean hazardousFluid) {
        public static Decision none() { return new Decision(null, null, false); }
        public static Decision bridge(MineInfrastructureTask task) { return new Decision(task, null, false); }
        public static Decision fail(String reason, boolean hazard) { return new Decision(null, reason, hazard); }
    }

    /** The current slice index is the next slice to excavate, not the last completed slice. */
    public static Decision assess(UUID tunnelId, List<MineTunnelGeometry.Slice> slices,
                                  int current, Terrain terrain, int maxSpan, int landingOverlap) {
        Objects.requireNonNull(tunnelId);
        Objects.requireNonNull(slices);
        Objects.requireNonNull(terrain);
        if (current < 0 || current >= slices.size()) return Decision.none();
        if (corridorHasFluid(terrain, slices.get(current)))
            return Decision.fail("FLUID_IN_NAVIGATION_CORRIDOR", true);

        // The *previous* slice becomes the worker's next staging target. Even a
        // shallow missing floor makes its exact authored work target unstandable.
        // Both engines request the same repair instead of immediately blocking
        // the tunnel front on a navigation failure.
        if (current >= 2) {
            var approach = slices.get(current - 1).floorCenter();
            var prior = slices.get(current - 2).floorCenter();
            if (terrain.block(approach) == Block.EMPTY
                && terrain.block(below(approach, 1)) == Block.EMPTY
                && terrain.block(below(prior, 1)) == Block.SOLID) {
                if (terrain.fluid(below(approach, 1)) == Fluid.LAVA)
                    return Decision.fail("LAVA_GAP", true);
                if (terrain.fluid(below(approach, 1)) == Fluid.NONE)
                    return Decision.bridge(MineInfrastructurePlanner.bridgeTask(
                        tunnelId, current - 1, current - 1, approach));
            }
        }

        // A deep gap *ahead* is only actionable after its walk cell is empty.
        // Rock must first be excavated; one-block depressions aren't long spans.
        if (current <= 0 || current >= slices.size() - 2) return Decision.none();
        var center = slices.get(current).floorCenter();
        if (terrain.block(center) != Block.EMPTY || !missingFloor(terrain, center))
            return Decision.none();
        if (slices.get(current - 1).floorCenter().y() != center.y()
            || shallowGround(terrain, center)) return Decision.none();
        if (missingFloor(terrain, slices.get(current - 1).floorCenter()))
            return Decision.fail("UNSAFE_GAP_WITHOUT_APPROACH", false);

        int end = current;
        boolean fluid = fluidBelow(terrain, center);
        boolean lava = lavaBelow(terrain, center);
        while (end + 1 < slices.size() && missingFloor(terrain, slices.get(end + 1).floorCenter())) {
            end++;
            var previous = slices.get(end - 1).floorCenter();
            var next = slices.get(end).floorCenter();
            if (previous.y() != next.y() || shallowGround(terrain, next)) return Decision.none();
            fluid |= fluidBelow(terrain, next);
            lava |= lavaBelow(terrain, next);
            if (end - current + 1 > maxSpan)
                return Decision.fail("GAP_EXCEEDS_BRIDGE_RANGE", lava);
        }
        if (lava) return Decision.fail("LAVA_GAP", true);
        if (end - current + 1 > maxSpan)
            return Decision.fail("GAP_EXCEEDS_SAFE_BRIDGE_RANGE", false);
        int landing = end + 1;
        if (!levelAcross(slices, Math.max(0, current - 1),
            Math.min(slices.size() - 1, landing + 1))) return Decision.none();
        if (landing >= slices.size() || !safeLanding(terrain, slices.get(landing)))
            return Decision.fail("GAP_WITHOUT_SAFE_LANDING", false);
        if (landing + 1 >= slices.size())
            return Decision.fail("GAP_WITHOUT_PLANNED_CONTINUATION", false);
        return Decision.bridge(MineInfrastructurePlanner.bridgeTask(tunnelId,
            Math.max(1, current - landingOverlap),
            Math.min(slices.size() - 1, landing + landingOverlap),
            center));
    }

    private static BlockPosition below(BlockPosition p, int offset) {
        return new BlockPosition(p.x(), p.y() - offset, p.z());
    }

    private static boolean missingFloor(Terrain t, BlockPosition p) {
        return t.block(below(p, 1)) == Block.EMPTY;
    }

    private static boolean shallowGround(Terrain t, BlockPosition p) {
        for (int depth = 2; depth <= 3; depth++)
            if (t.block(below(p, depth)) != Block.EMPTY) return true;
        return false;
    }

    private static boolean fluidBelow(Terrain t, BlockPosition p) {
        for (int depth = 1; depth <= 4; depth++) {
            Fluid state = t.fluid(below(p, depth));
            if (state == Fluid.WATER || state == Fluid.LAVA) return true;
        }
        return false;
    }

    private static boolean lavaBelow(Terrain t, BlockPosition p) {
        for (int depth = 1; depth <= 4; depth++)
            if (t.fluid(below(p, depth)) == Fluid.LAVA) return true;
        return false;
    }

    private static boolean corridorHasFluid(Terrain t, MineTunnelGeometry.Slice slice) {
        for (BlockPosition p : slice.navigationCoreBlocks()) {
            Fluid state = t.fluid(p);
            if (state == Fluid.WATER || state == Fluid.LAVA) return true;
        }
        return false;
    }

    private static boolean safeLanding(Terrain t, MineTunnelGeometry.Slice slice) {
        int y = slice.floorCenter().y();
        Set<String> columns = new HashSet<>();
        int safe = 0;
        for (var p : slice.navigationCoreBlocks()) {
            if (p.y() != y || !columns.add(p.x() + ":" + p.z())) continue;
            if (t.block(p) != Block.UNKNOWN && t.block(below(p,1)) == Block.SOLID
                && t.fluid(p) == Fluid.NONE) safe++;
        }
        return !columns.isEmpty() && safe >= Math.min(3, columns.size());
    }

    public static boolean levelAcross(List<MineTunnelGeometry.Slice> slices, int from, int to) {
        if (slices == null || from < 0 || from > to || to >= slices.size()) return false;
        int y = slices.get(from).floorCenter().y();
        for (int i = from + 1; i <= to; i++)
            if (slices.get(i).floorCenter().y() != y) return false;
        return true;
    }
}
