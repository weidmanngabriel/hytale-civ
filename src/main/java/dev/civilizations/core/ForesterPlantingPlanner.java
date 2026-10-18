package dev.civilizations.core;

import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Engine-independent, seeded Phase-1 natural planting strategy. */
public final class ForesterPlantingPlanner {
    public static final int PHASE_ONE_RADIUS = 24;
    public static final int PHASE_ONE_SPACING = 4;
    private static final int ATTEMPTS = 64;
    private final BlockPosition home;
    private final int radius;
    private final int spacing;
    private final Random random;

    public ForesterPlantingPlanner(BlockPosition home, int radius, int spacing, long seed) {
        this.home = Objects.requireNonNull(home, "home");
        if (radius < 1 || spacing < 1) throw new IllegalArgumentException("invalid forest radius/spacing");
        this.radius = radius;
        this.spacing = spacing;
        this.random = new Random(seed);
    }

    /** True three-dimensional sphere, including its boundary. Uses long arithmetic to avoid overflow. */
    public boolean contains(BlockPosition position) {
        Objects.requireNonNull(position, "position");
        long dx = (long) position.x() - home.x();
        long dy = (long) position.y() - home.y();
        long dz = (long) position.z() - home.z();
        return dx * dx + dy * dy + dz * dz <= (long) radius * radius;
    }

    /**
     * The engine supplies the highest open-sky standing/planting location for each X/Z column.
     * A null result means that the column is not a plantable surface. Native soil, trees,
     * protected volumes and fluid rules are never inferred from generic Core block names.
     */
    public BlockPosition chooseSurface(BlockPosition current, Set<BlockPosition> occupied,
                                       Set<BlockPosition> reserved,
                                       Function<BlockPosition, BlockPosition> resolveSurface,
                                       Predicate<BlockPosition> valid) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(occupied, "occupied");
        Objects.requireNonNull(reserved, "reserved");
        Objects.requireNonNull(resolveSurface, "resolveSurface");
        Objects.requireNonNull(valid, "valid");
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int localRadius = attempt < 48 ? Math.min(radius, 8) : radius;
            int x = current.x() + random.nextInt(2 * localRadius + 1) - localRadius;
            int z = current.z() + random.nextInt(2 * localRadius + 1) - localRadius;
            BlockPosition candidate = resolveSurface.apply(new BlockPosition(x, home.y(), z));
            if (candidate == null || !contains(candidate) || reserved.contains(candidate) || !valid.test(candidate))
                continue;
            boolean spaced = true;
            for (BlockPosition tree : occupied) {
                long dx = (long) x - tree.x(), dz = (long) z - tree.z();
                if (dx * dx + dz * dz < (long) spacing * spacing) {
                    spaced = false;
                    break;
                }
            }
            if (spaced) return candidate;
        }
        return null;
    }

    /** Compatibility for the existing flat-world headless scenarios. */
    public BlockPosition choose(BlockPosition current, Set<BlockPosition> occupied,
                                Set<BlockPosition> reserved, Predicate<BlockPosition> valid) {
        return chooseSurface(current, occupied, reserved,
            column -> new BlockPosition(column.x(), home.y(), column.z()), valid);
    }
}
