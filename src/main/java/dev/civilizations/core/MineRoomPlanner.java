package dev.civilizations.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/** Deterministic Layer-6 room opportunity planner for the currently authored V1 room types. */
public final class MineRoomPlanner {

    public static final int REST_MIN_SPACING_BLOCKS = 80;
    public static final int REST_MAX_SPACING_BLOCKS = 120;
    public static final double MATERIAL_STORAGE_CHANCE = 0.30;
    public static final int MATERIAL_STORAGE_MIN_SPACING_BLOCKS = 60;
    public static final int MATERIAL_STORAGE_MAX_SPACING_BLOCKS = 80;
    public static final double TOOL_WORKSHOP_CHANCE = 0.125;
    public static final double ORE_COLLECTION_CHANCE = 0.20;
    public static final double LARGE_NATURAL_CHAMBER_CHANCE = 0.075;
    public static final double SMALL_NICHE_CHANCE = 0.25;
    public static final double SUPPORT_SUPPLY_CHANCE = 0.15;
    public static final double WATER_DRAINAGE_CHANCE = 0.05;
    public static final double LARGE_WORK_HALL_CHANCE = 0.045;

    public static final int MAX_ACTIVE_ROOMS = 2;
    public static final int ROOM_PRIORITY = 8;
    public static final int EXCAVATION_CAPACITY = 3;
    public static final int BUILD_CAPACITY = 2;

    private static final int OPPORTUNITY_SPACING = 12;
    private static final int MIN_ROOM_CENTER_SPACING = 18;
    private static final long ROOM_SEED_SALT = 0x510E527FADE682D1L;

    private MineRoomPlanner() {
    }

    public static List<MineRoom> plan(MineNetworkGrowthPlanner.Plan minePlan) {
        if (minePlan == null) throw new IllegalArgumentException("Mine room planning requires a mine plan.");
        SplittableRandom random = new SplittableRandom(minePlan.seed() ^ ROOM_SEED_SALT);
        ArrayList<MineRoom> rooms = new ArrayList<>();
        Set<BlockPosition> occupiedCenters = new HashSet<>();

        MineNetworkGrowthPlanner.PlannedTunnel main = minePlan.mainTunnel();
        planMainRooms(main, minePlan.tunnels(), random, rooms, occupiedCenters);
        for (MineNetworkGrowthPlanner.PlannedTunnel tunnel : minePlan.tunnels()) {
            if (tunnel.tunnel().kind() == MineTunnel.Kind.BRANCH) {
                planBranchRooms(tunnel, minePlan.tunnels(), random, rooms, occupiedCenters);
            }
        }
        return List.copyOf(rooms);
    }

    private static void planMainRooms(
        MineNetworkGrowthPlanner.PlannedTunnel tunnel,
        List<MineNetworkGrowthPlanner.PlannedTunnel> allTunnels,
        SplittableRandom random,
        List<MineRoom> rooms,
        Set<BlockPosition> occupiedCenters
    ) {
        int sliceCount = tunnel.geometry().slices().size();

        int restIndex = random.nextInt(REST_MIN_SPACING_BLOCKS, REST_MAX_SPACING_BLOCKS + 1);
        while (restIndex < sliceCount - 8) {
            tryAdd(tunnel, restIndex, MineRoom.Type.REST_ACCOMMODATION, random, rooms, occupiedCenters);
            restIndex += random.nextInt(REST_MIN_SPACING_BLOCKS, REST_MAX_SPACING_BLOCKS + 1);
        }

        int storageEligible = random.nextInt(
            MATERIAL_STORAGE_MIN_SPACING_BLOCKS, MATERIAL_STORAGE_MAX_SPACING_BLOCKS + 1
        );
        for (int index = storageEligible; index < sliceCount - 6; index += OPPORTUNITY_SPACING) {
            if (random.nextDouble() <= MATERIAL_STORAGE_CHANCE
                && tryAdd(tunnel, index, MineRoom.Type.MATERIAL_STORAGE, random, rooms, occupiedCenters)) {
                storageEligible = index + random.nextInt(
                    MATERIAL_STORAGE_MIN_SPACING_BLOCKS, MATERIAL_STORAGE_MAX_SPACING_BLOCKS + 1
                );
                index = storageEligible - OPPORTUNITY_SPACING;
            }
        }
    }

    private static void planBranchRooms(
        MineNetworkGrowthPlanner.PlannedTunnel tunnel,
        List<MineNetworkGrowthPlanner.PlannedTunnel> allTunnels,
        SplittableRandom random,
        List<MineRoom> rooms,
        Set<BlockPosition> occupiedCenters
    ) {
        int sliceCount = tunnel.geometry().slices().size();
        for (int index = OPPORTUNITY_SPACING; index < sliceCount - 4; index += OPPORTUNITY_SPACING) {
            if (random.nextDouble() <= SMALL_NICHE_CHANCE) {
                tryAdd(tunnel, index, MineRoom.Type.SMALL_NICHE, random, rooms, occupiedCenters);
            }
        }
    }

    private static boolean tryAdd(
        MineNetworkGrowthPlanner.PlannedTunnel tunnel,
        List<MineNetworkGrowthPlanner.PlannedTunnel> allTunnels,
        int sliceIndex,
        MineRoom.Type type,
        SplittableRandom random,
        List<MineRoom> rooms,
        Set<BlockPosition> occupiedCenters
    ) {
        List<MineTunnelGeometry.Slice> slices = tunnel.geometry().slices();
        if (sliceIndex <= 1 || sliceIndex >= slices.size() - 2) return false;
        MineTunnelGeometry.Slice slice = slices.get(sliceIndex);
        MineHeading forward = cardinalForward(slices, sliceIndex);
        MineHeading firstOutward = random.nextBoolean() ? left90(forward) : right90(forward);
        MineHeading[] sides = new MineHeading[]{firstOutward, firstOutward.opposite()};

        for (MineHeading outward : sides) {
            MineRoomGeometry.Dimensions dimensions = MineRoomGeometry.dimensions(type);
            int centerDistance = Math.max(2, slice.widthBlocks() / 2) + 2 + dimensions.depth() / 2;
            BlockPosition center = offset(slice.floorCenter(), outward, centerDistance);
            if (!farEnough(center, occupiedCenters)) continue;

            MineRoom candidate = new MineRoom(
                roomId(tunnel.tunnel().id(), sliceIndex, type, outward),
                tunnel.tunnel().id(),
                type,
                center,
                outward,
                sliceIndex,
                MineRoom.State.PLANNED,
                0,
                Set.of()
            );
            MineRoomGeometry geometry = MineRoomGeometry.generate(candidate, tunnel.geometry());
            if (collidesWithTunnelBody(geometry, tunnel.geometry(), sliceIndex)) continue;
            if (collidesWithUnrelatedTunnel(geometry, tunnel.tunnel().id(), allTunnels)) continue;

            rooms.add(candidate);
            occupiedCenters.add(center);
            return true;
        }
        return false;
    }

    private static boolean collidesWithTunnelBody(
        MineRoomGeometry room,
        MineTunnelGeometry parent,
        int attachmentSliceIndex
    ) {
        // The short access throat is allowed to meet the parent passage. The room body must not
        // punch back into distant parts of that same tunnel.
        int exemptRadius = 3;
        for (int index = 0; index < parent.slices().size(); index++) {
            if (Math.abs(index - attachmentSliceIndex) <= exemptRadius) continue;
            for (BlockPosition block : parent.slices().get(index).excavationBlocks()) {
                if (room.excavationBlocks().contains(block)) return true;
            }
        }
        return false;
    }

    private static boolean collidesWithUnrelatedTunnel(
        MineRoomGeometry room,
        UUID parentTunnelId,
        List<MineNetworkGrowthPlanner.PlannedTunnel> tunnels
    ) {
        for (MineNetworkGrowthPlanner.PlannedTunnel tunnel : tunnels) {
            if (tunnel.tunnel().id().equals(parentTunnelId)) continue;
            for (BlockPosition block : tunnel.geometry().excavationBlocks()) {
                if (room.excavationBlocks().contains(block)) return true;
            }
        }
        return false;
    }

    private static boolean farEnough(BlockPosition candidate, Set<BlockPosition> existing) {
        double minimumSquared = (double) MIN_ROOM_CENTER_SPACING * MIN_ROOM_CENTER_SPACING;
        for (BlockPosition center : existing) {
            long dx = (long) candidate.x() - center.x();
            long dy = (long) candidate.y() - center.y();
            long dz = (long) candidate.z() - center.z();
            if ((double) dx * dx + (double) dy * dy + (double) dz * dz < minimumSquared) return false;
        }
        return true;
    }

    private static MineHeading cardinalForward(List<MineTunnelGeometry.Slice> slices, int index) {
        BlockPosition before = slices.get(Math.max(0, index - 1)).floorCenter();
        BlockPosition after = slices.get(Math.min(slices.size() - 1, index + 1)).floorCenter();
        int dx = after.x() - before.x();
        int dz = after.z() - before.z();
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? MineHeading.EAST : MineHeading.WEST;
        return dz >= 0 ? MineHeading.SOUTH : MineHeading.NORTH;
    }

    private static MineHeading left90(MineHeading heading) {
        return heading.left45().left45();
    }

    private static MineHeading right90(MineHeading heading) {
        return heading.right45().right45();
    }

    private static BlockPosition offset(BlockPosition origin, MineHeading direction, int distance) {
        return new BlockPosition(
            origin.x() + (int) Math.round(direction.unitX()) * distance,
            origin.y(),
            origin.z() + (int) Math.round(direction.unitZ()) * distance
        );
    }

    private static UUID roomId(
        UUID tunnelId,
        int sliceIndex,
        MineRoom.Type type,
        MineHeading outward
    ) {
        String key = "civ-mine-room:" + tunnelId + ":" + sliceIndex + ":" + type + ":" + outward;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
