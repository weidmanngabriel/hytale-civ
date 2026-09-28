package dev.civilizations.hytale;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.civilizations.core.BlockPosition;
import org.joml.Vector3i;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * Loads, previews, validates and places the Farm prefab.
 */
@SuppressWarnings("deprecation")
public final class FarmPrefabService {

    public static final String FARM_PREFAB_KEY = "Civilizations/Farm/Farm_01";
    private static final String WORKPLACE_TYPE_TAG = "civ.type";
    private static final String WORKPLACE_TYPE_VALUE = "workplace_access";
    private static final String BUILDING_TAG = "civ.building";
    private static final String FARM_BUILDING_VALUE = "farm";
    private static final int GROUND_SINK_BLOCKS = 1;

    public PlacementCandidate validatePlacement(World world, Vector3i pointedBlock) {
        BlockSelection source = requireSource();
        Vector3i anchor = new Vector3i(
            pointedBlock.x,
            pointedBlock.y - GROUND_SINK_BLOCKS,
            pointedBlock.z
        );

        List<PrefabCell> cells = readCells(source);
        int floorY = cells.stream()
            .mapToInt(PrefabCell::y)
            .min()
            .orElseThrow(() -> new IllegalStateException("Farm prefab has no physical blocks."));

        List<PrefabCell> floorCells = cells.stream()
            .filter(cell -> cell.y() == floorY)
            .toList();

        if (floorCells.isEmpty()) {
            return PlacementCandidate.invalid(anchor, "Gebäudeboden konnte nicht bestimmt werden.");
        }

        PlacementFootprint footprint = footprintFor(source, anchor, floorCells);
        Map<BlockPosition, Integer> replacedFloorBlocks = new LinkedHashMap<>();

        for (PrefabCell floor : floorCells) {
            Vector3i worldPosition = worldPosition(source, anchor, floor);
            int existingBlock = world.getBlock(worldPosition.x, worldPosition.y, worldPosition.z);
            if (existingBlock == BlockType.EMPTY_ID) {
                return PlacementCandidate.invalid(
                    anchor,
                    footprint,
                    "Der Gebäudeboden würde über einem Loch oder einer Kante liegen."
                );
            }
            if (world.getFluidId(worldPosition.x, worldPosition.y, worldPosition.z) != 0) {
                return PlacementCandidate.invalid(
                    anchor,
                    footprint,
                    "Der Gebäudeboden kann nicht in Wasser oder andere Flüssigkeiten gesetzt werden."
                );
            }
            if (world.getBlock(worldPosition.x, worldPosition.y - 1, worldPosition.z)
                == BlockType.EMPTY_ID) {
                return PlacementCandidate.invalid(
                    anchor,
                    footprint,
                    "Unter dem Gebäudeboden fehlt tragfähiger Boden."
                );
            }

            replacedFloorBlocks.put(
                new BlockPosition(worldPosition.x, worldPosition.y, worldPosition.z),
                existingBlock
            );
        }

        int clearanceTop = source.getSelectionMax().y - source.getAnchorY() + anchor.y;
        for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
            for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
                for (int y = footprint.floorY() + 1; y <= clearanceTop; y++) {
                    if (world.getBlock(x, y, z) != BlockType.EMPTY_ID
                        || world.getFluidId(x, y, z) != 0) {
                        return PlacementCandidate.invalid(
                            anchor,
                            footprint,
                            "Die Fläche ist durch Gelände oder ein Objekt blockiert."
                        );
                    }
                }
            }
        }

        for (PrefabCell cell : cells) {
            if (cell.y() == floorY) {
                continue;
            }
            Vector3i worldPosition = worldPosition(source, anchor, cell);
            if (world.getBlock(worldPosition.x, worldPosition.y, worldPosition.z)
                != BlockType.EMPTY_ID
                || world.getFluidId(worldPosition.x, worldPosition.y, worldPosition.z) != 0) {
                return PlacementCandidate.invalid(
                    anchor,
                    footprint,
                    "Die Fläche ist durch Gelände oder ein Objekt blockiert."
                );
            }
        }

        return PlacementCandidate.valid(anchor, footprint, replacedFloorBlocks);
    }

    public void showPreview(PlayerRef playerRef, PlacementCandidate candidate) {
        BlockSelection preview = new BlockSelection(requireSource());
        preview.relativizeInPlace();
        preview.setPosition(candidate.anchor().x, candidate.anchor().y, candidate.anchor().z);
        playerRef.getPacketHandler().write(preview.toPacketWithSelection());
    }

    public void clearPreview(PlayerRef playerRef) {
        playerRef.getPacketHandler().write(new BlockSelection().toPacketWithSelection());
    }

    public PlacedFarm placeFarm(
        PlayerRef playerRef,
        World world,
        PlacementCandidate candidate
    ) {
        if (!candidate.valid()) {
            throw new IllegalArgumentException("Cannot place an invalid Farm candidate.");
        }

        BlockSelection source = requireSource();
        Vector3i anchor = candidate.anchor();
        TriggerVolumeManager volumeManager = triggerVolumeManager(world);
        Set<String> existingVolumeIds = new HashSet<>(volumeManager.getVolumesMap().keySet());

        BlockSelection prefab = new BlockSelection(source);
        prefab.place(
            playerRef,
            world,
            new Vector3i(anchor),
            null,
            BlockSelection.DEFAULT_ENTITY_CONSUMER,
            false,
            null,
            false
        );

        List<Vector3i> workplaceBlocks = volumeManager.getVolumes().stream()
            .filter(volume -> !existingVolumeIds.contains(volume.getId()))
            .filter(FarmPrefabService::isFarmWorkplace)
            .map(FarmPrefabService::workplaceFloorBlock)
            .toList();
        if (workplaceBlocks.isEmpty()) {
            throw new IllegalStateException(
                "Farm prefab paste did not register a civ farm workplace trigger volume."
            );
        }

        return new PlacedFarm(
            workplaceBlocks,
            candidate.footprint(),
            candidate.replacedFloorBlocks()
        );
    }

    private static PlacementFootprint footprintFor(
        BlockSelection source,
        Vector3i anchor,
        List<PrefabCell> floorCells
    ) {
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
            cells.add(new PrefabCell(x, y, z, blockHolder.blockId()))
        );
        return List.copyOf(cells);
    }

    private static BlockSelection requireSource() {
        BlockSelection source = PrefabStore.get().getAssetPrefabFromAnyPack(FARM_PREFAB_KEY);
        if (source == null) {
            throw new IllegalStateException(
                "Farm prefab not found. Install the hytale-civ-assets Asset Pack next to the plugin."
            );
        }
        return source;
    }

    private static TriggerVolumeManager triggerVolumeManager(World world) {
        return world.getEntityStore().getStore().getResource(
            TriggerVolumesPlugin.get().getManagerResourceType()
        );
    }

    private static boolean isFarmWorkplace(VolumeEntry volume) {
        Map<String, String> tags = volume.getRawTags();
        return WORKPLACE_TYPE_VALUE.equals(tags.get(WORKPLACE_TYPE_TAG))
            && FARM_BUILDING_VALUE.equals(tags.get(BUILDING_TAG));
    }

    private static Vector3i workplaceFloorBlock(VolumeEntry volume) {
        return new Vector3i(
            (int) Math.floor(volume.getPosition().x),
            (int) Math.floor(volume.getPosition().y) - 1,
            (int) Math.floor(volume.getPosition().z)
        );
    }

    private record PrefabCell(int x, int y, int z, int blockId) {
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

    public record PlacementCandidate(
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
            Vector3i anchor,
            PlacementFootprint footprint,
            Map<BlockPosition, Integer> replacedFloorBlocks
        ) {
            return new PlacementCandidate(anchor, footprint, replacedFloorBlocks, null);
        }

        public static PlacementCandidate invalid(Vector3i anchor, String reason) {
            return new PlacementCandidate(anchor, null, Map.of(), reason);
        }

        public static PlacementCandidate invalid(
            Vector3i anchor,
            PlacementFootprint footprint,
            String reason
        ) {
            return new PlacementCandidate(anchor, footprint, Map.of(), reason);
        }

        public PlacementCandidate invalidate(String reason) {
            return new PlacementCandidate(anchor, footprint, replacedFloorBlocks, reason);
        }
    }

    public record PlacedFarm(
        List<Vector3i> entranceBlocks,
        PlacementFootprint footprint,
        Map<BlockPosition, Integer> replacedFloorBlocks
    ) {
        public PlacedFarm {
            entranceBlocks = List.copyOf(entranceBlocks);
            replacedFloorBlocks = Map.copyOf(replacedFloorBlocks);
            if (entranceBlocks.isEmpty()) {
                throw new IllegalArgumentException("entranceBlocks must not be empty");
            }
        }
    }
}
