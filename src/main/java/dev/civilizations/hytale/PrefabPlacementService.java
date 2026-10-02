package dev.civilizations.hytale;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.builtin.buildertools.utils.PasteToolUtil;
import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.component.TriggerVolume;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentPrefabPreview;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared placement boundary for Civ prefabs.
 *
 * <p>The creator owns prefab geometry. Civ derives authored semantics such as
 * construction ground level and reservation bounds from trigger-volume tags instead
 * of relying on fixed prefab dimensions or coordinates.</p>
 */
@SuppressWarnings("deprecation")
public final class PrefabPlacementService {

    private static final String TYPE_TAG = "civ.type";
    private static final String BUILDING_BOUNDS = "building_bounds";
    private static final String CONSTRUCTION_GROUND_LEVEL = "construction_ground_level";

    private final Map<UUID, ActiveConstructionPreview> activeConstructionPreviews =
        new ConcurrentHashMap<>();
    private final Map<UUID, ConstructionSite> constructionSites =
        new ConcurrentHashMap<>();

    public static final PlacementDefinition FARM = new PlacementDefinition(
        "farm",
        "Farm",
        "Civilizations/Farm/Farm_01",
        1
    );
    public static final PlacementDefinition MINE = new PlacementDefinition(
        "mine",
        "Mine",
        "Civilizations/Mine/Mine_01",
        1
    );
    public static final PlacementDefinition WHEAT_FIELD = new PlacementDefinition(
        "wheat_field",
        "Weizenfeld",
        "Civilizations/Farm/Field_01",
        1
    );

    public PlacementCandidate validatePlacement(
        World world,
        Vector3i pointedBlock,
        PlacementDefinition definition
    ) {
        /*
         * Construction-preview spike: deliberately do not run the legacy
         * immediate-paste collision rules here. Those rules were designed for
         * BlockSelection.place and reject the intentionally sunk Y - 1
         * construction anchor before PersistentPrefabPreview can even spawn.
         *
         * Keep only the transform/footprint calculation so we can verify the
         * engine preview lifecycle independently. Terrain/overlap validation
         * will be reintroduced against the construction-site semantics after
         * this preview boundary is proven in-game.
         */
        BlockSelection source = requireSource(definition);
        Vector3i anchor = placementAnchor(pointedBlock, definition);
        Vector3i placementOrigin = enginePlacementOrigin(definition, anchor);
        List<PrefabCell> cells = readCells(source);

        PlacementFootprint footprint = semanticFootprint(source, placementOrigin);
        if (footprint == null) {
            List<PrefabCell> floorCells = cells.stream()
                .filter(cell -> cell.y() <= source.getAnchorY())
                .toList();
            footprint = footprintFor(source, anchor, floorCells);
        }

        Map<BlockPosition, Integer> replacedBlocks = new LinkedHashMap<>();
        for (PrefabCell cell : cells) {
            Vector3i position = worldPosition(source, placementOrigin, cell);
            WorldChunk chunk = loadedChunk(world, position.x, position.z);
            if (chunk == null) {
                return PlacementCandidate.invalid(
                    definition,
                    anchor,
                    footprint,
                    "Die Baufläche ist noch nicht vollständig geladen."
                );
            }
            replacedBlocks.put(
                new BlockPosition(position.x, position.y, position.z),
                chunk.getBlock(position.x, position.y, position.z)
            );
        }

        return PlacementCandidate.valid(
            definition,
            anchor,
            footprint,
            replacedBlocks
        );
    }

    private static Vector3i placementAnchor(
        Vector3i pointedBlock,
        PlacementDefinition definition
    ) {
        return new Vector3i(
            pointedBlock.x,
            pointedBlock.y - definition.groundSinkBlocks(),
            pointedBlock.z
        );
    }

    public boolean startNativeConstructionGhost(
        PlayerRef playerRef,
        PlacementDefinition definition
    ) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return false;
        }
        Store<EntityStore> store = playerEntityRef.getStore();
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return false;
        }

        BlockSelection source = requireSource(definition);
        source.setAnchor(
            source.getAnchorX(),
            source.getAnchorY() + definition.groundSinkBlocks(),
            source.getAnchorZ()
        );
        BuilderToolsPlugin.addToQueue(
            player,
            playerRef,
            (ref, state, accessor) ->
                state.load(definition.displayName(), source, accessor)
        );
        PasteToolUtil.switchToPasteTool(playerEntityRef, playerRef, store);
        return true;
    }

    public ConstructionSite createConstructionSiteAtClick(
        PlayerRef playerRef,
        PlacementCandidate candidate
    ) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            throw new IllegalStateException("Player entity unavailable.");
        }
        Store<EntityStore> store = playerEntityRef.getStore();
        Vector3i previewOrigin = persistentPreviewOrigin(candidate);
        Ref<EntityStore> previewRef = PersistentPrefabPreview.spawn(
            store,
            new Vector3d(previewOrigin.x, previewOrigin.y, previewOrigin.z),
            new Rotation3f(),
            candidate.definition().prefabKey(),
            Integer.MAX_VALUE
        );
        if (previewRef == null || !previewRef.isValid()) {
            throw new IllegalStateException(
                "PersistentPrefabPreview.spawn returned no valid entity"
            );
        }
        ConstructionSite site = new ConstructionSite(
            UUID.randomUUID(),
            playerRef.getUuid(),
            playerRef.getWorldUuid(),
            candidate,
            previewRef
        );
        constructionSites.put(site.id(), site);
        return site;
    }

    public boolean startConstructionPreview(
        PlayerRef playerRef,
        PlacementDefinition definition
    ) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return false;
        }
        requireSource(definition);
        cancelConstructionPreview(playerRef);
        activeConstructionPreviews.put(
            playerRef.getUuid(),
            new ActiveConstructionPreview(definition, null)
        );
        return true;
    }

    public void updateConstructionPreview(
        PlayerRef playerRef,
        PlacementCandidate candidate
    ) {
        ActiveConstructionPreview active =
            activeConstructionPreviews.get(playerRef.getUuid());
        if (active == null || active.definition() != candidate.definition()) {
            return;
        }

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }
        Store<EntityStore> store = playerEntityRef.getStore();
        Ref<EntityStore> previewRef = active.previewRef();
        Vector3i previewOrigin = persistentPreviewOrigin(candidate);

        if (previewRef == null || !previewRef.isValid()) {
            previewRef = PersistentPrefabPreview.spawn(
                store,
                new Vector3d(previewOrigin.x, previewOrigin.y, previewOrigin.z),
                new Rotation3f(),
                active.definition().prefabKey(),
                Integer.MAX_VALUE
            );
            if (previewRef == null || !previewRef.isValid()) {
                throw new IllegalStateException(
                    "PersistentPrefabPreview.spawn returned no valid entity"
                );
            }
            activeConstructionPreviews.put(
                playerRef.getUuid(),
                new ActiveConstructionPreview(active.definition(), previewRef)
            );
            return;
        }

        TransformComponent transform = store.getComponent(
            previewRef,
            TransformComponent.getComponentType()
        );
        if (transform != null) {
            transform.setPosition(new Vector3d(
                previewOrigin.x,
                previewOrigin.y,
                previewOrigin.z
            ));
        }
    }

    public ConstructionSite commitConstructionPreview(
        PlayerRef playerRef,
        PlacementCandidate candidate
    ) {
        ActiveConstructionPreview active =
            activeConstructionPreviews.remove(playerRef.getUuid());
        if (active == null || active.previewRef() == null || !active.previewRef().isValid()) {
            throw new IllegalStateException("No active Civ construction preview.");
        }

        ConstructionSite site = new ConstructionSite(
            UUID.randomUUID(),
            playerRef.getUuid(),
            playerRef.getWorldUuid(),
            candidate,
            active.previewRef()
        );
        constructionSites.put(site.id(), site);
        return site;
    }

    public void cancelConstructionPreview(PlayerRef playerRef) {
        ActiveConstructionPreview active =
            activeConstructionPreviews.remove(playerRef.getUuid());
        if (active == null || active.previewRef() == null || !active.previewRef().isValid()) {
            return;
        }
        active.previewRef().getStore().removeEntity(active.previewRef(), RemoveReason.REMOVE);
    }

    public int cancelConstructionSites(PlayerRef playerRef) {
        UUID ownerId = playerRef.getUuid();
        List<ConstructionSite> ownedSites = constructionSites.values().stream()
            .filter(site -> site.ownerId().equals(ownerId))
            .toList();
        for (ConstructionSite site : ownedSites) {
            removeConstructionSite(site);
        }
        return ownedSites.size();
    }

    public void removeConstructionSite(ConstructionSite site) {
        constructionSites.remove(site.id(), site);
        Ref<EntityStore> previewRef = site.previewRef();
        if (previewRef != null && previewRef.isValid()) {
            PersistentPrefabPreview.remove(previewRef.getStore(), previewRef);
        }
    }

    public Collection<ConstructionSite> constructionSites() {
        return List.copyOf(constructionSites.values());
    }

    public int constructionLayerCount(ConstructionSite site) {
        return constructionLayers(requireSource(site.definition())).size();
    }

    /**
     * Materializes one authored prefab Y-layer, including explicit Empty cells,
     * without spawning prefab entities.
     */
    public boolean materializeConstructionLayer(
        World world,
        ConstructionSite site,
        int layerIndex,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        BlockSelection source = requireSource(site.definition());
        List<Integer> layers = constructionLayers(source);
        if (layerIndex < 0 || layerIndex >= layers.size()) {
            return false;
        }

        if (layerIndex == 0) {
            removeConstructionPreview(site, commandBuffer);
        }

        int sourceY = layers.get(layerIndex);
        BlockSelection layer = new BlockSelection();
        layer.copyPropertiesFrom(source);
        layer.setAnchor(source.getAnchorX(), source.getAnchorY(), source.getAnchorZ());
        source.forEachBlock((x, y, z, blockHolder) -> {
            if (y != sourceY) {
                return;
            }
            if (blockHolder.blockId() == BlockType.EMPTY_ID) {
                layer.addEmptyAtWorldPos(x, y, z);
                return;
            }
            layer.addBlockAtWorldPos(
                x,
                y,
                z,
                blockHolder.blockId(),
                blockHolder.rotation(),
                blockHolder.filler(),
                blockHolder.supportValue(),
                blockHolder.holder()
            );
        });
        layer.placeNoReturn(world, enginePlacementOrigin(site.candidate()), commandBuffer);
        return true;
    }

    /**
     * Performs the final native prefab placement so prefab entities and trigger volumes
     * are created only after all visible construction layers have been built.
     */
    public PlacedPrefab completeConstruction(
        PlayerRef playerRef,
        World world,
        ConstructionSite site
    ) {
        removeConstructionPreview(site);
        PlacedPrefab placed = place(playerRef, world, site.candidate());
        constructionSites.remove(site.id(), site);
        return placed;
    }

    public boolean demolish(World world, BuildingPlacementRegistry.BuildingInstance building) {
        if (world == null || building == null || building.placement() == null) {
            return false;
        }

        PlacementCandidate candidate = building.placement();
        for (BlockPosition position : candidate.replacedFloorBlocks().keySet()) {
            if (loadedChunk(world, position.x(), position.z()) == null) {
                return false;
            }
        }

        candidate.replacedFloorBlocks().forEach((position, blockId) ->
            loadedChunk(world, position.x(), position.z()).setBlock(
                position.x(),
                position.y(),
                position.z(),
                blockId
            )
        );

        TriggerVolumeManager volumes = triggerVolumeManager(world);
        building.semanticVolumes().stream()
            .map(PlacedMarker::id)
            .filter(volumes::hasVolume)
            .forEach(volumes::unregister);
        return true;
    }

    private static WorldChunk loadedChunk(World world, int blockX, int blockZ) {
        return world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(blockX, blockZ));
    }

    private static List<Integer> constructionLayers(BlockSelection source) {
        TreeSet<Integer> occupied = new TreeSet<>();
        source.forEachBlock((x, y, z, blockHolder) -> occupied.add(y));
        return PrefabConstructionOrder.order(occupied, constructionGroundSourceY(source));
    }

    private static Integer constructionGroundSourceY(BlockSelection source) {
        List<AuthoredMarker> groundMarkers = authoredMarkers(source).stream()
            .filter(marker -> marker.hasTag(TYPE_TAG, CONSTRUCTION_GROUND_LEVEL))
            .toList();
        if (groundMarkers.isEmpty()) {
            return null;
        }
        if (groundMarkers.size() != 1) {
            throw new IllegalStateException(
                "Prefab must define exactly one civ.type=construction_ground_level marker."
            );
        }
        return (int) Math.floor(groundMarkers.getFirst().bounds().minY());
    }

    private static PlacementFootprint semanticFootprint(
        BlockSelection source,
        Vector3i placementOrigin
    ) {
        List<AuthoredMarker> boundsMarkers = authoredMarkers(source).stream()
            .filter(marker -> marker.hasTag(TYPE_TAG, BUILDING_BOUNDS))
            .toList();
        if (boundsMarkers.isEmpty()) {
            return null;
        }

        double localMinX = boundsMarkers.stream()
            .mapToDouble(marker -> marker.bounds().minX())
            .min()
            .orElseThrow();
        double localMinZ = boundsMarkers.stream()
            .mapToDouble(marker -> marker.bounds().minZ())
            .min()
            .orElseThrow();
        double localMaxX = boundsMarkers.stream()
            .mapToDouble(marker -> marker.bounds().maxX())
            .max()
            .orElseThrow();
        double localMaxZ = boundsMarkers.stream()
            .mapToDouble(marker -> marker.bounds().maxZ())
            .max()
            .orElseThrow();

        int minX = (int) Math.floor(
            placementOrigin.x + localMinX - source.getAnchorX()
        );
        int minZ = (int) Math.floor(
            placementOrigin.z + localMinZ - source.getAnchorZ()
        );
        int maxX = (int) Math.ceil(
            placementOrigin.x + localMaxX - source.getAnchorX()
        ) - 1;
        int maxZ = (int) Math.ceil(
            placementOrigin.z + localMaxZ - source.getAnchorZ()
        ) - 1;

        Integer groundY = constructionGroundSourceY(source);
        int floorY = groundY == null
            ? placementOrigin.y
            : (int) Math.floor(placementOrigin.y + groundY - source.getAnchorY());
        return new PlacementFootprint(minX, minZ, maxX, maxZ, floorY);
    }

    private static List<AuthoredMarker> authoredMarkers(BlockSelection source) {
        List<AuthoredMarker> markers = new ArrayList<>();
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

            Vector3d min = new Vector3d();
            Vector3d max = new Vector3d();
            trigger.getShape().getWorldAABB(transform.getPosition(), min, max);
            VolumeEntry entry = trigger.toVolumeEntry(
                "civ-prefab-inspection",
                "civ-prefab-inspection",
                transform.getPosition()
            );
            markers.add(new AuthoredMarker(
                entry.getRawTags(),
                new BuildingBounds(min.x, min.y, min.z, max.x, max.y, max.z)
            ));
        });
        return List.copyOf(markers);
    }

    private static void removeConstructionPreview(
        ConstructionSite site,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> previewRef = site.previewRef();
        if (previewRef != null && previewRef.isValid()) {
            commandBuffer.tryRemoveEntity(previewRef, RemoveReason.REMOVE);
        }
    }

    private static void removeConstructionPreview(ConstructionSite site) {
        Ref<EntityStore> previewRef = site.previewRef();
        if (previewRef != null && previewRef.isValid()) {
            PersistentPrefabPreview.remove(previewRef.getStore(), previewRef);
        }
    }

    public PlacedPrefab place(
        PlayerRef playerRef,
        World world,
        PlacementCandidate candidate
    ) {
        if (!candidate.valid()) {
            throw new IllegalArgumentException("Cannot place an invalid Civ prefab candidate.");
        }

        TriggerVolumeManager volumeManager = triggerVolumeManager(world);
        Set<String> existingVolumeIds = new HashSet<>(volumeManager.getVolumesMap().keySet());

        BlockSelection prefab = requireSource(candidate.definition());
        prefab.place(
            playerRef,
            world,
            enginePlacementOrigin(candidate),
            null,
            BlockSelection.DEFAULT_ENTITY_CONSUMER,
            false,
            null,
            false
        );

        List<PlacedMarker> markers = volumeManager.getVolumes().stream()
            .filter(volume -> !existingVolumeIds.contains(volume.getId()))
            .map(PrefabPlacementService::toMarker)
            .toList();
        return new PlacedPrefab(candidate, markers);
    }

    private static TriggerVolumeManager triggerVolumeManager(World world) {
        return world.getEntityStore().getStore().getResource(
            TriggerVolumesPlugin.get().getManagerResourceType()
        );
    }

    private static PlacedMarker toMarker(VolumeEntry volume) {
        Vector3d min = new Vector3d();
        Vector3d max = new Vector3d();
        volume.getShape().getWorldAABB(volume.getPosition(), min, max);
        return new PlacedMarker(
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

    private static PlacementFootprint footprintFor(
        BlockSelection source,
        Vector3i anchor,
        List<PrefabCell> floorCells
    ) {
        if (floorCells.isEmpty()) {
            throw new IllegalStateException("Prefab has no floor cells.");
        }

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int floorWorldY = Integer.MIN_VALUE;

        for (PrefabCell cell : floorCells) {
            Vector3i worldPosition = worldPosition(source, anchor, cell);
            minX = Math.min(minX, worldPosition.x);
            minZ = Math.min(minZ, worldPosition.z);
            maxX = Math.max(maxX, worldPosition.x);
            maxZ = Math.max(maxZ, worldPosition.z);
            floorWorldY = worldPosition.y;
        }

        return new PlacementFootprint(minX, minZ, maxX, maxZ, floorWorldY);
    }

    private static Vector3i worldPosition(
        BlockSelection source,
        Vector3i anchor,
        PrefabCell cell
    ) {
        return new Vector3i(
            anchor.x + cell.x() - source.getAnchorX(),
            anchor.y + cell.y() - source.getAnchorY(),
            anchor.z + cell.z() - source.getAnchorZ()
        );
    }

    /**
     * Converts Civ's terrain-relative anchor into the origin expected by Hytale's
     * prefab placement APIs. The sink is part of the Civ terrain convention.
     */
    private static Vector3i enginePlacementOrigin(PlacementCandidate candidate) {
        return enginePlacementOrigin(candidate.definition(), candidate.anchor());
    }

    private static Vector3i enginePlacementOrigin(
        PlacementDefinition definition,
        Vector3i anchor
    ) {
        return new Vector3i(
            anchor.x,
            anchor.y + definition.groundSinkBlocks(),
            anchor.z
        );
    }

    /**
     * PersistentPrefabPreview loads the authored prefab key directly and therefore uses
     * the authored anchor rather than Civ's semantic construction-ground anchor. Apply
     * only the anchor delta to the preview entity so it renders where the prepared
     * BlockSelection will eventually be placed.
     */
    private static Vector3i persistentPreviewOrigin(PlacementCandidate candidate) {
        BlockSelection raw = requireRawSource(candidate.definition());
        Integer groundY = constructionGroundSourceY(raw);
        int effectiveAnchorY = groundY == null ? raw.getAnchorY() : groundY;
        Vector3i placementOrigin = enginePlacementOrigin(candidate);
        return new Vector3i(
            placementOrigin.x,
            placementOrigin.y + raw.getAnchorY() - effectiveAnchorY,
            placementOrigin.z
        );
    }

    private static List<PrefabCell> readCells(BlockSelection source) {
        List<PrefabCell> cells = new ArrayList<>();
        source.forEachBlock((x, y, z, blockHolder) ->
            cells.add(new PrefabCell(x, y, z))
        );
        return List.copyOf(cells);
    }

    /**
     * Returns a detached prefab selection whose Y anchor follows the semantic authored
     * construction ground level when present. No fixed mine depth or height is encoded.
     */
    private static BlockSelection requireSource(PlacementDefinition definition) {
        BlockSelection source = new BlockSelection(requireRawSource(definition));
        Integer groundY = constructionGroundSourceY(source);
        if (groundY != null) {
            source.setAnchor(source.getAnchorX(), groundY, source.getAnchorZ());
        }
        return source;
    }

    private static BlockSelection requireRawSource(PlacementDefinition definition) {
        PrefabStore prefabStore = PrefabStore.get();
        java.nio.file.Path prefabPath =
            prefabStore.findBrowsablePrefabPath(definition.prefabKey());
        if (prefabPath == null) {
            throw new IllegalStateException(
                definition.displayName() + " prefab not found for key '"
                    + definition.prefabKey() + "'."
            );
        }
        return prefabStore.getPrefab(prefabPath);
    }

    private record PrefabCell(int x, int y, int z) {
    }

    private record AuthoredMarker(Map<String, String> tags, BuildingBounds bounds) {
        private AuthoredMarker {
            tags = Map.copyOf(tags);
        }

        private boolean hasTag(String key, String value) {
            return value.equals(tags.get(key));
        }
    }

    public record PlacementDefinition(
        String id,
        String displayName,
        String prefabKey,
        int groundSinkBlocks
    ) {
    }

    public record PlacementFootprint(
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        int floorY
    ) {
        public boolean overlaps(PlacementFootprint other) {
            return minX <= other.maxX
                && maxX >= other.minX
                && minZ <= other.maxZ
                && maxZ >= other.minZ;
        }
    }

    private record ActiveConstructionPreview(
        PlacementDefinition definition,
        Ref<EntityStore> previewRef
    ) {
    }

    public record ConstructionSite(
        UUID id,
        UUID ownerId,
        UUID worldId,
        PlacementCandidate candidate,
        Ref<EntityStore> previewRef
    ) {
        public ConstructionSite {
            if (candidate == null) {
                throw new IllegalArgumentException("candidate cannot be null");
            }
        }

        public PlacementDefinition definition() {
            return candidate.definition();
        }

        public Vector3i anchor() {
            return new Vector3i(candidate.anchor());
        }
    }

    public record PlacedMarker(
        String id,
        Vector3i position,
        Map<String, String> tags,
        BuildingBounds bounds
    ) {
        public PlacedMarker {
            position = new Vector3i(position);
            tags = Map.copyOf(tags);
        }

        public boolean hasTag(String key, String value) {
            return value.equals(tags.get(key));
        }
    }

    public record PlacedPrefab(PlacementCandidate candidate, List<PlacedMarker> markers) {
        public PlacedPrefab {
            markers = List.copyOf(markers);
        }
    }

    public record PlacementCandidate(
        PlacementDefinition definition,
        Vector3i anchor,
        PlacementFootprint footprint,
        Map<BlockPosition, Integer> replacedFloorBlocks,
        String invalidReason
    ) {
        public PlacementCandidate {
            anchor = new Vector3i(anchor);
            replacedFloorBlocks = Map.copyOf(replacedFloorBlocks);
        }

        public boolean valid() {
            return invalidReason == null;
        }

        public static PlacementCandidate valid(
            PlacementDefinition definition,
            Vector3i anchor,
            PlacementFootprint footprint,
            Map<BlockPosition, Integer> replacedFloorBlocks
        ) {
            return new PlacementCandidate(
                definition,
                anchor,
                footprint,
                replacedFloorBlocks,
                null
            );
        }

        public static PlacementCandidate invalid(
            PlacementDefinition definition,
            Vector3i anchor,
            PlacementFootprint footprint,
            String reason
        ) {
            return new PlacementCandidate(
                definition,
                anchor,
                footprint,
                Map.of(),
                reason
            );
        }

        public PlacementCandidate invalidate(String reason) {
            return new PlacementCandidate(
                definition,
                anchor,
                footprint,
                replacedFloorBlocks,
                reason
            );
        }
    }
}
