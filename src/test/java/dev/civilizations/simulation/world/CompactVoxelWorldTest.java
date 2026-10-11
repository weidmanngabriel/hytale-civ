package dev.civilizations.simulation.world;

import dev.civilizations.core.BlockPosition;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class CompactVoxelWorldTest {
    @Test void entireRegionSizedMaterialArrayUsesDirectLoader() {
        var bounds = new WorldArchive.Bounds(56,8,-41,181,148,66);
        byte[] materials = new byte[Math.toIntExact(bounds.volume())];
        Arrays.fill(materials, (byte) WorldArchive.Material.SOLID.ordinal());
        int index = ((118-bounds.minX())*140 + (110-bounds.minY()))*107 + (12-bounds.minZ());
        materials[index] = (byte) WorldArchive.Material.AIR.ordinal();
        var world = VoxelWorld.fromMaterialCodes(bounds, materials);
        assertEquals(1_872_500, materials.length);
        assertEquals(WorldArchive.Material.AIR, world.material(new BlockPosition(118,110,12)));
        assertEquals(WorldArchive.Material.SOLID, world.material(new BlockPosition(118,110,13)));
        materials[index] = (byte) WorldArchive.Material.LAVA.ordinal();
        assertEquals(WorldArchive.Material.AIR, world.material(new BlockPosition(118,110,12)),
            "World must own a defensive copy of compact source data");
    }

    @Test void rejectInvalidMaterialCodesAndTruncatedData() {
        var bounds = new WorldArchive.Bounds(0,0,0,2,2,2);
        assertThrows(IllegalArgumentException.class,
            () -> VoxelWorld.fromMaterialCodes(bounds, new byte[7]));
        var invalid = new byte[8];
        invalid[6] = (byte) 9;
        assertThrows(IllegalArgumentException.class,
            () -> VoxelWorld.fromMaterialCodes(bounds, invalid));
    }
}
