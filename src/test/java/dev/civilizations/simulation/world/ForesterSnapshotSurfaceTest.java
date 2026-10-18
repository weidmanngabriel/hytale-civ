package dev.civilizations.simulation.world;

import dev.civilizations.core.BlockPosition;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForesterSnapshotSurfaceTest {
    @Test void observedGrassAndTreeTrunksAreSeparatedFromBuildingsAndCanopy() {
        var bounds = new WorldArchive.Bounds(0,0,0,3,5,3);
        var cells = new ArrayList<WorldArchive.Cell>();
        for(int x=0;x<3;x++) for(int y=0;y<5;y++) for(int z=0;z<3;z++) {
            String key = y==0 ? "Soil_Grass" : "air";
            if (x==1 && z==1 && y==1) key="Wood_Oak_Trunk";
            if (x==2 && z==2 && y==1) key="Wood_Softwood_Planks";
            cells.add(new WorldArchive.Cell(x,y,z,key,0,0,"NONE"));
        }
        var surface = new ForesterSnapshotSurface(new WorldArchive(WorldArchive.VERSION,"snapshot",bounds,cells));
        assertEquals(new BlockPosition(0,1,0),surface.observedExposedSoil(0,0));
        assertNull(surface.observedExposedSoil(1,1));
        assertNull(surface.observedExposedSoil(2,2));
        assertEquals(1,surface.observedTrunkBlocks().size());
        assertTrue(surface.observedTrunkBlocks().contains(new BlockPosition(1,1,1)));
    }
}
