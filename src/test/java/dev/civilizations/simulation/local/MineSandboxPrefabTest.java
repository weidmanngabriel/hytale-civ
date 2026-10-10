package dev.civilizations.simulation.local;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.simulation.world.VoxelWorld;
import dev.civilizations.simulation.world.WorldArchive;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class MineSandboxPrefabTest {
    @Test void loadsRealPhaseOnePrefabAndTranslatesMarkerCoordinates() throws Exception {
        var prefab = new MineSandboxPrefab(MineSandboxPrefab.DEFAULT_PREFAB);
        assertTrue(prefab.model().cells().size()>500);
        assertEquals("workplace_access",prefab.model().requireMarker("workplace_access").type());
        assertEquals("mine_tunnel_connector",prefab.model().requireMarker("mine_tunnel_connector").type());
        var base = prefab.model().requireMarker("workplace_access").bounds();
        assertTrue(base.minY()>=10); // authored upper workplace, below it is the tunnel connector
        assertThrows(IllegalArgumentException.class, () -> {
            var box=new WorldArchive.Bounds(0,0,0,2,2,2);
            var cells=new ArrayList<WorldArchive.Cell>();
            for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++)
                cells.add(new WorldArchive.Cell(x,y,z,"air",0,0,"NONE"));
            var world=new VoxelWorld(new WorldArchive(WorldArchive.VERSION,"fixture",box,cells));
            prefab.place(world,box,new BlockPosition(0,0,0));
        });
    }
}
