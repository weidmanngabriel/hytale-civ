package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivInhabitantData;
import dev.civilizations.hytale.CivUnitRegistry;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Headless-only integration probe. Registered only when civilizations.runtimeProbe=true.
 *
 * <p>The probe builds a tiny deterministic arena inside an already loaded real Hytale chunk.
 * That keeps the test focused on the Civ -> Hytale NPC movement contract instead of random
 * world-generation terrain around the default spawn.</p>
 */
final class CivRuntimeProbeCommand extends CommandBase {

    private static final String ROLE = "Civ_Inhabitant";
    private static final long ARENA_SETTLE_MILLIS = 500L;
    private static final long ASSERT_INTERVAL_MILLIS = 250L;
    private static final long PROBE_TIMEOUT_MILLIS = 10_000L;
    private static final long MINIMUM_WORLD_TICKS = 2L;
    private static final double MINIMUM_MOVED_DISTANCE = 2.0;
    private static final double MAXIMUM_TARGET_DISTANCE = 1.25;

    private static final int ARENA_FLOOR_Y = 200;
    private static final int ARENA_MIN_LOCAL_X = 8;
    private static final int ARENA_MAX_LOCAL_X = 23;
    private static final int ARENA_MIN_LOCAL_Z = 13;
    private static final int ARENA_MAX_LOCAL_Z = 19;
    private static final int ARENA_CLEARANCE_BLOCKS = 4;
    private static final int START_LOCAL_X = 12;
    private static final int START_LOCAL_Z = 16;
    private static final int TARGET_LOCAL_X = 18;
    private static final int TARGET_LOCAL_Z = 16;

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;

    CivRuntimeProbeCommand(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry
    ) {
        super("civruntimeprobe", "Runs the headless Civ gameplay runtime probe.");
        requireNoPermission();
        this.unitRegistry = unitRegistry;
        this.activityRegistry = activityRegistry;
    }

    @Override
    protected void executeSync(CommandContext context) {
        World world = Universe.get().getDefaultWorld();
        if (world == null) {
            fail("default world is unavailable", null);
            return;
        }

        world.execute(() -> loadArenaChunkAndStart(world));
    }

    private void loadArenaChunkAndStart(World world) {
        try {
            var spawn = world.getWorldConfig()
                .getSpawnProvider()
                .getSpawnPoint(world, UUID.randomUUID());
            if (spawn == null) {
                fail("world spawn provider returned no spawn point", null);
                return;
            }

            Vector3d spawnPosition = spawn.getPosition();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(spawnPosition.x, spawnPosition.z);
            world.getChunkAsync(chunkIndex).whenComplete((chunk, throwable) ->
                world.execute(() -> {
                    if (throwable != null || chunk == null) {
                        fail("arena chunk could not be loaded", throwable);
                        return;
                    }
                    prepareArenaAndScheduleProbe(world, chunk, spawn.getRotation());
                })
            );
        } catch (Throwable throwable) {
            fail("arena chunk setup threw an exception", throwable);
        }
    }

    private void prepareArenaAndScheduleProbe(
        World world,
        WorldChunk chunk,
        Rotation3f spawnRotation
    ) {
        try {
            if (!buildArena(chunk)) {
                fail("deterministic movement arena could not be verified after block updates", null);
                return;
            }

            int chunkMinX = ChunkUtil.minBlock(chunk.getX());
            int chunkMinZ = ChunkUtil.minBlock(chunk.getZ());
            Vector3d startPosition = new Vector3d(
                chunkMinX + START_LOCAL_X + 0.5,
                ARENA_FLOOR_Y + 1.0,
                chunkMinZ + START_LOCAL_Z + 0.5
            );
            WorldPosition destination = new WorldPosition(
                chunkMinX + TARGET_LOCAL_X + 0.5,
                ARENA_FLOOR_Y + 1.0,
                chunkMinZ + TARGET_LOCAL_Z + 0.5
            );

            System.out.println(
                "CIV_RUNTIME_ARENA_READY floor=" + BlockType.DEBUG_CUBE.getId()
                    + " start=" + format(startPosition)
                    + " target=" + format(destination)
            );

            world.scheduleAfter(
                () -> startProbe(world, startPosition, spawnRotation, destination),
                ARENA_SETTLE_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("arena preparation threw an exception", throwable);
        }
    }

    private static boolean buildArena(WorldChunk chunk) {
        for (int localX = ARENA_MIN_LOCAL_X; localX <= ARENA_MAX_LOCAL_X; localX++) {
            for (int localZ = ARENA_MIN_LOCAL_Z; localZ <= ARENA_MAX_LOCAL_Z; localZ++) {
                // setBlock returns whether the block changed, not whether the resulting state is valid.
                // The arena may already contain empty cells, so always verify the final state below.
                chunk.setBlock(localX, ARENA_FLOOR_Y, localZ, BlockType.DEBUG_CUBE);
                for (int dy = 1; dy <= ARENA_CLEARANCE_BLOCKS; dy++) {
                    chunk.setBlock(localX, ARENA_FLOOR_Y + dy, localZ, BlockType.EMPTY);
                }
            }
        }

        for (int localX = ARENA_MIN_LOCAL_X; localX <= ARENA_MAX_LOCAL_X; localX++) {
            for (int localZ = ARENA_MIN_LOCAL_Z; localZ <= ARENA_MAX_LOCAL_Z; localZ++) {
                if (chunk.getBlock(localX, ARENA_FLOOR_Y, localZ) != BlockType.DEBUG_CUBE_ID) {
                    return false;
                }
                for (int dy = 1; dy <= ARENA_CLEARANCE_BLOCKS; dy++) {
                    if (chunk.getBlock(localX, ARENA_FLOOR_Y + dy, localZ) != BlockType.EMPTY_ID) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void startProbe(
        World world,
        Vector3d requestedStartPosition,
        Rotation3f spawnRotation,
        WorldPosition destination
    ) {
        try {
            var spawned = NPCPlugin.get().spawnNPC(
                world.getEntityStore().getStore(),
                ROLE,
                null,
                requestedStartPosition,
                spawnRotation
            );
            if (spawned == null || spawned.first() == null) {
                fail("Hytale could not spawn Civ_Inhabitant", null);
                return;
            }

            Ref<EntityStore> ref = spawned.first();
            if (!ref.isValid()) {
                fail("spawned Civ_Inhabitant ref is invalid", null);
                return;
            }

            if (!unitRegistry.toggleClaim(ref) || !unitRegistry.isClaimed(ref)) {
                fail("spawned NPC could not be claimed as a Civ inhabitant", null);
                return;
            }

            CivInhabitantData data = unitRegistry.getInhabitantData(ref);
            if (data == null || !data.hasIdentity()) {
                fail("Civ inhabitant persistent identity was not initialized", null);
                return;
            }

            TransformComponent transform = ref.getStore()
                .getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                fail("spawned Civ inhabitant has no TransformComponent", null);
                return;
            }

            Vector3d startPosition = new Vector3d(transform.getPosition());
            if (horizontalDistance(startPosition, requestedStartPosition) > 0.75
                || Math.abs(startPosition.y - requestedStartPosition.y) > 1.0) {
                fail("NPC spawned outside the deterministic arena start position: " + format(startPosition), null);
                return;
            }

            if (!activityRegistry.orderManualMove(ref, destination)) {
                fail("Civ manual movement order was rejected", null);
                return;
            }
            if (activityRegistry.manualMovementIntent(ref) == null) {
                fail("manual movement intent was not created", null);
                return;
            }

            long startTick = world.getTick();
            long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PROBE_TIMEOUT_MILLIS);
            System.out.println(
                "CIV_RUNTIME_PROBE_STARTED tick=" + startTick
                    + " entity=" + ref.getIndex()
                    + " start=" + format(startPosition)
                    + " target=" + format(destination)
            );

            world.scheduleAfter(
                () -> assertProbe(world, ref, startTick, startPosition, destination, deadlineNanos),
                ASSERT_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("probe setup threw an exception", throwable);
        }
    }

    private void assertProbe(
        World world,
        Ref<EntityStore> ref,
        long startTick,
        Vector3d startPosition,
        WorldPosition destination,
        long deadlineNanos
    ) {
        try {
            long ticksElapsed = world.getTick() - startTick;
            if (!ref.isValid()) {
                fail("Civ inhabitant became invalid before assertion", null);
                return;
            }
            if (!unitRegistry.isClaimed(ref)) {
                fail("Civ inhabitant lost its claimed state", null);
                return;
            }

            TransformComponent transform = ref.getStore()
                .getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                fail("Civ inhabitant lost its TransformComponent", null);
                return;
            }

            Vector3d currentPosition = new Vector3d(transform.getPosition());
            double movedDistance = horizontalDistance(startPosition, currentPosition);
            double targetDistance = horizontalDistance(currentPosition, destination);
            boolean activityComplete = activityRegistry.manualMovementIntent(ref) == null;
            boolean nativeTargetCleared = unitRegistry.getMoveTarget(ref) == null;

            if (activityComplete && nativeTargetCleared) {
                if (ticksElapsed < MINIMUM_WORLD_TICKS) {
                    fail("fewer than two real world ticks elapsed: " + ticksElapsed, null);
                    return;
                }
                if (movedDistance < MINIMUM_MOVED_DISTANCE) {
                    fail("NPC movement completed without real displacement: " + movedDistance, null);
                    return;
                }
                if (targetDistance > MAXIMUM_TARGET_DISTANCE) {
                    fail("NPC movement completed too far from target: " + targetDistance, null);
                    return;
                }

                System.out.println(
                    "CIV_RUNTIME_PROBE_PASS ticks=" + ticksElapsed
                        + " entity=" + ref.getIndex()
                        + " moved=" + String.format("%.2f", movedDistance)
                        + " targetDistance=" + String.format("%.2f", targetDistance)
                        + " end=" + format(currentPosition)
                );
                HytaleServer.get().shutdownServer();
                return;
            }

            if (System.nanoTime() >= deadlineNanos) {
                fail(
                    "movement timed out after " + ticksElapsed + " ticks"
                        + ", moved=" + String.format("%.2f", movedDistance)
                        + ", targetDistance=" + String.format("%.2f", targetDistance)
                        + ", activityComplete=" + activityComplete
                        + ", nativeTargetCleared=" + nativeTargetCleared
                        + ", end=" + format(currentPosition),
                    null
                );
                return;
            }

            world.scheduleAfter(
                () -> assertProbe(world, ref, startTick, startPosition, destination, deadlineNanos),
                ASSERT_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("probe assertion threw an exception", throwable);
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

    private static String format(Vector3d position) {
        return String.format("(%.2f,%.2f,%.2f)", position.x, position.y, position.z);
    }

    private static String format(WorldPosition position) {
        return String.format("(%.2f,%.2f,%.2f)", position.x(), position.y(), position.z());
    }

    private static void fail(String reason, Throwable throwable) {
        System.out.println("CIV_RUNTIME_PROBE_FAIL " + reason);
        if (throwable != null) {
            throwable.printStackTrace(System.out);
        }
        HytaleServer.get().shutdownServer();
    }
}
