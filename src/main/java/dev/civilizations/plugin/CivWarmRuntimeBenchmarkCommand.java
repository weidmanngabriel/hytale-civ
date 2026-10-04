package dev.civilizations.plugin;

import com.hypixel.hytale.builtin.blockphysics.BlockSelectionSupportUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivInhabitantData;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.hytale.MineSupportPhysics;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime-only benchmark for warm-server Hytale test execution.
 *
 * <p>The benchmark creates three independent native Flat worlds in one Hytale process. Two
 * worlds run the normal Civ manual-movement adapter at 1x and 4x world time. A third world
 * concurrently exercises the Mine support prefab, native block-physics deco marking and a
 * native block break at 4x. The benchmark therefore measures time dilation while also proving
 * that two different Civ runtime scenarios can execute in isolated worlds in one warm server.</p>
 */
final class CivWarmRuntimeBenchmarkCommand extends CommandBase {

    private static final String FLAT_GENERATOR = "Flat";
    private static final String DEFAULT_STORAGE = "default";
    private static final String ROLE = "Civ_Inhabitant";
    private static final String WORLD_1X = "civ-warm-benchmark-1x";
    private static final String WORLD_4X = "civ-warm-benchmark-4x";
    private static final String WORLD_SUPPORT = "civ-warm-benchmark-support";
    private static final String SUPPORT_PREFAB_KEY = "Civilizations/Mine/Mine_Support_01.prefab.json";
    private static final String SUPPORT_BEAM_BLOCK = "Wood_Fir_Trunk";
    private static final int SUPPORT_DEPTH = 4;

    private static final WorldPosition TARGET = new WorldPosition(18.5, 1.0, 8.5);
    private static final Vector3d START = new Vector3d(4.5, 1.0, 8.5);
    private static final long SETTLE_MILLIS = 500L;
    private static final long POLL_MILLIS = 50L;
    private static final long TIMEOUT_MILLIS = 15_000L;
    private static final long SUPPORT_STABILITY_MILLIS = 2_000L;
    private static final long SUPPORT_BREAK_MILLIS = 500L;
    private static final double MINIMUM_MOVED_DISTANCE = 8.0;
    private static final double MAXIMUM_TARGET_DISTANCE = 1.5;

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;

    CivWarmRuntimeBenchmarkCommand(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry
    ) {
        super("civwarmruntimebenchmark", "Benchmarks warm parallel Hytale runtime worlds.");
        requireNoPermission();
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
    }

    @Override
    protected void executeSync(CommandContext context) {
        Universe universe = Universe.get();
        if (worldExists(universe, WORLD_1X)
            || worldExists(universe, WORLD_4X)
            || worldExists(universe, WORLD_SUPPORT)) {
            fail("benchmark world already exists or is loadable", null);
            return;
        }

        long benchmarkStartNanos = System.nanoTime();
        Coordinator coordinator = new Coordinator(universe, benchmarkStartNanos);
        System.out.println("CIV_WARM_RUNTIME_BENCHMARK_STARTED worlds=3 dilation=1x,4x,4x-support");

        CompletableFuture<World> oneXFuture = universe.addWorld(WORLD_1X, FLAT_GENERATOR, DEFAULT_STORAGE);
        CompletableFuture<World> fourXFuture = universe.addWorld(WORLD_4X, FLAT_GENERATOR, DEFAULT_STORAGE);
        CompletableFuture<World> supportFuture = universe.addWorld(WORLD_SUPPORT, FLAT_GENERATOR, DEFAULT_STORAGE);

        CompletableFuture.allOf(oneXFuture, fourXFuture, supportFuture).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                coordinator.fail("parallel benchmark worlds could not be created", throwable);
                return;
            }

            World oneX = oneXFuture.join();
            World fourX = fourXFuture.join();
            World support = supportFuture.join();
            if (oneX == null || fourX == null || support == null) {
                coordinator.fail("parallel benchmark world creation returned null", null);
                return;
            }

            coordinator.worldsCreatedNanos = System.nanoTime();
            // The entire isolated runtime directory is deleted after process exit. Avoid asking
            // Hytale to move world folders while their tick threads are still winding down on Windows.
            oneX.getWorldConfig().setDeleteOnRemove(false);
            fourX.getWorldConfig().setDeleteOnRemove(false);
            support.getWorldConfig().setDeleteOnRemove(false);

            oneX.execute(() -> prepareMovementWorld(oneX, 1.0f, coordinator));
            fourX.execute(() -> prepareMovementWorld(fourX, 4.0f, coordinator));
            support.execute(() -> prepareSupportWorld(support, coordinator));
        });
    }

    private static boolean worldExists(Universe universe, String worldName) {
        return universe.getWorld(worldName) != null || universe.isWorldLoadable(worldName);
    }

    private void prepareMovementWorld(World world, float dilation, Coordinator coordinator) {
        try {
            World.setTimeDilation(dilation, world.getEntityStore().getStore());
            long chunkIndex = ChunkUtil.indexChunkFromBlock(START.x, START.z);
            world.getChunkAsync(chunkIndex).whenComplete((chunk, throwable) ->
                world.execute(() -> {
                    if (throwable != null || chunk == null) {
                        coordinator.fail("benchmark chunk could not be loaded in " + world.getName(), throwable);
                        return;
                    }
                    world.scheduleAfter(
                        () -> startMovement(world, dilation, coordinator),
                        SETTLE_MILLIS,
                        TimeUnit.MILLISECONDS
                    );
                })
            );
        } catch (Throwable throwable) {
            coordinator.fail("benchmark world setup failed in " + world.getName(), throwable);
        }
    }

    private void startMovement(World world, float dilation, Coordinator coordinator) {
        try {
            var spawned = NPCPlugin.get().spawnNPC(
                world.getEntityStore().getStore(),
                ROLE,
                null,
                new Vector3d(START),
                new Rotation3f()
            );
            if (spawned == null || spawned.first() == null || !spawned.first().isValid()) {
                coordinator.fail("Hytale could not spawn benchmark Civ inhabitant in " + world.getName(), null);
                return;
            }

            Ref<EntityStore> ref = spawned.first();
            if (!unitRegistry.toggleClaim(ref) || !unitRegistry.isClaimed(ref)) {
                coordinator.fail("benchmark NPC could not be claimed in " + world.getName(), null);
                return;
            }
            CivInhabitantData data = unitRegistry.getInhabitantData(ref);
            if (data == null || !data.hasIdentity()) {
                coordinator.fail("benchmark NPC identity was not initialized in " + world.getName(), null);
                return;
            }

            TransformComponent transform = ref.getStore()
                .getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                coordinator.fail("benchmark NPC has no TransformComponent in " + world.getName(), null);
                return;
            }

            Vector3d actualStart = new Vector3d(transform.getPosition());
            if (!activityRegistry.orderManualMove(ref, TARGET)) {
                coordinator.fail("benchmark movement order was rejected in " + world.getName(), null);
                return;
            }

            long startNanos = System.nanoTime();
            long startTick = world.getTick();
            Probe probe = new Probe(
                world,
                dilation,
                ref,
                actualStart,
                startNanos,
                startTick,
                startNanos + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS)
            );
            System.out.println(
                "CIV_WARM_RUNTIME_WORLD_STARTED world=" + world.getName()
                    + " dilation=" + dilation
                    + " tick=" + startTick
            );

            world.scheduleAfter(
                () -> pollMovement(probe, coordinator),
                POLL_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            coordinator.fail("benchmark movement setup failed in " + world.getName(), throwable);
        }
    }

    private void pollMovement(Probe probe, Coordinator coordinator) {
        try {
            if (!probe.ref.isValid()) {
                coordinator.fail("benchmark NPC became invalid in " + probe.world.getName(), null);
                return;
            }

            TransformComponent transform = probe.ref.getStore()
                .getComponent(probe.ref, TransformComponent.getComponentType());
            if (transform == null) {
                coordinator.fail("benchmark NPC lost TransformComponent in " + probe.world.getName(), null);
                return;
            }

            Vector3d current = new Vector3d(transform.getPosition());
            boolean complete = activityRegistry.manualMovementIntent(probe.ref) == null
                && unitRegistry.getMoveTarget(probe.ref) == null;
            if (complete) {
                double moved = horizontalDistance(probe.startPosition, current);
                double targetDistance = horizontalDistance(current, TARGET);
                if (moved < MINIMUM_MOVED_DISTANCE) {
                    coordinator.fail(
                        "benchmark movement completed without meaningful displacement in "
                            + probe.world.getName() + ": " + moved,
                        null
                    );
                    return;
                }
                if (targetDistance > MAXIMUM_TARGET_DISTANCE) {
                    coordinator.fail(
                        "benchmark movement completed too far from target in "
                            + probe.world.getName() + ": " + targetDistance,
                        null
                    );
                    return;
                }

                long endNanos = System.nanoTime();
                Result result = new Result(
                    probe.world.getName(),
                    probe.dilation,
                    probe.startNanos,
                    endNanos,
                    TimeUnit.NANOSECONDS.toMillis(endNanos - probe.startNanos),
                    probe.world.getTick() - probe.startTick,
                    moved,
                    targetDistance
                );
                System.out.println(
                    "CIV_WARM_RUNTIME_WORLD_PASS world=" + result.worldName
                        + " dilation=" + result.dilation
                        + " elapsedMs=" + result.elapsedMillis
                        + " ticks=" + result.ticksElapsed
                        + " moved=" + String.format("%.2f", result.movedDistance)
                        + " targetDistance=" + String.format("%.2f", result.targetDistance)
                );
                coordinator.movementCompleted(result);
                return;
            }

            if (System.nanoTime() >= probe.deadlineNanos) {
                coordinator.fail(
                    "benchmark movement timed out in " + probe.world.getName()
                        + " after ticks=" + (probe.world.getTick() - probe.startTick),
                    null
                );
                return;
            }

            probe.world.scheduleAfter(
                () -> pollMovement(probe, coordinator),
                POLL_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            coordinator.fail("benchmark assertion failed in " + probe.world.getName(), throwable);
        }
    }

    private void prepareSupportWorld(World world, Coordinator coordinator) {
        try {
            World.setTimeDilation(4.0f, world.getEntityStore().getStore());
            MineSegment segment = MineSegment.reserved(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                new BlockPosition(8, 1, 8),
                MineDirection.EAST
            );
            List<BlockPosition> beam = MineSupportPhysics.beamBlocks(segment, SUPPORT_DEPTH);
            CompletableFuture<?>[] loads = beam.stream()
                .map(block -> world.getChunkAsync(ChunkUtil.indexChunkFromBlock(block.x(), block.z())))
                .distinct()
                .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(loads).whenComplete((ignored, throwable) -> world.execute(() -> {
                if (throwable != null) {
                    coordinator.fail("support benchmark chunk could not be loaded", throwable);
                    return;
                }
                placeSupport(world, segment, coordinator);
            }));
        } catch (Throwable throwable) {
            coordinator.fail("support benchmark setup failed", throwable);
        }
    }

    private void placeSupport(World world, MineSegment segment, Coordinator coordinator) {
        try {
            long startNanos = System.nanoTime();
            BlockSelection raw = PrefabStore.get().getAssetPrefabFromAnyPack(SUPPORT_PREFAB_KEY);
            if (raw == null) {
                coordinator.fail("support benchmark prefab is missing", null);
                return;
            }
            BlockSelection selection = new BlockSelection(raw);
            BlockSelectionSupportUtil.applySupportValues(selection);
            BlockPosition originBlock = segment.supportOrigin(SUPPORT_DEPTH);
            selection.placeNoReturn(
                world,
                new Vector3i(originBlock.x(), originBlock.y(), originBlock.z()),
                world.getEntityStore().getStore()
            );
            if (!MineSupportPhysics.markBeamAsDeco(world, segment, SUPPORT_DEPTH)
                || !MineSupportPhysics.beamIsDeco(world, segment, SUPPORT_DEPTH)) {
                coordinator.fail("support benchmark beam could not be marked as deco", null);
                return;
            }
            System.out.println(
                "CIV_WARM_RUNTIME_SUPPORT_STARTED world=" + world.getName()
                    + " dilation=4.0 beamCells=4"
            );
            world.scheduleAfter(
                () -> verifySupportStableAndBreak(world, segment, startNanos, coordinator),
                SUPPORT_STABILITY_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            coordinator.fail("support benchmark placement failed", throwable);
        }
    }

    private void verifySupportStableAndBreak(
        World world,
        MineSegment segment,
        long startNanos,
        Coordinator coordinator
    ) {
        try {
            List<BlockPosition> beam = MineSupportPhysics.beamBlocks(segment, SUPPORT_DEPTH);
            for (BlockPosition block : beam) {
                BlockType type = loadedBlockType(world, block);
                if (type == null || !SUPPORT_BEAM_BLOCK.equalsIgnoreCase(type.getId())) {
                    coordinator.fail("support benchmark beam was unstable at " + block, null);
                    return;
                }
            }
            if (!MineSupportPhysics.beamIsDeco(world, segment, SUPPORT_DEPTH)) {
                coordinator.fail("support benchmark beam lost deco metadata", null);
                return;
            }

            BlockPosition target = beam.getFirst();
            var spawned = NPCPlugin.get().spawnNPC(
                world.getEntityStore().getStore(),
                ROLE,
                null,
                new Vector3d(target.x() - 2.0, target.y() - 2.0, target.z() + 0.5),
                new Rotation3f()
            );
            if (spawned == null || spawned.first() == null || !spawned.first().isValid()) {
                coordinator.fail("support benchmark could not spawn breaker NPC", null);
                return;
            }
            Ref<EntityStore> breaker = spawned.first();
            BlockHarvestUtils.performBlockBreak(
                breaker,
                null,
                List.of(new Vector3i(target.x(), target.y(), target.z())),
                0,
                world.getEntityStore().getStore(),
                world.getChunkStore().getStore()
            );
            world.scheduleAfter(
                () -> verifySupportBreak(world, segment, target, startNanos, coordinator),
                SUPPORT_BREAK_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            coordinator.fail("support benchmark stability check failed", throwable);
        }
    }

    private void verifySupportBreak(
        World world,
        MineSegment segment,
        BlockPosition removed,
        long startNanos,
        Coordinator coordinator
    ) {
        try {
            BlockType removedType = loadedBlockType(world, removed);
            if (removedType != null && removedType != BlockType.EMPTY) {
                coordinator.fail("support benchmark deco beam block remained after native break", null);
                return;
            }

            int remaining = 0;
            for (BlockPosition block : MineSupportPhysics.beamBlocks(segment, SUPPORT_DEPTH)) {
                if (block.equals(removed)) {
                    continue;
                }
                BlockType type = loadedBlockType(world, block);
                if (type != null && SUPPORT_BEAM_BLOCK.equalsIgnoreCase(type.getId())) {
                    remaining++;
                }
            }
            if (remaining != 3) {
                coordinator.fail("support benchmark native break cascaded; remaining=" + remaining, null);
                return;
            }

            long endNanos = System.nanoTime();
            SupportResult result = new SupportResult(
                startNanos,
                endNanos,
                TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos)
            );
            System.out.println(
                "CIV_WARM_RUNTIME_SUPPORT_PASS world=" + world.getName()
                    + " dilation=4.0 elapsedMs=" + result.elapsedMillis
                    + " remainingBeam=" + remaining
            );
            coordinator.supportCompleted(result);
        } catch (Throwable throwable) {
            coordinator.fail("support benchmark breakability check failed", throwable);
        }
    }

    private static BlockType loadedBlockType(World world, BlockPosition block) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(block.x(), block.z()));
        return chunk == null ? null : chunk.getBlockType(block.x(), block.y(), block.z());
    }

    private static double horizontalDistance(Vector3d first, Vector3d second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double horizontalDistance(Vector3d first, WorldPosition second) {
        double dx = first.x - second.x();
        double dz = first.z - second.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void fail(String reason, Throwable throwable) {
        System.out.println("CIV_WARM_RUNTIME_BENCHMARK_FAIL " + reason);
        if (throwable != null) {
            throwable.printStackTrace(System.out);
        }
        HytaleServer.get().shutdownServer();
    }

    private final class Coordinator {
        private final Universe universe;
        private final long benchmarkStartNanos;
        private final Map<String, Result> results = new ConcurrentHashMap<>();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private volatile long worldsCreatedNanos;
        private volatile SupportResult supportResult;

        private Coordinator(Universe universe, long benchmarkStartNanos) {
            this.universe = universe;
            this.benchmarkStartNanos = benchmarkStartNanos;
        }

        private void movementCompleted(Result result) {
            if (terminal.get()) {
                return;
            }
            results.put(result.worldName, result);
            maybeFinish();
        }

        private void supportCompleted(SupportResult result) {
            if (terminal.get()) {
                return;
            }
            supportResult = result;
            maybeFinish();
        }

        private void maybeFinish() {
            Result oneX = results.get(WORLD_1X);
            Result fourX = results.get(WORLD_4X);
            SupportResult support = supportResult;
            if (oneX == null || fourX == null || support == null) {
                return;
            }
            if (!terminal.compareAndSet(false, true)) {
                return;
            }

            long movementOverlapNanos = Math.min(oneX.endNanos, fourX.endNanos)
                - Math.max(oneX.startNanos, fourX.startNanos);
            if (movementOverlapNanos <= 0L) {
                CivWarmRuntimeBenchmarkCommand.fail(
                    "benchmark movement world execution windows did not overlap",
                    null
                );
                return;
            }
            long distinctScenarioOverlapNanos = Math.min(fourX.endNanos, support.endNanos)
                - Math.max(fourX.startNanos, support.startNanos);
            if (distinctScenarioOverlapNanos <= 0L) {
                CivWarmRuntimeBenchmarkCommand.fail(
                    "movement and support scenario execution windows did not overlap",
                    null
                );
                return;
            }

            double speedup = fourX.elapsedMillis == 0L
                ? Double.POSITIVE_INFINITY
                : (double) oneX.elapsedMillis / (double) fourX.elapsedMillis;
            long totalMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - benchmarkStartNanos);
            long creationMillis = TimeUnit.NANOSECONDS.toMillis(worldsCreatedNanos - benchmarkStartNanos);

            System.out.println(
                "CIV_WARM_RUNTIME_BENCHMARK_RESULT"
                    + " worldCreateMs=" + creationMillis
                    + " oneXMs=" + oneX.elapsedMillis
                    + " fourXMs=" + fourX.elapsedMillis
                    + " support4XMs=" + support.elapsedMillis
                    + " speedup=" + String.format("%.2f", speedup)
                    + " movementOverlapMs=" + TimeUnit.NANOSECONDS.toMillis(movementOverlapNanos)
                    + " distinctScenarioOverlapMs=" + TimeUnit.NANOSECONDS.toMillis(distinctScenarioOverlapNanos)
                    + " totalMs=" + totalMillis
            );

            World defaultWorld = universe.getDefaultWorld();
            if (defaultWorld == null) {
                CivWarmRuntimeBenchmarkCommand.fail("default world unavailable for benchmark cleanup", null);
                return;
            }
            defaultWorld.execute(this::cleanupAndShutdown);
        }

        private void cleanupAndShutdown() {
            try {
                long cleanupStartNanos = System.nanoTime();
                boolean removedOneX = universe.removeWorld(WORLD_1X);
                boolean removedFourX = universe.removeWorld(WORLD_4X);
                boolean removedSupport = universe.removeWorld(WORLD_SUPPORT);
                boolean detached = universe.getWorld(WORLD_1X) == null
                    && universe.getWorld(WORLD_4X) == null
                    && universe.getWorld(WORLD_SUPPORT) == null;
                long cleanupMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cleanupStartNanos);
                System.out.println(
                    "CIV_WARM_RUNTIME_WORLDS_REMOVED oneX=" + removedOneX
                        + " fourX=" + removedFourX
                        + " support=" + removedSupport
                        + " detached=" + detached
                        + " cleanupMs=" + cleanupMillis
                );
                if (!removedOneX || !removedFourX || !removedSupport || !detached) {
                    CivWarmRuntimeBenchmarkCommand.fail(
                        "benchmark worlds could not all be detached cleanly",
                        null
                    );
                    return;
                }
            } catch (Throwable throwable) {
                CivWarmRuntimeBenchmarkCommand.fail("benchmark world cleanup failed", throwable);
                return;
            }

            System.out.println("CIV_WARM_RUNTIME_BENCHMARK_PASS");
            HytaleServer.get().shutdownServer();
        }

        private void fail(String reason, Throwable throwable) {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            CivWarmRuntimeBenchmarkCommand.fail(reason, throwable);
        }
    }

    private record Probe(
        World world,
        float dilation,
        Ref<EntityStore> ref,
        Vector3d startPosition,
        long startNanos,
        long startTick,
        long deadlineNanos
    ) {
    }

    private record Result(
        String worldName,
        float dilation,
        long startNanos,
        long endNanos,
        long elapsedMillis,
        long ticksElapsed,
        double movedDistance,
        double targetDistance
    ) {
    }

    private record SupportResult(
        long startNanos,
        long endNanos,
        long elapsedMillis
    ) {
    }
}
