package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineHeading;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.simulation.world.VoxelWorld;
import dev.civilizations.simulation.world.WorldArchive;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Real Core tunnel planning and shared block claims against an imported voxel
 * adapter, with no Hytale classes or prepared replay frames.
 */
class AutonomousMineWorldTest {
    @Test void minersExcavateNativeTerrainFromRepeatableStartState() {
        var world = RealRegionMineFixture.load();
        var runtime = new SimulationRuntime();
        runtime.setVoxelWorld(world);
        // A mine prefab opens a short 3-block-high worker access in the real terrain.
        for (int x = 116; x <= 120; x++)
            for (int z = 2; z <= 5; z++)
                for (int y = 110; y <= 112; y++)
                    if (world.material(new BlockPosition(x, y, z)) != WorldArchive.Material.AIR)
                        world.set(new BlockPosition(x, y, z), WorldArchive.Material.AIR);
        var home = new BlockPosition(118, 110, 5);
        var access = new BlockPosition(118, 110, 2);
        assertTrue(world.canStand(home));
        assertTrue(world.canStand(access));
        runtime.addMiner("miner-1", new WorldPosition(118.5, 110, 2.5));
        runtime.addMiner("miner-2", new WorldPosition(119.5, 110, 2.5));
        runtime.addMiner("miner-3", new WorldPosition(117.5, 110, 2.5));
        runtime.configureMineLab(home, access, MineHeading.SOUTH, 8, 99112233L);
        var initialRevision = world.revision();
        runtime.runTicks(100);
        assertFalse(runtime.cancelManualMove("miner-1"),
            "Miner has no manual order before interruption");
        runtime.orderManualMove("miner-1", new WorldPosition(118.5, 110, 2.5));
        runtime.runTicks(30);
        runtime.cancelManualMove("miner-1");
        runtime.runTicks(4500);
        var debug = runtime.mineDebugSnapshot();
        assertEquals(((Number)debug.get("sliceCount")).intValue(),
            ((Number)debug.get("sliceIndex")).intValue(),
            () -> "Workers stopped before completing real-world tunnel: " + debug);
        assertTrue(runtime.excavatedMineBlocks() > 0,
            "Core-planned miners must excavate on exported terrain");
        assertEquals(runtime.excavatedMineBlocks(), world.revision() - initialRevision);
        assertEquals(4630, runtime.tickCount());
    }
    @Test void fluidsBlockWorkAndExplicitRetryPreservesWorldProgress() {
        for (var fluid : java.util.List.of(WorldArchive.Material.WATER,WorldArchive.Material.LAVA)) {
            var cells = new ArrayList<WorldArchive.Cell>();
            for (int x=0;x<32;x++) for (int y=0;y<32;y++) for (int z=0;z<32;z++) {
                boolean cave=x>=14&&x<=18&&z>=14&&z<=18&&y>=10&&y<=14;
                cells.add(new WorldArchive.Cell(x,y,z,(y>=20||cave)?"air":"native:stone",0,0,"NONE"));
            }
            var world = new VoxelWorld(new WorldArchive(WorldArchive.VERSION,"hazard-fixture",
                new WorldArchive.Bounds(0,0,0,32,32,32),cells));
            var runtime = new SimulationRuntime(); runtime.setVoxelWorld(world);
            var home = new BlockPosition(16,10,16);
            var mineId = java.util.UUID.nameUUIDFromBytes("headless-mine:99112233".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var geometry = dev.civilizations.core.MineNetworkGrowthPlanner.plan(mineId,home,MineHeading.NORTH,8,1,99112233L)
                .mainTunnel().geometry();
            var block = geometry.slices().getFirst().excavationBlocks().stream()
                .filter(p -> world.material(p)==WorldArchive.Material.SOLID).findFirst().orElseThrow();
            world.set(block,fluid);
            runtime.addMiner("miner",new WorldPosition(16.5,10,16.5));
            runtime.configureMineLab(home,MineHeading.NORTH,8,99112233L);
            runtime.runTicks(100);
            assertEquals(fluid,world.material(block)); assertEquals(0,runtime.excavatedMineBlocks());
            assertEquals("BLOCKED",runtime.residentSnapshot("miner").autonomousState());
            world.set(block,WorldArchive.Material.SOLID);runtime.recoverMineLab();runtime.runTicks(4500);
            assertTrue(runtime.excavatedMineBlocks()>0);assertEquals(WorldArchive.Material.AIR,world.material(block));
            int progress=runtime.excavatedMineBlocks();runtime.recoverMineLab();runtime.runTicks(100);
            assertEquals(progress,runtime.excavatedMineBlocks());
        }
    }

}
