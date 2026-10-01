package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.civilizations.core.Profession;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the native Hytale nameplate of loaded Civ inhabitants in sync with a short runtime status.
 * Persistent identity/display-name components remain the plain inhabitant name.
 */
public final class CivNameplateStatusSystem extends EntityTickingSystem<EntityStore> {

    private static final String STATUS_SEPARATOR = " · ";

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final Map<CivUnitRegistry.UnitKey, StatusDebugEntry> debugEntries =
        new ConcurrentHashMap<>();

    public CivNameplateStatusSystem(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry
    ) {
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return NPCEntity.getComponentType();
    }

    @Override
    public void tick(
        float dt,
        int index,
        ArchetypeChunk<EntityStore> archetypeChunk,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        CivUnitRegistry.UnitKey key = unitRegistry.keyOf(ref);
        if (!ref.isValid() || !unitRegistry.isClaimed(ref)) {
            debugEntries.remove(key);
            return;
        }

        CivInhabitantData data = unitRegistry.getInhabitantData(ref);
        if (data == null || !data.hasIdentity()) {
            debugEntries.remove(key);
            return;
        }

        Profession profession = data.profession();
        boolean manualMovementActive = activityRegistry.manualMovementIntent(ref) != null;
        boolean autonomousWorkAllowed = activityRegistry.autonomousWorkAllowed(ref);
        boolean hasMoveTarget = unitRegistry.getMoveTarget(ref) != null;
        String status = CivInhabitantStatusText.derive(
            profession,
            manualMovementActive,
            autonomousWorkAllowed,
            hasMoveTarget
        );
        String desiredText = data.fullName() + STATUS_SEPARATOR + status;

        Nameplate nameplate = commandBuffer.getComponent(ref, Nameplate.getComponentType());
        if (nameplate == null) {
            commandBuffer.putComponent(
                ref,
                Nameplate.getComponentType(),
                new Nameplate(desiredText)
            );
        } else if (!desiredText.equals(nameplate.getText())) {
            nameplate.setText(desiredText);
        }

        debugEntries.put(
            key,
            new StatusDebugEntry(key.entityIndex(), profession, status, desiredText)
        );
    }

    public List<StatusDebugEntry> debugSnapshots(Store<EntityStore> store) {
        return debugEntries.entrySet().stream()
            .filter(entry -> entry.getKey().store() == store)
            .map(Map.Entry::getValue)
            .sorted(Comparator.comparingInt(StatusDebugEntry::entityIndex))
            .toList();
    }

    public record StatusDebugEntry(
        int entityIndex,
        Profession profession,
        String status,
        String nameplateText
    ) {
    }
}
