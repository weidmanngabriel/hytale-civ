package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineNetworkGrowthPlannerTest {

    private static final UUID MINE_ID = new UUID(7, 11);
    private static final BlockPosition ORIGIN = new BlockPosition(0, 80, 0);

    @Test
    void sameSeedProducesSameNetworkPlan() {
        MineNetworkGrowthPlanner.Plan first = MineNetworkGrowthPlanner.plan(
            MINE_ID, ORIGIN, MineHeading.NORTH, 320, 80, 123456789L
        );
        MineNetworkGrowthPlanner.Plan second = MineNetworkGrowthPlanner.plan(
            MINE_ID, ORIGIN, MineHeading.NORTH, 320, 80, 123456789L
        );

        assertEquals(first, second);
    }

    @Test
    void longRunKeepsOneMainTunnelAndCreatesNestedBranches() {
        boolean sawNestedBranch = false;
        boolean sawLongBranch = false;
        boolean sawDifferentLengths = false;

        for (long seed = 1; seed <= 20; seed++) {
            MineNetworkGrowthPlanner.Plan plan = MineNetworkGrowthPlanner.plan(
                MINE_ID, ORIGIN, MineHeading.NORTH, 420, 120, seed
            );

            assertEquals(1, plan.network().tunnels().stream()
                .filter(tunnel -> tunnel.kind() == MineTunnel.Kind.MAIN)
                .count());
            assertTrue(plan.mainTunnel().path().points().size() >= 421);
            assertTrue(plan.tunnels().size() <= 120);

            Set<Integer> lengths = new HashSet<>();
            for (MineNetworkGrowthPlanner.PlannedTunnel planned : plan.tunnels()) {
                MineTunnel tunnel = planned.tunnel();
                assertEquals(tunnel.kind() == MineTunnel.Kind.MAIN ? 0 : tunnel.branchDepth(),
                    tunnel.kind() == MineTunnel.Kind.MAIN ? 0 : plan.network().tunnel(tunnel.parentTunnelId()).branchDepth() + 1);
                if (tunnel.kind() == MineTunnel.Kind.BRANCH) {
                    int length = planned.path().points().size() - 1;
                    lengths.add(length);
                    sawNestedBranch |= tunnel.branchDepth() >= 2;
                    sawLongBranch |= length >= 100;
                }
            }
            sawDifferentLengths |= lengths.size() >= 2;
        }

        assertTrue(sawNestedBranch, "Expected nested side branches across deterministic long-run seeds");
        assertTrue(sawLongBranch, "Expected occasional long side branches");
        assertTrue(sawDifferentLengths, "Expected decreasing continuation rolls to produce varied branch lengths");
    }

    @Test
    void branchProbabilityFallsWithDepthAndContinuationNeverFallsBelowFloor() {
        assertEquals(0.15, MineNetworkGrowthPlanner.branchChance(0), 0.000001);
        assertEquals(0.30, MineNetworkGrowthPlanner.branchChance(1), 0.000001);
        assertTrue(MineNetworkGrowthPlanner.branchChance(2) < MineNetworkGrowthPlanner.branchChance(1));
        assertTrue(MineNetworkGrowthPlanner.branchChance(4) < MineNetworkGrowthPlanner.branchChance(3));

        for (long seed = 0; seed < 100; seed++) {
            int length = MineNetworkGrowthPlanner.sampleBranchLength(new java.util.SplittableRandom(seed));
            assertTrue(length >= 16);
            assertEquals(0, length % 8);
            assertTrue(length <= MinePathPlanner.FOOTPRINT_SIZE_BLOCKS + 8);
        }
    }

    @Test
    void branchStartsRespectConfiguredSpacing() {
        MineNetworkGrowthPlanner.Plan plan = MineNetworkGrowthPlanner.plan(
            MINE_ID, ORIGIN, MineHeading.EAST, 500, 160, 99887766L
        );

        var starts = plan.tunnels().stream()
            .filter(tunnel -> tunnel.tunnel().kind() == MineTunnel.Kind.BRANCH)
            .map(tunnel -> tunnel.tunnel().origin())
            .toList();
        double minimumSquared = (double) MineNetworkGrowthPlanner.MIN_BRANCH_START_SPACING_BLOCKS
            * MineNetworkGrowthPlanner.MIN_BRANCH_START_SPACING_BLOCKS;

        for (int i = 0; i < starts.size(); i++) {
            for (int j = i + 1; j < starts.size(); j++) {
                BlockPosition a = starts.get(i);
                BlockPosition b = starts.get(j);
                long dx = (long) a.x() - b.x();
                long dy = (long) a.y() - b.y();
                long dz = (long) a.z() - b.z();
                assertTrue((double) dx * dx + (double) dy * dy + (double) dz * dz >= minimumSquared);
            }
        }
    }
}
