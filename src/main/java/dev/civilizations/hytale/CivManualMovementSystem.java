package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.civilizations.core.MovementIntent;
import dev.civilizations.core.WorldPosition;
import org.joml.Vector3d;

/**
 * Executes Core manual movement intents through Hytale's native NPC movement target.
 */
public final class CivManualMovementSystem extends EntityTickingSystem<EntityStore> {

    private static final double ARRIVAL_DISTANCE = 0.6;

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;

    public CivManualMovementSystem(
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
        if (!ref.isValid()) {
            activityRegistry.forget(ref);
            unitRegistry.forget(ref);
            return;
        }

        MovementIntent intent = activityRegistry.manualMovementIntent(ref);
        if (intent == null) {
            return;
        }

        if (!unitRegistry.isClaimed(ref)) {
            activityRegistry.forget(ref);
            unitRegistry.cancelMoveTarget(ref);
            return;
        }

        TransformComponent transform =
            commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        Vector3d target = toVector(intent.destination());
        if (hasArrived(transform.getPosition(), target)) {
            activityRegistry.completeManualMove(ref);
            unitRegistry.clearMoveTarget(ref);
            return;
        }

        unitRegistry.setMoveTarget(ref, target);
    }

    private static Vector3d toVector(WorldPosition position) {
        return new Vector3d(position.x(), position.y(), position.z());
    }

    private static boolean hasArrived(Vector3d position, Vector3d target) {
        return position.distanceSquared(target) <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    }
}
