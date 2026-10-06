package dev.civilizations.core;

import java.util.ArrayList;
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
            addNavigationCore(slice.navigationCore, point, floorY, width, height);
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

        connectNavigationCore(path, mutableSlices);
        addOrganicClusters(path, mutableSlices);

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

    private static void addNavigationCore(
        Set<BlockPosition> result,
        MinePathPoint point,
        int floorY,
        int width,
        int height
    ) {
        int coreWidth = Math.min(NAVIGATION_CORE_WIDTH, width);
        int coreHeight = Math.min(NAVIGATION_CORE_HEIGHT, height);
        double radians = Math.toRadians(point.tangentAngleDegrees());
        double sideX = -Math.sin(radians);
        double sideZ = Math.cos(radians);
        double start = -(coreWidth - 1) / 2.0;

        for (int w = 0; w < coreWidth; w++) {
            double offset = start + w;
            int x = (int) Math.round(point.x() + sideX * offset);
            int z = (int) Math.round(point.z() + sideZ * offset);
            for (int y = 0; y < coreHeight; y++) {
                result.add(new BlockPosition(x, floorY + y, z));
            }
        }
    }

    /**
     * Consecutive rounded cross-sections can otherwise touch only diagonally on curves or at a Y
     * change. Fill the small integer-space gap explicitly so the trusted corridor stays connected.
     */
    private static void connectNavigationCore(MineTunnelPath path, List<MutableSlice> slices) {
        for (int i = 1; i < slices.size(); i++) {
            MutableSlice previous = slices.get(i - 1);
            MutableSlice current = slices.get(i);
            BlockPosition from = previous.floorCenter;
            BlockPosition to = current.floorCenter;
            int dx = to.x() - from.x();
            int dy = to.y() - from.y();
            int dz = to.z() - from.z();
            int count = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
            if (count == 0) continue;

            MinePathPoint point = path.points().get(i);
            double radians = Math.toRadians(point.tangentAngleDegrees());
            double sideX = -Math.sin(radians);
            double sideZ = Math.cos(radians);
            for (int step = 0; step <= count; step++) {
                double t = (double) step / count;
                double x = from.x() + dx * t;
                double y = from.y() + dy * t;
                double z = from.z() + dz * t;
                addCoreAt(current.navigationCore, x, y, z, sideX, sideZ);
            }
        }
    }

    private static void addCoreAt(
        Set<BlockPosition> result,
        double centerX,
        double floorY,
        double centerZ,
        double sideX,
        double sideZ
    ) {
        double start = -(NAVIGATION_CORE_WIDTH - 1) / 2.0;
        for (int w = 0; w < NAVIGATION_CORE_WIDTH; w++) {
            double offset = start + w;
            int x = (int) Math.round(centerX + sideX * offset);
            int z = (int) Math.round(centerZ + sideZ * offset);
            int baseY = (int) Math.round(floorY);
            for (int y = 0; y < NAVIGATION_CORE_HEIGHT; y++) {
                result.add(new BlockPosition(x, baseY + y, z));
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
