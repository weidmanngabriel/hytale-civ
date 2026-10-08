package dev.civilizations.core;

import java.util.Collection;

/** Tests whether an already loaded NPC stands in a known excavated mine volume. */
public final class MineRestartPositionPolicy {
    private MineRestartPositionPolicy() {}

    public static boolean alreadyInsideMine(
        BlockPosition feet,
        boolean verifiedEmptyWorldBlock,
        Collection<MineTunnelGeometry> tunnels,
        Collection<MineRoomGeometry> rooms
    ) {
        if (feet == null || !verifiedEmptyWorldBlock || tunnels == null || rooms == null) {
            return false;
        }
        for (MineTunnelGeometry tunnel : tunnels) {
            if (tunnel.excavationBlocks().contains(feet)) return true;
        }
        for (MineRoomGeometry room : rooms) {
            if (room.excavationBlocks().contains(feet)) return true;
        }
        return false;
    }
}
