package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.Profession;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.scenario.WoodcutterBasicScenario;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Headless end-to-end probe for the shared deterministic woodcutter scenario.
 * Registered only when civilizations.runtimeProbe=true.
 */
final class CivWoodcutterFixtureProbeCommand extends CommandBase {

    private static final String FLAT_GENERATOR = "Flat";
    private static final String DEFAULT_STORAGE = "default";
    private static final String ROLE = "Civ_Inhabitant";
    private static final String OAK_PREFAB_KEY = "Trees/Oak/Stage_1/Oak_Stage1_001";
    private static final String WOOD_GATHER_TYPE = "Woods";

    private static final int FIXTURE_MARGIN_BLOCKS = 8;
    private static final int MAX_SCAN_Y = 28;
    private static final long SETTLE_MILLIS = 750L;
    private static final long ASSERT_INTERVAL_MILLIS = 250L;
    private static final long PROBE_TIMEOUT_MILLIS = 40_000L;
    private static final double MINIMUM_MOVED_DISTANCE = 2.0;

    private final CivUnitRegistry unitRegistry;

    CivWoodcutterFixtureProbeCommand(CivUnitRegistry unitRegistry) {
        super("civwoodcutterprobe", "Runs the deterministic Hytale woodcutter runtime scenario.");
        requireNoPermission();
        this.unitRegistry = unitRegistry;
    }

    @Override
    protected void executeSync(CommandContext context) {
        Universe universe = Universe.get();
        String worldName = WoodcutterBasicScenario.TEST_WORLD_NAME;
        if (universe.getWorld(worldName) != null || universe.isWorldLoadable(worldName)) {
            fail("test world already exists: " + worldName, null);
            return;
        }

        System.out.println(
            "CIV_WOODCUTTER_RUNTIME_STARTED world=" + worldName
                + " generator=" + FLAT_GENERATOR
                + " prefab=" + OAK_PREFAB_KEY
        );

        universe.addWorld(worldName, FLAT_GENERATOR, DEFAULT_STORAGE)
            .whenComplete((world, throwable) -> {
                if (throwable != null || world == null) {
                    fail("flat test world could not be created", throwable);
                    return;
                }
                world.execute(() -> preloadFixtureChunks(world));
            });
    }

    private void preloadFixtureChunks(World world) {
        try {
            Bounds bounds = fixtureBounds();
            int minChunkX = Math.floorDiv(bounds.minX(), 32);
            int maxChunkX = Math.floorDiv(bounds.maxX(), 32);
            int minChunkZ = Math.floorDiv(bounds.minZ(), 32);
            int maxChunkZ = Math.floorDiv(bounds.maxZ(), 32);

            List<CompletableFuture<WorldChunk>> futures = new ArrayList<>();
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    long chunkIndex = ChunkUtil.indexChunkFromBlock(chunkX * 32, chunkZ * 32);
                    futures.add(world.getChunkAsync(chunkIndex));
                }
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, throwable) -> world.execute(() -> {
                    if (throwable != null) {
                        fail("fixture chunks could not be loaded", throwable);
                        return;
                    }
                    placeTreesAndScheduleStart(world);
                }));
        } catch (Throwable throwable) {
            fail("fixture chunk preload threw an exception", throwable);
        }
    }

    private void placeTreesAndScheduleStart(World world) {
        try {
            int startX = (int) Math.floor(WoodcutterBasicScenario.WOODCUTTER_START.x());
            int startZ = (int) Math.floor(WoodcutterBasicScenario.WOODCUTTER_START.z());
            WorldChunk startChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(startX, startZ));
            if (startChunk == null) {
                fail("flat start chunk is not loaded", null);
                return;
            }

            BlockType ground = startChunk.getBlockType(startX, 0, startZ);
            BlockType feet = startChunk.getBlockType(startX, 1, startZ);
            if (ground == null || ground == BlockType.EMPTY) {
                fail("flat generator did not create ground at y=0", null);
                return;
            }
            if (feet != null && feet != BlockType.EMPTY) {
                fail("flat generator did not leave entity space empty at y=1", null);
                return;
            }

            PrefabStore prefabStore = PrefabStore.get();
            Path prefabPath = prefabStore.findBrowsablePrefabPath(OAK_PREFAB_KEY);
            if (prefabPath == null) {
                fail("vanilla oak prefab is not browsable: " + OAK_PREFAB_KEY, null);
                return;
            }
            BlockSelection source = prefabStore.getPrefab(prefabPath);
            if (source == null || source.getBlockCount() == 0) {
                fail("vanilla oak prefab could not be loaded: " + prefabPath, null);
                return;
            }

            for (BlockPosition anchor : WoodcutterBasicScenario.TREE_ANCHORS) {
                BlockSelection tree = new BlockSelection(source);
                tree.placeNoReturn(
                    world,
                    new Vector3i(anchor.x(), anchor.y(), anchor.z()),
                    world.getEntityStore().getStore()
                );
            }

            System.out.println(
                "CIV_WOODCUTTER_FLAT_WORLD_READY ground=" + ground.getId()
                    + " treeAnchors=" + WoodcutterBasicScenario.TREE_ANCHORS.size()
                    + " prefabBlocks=" + source.getBlockCount()
            );

            world.scheduleAfter(
                () -> verifyFixtureAndSpawnWorker(world),
                SETTLE_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("tree fixture placement threw an exception", throwable);
        }
    }

    private void verifyFixtureAndSpawnWorker(World world) {
        try {
            int initialWoodBlocks = countWoodBlocks(world);
            int initialTrunkBases = countTrunkBases(world);
            if (initialWoodBlocks <= 0) {
                fail("placed oak fixtures contain no Woods blocks", null);
                return;
            }
            if (initialTrunkBases < WoodcutterBasicScenario.TREE_ANCHORS.size()) {
                fail(
                    "expected at least " + WoodcutterBasicScenario.TREE_ANCHORS.size()
                        + " trunk bases but found " + initialTrunkBases,
                    null
                );
                return;
            }

            Vector3d requestedStart = new Vector3d(
                WoodcutterBasicScenario.WOODCUTTER_START.x(),
                WoodcutterBasicScenario.WOODCUTTER_START.y(),
                WoodcutterBasicScenario.WOODCUTTER_START.z()
            );
            var spawned = NPCPlugin.get().spawnNPC(
                world.getEntityStore().getStore(),
                ROLE,
                null,
                requestedStart,
                new Rotation3f()
            );
            if (spawned == null || spawned.first() == null || !spawned.first().isValid()) {
                fail("Hytale could not spawn Civ_Inhabitant", null);
                return;
            }

            Ref<EntityStore> ref = spawned.first();
            if (!unitRegistry.toggleClaim(ref) || !unitRegistry.isClaimed(ref)) {
                fail("spawned NPC could not be claimed as a Civ inhabitant", null);
                return;
            }
            unitRegistry.assignProfession(ref, Profession.WOODCUTTER);
            if (unitRegistry.getProfession(ref) != Profession.WOODCUTTER) {
                fail("spawned Civ inhabitant was not assigned WOODCUTTER", null);
                return;
            }

            TransformComponent transform = ref.getStore()
                .getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                fail("spawned woodcutter has no TransformComponent", null);
                return;
            }

            ProbeState state = new ProbeState(
                initialWoodBlocks,
                new Vector3d(transform.getPosition()),
                world.getTick(),
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PROBE_TIMEOUT_MILLIS)
            );
            System.out.println(
                "CIV_WOODCUTTER_FIXTURE_READY woodBlocks=" + initialWoodBlocks
                    + " trunkBases=" + initialTrunkBases
                    + " entity=" + ref.getIndex()
            );

            world.scheduleAfter(
                () -> assertWoodcutterProgress(world, ref, state),
                ASSERT_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("woodcutter setup threw an exception", throwable);
        }
    }

    private void assertWoodcutterProgress(World world, Ref<EntityStore> ref, ProbeState state) {
        try {
            if (!ref.isValid()) {
                fail("woodcutter entity became invalid", null);
                return;
            }
            if (!unitRegistry.isClaimed(ref)) {
                fail("woodcutter lost its Civ claim", null);
                return;
            }
            if (unitRegistry.getProfession(ref) != Profession.WOODCUTTER) {
                fail("woodcutter lost its profession", null);
                return;
            }

            TransformComponent transform = ref.getStore()
                .getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                fail("woodcutter lost its TransformComponent", null);
                return;
            }

            int currentWoodBlocks = countWoodBlocks(world);
            long ticksElapsed = world.getTick() - state.startTick;
            double movedDistance = horizontalDistance(state.startPosition, transform.getPosition());

            if (!state.firstTreeFelled && currentWoodBlocks < state.initialWoodBlocks) {
                state.firstTreeFelled = true;
                state.woodBlocksAfterFirstFell = currentWoodBlocks;
                state.firstFellTick = world.getTick();
                System.out.println(
                    "CIV_WOODCUTTER_TREE_FELLED ticks=" + ticksElapsed
                        + " initialWood=" + state.initialWoodBlocks
                        + " remainingWood=" + currentWoodBlocks
                        + " moved=" + String.format("%.2f", movedDistance)
                );
            }

            if (state.firstTreeFelled && !state.reengagedAfterFirstFell
                && world.getTick() > state.firstFellTick) {
                boolean hasNextMoveTarget = unitRegistry.getMoveTarget(ref) != null;
                boolean moreWoodRemoved = currentWoodBlocks < state.woodBlocksAfterFirstFell;
                if (hasNextMoveTarget || moreWoodRemoved) {
                    state.reengagedAfterFirstFell = true;
                    System.out.println(
                        "CIV_WOODCUTTER_REENGAGED ticks=" + ticksElapsed
                            + " nextMoveTarget=" + hasNextMoveTarget
                            + " remainingWood=" + currentWoodBlocks
                    );
                }
            }

            if (state.firstTreeFelled && state.reengagedAfterFirstFell) {
                if (movedDistance < MINIMUM_MOVED_DISTANCE) {
                    fail("tree was felled without meaningful NPC movement: " + movedDistance, null);
                    return;
                }
                System.out.println(
                    "CIV_WOODCUTTER_RUNTIME_PASS ticks=" + ticksElapsed
                        + " entity=" + ref.getIndex()
                        + " moved=" + String.format("%.2f", movedDistance)
                        + " initialWood=" + state.initialWoodBlocks
                        + " remainingWood=" + currentWoodBlocks
                );
                HytaleServer.get().shutdownServer();
                return;
            }

            if (System.nanoTime() >= state.deadlineNanos) {
                fail(
                    "woodcutter scenario timed out after " + ticksElapsed + " ticks"
                        + ", moved=" + String.format("%.2f", movedDistance)
                        + ", initialWood=" + state.initialWoodBlocks
                        + ", remainingWood=" + currentWoodBlocks
                        + ", firstTreeFelled=" + state.firstTreeFelled
                        + ", reengaged=" + state.reengagedAfterFirstFell
                        + ", moveTarget=" + unitRegistry.getMoveTarget(ref),
                    null
                );
                return;
            }

            world.scheduleAfter(
                () -> assertWoodcutterProgress(world, ref, state),
                ASSERT_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("woodcutter assertion threw an exception", throwable);
        }
    }

    private static int countWoodBlocks(World world) {
        Bounds bounds = fixtureBounds();
        int count = 0;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = 1; y <= MAX_SCAN_Y; y++) {
                    if (isWoodStructureBlock(getLoadedBlockType(world, x, y, z))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static int countTrunkBases(World world) {
        Bounds bounds = fixtureBounds();
        int count = 0;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = 1; y <= MAX_SCAN_Y; y++) {
                    BlockType current = getLoadedBlockType(world, x, y, z);
                    if (!isTreeTrunk(current)) {
                        continue;
                    }
                    if (!isTreeTrunk(getLoadedBlockType(world, x, y - 1, z))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static BlockType getLoadedBlockType(World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        return chunk == null ? null : chunk.getBlockType(x, y, z);
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

    private static Bounds fixtureBounds() {
        int minX = (int) Math.floor(WoodcutterBasicScenario.WOODCUTTER_START.x());
        int maxX = minX;
        int minZ = (int) Math.floor(WoodcutterBasicScenario.WOODCUTTER_START.z());
        int maxZ = minZ;
        for (BlockPosition anchor : WoodcutterBasicScenario.TREE_ANCHORS) {
            minX = Math.min(minX, anchor.x());
            maxX = Math.max(maxX, anchor.x());
            minZ = Math.min(minZ, anchor.z());
            maxZ = Math.max(maxZ, anchor.z());
        }
        return new Bounds(
            minX - FIXTURE_MARGIN_BLOCKS,
            maxX + FIXTURE_MARGIN_BLOCKS,
            minZ - FIXTURE_MARGIN_BLOCKS,
            maxZ + FIXTURE_MARGIN_BLOCKS
        );
    }

    private static double horizontalDistance(Vector3d first, Vector3d second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void fail(String reason, Throwable throwable) {
        System.out.println("CIV_WOODCUTTER_RUNTIME_FAIL " + reason);
        if (throwable != null) {
            throwable.printStackTrace(System.out);
        }
        HytaleServer.get().shutdownServer();
    }

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {
    }

    private static final class ProbeState {
        private final int initialWoodBlocks;
        private final Vector3d startPosition;
        private final long startTick;
        private final long deadlineNanos;
        private boolean firstTreeFelled;
        private int woodBlocksAfterFirstFell;
        private long firstFellTick;
        private boolean reengagedAfterFirstFell;

        private ProbeState(
            int initialWoodBlocks,
            Vector3d startPosition,
            long startTick,
            long deadlineNanos
        ) {
            this.initialWoodBlocks = initialWoodBlocks;
            this.startPosition = startPosition;
            this.startTick = startTick;
            this.deadlineNanos = deadlineNanos;
            this.woodBlocksAfterFirstFell = initialWoodBlocks;
        }
    }
}
