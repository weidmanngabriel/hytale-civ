package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime-only registry used by the RTS validation spike.
 *
 * <p>Claimed NPCs and movement targets intentionally live outside the future simulation model.
 * This keeps the engine-validation code small until Civ inhabitants have a real lifecycle.</p>
 */
public final class CivUnitRegistry {

    private static final double FORMATION_SPACING = 1.4;

    private final Set<Ref<EntityStore>> claimed = ConcurrentHashMap.newKeySet();
    private final Map<Ref<EntityStore>, Vector3d> moveTargets = new ConcurrentHashMap<>();

    public boolean toggleClaim(Ref<EntityStore> ref) {
        if (claimed.remove(ref)) {
            moveTargets.remove(ref);
            return false;
        }

        claimed.add(ref);
        return true;
    }

    public boolean isClaimed(Ref<EntityStore> ref) {
        return ref.isValid() && claimed.contains(ref);
    }

    public void forget(Ref<EntityStore> ref) {
        claimed.remove(ref);
        moveTargets.remove(ref);
    }

    public Vector3d getMoveTarget(Ref<EntityStore> ref) {
        return moveTargets.get(ref);
    }

    public int assignMoveTargets(Collection<Ref<EntityStore>> refs, Vector3i targetBlock) {
        Ref<EntityStore>[] valid = refs.stream()
            .filter(this::isClaimed)
            .toArray(Ref[]::new);

        int count = valid.length;
        if (count == 0) {
            return 0;
        }

        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / columns);

        for (int index = 0; index < count; index++) {
            int column = index % columns;
            int row = index / columns;

            double offsetX = (column - (columns - 1) / 2.0) * FORMATION_SPACING;
            double offsetZ = (row - (rows - 1) / 2.0) * FORMATION_SPACING;

            moveTargets.put(valid[index], new Vector3d(
                targetBlock.x + 0.5 + offsetX,
                targetBlock.y + 1.0,
                targetBlock.z + 0.5 + offsetZ
            ));
        }

        return count;
    }

    public void clearMoveTarget(Ref<EntityStore> ref) {
        moveTargets.remove(ref);
    }
}
