package dev.civilizations.simulation;

import dev.civilizations.simulation.world.VoxelWorld;
import dev.civilizations.simulation.world.WorldArchive;

import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/** Real exported terrain materials, loaded directly without per-cell objects. */
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
            return VoxelWorld.fromMaterialCodes(bounds, materials);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read real terrain fixture", exception);
        }
    }
}
