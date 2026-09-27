package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads and places the farm prefab from the standalone Hytale Civ Asset Pack.
 */
public final class FarmPrefabService {

    public static final String FARM_PREFAB_KEY = "Civilizations/Farm/Farm_01";
    public static final String ENTRANCE_MARKER_BLOCK_KEY = "Civ_BuildingEntrance";

    public PlacedFarm placeFarm(PlayerRef playerRef, World world, Vector3i anchor) {
        BlockSelection source = PrefabStore.get().getAssetPrefabFromAnyPack(FARM_PREFAB_KEY);
        if (source == null) {
            throw new IllegalStateException(
                "Farm prefab not found. Install the hytale-civ-assets Asset Pack next to the plugin."
            );
        }

        int entranceMarkerBlockId = BlockType.getBlockIdOrUnknown(
            ENTRANCE_MARKER_BLOCK_KEY,
            "Building entrance marker block is unavailable"
        );
        if (entranceMarkerBlockId == BlockType.UNKNOWN_ID) {
            throw new IllegalStateException(
                "Building entrance marker block not found: " + ENTRANCE_MARKER_BLOCK_KEY
            );
        }

        List<Vector3i> entranceBlocks = new ArrayList<>();
        source.forEachBlock((x, y, z, blockHolder) -> {
            if (blockHolder.blockId() != entranceMarkerBlockId) {
                return;
            }

            int offsetX = x - source.getAnchorX();
            int offsetY = y - source.getAnchorY();
            int offsetZ = z - source.getAnchorZ();

            entranceBlocks.add(new Vector3i(
                anchor.x + offsetX,
                anchor.y + offsetY - 1,
                anchor.z + offsetZ
            ));
        });

        if (entranceBlocks.isEmpty()) {
            throw new IllegalStateException(
                "Farm prefab has no " + ENTRANCE_MARKER_BLOCK_KEY + " marker."
            );
        }

        BlockSelection prefab = new BlockSelection(source);
        prefab.place(
            playerRef,
            world,
            new Vector3i(anchor),
            null,
            BlockSelection.DEFAULT_ENTITY_CONSUMER,
            false,
            blockId -> blockId == entranceMarkerBlockId ? BlockType.EMPTY_ID : blockId,
            false
        );

        return new PlacedFarm(List.copyOf(entranceBlocks));
    }

    public record PlacedFarm(List<Vector3i> entranceBlocks) {
        public PlacedFarm {
            entranceBlocks = List.copyOf(entranceBlocks);
            if (entranceBlocks.isEmpty()) {
                throw new IllegalArgumentException("entranceBlocks must not be empty");
            }
        }
    }
}
