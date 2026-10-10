package dev.civilizations.simulation;

import dev.civilizations.simulation.world.VoxelWorld;
import dev.civilizations.simulation.world.WorldArchive;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/** A real terrain crop from region.civworld.gz. Material categories only; no fabricated terrain. */
final class RealRegionMineFixture {
    private RealRegionMineFixture() {}

    static VoxelWorld load() {
        try {
            var resource = RealRegionMineFixture.class.getResourceAsStream("/fixtures/real-region-miner-materials.b64");
            if (resource == null) throw new IllegalStateException("Missing real terrain fixture");
            byte[] packed;
            try (resource) {
                packed = Base64.getMimeDecoder().decode(resource.readAllBytes());
            }
            byte[] materials;
            try (var in = new GZIPInputStream(new java.io.ByteArrayInputStream(packed))) {
                materials = in.readAllBytes();
            }
            var bounds = new WorldArchive.Bounds(88, 88, -12, 136, 136, 36);
            if (materials.length != bounds.volume()) throw new IllegalStateException("Corrupt real terrain fixture");
            var cells = new ArrayList<WorldArchive.Cell>(materials.length);
            int i = 0;
            for (int x = bounds.minX(); x < bounds.maxX(); x++)
                for (int y = bounds.minY(); y < bounds.maxY(); y++)
                    for (int z = bounds.minZ(); z < bounds.maxZ(); z++) {
                        int kind = Byte.toUnsignedInt(materials[i++]);
                        String fluid = switch (kind) {
                            case 2 -> "WATER";
                            case 3 -> "LAVA";
                            case 4 -> "OTHER";
                            default -> "NONE";
                        };
                        if (kind > 4) throw new IllegalStateException("Invalid material category: " + kind);
                        cells.add(new WorldArchive.Cell(x, y, z,
                            kind == 0 ? "air" : "Rock_Stone", kind >= 2 ? 1 : 0, 0, fluid));
                    }
            return new VoxelWorld(new WorldArchive(WorldArchive.VERSION,
                "real-region-material-crop", bounds, cells));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read real terrain fixture", exception);
        }
    }
}
