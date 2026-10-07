package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MineNormalTaskSelectorTest {

    @Test
    void activeFrontBeatsHigherPriorityWaitingRoom() {
        UUID front = UUID.randomUUID();
        UUID room = UUID.randomUUID();

        MineNormalTaskSelector.Candidate selected = MineNormalTaskSelector.select(
            List.of(
                new MineNormalTaskSelector.Candidate(
                    front, MineNormalTaskSelector.Kind.TUNNEL_FRONT, 4, 1, 2,
                    new BlockPosition(50, 20, 0)
                ),
                new MineNormalTaskSelector.Candidate(
                    room, MineNormalTaskSelector.Kind.ROOM, 8, 0, 3,
                    new BlockPosition(2, 20, 0)
                )
            ),
            new BlockPosition(0, 20, 0)
        );

        assertEquals(front, selected.id());
    }

    @Test
    void roomPriorityWinsWhenOpeningNewNormalWork() {
        UUID branch = UUID.randomUUID();
        UUID room = UUID.randomUUID();

        MineNormalTaskSelector.Candidate selected = MineNormalTaskSelector.select(
            List.of(
                new MineNormalTaskSelector.Candidate(
                    branch, MineNormalTaskSelector.Kind.TUNNEL_FRONT, 6, 0, 2,
                    new BlockPosition(1, 20, 0)
                ),
                new MineNormalTaskSelector.Candidate(
                    room, MineNormalTaskSelector.Kind.ROOM, MineRoomPlanner.ROOM_PRIORITY, 0, 3,
                    new BlockPosition(30, 20, 0)
                )
            ),
            new BlockPosition(0, 20, 0)
        );

        assertEquals(room, selected.id());
    }

    @Test
    void higherPriorityActiveRoomWinsAmongActiveTasks() {
        UUID branch = UUID.randomUUID();
        UUID room = UUID.randomUUID();

        MineNormalTaskSelector.Candidate selected = MineNormalTaskSelector.select(
            List.of(
                new MineNormalTaskSelector.Candidate(
                    branch, MineNormalTaskSelector.Kind.TUNNEL_FRONT, 6, 1, 2,
                    new BlockPosition(1, 20, 0)
                ),
                new MineNormalTaskSelector.Candidate(
                    room, MineNormalTaskSelector.Kind.ROOM, 8, 1, 3,
                    new BlockPosition(30, 20, 0)
                )
            ),
            new BlockPosition(0, 20, 0)
        );

        assertEquals(room, selected.id());
    }
}
