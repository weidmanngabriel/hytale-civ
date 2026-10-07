package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinePathPlannerTest {

    private static final BlockPosition ORIGIN = new BlockPosition(0, 80, 0);

    @Test
    void headingModelProvidesEightDirectionsAndFortyFiveDegreeTurns() {
        assertEquals(8, MineHeading.values().length);
        assertEquals(MineHeading.NORTH_WEST, MineHeading.NORTH.left45());
        assertEquals(MineHeading.NORTH_EAST, MineHeading.NORTH.right45());
        assertEquals(MineHeading.SOUTH, MineHeading.NORTH.opposite());
    }

    @Test
    void sameSeedProducesTheSamePlan() {
        MineTunnelPath first = MinePathPlanner.plan(
            MineTunnel.Kind.MAIN, ORIGIN, MineHeading.NORTH, 240, 123456789L
        );
        MineTunnelPath second = MinePathPlanner.plan(
            MineTunnel.Kind.MAIN, ORIGIN, MineHeading.NORTH, 240, 123456789L
        );

        assertEquals(first, second);
    }

    @Test
    void mainTunnelUsesSixToEightBlockFormRangeAndChangesGradually() {
        MineTunnelPath path = MinePathPlanner.plan(
            MineTunnel.Kind.MAIN, ORIGIN, MineHeading.NORTH, 400, 42L
        );

        for (MineFormPhase phase : path.phases()) {
            assertTrue(phase.startWidth() >= 6.0 && phase.startWidth() <= 8.0);
            assertTrue(phase.targetWidth() >= 6.0 && phase.targetWidth() <= 8.0);
            assertTrue(phase.startHeight() >= 6.0 && phase.startHeight() <= 8.0);
            assertTrue(phase.targetHeight() >= 6.0 && phase.targetHeight() <= 8.0);
            assertTrue(Math.abs(phase.targetWidth() - phase.startWidth()) <= 1.0);
            assertTrue(Math.abs(phase.targetHeight() - phase.startHeight()) <= 1.0);
        }

        for (MinePathPoint point : path.points()) {
            assertTrue(point.width() >= 6.0 && point.width() <= 8.0);
            assertTrue(point.height() >= 6.0 && point.height() <= 8.0);
        }
    }

    @Test
    void branchUsesThreeToFiveBlockFormRange() {
        MineTunnelPath path = MinePathPlanner.plan(
            MineTunnel.Kind.BRANCH, ORIGIN, MineHeading.EAST, 300, 84L
        );

        for (MinePathPoint point : path.points()) {
            assertTrue(point.width() >= 3.0 && point.width() <= 5.0);
            assertTrue(point.height() >= 3.0 && point.height() <= 5.0);
        }
    }

    @Test
    void phaseHeadingChangesNeverJumpMoreThanFortyFiveDegrees() {
        MineTunnelPath path = MinePathPlanner.plan(
            MineTunnel.Kind.MAIN, ORIGIN, MineHeading.NORTH, 700, 987654321L
        );

        for (MineFormPhase phase : path.phases()) {
            assertTrue(Math.abs(MineHeading.shortestSignedAngleDegrees(
                phase.startHeading(), phase.targetHeading()
            )) <= 45.000001);
        }
    }

    @Test
    void softBoundaryStronglyDiscouragesContinuingOutward() {
        double east = MinePathPlanner.headingWeight(
            MineTunnel.Kind.MAIN,
            MineHeading.EAST,
            MineHeading.EAST,
            245.0,
            0.0,
            0.0,
            0.0
        );
        double northEast = MinePathPlanner.headingWeight(
            MineTunnel.Kind.MAIN,
            MineHeading.EAST,
            MineHeading.NORTH_EAST,
            245.0,
            0.0,
            0.0,
            0.0
        );

        assertTrue(east < northEast);
        assertTrue(east > 0.0, "Boundary steering must remain soft rather than becoming a wall.");
    }

    @Test
    void mainTunnelTrendsDownwardAcrossManyDeterministicPlans() {
        double totalDelta = 0.0;
        for (long seed = 0; seed < 100; seed++) {
            MineTunnelPath path = MinePathPlanner.plan(
                MineTunnel.Kind.MAIN, ORIGIN, MineHeading.NORTH, 500, seed
            );
            totalDelta += path.points().get(path.points().size() - 1).y() - ORIGIN.y();
        }

        assertTrue(totalDelta < -1500.0);
    }

    @Test
    void branchVerticalTendencyIsApproximatelyBalancedAcrossManyPlans() {
        double totalDelta = 0.0;
        for (long seed = 0; seed < 200; seed++) {
            MineTunnelPath path = MinePathPlanner.plan(
                MineTunnel.Kind.BRANCH, ORIGIN, MineHeading.NORTH, 300, seed
            );
            totalDelta += path.points().get(path.points().size() - 1).y() - ORIGIN.y();
        }

        assertTrue(Math.abs(totalDelta) < 250.0);
    }

    @Test
    void truncatedFinalPhaseKeepsCurrentFormInsteadOfCompressingANewTurn() {
        MineTunnelPath path = MinePathPlanner.plan(
            MineTunnel.Kind.MAIN, ORIGIN, MineHeading.NORTH_EAST, 220, 25L
        );
        MineFormPhase last = path.phases().getLast();

        assertTrue(last.lengthBlocks() < 10, "Fixture should end with a truncated main phase");
        assertEquals(last.startHeading(), last.targetHeading());
        assertEquals(last.startWidth(), last.targetWidth());
        assertEquals(last.startHeight(), last.targetHeight());
        assertEquals(last.startLateralOffset(), last.targetLateralOffset());
        assertEquals(0, last.verticalDeltaBlocks());
    }

    @Test
    void lateralDriftChangesAtMostOneBlockPerPhaseAndDoesNotJitterEveryBlock() {
        MineTunnelPath path = MinePathPlanner.plan(
            MineTunnel.Kind.MAIN, ORIGIN, MineHeading.SOUTH_EAST, 500, 9988L
        );

        Set<Double> phaseTargets = new HashSet<>();
        for (MineFormPhase phase : path.phases()) {
            assertTrue(Math.abs(phase.targetLateralOffset() - phase.startLateralOffset()) <= 1.0);
            phaseTargets.add(phase.targetLateralOffset());
        }
        assertTrue(phaseTargets.size() > 1, "Expected the deterministic fixture to exercise lateral drift.");

        for (int i = 1; i < path.points().size(); i++) {
            MinePathPoint previous = path.points().get(i - 1);
            MinePathPoint current = path.points().get(i);
            assertTrue(Math.abs(current.x() - previous.x()) < 1.6);
            assertTrue(Math.abs(current.z() - previous.z()) < 1.6);
        }
    }
}
