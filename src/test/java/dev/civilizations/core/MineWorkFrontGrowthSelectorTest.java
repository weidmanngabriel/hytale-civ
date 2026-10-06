package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineWorkFrontGrowthSelectorTest {

    @Test
    void mainFrontIsForcedAfterThreeConsecutiveBranchSelections() {
        Fixture fixture = fixture();

        MineWorkFrontGrowthSelector.Selection selected = MineWorkFrontGrowthSelector.select(
            fixture.network(),
            List.of(fixture.branch(), fixture.main()),
            MineWorkFrontGrowthSelector.MAX_CONSECUTIVE_BRANCH_SELECTIONS,
            44L
        );

        assertEquals(fixture.main(), selected.front());
        assertEquals(0, selected.consecutiveBranchSelections());
    }

    @Test
    void longRunNeverAllowsMoreThanThreeBranchGrowthSelectionsInARow() {
        Fixture fixture = fixture();
        int consecutiveBranches = 0;
        int longestBranchRun = 0;
        int mainSelections = 0;

        for (long seed = 1; seed <= 1_000; seed++) {
            MineWorkFrontGrowthSelector.Selection selected = MineWorkFrontGrowthSelector.select(
                fixture.network(),
                List.of(fixture.main(), fixture.branch()),
                consecutiveBranches,
                seed
            );
            consecutiveBranches = selected.consecutiveBranchSelections();
            if (selected.front().equals(fixture.main())) {
                mainSelections++;
            } else {
                longestBranchRun = Math.max(longestBranchRun, consecutiveBranches);
            }
        }

        assertTrue(longestBranchRun <= MineWorkFrontGrowthSelector.MAX_CONSECUTIVE_BRANCH_SELECTIONS);
        assertTrue(mainSelections >= 250, "Main growth should remain a substantial part of long-run selections");
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

    private static Fixture fixture() {
        UUID mineId = new UUID(1, 1);
        UUID mainId = new UUID(2, 2);
        UUID branchId = new UUID(3, 3);
        MineNetwork network = MineNetwork.create(mineId, mainId, new BlockPosition(0, 80, 0))
            .withTunnel(new MineTunnel(
                branchId,
                MineTunnel.Kind.BRANCH,
                mainId,
                1,
                new BlockPosition(20, 78, 0)
            ));
        MineWorkFront main = new MineWorkFront(
            new UUID(4, 4), mainId, new BlockPosition(50, 70, 0), MineWorkFront.State.OPEN
        );
        MineWorkFront branch = new MineWorkFront(
            new UUID(5, 5), branchId, new BlockPosition(20, 78, 30), MineWorkFront.State.OPEN
        );
        return new Fixture(network, main, branch);
    }

    private record Fixture(MineNetwork network, MineWorkFront main, MineWorkFront branch) {
    }
}
