package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.civilizations.core.ConstructionJob;
import dev.civilizations.core.MovementIntent;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WorldPosition;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes construction-worker intents using native Hytale navigation, animation and prefab
 * placement. The worker stays at one reachable point while the prefab materializes by Y-layer.
 */
public final class ConstructionWorkSystem extends EntityTickingSystem<EntityStore> {

    private static final double ARRIVAL_DISTANCE = 1.25;
    private static final double RETRY_SECONDS = 1.0;
    // Temporary verified generic action id. Replace with a dedicated hammer animation asset later.
    private static final String BUILD_ANIMATION = "Alerted";

    private static final String TYPE_TAG = "civ.type";
    private static final String BUILDING_TAG = "civ.building";
    private static final String WORKPLACE_ACCESS = "workplace_access";
    private static final String FARM = "farm";

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final FarmBuildingRegistry farmRegistry;
    private final FarmFieldRegistry fieldRegistry;
    private final PrefabPlacementService placementService;

    private final Map<CivUnitRegistry.UnitKey, WorkerRuntime> workers =
        new ConcurrentHashMap<>();
    private final Map<UUID, CivUnitRegistry.UnitKey> siteReservations =
        new ConcurrentHashMap<>();

    public ConstructionWorkSystem(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        FarmBuildingRegistry farmRegistry,
        FarmFieldRegistry fieldRegistry,
        PrefabPlacementService placementService
    ) {
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
        this.farmRegistry = farmRegistry;
        this.fieldRegistry = fieldRegistry;
        this.placementService = placementService;
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

        if (!ref.isValid()) {
            releaseWorker(key);
            activityRegistry.forget(ref);
            unitRegistry.forget(ref);
            return;
        }

        if (unitRegistry.getProfession(ref) != Profession.CONSTRUCTION_WORKER) {
            releaseWorker(key);
            return;
        }

        TransformComponent transform =
            commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || !activityRegistry.autonomousWorkAllowed(ref)) {
            return;
        }

        World world = store.getExternalData().getWorld();
        Vector3d position = transform.getPosition();
        WorkerRuntime runtime = workers.computeIfAbsent(key, ignored -> new WorkerRuntime());

        if (runtime.site != null && !placementService.constructionSites().contains(runtime.site)) {
            stopBuildAnimation(ref, store);
            releaseReservation(key, runtime);
            runtime.job.abandonTarget();
            runtime.site = null;
        }

        ConstructionJob.Intent intent = runtime.job.intent();
        if (intent instanceof ConstructionJob.FindConstructionSiteIntent) {
            findSite(ref, key, world, position, runtime, dt);
        } else if (intent instanceof ConstructionJob.MoveToConstructionSiteIntent moveIntent) {
            moveToSite(ref, position, runtime, moveIntent.movement());
        } else if (intent instanceof ConstructionJob.BuildIntent) {
            build(ref, world, store, commandBuffer, runtime, dt);
        } else if (intent instanceof ConstructionJob.CompleteConstructionIntent) {
            complete(ref, key, world, store, runtime);
        }
    }

    private void findSite(
        Ref<EntityStore> ref,
        CivUnitRegistry.UnitKey key,
        World world,
        Vector3d position,
        WorkerRuntime runtime,
        float dt
    ) {
        runtime.retrySeconds -= dt;
        if (runtime.retrySeconds > 0.0) {
            return;
        }

        PrefabPlacementService.ConstructionSite best = null;
        Vector3d bestWorkPoint = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (PrefabPlacementService.ConstructionSite site : placementService.constructionSites()) {
            if (Universe.get().getWorld(site.worldId()) != world) {
                continue;
            }
            CivUnitRegistry.UnitKey reservedBy = siteReservations.get(site.id());
            if (reservedBy != null && !reservedBy.equals(key)) {
                continue;
            }

            Vector3d workPoint = findWorkPoint(world, position, site.candidate().footprint());
            double distance = position.distanceSquared(workPoint);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = site;
                bestWorkPoint = workPoint;
            }
        }

        if (best == null) {
            unitRegistry.clearMoveTarget(ref);
            runtime.retrySeconds = RETRY_SECONDS;
            return;
        }

        siteReservations.put(best.id(), key);
        int layers = placementService.constructionLayerCount(best);
        ConstructionJob.WorkTarget target = new ConstructionJob.WorkTarget(
            best.id().toString(),
            new WorldPosition(bestWorkPoint.x, bestWorkPoint.y, bestWorkPoint.z),
            layers
        );
        if (!runtime.job.assignTarget(target)) {
            siteReservations.remove(best.id(), key);
            return;
        }

        runtime.site = best;
        unitRegistry.setMoveTarget(ref, bestWorkPoint);
    }

    private void moveToSite(
        Ref<EntityStore> ref,
        Vector3d position,
        WorkerRuntime runtime,
        MovementIntent movement
    ) {
        Vector3d target = toVector(movement.destination());
        if (!hasArrived(position, target)) {
            unitRegistry.setMoveTarget(ref, target);
            return;
        }

        unitRegistry.clearMoveTarget(ref);
        if (runtime.job.movementArrived()) {
            runtime.animationStarted = false;
            System.out.println(
                "[Civ Construction] Worker arrived at site " + runtime.site.id()
                    + " distance=" + Math.sqrt(horizontalDistanceSquared(position, target))
            );
        }
    }

    private void build(
        Ref<EntityStore> ref,
        World world,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer,
        WorkerRuntime runtime,
        float dt
    ) {
        unitRegistry.clearMoveTarget(ref);
        if (!runtime.animationStarted) {
            AnimationUtils.playAnimation(ref, AnimationSlot.Action, BUILD_ANIMATION, store);
            runtime.animationStarted = true;
            System.out.println(
                "[Civ Construction] BUILDING started for site " + runtime.site.id()
                    + " using animation set " + BUILD_ANIMATION
            );
        }

        int before = runtime.job.completedSteps();
        int completed = runtime.job.advanceWork(dt);
        for (int offset = 0; offset < completed; offset++) {
            int layerIndex = before + offset;
            boolean placed = placementService.materializeConstructionLayer(
                world,
                runtime.site,
                layerIndex,
                commandBuffer
            );
            System.out.println(
                "[Civ Construction] Site " + runtime.site.id()
                    + " materialized layer " + layerIndex
                    + " success=" + placed
            );
        }
    }

    private void complete(
        Ref<EntityStore> ref,
        CivUnitRegistry.UnitKey key,
        World world,
        Store<EntityStore> store,
        WorkerRuntime runtime
    ) {
        PrefabPlacementService.ConstructionSite site = runtime.site;
        PlayerRef owner = site == null ? null : Universe.get().getPlayer(site.ownerId());
        if (site == null || owner == null) {
            stopBuildAnimation(ref, store);
            releaseReservation(key, runtime);
            runtime.job.abandonTarget();
            runtime.site = null;
            return;
        }

        PrefabPlacementService.PlacedPrefab placed =
            placementService.completeConstruction(owner, world, site);

        if (PrefabPlacementService.WHEAT_FIELD.id().equals(site.definition().id())) {
            fieldRegistry.registerField(site.worldId(), site.candidate().footprint());
        } else if (PrefabPlacementService.FARM.id().equals(site.definition().id())) {
            var entrances = placed.markers().stream()
                .filter(marker -> marker.hasTag(TYPE_TAG, WORKPLACE_ACCESS))
                .filter(marker -> marker.hasTag(BUILDING_TAG, FARM))
                .map(PrefabPlacementService.PlacedMarker::position)
                .toList();
            if (!entrances.isEmpty()) {
                farmRegistry.registerFarm(
                    site.worldId(),
                    entrances,
                    site.candidate().footprint(),
                    site.candidate().replacedFloorBlocks()
                );
            }
        }

        stopBuildAnimation(ref, store);
        releaseReservation(key, runtime);
        runtime.site = null;
        runtime.job.constructionCompleted();
        runtime.retrySeconds = 0.25;
    }

    private void releaseWorker(CivUnitRegistry.UnitKey key) {
        WorkerRuntime runtime = workers.remove(key);
        if (runtime != null) {
            releaseReservation(key, runtime);
        }
    }

    private void releaseReservation(
        CivUnitRegistry.UnitKey key,
        WorkerRuntime runtime
    ) {
        if (runtime.site != null) {
            siteReservations.remove(runtime.site.id(), key);
        }
    }

    private static Vector3d findWorkPoint(
        World world,
        Vector3d worker,
        PrefabPlacementService.PlacementFootprint footprint
    ) {
        Vector3d best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        int y = footprint.floorY() + 1;

        for (int x = footprint.minX() - 1; x <= footprint.maxX() + 1; x++) {
            best = nearerFree(world, worker, best, bestDistance, x, y, footprint.minZ() - 1);
            if (best != null) {
                bestDistance = worker.distanceSquared(best);
            }
            best = nearerFree(world, worker, best, bestDistance, x, y, footprint.maxZ() + 1);
            if (best != null) {
                bestDistance = worker.distanceSquared(best);
            }
        }
        for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
            best = nearerFree(world, worker, best, bestDistance, footprint.minX() - 1, y, z);
            if (best != null) {
                bestDistance = worker.distanceSquared(best);
            }
            best = nearerFree(world, worker, best, bestDistance, footprint.maxX() + 1, y, z);
            if (best != null) {
                bestDistance = worker.distanceSquared(best);
            }
        }

        if (best != null) {
            return best;
        }

        return new Vector3d(
            (footprint.minX() + footprint.maxX() + 1) / 2.0,
            y,
            footprint.minZ() - 0.5
        );
    }

    private static Vector3d nearerFree(
        World world,
        Vector3d worker,
        Vector3d current,
        double currentDistance,
        int x,
        int y,
        int z
    ) {
        if (!isEmpty(world.getBlockType(x, y, z))
            || !isEmpty(world.getBlockType(x, y + 1, z))) {
            return current;
        }
        Vector3d candidate = new Vector3d(x + 0.5, y, z + 0.5);
        return worker.distanceSquared(candidate) < currentDistance ? candidate : current;
    }

    private static boolean isEmpty(BlockType blockType) {
        return blockType == null
            || blockType == BlockType.EMPTY
            || blockType.getMaterial() == BlockMaterial.Empty;
    }

    private static void stopBuildAnimation(
        Ref<EntityStore> ref,
        Store<EntityStore> store
    ) {
        if (ref != null && ref.isValid()) {
            AnimationUtils.stopAnimation(ref, AnimationSlot.Action, store);
        }
    }

    private static Vector3d toVector(WorldPosition position) {
        return new Vector3d(position.x(), position.y(), position.z());
    }

    private static boolean hasArrived(Vector3d position, Vector3d target) {
        return horizontalDistanceSquared(position, target)
            <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    }

    private static double horizontalDistanceSquared(Vector3d position, Vector3d target) {
        double dx = position.x - target.x;
        double dz = position.z - target.z;
        return dx * dx + dz * dz;
    }

    private static final class WorkerRuntime {
        private final ConstructionJob job = new ConstructionJob();
        private PrefabPlacementService.ConstructionSite site;
        private double retrySeconds;
        private boolean animationStarted;
    }
}
