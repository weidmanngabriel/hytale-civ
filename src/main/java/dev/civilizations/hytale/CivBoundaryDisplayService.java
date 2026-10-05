package dev.civilizations.hytale;

import com.hypixel.hytale.protocol.packets.player.TriggerVolumeDisplayEntry;
import com.hypixel.hytale.protocol.packets.player.TriggerVolumeShapeType;
import com.hypixel.hytale.protocol.packets.player.UpdateTriggerVolumeDisplay;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.civilizations.core.BuildingBounds;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Player-local boundary rendering through Hytale's native trigger-volume display packet.
 * No blocks, particles or persistent world entities are created for Civ selection visuals.
 */
public final class CivBoundaryDisplayService {

    private static final Vector3f SELECTED_COLOR = new Vector3f(0.30f, 0.72f, 1.0f);
    private static final Vector3f PLANNED_COLOR = new Vector3f(1.0f, 0.78f, 0.20f);
    private static final Vector3f BLOCKED_COLOR = new Vector3f(1.0f, 0.25f, 0.20f);
    private static final float OPACITY = 0.30f;

    public void showBuilding(PlayerRef playerRef, BuildingBounds bounds, String label) {
        if (playerRef == null || bounds == null) {
            clear(playerRef);
            return;
        }
        send(playerRef, List.of(box("civ:selected", bounds, SELECTED_COLOR, label)));
    }

    public void showSite(
        PlayerRef playerRef,
        PrefabPlacementService.PlacementFootprint footprint,
        String label
    ) {
        if (playerRef == null || footprint == null) {
            clear(playerRef);
            return;
        }
        send(playerRef, List.of(box("civ:selected-site", footprint, SELECTED_COLOR, label)));
    }

    public void showPlacementCollision(
        PlayerRef playerRef,
        PrefabPlacementService.PlacementFootprint planned,
        List<BuildingBounds> blockingBuildings,
        List<PrefabPlacementService.PlacementFootprint> blockingSites
    ) {
        if (playerRef == null || planned == null) return;
        List<TriggerVolumeDisplayEntry> entries = new ArrayList<>();
        entries.add(box("civ:planned", planned, PLANNED_COLOR, "Geplante Baufläche"));
        int index = 0;
        if (blockingBuildings != null) {
            for (BuildingBounds bounds : blockingBuildings) {
                if (bounds != null) {
                    entries.add(box("civ:blocking-building:" + index++, bounds, BLOCKED_COLOR, "Belegt"));
                }
            }
        }
        if (blockingSites != null) {
            for (PrefabPlacementService.PlacementFootprint footprint : blockingSites) {
                if (footprint != null) {
                    entries.add(box("civ:blocking-site:" + index++, footprint, BLOCKED_COLOR, "Baustelle"));
                }
            }
        }
        send(playerRef, entries);
    }

    public void clear(PlayerRef playerRef) {
        if (playerRef == null || playerRef.getPacketHandler() == null) return;
        playerRef.getPacketHandler().write(
            new UpdateTriggerVolumeDisplay(new TriggerVolumeDisplayEntry[0])
        );
    }

    private static void send(PlayerRef playerRef, List<TriggerVolumeDisplayEntry> entries) {
        if (playerRef.getPacketHandler() == null) return;
        playerRef.getPacketHandler().write(
            new UpdateTriggerVolumeDisplay(entries.toArray(TriggerVolumeDisplayEntry[]::new))
        );
    }

    private static TriggerVolumeDisplayEntry box(
        String id,
        BuildingBounds bounds,
        Vector3f color,
        String label
    ) {
        double minY = bounds.minY();
        double maxY = Math.max(minY + 0.10, bounds.maxY());
        TriggerVolumeDisplayEntry entry = new TriggerVolumeDisplayEntry();
        entry.volumeId = id;
        entry.shapeType = TriggerVolumeShapeType.Box;
        entry.position = new Vector3f(
            (float) ((bounds.minX() + bounds.maxX()) * 0.5),
            (float) ((minY + maxY) * 0.5),
            (float) ((bounds.minZ() + bounds.maxZ()) * 0.5)
        );
        entry.dimensions = new Vector3f(
            (float) Math.max(0.05, (bounds.maxX() - bounds.minX()) * 0.5),
            (float) Math.max(0.05, (maxY - minY) * 0.5),
            (float) Math.max(0.05, (bounds.maxZ() - bounds.minZ()) * 0.5)
        );
        entry.color = new Vector3f(color);
        entry.opacity = OPACITY;
        entry.name = label;
        return entry;
    }

    private static TriggerVolumeDisplayEntry box(
        String id,
        PrefabPlacementService.PlacementFootprint footprint,
        Vector3f color,
        String label
    ) {
        double minY = footprint.floorY() + 0.02;
        double maxY = minY + 0.16;
        return box(
            id,
            new BuildingBounds(
                footprint.minX(),
                minY,
                footprint.minZ(),
                footprint.maxX() + 1.0,
                maxY,
                footprint.maxZ() + 1.0
            ),
            color,
            label
        );
    }
}
