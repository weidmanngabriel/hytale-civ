package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MineRestartPositionPolicyTest {
    @Test
    void loadedUndergroundMinerSkipsEntranceOnlyWhenActualPositionIsKnownAndEmpty() {
        BlockPosition tunnelBlock = new BlockPosition(10, 20, 30);
        BlockPosition roomBlock = new BlockPosition(11, 20, 30);
        BlockPosition outside = new BlockPosition(100, 20, 30);
        MineTunnelGeometry tunnel = new MineTunnelGeometry(
            MineTunnel.Kind.MAIN, 1L,
            List.of(new MineTunnelGeometry.Slice(
                0, tunnelBlock, 1, 3, Set.of(tunnelBlock), Set.of(tunnelBlock)
            )),
            Set.of(tunnelBlock), Set.of(tunnelBlock), List.of()
        );
        MineRoom room = new MineRoom(
            UUID.randomUUID(), UUID.randomUUID(), MineRoom.Type.REST_ACCOMMODATION,
            roomBlock, MineHeading.EAST, 0, MineRoom.State.BUILT, 0, Set.of()
        );
        MineRoomGeometry geometry = new MineRoomGeometry(
            room, List.of(List.of(roomBlock)), Set.of(roomBlock), roomBlock
        );

        assertTrue(MineRestartPositionPolicy.alreadyInsideMine(
            tunnelBlock, true, List.of(tunnel), List.of(geometry)
        ));
        assertTrue(MineRestartPositionPolicy.alreadyInsideMine(
            roomBlock, true, List.of(tunnel), List.of(geometry)
        ));
        assertFalse(MineRestartPositionPolicy.alreadyInsideMine(
            outside, true, List.of(tunnel), List.of(geometry)
        ));
        assertFalse(MineRestartPositionPolicy.alreadyInsideMine(
            tunnelBlock, false, List.of(tunnel), List.of(geometry)
        ));
    }
}
