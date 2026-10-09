package dev.civilizations.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic, level dwarven mine grid. Unlike the human mine, there is no lateral drift,
 * organic voxel noise, or random turning. The existing mine task/runtime model is reused.
 */
public final class DwarvenMinePlanner {
    public static final int CROSSING_SPACING = 32;
    public static final int SIDE_LENGTH = 32;
    public static final int MAIN_WIDTH = 7;
    public static final int MAIN_HEIGHT = 6;
    public static final int SIDE_WIDTH = 5;
    public static final int SIDE_HEIGHT = 5;
    private static final int NAV_HEIGHT = 3;

    private DwarvenMinePlanner() {
    }

    public static MineNetworkGrowthPlanner.Plan plan(
        UUID mineId, BlockPosition origin, MineHeading entranceHeading,
        int mainLengthBlocks, int maxTunnels, long seed
    ) {
        if (mineId == null || origin == null || entranceHeading == null
            || mainLengthBlocks < 2 || maxTunnels < 1) {
            throw new IllegalArgumentException("Invalid dwarven mine planning inputs");
        }
        MineHeading forward = cardinal(entranceHeading);
        UUID mainId = tunnelId(mineId, "main");
        MineNetwork network = MineNetwork.create(mineId, mainId, origin);
        ArrayList<MineNetworkGrowthPlanner.PlannedTunnel> tunnels = new ArrayList<>();
        tunnels.add(straight(network.mainTunnel(), forward, mainLengthBlocks, seed));

        MineHeading left = forward.left45().left45();
        MineHeading right = forward.right45().right45();
        for (int at = CROSSING_SPACING; at < mainLengthBlocks - CROSSING_SPACING / 2
            && tunnels.size() < maxTunnels; at += CROSSING_SPACING) {
            BlockPosition crossing = offset(origin, forward, at);
            // Both perpendicular branches share the main-tunnel opening, forming a true
            // four-way junction instead of a visually crossing but disconnected corridor.
            for (MineHeading side : new MineHeading[]{left, right}) {
                if (tunnels.size() >= maxTunnels) break;
                UUID id = tunnelId(mineId, "crossing:" + at + ":" + side);
                MineTunnel branch = new MineTunnel(id, MineTunnel.Kind.BRANCH, mainId, 1, crossing);
                network = network.withTunnel(branch);
                tunnels.add(straight(branch, side, SIDE_LENGTH, seed ^ id.getMostSignificantBits()));
            }
        }
        return new MineNetworkGrowthPlanner.Plan(network, tunnels, seed, maxTunnels);
    }

    /** Fixed cardinal directions even if the entrance prefab faces diagonally. */
    public static MineHeading cardinal(MineHeading heading) {
        double x = heading.unitX();
        double z = heading.unitZ();
        return Math.abs(x) >= Math.abs(z)
            ? (x >= 0 ? MineHeading.EAST : MineHeading.WEST)
            : (z >= 0 ? MineHeading.SOUTH : MineHeading.NORTH);
    }

    private static MineNetworkGrowthPlanner.PlannedTunnel straight(
        MineTunnel tunnel, MineHeading heading, int length, long seed
    ) {
        int width = tunnel.kind() == MineTunnel.Kind.MAIN ? MAIN_WIDTH : SIDE_WIDTH;
        int height = tunnel.kind() == MineTunnel.Kind.MAIN ? MAIN_HEIGHT : SIDE_HEIGHT;
        ArrayList<MinePathPoint> points = new ArrayList<>();
        ArrayList<MineTunnelGeometry.Slice> slices = new ArrayList<>();
        Set<BlockPosition> excavation = new LinkedHashSet<>();
        Set<BlockPosition> navigation = new LinkedHashSet<>();
        int dx = (int) Math.round(heading.unitX());
        int dz = (int) Math.round(heading.unitZ());

        for (int index = 0; index < length; index++) {
            BlockPosition center = new BlockPosition(
                tunnel.origin().x() + dx * index,
                tunnel.origin().y(),
                tunnel.origin().z() + dz * index
            );
            points.add(new MinePathPoint(index, center.x(), center.y(), center.z(),
                width, height, heading, heading.angleDegrees(), 0));

            Set<BlockPosition> sliceExcavation = new LinkedHashSet<>();
            Set<BlockPosition> sliceNavigation = new LinkedHashSet<>();
            for (int side = -width / 2; side <= width / 2; side++) {
                int x = center.x() - dz * side;
                int z = center.z() + dx * side;
                for (int y = 0; y < height; y++) {
                    sliceExcavation.add(new BlockPosition(x, center.y() + y, z));
                }
            }
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    for (int y = 0; y < NAV_HEIGHT; y++) {
                        sliceNavigation.add(new BlockPosition(
                            center.x() + x, center.y() + y, center.z() + z
                        ));
                    }
                }
            }
            sliceExcavation.addAll(sliceNavigation);
            excavation.addAll(sliceExcavation);
            navigation.addAll(sliceNavigation);
            slices.add(new MineTunnelGeometry.Slice(
                index, center, width, height, sliceExcavation, sliceNavigation
            ));
        }
        MineFormPhase phase = new MineFormPhase(
            0, 0, length, heading, heading,
            width, width, height, height, 0, 0, 0
        );
        MineTunnelPath path = new MineTunnelPath(tunnel.kind(), seed, points, List.of(phase));
        MineTunnelGeometry geometry = new MineTunnelGeometry(
            tunnel.kind(), seed, slices, excavation, navigation, List.of()
        );
        return new MineNetworkGrowthPlanner.PlannedTunnel(tunnel, path, geometry, false);
    }

    private static BlockPosition offset(BlockPosition origin, MineHeading heading, int distance) {
        return new BlockPosition(
            origin.x() + (int) Math.round(heading.unitX()) * distance,
            origin.y(),
            origin.z() + (int) Math.round(heading.unitZ()) * distance
        );
    }

    private static UUID tunnelId(UUID mineId, String suffix) {
        return UUID.nameUUIDFromBytes(
            ("civ-dwarf-mine-v1:" + mineId + ":" + suffix).getBytes(StandardCharsets.UTF_8)
        );
    }
}
