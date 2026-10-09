package dev.civilizations.core;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Pure placement intent for the periodic finished stone arch pairs in dwarven tunnels. */
public final class DwarvenMineFinishPlan {
    public static final int INTERVAL = 8;
    private DwarvenMineFinishPlan() {
    }

    /**
     * Decoration is placed only on completed slices, leaving several more excavated
     * slices in front as safety clearance. The engine adapter checks actual loaded blocks.
     */
    public static Feature at(UUID tunnelId, MineTunnelGeometry geometry, int index) {
        if (tunnelId == null || geometry == null || index < INTERVAL
            || index % INTERVAL != 0 || index >= geometry.slices().size()) return null;
        if (geometry.tunnelKind() == MineTunnel.Kind.MAIN
            && index % DwarvenMinePlanner.CROSSING_SPACING == 0) return null;

        BlockPosition previous = geometry.slices().get(index - 1).floorCenter();
        BlockPosition current = geometry.slices().get(index).floorCenter();
        int dx = Integer.compare(current.x() - previous.x(), 0);
        int dz = Integer.compare(current.z() - previous.z(), 0);
        if (Math.abs(dx) + Math.abs(dz) != 1) return null;

        int half = geometry.slices().get(index).widthBlocks() / 2;
        BlockPosition left = new BlockPosition(
            current.x() - dz * half, current.y(), current.z() + dx * half
        );
        BlockPosition right = new BlockPosition(
            current.x() + dz * half, current.y(), current.z() - dx * half
        );
        UUID id = UUID.nameUUIDFromBytes(
            ("civ-dwarf-finish-v1:" + tunnelId + ":" + index)
                .getBytes(StandardCharsets.UTF_8)
        );
        return new Feature(id, index, left, right);
    }

    public record Feature(UUID id, int sliceIndex, BlockPosition left, BlockPosition right) {
    }
}
