package dev.civilizations.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

/**
 * Hytale boundary for crop planting.
 *
 * <p>Civ owns the decision that a farmer should sow wheat and the field
 * position. This adapter owns the engine-specific planting operation.
 *
 * <p>The current pinned Hytale runtime does not expose a verified native
 * server-side NPC seed-planting operation. Until such an API exists, planting
 * is reported as unsupported rather than simulating a player interaction or
 * recreating Hytale's farming rules.
 */
public final class FarmPlantingService {

    public Result plantWheat(
        Ref<EntityStore> farmer,
        World world,
        Vector3i soilPosition,
        ItemStack seed,
        CommandBuffer<EntityStore> entityAccessor
    ) {
        return Result.UNSUPPORTED;
    }

    public enum Result {
        PLANTED,
        UNSUPPORTED,
        FAILED
    }
}
