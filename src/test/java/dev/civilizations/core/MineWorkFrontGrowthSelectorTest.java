package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class MineWorkFrontGrowthSelectorTest {

    @Test
    void mainFrontIsForcedAfterThreeConsecutiveBranchSelections() {
        UUID mineId = new UUID(1, 1);
        UUID mainId = new UUID(2, 2);
        UUID branchId = new UUID(3, 3);
        MineNetwork network = MineNetwork.create(mineId, mainId, new BlockPosition(0, 80, 0))
            .withTunnel(new MineTunnel(
                branchId,
                MineTunnel.Kind.BRANCH,
                mainId,
                1,
                new BlockPosition(20, 78, 0),
                List.of()
            ));
        MineWorkFront main = new MineWorkFront(
            new UUID(4, 4), mainId, new BlockPosition(50, 70, 0), MineWorkFront.State.OPEN
        );
        MineWorkFront branch = new MineWorkFront(
            new UUID(5, 5), branchId, new BlockPosition(20, 78, 30), MineWorkFront.State.OPEN
        );

        MineWorkFrontGrowthSelector.Selection selected = MineWorkFrontGrowthSelector.select(
            network,
            List.of(branch, main),
            MineWorkFrontGrowthSelector.MAX_CONSECUTIVE_BRANCH_SELECTIONS,
            44L
        );

        assertEquals(main, selected.front());
        assertEquals(0, selected.consecutiveBranchSelections());
    }

    @Test
    void noOpenFrontReturnsNoSelection() {
        UUID mineId = new UUID(1, 1);
        UUID mainId = new UUID(2, 2);
        MineNetwork network = MineNetwork.create(mineId, mainId, new BlockPosition(0, 80, 0));
        MineWorkFront complete = new MineWorkFront(
            new UUID(4, 4), mainId, new BlockPosition(10, 78, 0), MineWorkFront.State.COMPLETE
        );

        MineWorkFrontGrowthSelector.Selection selected = MineWorkFrontGrowthSelector.select(
            network, List.of(complete), 2, 12L
        );

        assertNull(selected.front());
        assertEquals(0, selected.consecutiveBranchSelections());
    }
}
