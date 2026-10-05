package dev.civilizations.hytale;

import com.hypixel.hytale.protocol.packets.player.AddOrUpdateTriggerVolumeDisplay;
import com.hypixel.hytale.protocol.packets.player.RemoveTriggerVolumeDisplay;
import com.hypixel.hytale.protocol.packets.player.TriggerVolumeDisplayEntry;
import com.hypixel.hytale.protocol.packets.player.TriggerVolumeShapeType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineTuning;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only, player-local visualization of the currently persisted Civ mine state. */
public final class CivMineDebugService {

    private static final String MINE_BUILDING = "mine";
    private static final double MAX_SELECTION_DISTANCE = 128.0;
    private static final float SEGMENT_OPACITY = 0.22f;
    private static final Vector3f COMPLETE_COLOR = new Vector3f(0.25f, 0.55f, 1.0f);
    private static final Vector3f ACTIVE_COLOR = new Vector3f(1.0f, 0.85f, 0.15f);
    private static final Vector3f OPEN_COLOR = new Vector3f(1.0f, 0.50f, 0.12f);
    private static final Vector3f ROOT_COLOR = new Vector3f(0.20f, 0.95f, 0.95f);
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

        List<MineSegment> segments = tunnelRegistry.segmentsForMine(worldId, mine.id());
        Map<UUID, MineSegment> byId = new HashMap<>();
        for (MineSegment segment : segments) byId.put(segment.id(), segment);

        List<SegmentDebugSnapshot> projected = segments.stream()
            .map(segment -> new SegmentDebugSnapshot(
                segment,
                branchLevel(segment, byId),
                segment.parentId() == null,
                segment.status() == MineSegment.Status.MINING,
                segment.status() == MineSegment.Status.RESERVED
                    || segment.status() == MineSegment.Status.MINING
            ))
            .toList();

        double distance = horizontalDistance(playerPosition, mine.bounds());
        return new MineDebugSnapshot(mine, distance, projected);
    }

    public ShowResult show(PlayerRef playerRef, MineDebugSnapshot snapshot, boolean includeBounds) {
        if (playerRef == null || snapshot == null || playerRef.getPacketHandler() == null) {
            return new ShowResult(0, includeBounds);
        }
        hide(playerRef);

        Set<String> ids = new HashSet<>();
        for (SegmentDebugSnapshot segment : snapshot.segments()) {
            String id = id(playerRef, "segment:" + segment.segment().id());
            TriggerVolumeDisplayEntry entry = segmentEntry(id, segment);
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

    static int branchLevel(MineSegment segment, Map<UUID, MineSegment> byId) {
        int level = 0;
        UUID parentId = segment.parentId();
        Set<UUID> visited = new HashSet<>();
        while (parentId != null && visited.add(parentId)) {
            MineSegment parent = byId.get(parentId);
            if (parent == null) break;
            level++;
            parentId = parent.parentId();
        }
        return level;
    }

    private static double horizontalDistance(Vector3dc position, BuildingBounds bounds) {
        double centerX = (bounds.minX() + bounds.maxX()) * 0.5;
        double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5;
        return Math.hypot(position.x() - centerX, position.z() - centerZ);
    }

    private static TriggerVolumeDisplayEntry segmentEntry(String id, SegmentDebugSnapshot debug) {
        MineSegment segment = debug.segment();
        MineSegment.HorizontalBounds horizontal = segment.horizontalBounds();
        float minX = horizontal.minX();
        float maxX = horizontal.maxX() + 1.0f;
        float minZ = horizontal.minZ();
        float maxZ = horizontal.maxZ() + 1.0f;
        float minY = segment.start().y();
        float maxY = minY + MineTuning.TUNNEL_HEIGHT_BLOCKS;

        Vector3f color = debug.active() ? ACTIVE_COLOR
            : debug.open() ? OPEN_COLOR
            : debug.root() ? ROOT_COLOR
            : COMPLETE_COLOR;
        String topology = debug.root() ? "ROOT" : "LEGACY_CHILD";
        String label = topology
            + " · L" + debug.branchLevel()
            + " · " + segment.direction()
            + " · " + segment.status();
        return box(id, minX, minY, minZ, maxX, maxY, maxZ, color, SEGMENT_OPACITY, label);
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

    private static String id(PlayerRef playerRef, String suffix) {
        return "civ:mine-debug:" + playerRef.getUuid() + ":" + suffix;
    }

    public record MineDebugSnapshot(
        BuildingPlacementRegistry.BuildingInstance mine,
        double distanceBlocks,
        List<SegmentDebugSnapshot> segments
    ) {
        public MineDebugSnapshot {
            segments = List.copyOf(segments == null ? new ArrayList<>() : segments);
        }

        public long activeFrontCount() {
            return segments.stream().filter(SegmentDebugSnapshot::active).count();
        }

        public long openFrontCount() {
            return segments.stream().filter(SegmentDebugSnapshot::open).count();
        }
    }

    public record SegmentDebugSnapshot(
        MineSegment segment,
        int branchLevel,
        boolean root,
        boolean active,
        boolean open
    ) {
    }

    public record ShowResult(int displayedEntryCount, boolean boundsIncluded) {
    }
}
