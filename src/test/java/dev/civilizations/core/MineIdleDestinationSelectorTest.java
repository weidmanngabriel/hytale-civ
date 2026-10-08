package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MineIdleDestinationSelectorTest {
    private static MineRoom room(int x, MineRoom.Type type, MineRoom.State state) {
        return new MineRoom(
            UUID.randomUUID(), UUID.randomUUID(), type,
            new BlockPosition(x, 10, 0), MineHeading.EAST,
            0, state, 0, Set.of()
        );
    }

    @Test
    void choosesNearestFinishedAccommodationOnly() {
        MineRoom far = room(50, MineRoom.Type.REST_ACCOMMODATION, MineRoom.State.BUILT);
        MineRoom near = room(10, MineRoom.Type.REST_ACCOMMODATION, MineRoom.State.BUILT);
        MineRoom unfinished = room(1, MineRoom.Type.REST_ACCOMMODATION, MineRoom.State.EXCAVATING);
        MineRoom storage = room(2, MineRoom.Type.MATERIAL_STORAGE, MineRoom.State.BUILT);
        assertEquals(near, MineIdleDestinationSelector.select(
            List.of(far, unfinished, storage, near), 0, 10, 0,
            null, Set.of(), room -> true
        ));
    }

    @Test
    void staysInExistingRoomUnlessRemovedOrUnusable() {
        MineRoom far = room(50, MineRoom.Type.REST_ACCOMMODATION, MineRoom.State.BUILT);
        MineRoom near = room(10, MineRoom.Type.REST_ACCOMMODATION, MineRoom.State.BUILT);
        assertEquals(far, MineIdleDestinationSelector.select(
            List.of(far, near), 0, 10, 0, far.id(), Set.of(), room -> true
        ));
        assertEquals(near, MineIdleDestinationSelector.select(
            List.of(far, near), 0, 10, 0, far.id(), Set.of(far.id()), room -> true
        ));
    }

    @Test
    void returnsNullWhenNoAccommodationCanBeUsed() {
        MineRoom room = room(4, MineRoom.Type.REST_ACCOMMODATION, MineRoom.State.BUILT);
        assertNull(MineIdleDestinationSelector.select(
            List.of(room), 0, 10, 0, null, Set.of(), candidate -> false
        ));
    }
}
