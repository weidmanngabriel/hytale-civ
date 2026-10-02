package dev.civilizations.plugin;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.civilizations.scenario.WoodcutterBasicScenario;

import java.util.List;
import java.util.Locale;

/**
 * Temporary headless discovery probe for the deterministic woodcutter fixture.
 * Registered only when civilizations.runtimeProbe=true.
 */
final class CivWoodcutterFixtureProbeCommand extends CommandBase {

    private static final String FLAT_GENERATOR = "Flat";
    private static final String DEFAULT_STORAGE = "default";

    CivWoodcutterFixtureProbeCommand() {
        super("civwoodcutterprobe", "Discovers deterministic Hytale fixtures for the woodcutter runtime scenario.");
        requireNoPermission();
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
            "CIV_WOODCUTTER_FIXTURE_DISCOVERY_STARTED world=" + worldName
                + " generator=" + FLAT_GENERATOR
        );

        universe.addWorld(worldName, FLAT_GENERATOR, DEFAULT_STORAGE)
            .whenComplete((world, throwable) -> {
                if (throwable != null || world == null) {
                    fail("flat test world could not be created", throwable);
                    return;
                }
                world.execute(() -> verifyFlatWorldAndDiscoverPrefabs(world));
            });
    }

    private static void verifyFlatWorldAndDiscoverPrefabs(World world) {
        try {
            int blockX = (int) Math.floor(WoodcutterBasicScenario.WOODCUTTER_START.x());
            int blockZ = (int) Math.floor(WoodcutterBasicScenario.WOODCUTTER_START.z());
            long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
            world.getChunkAsync(chunkIndex).whenComplete((chunk, throwable) ->
                world.execute(() -> {
                    if (throwable != null || chunk == null) {
                        fail("flat test chunk could not be loaded", throwable);
                        return;
                    }

                    BlockType ground = chunk.getBlockType(blockX, 0, blockZ);
                    BlockType feet = chunk.getBlockType(blockX, 1, blockZ);
                    if (ground == null || ground == BlockType.EMPTY) {
                        fail("flat generator did not create ground at y=0", null);
                        return;
                    }
                    if (feet != null && feet != BlockType.EMPTY) {
                        fail("flat generator did not leave entity space empty at y=1", null);
                        return;
                    }

                    List<String> treeCandidates = PrefabStore.get().listBrowsablePrefabKeys().stream()
                        .filter(key -> {
                            String lower = key.toLowerCase(Locale.ROOT);
                            return lower.contains("tree") || lower.contains("oak");
                        })
                        .sorted()
                        .limit(120)
                        .toList();

                    System.out.println(
                        "CIV_WOODCUTTER_FLAT_WORLD_READY ground=" + ground.getId()
                            + " feet=" + (feet == null ? "null" : feet.getId())
                    );
                    System.out.println(
                        "CIV_TREE_PREFAB_CANDIDATES count=" + treeCandidates.size()
                            + " keys=" + String.join("|", treeCandidates)
                    );

                    if (treeCandidates.isEmpty()) {
                        fail("no tree-like prefab keys were found in loaded asset packs", null);
                        return;
                    }

                    System.out.println("CIV_WOODCUTTER_FIXTURE_DISCOVERY_PASS");
                    HytaleServer.get().shutdownServer();
                })
            );
        } catch (Throwable throwable) {
            fail("fixture discovery threw an exception", throwable);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        System.out.println("CIV_WOODCUTTER_FIXTURE_DISCOVERY_FAIL " + reason);
        if (throwable != null) {
            throwable.printStackTrace(System.out);
        }
        HytaleServer.get().shutdownServer();
    }
}
