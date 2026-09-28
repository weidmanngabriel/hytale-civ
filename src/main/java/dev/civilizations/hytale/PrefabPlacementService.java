package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.civilizations.core.BlockPosition;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        BlockSelection source = requireSource(definition);
        Vector3i anchor = new Vector3i(
            pointedBlock.x,
            pointedBlock.y - definition.groundSinkBlocks(),
            pointedBlock.z
        );

        List<PrefabCell> cells = readCells(source);
        int floorY = cells.stream()
            .mapToInt(PrefabCell::y)
            .min()
            .orElseThrow(() -> new IllegalStateException(
                definition.displayName() + " prefab has no physical blocks."
            ));

        List<PrefabCell> floorCells = cells.stream()
            .filter(cell -> cell.y() == floorY)
            .toList();
        PlacementFootprint footprint = footprintFor(source, anchor, floorCells);
        Map<BlockPosition, Integer> replacedFloorBlocks = new LinkedHashMap<>();

        for (PrefabCell floor : floorCells) {
            Vector3i worldPosition = worldPosition(source, anchor, floor);
            int existingBlock = world.getBlock(worldPosition.x, worldPosition.y, worldPosition.z);
            if (existingBlock == BlockType.EMPTY_ID) {
                return PlacementCandidate.invalid(
                    definition,
                    anchor,
                    footprint,
                    "Der Boden würde über einem Loch oder einer Kante liegen."
                );
            }
            if (world.getFluidId(worldPosition.x, worldPosition.y, worldPosition.z) != 0) {
                return PlacementCandidate.invalid(
                    definition,
                    anchor,
                    footprint,
                    "Der Boden kann nicht in Wasser oder andere Flüssigkeiten gesetzt werden."
                );
            }
            if (world.getBlock(worldPosition.x, worldPosition.y - 1, worldPosition.z)
                == BlockType.EMPTY_ID) {
                return PlacementCandidate.invalid(
                    definition,
                    anchor,
                    footprint,
                    "Unter dem Bauplatz fehlt tragfähiger Boden."
                );
            }
            replacedFloorBlocks.put(
                new BlockPosition(worldPosition.x, worldPosition.y, worldPosition.z),
                existingBlock
            );
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
                    definition,
                    anchor,
                    footprint,
                    "Der Bauplatz ist durch Gelände oder ein Objekt blockiert."
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

    public void showPreview(PlayerRef playerRef, PlacementCandidate candidate) {
        BlockSelection preview = new BlockSelection(requireSource(candidate.definition()));
        preview.relativizeInPlace();
        preview.setPosition(candidate.anchor().x, candidate.anchor().y, candidate.anchor().z);
        playerRef.getPacketHandler().write(preview.toPacketWithSelection());
    }

    public void clearPreview(PlayerRef playerRef) {
        playerRef.getPacketHandler().write(new BlockSelection().toPacketWithSelection());
    }

    public void place(
        PlayerRef playerRef,
        World world,
        PlacementCandidate candidate
    ) {
        if (!candidate.valid()) {
            throw new IllegalArgumentException("Cannot place an invalid Civ prefab candidate.");
        }

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
