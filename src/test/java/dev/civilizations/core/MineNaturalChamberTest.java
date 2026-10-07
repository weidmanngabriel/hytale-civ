package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineNaturalChamberTest {

    @Test
    void integratedNaturalChamberIsTerminalWithoutBuildWork() {
        MineRoom room = new MineRoom(
            UUID.randomUUID(),
            UUID.randomUUID(),
            MineRoom.Type.LARGE_NATURAL_CHAMBER,
            new BlockPosition(20, 10, 20),
            MineHeading.EAST,
            12,
            MineRoom.State.NATURAL_INTEGRATED,
            0,
            Set.of()
        );

        assertTrue(room.terminal());
    }
}
