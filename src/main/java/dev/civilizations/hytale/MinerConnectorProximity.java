package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingBounds;

/** Navigation-only proximity to the original connector, without modifying prefab bounds. */
final class MinerConnectorProximity {
    static final double HORIZONTAL_MARGIN_BLOCKS = 2.0;

    private MinerConnectorProximity() {
    }

    static boolean isWithinNavigationRange(BuildingBounds connector, BlockPosition feet) {
        if (connector == null || feet == null) return false;
        double x = feet.x() + 0.5;
        double y = feet.y() + 0.5;
        double z = feet.z() + 0.5;
        return y >= connector.minY() && y <= connector.maxY()
            && x >= connector.minX() - HORIZONTAL_MARGIN_BLOCKS
            && x <= connector.maxX() + HORIZONTAL_MARGIN_BLOCKS
            && z >= connector.minZ() - HORIZONTAL_MARGIN_BLOCKS
            && z <= connector.maxZ() + HORIZONTAL_MARGIN_BLOCKS;
    }
}
