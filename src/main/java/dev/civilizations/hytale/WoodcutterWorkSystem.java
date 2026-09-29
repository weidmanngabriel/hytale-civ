package dev.civilizations.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MovementIntent;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WoodcutterJob;
import dev.civilizations.core.WorldPosition;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes headless woodcutter intents against Hytale's world, navigation and native harvesting.
 */
public final class WoodcutterWorkSystem extends EntityTickingSystem<EntityStore> {

    private static final String WOOD_GATHER_TYPE = "Woods";
    private static final int SEARCH_RADIUS = 16;
    private static final int SEARCH_VERTICAL_RADIUS = 4;
    private static final double ARRIVAL_DISTANCE = 0.6;
    private static final double RETRY_SECONDS = 1.0;
    private static final float FELL_DAMAGE_SCALE = 100_000.0f;

    private static final int[][] CARDINAL_OFFSETS = {
        {1, 0},
        {-1, 0},
        {0, 1},
        {0, -1}
    };

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final Map<CivUnitRegistry.UnitKey, WorkerRuntime> workers =
        new ConcurrentHashMap<>();

    public WoodcutterWorkSystem(
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

        if (!ref.isValid()) {
            workers.remove(key);
            activityRegistry.forget(ref);
            unitRegistry.forget(ref);
            return;
        }

        if (unitRegistry.getProfession(ref) != Profession.WOODCUTTER) {
            workers.remove(key);
            return;
        }

        TransformComponent transform =
            commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        if (!activityRegistry.autonomousWorkAllowed(ref)) {
            return;
        }

        World world = store.getExternalData().getWorld();
        Vector3d position = transform.getPosition();
        WorkerRuntime runtime = workers.computeIfAbsent(key, ignored -> new WorkerRuntime());

        BlockPosition target = runtime.job.targetTree();
        if (target != null && !isTreeBase(world, target.x(), target.y(), target.z())) {
            unitRegistry.clearMoveTarget(ref);
            runtime.job.abandonTarget();
            runtime.retrySeconds = 0.0;
        }

        WoodcutterJob.Intent intent = runtime.job.intent();
        if (intent instanceof WoodcutterJob.FindTreeIntent) {
            searchForTree(ref, world, position, runtime, dt);
        } else if (intent instanceof WoodcutterJob.MoveToTreeIntent moveIntent) {
            executeMovement(ref, position, runtime, moveIntent.movement());
        } else if (intent instanceof WoodcutterJob.ChopTreeIntent) {
            unitRegistry.clearMoveTarget(ref);
            runtime.job.advanceWork(dt);
        } else if (intent instanceof WoodcutterJob.FellTreeIntent fellIntent) {
            unitRegistry.clearMoveTarget(ref);
            fellTree(world, store, fellIntent.tree());
            runtime.job.fellingCompleted();
            runtime.retrySeconds = 0.25;
        }
    }

    private void searchForTree(
        Ref<EntityStore> ref,
        World world,
        Vector3d position,
        WorkerRuntime runtime,
        float dt
    ) {
        runtime.retrySeconds -= dt;
        if (runtime.retrySeconds > 0.0) {
            return;
        }

        WoodcutterJob.WorkTarget target = findNearestTarget(world, position);
        if (target == null) {
            unitRegistry.clearMoveTarget(ref);
            runtime.retrySeconds = RETRY_SECONDS;
            return;
        }

        if (!runtime.job.assignTarget(target)) {
            return;
        }

        WoodcutterJob.Intent nextIntent = runtime.job.intent();
        if (nextIntent instanceof WoodcutterJob.MoveToTreeIntent moveIntent) {
            unitRegistry.setMoveTarget(ref, toVector(moveIntent.movement().destination()));
        }
    }

    private void executeMovement(
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
        runtime.job.movementArrived();
    }

    private static WoodcutterJob.WorkTarget findNearestTarget(
        World world,
        Vector3d position
    ) {
        int centerX = (int) Math.floor(position.x);
        int centerY = (int) Math.floor(position.y);
        int centerZ = (int) Math.floor(position.z);

        BlockPosition nearest = null;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;

        for (int x = centerX - SEARCH_RADIUS; x <= centerX + SEARCH_RADIUS; x++) {
            for (int z = centerZ - SEARCH_RADIUS; z <= centerZ + SEARCH_RADIUS; z++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                if (dx * dx + dz * dz > SEARCH_RADIUS * SEARCH_RADIUS) {
                    continue;
                }

                for (
                    int y = centerY - SEARCH_VERTICAL_RADIUS;
                    y <= centerY + SEARCH_VERTICAL_RADIUS;
                    y++
                ) {
                    if (!isTreeBase(world, x, y, z)) {
                        continue;
                    }

                    double distanceSquared =
                        squared(position.x - (x + 0.5))
                            + squared(position.z - (z + 0.5));
                    if (distanceSquared < bestDistanceSquared) {
                        bestDistanceSquared = distanceSquared;
                        nearest = new BlockPosition(x, y, z);
                    }
                }
            }
        }

        if (nearest == null) {
            return null;
        }

        Vector3d interaction = findWorkTarget(world, position, nearest);
        return new WoodcutterJob.WorkTarget(
            nearest,
            new WorldPosition(interaction.x, interaction.y, interaction.z)
        );
    }

    private static boolean isTreeBase(World world, int x, int y, int z) {
        BlockType current = getLoadedBlockType(world, x, y, z);
        if (!isTreeTrunk(current)) {
            return false;
        }

        return !isTreeTrunk(getLoadedBlockType(world, x, y - 1, z));
    }

    private static BlockType getLoadedBlockType(World world, int x, int y, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            return null;
        }
        return chunk.getBlockType(x, y, z);
    }

    private static boolean isTreeTrunk(BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }

        BlockGathering gathering = blockType.getGathering();
        BlockBreakingDropType breaking = gathering == null ? null : gathering.getBreaking();
        if (breaking == null || !WOOD_GATHER_TYPE.equals(breaking.getGatherType())) {
            return false;
        }

        String id = blockType.getId();
        return id != null && id.toLowerCase().contains("trunk");
    }

    private static Vector3d findWorkTarget(
        World world,
        Vector3d workerPosition,
        BlockPosition tree
    ) {
        Vector3d best = null;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;

        for (int[] offset : CARDINAL_OFFSETS) {
            int x = tree.x() + offset[0];
            int z = tree.z() + offset[1];
            if (!isEmpty(getLoadedBlockType(world, x, tree.y(), z))
                || !isEmpty(getLoadedBlockType(world, x, tree.y() + 1, z))) {
                continue;
            }

            Vector3d candidate = new Vector3d(x + 0.5, tree.y(), z + 0.5);
            double distanceSquared =
                squared(workerPosition.x - candidate.x)
                    + squared(workerPosition.z - candidate.z);
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                best = candidate;
            }
        }

        if (best != null) {
            return best;
        }

        int dx = Math.abs(workerPosition.x - tree.x()) >=
            Math.abs(workerPosition.z - tree.z())
            ? (workerPosition.x < tree.x() ? -1 : 1)
            : 0;
        int dz = dx == 0 ? (workerPosition.z < tree.z() ? -1 : 1) : 0;
        return new Vector3d(tree.x() + dx + 0.5, tree.y(), tree.z() + dz + 0.5);
    }

    private static boolean isEmpty(BlockType blockType) {
        return blockType == null
            || blockType == BlockType.EMPTY
            || blockType.getMaterial() == BlockMaterial.Empty;
    }

    private static boolean fellTree(
        World world,
        Store<EntityStore> entityStore,
        BlockPosition target
    ) {
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(target.x(), target.z());
        Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            return false;
        }

        return BlockHarvestUtils.performBlockDamage(
            new Vector3i(target.x(), target.y(), target.z()),
            null,
            null,
            FELL_DAMAGE_SCALE,
            0,
            false,
            chunkRef,
            entityStore,
            chunkStore
        );
    }

    private static Vector3d toVector(WorldPosition position) {
        return new Vector3d(position.x(), position.y(), position.z());
    }

    private static boolean hasArrived(Vector3d position, Vector3d target) {
        double dx = position.x - target.x;
        double dz = position.z - target.z;
        return dx * dx + dz * dz <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    }

    private static double squared(double value) {
        return value * value;
    }

    private static final class WorkerRuntime {
        private final WoodcutterJob job = new WoodcutterJob();
        private double retrySeconds;
    }
}
