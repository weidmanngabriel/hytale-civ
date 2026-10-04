package dev.civilizations.hytale;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.MovementIntent;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WorldPosition;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Executes Core manual movement intents through Hytale's native NPC movement target.
 */
public final class CivManualMovementSystem extends EntityTickingSystem<EntityStore> {

    private static final String TYPE_TAG = "civ.type";
    private static final String BUILDING_TAG = "civ.building";
    private static final String WORKPLACE_ACCESS = "workplace_access";
    private static final String MINE_BUILDING = "mine";
    private static final double ARRIVAL_HORIZONTAL_DISTANCE = 1.0;
    private static final double ARRIVAL_VERTICAL_DISTANCE = 2.0;

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final BuildingPlacementRegistry buildingRegistry;

    public CivManualMovementSystem(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        BuildingPlacementRegistry buildingRegistry
    ) {
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
        this.buildingRegistry = buildingRegistry;
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

        // The shared Core activity state owns the post-command resume delay, so every profession
        // gets identical interruption behavior without profession-specific timers.
        activityRegistry.advance(ref, dt);

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

        World world = store.getExternalData().getWorld();
        if (teleportUndergroundMinerToAccess(ref, world, transform, commandBuffer)) {
            // Keep the MovementIntent active. On the next tick the NPC continues from the mine
            // access towards the player's original clicked destination using native navigation.
            unitRegistry.clearMoveTarget(ref);
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

    private boolean teleportUndergroundMinerToAccess(
        Ref<EntityStore> ref,
        World world,
        TransformComponent transform,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        if (unitRegistry.getProfession(ref) != Profession.MINER) return false;

        BuildingPlacementRegistry.BuildingInstance mine = assignedMine(ref, world.getWorldConfig().getUuid());
        if (mine == null) return false;

        PrefabPlacementService.PlacedMarker access = marker(world, mine, WORKPLACE_ACCESS);
        if (access == null || access.bounds() == null) return false;
        if (transform.getPosition().y >= access.bounds().minY() - ARRIVAL_VERTICAL_DISTANCE) {
            return false;
        }

        BuildingBounds bounds = access.bounds();
        Vector3d exit = new Vector3d(
            (bounds.minX() + bounds.maxX()) * 0.5,
            (bounds.minY() + bounds.maxY()) * 0.5,
            (bounds.minZ() + bounds.maxZ()) * 0.5
        );
        commandBuffer.putComponent(
            ref,
            Teleport.getComponentType(),
            new Teleport(exit, transform.getRotation())
        );
        return true;
    }

    private BuildingPlacementRegistry.BuildingInstance assignedMine(Ref<EntityStore> ref, UUID worldId) {
        CivInhabitantData data = unitRegistry.getInhabitantData(ref);
        if (data == null || data.workplaceId() == null || data.workplaceId().isBlank()) return null;
        try {
            BuildingPlacementRegistry.BuildingInstance building =
                buildingRegistry.find(worldId, UUID.fromString(data.workplaceId()));
            return building != null && MINE_BUILDING.equals(building.buildingType()) ? building : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static PrefabPlacementService.PlacedMarker marker(
        World world,
        BuildingPlacementRegistry.BuildingInstance building,
        String type
    ) {
        PrefabPlacementService.PlacedMarker marker = building.semanticVolumes().stream()
            .filter(candidate -> candidate.hasTag(TYPE_TAG, type))
            .filter(candidate -> candidate.hasTag(BUILDING_TAG, MINE_BUILDING))
            .findFirst()
            .orElse(null);
        if (marker == null || marker.bounds() != null) return marker;

        TriggerVolumeManager manager = world.getEntityStore().getStore().getResource(
            TriggerVolumesPlugin.get().getManagerResourceType()
        );
        var volume = manager == null ? null : manager.getVolume(marker.id());
        if (volume == null || volume.getShape() == null || volume.getPosition() == null) return marker;
        Vector3d min = new Vector3d();
        Vector3d max = new Vector3d();
        volume.getShape().getWorldAABB(volume.getPosition(), min, max);
        return new PrefabPlacementService.PlacedMarker(
            marker.id(), marker.position(), marker.tags(),
            new BuildingBounds(min.x, min.y, min.z, max.x, max.y, max.z)
        );
    }

    private static Vector3d toVector(WorldPosition position) {
        return new Vector3d(position.x(), position.y(), position.z());
    }

    private static boolean hasArrived(Vector3d position, Vector3d target) {
        double dx = position.x - target.x;
        double dz = position.z - target.z;
        double horizontalDistanceSquared = dx * dx + dz * dz;
        return horizontalDistanceSquared
            <= ARRIVAL_HORIZONTAL_DISTANCE * ARRIVAL_HORIZONTAL_DISTANCE
            && Math.abs(position.y - target.y) <= ARRIVAL_VERTICAL_DISTANCE;
    }
}
