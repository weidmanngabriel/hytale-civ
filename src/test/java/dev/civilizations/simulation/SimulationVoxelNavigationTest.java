package dev.civilizations.simulation;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.simulation.world.WorldArchive;
import dev.civilizations.simulation.world.VoxelWorld;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Headless navigation regression on real-shaped raw voxel fixtures. */
class SimulationVoxelNavigationTest {
    private static VoxelWorld fixture() {
        var cells = new ArrayList<WorldArchive.Cell>();
        for(int x=0;x<6;x++) for(int y=0;y<4;y++) for(int z=0;z<5;z++){
            boolean wall = x==2 && z>0 && z<4 && y==1;
            String block = y==0 || wall ? "native:stone" : "air";
            cells.add(new WorldArchive.Cell(x,y,z,block,0,0,"NONE"));
        }
        return new VoxelWorld(new WorldArchive(1,"maze",
            new WorldArchive.Bounds(0,0,0,6,4,5),cells));
    }

    @Test
    void multipleResidentsTakeDetoursAroundTerrain() {
        var world=fixture();
        var runtime=new SimulationRuntime();
        runtime.setVoxelWorld(world);
        runtime.addMiner("m1",new WorldPosition(.5,1,1.5));
        runtime.addMiner("m2",new WorldPosition(.5,1,3.5));
        runtime.orderManualMove("m1",new WorldPosition(4.5,1,1.5));
        runtime.orderManualMove("m2",new WorldPosition(4.5,1,3.5));
        for(int i=0;i<350;i++){
            runtime.tick();
            for(var resident:runtime.worldSnapshot().residents()){
                var p=resident.position();
                assertNotEquals(WorldArchive.Material.SOLID,world.material(
                    new BlockPosition((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()))));
            }
        }
        assertEquals(4.5,runtime.residentSnapshot("m1").position().x(),.02);
        assertEquals(4.5,runtime.residentSnapshot("m2").position().x(),.02);
    }

    @Test
    void modifiedTerrainInvalidatesAnExistingRoute() {
        var world=fixture();
        var runtime=new SimulationRuntime();
        runtime.setVoxelWorld(world);
        runtime.addMiner("m",new WorldPosition(.5,1,1.5));
        runtime.orderManualMove("m",new WorldPosition(4.5,1,1.5));
        runtime.runTicks(10);
        world.set(new BlockPosition(2,1,0),WorldArchive.Material.LAVA);
        runtime.runTicks(350);
        assertEquals(4.5,runtime.residentSnapshot("m").position().x(),.02);
    }
}
