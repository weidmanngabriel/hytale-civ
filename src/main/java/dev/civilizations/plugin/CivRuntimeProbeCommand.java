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
 */
final class CivRuntimeProbeCommand extends CommandBase {

    private static final String ROLE = "Civ_Inhabitant";
    private static final long ASSERT_INTERVAL_MILLIS = 250L;
    private static final long PROBE_TIMEOUT_MILLIS = 10_000L;
    private static final long MINIMUM_WORLD_TICKS = 2L;
    private static final double MINIMUM_MOVED_DISTANCE = 2.0;
    private static final double MAXIMUM_TARGET_DISTANCE = 1.25;
    private static final int MAXIMUM_TARGET_DISTANCE_BLOCKS = 5;
    private static final int MINIMUM_TARGET_DISTANCE_BLOCKS = 3;

    private static final int[][] CARDINAL_DIRECTIONS = {
        {1, 0},
        {-1, 0},
        {0, 1},
        {0, -1}
    };

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

        world.execute(() -> loadSpawnChunkAndStart(world));
    }

    private void loadSpawnChunkAndStart(World world) {
        try {
            var spawn = world.getWorldConfig()
                .getSpawnProvider()
                .getSpawnPoint(world, UUID.randomUUID());
            if (spawn == null) {
                fail("world spawn provider returned no spawn point", null);
                return;
            }

            Vector3d position = spawn.getPosition();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(position.x, position.z);
            world.getChunkAsync(chunkIndex).whenComplete((chunk, throwable) ->
                world.execute(() -> {
                    if (throwable != null || chunk == null) {
                        fail("spawn chunk could not be loaded", throwable);
                        return;
                    }
                    startProbe(world, chunk, spawn.getPosition(), spawn.getRotation());
                })
            );
        } catch (Throwable throwable) {
            fail("spawn chunk setup threw an exception", throwable);
        }
    }

    private void startProbe(
        World world,
        WorldChunk spawnChunk,
        Vector3d spawnPosition,
        Rotation3f spawnRotation
    ) {
        try {
            var spawned = NPCPlugin.get().spawnNPC(
                world.getEntityStore().getStore(),
                ROLE,
                null,
                spawnPosition,
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
            WorldPosition destination = findNearbyWalkableDestination(spawnChunk, startPosition);
            if (destination == null) {
                fail("NO_SAFE_TARGET: no flat same-chunk corridor with solid floor and clear headroom", null);
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

    private static WorldPosition findNearbyWalkableDestination(
        WorldChunk chunk,
        Vector3d startPosition
    ) {
        int startBlockX = (int) Math.floor(startPosition.x);
        int startBlockZ = (int) Math.floor(startPosition.z);
        int feetY = (int) Math.floor(startPosition.y + 0.01);
        long startChunkIndex = chunk.getIndex();

        if (!isWalkableColumn(chunk, startBlockX, feetY, startBlockZ)) {
            return null;
        }

        for (int distance = MAXIMUM_TARGET_DISTANCE_BLOCKS;
             distance >= MINIMUM_TARGET_DISTANCE_BLOCKS;
             distance--) {
            for (int[] direction : CARDINAL_DIRECTIONS) {
                if (!isStraightWalkableCorridor(
                    chunk,
                    startChunkIndex,
                    startBlockX,
                    feetY,
                    startBlockZ,
                    direction[0],
                    direction[1],
                    distance
                )) {
                    continue;
                }

                int targetBlockX = startBlockX + direction[0] * distance;
                int targetBlockZ = startBlockZ + direction[1] * distance;
                return new WorldPosition(
                    targetBlockX + 0.5,
                    startPosition.y,
                    targetBlockZ + 0.5
                );
            }
        }

        return null;
    }

    private static boolean isStraightWalkableCorridor(
        WorldChunk chunk,
        long expectedChunkIndex,
        int startBlockX,
        int feetY,
        int startBlockZ,
        int dx,
        int dz,
        int distance
    ) {
        for (int step = 1; step <= distance; step++) {
            int blockX = startBlockX + dx * step;
            int blockZ = startBlockZ + dz * step;
            if (ChunkUtil.indexChunkFromBlock(blockX, blockZ) != expectedChunkIndex) {
                return false;
            }
            if (!isWalkableColumn(chunk, blockX, feetY, blockZ)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWalkableColumn(
        WorldChunk chunk,
        int blockX,
        int feetY,
        int blockZ
    ) {
        if (feetY <= 0 || feetY >= 319) {
            return false;
        }

        int localX = ChunkUtil.localCoordinate(blockX);
        int localZ = ChunkUtil.localCoordinate(blockZ);
        int floor = chunk.getBlock(localX, feetY - 1, localZ);
        int feet = chunk.getBlock(localX, feetY, localZ);
        int head = chunk.getBlock(localX, feetY + 1, localZ);

        return floor != BlockType.EMPTY_ID
            && feet == BlockType.EMPTY_ID
            && head == BlockType.EMPTY_ID;
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
                        + ", nativeTargetCleared=" + nativeTargetCleared,
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
