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
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.Profession;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary safety net for native miner navigation escaping to the surface.
 *
 * <p>This deliberately uses the placed mine's Y level as a surface heuristic. Once terrain-aware
 * world generation matters (for example mines inside mountains), replace that heuristic with a
 * terrain-aware classification instead of teaching the mining algorithm about surface shapes.</p>
 */
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
    private final Map<CivUnitRegistry.UnitKey, Double> invalidSurfaceSeconds = new ConcurrentHashMap<>();

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
            invalidSurfaceSeconds.remove(key);
            return;
        }

        // Manual commands and their resume delay always win. Recovery must never fight the player.
        if (!activityRegistry.autonomousWorkAllowed(ref)) {
            invalidSurfaceSeconds.remove(key);
            return;
        }

        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        World world = store.getExternalData().getWorld();
        UUID worldId = world.getWorldConfig().getUuid();
        BuildingPlacementRegistry.BuildingInstance mine = assignedMine(ref, worldId);
        if (mine == null) {
            invalidSurfaceSeconds.remove(key);
            return;
        }

        Vector3d position = transform.getPosition();
        boolean suspicious = shouldRecover(worldId, mine, position);
        if (!suspicious) {
            invalidSurfaceSeconds.remove(key);
            return;
        }

        double elapsed = invalidSurfaceSeconds.merge(key, (double) dt, Double::sum);
        if (elapsed < RECOVERY_DELAY_SECONDS) return;

        PrefabPlacementService.PlacedMarker connector = marker(world, mine, TUNNEL_CONNECTOR);
        if (connector == null || connector.bounds() == null) {
            invalidSurfaceSeconds.remove(key);
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
        invalidSurfaceSeconds.remove(key);
    }

    boolean shouldRecover(
        UUID worldId,
        BuildingPlacementRegistry.BuildingInstance mine,
        Vector3d position
    ) {
        if (position.y < mine.bounds().minY()) return false;
        BlockPosition feet = new BlockPosition(
            (int) Math.floor(position.x),
            (int) Math.floor(position.y),
            (int) Math.floor(position.z)
        );
        if (mine.bounds().containsBlock(feet)) return false;
        return !insideKnownTunnel(worldId, mine.id(), feet);
    }

    private boolean insideKnownTunnel(UUID worldId, UUID mineId, BlockPosition feet) {
        for (MineSegment segment : tunnelRegistry.segmentsForMine(worldId, mineId)) {
            if (feet.y() < segment.start().y()
                || feet.y() >= segment.start().y() + dev.civilizations.core.MineTuning.TUNNEL_HEIGHT_BLOCKS) {
                continue;
            }
            BuildingBounds horizontal = segment.horizontalBounds();
            if (feet.x() >= Math.floor(horizontal.minX())
                && feet.x() < Math.ceil(horizontal.maxX())
                && feet.z() >= Math.floor(horizontal.minZ())
                && feet.z() < Math.ceil(horizontal.maxZ())) {
                return true;
            }
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
}
