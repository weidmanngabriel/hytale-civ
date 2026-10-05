package dev.civilizations.hytale;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.component.TriggerVolume;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Derives only the authored placement footprint needed while the mouse moves a native prefab ghost.
 * It deliberately performs no world-block snapshotting; full validation remains click-driven.
 */
public final class CivPlacementFootprintProbe {

    private static final String TYPE_TAG = "civ.type";
    private static final String BUILDING_TAG = "civ.building";
    private static final String BUILDING_BOUNDS = "building_bounds";
    private static final String FIELD = "field";
    private static final String FARM = "farm";

    private final Map<String, LocalFootprint> cache = new ConcurrentHashMap<>();

    public PrefabPlacementService.PlacementFootprint footprint(
        Vector3i pointedBlock,
        PrefabPlacementService.PlacementDefinition definition
    ) {
        if (pointedBlock == null || definition == null) return null;
        LocalFootprint local = cache.computeIfAbsent(definition.prefabKey(), ignored -> inspect(definition));
        return new PrefabPlacementService.PlacementFootprint(
            pointedBlock.x + local.minX(),
            pointedBlock.z + local.minZ(),
            pointedBlock.x + local.maxX(),
            pointedBlock.z + local.maxZ(),
            pointedBlock.y
        );
    }

    private static LocalFootprint inspect(PrefabPlacementService.PlacementDefinition definition) {
        PrefabStore prefabStore = PrefabStore.get();
        java.nio.file.Path path = prefabStore.findBrowsablePrefabPath(definition.prefabKey());
        if (path == null) throw new IllegalStateException("Prefab not found: " + definition.prefabKey());
        BlockSelection source = new BlockSelection(prefabStore.getPrefab(path));
        List<Marker> markers = markers(source);
        List<Marker> bounds = markers.stream()
            .filter(marker -> BUILDING_BOUNDS.equals(marker.tags().get(TYPE_TAG)))
            .toList();
        if (bounds.isEmpty() && PrefabPlacementService.WHEAT_FIELD.prefabKey().equals(definition.prefabKey())) {
            bounds = markers.stream()
                .filter(marker -> FIELD.equals(marker.tags().get(TYPE_TAG)))
                .filter(marker -> FARM.equals(marker.tags().get(BUILDING_TAG)))
                .toList();
        }
        if (bounds.isEmpty()) {
            throw new IllegalStateException(
                definition.displayName() + " requires an authored lifecycle boundary marker for collision preview."
            );
        }

        double localMinX = bounds.stream().mapToDouble(Marker::minX).min().orElseThrow();
        double localMinZ = bounds.stream().mapToDouble(Marker::minZ).min().orElseThrow();
        double localMaxX = bounds.stream().mapToDouble(Marker::maxX).max().orElseThrow();
        double localMaxZ = bounds.stream().mapToDouble(Marker::maxZ).max().orElseThrow();
        return new LocalFootprint(
            (int) Math.floor(localMinX - source.getAnchorX()),
            (int) Math.floor(localMinZ - source.getAnchorZ()),
            (int) Math.ceil(localMaxX - source.getAnchorX()) - 1,
            (int) Math.ceil(localMaxZ - source.getAnchorZ()) - 1
        );
    }

    private static List<Marker> markers(BlockSelection source) {
        List<Marker> result = new ArrayList<>();
        source.forEachEntity(holder -> {
            TriggerVolume trigger = holder.getComponent(
                TriggerVolumesPlugin.get().getTriggerVolumeComponentType()
            );
            TransformComponent transform = holder.getComponent(TransformComponent.getComponentType());
            if (trigger == null || transform == null || trigger.getShape() == null) return;
            VolumeEntry entry = trigger.toVolumeEntry("civ-preview", "civ-preview", transform.getPosition());
            Vector3d min = new Vector3d();
            Vector3d max = new Vector3d();
            trigger.getShape().getWorldAABB(transform.getPosition(), min, max);
            result.add(new Marker(entry.getRawTags(), min.x, min.z, max.x, max.z));
        });
        return List.copyOf(result);
    }

    private record LocalFootprint(int minX, int minZ, int maxX, int maxZ) {
    }

    private record Marker(
        Map<String, String> tags,
        double minX,
        double minZ,
        double maxX,
        double maxZ
    ) {
    }
}
