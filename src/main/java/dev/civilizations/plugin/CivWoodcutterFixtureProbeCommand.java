package dev.civilizations.plugin;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.scenario.WoodcutterBasicScenario;
import org.joml.Vector3i;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Temporary runtime diagnostic for selecting a stable native tree classifier. */
final class CivWoodcutterFixtureProbeCommand extends CommandBase {

    private static final String WORLD_NAME = WoodcutterBasicScenario.TEST_WORLD_NAME;
    private static final String OAK_PREFAB_KEY = "Trees/Oak/Stage_1/Oak_Stage1_001";
    private static final String WOOD_GATHER_TYPE = "Woods";

    CivWoodcutterFixtureProbeCommand(CivUnitRegistry ignoredUnitRegistry) {
        super("civwoodcutterprobe", "Inspects a vanilla oak fixture in a native Flat world.");
        requireNoPermission();
    }

    @Override
    protected void executeSync(CommandContext context) {
        Universe universe = Universe.get();
        if (universe.getWorld(WORLD_NAME) != null || universe.isWorldLoadable(WORLD_NAME)) {
            fail("test world already exists: " + WORLD_NAME, null);
            return;
        }

        System.out.println("CIV_WOODCUTTER_RUNTIME_STARTED diagnostic=oak-block-metadata");
        universe.addWorld(WORLD_NAME, "Flat", "default").whenComplete((world, throwable) -> {
            if (throwable != null || world == null) {
                fail("flat test world could not be created", throwable);
                return;
            }
            world.execute(() -> prepareOak(world));
        });
    }

    private static void prepareOak(World world) {
        BlockPosition anchor = WoodcutterBasicScenario.TREE_ANCHORS.getFirst();
        int minX = anchor.x() - 8;
        int maxX = anchor.x() + 8;
        int minZ = anchor.z() - 8;
        int maxZ = anchor.z() + 8;

        long minChunk = ChunkUtil.indexChunkFromBlock(minX, minZ);
        long maxChunk = ChunkUtil.indexChunkFromBlock(maxX, maxZ);
        world.getChunkAsync(minChunk).thenCompose(ignored -> world.getChunkAsync(maxChunk))
            .whenComplete((ignored, throwable) -> world.execute(() -> {
                if (throwable != null) {
                    fail("fixture chunks could not be loaded", throwable);
                    return;
                }
                try {
                    PrefabStore prefabStore = PrefabStore.get();
                    Path prefabPath = prefabStore.findBrowsablePrefabPath(OAK_PREFAB_KEY);
                    if (prefabPath == null) {
                        fail("oak prefab path not found", null);
                        return;
                    }
                    BlockSelection source = prefabStore.getPrefab(prefabPath);
                    if (source == null || source.getBlockCount() == 0) {
                        fail("oak prefab could not be loaded", null);
                        return;
                    }
                    new BlockSelection(source).placeNoReturn(
                        world,
                        new Vector3i(anchor.x(), anchor.y(), anchor.z()),
                        world.getEntityStore().getStore()
                    );
                    System.out.println(
                        "CIV_WOODCUTTER_FLAT_WORLD_READY prefabBlocks=" + source.getBlockCount()
                    );
                    world.scheduleAfter(
                        () -> inspectOak(world, anchor),
                        750L,
                        TimeUnit.MILLISECONDS
                    );
                } catch (Throwable placementFailure) {
                    fail("oak placement failed", placementFailure);
                }
            }));
    }

    private static void inspectOak(World world, BlockPosition anchor) {
        try {
            Map<String, BlockMetadata> unique = new LinkedHashMap<>();
            int woods = 0;
            for (int x = anchor.x() - 8; x <= anchor.x() + 8; x++) {
                for (int z = anchor.z() - 8; z <= anchor.z() + 8; z++) {
                    WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
                    if (chunk == null) {
                        continue;
                    }
                    for (int y = 0; y <= 28; y++) {
                        BlockType blockType = chunk.getBlockType(x, y, z);
                        if (!isWood(blockType)) {
                            continue;
                        }
                        woods++;
                        unique.putIfAbsent(
                            blockType.getId(),
                            new BlockMetadata(
                                blockType.getId(),
                                blockType.getGroup(),
                                blockType.getBlockListAssetId(),
                                blockType.getPrefabListAssetId(),
                                String.valueOf(blockType.getMaterial()),
                                x,
                                y,
                                z
                            )
                        );
                    }
                }
            }

            System.out.println(
                "CIV_WOODCUTTER_WOOD_METADATA totalWoodBlocks=" + woods
                    + " uniqueTypes=" + unique.size()
            );
            for (BlockMetadata metadata : unique.values()) {
                System.out.println(
                    "CIV_WOOD_BLOCK id=" + metadata.id()
                        + " group=" + metadata.group()
                        + " blockList=" + metadata.blockList()
                        + " prefabList=" + metadata.prefabList()
                        + " material=" + metadata.material()
                        + " sample=" + metadata.x() + "," + metadata.y() + "," + metadata.z()
                );
            }

            if (woods == 0) {
                fail("placed vanilla oak contains no Woods gathering blocks", null);
                return;
            }

            System.out.println("CIV_WOODCUTTER_METADATA_DISCOVERY_PASS");
            HytaleServer.get().shutdownServer();
        } catch (Throwable throwable) {
            fail("oak metadata inspection failed", throwable);
        }
    }

    private static boolean isWood(BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        BlockGathering gathering = blockType.getGathering();
        BlockBreakingDropType breaking = gathering == null ? null : gathering.getBreaking();
        return breaking != null && WOOD_GATHER_TYPE.equals(breaking.getGatherType());
    }

    private static void fail(String reason, Throwable throwable) {
        System.out.println("CIV_WOODCUTTER_RUNTIME_FAIL " + reason);
        if (throwable != null) {
            throwable.printStackTrace(System.out);
        }
        HytaleServer.get().shutdownServer();
    }

    private record BlockMetadata(
        String id,
        String group,
        String blockList,
        String prefabList,
        String material,
        int x,
        int y,
        int z
    ) {
    }
}
