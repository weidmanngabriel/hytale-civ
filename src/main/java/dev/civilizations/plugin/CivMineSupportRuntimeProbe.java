package dev.civilizations.plugin;

import com.hypixel.hytale.builtin.blockphysics.BlockSelectionSupportUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
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
import dev.civilizations.core.MineTuning;
import dev.civilizations.hytale.MineSupportPhysics;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Real-Hytale runtime probe for mine-support trunk deco semantics. */
final class CivMineSupportRuntimeProbe {

    private static final String SCENARIO = "minesupport";
    private static final String WORLD_NAME = "civ_mine_support_runtime";
    private static final String FLAT_GENERATOR = "Flat";
    private static final String DEFAULT_STORAGE = "default";
    private static final String ROLE = "Civ_Inhabitant";
    private static final String SUPPORT_PREFAB_KEY = "Civilizations/Mine/Mine_Support_01.prefab.json";
    private static final String SUPPORT_BEAM_BLOCK = "Wood_Fir_Trunk";
    private static final int SUPPORT_DEPTH = 4;
    private static final long STABILITY_WAIT_MILLIS = 2_000L;
    private static final long BREAK_WAIT_MILLIS = 500L;

    private CivMineSupportRuntimeProbe() {
    }

    static void start() {
        Universe universe = Universe.get();
        if (universe.getWorld(WORLD_NAME) != null || universe.isWorldLoadable(WORLD_NAME)) {
            fail("test world already exists", null);
            return;
        }

        System.out.println("CIV_MINE_SUPPORT_RUNTIME_STARTED world=" + WORLD_NAME);
        universe.addWorld(WORLD_NAME, FLAT_GENERATOR, DEFAULT_STORAGE)
            .whenComplete((world, throwable) -> {
                if (throwable != null || world == null) {
                    fail("flat test world could not be created", throwable);
                    return;
                }
                CivRuntimeProbeSuite.applyWarmDilation(world);
                world.execute(() -> preloadAndPlace(world));
            });
    }

    private static void preloadAndPlace(World world) {
        try {
            MineSegment segment = MineSegment.reserved(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                new BlockPosition(8, 1, 8),
                MineDirection.EAST,
                MineTuning.REFERENCE_SEGMENT_LENGTH_BLOCKS
            );
            List<BlockPosition> beam = MineSupportPhysics.beamBlocks(segment, SUPPORT_DEPTH);

            CompletableFuture<?>[] loads = beam.stream()
                .map(block -> world.getChunkAsync(ChunkUtil.indexChunkFromBlock(block.x(), block.z())))
                .distinct()
                .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(loads).whenComplete((ignored, throwable) -> world.execute(() -> {
                if (throwable != null) {
                    fail("support chunk could not be loaded", throwable);
                    return;
                }
                placeAndMark(world, segment);
            }));
        } catch (Throwable throwable) {
            fail("support fixture setup threw an exception", throwable);
        }
    }

    private static void placeAndMark(World world, MineSegment segment) {
        try {
            BlockSelection raw = PrefabStore.get().getAssetPrefabFromAnyPack(SUPPORT_PREFAB_KEY);
            if (raw == null) {
                fail("support prefab is missing", null);
                return;
            }
            BlockSelection selection = new BlockSelection(raw);
            BlockSelectionSupportUtil.applySupportValues(selection);
            BlockPosition originBlock = segment.supportOrigin(SUPPORT_DEPTH);
            Vector3i origin = new Vector3i(originBlock.x(), originBlock.y(), originBlock.z());
            selection.placeNoReturn(world, origin, world.getEntityStore().getStore());
            System.out.println("CIV_MINE_SUPPORT_PREFAB_PLACED origin=" + originBlock);

            if (!MineSupportPhysics.markBeamAsDeco(world, segment, SUPPORT_DEPTH)) {
                fail("beam could not be marked as deco", null);
                return;
            }
            if (!MineSupportPhysics.beamIsDeco(world, segment, SUPPORT_DEPTH)) {
                fail("beam did not retain deco metadata immediately after marking", null);
                return;
            }
            System.out.println("CIV_MINE_SUPPORT_DECO_MARKED beamCells=4");

            world.scheduleAfter(
                () -> verifyStableAndBreak(world, segment),
                STABILITY_WAIT_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("support placement/deco marking threw an exception", throwable);
        }
    }

    private static void verifyStableAndBreak(World world, MineSegment segment) {
        try {
            List<BlockPosition> beam = MineSupportPhysics.beamBlocks(segment, SUPPORT_DEPTH);
            for (BlockPosition block : beam) {
                BlockType type = loadedBlockType(world, block);
                if (type == null || !SUPPORT_BEAM_BLOCK.equalsIgnoreCase(type.getId())) {
                    fail("beam did not survive native block physics at " + block + " type="
                        + (type == null ? "null" : type.getId()), null);
                    return;
                }
            }
            if (!MineSupportPhysics.beamIsDeco(world, segment, SUPPORT_DEPTH)) {
                fail("beam lost deco metadata during stability wait", null);
                return;
            }
            System.out.println("CIV_MINE_SUPPORT_STABLE beamCells=4 waitMs=" + STABILITY_WAIT_MILLIS);

            BlockPosition target = beam.getFirst();
            var spawned = NPCPlugin.get().spawnNPC(
                world.getEntityStore().getStore(),
                ROLE,
                null,
                new Vector3d(target.x() - 2.0, target.y() - 2.0, target.z() + 0.5),
                new Rotation3f()
            );
            if (spawned == null || spawned.first() == null || !spawned.first().isValid()) {
                fail("could not spawn native breaker NPC", null);
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
                () -> verifyBreakability(world, segment, target),
                BREAK_WAIT_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (Throwable throwable) {
            fail("support stability/breakability check threw an exception", throwable);
        }
    }

    private static void verifyBreakability(World world, MineSegment segment, BlockPosition removed) {
        try {
            BlockType removedType = loadedBlockType(world, removed);
            if (removedType != null && removedType != BlockType.EMPTY) {
                fail("deco beam block remained after native break: " + removedType.getId(), null);
                return;
            }

            int remaining = 0;
            for (BlockPosition block : MineSupportPhysics.beamBlocks(segment, SUPPORT_DEPTH)) {
                if (block.equals(removed)) continue;
                BlockType type = loadedBlockType(world, block);
                if (type != null && SUPPORT_BEAM_BLOCK.equalsIgnoreCase(type.getId())) remaining++;
            }
            if (remaining != 3) {
                fail("breaking one deco trunk cascaded into the remaining beam; remaining=" + remaining, null);
                return;
            }

            System.out.println("CIV_MINE_SUPPORT_BREAKABLE removed=" + removed + " remainingBeam=3");
            System.out.println("CIV_MINE_SUPPORT_RUNTIME_PASS");
            CivRuntimeProbeSuite.scenarioPassed(SCENARIO);
        } catch (Throwable throwable) {
            fail("support breakability verification threw an exception", throwable);
        }
    }

    private static BlockType loadedBlockType(World world, BlockPosition block) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(block.x(), block.z()));
        return chunk == null ? null : chunk.getBlockType(block.x(), block.y(), block.z());
    }

    private static void fail(String reason, Throwable throwable) {
        System.out.println("CIV_MINE_SUPPORT_RUNTIME_FAIL " + reason);
        if (throwable != null) throwable.printStackTrace(System.out);
        CivRuntimeProbeSuite.scenarioFailed(SCENARIO, reason);
    }
}
