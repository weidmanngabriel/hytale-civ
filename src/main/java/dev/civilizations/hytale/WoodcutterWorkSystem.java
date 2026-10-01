package dev.civilizations.hytale;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
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
import dev.civilizations.core.WorkDecisionSchedule;
import dev.civilizations.core.WorldPosition;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes headless woodcutter intents against Hytale's world, navigation and native harvesting.
 */
public final class WoodcutterWorkSystem extends EntityTickingSystem<EntityStore> {

    private static final String WOOD_GATHER_TYPE = "Woods";
    private static final String CIV_TYPE_TAG = "civ.type";
    private static final String BUILDING_BOUNDS_TAG = "building_bounds";
    private static final int SEARCH_RADIUS = 16;
    private static final int SEARCH_VERTICAL_RADIUS = 4;
    private static final int TREE_HORIZONTAL_RADIUS = 8;
    private static final int TREE_MAX_ABOVE_BASE = 32;
    private static final int TREE_MAX_BELOW_BASE = 6;
    private static final int MAX_TREE_BLOCKS = 512;
    private static final int WORK_POSITION_RADIUS = 3;
    private static final int WORK_SURFACE_VERTICAL_RADIUS = 6;
    private static final double ARRIVAL_DISTANCE = 1.0;
    private static final double RETRY_SECONDS = 1.0;
    private static final double DIAGNOSTIC_INTERVAL_SECONDS = 5.0;
    // Temporary generic action animation. Kept behind one constant so a dedicated axe animation
    // can replace it without changing woodcutter gameplay logic.
    private static final String WOODCUTTING_ANIMATION = "Alerted";

    private static final int[][] CONNECTED_OFFSETS = {
        {1, 0, 0},
        {-1, 0, 0},
        {0, 1, 0},
        {0, -1, 0},
        {0, 0, 1},
        {0, 0, -1}
    };

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final Map<CivUnitRegistry.UnitKey, WorkerRuntime> workers =
        new ConcurrentHashMap<>();
    private final Map<ReservedBlock, CivUnitRegistry.UnitKey> treeReservations =
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
            releaseWorker(key);
            activityRegistry.forget(ref);
            unitRegistry.forget(ref);
            return;
        }

        if (unitRegistry.getProfession(ref) != Profession.WOODCUTTER) {
            stopChopAnimation(ref, store);
            releaseWorker(key);
            return;
        }

        TransformComponent transform =
            commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        WorkerRuntime runtime = workers.computeIfAbsent(key, ignored -> new WorkerRuntime());
        runtime.diagnosticElapsed += dt;
        if (!activityRegistry.autonomousWorkAllowed(ref)) {
            stopChopAnimation(ref, store);
            runtime.animationStarted = false;
            if (runtime.diagnosticElapsed >= DIAGNOSTIC_INTERVAL_SECONDS) {
                System.out.println(
                    "[CivWoodcutterDiag] worker=" + key.entityIndex()
                        + " state=autonomous-work-blocked"
                );
                runtime.diagnosticElapsed = 0.0;
            }
            return;
        }

        World world = store.getExternalData().getWorld();
        Vector3d position = transform.getPosition();

        if (runtime.tree != null && (!isTreeBase(world, runtime.tree.base())
            || treeTouchesProtectedVolume(world, runtime.tree))) {
            stopChopAnimation(ref, store);
            unitRegistry.clearMoveTarget(ref);
            releaseReservation(key, runtime);
            runtime.job.abandonTarget();
            runtime.tree = null;
            runtime.animationStarted = false;
            runtime.decisions.requestImmediate();
        }

        WoodcutterJob.Intent intent = runtime.job.intent();
        if (intent instanceof WoodcutterJob.FindTreeIntent) {
            searchForTree(ref, key, world, position, runtime, dt);
        } else if (intent instanceof WoodcutterJob.MoveToTreeIntent moveIntent) {
            executeMovement(ref, key, position, runtime, moveIntent.movement());
        } else if (intent instanceof WoodcutterJob.ChopTreeIntent chopIntent) {
            unitRegistry.clearMoveTarget(ref);
            if (!runtime.animationStarted) {
                System.out.println(
                    "[CivWoodcutterDiag] worker=" + key.entityIndex()
                        + " state=chopping remaining=" + chopIntent.remainingSeconds()
                );
                AnimationUtils.playAnimation(
                    ref,
                    AnimationSlot.Action,
                    WOODCUTTING_ANIMATION,
                    store
                );
                runtime.animationStarted = true;
            }
            if (runtime.job.advanceWork(dt)) {
                System.out.println(
                    "[CivWoodcutterDiag] worker=" + key.entityIndex()
                        + " state=ready-to-fell"
                );
                stopChopAnimation(ref, store);
                runtime.animationStarted = false;
            }
        } else if (intent instanceof WoodcutterJob.FellTreeIntent) {
            unitRegistry.clearMoveTarget(ref);
            stopChopAnimation(ref, store);
            runtime.animationStarted = false;

            if (runtime.tree == null || treeTouchesProtectedVolume(world, runtime.tree)) {
                System.out.println(
                    "[CivWoodcutterDiag] worker=" + key.entityIndex()
                        + " state=fell-aborted-invalid-target"
                );
                releaseReservation(key, runtime);
                runtime.job.abandonTarget();
                runtime.tree = null;
                runtime.decisions.requestImmediate();
                return;
            }

            boolean felled = fellTree(world, ref, store, runtime.tree);
            releaseReservation(key, runtime);
            runtime.tree = null;
            if (felled) {
                System.out.println(
                    "[CivWoodcutterDiag] worker=" + key.entityIndex()
                        + " state=fell-success"
                );
                runtime.job.fellingCompleted();
                runtime.decisions.scheduleRetry(0.25);
            } else {
                System.out.println(
                    "[CivWoodcutterDiag] worker=" + key.entityIndex()
                        + " state=fell-failed"
                );
                runtime.job.abandonTarget();
                runtime.decisions.requestImmediate();
            }
        }
    }

    private void searchForTree(
        Ref<EntityStore> ref,
        CivUnitRegistry.UnitKey key,
        World world,
        Vector3d position,
        WorkerRuntime runtime,
        float dt
    ) {
        WorkDecisionSchedule.DecisionKind decision = runtime.decisions.advance(dt);
        if (decision == WorkDecisionSchedule.DecisionKind.NONE) {
            return;
        }

        TreeCandidate candidate = findNearestTarget(world, position, key);
        if (candidate == null) {
            unitRegistry.clearMoveTarget(ref);
            if (runtime.diagnosticElapsed >= DIAGNOSTIC_INTERVAL_SECONDS) {
                logSearchDiagnostics(world, position, key);
                runtime.diagnosticElapsed = 0.0;
            }
            runtime.decisions.scheduleRetry(RETRY_SECONDS);
            return;
        }

        if (!reserveTree(key, candidate.tree())) {
            System.out.println(
                "[CivWoodcutterDiag] worker=" + key.entityIndex()
                    + " state=reservation-race base=" + candidate.tree().base()
            );
            runtime.decisions.requestImmediate();
            return;
        }

        WoodcutterJob.WorkTarget target = new WoodcutterJob.WorkTarget(
            candidate.tree().base(),
            new WorldPosition(
                candidate.interactionPoint().x,
                candidate.interactionPoint().y,
                candidate.interactionPoint().z
            ),
            candidate.tree().blocks().size()
        );
        if (!runtime.job.assignTarget(target)) {
            System.out.println(
                "[CivWoodcutterDiag] worker=" + key.entityIndex()
                    + " state=target-assignment-rejected base=" + candidate.tree().base()
            );
            releaseTree(key, candidate.tree());
            return;
        }

        runtime.tree = candidate.tree();
        System.out.println(
            "[CivWoodcutterDiag] worker=" + key.entityIndex()
                + " state=target-assigned base=" + candidate.tree().base()
                + " blocks=" + candidate.tree().blocks().size()
                + " interaction=" + candidate.interactionPoint()
        );
        WoodcutterJob.Intent nextIntent = runtime.job.intent();
        if (nextIntent instanceof WoodcutterJob.MoveToTreeIntent moveIntent) {
            unitRegistry.setMoveTarget(ref, toVector(moveIntent.movement().destination()));
        }
    }

    private void executeMovement(
        Ref<EntityStore> ref,
        CivUnitRegistry.UnitKey key,
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
            System.out.println(
                "[CivWoodcutterDiag] worker=" + key.entityIndex()
                    + " state=arrived distance="
                    + Math.sqrt(squared(position.x - target.x) + squared(position.z - target.z))
            );
        }
    }

    private TreeCandidate findNearestTarget(
        World world,
        Vector3d position,
        CivUnitRegistry.UnitKey worker
    ) {
        int centerX = (int) Math.floor(position.x);
        int centerY = (int) Math.floor(position.y);
        int centerZ = (int) Math.floor(position.z);

        TreeCandidate nearest = null;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        Set<BlockPosition> evaluatedWood = new HashSet<>();

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
                    BlockPosition base = new BlockPosition(x, y, z);
                    if (evaluatedWood.contains(base) || !isTreeBase(world, base)) {
                        continue;
                    }

                    TreeStructure tree = collectTree(world, base);
                    evaluatedWood.addAll(tree.blocks());
                    if (tree.blocks().isEmpty()
                        || treeTouchesProtectedVolume(world, tree)
                        || isReservedByOther(tree, worker)) {
                        continue;
                    }

                    Vector3d interaction = findWorkTarget(world, position, tree);
                    if (interaction == null) {
                        continue;
                    }

                    double distanceSquared =
                        squared(position.x - interaction.x)
                            + squared(position.y - interaction.y)
                            + squared(position.z - interaction.z);
                    if (distanceSquared < bestDistanceSquared) {
                        bestDistanceSquared = distanceSquared;
                        nearest = new TreeCandidate(tree, interaction);
                    }
                }
            }
        }

        return nearest;
    }

    private void logSearchDiagnostics(
        World world,
        Vector3d position,
        CivUnitRegistry.UnitKey worker
    ) {
        int centerX = (int) Math.floor(position.x);
        int centerY = (int) Math.floor(position.y);
        int centerZ = (int) Math.floor(position.z);
        int woodBlocks = 0;
        int treeBases = 0;
        int protectedTrees = 0;
        int reservedTrees = 0;
        int noStandPositionTrees = 0;
        int usableTrees = 0;

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
                    BlockType type = getLoadedBlockType(world, x, y, z);
                    if (isWoodStructureBlock(type)) {
                        woodBlocks++;
                    }

                    BlockPosition base = new BlockPosition(x, y, z);
                    if (!isTreeBase(world, base)) {
                        continue;
                    }
                    treeBases++;
                    TreeStructure tree = collectTree(world, base);
                    if (tree.blocks().isEmpty()) {
                        continue;
                    }
                    if (treeTouchesProtectedVolume(world, tree)) {
                        protectedTrees++;
                    } else if (isReservedByOther(tree, worker)) {
                        reservedTrees++;
                    } else if (findWorkTarget(world, position, tree) == null) {
                        noStandPositionTrees++;
                    } else {
                        usableTrees++;
                    }
                }
            }
        }

        System.out.println(
            "[CivWoodcutterDiag] worker=" + worker.entityIndex()
                + " state=no-target"
                + " pos=" + centerX + "," + centerY + "," + centerZ
                + " woods=" + woodBlocks
                + " treeBases=" + treeBases
                + " protected=" + protectedTrees
                + " reserved=" + reservedTrees
                + " noStand=" + noStandPositionTrees
                + " usable=" + usableTrees
        );
    }

    private static boolean isTreeBase(World world, BlockPosition position) {
        return isTreeBase(world, position.x(), position.y(), position.z());
    }

    private static boolean isTreeBase(World world, int x, int y, int z) {
        BlockType current = getLoadedBlockType(world, x, y, z);
        if (!isTreeTrunk(current)) {
            return false;
        }

        return !isTreeTrunk(getLoadedBlockType(world, x, y - 1, z));
    }

    private static TreeStructure collectTree(World world, BlockPosition base) {
        ArrayDeque<BlockPosition> queue = new ArrayDeque<>();
        Set<BlockPosition> visited = new HashSet<>();
        List<BlockPosition> blocks = new ArrayList<>();
        Map<BlockPosition, Integer> rootFillBlocks = new HashMap<>();
        queue.add(base);

        while (!queue.isEmpty() && blocks.size() < MAX_TREE_BLOCKS) {
            BlockPosition current = queue.removeFirst();
            if (!visited.add(current) || !withinTreeBounds(base, current)) {
                continue;
            }
            if (!isWoodStructureBlock(getLoadedBlockType(
                world,
                current.x(),
                current.y(),
                current.z()
            ))) {
                continue;
            }

            blocks.add(current);
            if (current.y() < base.y()) {
                Integer fillBlock = findNaturalFillBlock(world, current);
                if (fillBlock != null) {
                    rootFillBlocks.put(current, fillBlock);
                }
            }

            for (int[] offset : CONNECTED_OFFSETS) {
                queue.addLast(new BlockPosition(
                    current.x() + offset[0],
                    current.y() + offset[1],
                    current.z() + offset[2]
                ));
            }
        }

        blocks.sort(Comparator
            .comparingInt(BlockPosition::y)
            .thenComparingInt(BlockPosition::x)
            .thenComparingInt(BlockPosition::z));
        return new TreeStructure(
            base,
            List.copyOf(blocks),
            Map.copyOf(rootFillBlocks),
            world.getWorldConfig().getUuid()
        );
    }

    private static boolean withinTreeBounds(BlockPosition base, BlockPosition position) {
        return Math.abs(position.x() - base.x()) <= TREE_HORIZONTAL_RADIUS
            && Math.abs(position.z() - base.z()) <= TREE_HORIZONTAL_RADIUS
            && position.y() >= base.y() - TREE_MAX_BELOW_BASE
            && position.y() <= base.y() + TREE_MAX_ABOVE_BASE;
    }

    private static BlockType getLoadedBlockType(World world, int x, int y, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            return null;
        }
        return chunk.getBlockType(x, y, z);
    }

    private static Integer getLoadedBlockId(World world, int x, int y, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            return null;
        }
        return chunk.getBlock(x, y, z);
    }

    private static boolean isTreeTrunk(BlockType blockType) {
        if (!isWoodStructureBlock(blockType)) {
            return false;
        }

        String id = blockType.getId();
        return id != null && id.toLowerCase().contains("trunk");
    }

    private static boolean isWoodStructureBlock(BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }

        BlockGathering gathering = blockType.getGathering();
        BlockBreakingDropType breaking = gathering == null ? null : gathering.getBreaking();
        return breaking != null && WOOD_GATHER_TYPE.equals(breaking.getGatherType());
    }

    private static Vector3d findWorkTarget(
        World world,
        Vector3d workerPosition,
        TreeStructure tree
    ) {
        Vector3d best = null;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        BlockPosition base = tree.base();
        int preferredFeetY = (int) Math.floor(workerPosition.y);

        for (int radius = 1; radius <= WORK_POSITION_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    int x = base.x() + dx;
                    int z = base.z() + dz;
                    for (int verticalOffset = 0;
                         verticalOffset <= WORK_SURFACE_VERTICAL_RADIUS;
                         verticalOffset++) {
                        int upperY = preferredFeetY + verticalOffset;
                        Vector3d upperCandidate = validWorkSurface(
                            world,
                            workerPosition,
                            x,
                            upperY,
                            z
                        );
                        if (upperCandidate != null) {
                            double distanceSquared = workerPosition.distanceSquared(upperCandidate);
                            if (distanceSquared < bestDistanceSquared) {
                                bestDistanceSquared = distanceSquared;
                                best = upperCandidate;
                            }
                        }

                        if (verticalOffset == 0) {
                            continue;
                        }
                        int lowerY = preferredFeetY - verticalOffset;
                        Vector3d lowerCandidate = validWorkSurface(
                            world,
                            workerPosition,
                            x,
                            lowerY,
                            z
                        );
                        if (lowerCandidate != null) {
                            double distanceSquared = workerPosition.distanceSquared(lowerCandidate);
                            if (distanceSquared < bestDistanceSquared) {
                                bestDistanceSquared = distanceSquared;
                                best = lowerCandidate;
                            }
                        }
                    }
                }
            }
        }

        return best;
    }

    private static Vector3d validWorkSurface(
        World world,
        Vector3d workerPosition,
        int x,
        int feetY,
        int z
    ) {
        BlockPosition feet = new BlockPosition(x, feetY, z);
        BlockPosition support = new BlockPosition(x, feetY - 1, z);
        BlockType supportType = getLoadedBlockType(world, x, feetY - 1, z);

        if (!isEmpty(getLoadedBlockType(world, x, feetY, z))
            || !isEmpty(getLoadedBlockType(world, x, feetY + 1, z))
            || isEmpty(supportType)
            || isWoodStructureBlock(supportType)
            || isInsideTriggerVolume(world, feet)
            || isInsideTriggerVolume(world, support)) {
            return null;
        }

        return new Vector3d(x + 0.5, feetY, z + 0.5);
    }

    private boolean reserveTree(CivUnitRegistry.UnitKey worker, TreeStructure tree) {
        for (BlockPosition block : tree.blocks()) {
            CivUnitRegistry.UnitKey reservedBy = treeReservations.get(
                new ReservedBlock(tree.worldId(), block)
            );
            if (reservedBy != null && !reservedBy.equals(worker)) {
                return false;
            }
        }
        for (BlockPosition block : tree.blocks()) {
            treeReservations.put(new ReservedBlock(tree.worldId(), block), worker);
        }
        return true;
    }

    private boolean isReservedByOther(
        TreeStructure tree,
        CivUnitRegistry.UnitKey worker
    ) {
        for (BlockPosition block : tree.blocks()) {
            CivUnitRegistry.UnitKey reservedBy = treeReservations.get(
                new ReservedBlock(tree.worldId(), block)
            );
            if (reservedBy != null && !reservedBy.equals(worker)) {
                return true;
            }
        }
        return false;
    }

    private void releaseWorker(CivUnitRegistry.UnitKey worker) {
        WorkerRuntime runtime = workers.remove(worker);
        if (runtime != null) {
            releaseReservation(worker, runtime);
        }
    }

    private void releaseReservation(CivUnitRegistry.UnitKey worker, WorkerRuntime runtime) {
        if (runtime.tree != null) {
            releaseTree(worker, runtime.tree);
        }
    }

    private void releaseTree(CivUnitRegistry.UnitKey worker, TreeStructure tree) {
        for (BlockPosition block : tree.blocks()) {
            treeReservations.remove(new ReservedBlock(tree.worldId(), block), worker);
        }
    }

    private static boolean treeTouchesProtectedVolume(World world, TreeStructure tree) {
        for (BlockPosition block : tree.blocks()) {
            if (isInsideTriggerVolume(world, block)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInsideTriggerVolume(World world, BlockPosition block) {
        TriggerVolumeManager volumes = world.getEntityStore().getStore().getResource(
            TriggerVolumesPlugin.get().getManagerResourceType()
        );
        if (volumes == null) {
            return false;
        }

        Vector3d blockCenter = new Vector3d(
            block.x() + 0.5,
            block.y() + 0.5,
            block.z() + 0.5
        );
        for (VolumeEntry volume : volumes.getVolumes()) {
            Map<String, String> tags = volume.getRawTags();
            if (tags == null || !BUILDING_BOUNDS_TAG.equals(tags.get(CIV_TYPE_TAG))) {
                continue;
            }
            if (volume.getShape() != null
                && volume.getPosition() != null
                && volume.getShape().contains(volume.getPosition(), blockCenter)) {
                return true;
            }
        }
        return false;
    }

    private static Integer findNaturalFillBlock(World world, BlockPosition root) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int[] offset : CONNECTED_OFFSETS) {
            int x = root.x() + offset[0];
            int y = root.y() + offset[1];
            int z = root.z() + offset[2];
            BlockType type = getLoadedBlockType(world, x, y, z);
            Integer id = getLoadedBlockId(world, x, y, z);
            if (id == null || isEmpty(type) || isWoodStructureBlock(type)) {
                continue;
            }
            counts.merge(id, 1, Integer::sum);
        }
        return counts.entrySet().stream()
            .max(Map.Entry.<Integer, Integer>comparingByValue()
                .thenComparing(Map.Entry.comparingByKey()))
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private static boolean fellTree(
        World world,
        Ref<EntityStore> workerRef,
        Store<EntityStore> entityStore,
        TreeStructure tree
    ) {
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();

        List<BlockPosition> ordered = new ArrayList<>(tree.blocks());
        ordered.sort(Comparator
            .comparing((BlockPosition block) -> !block.equals(tree.base()))
            .thenComparingInt(BlockPosition::y));

        List<Vector3i> breakTargets = new ArrayList<>();
        for (BlockPosition block : ordered) {
            if (isInsideTriggerVolume(world, block)) {
                return false;
            }
            if (!isWoodStructureBlock(getLoadedBlockType(world, block.x(), block.y(), block.z()))) {
                continue;
            }
            breakTargets.add(new Vector3i(block.x(), block.y(), block.z()));
        }

        if (breakTargets.isEmpty()) {
            return false;
        }

        BlockHarvestUtils.performBlockBreak(
            workerRef,
            null,
            breakTargets,
            0,
            entityStore,
            chunkStore
        );

        boolean changed = false;
        for (Vector3i block : breakTargets) {
            if (!isWoodStructureBlock(getLoadedBlockType(world, block.x, block.y, block.z))) {
                changed = true;
                break;
            }
        }

        for (Map.Entry<BlockPosition, Integer> fill : tree.rootFillBlocks().entrySet()) {
            BlockPosition position = fill.getKey();
            if (isInsideTriggerVolume(world, position)
                || !isEmpty(getLoadedBlockType(
                    world,
                    position.x(),
                    position.y(),
                    position.z()
                ))) {
                continue;
            }
            WorldChunk chunk = world.getChunkIfLoaded(
                ChunkUtil.indexChunkFromBlock(position.x(), position.z())
            );
            if (chunk != null) {
                chunk.setBlock(position.x(), position.y(), position.z(), fill.getValue());
            }
        }

        return changed;
    }

    private static boolean isEmpty(BlockType blockType) {
        return blockType == null
            || blockType == BlockType.EMPTY
            || blockType.getMaterial() == BlockMaterial.Empty;
    }

    private static void stopChopAnimation(
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
        double dx = position.x - target.x;
        double dz = position.z - target.z;
        return dx * dx + dz * dz <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    }

    private static double squared(double value) {
        return value * value;
    }

    private record TreeCandidate(TreeStructure tree, Vector3d interactionPoint) {
    }

    private record TreeStructure(
        BlockPosition base,
        List<BlockPosition> blocks,
        Map<BlockPosition, Integer> rootFillBlocks,
        UUID worldId
    ) {
    }

    private record ReservedBlock(UUID worldId, BlockPosition block) {
    }

    private static final class WorkerRuntime {
        private final WoodcutterJob job = new WoodcutterJob();
        private final WorkDecisionSchedule decisions = new WorkDecisionSchedule();
        private TreeStructure tree;
        private boolean animationStarted;
        private double diagnosticElapsed;
    }
}
