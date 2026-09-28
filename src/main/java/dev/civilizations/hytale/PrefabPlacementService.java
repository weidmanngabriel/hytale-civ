package dev.civilizations.hytale;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.builtin.buildertools.utils.PasteToolUtil;
import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.BlockPosition;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared placement boundary for Civ prefabs.
 *
 * <p>The creator owns the prefab geometry. Civ only applies the common terrain
 * convention, validates occupied cells, previews the exact prefab and pastes it.
 */
@SuppressWarnings("deprecation")
public final class PrefabPlacementService {

    public static final PlacementDefinition FARM = new PlacementDefinition(
        "farm",
        "Farm",
        "Civilizations/Farm/Farm_01",
        0
    );
    public static final PlacementDefinition WHEAT_FIELD = new PlacementDefinition(
        "wheat_field",
        "Weizenfeld",
        "Civilizations/Farm/Field_01",
        0
    );

    public PlacementCandidate validatePlacement(
        World world,
        Vector3i pointedBlock,
        PlacementDefinition definition
    ) {
        BlockSelection source = requireSource(definition);
        Vector3i anchor = placementAnchor(pointedBlock, definition);

        List<PrefabCell> cells = readCells(source);
        int terrainReplaceMaxY = source.getAnchorY() + definition.groundSinkBlocks();
        List<PrefabCell> terrainCells = cells.stream()
            .filter(cell -> cell.y() <= terrainReplaceMaxY)
            .toList();
        PlacementFootprint footprint = footprintFor(source, anchor, terrainCells);
        Map<BlockPosition, Integer> replacedFloorBlocks = new LinkedHashMap<>();

        for (PrefabCell terrainCell : terrainCells) {
            Vector3i worldPosition = worldPosition(source, anchor, terrainCell);
            int existingBlock = world.getBlock(worldPosition.x, worldPosition.y, worldPosition.z);
            if (existingBlock == BlockType.EMPTY_ID) {
                return PlacementCandidate.invalid(
                    definition,
                    anchor,
                    footprint,
                    "Der eingelassene Baugrund würde über einem Loch oder einer Kante liegen."
                );
            }
            if (world.getFluidId(worldPosition.x, worldPosition.y, worldPosition.z) != 0) {
                return PlacementCandidate.invalid(
                    definition,
                    anchor,
                    footprint,
                    "Der Baugrund kann nicht in Wasser oder andere Flüssigkeiten gesetzt werden."
                );
            }
            replacedFloorBlocks.put(
                new BlockPosition(worldPosition.x, worldPosition.y, worldPosition.z),
                existingBlock
            );
        }

        for (PrefabCell cell : cells) {
            if (cell.y() <= terrainReplaceMaxY) {
                continue;
            }
            Vector3i worldPosition = worldPosition(source, anchor, cell);
            if (world.getBlock(worldPosition.x, worldPosition.y, worldPosition.z)
                != BlockType.EMPTY_ID
                || world.getFluidId(worldPosition.x, worldPosition.y, worldPosition.z) != 0) {
                return PlacementCandidate.invalid(
                    definition,
                    anchor,
                    footprint,
                    "Der Bauplatz ist oberhalb des Baugrunds durch Gelände oder ein Objekt blockiert."
                );
            }
        }

        return PlacementCandidate.valid(
            definition,
            anchor,
            footprint,
            replacedFloorBlocks
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

    public boolean startNativePastePlacement(
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
        BuilderToolsPlugin.addToQueue(
            player,
            playerRef,
            (ref, queuedState, accessor) ->
                queuedState.load(definition.displayName(), source, accessor)
        );
        PasteToolUtil.switchToPasteTool(playerEntityRef, playerRef, store);
        return true;
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

        BlockSelection prefab = new BlockSelection(requireSource(candidate.definition()));
        prefab.place(
            playerRef,
            world,
            new Vector3i(candidate.anchor()),
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
        return new PlacedMarker(
            volume.getId(),
            new Vector3i(
                (int) Math.floor(volume.getPosition().x),
                (int) Math.floor(volume.getPosition().y),
                (int) Math.floor(volume.getPosition().z)
            ),
            volume.getRawTags()
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

    private static List<PrefabCell> readCells(BlockSelection source) {
        List<PrefabCell> cells = new ArrayList<>();
        source.forEachBlock((x, y, z, blockHolder) ->
            cells.add(new PrefabCell(x, y, z))
        );
        return List.copyOf(cells);
    }

    private static BlockSelection requireSource(PlacementDefinition definition) {
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

    public record PlacedMarker(String id, Vector3i position, Map<String, String> tags) {
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
