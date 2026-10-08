package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineDecisionDiagnosticsPlannerTest {

    private static final UUID MINE_ID = new UUID(17, 23);
    private static final BlockPosition ORIGIN = new BlockPosition(0, 80, 0);

    @Test
    void enablingDecisionDiagnosticsDoesNotChangeGeneratedMine() {
        long seed = 123456789L;
        MineNetworkGrowthPlanner.Plan baseline = MineNetworkGrowthPlanner.plan(
            MINE_ID, ORIGIN, MineHeading.NORTH, 360, 90, seed
        );
        List<MineRoom> baselineRooms = MineRoomPlanner.plan(baseline);

        RecordingSink sink = new RecordingSink();
        MineNetworkGrowthPlanner.Plan diagnosed = MineNetworkGrowthPlanner.plan(
            MINE_ID, ORIGIN, MineHeading.NORTH, 360, 90, seed, sink
        );
        List<MineRoom> diagnosedRooms = MineRoomPlanner.plan(diagnosed, sink);

        assertEquals(baseline, diagnosed);
        assertEquals(baselineRooms, diagnosedRooms);
        assertTrue(sink.events.stream().anyMatch(event ->
            event.category() == MineDecisionCategory.PLANNING
                && event.type().equals("HEADING_SELECTED")
        ));
        assertTrue(sink.events.stream().anyMatch(event ->
            event.category() == MineDecisionCategory.GEOMETRY
                && event.type().equals("FORM_PHASE")
        ));
        assertTrue(sink.events.stream().anyMatch(event ->
            event.category() == MineDecisionCategory.ROOM
                && (event.type().equals("ROOM_OPPORTUNITY")
                    || event.type().equals("ROOM_CREATED")
                    || event.type().equals("ROOM_REJECTED"))
        ));
    }

    private static final class RecordingSink implements MineDecisionSink {
        private final List<MineDecisionEvent> events = new ArrayList<>();

        @Override
        public boolean enabled(MineDecisionCategory category) {
            return true;
        }

        @Override
        public void record(MineDecisionEvent event) {
            events.add(event);
        }
    }
}
