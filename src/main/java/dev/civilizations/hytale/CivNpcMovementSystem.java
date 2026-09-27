package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.Steering;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.movement.steeringforces.SteeringForcePursue;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.system.EntityTickingSystem;
import org.joml.Vector3d;

/**
 * Drives claimed Civ NPCs toward runtime RTS movement targets.
 *
 * <p>This deliberately validates Hytale steering and collision-aware locomotion only.
 * It does not claim to provide route finding around arbitrary obstacles.</p>
 */
public final class CivNpcMovementSystem extends EntityTickingSystem<EntityStore> {

    private static final double STOP_DISTANCE = 0.35;
    private static final double SLOWDOWN_DISTANCE = 1.5;

    private final CivUnitRegistry unitRegistry;

    public CivNpcMovementSystem(CivUnitRegistry unitRegistry) {
        this.unitRegistry = unitRegistry;
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
        Vector3d target = unitRegistry.getMoveTarget(ref);

        if (target == null) {
            return;
        }

        if (!ref.isValid()) {
            unitRegistry.forget(ref);
            return;
        }

        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());

        if (npc == null || transform == null) {
            unitRegistry.forget(ref);
            return;
        }

        Vector3d position = transform.getPosition();
        double horizontalDistanceSquared =
            squared(position.x - target.x) + squared(position.z - target.z);

        if (horizontalDistanceSquared <= STOP_DISTANCE * STOP_DISTANCE) {
            unitRegistry.clearMoveTarget(ref);
            return;
        }

        Role role = npc.getRole();
        if (role == null) {
            unitRegistry.clearMoveTarget(ref);
            return;
        }

        MotionController motionController = role.getActiveMotionController();
        if (motionController == null || !motionController.canSteer(ref, commandBuffer)) {
            return;
        }

        Steering bodySteering = role.getBodySteering();
        SteeringForcePursue pursue = new SteeringForcePursue();
        pursue.setPositions(position, target);
        pursue.setStopDistance(STOP_DISTANCE);
        pursue.setSlowdownDistance(SLOWDOWN_DISTANCE);

        if (!pursue.compute(bodySteering)) {
            unitRegistry.clearMoveTarget(ref);
            return;
        }

        motionController.steer(
            ref,
            role,
            bodySteering,
            role.getHeadSteering(),
            dt,
            commandBuffer
        );
    }

    private static double squared(double value) {
        return value * value;
    }
}
