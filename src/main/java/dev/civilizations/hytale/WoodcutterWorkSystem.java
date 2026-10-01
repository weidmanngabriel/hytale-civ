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
    private static final int WORK_TRUNK_HEIGHT = 3;
    private static final double ARRIVAL_DISTANCE = 1.0;
    private static final double RETRY_SECONDS = 1.0;
    private static final double DIAGNOSTIC_INTERVAL_SECONDS = 5.0;
    private static final double WORK_TARGET_SCORE_EPSILON = 0.0001;
    // The ItemPlayerAnimations child loops Hytale's native axe swing client-side, so Civ only
    // sends one start and one stop for each chopping phase instead of retriggering every swing.
    private static final String WOODCUTTING_ITEM_ANIMATIONS = "Civ_Woodcutter_Axe";
    private static final String WOODCUTTING_ANIMATION = "SwingLeft";

    /**
     * A Hytale tree can contain diagonally touching branches and roots. Treat all 26 adjacent
     * positions in the surrounding 3x3x3 cube as connected, while the existing tree bounds and
     * MAX_TREE_BLOCKS limit still cap how far one target may spread.
     */
    private static final int[][] TREE_CONNECTED_OFFSETS = createTreeConnectedOffsets();

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final WoodcutterScanDiagnostics scanDiagnostics;
    private final Map<CivUnitRegistry.UnitKey, WorkerRuntime> workers =
        new ConcurrentHashMap<>();
    private final Map<ReservedBlock, CivUnitRegistry.UnitKey> treeReservations =
        new ConcurrentHashMap<>();

    public WoodcutterWorkSystem(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry,
        WoodcutterScanDiagnostics scanDiagnostics
    ) {
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
        this.scanDiagnostics = scanDiagnostics;
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
                    WOODCUTTING_ITEM_ANIMATIONS,
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

        ScanCounters counters = scanDiagnostics.enabled() ? new ScanCounters() : null;
        long scanStartedNanos = counters == null ? 0L : System.nanoTime();
        TreeCandidate candidate = findNearestTarget(world, position, key, counters);
        if (counters != null) {
            scanDiagnostics.record(
                System.nanoTime() - scanStartedNanos,
                counters.positionsChecked,
                counters.treeBases,
                counters.treesCollected,
                counters.treeBlocksCollected,
                counters.protectedTrees,
                counters.reservedTrees,
                counters.workTargetChecks,
                counters.noStandPositionTrees,
                counters.usableTrees
            );
        }

        if (candidate == null) {
            unitRegistry.clearMoveTarget(ref);
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
                + " groundFill=" + candidate.tree().rootFillBlocks().size()
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
        CivUnitRegistry.UnitKey worker,
        ScanCounters counters
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
                    if (counters != null) {
                        counters.positionsChecked++;
                    }
                    BlockPosition base = new BlockPosition(x, y, z);
                    if (evaluatedWood.contains(base) || !isTreeBase(world, base)) {
                        continue;
                    }
                    if (counters != null) {
                        counters.treeBases++;
                        counters.treesCollected++;
                    }

                    TreeStructure tree = collectTree(world, base, centerY);
                    if (counters != null) {
                        counters.treeBlocksCollected += tree.blocks().size();
                    }
                    evaluatedWood.addAll(tree.blocks());
                    if (tree.blocks().isEmpty()) {
                        continue;
                    }
                    if (treeTouchesProtectedVolume(world, tree)) {
                        if (counters != null) {
                            counters.protectedTrees++;
                        }
                        continue;
                    }
                    if (isReservedByOther(tree, worker)) {
                        if (counters != null) {
                            counters.reservedTrees++;
                        }
                        continue;
                    }

                    if (counters != null) {
                        counters.workTargetChecks++;
                    }
                    Vector3d interaction = findWorkTarget(world, position, tree);
                    if (interaction == null) {
                        if (counters != null) {
                            counters.noStandPositionTrees++;
                        }
                        continue;
                    }
                    if (counters != null) {
                        counters.usableTrees++;
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

    private static TreeStructure collectTree(
        World world,
        BlockPosition base,
        int fillBelowY
    ) {
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
            // Only wood positions Civ is about to remove can be restored. Using the worker's
            // standing Y as the cutoff catches roots and buried trunk pieces without filling
            // unrelated pre-existing air pockets around the tree.
            if (current.y() < fillBelowY) {
                Integer fillBlock = findNaturalFillBlock(world, current);
                if (fillBlock != null) {
                    rootFillBlocks.put(current, fillBlock);
                }
            }

            for (int[] offset : TREE_CONNECTED_OFFSETS) {
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
        double bestWoodDistanceSquared = Double.POSITIVE_INFINITY;
        double bestWorkerDistanceSquared = Double.POSITIVE_INFINITY;
        BlockPosition base = tree.base();
        int preferredFeetY = (int) Math.floor(workerPosition.y);
        List<BlockPosition> lowerTrunkBlocks = lowerTrunkBlocks(tree);

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
                            WorkTargetScore score = workTargetScore(
                                upperCandidate,
                                workerPosition,
                                lowerTrunkBlocks
                            );
                            if (score.betterThan(
                                bestWoodDistanceSquared,
                                bestWorkerDistanceSquared
                            )) {
                                bestWoodDistanceSquared = score.woodDistanceSquared();
                                bestWorkerDistanceSquared = score.workerDistanceSquared();
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
                            WorkTargetScore score = workTargetScore(
                                lowerCandidate,
                                workerPosition,
                                lowerTrunkBlocks
                            );
                            if (score.betterThan(
                                bestWoodDistanceSquared,
                                bestWorkerDistanceSquared
                            )) {
                                bestWoodDistanceSquared = score.woodDistanceSquared();
                                bestWorkerDistanceSquared = score.workerDistanceSquared();
                                best = lowerCandidate;
                            }
                        }
                    }
                }
            }
        }

        return best;
    }

    private static List<BlockPosition> lowerTrunkBlocks(TreeStructure tree) {
        BlockPosition base = tree.base();
        List<BlockPosition> lowerTrunk = new ArrayList<>();
        for (BlockPosition block : tree.blocks()) {
            if (block.y() < base.y() || block.y() > base.y() + WORK_TRUNK_HEIGHT) {
                continue;
            }
            if (Math.abs(block.x() - base.x()) > WORK_POSITION_RADIUS + 1
                || Math.abs(block.z() - base.z()) > WORK_POSITION_RADIUS + 1) {
                continue;
            }
            lowerTrunk.add(block);
        }
        return lowerTrunk.isEmpty() ? List.of(base) : lowerTrunk;
    }

    private static WorkTargetScore workTargetScore(
        Vector3d candidate,
        Vector3d workerPosition,
        List<BlockPosition> lowerTrunkBlocks
    ) {
        double nearestWoodDistanceSquared = Double.POSITIVE_INFINITY;
        for (BlockPosition block : lowerTrunkBlocks) {
            double dx = candidate.x - (block.x() + 0.5);
            double dz = candidate.z - (block.z() + 0.5);
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < nearestWoodDistanceSquared) {
                nearestWoodDistanceSquared = distanceSquared;
            }
        }
        return new WorkTargetScore(
            nearestWoodDistanceSquared,
            workerPosition.distanceSquared(candidate)
        );
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

    public void forgetRuntime(Ref<EntityStore> ref) {
        if (ref != null) {
            releaseWorker(unitRegistry.keyOf(ref));
        }
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
        for (int[] offset : TREE_CONNECTED_OFFSETS) {
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

    private static int[][] createTreeConnectedOffsets() {
        int[][] offsets = new int[26][3];
        int index = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    offsets[index][0] = dx;
                    offsets[index][1] = dy;
                    offsets[index][2] = dz;
                    index++;
                }
            }
        }
        return offsets;
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

    private record WorkTargetScore(
        double woodDistanceSquared,
        double workerDistanceSquared
    ) {
        private boolean betterThan(
            double bestWoodDistanceSquared,
            double bestWorkerDistanceSquared
        ) {
            if (woodDistanceSquared + WORK_TARGET_SCORE_EPSILON < bestWoodDistanceSquared) {
                return true;
            }
            return Math.abs(woodDistanceSquared - bestWoodDistanceSquared)
                <= WORK_TARGET_SCORE_EPSILON
                && workerDistanceSquared < bestWorkerDistanceSquared;
        }
    }

    private static final class ScanCounters {
        private long positionsChecked;
        private long treeBases;
        private long treesCollected;
        private long treeBlocksCollected;
        private long protectedTrees;
        private long reservedTrees;
        private long workTargetChecks;
        private long noStandPositionTrees;
        private long usableTrees;
    }

    private static final class WorkerRuntime {
        private final WoodcutterJob job = new WoodcutterJob();
        private final WorkDecisionSchedule decisions = new WorkDecisionSchedule();
        private TreeStructure tree;
        private boolean animationStarted;
        private double diagnosticElapsed;
    }
}
