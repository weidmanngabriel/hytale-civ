package dev.civilizations.simulation.world;

import dev.civilizations.core.BlockPosition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForesterSurfaceTest {
    @Test void selectsTopOpenSkySurfaceNotCaveFloorOrWater() {
        var b = new WorldArchive.Bounds(-2, -2, -2, 3, 10, 3);
        byte[] raw = new byte[Math.toIntExact(b.volume())];
        int index=0;
        for (int x=b.minX(); x<b.maxX(); x++)
            for (int y=b.minY(); y<b.maxY(); y++)
                for (int z=b.minZ(); z<b.maxZ(); z++)
                    raw[index++] = (byte)(y==-1 ? WorldArchive.Material.SOLID.ordinal()
                        : WorldArchive.Material.AIR.ordinal());
        var w = VoxelWorld.fromMaterialCodes(b,raw);
        assertEquals(new BlockPosition(0,0,0),w.openSkySurface(0,0));
        w.set(new BlockPosition(0,5,0),WorldArchive.Material.SOLID);
        assertEquals(new BlockPosition(0,6,0),w.openSkySurface(0,0));
        w.set(new BlockPosition(1,0,0),WorldArchive.Material.WATER);
        assertNull(w.openSkySurface(1,0));
        w.set(new BlockPosition(2,0,0),WorldArchive.Material.SOLID);
        assertEquals(new BlockPosition(2,1,0),w.openSkySurface(2,0));
        assertNull(w.openSkySurface(15,15));
    }
}
