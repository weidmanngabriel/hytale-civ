package dev.civilizations.hytale;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Executes the Phase-1 Civ mine worker using native Hytale navigation, harvest and prefab APIs. */
public final class MinerWorkSystem extends EntityTickingSystem<EntityStore> {

    private static final String TYPE_TAG = "civ.type";
    private static final String BUILDING_TAG = "civ.building";
    private static final String WORKPLACE_ACCESS = "workplace_access";
    private static final String TUNNEL_CONNECTOR = "mine_tunnel_connector";
    private static final String MINE_BUILDING = "mine";
    private static final String MINING_ITEM_ANIMATIONS = "Civ_Miner_Pickaxe";
    private static final String MINING_ANIMATION = "SwingDown";
    private static final String SUPPORT_PREFAB_KEY = "Civilizations/Mine/Mine_Support_01";
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
            stopMiningAnimation(ref, store);
            workers.remove(key);
            return;
        }
        if (!activityRegistry.autonomousWorkAllowed(ref)) {
            stopMiningAnimation(ref, store);
            return;
        }

        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        World world = store.getExternalData().getWorld();
        UUID worldId = world.getWorldConfig().getUuid();
        BuildingPlacementRegistry.BuildingInstance mine = assignedMine(ref, worldId);
        if (mine == null) {
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store);
            return;
        }

        WorkerRuntime runtime = workers.computeIfAbsent(key, ignored -> new WorkerRuntime());
        if (!mine.id().equals(runtime.mineId)) {
            runtime.reset(mine.id());
        }

        PrefabPlacementService.PlacedMarker entrance = marker(world, mine, WORKPLACE_ACCESS);
        PrefabPlacementService.PlacedMarker connector = marker(world, mine, TUNNEL_CONNECTOR);
        if (connector == null || connector.bounds() == null) {
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store);
            return;
        }

        Vector3d position = transform.getPosition();
        if (!runtime.enteredMine && entrance != null && entrance.bounds() != null) {
            Vector3d target = center(entrance.bounds(), entrance.bounds().minY());
            if (!arrived(position, target)) {
                unitRegistry.setMoveTarget(ref, target);
                stopMiningAnimation(ref, store);
                return;
            }
            runtime.enteredMine = true;
        }

        MineSegment segment = resolveSegment(world, mine, connector, runtime);
        if (segment == null) {
            unitRegistry.clearMoveTarget(ref);
            stopMiningAnimation(ref, store);
            runtime.retryElapsed += dt;
            if (runtime.retryElapsed >= RETRY_SECONDS) {
                runtime.retryElapsed = 0.0;
                runtime.segmentId = null;
            }
            return;
        }
        runtime.retryElapsed = 0.0;

        if (segment.status() == MineSegment.Status.RESERVED) {
            segment = segment.withStatus(MineSegment.Status.MINING);
            tunnelRegistry.put(world, segment);
        }

        Vector3d workTarget = workTarget(segment);
        if (!arrived(position, workTarget)) {
            unitRegistry.setMoveTarget(ref, workTarget);
            stopMiningAnimation(ref, store);
            runtime.animationStarted = false;
            return;
        }
        unitRegistry.clearMoveTarget(ref);

        if (!runtime.animationStarted) {
            AnimationUtils.playAnimation(
                ref,
                AnimationSlot.Action,
                MINING_ITEM_ANIMATIONS,
                MINING_ANIMATION,
                store
            );
            runtime.animationStarted = true;
        }

        runtime.workElapsed += dt;
        while (runtime.workElapsed >= MineTuning.secondsPerBlock()) {
            runtime.workElapsed -= MineTuning.secondsPerBlock();
            segment = advanceOneBlock(world, ref, store, mine, segment);
            if (segment == null) {
                stopMiningAnimation(ref, store);
                runtime.animationStarted = false;
                runtime.segmentId = null;
                return;
            }
            runtime.segmentId = segment.id();
            if (segment.status() == MineSegment.Status.COMPLETE) {
                stopMiningAnimation(ref, store);
                runtime.animationStarted = false;
                runtime.workElapsed = 0.0;
                MineSegment next = chooseNext(world, mine, segment);
                runtime.segmentId = next == null ? null : next.id();
                return;
            }
        }
    }

    public void forgetRuntime(Ref<EntityStore> ref) {
        if (ref != null) workers.remove(unitRegistry.keyOf(ref));
    }

    private BuildingPlacementRegistry.BuildingInstance assignedMine(
        Ref<EntityStore> ref,
        UUID worldId
    ) {
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
        if (runtime.segmentId != null) {
            MineSegment existing = tunnelRegistry.get(worldId, runtime.segmentId);
            if (existing != null && existing.status() != MineSegment.Status.COMPLETE
                && existing.status() != MineSegment.Status.BLOCKED) {
                return existing;
            }
        }

        MineSegment unfinished = tunnelRegistry.unfinishedForMine(worldId, mine.id());
        if (unfinished != null) {
            runtime.segmentId = unfinished.id();
            return unfinished;
        }

        if (!tunnelRegistry.segmentsForMine(worldId, mine.id()).isEmpty()) {
            return null;
        }

        MineDirection direction = outwardDirection(mine.bounds(), connector.bounds());
        MineSegment initial = MineSegment.reserved(
            UUID.randomUUID(),
            mine.id(),
            null,
            initialStart(connector.bounds(), direction),
            direction
        );
        if (!validCandidate(world, mine, initial)) return null;
        tunnelRegistry.put(world, initial);
        runtime.segmentId = initial.id();
        return initial;
    }

    private MineSegment advanceOneBlock(
        World world,
        Ref<EntityStore> worker,
        Store<EntityStore> entityStore,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineSegment segment
    ) {
        int index = segment.nextBlockIndex();
        if (index >= MineTuning.blocksPerSegment()) return finishSegment(world, segment);

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
                worker,
                null,
                List.of(new Vector3i(target.x(), target.y(), target.z())),
                0,
                entityStore,
                chunkStore
            );
            if (!isEmpty(loadedBlockType(world, target))) {
                return segment;
            }
        }

        MineSegment updated = segment.withProgress(index + 1);
        updated = placeDueSupports(world, updated);
        if (updated.complete()) updated = finishSegment(world, updated);
        tunnelRegistry.put(world, updated);
        return updated;
    }

    private MineSegment placeDueSupports(World world, MineSegment segment) {
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int completedDepth = segment.nextBlockIndex() / faceSize;
        int due = completedDepth / MineTuning.SUPPORT_SPACING_BLOCKS;
        MineSegment updated = segment;
        while (updated.supportsPlaced() < due && updated.supportsPlaced() < 2) {
            int supportDepth = (updated.supportsPlaced() + 1) * MineTuning.SUPPORT_SPACING_BLOCKS;
            placeSupport(world, updated, supportDepth);
            updated = updated.withSupportsPlaced(updated.supportsPlaced() + 1);
        }
        return updated;
    }

    private static MineSegment finishSegment(World world, MineSegment segment) {
        return segment.withStatus(MineSegment.Status.COMPLETE)
            .withProgress(MineTuning.blocksPerSegment());
    }

    private MineSegment chooseNext(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineSegment parent
    ) {
        List<WeightedDirection> valid = new ArrayList<>();
        addIfValid(world, mine, parent, parent.direction(), MineTuning.STRAIGHT_WEIGHT, valid);
        addIfValid(world, mine, parent, parent.direction().left(), MineTuning.LEFT_WEIGHT, valid);
        addIfValid(world, mine, parent, parent.direction().right(), MineTuning.RIGHT_WEIGHT, valid);
        if (valid.isEmpty()) return null;

        int total = valid.stream().mapToInt(WeightedDirection::weight).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);
        WeightedDirection selected = valid.getFirst();
        for (WeightedDirection option : valid) {
            if (roll < option.weight()) {
                selected = option;
                break;
            }
            roll -= option.weight();
        }

        MineSegment next = MineSegment.reserved(
            UUID.randomUUID(),
            mine.id(),
            parent.id(),
            parent.nextStart(selected.direction()),
            selected.direction()
        );
        tunnelRegistry.put(world, next);
        return next;
    }

    private void addIfValid(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineSegment parent,
        MineDirection direction,
        int weight,
        List<WeightedDirection> result
    ) {
        MineSegment candidate = MineSegment.reserved(
            UUID.randomUUID(), mine.id(), parent.id(), parent.nextStart(direction), direction
        );
        if (validCandidate(world, mine, candidate)) result.add(new WeightedDirection(direction, weight));
    }

    private boolean validCandidate(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        MineSegment segment
    ) {
        UUID worldId = world.getWorldConfig().getUuid();
        if (tunnelRegistry.conflicts(worldId, segment)) return false;
        for (BlockPosition block : segment.blocks()) {
            if (!safeBlock(world, mine, block)) return false;
        }
        return true;
    }

    private boolean safeBlock(
        World world,
        BuildingPlacementRegistry.BuildingInstance mine,
        BlockPosition block
    ) {
        for (BuildingPlacementRegistry.BuildingInstance building :
            buildingRegistry.buildings(world.getWorldConfig().getUuid())) {
            if (!building.id().equals(mine.id()) && building.bounds().containsBlock(block)) return false;
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

    private void placeSupport(World world, MineSegment segment, int depth) {
        BlockSelection raw = PrefabStore.get().getAssetPrefab(SUPPORT_PREFAB_KEY);
        if (raw == null) {
            System.err.println("[Civ Mine] Missing support prefab " + SUPPORT_PREFAB_KEY);
            return;
        }
        BlockSelection selection = new BlockSelection(raw);
        if (segment.direction() == MineDirection.NORTH || segment.direction() == MineDirection.SOUTH) {
            selection = selection.rotate(Axis.Y, 1);
        }

        List<BlockPosition> face = new ArrayList<>();
        int faceStart = (depth - 1) * MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        for (int width = 0; width < MineTuning.TUNNEL_WIDTH_BLOCKS; width++) {
            face.add(segment.blockAtIndex(faceStart + width));
        }
        int desiredMinX = face.stream().mapToInt(BlockPosition::x).min().orElseThrow();
        int desiredMinZ = face.stream().mapToInt(BlockPosition::z).min().orElseThrow();
        Vector3i localMin = selection.getSelectionMin();
        Vector3i origin = new Vector3i(
            desiredMinX - localMin.x + selection.getAnchorX(),
            segment.start().y() - localMin.y + selection.getAnchorY(),
            desiredMinZ - localMin.z + selection.getAnchorZ()
        );
        selection.placeNoReturn(world, origin, world.getEntityStore().getStore());
    }

    private static Vector3d workTarget(MineSegment segment) {
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int depth = Math.min(MineTuning.SEGMENT_LENGTH_BLOCKS - 1, segment.nextBlockIndex() / faceSize);
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
        double dx = position.x - target.x;
        double dz = position.z - target.z;
        return dx * dx + dz * dz <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    }

    private static void stopMiningAnimation(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref != null && ref.isValid()) AnimationUtils.stopAnimation(ref, AnimationSlot.Action, store);
    }

    private record WeightedDirection(MineDirection direction, int weight) {
    }

    private static final class WorkerRuntime {
        private UUID mineId;
        private UUID segmentId;
        private boolean enteredMine;
        private boolean animationStarted;
        private double workElapsed;
        private double retryElapsed;

        private void reset(UUID nextMineId) {
            mineId = nextMineId;
            segmentId = null;
            enteredMine = false;
            animationStarted = false;
            workElapsed = 0.0;
            retryElapsed = 0.0;
        }
    }
}
