package dev.civilizations.hytale;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.component.TriggerVolume;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.BuildingOrientation;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Hytale's {@link BlockSelection#place} queues prefab entity insertion through
 * {@link World#execute(Runnable)}. The entity consumer therefore observes refs before they are
 * valid. This adapter deliberately waits behind Hytale's queued insertions before reading UUIDs
 * or trigger-volume registrations.
 */
final class NativePrefabPlacementFinalizer {

    private static final String TYPE_TAG = "civ.type";
    private static final String CONSTRUCTION_GROUND_LEVEL = "construction_ground_level";

    private NativePrefabPlacementFinalizer() {
    }

    static void placeAsync(
        PrefabPlacementService placementService,
        PlayerRef playerRef,
        World world,
        PrefabPlacementService.ConstructionSite site,
        Consumer<PrefabPlacementService.PlacedPrefab> onComplete
    ) {
        if (placementService == null || playerRef == null || world == null
            || site == null || onComplete == null) {
            throw new IllegalArgumentException("Native prefab finalization requires all arguments.");
        }

        TriggerVolumeManager volumeManager = triggerVolumeManager(world);
        Set<String> existingVolumeIds = new HashSet<>(volumeManager.getVolumesMap().keySet());
        List<Ref<EntityStore>> placedEntityRefs = new ArrayList<>();

        // Remove the construction preview/site before materializing the real prefab. The
        // reservation is owned by BuildingPlacementRegistry and remains until completion.
        placementService.removeConstructionSite(site);

        BlockSelection prefab = requireSource(site.definition(), site.candidate().orientation());
        prefab.place(
            playerRef,
            world,
            enginePlacementOrigin(site.candidate()),
            null,
            entityRef -> {
                // Do not call isValid() here. Hytale has created the Ref, but queues the actual
                // Store insertion afterwards via World.execute(...).
                if (entityRef != null) {
                    placedEntityRefs.add(entityRef);
                }
            },
            false,
            null,
            false
        );

        // Hytale's entity insertion tasks were queued by BlockSelection.place before this task.
        // Running finalization from the same world queue guarantees that those refs and native
        // TriggerVolume registrations are observable here.
        world.execute(() -> {
            List<UUID> prefabEntityIds = new ArrayList<>();
            for (Ref<EntityStore> entityRef : placedEntityRefs) {
                if (entityRef == null || !entityRef.isValid()) {
                    continue;
                }
                Store<EntityStore> entityStore = entityRef.getStore();
                TriggerVolume trigger = entityStore.getComponent(
                    entityRef,
                    TriggerVolumesPlugin.get().getTriggerVolumeComponentType()
                );
                if (trigger != null) {
                    continue;
                }
                UUIDComponent uuid = entityStore.getComponent(
                    entityRef,
                    UUIDComponent.getComponentType()
                );
                if (uuid != null && uuid.getUuid() != null) {
                    prefabEntityIds.add(uuid.getUuid());
                }
            }

            List<PrefabPlacementService.PlacedMarker> markers = volumeManager.getVolumes().stream()
                .filter(volume -> !existingVolumeIds.contains(volume.getId()))
                .map(NativePrefabPlacementFinalizer::toMarker)
                .toList();

            System.out.println(
                "[Civ Buildings] finalized prefab site=" + site.id()
                    + " entities=" + prefabEntityIds.size()
                    + " semanticVolumes=" + markers.size()
            );
            onComplete.accept(new PrefabPlacementService.PlacedPrefab(
                site.candidate(),
                markers,
                prefabEntityIds
            ));
        });
    }

    private static TriggerVolumeManager triggerVolumeManager(World world) {
        return world.getEntityStore().getStore().getResource(
            TriggerVolumesPlugin.get().getManagerResourceType()
        );
    }

    private static PrefabPlacementService.PlacedMarker toMarker(VolumeEntry volume) {
        Vector3d min = new Vector3d();
        Vector3d max = new Vector3d();
        volume.getShape().getWorldAABB(volume.getPosition(), min, max);
        return new PrefabPlacementService.PlacedMarker(
            volume.getId(),
            new Vector3i(
                (int) Math.floor(volume.getPosition().x),
                (int) Math.floor(volume.getPosition().y),
                (int) Math.floor(volume.getPosition().z)
            ),
            volume.getRawTags(),
            new BuildingBounds(min.x, min.y, min.z, max.x, max.y, max.z)
        );
    }

    private static BlockSelection requireSource(
        PrefabPlacementService.PlacementDefinition definition,
        BuildingOrientation orientation
    ) {
        BlockSelection source = new BlockSelection(requireRawSource(definition));
        Integer groundY = constructionGroundSourceY(source);
        if (groundY != null) {
            source.setAnchor(source.getAnchorX(), groundY, source.getAnchorZ());
        }
        if (orientation != BuildingOrientation.NORTH) {
            source = source.rotate(
                Axis.Y,
                HytalePrefabOrientation.blockSelectionDegrees(orientation),
                new Vector3d(source.getAnchorX(), source.getAnchorY(), source.getAnchorZ())
            );
        }
        return source;
    }

    private static BlockSelection requireRawSource(
        PrefabPlacementService.PlacementDefinition definition
    ) {
        PrefabStore prefabStore = PrefabStore.get();
        java.nio.file.Path prefabPath = prefabStore.findBrowsablePrefabPath(definition.prefabKey());
        if (prefabPath == null) {
            throw new IllegalStateException(
                definition.displayName() + " prefab not found for key '"
                    + definition.prefabKey() + "'."
            );
        }
        return prefabStore.getPrefab(prefabPath);
    }

    private static Integer constructionGroundSourceY(BlockSelection source) {
        List<Integer> groundLevels = new ArrayList<>();
        source.forEachEntity(holder -> {
            TriggerVolume trigger = holder.getComponent(
                TriggerVolumesPlugin.get().getTriggerVolumeComponentType()
            );
            TransformComponent transform = holder.getComponent(
                TransformComponent.getComponentType()
            );
            if (trigger == null || transform == null || trigger.getShape() == null) {
                return;
            }
            VolumeEntry entry = trigger.toVolumeEntry(
                "civ-prefab-finalization",
                "civ-prefab-finalization",
                transform.getPosition()
            );
            Map<String, String> tags = entry.getRawTags();
            if (tags == null || !CONSTRUCTION_GROUND_LEVEL.equals(tags.get(TYPE_TAG))) {
                return;
            }
            Vector3d min = new Vector3d();
            Vector3d max = new Vector3d();
            trigger.getShape().getWorldAABB(transform.getPosition(), min, max);
            groundLevels.add((int) Math.floor(min.y));
        });
        if (groundLevels.isEmpty()) {
            return null;
        }
        if (groundLevels.size() != 1) {
            throw new IllegalStateException(
                "Prefab must define exactly one civ.type=construction_ground_level marker."
            );
        }
        return groundLevels.getFirst();
    }

    private static Vector3i enginePlacementOrigin(
        PrefabPlacementService.PlacementCandidate candidate
    ) {
        return new Vector3i(
            candidate.anchor().x,
            candidate.anchor().y + candidate.definition().groundSinkBlocks(),
            candidate.anchor().z
        );
    }
}
