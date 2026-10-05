package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MineDecisionEventTest {

    @Test
    void formatsStableStructuredContextAndDetails() {
        UUID mineId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID frontId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        MineDecisionEvent event = MineDecisionEvent.of(
            mineId,
            frontId,
            MineDecisionCategory.PLANNING,
            "DIRECTION_SELECTED",
            "direction", "NORTH",
            "weight", 50,
            "roll", 17
        );

        assertEquals(
            "[Civ Mine][mine=00000000-0000-0000-0000-000000000001]"
                + "[front=00000000-0000-0000-0000-000000000002]"
                + "[PLANNING][DIRECTION_SELECTED] direction=NORTH weight=50 roll=17",
            event.format()
        );
    }

    @Test
    void disabledSinkDoesNotBuildOrRecordEvents() {
        assertFalse(MineDecisionSink.NONE.enabled(MineDecisionCategory.PLANNING));
        MineDecisionSink.NONE.record(
            UUID.randomUUID(),
            UUID.randomUUID(),
            MineDecisionCategory.PLANNING,
            "IGNORED",
            "value", 1
        );
    }
}
