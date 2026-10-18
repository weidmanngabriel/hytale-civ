package dev.civilizations.simulation.world;

import dev.civilizations.core.BlockPosition;
import java.util.ArrayList;
import java.util.List;

/**
 * Compact view of the existing raw Hytale export, not a second importer.
 * Conservatively identifies exposed observed grass/dirt and pre-existing trunks.
 * A valid surface here is NOT proof of native sapling-placement eligibility.
 */
public final class ForesterSnapshotSurface {
    private final WorldArchive.Bounds bounds;
    private final int depth;
    private final int[] highestY;
    private final byte[] surfaceKind;
    private final List<BlockPosition> trunks;

    public ForesterSnapshotSurface(WorldArchive archive) {
        bounds = archive.bounds();
        depth = bounds.maxZ() - bounds.minZ();
        int size = (bounds.maxX() - bounds.minX()) * depth;
        highestY = new int[size];
        surfaceKind = new byte[size];
        java.util.Arrays.fill(highestY, Integer.MIN_VALUE);
        List<BlockPosition> found = new ArrayList<>();
        for (var cell : archive.cells()) {
            int i = index(cell.x(), cell.z());
            String key = cell.blockKey();
            if (key.startsWith("Wood_") && key.contains("_Trunk"))
                found.add(new BlockPosition(cell.x(), cell.y(), cell.z()));
            if (!"air".equals(key) || !"NONE".equals(cell.fluidCategory())) {
                if (cell.y() > highestY[i]) {
                    highestY[i] = cell.y();
                    surfaceKind[i] = (byte) (("Soil_Grass".equals(key)
                        || "Soil_Grass_Full".equals(key) || "Soil_Grass_Deep".equals(key)
                        || "Soil_Dirt".equals(key)) && "NONE".equals(cell.fluidCategory()) ? 1 : 0);
                }
            }
        }
        trunks = List.copyOf(found);
    }

    private int index(int x, int z) {
        return (x - bounds.minX()) * depth + z - bounds.minZ();
    }

    public BlockPosition observedExposedSoil(int x, int z) {
        if (x < bounds.minX() || x >= bounds.maxX() || z < bounds.minZ() || z >= bounds.maxZ()) return null;
        int i = index(x,z);
        if (surfaceKind[i] != 1 || highestY[i] + 2 >= bounds.maxY()) return null;
        return new BlockPosition(x, highestY[i] + 1, z);
    }

    public List<BlockPosition> observedTrunkBlocks() { return trunks; }
}
