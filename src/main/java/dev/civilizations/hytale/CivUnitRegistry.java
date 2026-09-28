package dev.civilizations.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import dev.civilizations.core.Profession;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime cache for persistent Civ inhabitants and transient movement targets.
 *
 * <p>{@link CivInhabitantData} is the authoritative marker that an NPC belongs to the Civ;
 * this registry only caches live references and movement targets.</p>
 */
public final class CivUnitRegistry {

    private static final double FORMATION_SPACING = 1.4;
    private static final String CIV_INHABITANT_ROLE = "Civ_Inhabitant";
    // Civ_Inhabitant declares exactly one ReadPosition slot: CivMoveTarget.
    private static final int CIV_MOVE_POSITION_SLOT = 0;

    private final CivInhabitantService inhabitantService;
    private final Map<UnitKey, UnitState> units = new ConcurrentHashMap<>();

    public CivUnitRegistry(CivInhabitantService inhabitantService) {
        this.inhabitantService = inhabitantService;
    }

    public UnitKey keyOf(Ref<EntityStore> ref) {
        return new UnitKey(ref.getStore(), ref.getIndex());
    }

    public boolean toggleClaim(Ref<EntityStore> ref) {
        if (inhabitantService.get(ref) != null) {
            cancelMoveTarget(ref);
            inhabitantService.releaseInhabitant(ref);
            units.remove(keyOf(ref));
            return false;
        }

        CivInhabitantData data = inhabitantService.ensureInhabitant(ref);
        if (data == null) {
            return false;
        }
        units.putIfAbsent(keyOf(ref), new UnitState(ref, null, false));
        return true;
    }

    public ClaimResult toggleClaimBuffered(
        Ref<EntityStore> ref,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        if (commandBuffer.getComponent(ref, inhabitantService.componentType()) != null) {
            cancelMoveTarget(ref);
            inhabitantService.releaseInhabitant(ref, commandBuffer);
            units.remove(keyOf(ref));
            return new ClaimResult(false, null);
        }

        CivInhabitantData data = inhabitantService.ensureInhabitant(ref, commandBuffer);
        if (data == null) {
            return new ClaimResult(false, null);
        }
        units.putIfAbsent(keyOf(ref), new UnitState(ref, null, false));
        return new ClaimResult(true, data);
    }

    public CivInhabitantData getInhabitantData(Ref<EntityStore> ref) {
        return inhabitantService.get(ref);
    }

    public boolean isClaimed(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return false;
        }

        UnitKey key = keyOf(ref);
        UnitState state = units.get(key);
        if (state != null && !state.ref().isValid()) {
            units.remove(key, state);
            state = null;
        }

        if (inhabitantService.get(ref) == null) {
            if (state != null) {
                units.remove(key, state);
            }
            return false;
        }

        if (state == null) {
            units.put(key, new UnitState(ref, null, false));
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
            setManualMoveTarget(
                ref,
                new Vector3d(
                    targetBlock.x + 0.5 + offsetX,
                    targetBlock.y + 1.0,
                    targetBlock.z + 0.5 + offsetZ
                )
            );
        }

        return count;
    }

    public void clearMoveTarget(Ref<EntityStore> ref) {
        setJobMoveTarget(ref, null);
    }

    public void setMoveTarget(Ref<EntityStore> ref, Vector3d target) {
        setJobMoveTarget(ref, target);
    }

    public boolean continueManualMove(Ref<EntityStore> ref, Vector3d position) {
        UnitState state = units.get(keyOf(ref));
        if (state == null || !state.manualMove() || state.moveTarget() == null) {
            return false;
        }
        if (position.distanceSquared(state.moveTarget()) > 0.36) {
            return true;
        }

        units.put(keyOf(ref), new UnitState(ref, null, false));
        applyNativePath(ref, null);
        return false;
    }

    public void cancelMoveTarget(Ref<EntityStore> ref) {
        UnitState state = units.get(keyOf(ref));
        if (state != null) {
            units.put(keyOf(ref), new UnitState(ref, null, false));
        }
        applyNativePath(ref, null);
    }

    private void setManualMoveTarget(Ref<EntityStore> ref, Vector3d target) {
        setMoveTargetInternal(ref, target, true);
    }

    private void setJobMoveTarget(Ref<EntityStore> ref, Vector3d target) {
        UnitState current = units.get(keyOf(ref));
        if (current != null && current.manualMove()) {
            return;
        }
        setMoveTargetInternal(ref, target, false);
    }

    private void setMoveTargetInternal(Ref<EntityStore> ref, Vector3d target, boolean manualMove) {
        UnitKey key = keyOf(ref);
        UnitState current = units.get(key);
        if (current == null || !current.ref().isValid()) {
            units.remove(key);
            return;
        }

        Vector3d nextTarget = target == null ? null : new Vector3d(target);
        if (sameTarget(current.moveTarget(), nextTarget)) {
            return;
        }

        units.put(key, new UnitState(current.ref(), nextTarget, manualMove && nextTarget != null));
        applyNativePath(ref, nextTarget);
    }

    private static void applyNativePath(Ref<EntityStore> ref, Vector3d target) {
        NPCEntity npc = ref.getStore().getComponent(ref, NPCEntity.getComponentType());
        if (npc == null || !CIV_INHABITANT_ROLE.equals(npc.getRoleName())) {
            return;
        }

        MarkedEntitySupport markedEntitySupport =
            ref.getStore().getComponent(ref, MarkedEntitySupport.getComponentType());
        if (markedEntitySupport == null) {
            return;
        }

        Vector3d moveTarget = markedEntitySupport.getStoredPosition(CIV_MOVE_POSITION_SLOT);
        if (target == null) {
            moveTarget.set(Vector3dUtil.MIN);
            return;
        }

        moveTarget.set(target);
    }

    private static boolean sameTarget(Vector3d current, Vector3d next) {
        if (current == null || next == null) {
            return current == next;
        }
        return current.distanceSquared(next) < 0.0001;
    }

    public void assignProfession(Ref<EntityStore> ref, Profession profession) {
        if (!isClaimed(ref)) {
            return;
        }

        inhabitantService.assignProfession(ref, profession);
    }

    public Profession getProfession(Ref<EntityStore> ref) {
        return inhabitantService.getProfession(ref);
    }

    public record ClaimResult(boolean claimed, CivInhabitantData inhabitantData) {
    }

    public record UnitKey(Store<EntityStore> store, int entityIndex) {
    }

    private record UnitState(
        Ref<EntityStore> ref,
        Vector3d moveTarget,
        boolean manualMove
    ) {
    }
}
