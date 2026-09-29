package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkDecisionScheduleTest {

    @Test
    void startsWithOneImmediateDecisionThenWaits() {
        WorkDecisionSchedule schedule = new WorkDecisionSchedule();

        assertEquals(
            WorkDecisionSchedule.DecisionKind.IMMEDIATE,
            schedule.advance(0.05)
        );
        assertEquals(
            WorkDecisionSchedule.DecisionKind.NONE,
            schedule.advance(10.0)
        );
        assertFalse(schedule.immediateRequested());
    }

    @Test
    void retryOnlyFiresAfterConfiguredDelay() {
        WorkDecisionSchedule schedule = new WorkDecisionSchedule();
        schedule.advance(0.05);
        schedule.scheduleRetry(1.0);

        assertEquals(
            WorkDecisionSchedule.DecisionKind.NONE,
            schedule.advance(0.5)
        );
        assertEquals(
            WorkDecisionSchedule.DecisionKind.NONE,
            schedule.advance(0.49)
        );
        assertEquals(
            WorkDecisionSchedule.DecisionKind.RETRY,
            schedule.advance(0.01)
        );
        assertEquals(
            WorkDecisionSchedule.DecisionKind.NONE,
            schedule.advance(1.0)
        );
    }

    @Test
    void immediateEventPreemptsPendingRetry() {
        WorkDecisionSchedule schedule = new WorkDecisionSchedule();
        schedule.advance(0.05);
        schedule.scheduleRetry(10.0);
        schedule.advance(2.0);

        schedule.requestImmediate();

        assertTrue(schedule.immediateRequested());
        assertEquals(
            WorkDecisionSchedule.DecisionKind.IMMEDIATE,
            schedule.advance(0.05)
        );
        assertEquals(
            WorkDecisionSchedule.DecisionKind.NONE,
            schedule.advance(20.0)
        );
    }
}
