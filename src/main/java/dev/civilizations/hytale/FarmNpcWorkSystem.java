package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.civilizations.core.FarmBuilding;
import org.joml.Vector3d;

/**
 * Translates the core farm work state into Hytale NPC movement targets.
 */
public final class FarmNpcWorkSystem extends EntityTickingSystem<EntityStore> {

    private static final double ARRIVAL_DISTANCE = 0.45;
    private static final String WHEAT_ITEM_ID = "Plant_Crop_Wheat_Item";

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final FarmBuildingRegistry farmRegistry;
    private final FarmFieldRegistry fieldRegistry;
    private final NativeBuildingStorage storage = new NativeBuildingStorage();

    public FarmNpcWorkSystem(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        FarmBuildingRegistry farmRegistry,
        FarmFieldRegistry fieldRegistry
    ) {
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
        this.farmRegistry = farmRegistry;
        this.fieldRegistry = fieldRegistry;
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
        FarmBuildingRegistry.FarmSite site = farmRegistry.getAssignment(ref);

        if (site == null) {
            return;
        }

        if (!ref.isValid()) {
            farmRegistry.unassignFarmer(ref);
            activityRegistry.forget(ref);
            unitRegistry.forget(ref);
            return;
        }

        TransformComponent transform =
            commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            farmRegistry.unassignFarmer(ref);
            return;
        }

        FarmBuilding building = site.building();
        Vector3d position = transform.getPosition();
        if (!activityRegistry.autonomousWorkAllowed(ref)) {
            return;
        }

        FarmFieldRegistry.FieldSite field =
            fieldRegistry.nearestField(site.worldId(), building.entranceBlock());

        switch (building.workState()) {
            case WAITING_FOR_FARMER, WAITING_FOR_INPUTS -> unitRegistry.clearMoveTarget(ref);
            case WALKING_TO_FARM -> {
                Vector3d target = site.entranceTarget();
                unitRegistry.setMoveTarget(ref, target);
                if (hasArrived(position, target)) {
                    unitRegistry.clearMoveTarget(ref);
                    building.arriveAtFarm();
                }
            }
            case WALKING_TO_FIELD -> {
                if (field == null) {
                    unitRegistry.clearMoveTarget(ref);
                    return;
                }
                Vector3d target = field.workTarget();
                unitRegistry.setMoveTarget(ref, target);
                if (hasArrived(position, target)) {
                    unitRegistry.clearMoveTarget(ref);
                    building.arriveAtField();
                }
            }
            case WORKING_FIELD -> {
                unitRegistry.clearMoveTarget(ref);
                if (building.advanceWork(dt)) {
                    unitRegistry.setMoveTarget(ref, site.outputStorageTarget());
                }
            }
            case RETURNING_TO_STORAGE -> {
                Vector3d target = site.outputStorageTarget();
                unitRegistry.setMoveTarget(ref, target);
                if (hasArrived(position, target)) {
                    unitRegistry.clearMoveTarget(ref);
                    building.arriveAtFarm();
                }
            }
            case STORING_OUTPUT -> {
                unitRegistry.clearMoveTarget(ref);
                World world = Universe.get().getWorld(site.worldId());
                if (storage.tryStore(world, site.outputStorageMarker(), new ItemStack(WHEAT_ITEM_ID, 1))) {
                    building.outputStored();
                }
            }
        }
    }

    private static boolean hasArrived(Vector3d position, Vector3d target) {
        double dx = position.x - target.x;
        double dy = position.y - target.y;
        double dz = position.z - target.z;
        return dx * dx + dy * dy + dz * dz <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    }
}
