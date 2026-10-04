package dev.civilizations.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import dev.civilizations.core.Profession;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime cache for persistent Civ inhabitants and transient native movement targets.
 *
 * <p>{@link CivInhabitantData} is the authoritative marker that an NPC belongs to the Civ.
 * Gameplay priority such as manual movement overrides lives in Core state via
 * {@link CivActivityRegistry}; this registry only adapts the currently requested movement target
 * to Hytale's native NPC position slot.</p>
 */
public final class CivUnitRegistry {

    private static final String CIV_INHABITANT_ROLE = "Civ_Inhabitant";
    private static final int CIV_MOVE_POSITION_SLOT = 0;
    private static final int CIV_MINER_MOVE_POSITION_SLOT = 1;

    private final CivInhabitantService inhabitantService;
    private final ProfessionBootstrapInventory professionInventory = new ProfessionBootstrapInventory();
    private final Map<UnitKey, UnitState> units = new ConcurrentHashMap<>();

    public CivUnitRegistry(CivInhabitantService inhabitantService) {
        this.inhabitantService = inhabitantService;
    }

    public UnitKey keyOf(Ref<EntityStore> ref) {
        return new UnitKey(ref.getStore(), ref.getIndex());
    }

    public boolean toggleClaim(Ref<EntityStore> ref) {
        if (inhabitantService.get(ref) != null) {
            professionInventory.leaveProfession(ref, inhabitantService.getProfession(ref));
            cancelMoveTarget(ref);
            inhabitantService.releaseInhabitant(ref);
            units.remove(keyOf(ref));
            return false;
        }

        CivInhabitantData data = inhabitantService.ensureInhabitant(ref);
        if (data == null) {
            return false;
        }
        trackLoaded(ref);
        return true;
    }

    public ClaimResult toggleClaimBuffered(
        Ref<EntityStore> ref,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        if (inhabitantService.isInhabitant(ref, commandBuffer)) {
            professionInventory.leaveProfession(ref, inhabitantService.getProfession(ref));
            cancelMoveTarget(ref);
            inhabitantService.releaseInhabitant(ref, commandBuffer);
            units.remove(keyOf(ref));
            return new ClaimResult(false, null);
        }

        CivInhabitantData data = inhabitantService.ensureInhabitant(ref, commandBuffer);
        if (data == null) {
            return new ClaimResult(false, null);
        }
        trackLoaded(ref);
        return new ClaimResult(true, data);
    }

    public void trackLoaded(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        units.putIfAbsent(keyOf(ref), new UnitState(ref, null));
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
            trackLoaded(ref);
        }
        return true;
    }

    public void forget(Ref<EntityStore> ref) {
        units.remove(keyOf(ref));
    }

    public List<Ref<EntityStore>> workersAt(UUID buildingId) {
        if (buildingId == null) {
            return List.of();
        }
        String workplaceId = buildingId.toString();
        List<Ref<EntityStore>> result = new ArrayList<>();
        for (Map.Entry<UnitKey, UnitState> entry : units.entrySet()) {
            UnitState state = entry.getValue();
            Ref<EntityStore> ref = state.ref();
            if (ref == null || !ref.isValid()) {
                units.remove(entry.getKey(), state);
                continue;
            }
            CivInhabitantData data = inhabitantService.get(ref);
            if (data != null && workplaceId.equals(data.workplaceId())) {
                result.add(ref);
            }
        }
        return List.copyOf(result);
    }

    public void assignWorkplace(Ref<EntityStore> ref, UUID buildingId) {
        if (isClaimed(ref)) {
            inhabitantService.assignWorkplace(ref, buildingId);
        }
    }

    public void clearWorkplace(Ref<EntityStore> ref) {
        assignWorkplace(ref, null);
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

    public void clearMoveTarget(Ref<EntityStore> ref) {
        setMoveTargetInternal(ref, null);
    }

    public void setMoveTarget(Ref<EntityStore> ref, Vector3d target) {
        setMoveTargetInternal(ref, target);
    }

    public void cancelMoveTarget(Ref<EntityStore> ref) {
        setMoveTargetInternal(ref, null);
    }

    private void setMoveTargetInternal(Ref<EntityStore> ref, Vector3d target) {
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

        units.put(key, new UnitState(current.ref(), nextTarget));
        applyNativePath(ref, nextTarget);
    }

    private void applyNativePath(Ref<EntityStore> ref, Vector3d target) {
        NPCEntity npc = ref.getStore().getComponent(ref, NPCEntity.getComponentType());
        if (npc == null || !CIV_INHABITANT_ROLE.equals(npc.getRoleName())) {
            return;
        }

        MarkedEntitySupport markedEntitySupport =
            ref.getStore().getComponent(ref, MarkedEntitySupport.getComponentType());
        if (markedEntitySupport == null) {
            return;
        }

        Vector3d normalMoveTarget = markedEntitySupport.getStoredPosition(CIV_MOVE_POSITION_SLOT);
        Vector3d minerMoveTarget = markedEntitySupport.getStoredPosition(CIV_MINER_MOVE_POSITION_SLOT);

        if (target == null) {
            normalMoveTarget.set(Vector3dUtil.MIN);
            minerMoveTarget.set(Vector3dUtil.MIN);
            return;
        }

        if (inhabitantService.getProfession(ref) == Profession.MINER) {
            normalMoveTarget.set(Vector3dUtil.MIN);
            minerMoveTarget.set(target);
            return;
        }

        minerMoveTarget.set(Vector3dUtil.MIN);
        normalMoveTarget.set(target);
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

        Profession previous = inhabitantService.getProfession(ref);
        if (previous == profession) {
            return;
        }
        professionInventory.leaveProfession(ref, previous);
        inhabitantService.assignProfession(ref, profession);
        professionInventory.enterProfession(ref, profession);

        Vector3d activeTarget = getMoveTarget(ref);
        if (activeTarget != null) {
            applyNativePath(ref, activeTarget);
        }
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
        Vector3d moveTarget
    ) {
    }
}
