package dev.civilizations.hytale;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.NavState;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.MineNavigationAnchor;
import dev.civilizations.core.MineNavigationPolicy;
import dev.civilizations.core.MineNetwork;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineTunnelGeometry;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WorldPosition;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Observes native miner movement and maintains Civ's sparse, known-safe navigation-anchor graph.
 *
 * <p>Hytale remains the physical pathfinder. This system only records positions that miners have
 * actually traversed, applies the agreed long-distance anchor teleport policy, and reacts to
 * Hytale's native navigation failure states.</p>
 */
public final class MinerNavigationSystem extends DelayedEntitySystem<EntityStore> {

    private static final float TICK_INTERVAL_SECONDS = 0.50f;
    private static final String TYPE_TAG = "civ.type";
    private static final String BUILDING_TAG = "civ.building";
    private static final String TUNNEL_CONNECTOR = "mine_tunnel_connector";
    private static final String MINE_BUILDING = "mine";
    private static final double ANCHOR_PASS_DISTANCE_SQUARED = 2.25;
    private static final double TARGET_EPSILON_SQUARED = 0.0001;

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final BuildingPlacementRegistry buildingRegistry;
    private final MineTunnelRegistry tunnelRegistry;
    private final Map<CivUnitRegistry.UnitKey, NavigationRuntime> runtimes = new ConcurrentHashMap<>();

    public MinerNavigationSystem(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        BuildingPlacementRegistry buildingRegistry,
        MineTunnelRegistry tunnelRegistry
    ) {
        super(TICK_INTERVAL_SECONDS);
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
        NPCEntity npc = commandBuffer.getComponent(ref, NPCEntity.getComponentType());
        if (transform == null || npc == null) return;

        World world = store.getExternalData().getWorld();
        UUID worldId = world.getWorldConfig().getUuid();
        BuildingPlacementRegistry.BuildingInstance mine = assignedMine(ref, worldId);
        if (mine == null) {
            runtimes.remove(key);
            return;
        }

        MineNetwork network = tunnelRegistry.networkForMine(worldId, mine.id());
        if (network == null) return;
        PrefabPlacementService.PlacedMarker connector = marker(world, mine, TUNNEL_CONNECTOR);
        if (connector == null || connector.bounds() == null) return;

        NavigationRuntime runtime = runtimes.computeIfAbsent(key, ignored -> new NavigationRuntime());
        if (!mine.id().equals(runtime.mineId)) runtime.reset(mine.id());

        Vector3d position = transform.getPosition();
        BlockPosition feet = blockPosition(position);
        UUID currentTunnelId = tunnelIdAt(network, worldId, feet, connector);
        boolean atConnector = connector.bounds().containsBlock(feet);
        boolean undergroundOrConnector = currentTunnelId != null || atConnector;
        if (undergroundOrConnector) runtime.reachedConnector = true;

        MineNavigationAnchor passedAnchor = passedAnchor(network.navigationAnchors(), position);
        if (passedAnchor != null) runtime.lastAnchorId = passedAnchor.id();
        if (runtime.pendingTeleportAnchorId != null
            && runtime.pendingTeleportAnchorId.equals(runtime.lastAnchorId)) {
            runtime.pendingTeleportAnchorId = null;
        }

        BlockPosition previousBlock = runtime.previousBlock;
        runtime.previousBlock = feet;
        boolean traversedNewBlock = previousBlock != null && !previousBlock.equals(feet);
        if (undergroundOrConnector && traversedNewBlock && currentTunnelId != null) {
            MineNetwork updated = maybeRecordAnchor(world, network, currentTunnelId, feet, runtime);
            if (updated != network) network = updated;
        }

        Vector3d moveTarget = unitRegistry.getMoveTarget(ref);
        if (moveTarget == null) {
            runtime.clearNavigationAttempt();
            return;
        }

        if (runtime.navigationTarget == null
            || runtime.navigationTarget.distanceSquared(moveTarget) > TARGET_EPSILON_SQUARED) {
            runtime.beginNavigationAttempt(moveTarget);
        }

        if (!runtime.reachedConnector || !undergroundOrConnector) {
            observeNativeNavigation(ref, npc, world, network, currentTunnelId, moveTarget, transform, runtime,
                commandBuffer);
            return;
        }

        maybeTeleportForLongDistance(
            ref, world, network, currentTunnelId, connector, position, moveTarget, transform, runtime,
            commandBuffer
        );
        observeNativeNavigation(
            ref, npc, world, network, currentTunnelId, moveTarget, transform, runtime, commandBuffer
        );
    }

    private MineNetwork maybeRecordAnchor(
        World world,
        MineNetwork network,
        UUID tunnelId,
        BlockPosition feet,
        NavigationRuntime runtime
    ) {
        BlockType blockType = loadedBlockType(world, feet);
        if (blockType != BlockType.EMPTY) return network;
        if (!MineNavigationPolicy.canCreateRegularAnchor(feet, network.navigationAnchors())) return network;

        MineNavigationAnchor previous = anchorById(network.navigationAnchors(), runtime.lastAnchorId);
        if (previous == null && !network.navigationAnchors().isEmpty()) return network;

        UUID newId = UUID.randomUUID();
        MineNavigationAnchor created = new MineNavigationAnchor(
            newId,
            tunnelId,
            feet,
            MineNavigationAnchor.Type.REGULAR,
            previous == null ? Set.of() : Set.of(previous.id())
        );

        MineNetwork updated;
        if (previous == null) {
            updated = network.withNavigationAnchor(created);
        } else {
            Set<UUID> connections = new HashSet<>(previous.connectedAnchorIds());
            connections.add(newId);
            MineNavigationAnchor connectedPrevious = new MineNavigationAnchor(
                previous.id(), previous.tunnelId(), previous.position(), previous.type(), connections
            );
            updated = network.withNavigationAnchors(List.of(connectedPrevious, created));
        }

        tunnelRegistry.putNetwork(world, updated);
        runtime.lastAnchorId = newId;
        return updated;
    }

    private void maybeTeleportForLongDistance(
        Ref<EntityStore> ref,
        World world,
        MineNetwork network,
        UUID currentTunnelId,
        PrefabPlacementService.PlacedMarker connector,
        Vector3d currentPosition,
        Vector3d moveTarget,
        TransformComponent transform,
        NavigationRuntime runtime,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        if (runtime.pendingTeleportAnchorId != null || network.navigationAnchors().isEmpty()) return;

        MineNavigationAnchor sourceAnchor = anchorById(network.navigationAnchors(), runtime.lastAnchorId);
        if (sourceAnchor == null) {
            sourceAnchor = MineNavigationPolicy.closestAnchor(
                network.navigationAnchors(),
                currentTunnelId == null ? network.mainTunnelId() : currentTunnelId,
                worldPosition(currentPosition)
            );
        }
        if (sourceAnchor == null) return;

        BlockPosition distanceReference = connector.bounds().containsBlock(blockPosition(currentPosition))
            ? blockPosition(center(connector.bounds(), connector.bounds().minY()))
            : sourceAnchor.position();
        WorldPosition target = worldPosition(moveTarget);
        if (!MineNavigationPolicy.shouldTeleport(distanceReference, target)) return;

        UUID targetTunnelId = tunnelIdForTarget(network, world.getWorldConfig().getUuid(), moveTarget, connector);
        if (targetTunnelId == null) return;
        MineNavigationAnchor destination = MineNavigationPolicy.selectTeleportAnchor(
            network.navigationAnchors(), sourceAnchor.id(), targetTunnelId, target
        );
        if (destination == null || destination.id().equals(sourceAnchor.id())) return;
        if (loadedBlockType(world, destination.position()) != BlockType.EMPTY) return;
        if (distanceSquared(destination.position(), target) >= distanceSquared(distanceReference, target)) return;

        Vector3d teleportTarget = anchorPosition(destination.position());
        commandBuffer.putComponent(
            ref,
            Teleport.getComponentType(),
            new Teleport(teleportTarget, transform.getRotation())
        );
        runtime.pendingTeleportAnchorId = destination.id();
        runtime.lastAnchorId = destination.id();
        runtime.repathRequested = false;
    }

    private void observeNativeNavigation(
        Ref<EntityStore> ref,
        NPCEntity npc,
        World world,
        MineNetwork network,
        UUID currentTunnelId,
        Vector3d moveTarget,
        TransformComponent transform,
        NavigationRuntime runtime,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        if (npc.getRole() == null) return;
        MotionController controller = npc.getRole().getActiveMotionController();
        if (controller == null) return;

        NavState state = controller.getNavState();
        if (state == null || state == NavState.DEFER || state == NavState.INIT) return;
        if (state == NavState.PROGRESSING || state == NavState.AT_GOAL) {
            runtime.repathRequested = false;
            return;
        }
        if (state != NavState.BLOCKED && state != NavState.ABORTED) return;

        if (!runtime.repathRequested) {
            controller.setForceRecomputePath(true);
            runtime.repathRequested = true;
            return;
        }

        UUID targetTunnelId = tunnelIdForTarget(
            network, world.getWorldConfig().getUuid(), moveTarget, null
        );
        UUID semanticTunnelId = targetTunnelId == null ? currentTunnelId : targetTunnelId;
        if (semanticTunnelId != null && semanticTunnelId.equals(network.mainTunnelId())) {
            MineNavigationAnchor lastSafe = anchorById(network.navigationAnchors(), runtime.lastAnchorId);
            if (lastSafe != null && loadedBlockType(world, lastSafe.position()) == BlockType.EMPTY) {
                commandBuffer.putComponent(
                    ref,
                    Teleport.getComponentType(),
                    new Teleport(anchorPosition(lastSafe.position()), transform.getRotation())
                );
                runtime.pendingTeleportAnchorId = lastSafe.id();
                controller.setForceRecomputePath(true);
            }
        }

        // Non-main task release is owned by MinerWorkSystem's front scheduler. This navigation
        // adapter only reports/recovers native movement and never invents a competing task state.
        runtime.repathRequested = false;
    }

    private UUID tunnelIdAt(
        MineNetwork network,
        UUID worldId,
        BlockPosition position,
        PrefabPlacementService.PlacedMarker connector
    ) {
        if (connector != null && connector.bounds() != null && connector.bounds().containsBlock(position)) {
            return network.mainTunnelId();
        }
        for (MineTunnel tunnel : network.tunnels()) {
            MineTunnelGeometry geometry = tunnelRegistry.geometryForTunnel(worldId, network.mineId(), tunnel.id());
            if (geometry != null && geometry.excavationBlocks().contains(position)) return tunnel.id();
        }
        return null;
    }

    private UUID tunnelIdForTarget(
        MineNetwork network,
        UUID worldId,
        Vector3d target,
        PrefabPlacementService.PlacedMarker connector
    ) {
        BlockPosition block = blockPosition(target);
        UUID direct = tunnelIdAt(network, worldId, block, connector);
        if (direct != null) return direct;

        UUID closestTunnel = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (MineTunnel tunnel : network.tunnels()) {
            MineTunnelGeometry geometry = tunnelRegistry.geometryForTunnel(worldId, network.mineId(), tunnel.id());
            if (geometry == null) continue;
            double distance = distanceSquaredToGeometry(geometry, target);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestTunnel = tunnel.id();
            }
        }
        return closestTunnel;
    }

    private static double distanceSquaredToGeometry(MineTunnelGeometry geometry, Vector3d target) {
        double closest = Double.POSITIVE_INFINITY;
        for (MineTunnelGeometry.Slice slice : geometry.slices()) {
            BlockPosition center = slice.floorCenter();
            double dx = target.x - (center.x() + 0.5);
            double dy = target.y - center.y();
            double dz = target.z - (center.z() + 0.5);
            closest = Math.min(closest, dx * dx + dy * dy + dz * dz);
        }
        return closest;
    }

    private static MineNavigationAnchor passedAnchor(List<MineNavigationAnchor> anchors, Vector3d position) {
        MineNavigationAnchor closest = MineNavigationPolicy.closestAnchor(
            anchors, null, worldPosition(position)
        );
        if (closest == null) return null;
        return distanceSquared(closest.position(), worldPosition(position)) <= ANCHOR_PASS_DISTANCE_SQUARED
            ? closest
            : null;
    }

    private static MineNavigationAnchor anchorById(List<MineNavigationAnchor> anchors, UUID id) {
        if (id == null) return null;
        return anchors.stream().filter(anchor -> id.equals(anchor.id())).findFirst().orElse(null);
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

    private static BlockType loadedBlockType(World world, BlockPosition block) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(block.x(), block.z()));
        return chunk == null ? null : chunk.getBlockType(block.x(), block.y(), block.z());
    }

    private static BlockPosition blockPosition(Vector3d position) {
        return new BlockPosition(
            (int) Math.floor(position.x),
            (int) Math.floor(position.y),
            (int) Math.floor(position.z)
        );
    }

    private static WorldPosition worldPosition(Vector3d position) {
        return new WorldPosition(position.x, position.y, position.z);
    }

    private static Vector3d anchorPosition(BlockPosition position) {
        return new Vector3d(position.x() + 0.5, position.y(), position.z() + 0.5);
    }

    private static Vector3d center(BuildingBounds bounds, double y) {
        return new Vector3d(
            (bounds.minX() + bounds.maxX()) * 0.5,
            y,
            (bounds.minZ() + bounds.maxZ()) * 0.5
        );
    }

    private static double distanceSquared(BlockPosition first, WorldPosition second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static final class NavigationRuntime {
        private UUID mineId;
        private boolean reachedConnector;
        private UUID lastAnchorId;
        private UUID pendingTeleportAnchorId;
        private BlockPosition previousBlock;
        private Vector3d navigationTarget;
        private boolean repathRequested;

        private void reset(UUID nextMineId) {
            mineId = nextMineId;
            reachedConnector = false;
            lastAnchorId = null;
            pendingTeleportAnchorId = null;
            previousBlock = null;
            clearNavigationAttempt();
        }

        private void beginNavigationAttempt(Vector3d target) {
            navigationTarget = new Vector3d(target);
            repathRequested = false;
        }

        private void clearNavigationAttempt() {
            navigationTarget = null;
            repathRequested = false;
        }
    }
}
