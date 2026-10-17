package dev.civilizations.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Engine-independent, reproducible search for a forester's next planting site. */
public final class ForesterPlantingPlanner {
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

    /**
     * A bounded local random walk: nearby destinations are more likely than distant ones.
     * The adapter supplies valid standing/planting locations; reservations exclude concurrent picks.
     */
    public BlockPosition choose(BlockPosition current, Set<BlockPosition> occupied,
                                Set<BlockPosition> reserved, java.util.function.Predicate<BlockPosition> valid) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(occupied, "occupied");
        Objects.requireNonNull(reserved, "reserved");
        Objects.requireNonNull(valid, "valid");
        List<BlockPosition> candidates = new ArrayList<>();
        int near = Math.min(radius, 7);
        for (int attempt = 0; attempt < 20; attempt++) {
            int localRadius = attempt < 16 ? near : radius;
            int x = current.x() + random.nextInt(2 * localRadius + 1) - localRadius;
            int z = current.z() + random.nextInt(2 * localRadius + 1) - localRadius;
            if (Math.abs(x - home.x()) > radius || Math.abs(z - home.z()) > radius) continue;
            BlockPosition candidate = new BlockPosition(x, home.y(), z);
            if (reserved.contains(candidate) || !valid.test(candidate)) continue;
            boolean spaced = true;
            for (BlockPosition tree : occupied) {
                long dx = x - tree.x(), dz = z - tree.z();
                if (dx * dx + dz * dz < (long) spacing * spacing) { spaced = false; break; }
            }
            if (!spaced) continue;
            candidates.add(candidate);
            break;
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }
}
