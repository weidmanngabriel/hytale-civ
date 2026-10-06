package dev.civilizations.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Hytale-independent Layer-4 planner for a branching mine network.
 *
 * <p>Layer 2 remains authoritative for a single tunnel path and Layer 3 for voxel geometry. This
 * planner composes those layers into a network, decides where nested branches may start and rejects
 * candidates that violate spacing unless a rare intentional connection is accepted.</p>
 */
public final class MineNetworkGrowthPlanner {

    public static final double MAIN_BRANCH_CHANCE = 0.15;
    public static final double BRANCH_BRANCH_CHANCE = 0.30;
    public static final int MIN_BRANCH_START_SPACING_BLOCKS = 18;
    public static final int MIN_ROCK_GAP_BLOCKS = 5;
    public static final double INTENTIONAL_CROSSING_CHANCE = 0.05;
    public static final double INITIAL_CONTINUATION_CHANCE = 0.95;
    public static final double CONTINUATION_CHANCE_DROP = 0.04;
    public static final double MIN_CONTINUATION_CHANCE = 0.20;

    private static final int BRANCH_OPPORTUNITY_SPACING = 8;
    private static final int BRANCH_MIN_LENGTH = 16;
    private static final int BRANCH_LENGTH_INCREMENT = 8;
    private static final int PARENT_CONNECTION_EXEMPT_POINTS = 18;
    private static final long MAIN_TUNNEL_ID_SALT = 0x243F6A8885A308D3L;
    private static final long CHILD_ID_SALT = 0x13198A2E03707344L;
    private static final long PATH_SEED_SALT = 0xA4093822299F31D0L;

    private MineNetworkGrowthPlanner() {
    }

    public static Plan plan(
        UUID mineId,
        BlockPosition origin,
        MineHeading initialHeading,
        int mainLengthBlocks,
        int maxTunnels,
        long seed
    ) {
        if (mineId == null || origin == null || initialHeading == null) {
            throw new IllegalArgumentException("Mine growth inputs must not be null.");
        }
        if (mainLengthBlocks <= 0) throw new IllegalArgumentException("Main tunnel length must be positive.");
        if (maxTunnels <= 0) throw new IllegalArgumentException("Tunnel planning budget must be positive.");

        UUID mainTunnelId = deterministicUuid(seed ^ MAIN_TUNNEL_ID_SALT, 0);
        MineNetwork network = MineNetwork.create(mineId, mainTunnelId, origin);
        MineTunnelPath mainPath = MinePathPlanner.plan(
            MineTunnel.Kind.MAIN,
            origin,
            origin,
            initialHeading,
            mainLengthBlocks,
            seed ^ PATH_SEED_SALT
        );
        MineTunnelGeometry mainGeometry = MineTunnelVoxelizer.voxelize(mainPath);

        Map<UUID, PlannedTunnel> planned = new HashMap<>();
        PlannedTunnel main = new PlannedTunnel(network.mainTunnel(), mainPath, mainGeometry, false);
        planned.put(mainTunnelId, main);

        ArrayDeque<UUID> scanQueue = new ArrayDeque<>();
        scanQueue.add(mainTunnelId);
        ArrayList<BlockPosition> branchStarts = new ArrayList<>();
        SplittableRandom random = new SplittableRandom(seed);
        int childSequence = 1;

        while (!scanQueue.isEmpty() && planned.size() < maxTunnels) {
            UUID parentId = scanQueue.removeFirst();
            PlannedTunnel parent = planned.get(parentId);
            List<MinePathPoint> points = parent.path().points();
            double branchChance = branchChance(parent.tunnel().branchDepth());

            for (int i = BRANCH_OPPORTUNITY_SPACING;
                 i < points.size() - BRANCH_OPPORTUNITY_SPACING && planned.size() < maxTunnels;
                 i += BRANCH_OPPORTUNITY_SPACING) {
                MinePathPoint point = points.get(i);
                BlockPosition start = rounded(point);
                if (!farEnoughFromBranchStarts(start, branchStarts)) continue;
                if (random.nextDouble() >= branchChance) continue;

                MineHeading parentHeading = nearestHeading(point.tangentAngleDegrees());
                MineHeading branchHeading = random.nextBoolean() ? parentHeading.left45() : parentHeading.right45();
                int branchLength = sampleBranchLength(random);
                long pathSeed = random.nextLong();
                UUID childId = deterministicUuid(seed ^ CHILD_ID_SALT, childSequence++);

                MineTunnelPath candidatePath = MinePathPlanner.plan(
                    MineTunnel.Kind.BRANCH,
                    start,
                    origin,
                    branchHeading,
                    branchLength,
                    pathSeed
                );
                MineTunnelGeometry candidateGeometry = MineTunnelVoxelizer.voxelize(candidatePath);
                CollisionResult collision = collisionResult(parentId, candidatePath, planned);
                boolean intentionalCrossing = false;
                if (collision.collides()) {
                    if (!collision.onlyParentConnection() && random.nextDouble() < INTENTIONAL_CROSSING_CHANCE) {
                        intentionalCrossing = true;
                    } else {
                        continue;
                    }
                }

                MineTunnel child = new MineTunnel(
                    childId,
                    MineTunnel.Kind.BRANCH,
                    parentId,
                    parent.tunnel().branchDepth() + 1,
                    start,
                    List.of()
                );
                network = network.withTunnel(child);
                planned.put(childId, new PlannedTunnel(child, candidatePath, candidateGeometry, intentionalCrossing));
                branchStarts.add(start);
                scanQueue.addLast(childId);
            }
        }

        List<PlannedTunnel> ordered = network.tunnels().stream().map(t -> planned.get(t.id())).toList();
        return new Plan(network, ordered, seed, maxTunnels);
    }

    static double branchChance(int parentDepth) {
        if (parentDepth == 0) return MAIN_BRANCH_CHANCE;
        return BRANCH_BRANCH_CHANCE * Math.pow(0.65, Math.max(0, parentDepth - 1));
    }

    static int sampleBranchLength(SplittableRandom random) {
        int length = BRANCH_MIN_LENGTH;
        double continuation = INITIAL_CONTINUATION_CHANCE;
        while (random.nextDouble() < continuation) {
            length += BRANCH_LENGTH_INCREMENT;
            continuation = Math.max(MIN_CONTINUATION_CHANCE, continuation - CONTINUATION_CHANCE_DROP);
            if (length >= MinePathPlanner.FOOTPRINT_SIZE_BLOCKS) break;
        }
        return length;
    }

    private static boolean farEnoughFromBranchStarts(BlockPosition candidate, List<BlockPosition> existing) {
        double minimumSquared = (double) MIN_BRANCH_START_SPACING_BLOCKS * MIN_BRANCH_START_SPACING_BLOCKS;
        for (BlockPosition start : existing) {
            if (distanceSquared(candidate, start) < minimumSquared) return false;
        }
        return true;
    }

    private static CollisionResult collisionResult(
        UUID parentId,
        MineTunnelPath candidate,
        Map<UUID, PlannedTunnel> existing
    ) {
        boolean collision = false;
        boolean onlyParentConnection = true;
        List<MinePathPoint> candidatePoints = candidate.points();
        for (int candidateIndex = 0; candidateIndex < candidatePoints.size(); candidateIndex++) {
            MinePathPoint candidatePoint = candidatePoints.get(candidateIndex);
            for (PlannedTunnel other : existing.values()) {
                boolean parent = other.tunnel().id().equals(parentId);
                for (MinePathPoint otherPoint : other.path().points()) {
                    double allowed = candidatePoint.width() / 2.0 + otherPoint.width() / 2.0 + MIN_ROCK_GAP_BLOCKS;
                    if (pointDistanceSquared(candidatePoint, otherPoint) >= allowed * allowed) continue;
                    if (parent && candidateIndex <= PARENT_CONNECTION_EXEMPT_POINTS) continue;
                    collision = true;
                    if (!parent) onlyParentConnection = false;
                }
            }
        }
        return new CollisionResult(collision, collision && onlyParentConnection);
    }

    private static BlockPosition rounded(MinePathPoint point) {
        return new BlockPosition(
            (int) Math.round(point.x()),
            (int) Math.round(point.y()),
            (int) Math.round(point.z())
        );
    }

    private static MineHeading nearestHeading(double angleDegrees) {
        double normalized = ((angleDegrees % 360.0) + 360.0) % 360.0;
        MineHeading best = MineHeading.EAST;
        double bestDelta = Double.MAX_VALUE;
        for (MineHeading heading : MineHeading.values()) {
            double headingAngle = ((heading.angleDegrees() % 360.0) + 360.0) % 360.0;
            double delta = Math.abs(normalized - headingAngle);
            delta = Math.min(delta, 360.0 - delta);
            if (delta < bestDelta) {
                best = heading;
                bestDelta = delta;
            }
        }
        return best;
    }

    private static double pointDistanceSquared(MinePathPoint a, MinePathPoint b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static double distanceSquared(BlockPosition a, BlockPosition b) {
        long dx = (long) a.x() - b.x();
        long dy = (long) a.y() - b.y();
        long dz = (long) a.z() - b.z();
        return (double) dx * dx + (double) dy * dy + (double) dz * dz;
    }

    private static UUID deterministicUuid(long seed, int sequence) {
        long mixed = mix(seed + 0x9E3779B97F4A7C15L * sequence);
        return new UUID(mixed, mix(mixed ^ 0xD1B54A32D192ED03L));
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public record Plan(MineNetwork network, List<PlannedTunnel> tunnels, long seed, int planningBudget) {
        public Plan {
            if (network == null || tunnels == null) throw new IllegalArgumentException("Mine plan fields must not be null.");
            tunnels = List.copyOf(tunnels);
        }

        public PlannedTunnel mainTunnel() {
            return tunnels.stream()
                .filter(t -> t.tunnel().kind() == MineTunnel.Kind.MAIN)
                .findFirst()
                .orElseThrow();
        }
    }

    public record PlannedTunnel(
        MineTunnel tunnel,
        MineTunnelPath path,
        MineTunnelGeometry geometry,
        boolean intentionalCrossing
    ) {
        public PlannedTunnel {
            if (tunnel == null || path == null || geometry == null) {
                throw new IllegalArgumentException("Planned tunnel fields must not be null.");
            }
        }
    }

    private record CollisionResult(boolean collides, boolean onlyParentConnection) {
    }
}
