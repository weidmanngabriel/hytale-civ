package dev.civilizations.hytale;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineTuning;
import dev.civilizations.core.Profession;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Executes Civ mine work using native Hytale navigation, harvesting and prefab placement.
 * Hytale navigation continues between delayed Civ sessions so segment reconciliation and marker
 * resolution are not repeated for every miner on every engine tick.
 */
public final class MinerWorkSystem extends DelayedEntitySystem<EntityStore> {

    private static final float TICK_INTERVAL_SECONDS = 0.50f;
    private static final String TYPE_TAG = "civ.type";
    private static final String BUILDING_TAG = "civ.building";
    private static final String WORKPLACE_ACCESS = "workplace_access";
    private static final String TUNNEL_CONNECTOR = "mine_tunnel_connector";
    private static final String MINE_BUILDING = "mine";
    private static final String MINING_ITEM_ANIMATIONS = "Civ_Miner_Pickaxe";
    private static final String MINING_ANIMATION = "SwingDown";
    private static final String SUPPORT_PREFAB_KEY = "Civilizations/Mine/Mine_Support_01.prefab.json";
    private static final String SUPPORT_POST_BLOCK = "Wood_Fir_Branch_Long";
    private static final String SUPPORT_BEAM_BLOCK = "Wood_Fir_Trunk";
    private static final double ARRIVAL_DISTANCE = 1.1;
    private static final double RETRY_SECONDS = 1.0;

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;
    private final BuildingPlacementRegistry buildingRegistry;
    private final MineTunnelRegistry tunnelRegistry;
    private final Map<CivUnitRegistry.UnitKey, WorkerRuntime> workers = new ConcurrentHashMap<>();

    public MinerWorkSystem(
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
        if (!ref.isValid()) {
            workers.remove(key);
            return;
        }
        if (unitRegistry.getProfession(ref) != Profession.MINER) {
            workers.remove(key);
            return;
        }

        WorkerRuntime runtime = workers.computeIfAbsent(key, ignored -> new WorkerRuntime());
        if (!activityRegistry.autonomousWorkAllowed(ref)) {
            stopMiningAnimation(ref, store, runtime);
            runtime.interruptForManualMove();
            return;
        }

        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        World world = store.getExternalData().getWorld();
        UUID worldId = world.getWorldConfig().getUuid();
        BuildingPlacementRegistry.BuildingInstance mine = assignedMine(ref, worldId);
        if (mine == null) {
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            return;
        }
        if (!mine.id().equals(runtime.mineId) || mine.phase() != runtime.minePhase) {
            stopMiningAnimation(ref, store, runtime);
            runtime.reset(mine.id(), mine.phase());
        }

        PrefabPlacementService.PlacedMarker entrance = marker(world, mine, WORKPLACE_ACCESS);
        PrefabPlacementService.PlacedMarker connector = marker(world, mine, TUNNEL_CONNECTOR);
        if (connector == null || connector.bounds() == null) {
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            return;
        }

        Vector3d position = transform.getPosition();
        if (!runtime.enteredMine && entrance != null && entrance.bounds() != null) {
            Vector3d target = center(entrance.bounds(), entrance.bounds().minY());
            if (!arrived(position, target)) {
                navigateTo(ref, position, target, runtime);
                stopMiningAnimation(ref, store, runtime);
                return;
            }
            runtime.enteredMine = true;
            runtime.navigationArrived();
        }

        if (!runtime.reachedConnector) {
            Vector3d target = center(connector.bounds(), connector.bounds().minY());
            if (!arrived(position, target)) {
                navigateTo(ref, position, target, runtime);
                stopMiningAnimation(ref, store, runtime);
                return;
            }
            runtime.reachedConnector = true;
            runtime.navigationArrived();
        }

        MineSegment segment = resolveSegment(world, mine, connector, runtime);
        if (segment == null) {
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            runtime.retryElapsed += dt;
            if (runtime.retryElapsed >= RETRY_SECONDS) {
                runtime.retryElapsed = 0.0;
                runtime.segmentId = null;
            }
            return;
        }
        runtime.retryElapsed = 0.0;

        segment = reconcileSegmentWithWorld(world, segment);
        if (segment == null) {
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store, runtime);
            return;
        }
        segment = placeDueSupports(world, segment);
        tunnelRegistry.put(world, segment);
        runtime.segmentId = segment.id();

        if (segment.status() == MineSegment.Status.COMPLETE) {
            continueAfterCompletedSegment(world, mine, position, runtime, segment);
            stopMiningAnimation(ref, store, runtime);
            return;
        }

        if (segment.status() == MineSegment.Status.RESERVED) {
            segment = segment.withStatus(MineSegment.Status.MINING);
            tunnelRegistry.put(world, segment);
        }

        Vector3d workTarget = workTarget(segment);
        if (!arrived(position, workTarget)) {
            navigateTo(ref, position, workTarget, runtime);
            stopMiningAnimation(ref, store, runtime);
            return;
        }
        runtime.navigationArrived();
        unitRegistry.clearMoveTarget(ref);

        if (!runtime.animationStarted) {
            AnimationUtils.playAnimation(
                ref, AnimationSlot.Action, MINING_ITEM_ANIMATIONS, MINING_ANIMATION, store
            );
            runtime.animationStarted = true;
        }

        runtime.workElapsed += dt;
        while (runtime.workElapsed >= MineTuning.secondsPerBlock()) {
            runtime.workElapsed -= MineTuning.secondsPerBlock();
            segment = advanceOneBlock(world, ref, store, mine, segment);
            if (segment == null) {
                stopMiningAnimation(ref, store, runtime);
                runtime.segmentId = null;
                return;
            }
            runtime.segmentId = segment.id();
            if (segment.status() == MineSegment.Status.COMPLETE) {
                continueAfterCompletedSegment(world, mine, position, runtime, segment);
                stopMiningAnimation(ref, store, runtime);
                return;
            }
        }
    }

    public void forgetRuntime(Ref<EntityStore> ref) {
        if (ref != null) workers.remove(unitRegistry.keyOf(ref));
    }

    private void continueAfterCompletedSegment(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        Vector3d workerPosition,
        WorkerRuntime runtime,
        MineSegment completed
    ) {
        runtime.workElapsed = 0.0;
        MineSegment next = existingChild(world.getWorldConfig().getUuid(), completed.id());
        if (next == null) next = chooseNext(world, mine, completed);
        logSegmentTransition(workerPosition, completed, next);
        runtime.segmentId = next == null ? null : next.id();
        runtime.navigationArrived();
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

    private MineSegment resolveSegment(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        PrefabPlacementService.PlacedMarker connector,
        WorkerRuntime runtime
    ) {
        UUID worldId = world.getWorldConfig().getUuid();

        if (runtime.resumeAfterManual) {
            MineSegment resumed = chooseResumeSegment(world, mine, runtime);
            if (resumed != null) {
                runtime.resumeAfterManual = false;
                runtime.interruptedSegmentId = null;
                runtime.segmentId = resumed.id();
                return resumed;
            }
        }

        MineSegment reopened = earliestReopenedCompletedSegment(world, mine.id());
        if (reopened != null) {
            runtime.segmentId = reopened.id();
            return reopened;
        }
        if (runtime.segmentId != null) {
            MineSegment existing = tunnelRegistry.get(worldId, runtime.segmentId);
            if (existing != null && existing.status() != MineSegment.Status.BLOCKED) return existing;
        }
        MineSegment unfinished = tunnelRegistry.unfinishedForMine(worldId, mine.id());
        if (unfinished != null) {
            runtime.segmentId = unfinished.id();
            return unfinished;
        }
        if (!tunnelRegistry.segmentsForMine(worldId, mine.id()).isEmpty()) return null;

        MineDirection direction = outwardDirection(mine.bounds(), connector.bounds());
        MineSegment initial = fittingCandidate(
            world, mine, null, initialStart(connector.bounds(), direction), direction, true
        );
        if (initial == null) return null;
        tunnelRegistry.put(world, initial);
        runtime.segmentId = initial.id();
        return initial;
    }

    private MineSegment chooseResumeSegment(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        WorkerRuntime runtime
    ) {
        UUID worldId = world.getWorldConfig().getUuid();
        List<MineSegment> alternatives = tunnelRegistry.segmentsForMine(worldId, mine.id()).stream()
            .filter(candidate -> candidate.status() == MineSegment.Status.RESERVED
                || candidate.status() == MineSegment.Status.MINING)
            .filter(candidate -> runtime.interruptedSegmentId == null
                || !candidate.id().equals(runtime.interruptedSegmentId))
            .toList();
        if (!alternatives.isEmpty()) {
            return alternatives.get(ThreadLocalRandom.current().nextInt(alternatives.size()));
        }

        MineSegment fresh = chooseFreshBranch(world, mine);
        if (fresh != null) {
            tunnelRegistry.put(world, fresh);
            return fresh;
        }

        if (runtime.interruptedSegmentId != null) {
            MineSegment interrupted = tunnelRegistry.get(worldId, runtime.interruptedSegmentId);
            if (interrupted != null && interrupted.status() != MineSegment.Status.BLOCKED) return interrupted;
        }
        return tunnelRegistry.unfinishedForMine(worldId, mine.id());
    }

    private MineSegment chooseFreshBranch(World world, BuildingPlacementRegistry.BuildingInstance mine) {
        List<WeightedCandidate> valid = new ArrayList<>();
        for (MineSegment parent : tunnelRegistry.segmentsForMine(world.getWorldConfig().getUuid(), mine.id())) {
            if (parent.status() != MineSegment.Status.COMPLETE) continue;
            addIfValid(world, mine, parent, parent.direction(), MineTuning.STRAIGHT_WEIGHT, valid);
            addIfValid(world, mine, parent, parent.direction().left(), MineTuning.LEFT_WEIGHT, valid);
            addIfValid(world, mine, parent, parent.direction().right(), MineTuning.RIGHT_WEIGHT, valid);
        }
        return selectWeighted(valid);
    }

    private MineSegment earliestReopenedCompletedSegment(World world, UUID mineId) {
        UUID worldId = world.getWorldConfig().getUuid();
        List<MineSegment> segments = tunnelRegistry.segmentsForMine(worldId, mineId);
        if (segments.isEmpty()) return null;
        Map<UUID, MineSegment> byId = new HashMap<>();
        for (MineSegment segment : segments) byId.put(segment.id(), segment);
        return segments.stream()
            .filter(segment -> segment.status() == MineSegment.Status.COMPLETE)
            .sorted(Comparator.comparingInt(segment -> ancestryDepth(segment, byId)))
            .map(segment -> reconcileSegmentWithWorld(world, segment))
            .filter(segment -> segment != null && segment.status() != MineSegment.Status.COMPLETE)
            .findFirst()
            .orElse(null);
    }

    private static int ancestryDepth(MineSegment segment, Map<UUID, MineSegment> byId) {
        int depth = 0;
        UUID parentId = segment.parentId();
        while (parentId != null && depth < byId.size()) {
            MineSegment parent = byId.get(parentId);
            if (parent == null) break;
            depth++;
            parentId = parent.parentId();
        }
        return depth;
    }

    private MineSegment reconcileSegmentWithWorld(World world, MineSegment segment) {
        int firstSolid = firstExcavationIndex(world, segment);
        if (firstSolid < 0) return null;
        MineSegment reconciled = segment.withProgress(firstSolid);
        if (firstSolid >= segment.blockCount()) return reconciled.withStatus(MineSegment.Status.COMPLETE);
        return reconciled.withStatus(MineSegment.Status.MINING);
    }

    private int firstExcavationIndex(World world, MineSegment segment) {
        for (int index = 0; index < segment.blockCount(); index++) {
            BlockType type = loadedBlockType(world, segment.blockAtIndex(index));
            if (type == null) return -1;
            if (isEmpty(type) || isExpectedSupportBlock(segment, index, type)) continue;
            return index;
        }
        return segment.blockCount();
    }

    private MineSegment advanceOneBlock(
        World world,
        Ref<EntityStore> worker,
        Store<EntityStore> entityStore,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineSegment segment
    ) {
        int index = segment.nextBlockIndex();
        if (index >= segment.blockCount()) return finishSegment(segment);
        BlockPosition target = segment.blockAtIndex(index);
        if (!safeBlock(world, mine, target)) {
            MineSegment blocked = segment.withStatus(MineSegment.Status.BLOCKED);
            tunnelRegistry.put(world, blocked);
            return null;
        }

        BlockType type = loadedBlockType(world, target);
        if (!isEmpty(type)) {
            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            BlockHarvestUtils.performBlockBreak(
                worker, null, List.of(new Vector3i(target.x(), target.y(), target.z())),
                0, entityStore, chunkStore
            );
            if (!isEmpty(loadedBlockType(world, target))) return segment;
        }

        MineSegment updated = segment.withProgress(index + 1);
        updated = placeDueSupports(world, updated);
        if (updated.complete()) updated = finishSegment(updated);
        tunnelRegistry.put(world, updated);
        return updated;
    }

    private MineSegment placeDueSupports(World world, MineSegment segment) {
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int completedDepth = segment.nextBlockIndex() / faceSize;
        int due = completedDepth / MineTuning.SUPPORT_SPACING_BLOCKS;
        int maxSupports = MineTuning.supportFramesForLength(segment.lengthBlocks());
        int highestKnown = segment.supportsPlaced();
        for (int number = 1; number <= due && number <= maxSupports; number++) {
            int depth = number * MineTuning.SUPPORT_SPACING_BLOCKS;
            if (supportPresent(world, segment, depth)) {
                if (number > highestKnown && !MineSupportPhysics.markBeamAsDeco(world, segment, depth)) continue;
                highestKnown = Math.max(highestKnown, number);
            } else if (number > highestKnown && placeSupport(world, segment, depth)) {
                highestKnown = number;
            }
        }
        return segment.withSupportsPlaced(highestKnown);
    }

    private boolean supportPresent(World world, MineSegment segment, int depth) {
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int faceStart = (depth - 1) * faceSize;
        for (int y = 0; y < MineTuning.TUNNEL_HEIGHT_BLOCKS; y++) {
            for (int width = 0; width < MineTuning.TUNNEL_WIDTH_BLOCKS; width++) {
                boolean top = y == MineTuning.TUNNEL_HEIGHT_BLOCKS - 1;
                boolean post = y < MineTuning.TUNNEL_HEIGHT_BLOCKS - 1
                    && (width == 0 || width == MineTuning.TUNNEL_WIDTH_BLOCKS - 1);
                if (!top && !post) continue;
                BlockType type = loadedBlockType(world, segment.blockAtIndex(
                    faceStart + y * MineTuning.TUNNEL_WIDTH_BLOCKS + width
                ));
                if (type == null || type.getId() == null) return false;
                if (!supportBlockIdMatches(top ? SUPPORT_BEAM_BLOCK : SUPPORT_POST_BLOCK, type.getId())) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean placeSupport(World world, MineSegment segment, int depth) {
        BlockSelection raw = PrefabStore.get().getAssetPrefabFromAnyPack(SUPPORT_PREFAB_KEY);
        if (raw == null) return false;
        BlockSelection selection = new BlockSelection(raw);
        int rotation = supportRotationDegrees(segment.direction());
        if (rotation != 0) selection = selection.rotate(Axis.Y, rotation);
        com.hypixel.hytale.builtin.blockphysics.BlockSelectionSupportUtil.applySupportValues(selection);
        BlockPosition support = segment.supportOrigin(depth);
        selection.placeNoReturn(
            world,
            new Vector3i(support.x(), support.y(), support.z()),
            world.getEntityStore().getStore()
        );
        return MineSupportPhysics.markBeamAsDeco(world, segment, depth);
    }

    private static boolean isExpectedSupportBlock(MineSegment segment, int index, BlockType type) {
        return type != null && isExpectedSupportCell(segment, index, type.getId());
    }

    static boolean isExpectedSupportCell(MineSegment segment, int index, String blockId) {
        if (blockId == null) return false;
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int depth = index / faceSize;
        int oneBasedDepth = depth + 1;
        if (oneBasedDepth > segment.lengthBlocks()) return false;
        if (oneBasedDepth % MineTuning.SUPPORT_SPACING_BLOCKS != 0) return false;
        int supportNumber = oneBasedDepth / MineTuning.SUPPORT_SPACING_BLOCKS;
        int completedDepth = segment.nextBlockIndex() / faceSize;
        int knownSupports = Math.max(
            segment.supportsPlaced(), completedDepth / MineTuning.SUPPORT_SPACING_BLOCKS
        );
        if (supportNumber > knownSupports) return false;
        int inFace = index % faceSize;
        int y = inFace / MineTuning.TUNNEL_WIDTH_BLOCKS;
        int width = inFace % MineTuning.TUNNEL_WIDTH_BLOCKS;
        if (y == MineTuning.TUNNEL_HEIGHT_BLOCKS - 1) {
            return supportBlockIdMatches(SUPPORT_BEAM_BLOCK, blockId);
        }
        return (width == 0 || width == MineTuning.TUNNEL_WIDTH_BLOCKS - 1)
            && supportBlockIdMatches(SUPPORT_POST_BLOCK, blockId);
    }

    static boolean supportBlockIdMatches(String expected, String actual) {
        return expected != null && actual != null && expected.equalsIgnoreCase(actual);
    }

    private static MineSegment finishSegment(MineSegment segment) {
        return segment.withStatus(MineSegment.Status.COMPLETE).withProgress(segment.blockCount());
    }

    private MineSegment existingChild(UUID worldId, UUID parentId) {
        return tunnelRegistry.segments(worldId).stream()
            .filter(segment -> parentId.equals(segment.parentId()))
            .findFirst()
            .orElse(null);
    }

    private MineSegment chooseNext(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineSegment parent
    ) {
        List<WeightedCandidate> valid = new ArrayList<>();
        addIfValid(world, mine, parent, parent.direction(), MineTuning.STRAIGHT_WEIGHT, valid);
        addIfValid(world, mine, parent, parent.direction().left(), MineTuning.LEFT_WEIGHT, valid);
        addIfValid(world, mine, parent, parent.direction().right(), MineTuning.RIGHT_WEIGHT, valid);
        MineSegment next = selectWeighted(valid);
        if (next != null) tunnelRegistry.put(world, next);
        return next;
    }

    private void addIfValid(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineSegment parent,
        MineDirection direction,
        int weight,
        List<WeightedCandidate> result
    ) {
        MineSegment candidate = fittingCandidate(
            world, mine, parent, parent.nextStart(direction), direction, false
        );
        if (candidate != null) result.add(new WeightedCandidate(candidate, weight));
    }

    private MineSegment fittingCandidate(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineSegment parent,
        BlockPosition start,
        MineDirection direction,
        boolean allowOwnMine
    ) {
        int minimum = MineTuning.MIN_SEGMENT_LENGTH_BLOCKS;
        int desired = Math.max(minimum, randomSegmentLength());
        for (int length = desired; length >= minimum; length--) {
            MineSegment candidate = MineSegment.reserved(
                UUID.randomUUID(), mine.id(), parent == null ? null : parent.id(),
                start, direction, length
            );
            if (validCandidate(world, mine, candidate, allowOwnMine)) return candidate;
        }
        return null;
    }

    private static int randomSegmentLength() {
        return ThreadLocalRandom.current().nextInt(
            MineTuning.MIN_SEGMENT_LENGTH_BLOCKS,
            MineTuning.MAX_SEGMENT_LENGTH_BLOCKS + 1
        );
    }

    private static MineSegment selectWeighted(List<WeightedCandidate> valid) {
        if (valid.isEmpty()) return null;
        int total = valid.stream().mapToInt(WeightedCandidate::weight).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (WeightedCandidate option : valid) {
            if (roll < option.weight()) return option.segment();
            roll -= option.weight();
        }
        return valid.getLast().segment();
    }

    private boolean validCandidate(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineSegment segment,
        boolean allowOwnMine
    ) {
        if (tunnelRegistry.conflicts(world.getWorldConfig().getUuid(), segment)) return false;
        for (BlockPosition block : segment.blocks()) {
            if (!safeBlock(world, mine, block, allowOwnMine)) return false;
        }
        return true;
    }

    private boolean safeBlock(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        BlockPosition block
    ) {
        return safeBlock(world, mine, block, false);
    }

    private boolean safeBlock(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        BlockPosition block,
        boolean allowOwnMine
    ) {
        for (BuildingPlacementRegistry.BuildingInstance building :
            buildingRegistry.buildings(world.getWorldConfig().getUuid())) {
            if ((!allowOwnMine || !building.id().equals(mine.id()))
                && building.bounds().containsBlock(block)) return false;
        }
        BlockType type = loadedBlockType(world, block);
        if (type == null) return false;
        if (isEmpty(type)) return true;
        BlockGathering gathering = type.getGathering();
        BlockBreakingDropType breaking = gathering == null ? null : gathering.getBreaking();
        return breaking != null;
    }

    private static BlockType loadedBlockType(World world, BlockPosition block) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(block.x(), block.z()));
        return chunk == null ? null : chunk.getBlockType(block.x(), block.y(), block.z());
    }

    private static boolean isEmpty(BlockType blockType) {
        return blockType == BlockType.EMPTY
            || (blockType != null && blockType.getMaterial() == BlockMaterial.Empty);
    }

    static int supportRotationDegrees(MineDirection direction) {
        return switch (direction) {
            case EAST -> 0;
            case NORTH -> 90;
            case WEST -> 180;
            case SOUTH -> 270;
        };
    }

    private static Vector3d workTarget(MineSegment segment) {
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int depth = Math.min(segment.totalDepthBlocks() - 1, segment.nextBlockIndex() / faceSize);
        int first = depth * faceSize;
        double x = 0.0;
        double z = 0.0;
        for (int width = 0; width < MineTuning.TUNNEL_WIDTH_BLOCKS; width++) {
            BlockPosition block = segment.blockAtIndex(first + width);
            x += block.x() + 0.5;
            z += block.z() + 0.5;
        }
        x /= MineTuning.TUNNEL_WIDTH_BLOCKS;
        z /= MineTuning.TUNNEL_WIDTH_BLOCKS;
        return new Vector3d(
            x - segment.direction().dx() * 1.35,
            segment.start().y(),
            z - segment.direction().dz() * 1.35
        );
    }

    private void navigateTo(
        Ref<EntityStore> ref,
        Vector3d position,
        Vector3d target,
        WorkerRuntime runtime
    ) {
        if (runtime.navigationTarget == null
            || runtime.navigationTarget.distanceSquared(target) > 0.0001) {
            runtime.beginNavigation(position, target);
            unitRegistry.setMoveTarget(ref, target);
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

    private static MineDirection outwardDirection(BuildingBounds building, BuildingBounds connector) {
        double bx = (building.minX() + building.maxX()) * 0.5;
        double bz = (building.minZ() + building.maxZ()) * 0.5;
        double cx = (connector.minX() + connector.maxX()) * 0.5;
        double cz = (connector.minZ() + connector.maxZ()) * 0.5;
        double dx = cx - bx;
        double dz = cz - bz;
        if (Math.abs(dx) > Math.abs(dz)) return dx >= 0 ? MineDirection.EAST : MineDirection.WEST;
        return dz >= 0 ? MineDirection.SOUTH : MineDirection.NORTH;
    }

    private static BlockPosition initialStart(BuildingBounds connector, MineDirection direction) {
        int y = (int) Math.floor(connector.minY());
        return switch (direction) {
            case NORTH -> new BlockPosition(
                (int) Math.floor(connector.minX()), y, (int) Math.floor(connector.minZ()) - 1
            );
            case SOUTH -> new BlockPosition(
                (int) Math.ceil(connector.maxX()) - 1, y, (int) Math.ceil(connector.maxZ())
            );
            case EAST -> new BlockPosition(
                (int) Math.ceil(connector.maxX()), y, (int) Math.floor(connector.minZ())
            );
            case WEST -> new BlockPosition(
                (int) Math.floor(connector.minX()) - 1, y, (int) Math.ceil(connector.maxZ()) - 1
            );
        };
    }

    private static Vector3d center(BuildingBounds bounds, double y) {
        return new Vector3d(
            (bounds.minX() + bounds.maxX()) * 0.5,
            y,
            (bounds.minZ() + bounds.maxZ()) * 0.5
        );
    }

    private static boolean arrived(Vector3d position, Vector3d target) {
        return position.distanceSquared(target) <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    }

    private static void logSegmentTransition(Vector3d position, MineSegment completed, MineSegment next) {
        System.out.println(
            "[Civ Mine Debug] segment-transition completed=" + completed.id()
                + " oldDirection=" + completed.direction()
                + " oldLength=" + completed.lengthBlocks()
                + " next=" + (next == null ? "null" : next.id())
                + " nextDirection=" + (next == null ? "null" : next.direction())
                + " nextLength=" + (next == null ? "null" : next.lengthBlocks())
                + " workerPos=" + position
        );
    }

    private static void stopMiningAnimation(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        WorkerRuntime runtime
    ) {
        if (runtime == null || !runtime.animationStarted) return;
        if (ref != null && ref.isValid()) AnimationUtils.stopAnimation(ref, AnimationSlot.Action, store);
        runtime.animationStarted = false;
    }

    private record WeightedCandidate(MineSegment segment, int weight) {
    }

    private static final class WorkerRuntime {
        private UUID mineId;
        private int minePhase;
        private UUID segmentId;
        private boolean enteredMine;
        private boolean reachedConnector;
        private boolean animationStarted;
        private double workElapsed;
        private double retryElapsed;
        private Vector3d navigationTarget;
        private boolean resumeAfterManual;
        private UUID interruptedSegmentId;

        private void interruptForManualMove() {
            if (!resumeAfterManual) interruptedSegmentId = segmentId;
            resumeAfterManual = true;
            segmentId = null;
            enteredMine = false;
            reachedConnector = false;
            workElapsed = 0.0;
            navigationArrived();
        }

        private void beginNavigation(Vector3d position, Vector3d target) {
            navigationTarget = new Vector3d(target);
        }

        private void navigationArrived() {
            navigationTarget = null;
        }

        private void reset(UUID nextMineId, int nextMinePhase) {
            mineId = nextMineId;
            minePhase = nextMinePhase;
            segmentId = null;
            enteredMine = false;
            reachedConnector = false;
            animationStarted = false;
            workElapsed = 0.0;
            retryElapsed = 0.0;
            resumeAfterManual = false;
            interruptedSegmentId = null;
            navigationArrived();
        }
    }
}
