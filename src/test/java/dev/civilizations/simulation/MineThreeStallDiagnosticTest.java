package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineHeading;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.simulation.world.VoxelWorld;
import dev.civilizations.simulation.world.WorldArchive;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Diagnostic reproduction of the three-miner MineLab stall. No production behavior is changed.
 * Print progress and controller states so the CI job log identifies where progress stops.
 */
class MineThreeStallDiagnosticTest {
    @Test
    void threeMinersContinueExcavatingBeyondInitialBlocks() {
        var cells = new ArrayList<WorldArchive.Cell>();
        for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) for (int z = 0; z < 32; z++) {
            boolean cave = x >= 14 && x <= 18 && z >= 14 && z <= 18 && y >= 10 && y <= 14;
            cells.add(new WorldArchive.Cell(x, y, z,
                (y >= 20 || cave) ? "air" : "native:stone", 0, 0, "NONE"));
        }
        var world = new VoxelWorld(new WorldArchive(WorldArchive.VERSION, "mine-three-diagnostic",
            new WorldArchive.Bounds(0, 0, 0, 32, 32, 32), cells));
        var runtime = new SimulationRuntime();
        runtime.setVoxelWorld(world);
        var home = new BlockPosition(16, 10, 16);
        runtime.addMiner("miner-1", new WorldPosition(16.5, 10, 16.5));
        runtime.addMiner("miner-2", new WorldPosition(17.5, 10, 16.5));
        runtime.addMiner("miner-3", new WorldPosition(16.5, 10, 17.5));
        runtime.configureMineLab(home, MineHeading.NORTH, 8, 99112233L);

        int lastProgress = 0;
        int longestStall = 0;
        int stagnantTicks = 0;
        for (int tick = 1; tick <= 4500; tick++) {
            runtime.tick();
            int current = runtime.excavatedMineBlocks();
            if (current > lastProgress) {
                stagnantTicks = 0;
                lastProgress = current;
            } else {
                stagnantTicks++;
            }
            longestStall = Math.max(longestStall, stagnantTicks);
            if (tick % 200 == 0) {
                Map<String, Object> state = runtime.mineDebugSnapshot();
                System.out.println("MINE_THREE_DIAGNOSTIC tick=" + tick +
                    " excavated=" + current + " slice=" + state.get("sliceIndex") +
                    "/" + state.get("sliceCount") + " stagnantTicks=" + stagnantTicks +
                    " workers=" + state.get("workers"));
            }
        }
        var state = runtime.mineDebugSnapshot();
        System.out.println("MINE_THREE_RESULT excavated=" + lastProgress +
            " longestStall=" + longestStall + " slice=" + state.get("sliceIndex") +
            "/" + state.get("sliceCount") + " workers=" + state.get("workers"));

        // Existing test only checks >0 blocks. This checks meaningful ongoing progression.
        assertTrue(((Number) state.get("sliceIndex")).intValue() == ((Number) state.get("sliceCount")).intValue(),
            "Three miners did not finish the planned tunnel: excavated=" + lastProgress +
            " longestStall=" + longestStall + " snapshot=" + state);
    }
    @Test
    void threeMinersWithSeparatedSurfaceAccessKeepDiggingSouth() {
        var cells = new ArrayList<WorldArchive.Cell>();
        for (int x = 0; x < 48; x++) for (int y = 0; y < 32; y++) for (int z = 0; z < 48; z++) {
            boolean openShaft = x >= 22 && x <= 26 && z >= 15 && z <= 20 && y >= 10 && y <= 14;
            String material = (openShaft || y >= 20) ? "air" : "native:stone";
            cells.add(new WorldArchive.Cell(x, y, z, material, 0, 0, "NONE"));
        }
        var world = new VoxelWorld(new WorldArchive(WorldArchive.VERSION,
            "south-surface-access-diagnostic", new WorldArchive.Bounds(0, 0, 0, 48, 32, 48), cells));
        var runtime = new SimulationRuntime();
        runtime.setVoxelWorld(world);
        var connector = new BlockPosition(24, 10, 18);
        var access = new BlockPosition(24, 10, 16);
        assertTrue(world.canStand(connector) && world.canStand(access));
        runtime.addMiner("miner-1", new WorldPosition(24.5, 10, 16.5));
        runtime.addMiner("miner-2", new WorldPosition(25.5, 10, 16.5));
        runtime.addMiner("miner-3", new WorldPosition(23.5, 10, 16.5));
        runtime.configureMineLab(connector, access, MineHeading.SOUTH, 8, 99112233L);

        for (int tick = 1; tick <= 4500; tick++) {
            runtime.tick();
            if (tick % 200 == 0) {
                var debug = runtime.mineDebugSnapshot();
                System.out.println("MINE_THREE_SOUTH tick=" + tick +
                    " excavated=" + runtime.excavatedMineBlocks() +
                    " slice=" + debug.get("sliceIndex") + "/" + debug.get("sliceCount") +
                    " workers=" + debug.get("workers"));
            }
        }
        var debug = runtime.mineDebugSnapshot();
        assertTrue(((Number)debug.get("sliceIndex")).intValue() ==
                ((Number)debug.get("sliceCount")).intValue(),
            "South-heading miners stopped: " + debug);
    }

    @Test
    void threeMinersExcavateInRealImportedTerrain() {
        var world = RealRegionMineFixture.load();
        var runtime = new SimulationRuntime();
        runtime.setVoxelWorld(world);

        // Carve only the prefab-equivalent entrance in the original terrain.
        // All tunnel surroundings remain as imported from region.civworld.gz.
        for (int x = 116; x <= 120; x++)
            for (int z = 2; z <= 5; z++)
                for (int y = 110; y <= 112; y++)
                    if (world.material(new BlockPosition(x, y, z)) != WorldArchive.Material.AIR)
                        world.set(new BlockPosition(x, y, z), WorldArchive.Material.AIR);
        var home = new BlockPosition(118, 110, 5);
        var access = new BlockPosition(118, 110, 2);
        assertTrue(world.canStand(home) && world.canStand(access),
            "Prefab-style entrance must be traversable on the imported terrain");
        runtime.addMiner("miner-1", new WorldPosition(118.5, 110, 2.5));
        runtime.addMiner("miner-2", new WorldPosition(119.5, 110, 2.5));
        runtime.addMiner("miner-3", new WorldPosition(117.5, 110, 2.5));
        runtime.configureMineLab(home, access, MineHeading.SOUTH, 8, 99112233L);
        int start = runtime.excavatedMineBlocks();
        for (int tick = 1; tick <= 4500; tick++) {
            runtime.tick();
            if (tick % 200 == 0) {
                var debug = runtime.mineDebugSnapshot();
                System.out.println("REAL_REGION_MINE3 tick=" + tick + " excavated=" +
                    runtime.excavatedMineBlocks() + " slice=" + debug.get("sliceIndex") +
                    "/" + debug.get("sliceCount") + " workers=" + debug.get("workers"));
            }
        }
        var debug = runtime.mineDebugSnapshot();
        assertTrue(runtime.excavatedMineBlocks() > start, "No excavation on actual terrain: " + debug);
        assertTrue(((Number)debug.get("sliceIndex")).intValue() ==
                ((Number)debug.get("sliceCount")).intValue(),
            "Real terrain miners did not complete the tunnel: " + debug);
    }

}
