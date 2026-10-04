package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivInhabitantData;
import dev.civilizations.hytale.CivUnitRegistry;
import org.joml.Vector3d;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime-only benchmark for warm-server Hytale test execution.
 *
 * <p>The benchmark creates two independent native Flat worlds in one Hytale process. Each
 * world spawns a real Civ inhabitant and runs the normal manual-movement adapter. One world
 * runs at 1x world time and one at 4x via Hytale's native time-dilation API. The benchmark
 * records wall-clock completion time and verifies that both worlds made real progress while
 * their execution windows overlapped.</p>
 */
final class CivWarmRuntimeBenchmarkCommand extends CommandBase {

    private static final String FLAT_GENERATOR = "Flat";
    private static final String DEFAULT_STORAGE = "default";
    private static final String ROLE = "Civ_Inhabitant";
    private static final String WORLD_1X = "civ-warm-benchmark-1x";
    private static final String WORLD_4X = "civ-warm-benchmark-4x";

    private static final WorldPosition TARGET = new WorldPosition(18.5, 1.0, 8.5);
    private static final Vector3d START = new Vector3d(4.5, 1.0, 8.5);
    private static final long SETTLE_MILLIS = 500L;
    private static final long POLL_MILLIS = 50L;
    private static final long TIMEOUT_MILLIS = 15_000L;
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
        if (worldExists(universe, WORLD_1X) || worldExists(universe, WORLD_4X)) {
            fail("benchmark world already exists or is loadable", null);
            return;
        }

        long benchmarkStartNanos = System.nanoTime();
        Coordinator coordinator = new Coordinator(universe, benchmarkStartNanos);
        System.out.println("CIV_WARM_RUNTIME_BENCHMARK_STARTED worlds=2 dilation=1x,4x");

        CompletableFuture<World> oneXFuture = universe.addWorld(WORLD_1X, FLAT_GENERATOR, DEFAULT_STORAGE);
        CompletableFuture<World> fourXFuture = universe.addWorld(WORLD_4X, FLAT_GENERATOR, DEFAULT_STORAGE);

        CompletableFuture.allOf(oneXFuture, fourXFuture).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                coordinator.fail("parallel benchmark worlds could not be created", throwable);
                return;
            }

            World oneX = oneXFuture.join();
            World fourX = fourXFuture.join();
            if (oneX == null || fourX == null) {
                coordinator.fail("parallel benchmark world creation returned null", null);
                return;
            }

            coordinator.worldsCreatedNanos = System.nanoTime();
            oneX.getWorldConfig().setDeleteOnRemove(true);
            fourX.getWorldConfig().setDeleteOnRemove(true);

            oneX.execute(() -> prepareWorld(oneX, 1.0f, coordinator));
            fourX.execute(() -> prepareWorld(fourX, 4.0f, coordinator));
        });
    }

    private static boolean worldExists(Universe universe, String worldName) {
        return universe.getWorld(worldName) != null || universe.isWorldLoadable(worldName);
    }

    private void prepareWorld(World world, float dilation, Coordinator coordinator) {
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
                coordinator.completed(result);
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

        private Coordinator(Universe universe, long benchmarkStartNanos) {
            this.universe = universe;
            this.benchmarkStartNanos = benchmarkStartNanos;
        }

        private void completed(Result result) {
            if (terminal.get()) {
                return;
            }
            results.put(result.worldName, result);
            if (results.size() != 2 || !terminal.compareAndSet(false, true)) {
                return;
            }

            Result oneX = results.get(WORLD_1X);
            Result fourX = results.get(WORLD_4X);
            if (oneX == null || fourX == null) {
                CivWarmRuntimeBenchmarkCommand.fail("benchmark results did not contain both worlds", null);
                return;
            }

            long overlapNanos = Math.min(oneX.endNanos, fourX.endNanos)
                - Math.max(oneX.startNanos, fourX.startNanos);
            if (overlapNanos <= 0L) {
                CivWarmRuntimeBenchmarkCommand.fail(
                    "benchmark world execution windows did not overlap",
                    null
                );
                return;
            }

            double speedup = fourX.elapsedMillis == 0L
                ? Double.POSITIVE_INFINITY
                : (double) oneX.elapsedMillis / (double) fourX.elapsedMillis;
            long totalMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - benchmarkStartNanos);
            long creationMillis = TimeUnit.NANOSECONDS.toMillis(worldsCreatedNanos - benchmarkStartNanos);
            long overlapMillis = TimeUnit.NANOSECONDS.toMillis(overlapNanos);

            System.out.println(
                "CIV_WARM_RUNTIME_BENCHMARK_RESULT"
                    + " worldCreateMs=" + creationMillis
                    + " oneXMs=" + oneX.elapsedMillis
                    + " fourXMs=" + fourX.elapsedMillis
                    + " speedup=" + String.format("%.2f", speedup)
                    + " overlapMs=" + overlapMillis
                    + " totalMs=" + totalMillis
            );

            try {
                boolean removedOneX = universe.removeWorld(WORLD_1X);
                boolean removedFourX = universe.removeWorld(WORLD_4X);
                System.out.println(
                    "CIV_WARM_RUNTIME_WORLDS_REMOVED oneX=" + removedOneX
                        + " fourX=" + removedFourX
                );
                if (!removedOneX || !removedFourX) {
                    CivWarmRuntimeBenchmarkCommand.fail(
                        "benchmark worlds could not both be removed",
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
}
