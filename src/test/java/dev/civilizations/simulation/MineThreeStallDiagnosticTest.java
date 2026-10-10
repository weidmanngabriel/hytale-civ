package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineHeading;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.simulation.world.VoxelWorld;
import dev.civilizations.simulation.world.WorldArchive;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Diagnostic reproduction of the three-miner MineLab stall. No production behavior is changed.
 * Print progress and controller states so the CI job log identifies where progress stops.
 */
class MineThreeStallDiagnosticTest {
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

    @Test
    void originalSurfaceMinePrefabThreeMinersOnExportedTerrain() throws Exception {
        var world = RealRegionMineFixture.load();
        var prefab = new dev.civilizations.simulation.local.MineSandboxPrefab(
            dev.civilizations.simulation.local.MineSandboxPrefab.DEFAULT_PREFAB);
        // Exact world-centre candidate from the source export (not the crop centre).
        var placement = prefab.place(world,
            new WorldArchive.Bounds(88, 88, -12, 136, 136, 36),
            new BlockPosition(118, 102, 12));
        var access = placement.access();
        var connector = placement.connector();
        var reachable = new ArrayList<BlockPosition>();
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++)
            for (int dy = -2; dy <= 2; dy++) {
                var p = new BlockPosition(access.x()+dx, access.y()+dy, access.z()+dz);
                if (world.canStand(p) && !world.path(p, access).isEmpty()) reachable.add(p);
            }
        reachable.sort(java.util.Comparator
            .comparingInt((BlockPosition p) -> Math.abs(p.x()-access.x()) +
                Math.abs(p.z()-access.z()) + Math.abs(p.y()-access.y()))
            .thenComparingInt(BlockPosition::x).thenComparingInt(BlockPosition::z));
        assertTrue(reachable.size() >= 3, "Mine prefab lacks reachable spawn cells");
        var runtime = new SimulationRuntime();
        runtime.setVoxelWorld(world);
        for (int i = 0; i < 3; i++) {
            var p = reachable.get(i);
            runtime.addMiner("miner-" + (i+1), new WorldPosition(p.x()+.5,p.y(),p.z()+.5));
        }
        BlockPosition missingFloor = new BlockPosition(118, 102, 22);
        assertEquals(WorldArchive.Material.AIR, world.material(missingFloor),
            "Real-world test must contain the unwalkable gap that caused the original stall");
        runtime.configureMineLab(connector, access, MineHeading.SOUTH, 8, 99112233L);
        for (int tick = 1; tick <= 4500; tick++) {
            runtime.tick();
            if (tick % 200 == 0) {
                var debug = runtime.mineDebugSnapshot();
                System.out.println("MINE_THREE_ORIGINAL tick=" + tick +
                    " excavated=" + runtime.excavatedMineBlocks() +
                    " slices=" + debug.get("sliceIndex") + "/" + debug.get("sliceCount") +
                    " workers=" + debug.get("workers"));
            }
        }
        var debug = runtime.mineDebugSnapshot();
        assertTrue(((Number)debug.get("sliceIndex")).intValue() ==
                ((Number)debug.get("sliceCount")).intValue(),
            "Original Mine_01 / real terrain three-miner scenario stalled: " + debug);
        assertEquals(WorldArchive.Material.SOLID, world.material(missingFloor),
            "Mandatory bridge infrastructure must restore walkable support before tunneling");
    }

}
