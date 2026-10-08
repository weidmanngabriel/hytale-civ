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
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineInfrastructureTask;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineTunnelGeometry;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.hytale.MineBlockPlacement;
import dev.civilizations.hytale.MineInfrastructurePlacementResolver;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Runtime-only probe for real Hytale atmosphere assets and central-corridor passability. */
final class CivMineAtmosphereProbeCommand extends CommandBase {

    private static final String ROLE = "Civ_Inhabitant";
    private static final int FLOOR_Y = 200;
    private static final int WALK_Y = FLOOR_Y + 1;
    private static final int CENTER_Z = 16;
    private static final int MIN_X = 3;
    private static final int MAX_X = 28;
    private static final int MIN_Z = CENTER_Z - 4;
    private static final int MAX_Z = CENTER_Z + 4;
    private static final int TUNNEL_HEIGHT = 7;
    private static final int START_X = 5;
    private static final int TARGET_X = 26;
    private static final long ASSERT_INTERVAL_MILLIS = 250L;
    private static final long TIMEOUT_MILLIS = 12_000L;

    private final CivUnitRegistry unitRegistry;
    private final CivActivityRegistry activityRegistry;

    CivMineAtmosphereProbeCommand(
        CivUnitRegistry unitRegistry,
        CivActivityRegistry activityRegistry
    ) {
        super("civmineatmosphereprobe", "Runs the real Hytale mine-atmosphere runtime probe.");
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
        System.out.println("CIV_MINE_ATMOSPHERE_RUNTIME_STARTED");
        world.execute(() -> loadChunk(world));
    }

    private void loadChunk(World world) {
        try {
            var spawn = world.getWorldConfig().getSpawnProvider().getSpawnPoint(world, UUID.randomUUID());
            if (spawn == null) {
                fail("world spawn provider returned no spawn point", null);
                return;
            }
            long chunkIndex = ChunkUtil.indexChunkFromBlock(
                spawn.getPosition().x, spawn.getPosition().z
            );
            world.getChunkAsync(chunkIndex).whenComplete((chunk, throwable) ->
                world.execute(() -> {
                    if (throwable != null || chunk == null) {
                        fail("fixture chunk could not be loaded", throwable);
                        return;
                    }
                    prepareFixture(world, chunk, spawn.getRotation());
                })
            );
        } catch (Throwable throwable) {
            fail("fixture setup threw an exception", throwable);
        }
    }

    private void prepareFixture(World world, WorldChunk chunk, Rotation3f spawnRotation) {
        try {
            int minWorldX = ChunkUtil.minBlock(chunk.getX());
            int minWorldZ = ChunkUtil.minBlock(chunk.getZ());
            buildTunnelShell(chunk);

            MineTunnelGeometry geometry = geometry(minWorldX, minWorldZ);
            UUID tunnelId = UUID.fromString("00000000-0000-0000-0000-000000000808");
            MineInfrastructureTask.DecorationKind[] kinds =
                MineInfrastructureTask.DecorationKind.values();
            int[] slices = new int[]{3, 6, 9, 12, 15, 18, 21};

            for (int i = 0; i < kinds.length; i++) {
                MineInfrastructureTask task = new MineInfrastructureTask(
                    UUID.nameUUIDFromBytes(("atmosphere:" + kinds[i]).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    tunnelId,
                    MineInfrastructureTask.Type.PLACE_DECORATION,
                    2,
                    slices[i],
                    slices[i],
                    geometry.slices().get(slices[i]).floorCenter(),
                    kinds[i]
                );
                MineInfrastructurePlacementResolver.ResolvedTask resolved =
                    MineInfrastructurePlacementResolver.resolve(
                        world, task, MineTunnel.Kind.MAIN, geometry
                    );
                if (resolved == null || resolved.placements().isEmpty()) {
                    fail("decoration could not resolve: " + kinds[i], null);
                    return;
                }

                List<String> assets = new ArrayList<>();
                for (MineInfrastructurePlacementResolver.PlacementStep placement : resolved.placements()) {
                    assets.add(placement.blockId());
                    if (!MineBlockPlacement.place(
                        world,
                        placement.position(),
                        placement.blockId(),
                        placement.rotation(),
                        placement.placedAgainst(),
                        placement.markDeco()
                    )) {
                        fail(
                            "decoration placement failed: " + kinds[i] + " asset=" + placement.blockId(),
                            null
                        );
                        return;
                    }
                }
                System.out.println(
                    "CIV_MINE_ATMOSPHERE_DECORATION kind=" + kinds[i]
                        + " assets=" + String.join(",", assets)
                );
            }

            System.out.println("CIV_MINE_ATMOSPHERE_FIXTURE_READY");
            Vector3d start = new Vector3d(
                minWorldX + START_X + 0.5,
                WALK_Y,
                minWorldZ + CENTER_Z + 0.5
            );
            WorldPosition target = new WorldPosition(
                minWorldX + TARGET_X + 0.5,
                WALK_Y,
                minWorldZ + CENTER_Z + 0.5
            );
            startNavigation(world, start, spawnRotation, target);
        } catch (Throwable throwable) {
            fail("atmosphere fixture threw an exception", throwable);
        }
    }

    private static void buildTunnelShell(WorldChunk chunk) {
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                boolean wall = z == MIN_Z || z == MAX_Z;
                chunk.setBlock(x, FLOOR_Y, z, BlockType.DEBUG_CUBE);
                for (int y = WALK_Y; y < WALK_Y + TUNNEL_HEIGHT; y++) {
                    chunk.setBlock(x, y, z, wall ? BlockType.DEBUG_CUBE : BlockType.EMPTY);
                }
                chunk.setBlock(x, WALK_Y + TUNNEL_HEIGHT, z, BlockType.DEBUG_CUBE);
            }
        }
    }

    private static MineTunnelGeometry geometry(int minWorldX, int minWorldZ) {
        List<MineTunnelGeometry.Slice> slices = new ArrayList<>();
        Set<BlockPosition> all = new LinkedHashSet<>();
        Set<BlockPosition> nav = new LinkedHashSet<>();

        for (int localX = MIN_X; localX <= MAX_X; localX++) {
            int index = localX - MIN_X;
            BlockPosition center = new BlockPosition(
                minWorldX + localX,
                WALK_Y,
                minWorldZ + CENTER_Z
            );
            Set<BlockPosition> excavation = new LinkedHashSet<>();
            Set<BlockPosition> navigation = new LinkedHashSet<>();
            for (int lateral = -3; lateral <= 3; lateral++) {
                for (int y = 0; y < TUNNEL_HEIGHT; y++) {
                    excavation.add(new BlockPosition(
                        center.x(), center.y() + y, center.z() + lateral
                    ));
                }
            }
            for (int lateral = -1; lateral <= 1; lateral++) {
                for (int y = 0; y < 3; y++) {
                    navigation.add(new BlockPosition(
                        center.x(), center.y() + y, center.z() + lateral
                    ));
                }
            }
            excavation.addAll(navigation);
            MineTunnelGeometry.Slice slice = new MineTunnelGeometry.Slice(
                index, center, 7, TUNNEL_HEIGHT, excavation, navigation
            );
            slices.add(slice);
            all.addAll(excavation);
            nav.addAll(navigation);
        }
        return new MineTunnelGeometry(
            MineTunnel.Kind.MAIN,
            808L,
            slices,
            all,
            nav,
            List.of()
        );
    }

    private void startNavigation(
        World world,
        Vector3d requestedStart,
        Rotation3f spawnRotation,
        WorldPosition target
    ) {
        var spawned = NPCPlugin.get().spawnNPC(
            world.getEntityStore().getStore(), ROLE, null, requestedStart, spawnRotation
        );
        if (spawned == null || spawned.first() == null || !spawned.first().isValid()) {
            fail("could not spawn Civ inhabitant", null);
            return;
        }

        Ref<EntityStore> ref = spawned.first();
        if (!unitRegistry.toggleClaim(ref) || !unitRegistry.isClaimed(ref)) {
            fail("could not claim Civ inhabitant", null);
            return;
        }
        if (!activityRegistry.orderManualMove(ref, target)) {
            fail("manual movement order was rejected", null);
            return;
        }

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS);
        world.scheduleAfter(
            () -> assertNavigation(world, ref, target, deadline),
            ASSERT_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS
        );
    }

    private void assertNavigation(
        World world,
        Ref<EntityStore> ref,
        WorldPosition target,
        long deadlineNanos
    ) {
        try {
            if (!ref.isValid()) {
                fail("probe NPC became invalid", null);
                return;
            }
            TransformComponent transform = ref.getStore()
                .getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                fail("probe NPC lost TransformComponent", null);
                return;
            }

            Vector3d position = transform.getPosition();
            double dx = position.x - target.x();
            double dz = position.z - target.z();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (activityRegistry.manualMovementIntent(ref) == null
                && unitRegistry.getMoveTarget(ref) == null
                && distance <= 1.25) {
                System.out.println(
                    "CIV_MINE_ATMOSPHERE_NAVIGATION_PASS targetDistance="
                        + String.format(java.util.Locale.ROOT, "%.2f", distance)
                );
                System.out.println("CIV_MINE_ATMOSPHERE_RUNTIME_PASS");
                HytaleServer.get().shutdownServer();
                return;
            }

            if (System.nanoTime() >= deadlineNanos) {
                fail(
                    "NPC did not traverse decorated central corridor; targetDistance="
                        + String.format(java.util.Locale.ROOT, "%.2f", distance),
                    null
                );
                return;
            }
            world.scheduleAfter(
                () -> assertNavigation(world, ref, target, deadlineNanos),
                ASSERT_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("navigation assertion threw an exception", throwable);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        System.out.println("CIV_MINE_ATMOSPHERE_RUNTIME_FAIL " + reason);
        if (throwable != null) throwable.printStackTrace(System.out);
        HytaleServer.get().shutdownServer();
    }
}
