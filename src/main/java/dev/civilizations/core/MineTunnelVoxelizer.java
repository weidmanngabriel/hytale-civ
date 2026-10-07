package dev.civilizations.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/** Turns a Layer-2 {@link MineTunnelPath} into deterministic excavation slices and voxels. */
public final class MineTunnelVoxelizer {

    private static final int NAVIGATION_CORE_WIDTH = 3;
    private static final int NAVIGATION_CORE_HEIGHT = 3;
    private static final long ORGANIC_SEED_SALT = 0x6A09E667F3BCC909L;

    private MineTunnelVoxelizer() {
    }

    public static MineTunnelGeometry voxelize(MineTunnelPath path) {
        if (path == null) throw new IllegalArgumentException("Mine tunnel path must not be null.");

        List<MutableSlice> mutableSlices = new ArrayList<>(path.points().size());
        List<MineTunnelGeometry.StepTransition> steps = new ArrayList<>();
        BlockPosition previousCenter = null;

        for (MinePathPoint point : path.points()) {
            int width = Math.max(1, (int) Math.round(point.width()));
            int height = Math.max(1, (int) Math.round(point.height()));
            int floorY = (int) Math.round(point.y());
            BlockPosition center = new BlockPosition(
                (int) Math.round(point.x()),
                floorY,
                (int) Math.round(point.z())
            );

            MutableSlice slice = new MutableSlice(point.index(), center, width, height);
            addCrossSection(slice.excavation, point, floorY, width, height);
            addNavigationCore(slice.navigationCore, center, width, height);
            slice.excavation.addAll(slice.navigationCore);
            mutableSlices.add(slice);

            if (previousCenter != null && previousCenter.y() != center.y()) {
                int delta = center.y() - previousCenter.y();
                if (Math.abs(delta) > 1) {
                    throw new IllegalStateException("Layer-2 path produced an unsafe multi-block vertical jump.");
                }
                steps.add(new MineTunnelGeometry.StepTransition(
                    point.index() - 1,
                    point.index(),
                    previousCenter,
                    center
                ));
            }
            previousCenter = center;
        }

        connectNavigationCore(mutableSlices);
        addOrganicClusters(path, mutableSlices);
        removeDisconnectedExcavation(mutableSlices);

        List<MineTunnelGeometry.Slice> slices = new ArrayList<>(mutableSlices.size());
        Set<BlockPosition> excavation = new LinkedHashSet<>();
        Set<BlockPosition> navigation = new LinkedHashSet<>();
        for (MutableSlice mutable : mutableSlices) {
            mutable.excavation.addAll(mutable.navigationCore);
            excavation.addAll(mutable.excavation);
            navigation.addAll(mutable.navigationCore);
            slices.add(new MineTunnelGeometry.Slice(
                mutable.index,
                mutable.floorCenter,
                mutable.width,
                mutable.height,
                mutable.excavation,
                mutable.navigationCore
            ));
        }

        return new MineTunnelGeometry(
            path.tunnelKind(),
            path.seed(),
            slices,
            excavation,
            navigation,
            steps
        );
    }

    private static void addCrossSection(
        Set<BlockPosition> result,
        MinePathPoint point,
        int floorY,
        int width,
        int height
    ) {
        double radians = Math.toRadians(point.tangentAngleDegrees());
        double sideX = -Math.sin(radians);
        double sideZ = Math.cos(radians);
        double start = -(width - 1) / 2.0;

        for (int w = 0; w < width; w++) {
            double offset = start + w;
            int x = (int) Math.round(point.x() + sideX * offset);
            int z = (int) Math.round(point.z() + sideZ * offset);
            for (int y = 0; y < height; y++) {
                result.add(new BlockPosition(x, floorY + y, z));
            }
        }
    }

    /**
     * The guaranteed corridor is deliberately conservative: a small axis-aligned prism around the
     * rounded centerline sample. The visible structural cross-section may rotate with the tangent,
     * but the trusted core must never become diagonally disconnected because of voxel rounding.
     */
    private static void addNavigationCore(
        Set<BlockPosition> result,
        BlockPosition center,
        int width,
        int height
    ) {
        int coreWidth = Math.min(NAVIGATION_CORE_WIDTH, width);
        int coreHeight = Math.min(NAVIGATION_CORE_HEIGHT, height);
        int minOffset = -(coreWidth / 2);
        int maxOffset = minOffset + coreWidth - 1;

        for (int xOffset = minOffset; xOffset <= maxOffset; xOffset++) {
            for (int zOffset = minOffset; zOffset <= maxOffset; zOffset++) {
                for (int y = 0; y < coreHeight; y++) {
                    result.add(new BlockPosition(
                        center.x() + xOffset,
                        center.y() + y,
                        center.z() + zOffset
                    ));
                }
            }
        }
    }

    /**
     * Rounded centerline samples may move diagonally in X/Z and may also change Y by one block.
     * Walk between their floor centers one axis at a time and stamp the trusted core at every
     * intermediate position. This creates a six-neighbor-connected corridor by construction.
     */
    private static void connectNavigationCore(List<MutableSlice> slices) {
        for (int i = 1; i < slices.size(); i++) {
            MutableSlice previous = slices.get(i - 1);
            MutableSlice current = slices.get(i);
            int x = previous.floorCenter.x();
            int y = previous.floorCenter.y();
            int z = previous.floorCenter.z();

            while (x != current.floorCenter.x()) {
                x += Integer.compare(current.floorCenter.x(), x);
                addNavigationCore(
                    current.navigationCore,
                    new BlockPosition(x, y, z),
                    current.width,
                    current.height
                );
            }
            while (z != current.floorCenter.z()) {
                z += Integer.compare(current.floorCenter.z(), z);
                addNavigationCore(
                    current.navigationCore,
                    new BlockPosition(x, y, z),
                    current.width,
                    current.height
                );
            }
            while (y != current.floorCenter.y()) {
                y += Integer.compare(current.floorCenter.y(), y);
                addNavigationCore(
                    current.navigationCore,
                    new BlockPosition(x, y, z),
                    current.width,
                    current.height
                );
            }
        }
    }

    /**
     * Adds seeded, spatially coherent wall/ceiling pockets. Each cluster spans several neighboring
     * slices, so naturalization looks carved rather than like independent block noise.
     */
    private static void addOrganicClusters(MineTunnelPath path, List<MutableSlice> slices) {
        if (slices.size() < 4) return;
        SplittableRandom random = new SplittableRandom(path.seed() ^ ORGANIC_SEED_SALT);
        int cursor = 2 + random.nextInt(3);
        while (cursor < slices.size() - 1) {
            int duration = 2 + random.nextInt(4);
            int mode = random.nextInt(3); // left wall, right wall, ceiling
            int depth = 1 + random.nextInt(2);
            int end = Math.min(slices.size(), cursor + duration);
            for (int i = cursor; i < end; i++) {
                addOrganicCutout(path.points().get(i), slices.get(i), mode, depth);
            }
            cursor = end + 3 + random.nextInt(6);
        }
    }

    private static void addOrganicCutout(
        MinePathPoint point,
        MutableSlice slice,
        int mode,
        int depth
    ) {
        double radians = Math.toRadians(point.tangentAngleDegrees());
        double sideX = -Math.sin(radians);
        double sideZ = Math.cos(radians);
        int floorY = slice.floorCenter.y();

        if (mode == 2) {
            int halfSpan = Math.max(1, slice.width / 4);
            for (int layer = 1; layer <= depth; layer++) {
                for (int offset = -halfSpan; offset <= halfSpan; offset++) {
                    int x = (int) Math.round(point.x() + sideX * offset);
                    int z = (int) Math.round(point.z() + sideZ * offset);
                    slice.excavation.add(new BlockPosition(x, floorY + slice.height - 1 + layer, z));
                }
            }
            return;
        }

        double edge = (slice.width - 1) / 2.0;
        double sign = mode == 0 ? -1.0 : 1.0;
        int minY = floorY + 1;
        int maxY = floorY + Math.max(1, slice.height - 2);
        for (int layer = 1; layer <= depth; layer++) {
            double lateral = sign * (edge + layer);
            int x = (int) Math.round(point.x() + sideX * lateral);
            int z = (int) Math.round(point.z() + sideZ * lateral);
            for (int y = minY; y <= maxY; y++) {
                slice.excavation.add(new BlockPosition(x, y, z));
            }
        }
    }

    /**
     * Organic wall/ceiling rounding may occasionally create a detached voxel island. Such a pocket
     * would be invisible gameplay-wise but produces implausible floating cavities in replay and in
     * the real excavation plan. Keep only excavation that is six-neighbor-connected to the trusted
     * navigation corridor.
     */
    private static void removeDisconnectedExcavation(List<MutableSlice> slices) {
        Set<BlockPosition> all = new HashSet<>();
        Set<BlockPosition> reachable = new HashSet<>();
        ArrayDeque<BlockPosition> queue = new ArrayDeque<>();

        for (MutableSlice slice : slices) {
            all.addAll(slice.excavation);
            all.addAll(slice.navigationCore);
            for (BlockPosition block : slice.navigationCore) {
                if (reachable.add(block)) queue.addLast(block);
            }
        }

        while (!queue.isEmpty()) {
            BlockPosition current = queue.removeFirst();
            for (BlockPosition neighbor : neighbors(current)) {
                if (all.contains(neighbor) && reachable.add(neighbor)) queue.addLast(neighbor);
            }
        }

        for (MutableSlice slice : slices) {
            slice.excavation.retainAll(reachable);
            slice.excavation.addAll(slice.navigationCore);
        }
    }

    private static List<BlockPosition> neighbors(BlockPosition position) {
        return List.of(
            new BlockPosition(position.x() + 1, position.y(), position.z()),
            new BlockPosition(position.x() - 1, position.y(), position.z()),
            new BlockPosition(position.x(), position.y() + 1, position.z()),
            new BlockPosition(position.x(), position.y() - 1, position.z()),
            new BlockPosition(position.x(), position.y(), position.z() + 1),
            new BlockPosition(position.x(), position.y(), position.z() - 1)
        );
    }

    private static final class MutableSlice {
        private final int index;
        private final BlockPosition floorCenter;
        private final int width;
        private final int height;
        private final Set<BlockPosition> excavation = new LinkedHashSet<>();
        private final Set<BlockPosition> navigationCore = new LinkedHashSet<>();

        private MutableSlice(int index, BlockPosition floorCenter, int width, int height) {
            this.index = index;
            this.floorCenter = floorCenter;
            this.width = width;
            this.height = height;
        }
    }
}
