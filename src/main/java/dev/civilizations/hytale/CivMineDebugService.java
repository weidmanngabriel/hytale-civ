package dev.civilizations.hytale;

import com.hypixel.hytale.protocol.packets.player.AddOrUpdateTriggerVolumeDisplay;
import com.hypixel.hytale.protocol.packets.player.RemoveTriggerVolumeDisplay;
import com.hypixel.hytale.protocol.packets.player.TriggerVolumeDisplayEntry;
import com.hypixel.hytale.protocol.packets.player.TriggerVolumeShapeType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.MineNetwork;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineTunnelGeometry;
import dev.civilizations.core.MineWorkFront;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only, player-local visualization of the current Civ mine network. */
public final class CivMineDebugService {

    private static final String MINE_BUILDING = "mine";
    private static final double MAX_SELECTION_DISTANCE = 128.0;
    private static final float FRONT_OPACITY = 0.26f;
    private static final Vector3f COMPLETE_COLOR = new Vector3f(0.25f, 0.55f, 1.0f);
    private static final Vector3f ACTIVE_COLOR = new Vector3f(1.0f, 0.85f, 0.15f);
    private static final Vector3f OPEN_COLOR = new Vector3f(1.0f, 0.50f, 0.12f);
    private static final Vector3f MAIN_COLOR = new Vector3f(0.20f, 0.95f, 0.95f);
    private static final Vector3f BOUNDS_COLOR = new Vector3f(0.70f, 0.70f, 0.70f);

    private final BuildingPlacementRegistry buildingRegistry;
    private final MineTunnelRegistry tunnelRegistry;
    private final Map<UUID, Set<String>> displayedIdsByPlayer = new ConcurrentHashMap<>();

    public CivMineDebugService(
        BuildingPlacementRegistry buildingRegistry,
        MineTunnelRegistry tunnelRegistry
    ) {
        this.buildingRegistry = buildingRegistry;
        this.tunnelRegistry = tunnelRegistry;
    }

    public MineDebugSnapshot snapshot(UUID worldId, Vector3dc playerPosition) {
        BuildingPlacementRegistry.BuildingInstance mine = nearestMine(worldId, playerPosition);
        if (mine == null) return null;

        MineNetwork network = tunnelRegistry.networkForMine(worldId, mine.id());
        Map<UUID, MineTunnelGeometry> geometries = tunnelRegistry.geometriesForMine(worldId, mine.id());
        List<TunnelDebugSnapshot> tunnels = new ArrayList<>();
        if (network != null) {
            for (MineTunnel tunnel : network.tunnels()) {
                MineWorkFront front = network.workFronts().stream()
                    .filter(candidate -> candidate.tunnelId().equals(tunnel.id()))
                    .findFirst()
                    .orElse(null);
                tunnels.add(new TunnelDebugSnapshot(tunnel, front, geometries.get(tunnel.id())));
            }
        }

        double distance = horizontalDistance(playerPosition, mine.bounds());
        return new MineDebugSnapshot(mine, distance, tunnels);
    }

    public ShowResult show(PlayerRef playerRef, MineDebugSnapshot snapshot, boolean includeBounds) {
        if (playerRef == null || snapshot == null || playerRef.getPacketHandler() == null) {
            return new ShowResult(0, includeBounds);
        }
        hide(playerRef);

        Set<String> ids = new HashSet<>();
        for (TunnelDebugSnapshot tunnel : snapshot.tunnels()) {
            MineTunnelGeometry.Slice slice = currentSlice(tunnel);
            if (slice == null) continue;
            String id = id(playerRef, "front:" + tunnel.tunnel().id());
            TriggerVolumeDisplayEntry entry = frontEntry(id, tunnel, slice);
            playerRef.getPacketHandler().write(new AddOrUpdateTriggerVolumeDisplay(id, entry));
            ids.add(id);
        }

        if (includeBounds) {
            String id = id(playerRef, "bounds:" + snapshot.mine().id());
            TriggerVolumeDisplayEntry entry = mineBoundsEntry(id, snapshot.mine().bounds());
            playerRef.getPacketHandler().write(new AddOrUpdateTriggerVolumeDisplay(id, entry));
            ids.add(id);
        }

        displayedIdsByPlayer.put(playerRef.getUuid(), ids);
        return new ShowResult(ids.size(), includeBounds);
    }

    public int hide(PlayerRef playerRef) {
        if (playerRef == null || playerRef.getPacketHandler() == null) return 0;
        Set<String> ids = displayedIdsByPlayer.remove(playerRef.getUuid());
        if (ids == null || ids.isEmpty()) return 0;
        for (String id : ids) {
            playerRef.getPacketHandler().write(new RemoveTriggerVolumeDisplay(id));
        }
        return ids.size();
    }

    private BuildingPlacementRegistry.BuildingInstance nearestMine(UUID worldId, Vector3dc position) {
        if (worldId == null || position == null) return null;
        BuildingPlacementRegistry.BuildingInstance best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (BuildingPlacementRegistry.BuildingInstance building : buildingRegistry.buildings(worldId)) {
            if (!MINE_BUILDING.equals(building.buildingType()) || building.bounds() == null) continue;
            double distance = horizontalDistance(position, building.bounds());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = building;
            }
        }
        return bestDistance <= MAX_SELECTION_DISTANCE ? best : null;
    }

    private static MineTunnelGeometry.Slice currentSlice(TunnelDebugSnapshot debug) {
        if (debug.geometry() == null || debug.front() == null) return null;
        MineTunnelGeometry.Slice closest = null;
        long bestDistance = Long.MAX_VALUE;
        for (MineTunnelGeometry.Slice slice : debug.geometry().slices()) {
            long distance = distanceSquared(slice.floorCenter(), debug.front().position());
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = slice;
            }
        }
        return closest;
    }

    private static double horizontalDistance(Vector3dc position, BuildingBounds bounds) {
        double centerX = (bounds.minX() + bounds.maxX()) * 0.5;
        double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5;
        return Math.hypot(position.x() - centerX, position.z() - centerZ);
    }

    private static TriggerVolumeDisplayEntry frontEntry(
        String id,
        TunnelDebugSnapshot debug,
        MineTunnelGeometry.Slice slice
    ) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPosition block : slice.excavationBlocks()) {
            minX = Math.min(minX, block.x());
            minY = Math.min(minY, block.y());
            minZ = Math.min(minZ, block.z());
            maxX = Math.max(maxX, block.x());
            maxY = Math.max(maxY, block.y());
            maxZ = Math.max(maxZ, block.z());
        }

        MineWorkFront.State state = debug.front().state();
        Vector3f color = state == MineWorkFront.State.ACTIVE ? ACTIVE_COLOR
            : state == MineWorkFront.State.OPEN ? OPEN_COLOR
            : debug.tunnel().kind() == MineTunnel.Kind.MAIN ? MAIN_COLOR
            : COMPLETE_COLOR;
        String label = debug.tunnel().kind()
            + " · depth=" + debug.tunnel().branchDepth()
            + " · " + state;
        return box(id, minX, minY, minZ, maxX + 1.0f, maxY + 1.0f, maxZ + 1.0f,
            color, FRONT_OPACITY, label);
    }

    private static TriggerVolumeDisplayEntry mineBoundsEntry(String id, BuildingBounds mineBounds) {
        double centerX = (mineBounds.minX() + mineBounds.maxX()) * 0.5;
        double centerZ = (mineBounds.minZ() + mineBounds.maxZ()) * 0.5;
        double minY = mineBounds.minY() + 0.05;
        return box(
            id,
            (float) (centerX - 250.0),
            (float) minY,
            (float) (centerZ - 250.0),
            (float) (centerX + 250.0),
            (float) (minY + 0.20),
            (float) (centerZ + 250.0),
            BOUNDS_COLOR,
            0.12f,
            "Mine design area · 500x500 (debug only)"
        );
    }

    private static TriggerVolumeDisplayEntry box(
        String id,
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ,
        Vector3f color,
        float opacity,
        String label
    ) {
        TriggerVolumeDisplayEntry entry = new TriggerVolumeDisplayEntry();
        entry.volumeId = id;
        entry.shapeType = TriggerVolumeShapeType.Box;
        entry.position = new Vector3f(
            (minX + maxX) * 0.5f,
            (minY + maxY) * 0.5f,
            (minZ + maxZ) * 0.5f
        );
        entry.dimensions = new Vector3f(
            Math.max(0.05f, (maxX - minX) * 0.5f),
            Math.max(0.05f, (maxY - minY) * 0.5f),
            Math.max(0.05f, (maxZ - minZ) * 0.5f)
        );
        entry.color = new Vector3f(color);
        entry.opacity = opacity;
        entry.name = label;
        return entry;
    }

    private static long distanceSquared(BlockPosition first, BlockPosition second) {
        long dx = (long) first.x() - second.x();
        long dy = (long) first.y() - second.y();
        long dz = (long) first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static String id(PlayerRef playerRef, String suffix) {
        return "civ:mine-debug:" + playerRef.getUuid() + ":" + suffix;
    }

    public record MineDebugSnapshot(
        BuildingPlacementRegistry.BuildingInstance mine,
        double distanceBlocks,
        List<TunnelDebugSnapshot> tunnels
    ) {
        public MineDebugSnapshot {
            tunnels = List.copyOf(tunnels == null ? new ArrayList<>() : tunnels);
        }

        public long activeFrontCount() {
            return tunnels.stream().filter(TunnelDebugSnapshot::active).count();
        }

        public long openFrontCount() {
            return tunnels.stream().filter(TunnelDebugSnapshot::open).count();
        }
    }

    public record TunnelDebugSnapshot(
        MineTunnel tunnel,
        MineWorkFront front,
        MineTunnelGeometry geometry
    ) {
        public boolean active() {
            return front != null && front.state() == MineWorkFront.State.ACTIVE;
        }

        public boolean open() {
            return front != null && front.state() == MineWorkFront.State.OPEN;
        }
    }

    public record ShowResult(int displayedEntryCount, boolean boundsIncluded) {
    }
}
