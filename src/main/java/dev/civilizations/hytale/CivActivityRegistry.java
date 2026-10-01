package dev.civilizations.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.civilizations.core.InhabitantActivity;
import dev.civilizations.core.MovementIntent;
import dev.civilizations.core.WorldPosition;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hytale-side ownership of headless inhabitant activity state.
 *
 * <p>The contained activity objects remain pure Core state. This registry only associates them
 * with currently loaded Hytale entities.</p>
 */
public final class CivActivityRegistry {

    private final CivUnitRegistry unitRegistry;
    private final Map<CivUnitRegistry.UnitKey, InhabitantActivity> activities =
        new ConcurrentHashMap<>();

    public CivActivityRegistry(CivUnitRegistry unitRegistry) {
        this.unitRegistry = unitRegistry;
    }

    public boolean orderManualMove(Ref<EntityStore> ref, WorldPosition destination) {
        if (!unitRegistry.isClaimed(ref)) {
            return false;
        }

        activity(ref).orderManualMove(destination);
        return true;
    }

    public MovementIntent manualMovementIntent(Ref<EntityStore> ref) {
        InhabitantActivity activity = activities.get(unitRegistry.keyOf(ref));
        return activity == null ? null : activity.manualMovementIntent();
    }

    public void advance(Ref<EntityStore> ref, double deltaSeconds) {
        InhabitantActivity activity = activities.get(unitRegistry.keyOf(ref));
        if (activity != null) {
            activity.advance(deltaSeconds);
        }
    }

    public boolean autonomousWorkAllowed(Ref<EntityStore> ref) {
        InhabitantActivity activity = activities.get(unitRegistry.keyOf(ref));
        return activity == null || activity.autonomousWorkAllowed();
    }

    public boolean completeManualMove(Ref<EntityStore> ref) {
        InhabitantActivity activity = activities.get(unitRegistry.keyOf(ref));
        return activity != null && activity.completeManualMove();
    }

    public boolean cancelManualMove(Ref<EntityStore> ref) {
        InhabitantActivity activity = activities.get(unitRegistry.keyOf(ref));
        return activity != null && activity.cancelManualMove();
    }

    public List<ActivityDebugEntry> debugSnapshots(Store<EntityStore> store) {
        return activities.entrySet().stream()
            .filter(entry -> entry.getKey().store() == store)
            .map(entry -> new ActivityDebugEntry(
                entry.getKey().entityIndex(),
                entry.getValue().snapshot()
            ))
            .sorted(Comparator.comparingInt(ActivityDebugEntry::entityIndex))
            .toList();
    }

    public void forget(Ref<EntityStore> ref) {
        activities.remove(unitRegistry.keyOf(ref));
    }

    private InhabitantActivity activity(Ref<EntityStore> ref) {
        return activities.computeIfAbsent(
            unitRegistry.keyOf(ref),
            ignored -> new InhabitantActivity()
        );
    }

    public record ActivityDebugEntry(
        int entityIndex,
        InhabitantActivity.ActivitySnapshot snapshot
    ) {
    }
}
