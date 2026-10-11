package dev.civilizations.simulation;

import dev.civilizations.simulation.world.VoxelWorld;
import dev.civilizations.simulation.world.WorldArchive;

import java.io.IOException;
import java.util.Base64;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/** Real exported terrain materials, loaded directly without per-cell objects. */
final class RealRegionMineFixture {
    private RealRegionMineFixture() {}

    static VoxelWorld load() {
        try {
            var resource = RealRegionMineFixture.class.getResourceAsStream("/fixtures/full-region-materials.b64");
            if (resource == null) throw new IllegalStateException("Missing real terrain fixture");
            byte[] packed;
            try (resource) {
                packed = Base64.getMimeDecoder().decode(resource.readAllBytes());
            }
            byte[] materials;
            try (var in = new GZIPInputStream(new java.io.ByteArrayInputStream(packed))) {
                materials = in.readAllBytes();
            }
            var bounds = new WorldArchive.Bounds(56, 8, -41, 181, 148, 66);
            if (materials.length != bounds.volume()) throw new IllegalStateException("Corrupt full-region terrain fixture");
            try {
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(materials));
                if (!digest.equals("34cc1622257266142e946dcd86583e4347928fd99a9e01d8d6c16867722aae35"))
                    throw new IllegalStateException("Full-region fixture checksum mismatch: " + digest);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
            return VoxelWorld.fromMaterialCodes(bounds, materials);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read real terrain fixture", exception);
        }
    }
}
