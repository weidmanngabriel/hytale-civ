package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

/**
 * Loads and places the farm prefab from the standalone Hytale Civ Asset Pack.
 */
public final class FarmPrefabService {

    public static final String FARM_PREFAB_KEY = "Civilizations/Farm/Farm_01";

    public void placeFarm(PlayerRef playerRef, World world, Vector3i anchor) {
        BlockSelection source = PrefabStore.get().getAssetPrefabFromAnyPack(FARM_PREFAB_KEY);
        if (source == null) {
            throw new IllegalStateException(
                "Farm prefab not found. Install the hytale-civ-assets Asset Pack next to the plugin."
            );
        }

        BlockSelection prefab = new BlockSelection(source);
        prefab.place(
            playerRef,
            world,
            new Vector3i(anchor),
            null,
            BlockSelection.DEFAULT_ENTITY_CONSUMER
        );
    }
}
