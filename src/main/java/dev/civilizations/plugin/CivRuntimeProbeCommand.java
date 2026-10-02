package dev.civilizations.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
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

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Headless-only integration probe. Registered only when civilizations.runtimeProbe=true.
 */
final class CivRuntimeProbeCommand extends CommandBase {

    private static final String ROLE = "Civ_Inhabitant";
    private static final long ASSERT_AFTER_MILLIS = 1_000L;
    private static final long MINIMUM_WORLD_TICKS = 2L;

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

            var position = spawn.getPosition();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(position.x, position.z);
            world.getChunkAsync(chunkIndex).whenComplete((chunk, throwable) ->
                world.execute(() -> {
                    if (throwable != null || chunk == null) {
                        fail("spawn chunk could not be loaded", throwable);
                        return;
                    }
                    startProbe(world, spawn.getPosition(), spawn.getRotation());
                })
            );
        } catch (Throwable throwable) {
            fail("spawn chunk setup threw an exception", throwable);
        }
    }

    private void startProbe(World world, Vector3d spawnPosition, org.joml.Vector3f spawnRotation) {
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

            Vector3d position = transform.getPosition();
            WorldPosition destination = new WorldPosition(position.x, position.y, position.z);
            if (!activityRegistry.orderManualMove(ref, destination)) {
                fail("Civ manual movement order was rejected", null);
                return;
            }
            if (activityRegistry.manualMovementIntent(ref) == null) {
                fail("manual movement intent was not created", null);
                return;
            }

            long startTick = world.getTick();
            System.out.println(
                "CIV_RUNTIME_PROBE_STARTED tick=" + startTick
                    + " entity=" + ref.getIndex()
            );

            world.scheduleAfter(
                () -> assertProbe(world, ref, startTick),
                ASSERT_AFTER_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("probe setup threw an exception", throwable);
        }
    }

    private void assertProbe(World world, Ref<EntityStore> ref, long startTick) {
        try {
            long ticksElapsed = world.getTick() - startTick;
            if (ticksElapsed < MINIMUM_WORLD_TICKS) {
                fail("fewer than two real world ticks elapsed: " + ticksElapsed, null);
                return;
            }
            if (!ref.isValid()) {
                fail("Civ inhabitant became invalid before assertion", null);
                return;
            }
            if (!unitRegistry.isClaimed(ref)) {
                fail("Civ inhabitant lost its claimed state", null);
                return;
            }
            if (activityRegistry.manualMovementIntent(ref) != null) {
                fail("CivManualMovementSystem did not complete the arrived movement intent", null);
                return;
            }
            if (unitRegistry.getMoveTarget(ref) != null) {
                fail("native Civ movement target was not cleared after arrival", null);
                return;
            }

            System.out.println(
                "CIV_RUNTIME_PROBE_PASS ticks=" + ticksElapsed
                    + " entity=" + ref.getIndex()
            );
            HytaleServer.get().shutdownServer();
        } catch (Throwable throwable) {
            fail("probe assertion threw an exception", throwable);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        System.out.println("CIV_RUNTIME_PROBE_FAIL " + reason);
        if (throwable != null) {
            throwable.printStackTrace(System.out);
        }
        HytaleServer.get().shutdownServer();
    }
}
