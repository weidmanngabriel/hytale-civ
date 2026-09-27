package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.Profession;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime-only registry used by the RTS validation spike.
 *
 * <p>Claimed NPCs, professions and movement targets intentionally remain runtime-only
 * until Civ inhabitants have a persistent lifecycle.</p>
 */
public final class CivUnitRegistry {

    private static final double FORMATION_SPACING = 1.4;

    private final Map<UnitKey, UnitState> units = new ConcurrentHashMap<>();

    public UnitKey keyOf(Ref<EntityStore> ref) {
        return new UnitKey(ref.getStore(), ref.getIndex());
    }

    public boolean toggleClaim(Ref<EntityStore> ref) {
        UnitKey key = keyOf(ref);
        UnitState existing = units.get(key);

        if (existing != null && existing.ref().isValid()) {
            units.remove(key);
            return false;
        }

        units.put(key, new UnitState(ref, null, null));
        return true;
    }

    public boolean isClaimed(Ref<EntityStore> ref) {
        UnitKey key = keyOf(ref);
        UnitState state = units.get(key);

        if (state == null) {
            return false;
        }

        if (!state.ref().isValid()) {
            units.remove(key, state);
            return false;
        }

        return true;
    }

    public void forget(Ref<EntityStore> ref) {
        units.remove(keyOf(ref));
    }

    public Vector3d getMoveTarget(Ref<EntityStore> ref) {
        UnitKey key = keyOf(ref);
        UnitState state = units.get(key);

        if (state == null) {
            return null;
        }

        if (!state.ref().isValid()) {
            units.remove(key, state);
            return null;
        }

        return state.moveTarget();
    }

    public int assignMoveTargets(Collection<Ref<EntityStore>> refs, Vector3i targetBlock) {
        List<Ref<EntityStore>> valid = refs.stream()
            .filter(this::isClaimed)
            .toList();

        int count = valid.size();
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
            Ref<EntityStore> ref = valid.get(index);
            UnitKey key = keyOf(ref);

            units.computeIfPresent(key, (ignored, state) -> new UnitState(
                state.ref(),
                new Vector3d(
                    targetBlock.x + 0.5 + offsetX,
                    targetBlock.y + 1.0,
                    targetBlock.z + 0.5 + offsetZ
                ),
                state.profession()
            ));
        }

        return count;
    }

    public void clearMoveTarget(Ref<EntityStore> ref) {
        setMoveTarget(ref, null);
    }

    public void setMoveTarget(Ref<EntityStore> ref, Vector3d target) {
        UnitKey key = keyOf(ref);
        units.computeIfPresent(
            key,
            (ignored, state) -> new UnitState(
                state.ref(),
                target == null ? null : new Vector3d(target),
                state.profession()
            )
        );
    }

    public void assignProfession(Ref<EntityStore> ref, Profession profession) {
        UnitKey key = keyOf(ref);
        units.computeIfPresent(
            key,
            (ignored, state) -> new UnitState(state.ref(), state.moveTarget(), profession)
        );
    }

    public Profession getProfession(Ref<EntityStore> ref) {
        UnitState state = units.get(keyOf(ref));
        return state == null ? null : state.profession();
    }

    public record UnitKey(Store<EntityStore> store, int entityIndex) {
    }

    private record UnitState(
        Ref<EntityStore> ref,
        Vector3d moveTarget,
        Profession profession
    ) {
    }
}
