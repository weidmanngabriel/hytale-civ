package dev.civilizations.simulation.prefab;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.simulation.world.VoxelWorld;
import dev.civilizations.simulation.world.WorldArchive;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class MinePrefabPlacementTest {
    static WorldArchive terrain() {
        var cells=new ArrayList<WorldArchive.Cell>();
        for(int x=-15;x<16;x++) for(int y=0;y<58;y++) for(int z=-15;z<31;z++)
            cells.add(new WorldArchive.Cell(x,y,z,y<28?"native:stone":"air",0,0,"NONE"));
        return new WorldArchive(WorldArchive.VERSION,"mine-lab",
            new WorldArchive.Bounds(-15,0,-15,16,58,31),cells);
    }

    @Test void authoredMineBlocksAndMarkersArePlacedAtOffsetAndRemainWalkable() throws Exception {
        var world=new VoxelWorld(terrain());
        var placement=MinePrefabPlacement.place(world,Path.of(MinePrefabPlacement.ASSET),
            new BlockPosition(0,12,0));
        assertTrue(placement.changedBlocks()>0);
        assertTrue(placement.markers().stream().anyMatch(m->m.type().equals("workplace_access")));
        assertTrue(placement.markers().stream().anyMatch(m->m.type().equals("mine_tunnel_connector")));
        assertTrue(world.canStand(placement.access()));
        assertTrue(world.canStand(placement.connector()));
        assertEquals(12,placement.origin().y());
    }

    @Test void invalidPlacementDoesNotPartiallyModifyWorld() {
        var world=new VoxelWorld(terrain());
        assertThrows(IllegalArgumentException.class,()->MinePrefabPlacement.place(
            world,Path.of(MinePrefabPlacement.ASSET),new BlockPosition(100,12,0)));
        assertEquals(0,world.revision());
    }
}
