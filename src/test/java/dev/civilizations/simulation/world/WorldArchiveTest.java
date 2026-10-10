package dev.civilizations.simulation.world;

import dev.civilizations.core.BlockPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldArchiveTest {
    @TempDir Path temp;

    private WorldArchive fixture() {
        var cells=new ArrayList<WorldArchive.Cell>();
        for(int x=0;x<4;x++) for(int y=0;y<4;y++) for(int z=0;z<4;z++) {
            String block=y==0?"native:stone":"air";
            cells.add(new WorldArchive.Cell(x,y,z,block,0,0,"NONE"));
        }
        return new WorldArchive(1,"test-world",new WorldArchive.Bounds(0,0,0,4,4,4),cells);
    }

    @Test void archiveSurvivesRoundTripWithoutDiscardingNativeIds() throws Exception {
        var original=fixture();
        Path path=temp.resolve("region.civworld.gz");
        original.write(path);
        var restored=WorldArchive.read(path);
        assertEquals(original,restored);
        assertEquals("native:stone",restored.cells().getFirst().blockKey());
    }

    @Test void incompleteOrDuplicateArchiveIsRejected() {
        var original=fixture();
        assertThrows(IllegalArgumentException.class,()->new WorldArchive(
            1,"test-world",original.bounds(),original.cells().subList(1,original.cells().size())));
        var duplicate=new ArrayList<>(original.cells());
        duplicate.set(1,duplicate.getFirst());
        assertThrows(IllegalArgumentException.class,()->new WorldArchive(
            1,"test-world",original.bounds(),duplicate));
    }

    @Test void navigationOnlyUsesFourDirectionsAndOneStepHeight() {
        var world=new VoxelWorld(fixture());
        var start=new BlockPosition(0,1,0);
        var goal=new BlockPosition(2,1,2);
        List<BlockPosition> path=world.path(start,goal);
        assertFalse(path.isEmpty());
        for(int i=1;i<path.size();i++){
            var a=path.get(i-1);var b=path.get(i);
            assertEquals(1,Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z()));
            assertTrue(Math.abs(a.y()-b.y())<=1);
        }
        world.set(new BlockPosition(1,1,0),WorldArchive.Material.SOLID);
        world.set(new BlockPosition(1,2,0),WorldArchive.Material.SOLID);
        assertFalse(world.path(start,goal).contains(new BlockPosition(1,1,0)));
        world.set(new BlockPosition(0,1,0),WorldArchive.Material.WATER);
        assertTrue(world.path(start,goal).isEmpty());
    }
}
