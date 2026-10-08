package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    @Test
    void waitingTasksAgeOnlyWhenNewWaitingWorkIsOpened() {
        UUID chosen = UUID.randomUUID();
        UUID decoration = UUID.randomUUID();

        MineNormalTaskSelector.Selection first = MineNormalTaskSelector.selectWithAging(
            List.of(
                new MineNormalTaskSelector.Candidate(
                    chosen, MineNormalTaskSelector.Kind.TUNNEL_FRONT, 6, 0, 2,
                    new BlockPosition(0, 20, 0)
                ),
                new MineNormalTaskSelector.Candidate(
                    decoration, MineNormalTaskSelector.Kind.INFRASTRUCTURE, 2, 0, 1,
                    new BlockPosition(20, 20, 0)
                )
            ),
            new BlockPosition(0, 20, 0),
            Map.of()
        );

        assertEquals(chosen, first.selected().id());
        assertTrue(first.openedWaitingWork());
        assertEquals(1, first.updatedPriorityBonuses().get(decoration));

        MineNormalTaskSelector.Selection active = MineNormalTaskSelector.selectWithAging(
            List.of(
                new MineNormalTaskSelector.Candidate(
                    chosen, MineNormalTaskSelector.Kind.TUNNEL_FRONT, 6, 1, 2,
                    new BlockPosition(0, 20, 0)
                ),
                new MineNormalTaskSelector.Candidate(
                    decoration, MineNormalTaskSelector.Kind.INFRASTRUCTURE, 2, 0, 1,
                    new BlockPosition(20, 20, 0)
                )
            ),
            new BlockPosition(0, 20, 0),
            first.updatedPriorityBonuses()
        );

        assertEquals(chosen, active.selected().id());
        assertFalse(active.openedWaitingWork());
        assertEquals(1, active.updatedPriorityBonuses().get(decoration));
    }

    @Test
    void decorationEventuallyOvertakesFreshMainTunnelButNeverReachesPriorityTen() {
        UUID main = UUID.randomUUID();
        UUID decoration = UUID.randomUUID();
        Map<UUID, Integer> bonuses = Map.of();

        MineNormalTaskSelector.Candidate selected = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            MineNormalTaskSelector.Selection selection = MineNormalTaskSelector.selectWithAging(
                List.of(
                    new MineNormalTaskSelector.Candidate(
                        main, MineNormalTaskSelector.Kind.TUNNEL_FRONT, 4, 0, 2,
                        new BlockPosition(0, 20, 0)
                    ),
                    new MineNormalTaskSelector.Candidate(
                        decoration, MineNormalTaskSelector.Kind.INFRASTRUCTURE, 2, 0, 1,
                        new BlockPosition(10, 20, 0)
                    )
                ),
                new BlockPosition(0, 20, 0),
                bonuses
            );
            selected = selection.selected();
            bonuses = selection.updatedPriorityBonuses();
            if (selected.id().equals(decoration)) break;
        }

        assertEquals(decoration, selected.id());
        assertEquals(
            9,
            MineNormalTaskSelector.effectivePriority(
                new MineNormalTaskSelector.Candidate(
                    decoration, MineNormalTaskSelector.Kind.INFRASTRUCTURE, 2, 0, 1,
                    new BlockPosition(10, 20, 0)
                ),
                Map.of(decoration, 99)
            )
        );
    }

}
