package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class MineFrontTaskSchedulerTest {

    @Test
    void opensUnoccupiedFrontBeforeJoiningAnOccupiedFront() {
        Fixture fixture = fixture();

        MineWorkFront selected = MineFrontTaskScheduler.select(
            fixture.network,
            List.of(fixture.mainFront, fixture.branchFront),
            Map.of(fixture.mainFront.id(), 1),
            new BlockPosition(18, 10, 0)
        );

        assertEquals(fixture.branchFront.id(), selected.id());
    }

    @Test
    void opensBranchBeforeMainWhenNoFrontIsActive() {
        Fixture fixture = fixture();

        MineWorkFront selected = MineFrontTaskScheduler.select(
            fixture.network,
            List.of(fixture.mainFront, fixture.branchFront),
            Map.of(),
            new BlockPosition(0, 10, 0)
        );

        assertEquals(fixture.branchFront.id(), selected.id());
    }

    @Test
    void joinsOccupiedFrontOnlyWhenNoUnoccupiedFrontRemains() {
        Fixture fixture = fixture();

        MineWorkFront selected = MineFrontTaskScheduler.select(
            fixture.network,
            List.of(fixture.mainFront),
            Map.of(fixture.mainFront.id(), 1),
            new BlockPosition(0, 10, 0)
        );

        assertEquals(fixture.mainFront.id(), selected.id());
    }

    @Test
    void blockedAndCompleteFrontsAreNotSelectable() {
        Fixture fixture = fixture();
        MineWorkFront blocked = new MineWorkFront(
            fixture.branchFront.id(), fixture.branchFront.tunnelId(), fixture.branchFront.position(),
            MineWorkFront.State.BLOCKED
        );
        MineWorkFront complete = new MineWorkFront(
            fixture.mainFront.id(), fixture.mainFront.tunnelId(), fixture.mainFront.position(),
            MineWorkFront.State.COMPLETE
        );

        assertNull(MineFrontTaskScheduler.select(
            fixture.network,
            List.of(blocked, complete),
            Map.of(),
            new BlockPosition(0, 10, 0)
        ));
    }

    private static Fixture fixture() {
        UUID mineId = UUID.randomUUID();
        UUID mainId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        MineTunnel main = new MineTunnel(
            mainId, MineTunnel.Kind.MAIN, null, 0, new BlockPosition(0, 10, 0)
        );
        MineTunnel branch = new MineTunnel(
            branchId, MineTunnel.Kind.BRANCH, mainId, 1, new BlockPosition(20, 10, 0)
        );
        MineWorkFront mainFront = new MineWorkFront(
            UUID.randomUUID(), mainId, new BlockPosition(10, 10, 0), MineWorkFront.State.OPEN
        );
        MineWorkFront branchFront = new MineWorkFront(
            UUID.randomUUID(), branchId, new BlockPosition(20, 10, 0), MineWorkFront.State.OPEN
        );
        MineNetwork network = new MineNetwork(
            mineId, mainId, List.of(main, branch), List.of(), List.of(mainFront, branchFront), List.of()
        );
        return new Fixture(network, mainFront, branchFront);
    }

    private record Fixture(MineNetwork network, MineWorkFront mainFront, MineWorkFront branchFront) {
    }
}
