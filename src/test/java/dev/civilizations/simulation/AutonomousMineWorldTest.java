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
        var cells=new ArrayList<WorldArchive.Cell>();
        for(int x=0;x<32;x++) for(int y=0;y<32;y++) for(int z=0;z<32;z++){
            boolean cave=x>=14&&x<=18&&z>=14&&z<=18&&y>=10&&y<=14;
            String block=(y>=20||cave)?"air":"native:stone";
            cells.add(new WorldArchive.Cell(x,y,z,block,0,0,"NONE"));
        }
        var archive=new WorldArchive(WorldArchive.VERSION,"mine-fixture",new WorldArchive.Bounds(0,0,0,32,32,32),cells);
        var world=new VoxelWorld(archive);
        var runtime=new SimulationRuntime();
        runtime.setVoxelWorld(world);
        var home=new BlockPosition(16,10,16);
        assertTrue(world.canStand(home));
        runtime.addMiner("miner-1",new WorldPosition(16.5,10,16.5));
        runtime.addMiner("miner-2",new WorldPosition(17.5,10,16.5));
        runtime.addMiner("miner-3",new WorldPosition(16.5,10,17.5));
        runtime.configureMineLab(home,MineHeading.NORTH,8,99112233L);
        runtime.runTicks(100);
        assertTrue(runtime.cancelManualMove("miner-1") == false,
            "Miner has no manual order before the interruption");
        runtime.orderManualMove("miner-1",new WorldPosition(16.5,10,16.5));
        runtime.runTicks(30);
        runtime.cancelManualMove("miner-1");
        runtime.runTicks(4500);
        assertTrue(runtime.worldSnapshot().residents().stream()
                .anyMatch(r -> r.state().equals("COMPLETE")),
            "At least one miner should find its way back to the starting cave");
        assertTrue(runtime.excavatedMineBlocks()>0,
            "Core-planned miner fixtures must actually change imported terrain");
        assertEquals(runtime.excavatedMineBlocks(),world.revision());
        assertEquals(4630,runtime.tickCount());
    }
}
