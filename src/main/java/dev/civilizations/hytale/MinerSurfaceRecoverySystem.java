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
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.MineTunnelGeometry;
import dev.civilizations.core.Profession;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Temporary safety net for native miner navigation escaping to the surface. */
public final class MinerSurfaceRecoverySystem extends EntityTickingSystem<EntityStore> {

    static final double RECOVERY_DELAY_SECONDS = 1.5;

    private static final String TYPE_TAG = "civ.type";
    private static final String BUILDING_TAG = "civ.building";
    private static final String TUNNEL_CONNECTOR = "mine_tunnel_connector";
    private static final String MINE_BUILDING = "mine";

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final BuildingPlacementRegistry buildingRegistry;
    private final MineTunnelRegistry tunnelRegistry;
    private final Map<CivUnitRegistry.UnitKey, RecoveryRuntime> runtimes = new ConcurrentHashMap<>();

    public MinerSurfaceRecoverySystem(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        BuildingPlacementRegistry buildingRegistry,
        MineTunnelRegistry tunnelRegistry
    ) {
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
        this.buildingRegistry = buildingRegistry;
        this.tunnelRegistry = tunnelRegistry;
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
        if (!ref.isValid() || unitRegistry.getProfession(ref) != Profession.MINER) {
            runtimes.remove(key);
            return;
        }
        if (!activityRegistry.autonomousWorkAllowed(ref)) {
            runtimes.remove(key);
            return;
        }

        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        World world = store.getExternalData().getWorld();
        UUID worldId = world.getWorldConfig().getUuid();
        BuildingPlacementRegistry.BuildingInstance mine = assignedMine(ref, worldId);
        if (mine == null) {
            runtimes.remove(key);
            return;
        }

        RecoveryRuntime runtime = runtimes.computeIfAbsent(key, ignored -> new RecoveryRuntime());
        Vector3d position = transform.getPosition();
        BlockPosition feet = blockPosition(position);
        boolean inTunnel = insideKnownTunnel(worldId, mine.id(), feet);
        boolean belowReferenceLevel = position.y < mine.bounds().minY();

        if (inTunnel || belowReferenceLevel) {
            runtime.armed = true;
            runtime.invalidSurfaceSeconds = 0.0;
            return;
        }
        if (mine.bounds().containsBlock(feet)) {
            runtime.invalidSurfaceSeconds = 0.0;
            return;
        }
        if (!runtime.armed) return;

        runtime.invalidSurfaceSeconds += dt;
        if (runtime.invalidSurfaceSeconds < RECOVERY_DELAY_SECONDS) return;

        PrefabPlacementService.PlacedMarker connector = marker(world, mine, TUNNEL_CONNECTOR);
        if (connector == null || connector.bounds() == null) {
            runtime.invalidSurfaceSeconds = 0.0;
            return;
        }

        BuildingBounds bounds = connector.bounds();
        Vector3d target = new Vector3d(
            (bounds.minX() + bounds.maxX()) * 0.5,
            bounds.minY(),
            (bounds.minZ() + bounds.maxZ()) * 0.5
        );
        commandBuffer.putComponent(
            ref,
            Teleport.getComponentType(),
            new Teleport(target, transform.getRotation())
        );
        runtime.invalidSurfaceSeconds = 0.0;
    }

    private static BlockPosition blockPosition(Vector3d position) {
        return new BlockPosition(
            (int) Math.floor(position.x),
            (int) Math.floor(position.y),
            (int) Math.floor(position.z)
        );
    }

    private boolean insideKnownTunnel(UUID worldId, UUID mineId, BlockPosition feet) {
        for (MineTunnelGeometry geometry : tunnelRegistry.geometriesForMine(worldId, mineId).values()) {
            if (geometry.excavationBlocks().contains(feet)) return true;
        }
        return false;
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

    private static final class RecoveryRuntime {
        private boolean armed;
        private double invalidSurfaceSeconds;
    }
}
