package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineInfrastructurePlannerTest {

    @Test
    void plansSupportsWithinSixToTenBlocksAndKeepsMinimumOpening() {
        UUID tunnelId = UUID.randomUUID();
        MineTunnelGeometry geometry = straightGeometry(MineTunnel.Kind.MAIN, 48, 6, 5, 1234L);

        List<MineInfrastructureTask> supports = MineInfrastructurePlanner.plan(tunnelId, geometry)
            .stream()
            .filter(task -> task.type() == MineInfrastructureTask.Type.BUILD_SUPPORT)
            .toList();

        assertFalse(supports.isEmpty());
        int previous = 0;
        for (MineInfrastructureTask support : supports) {
            int delta = support.startSliceIndex() - previous;
            assertTrue(delta >= 3 && delta <= 13,
                "support shift may move a 6-10 target by at most three slices");
            assertEquals(5, support.priority());
            previous = support.startSliceIndex();
        }
    }

    @Test
    void supportDiagnosticsDoNotChangePlanAndExplainPlannedSupports() {
        UUID mineId = UUID.randomUUID();
        UUID tunnelId = UUID.randomUUID();
        MineTunnelGeometry geometry = straightGeometry(MineTunnel.Kind.MAIN, 48, 6, 5, 1234L);

        List<MineInfrastructureTask> baseline = MineInfrastructurePlanner.plan(tunnelId, geometry);
        List<MineDecisionEvent> events = new ArrayList<>();
        MineDecisionSink sink = new MineDecisionSink() {
            @Override
            public boolean enabled(MineDecisionCategory category) {
                return true;
            }

            @Override
            public void record(MineDecisionEvent event) {
                events.add(event);
            }
        };

        List<MineInfrastructureTask> diagnosed =
            MineInfrastructurePlanner.plan(mineId, tunnelId, geometry, sink);

        assertEquals(baseline, diagnosed);
        assertTrue(events.stream().anyMatch(event ->
            event.type().equals("BUILD_SUPPORT_PLANNED")
                && event.category() == MineDecisionCategory.PLANNING
        ));
        assertTrue(events.stream().allMatch(event -> event.mineId().equals(mineId)));
    }

    @Test
    void branchSupportsAllowSimplerThreeWideClearOpening() {
        MineTunnelGeometry geometry = straightGeometry(MineTunnel.Kind.BRANCH, 48, 5, 4, 55L);

        List<MineInfrastructureTask> supports = MineInfrastructurePlanner.plan(
            UUID.randomUUID(), geometry
        ).stream()
            .filter(task -> task.type() == MineInfrastructureTask.Type.BUILD_SUPPORT)
            .toList();

        assertFalse(supports.isEmpty());
    }

    @Test
    void plansDenserRicherDecorationOnMainThanBranches() {
        UUID mainId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        MineTunnelGeometry main = straightGeometry(MineTunnel.Kind.MAIN, 180, 7, 7, 777L);
        MineTunnelGeometry branch = straightGeometry(MineTunnel.Kind.BRANCH, 180, 5, 4, 777L);

        List<MineInfrastructureTask> mainDecor = MineInfrastructurePlanner.plan(mainId, main).stream()
            .filter(task -> task.type() == MineInfrastructureTask.Type.PLACE_DECORATION)
            .toList();
        List<MineInfrastructureTask> branchDecor = MineInfrastructurePlanner.plan(branchId, branch).stream()
            .filter(task -> task.type() == MineInfrastructureTask.Type.PLACE_DECORATION)
            .toList();

        assertFalse(mainDecor.isEmpty());
        assertFalse(branchDecor.isEmpty());
        assertTrue(mainDecor.size() > branchDecor.size());
        assertTrue(mainDecor.stream().allMatch(task -> task.priority() == 2));
        assertTrue(branchDecor.stream().noneMatch(task ->
            task.decorationKind() == MineInfrastructureTask.DecorationKind.HANGING_CHAIN
                || task.decorationKind() == MineInfrastructureTask.DecorationKind.HANGING_LANTERN));
        assertTrue(mainDecor.stream().noneMatch(task ->
            task.decorationKind() == MineInfrastructureTask.DecorationKind.TIMBER_PILE));
        assertTrue(branchDecor.stream().noneMatch(task ->
            task.decorationKind() == MineInfrastructureTask.DecorationKind.TIMBER_PILE));
    }

    @Test
    void decorationDiagnosticsDoNotChangePlanAndExplainPlanningDecisions() {
        UUID mineId = UUID.randomUUID();
        UUID tunnelId = UUID.randomUUID();
        MineTunnelGeometry geometry = straightGeometry(MineTunnel.Kind.MAIN, 140, 7, 7, 991L);

        List<MineInfrastructureTask> baseline = MineInfrastructurePlanner.plan(tunnelId, geometry);
        List<MineDecisionEvent> events = new ArrayList<>();
        MineDecisionSink sink = new MineDecisionSink() {
            @Override
            public boolean enabled(MineDecisionCategory category) {
                return true;
            }

            @Override
            public void record(MineDecisionEvent event) {
                events.add(event);
            }
        };

        List<MineInfrastructureTask> diagnosed =
            MineInfrastructurePlanner.plan(mineId, tunnelId, geometry, sink);

        assertEquals(baseline, diagnosed);
        assertTrue(events.stream().anyMatch(event ->
            event.type().equals("DECORATION_PLANNED")
                && event.category() == MineDecisionCategory.PLANNING
        ));
        assertTrue(events.stream().anyMatch(event ->
            event.type().equals("DECORATION_SKIPPED")
                && event.category() == MineDecisionCategory.PLANNING
        ));
    }

    @Test
    void decorationKeepsDistanceFromPlannedInfrastructureSlices() {
        MineTunnelGeometry geometry = straightGeometry(MineTunnel.Kind.MAIN, 180, 7, 7, 12345L);
        List<MineInfrastructureTask> tasks = MineInfrastructurePlanner.plan(UUID.randomUUID(), geometry);
        List<MineInfrastructureTask> decoration = tasks.stream()
            .filter(MineInfrastructureTask::decoration)
            .toList();
        List<MineInfrastructureTask> infrastructure = tasks.stream()
            .filter(task -> !task.decoration())
            .toList();

        for (MineInfrastructureTask decor : decoration) {
            assertTrue(infrastructure.stream().allMatch(other ->
                Math.abs(other.startSliceIndex() - decor.startSliceIndex()) > 1
                    || other.type() == MineInfrastructureTask.Type.BUILD_BRIDGE));
        }
    }

    @Test
    void decorationPlanIsDeterministicForSameTunnelAndGeometry() {
        UUID tunnelId = UUID.randomUUID();
        MineTunnelGeometry geometry = straightGeometry(MineTunnel.Kind.MAIN, 140, 7, 7, 991L);

        List<MineInfrastructureTask> first = MineInfrastructurePlanner.plan(tunnelId, geometry).stream()
            .filter(MineInfrastructureTask::decoration)
            .toList();
        List<MineInfrastructureTask> second = MineInfrastructurePlanner.plan(tunnelId, geometry).stream()
            .filter(MineInfrastructureTask::decoration)
            .toList();

        assertEquals(first, second);
    }

    @Test
    void leavesHeightTransitionsIntactButDoesNotScheduleDisabledStairs() {
        List<MineTunnelGeometry.Slice> slices = new ArrayList<>();
        Set<BlockPosition> all = new LinkedHashSet<>();
        Set<BlockPosition> nav = new LinkedHashSet<>();
        for (int i = 0; i < 7; i++) {
            int y = i < 2 ? 0 : Math.min(3, i - 1);
            MineTunnelGeometry.Slice slice = slice(i, new BlockPosition(i, y, 0), 6, 5);
            slices.add(slice);
            all.addAll(slice.excavationBlocks());
            nav.addAll(slice.navigationCoreBlocks());
        }

        List<MineTunnelGeometry.StepTransition> steps = List.of(
            new MineTunnelGeometry.StepTransition(1, 2, slices.get(1).floorCenter(), slices.get(2).floorCenter()),
            new MineTunnelGeometry.StepTransition(2, 3, slices.get(2).floorCenter(), slices.get(3).floorCenter()),
            new MineTunnelGeometry.StepTransition(3, 4, slices.get(3).floorCenter(), slices.get(4).floorCenter())
        );
        MineTunnelGeometry geometry = new MineTunnelGeometry(
            MineTunnel.Kind.MAIN, 99L, slices, all, nav, steps
        );

        List<MineInfrastructureTask> stairs = MineInfrastructurePlanner.plan(UUID.randomUUID(), geometry)
            .stream()
            .filter(task -> task.type() == MineInfrastructureTask.Type.BUILD_STEP)
            .toList();

        assertFalse(MineInfrastructurePlanner.ENABLE_MINE_STEPS);
        assertTrue(stairs.isEmpty());
        assertEquals(3, geometry.stepTransitions().size());
    }

    @Test
    void recoveryStepTaskIsDeterministicWithoutEnablingEagerStairs() {
        UUID tunnelId = UUID.randomUUID();
        var transition = new MineTunnelGeometry.StepTransition(
            2, 3, new BlockPosition(2, 10, 0), new BlockPosition(3, 11, 0)
        );
        MineInfrastructureTask first = MineInfrastructurePlanner.recoveryStepTask(
            tunnelId, transition
        );
        MineInfrastructureTask second = MineInfrastructurePlanner.recoveryStepTask(
            tunnelId, transition
        );
        assertEquals(first, second);
        assertEquals(MineInfrastructureTask.Type.BUILD_STEP, first.type());
        assertEquals(10, first.priority());
        assertFalse(MineInfrastructurePlanner.ENABLE_MINE_STEPS);
    }

    @Test
    void bridgeFactoryCreatesDeterministicMandatoryTask() {
        UUID tunnelId = UUID.randomUUID();
        BlockPosition anchor = new BlockPosition(12, 4, -3);

        MineInfrastructureTask first =
            MineInfrastructurePlanner.bridgeTask(tunnelId, 8, 15, anchor);
        MineInfrastructureTask second =
            MineInfrastructurePlanner.bridgeTask(tunnelId, 8, 15, anchor);

        assertEquals(first.id(), second.id());
        assertEquals(MineInfrastructureTask.Type.BUILD_BRIDGE, first.type());
        assertTrue(first.mandatory());
    }

    private static MineTunnelGeometry straightGeometry(
        MineTunnel.Kind kind,
        int length,
        int width,
        int height,
        long seed
    ) {
        List<MineTunnelGeometry.Slice> slices = new ArrayList<>();
        Set<BlockPosition> all = new LinkedHashSet<>();
        Set<BlockPosition> nav = new LinkedHashSet<>();
        for (int i = 0; i < length; i++) {
            MineTunnelGeometry.Slice slice = slice(i, new BlockPosition(i, 0, 0), width, height);
            slices.add(slice);
            all.addAll(slice.excavationBlocks());
            nav.addAll(slice.navigationCoreBlocks());
        }
        return new MineTunnelGeometry(kind, seed, slices, all, nav, List.of());
    }

    private static MineTunnelGeometry.Slice slice(
        int index,
        BlockPosition center,
        int width,
        int height
    ) {
        Set<BlockPosition> excavation = new LinkedHashSet<>();
        Set<BlockPosition> nav = new LinkedHashSet<>();
        int start = -(width / 2);
        for (int x = start; x < start + width; x++) {
            for (int y = 0; y < height; y++) {
                excavation.add(new BlockPosition(center.x(), center.y() + y, center.z() + x));
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y < Math.min(3, height); y++) {
                    BlockPosition block = new BlockPosition(
                        center.x() + x, center.y() + y, center.z() + z
                    );
                    excavation.add(block);
                    nav.add(block);
                }
            }
        }
        return new MineTunnelGeometry.Slice(index, center, width, height, excavation, nav);
    }
}
