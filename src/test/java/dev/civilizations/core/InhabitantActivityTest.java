package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InhabitantActivityTest {

    @Test
    void manualMoveSuppressesAutonomousWorkUntilResumeDelayExpires() {
        InhabitantActivity activity = new InhabitantActivity();
        WorldPosition destination = new WorldPosition(8.5, 65.0, -2.5);

        assertTrue(activity.autonomousWorkAllowed());
        assertNull(activity.manualMovementIntent());

        activity.orderManualMove(destination);

        assertFalse(activity.autonomousWorkAllowed());
        assertEquals(new MovementIntent(destination), activity.manualMovementIntent());
        assertEquals(
            InhabitantActivity.ActivityMode.MANUAL_MOVE,
            activity.snapshot().mode()
        );

        assertTrue(activity.completeManualMove());
        assertNull(activity.manualMovementIntent());
        assertFalse(activity.autonomousWorkAllowed());
        assertEquals(
            InhabitantActivity.ActivityMode.RESUME_DELAY,
            activity.snapshot().mode()
        );
        assertEquals(
            InhabitantActivity.MANUAL_MOVE_RESUME_DELAY_SECONDS,
            activity.snapshot().resumeDelayRemainingSeconds(),
            0.0001
        );

        activity.advance(1.5);
        assertFalse(activity.autonomousWorkAllowed());
        assertEquals(0.5, activity.snapshot().resumeDelayRemainingSeconds(), 0.0001);

        activity.advance(0.5);
        assertTrue(activity.autonomousWorkAllowed());
        assertEquals(
            InhabitantActivity.ActivityMode.AUTONOMOUS,
            activity.snapshot().mode()
        );
    }

    @Test
    void replacingAndCancellingManualMoveRemainDeterministic() {
        InhabitantActivity activity = new InhabitantActivity();
        WorldPosition first = new WorldPosition(1.0, 2.0, 3.0);
        WorldPosition second = new WorldPosition(4.0, 5.0, 6.0);

        activity.orderManualMove(first);
        activity.orderManualMove(second);

        assertEquals(new MovementIntent(second), activity.manualMovementIntent());
        assertTrue(activity.cancelManualMove());
        assertFalse(activity.cancelManualMove());
        assertTrue(activity.autonomousWorkAllowed());
    }

    @Test
    void manualMoveDoesNotMutatePausedWoodcutterTask() {
        InhabitantActivity activity = new InhabitantActivity();
        WoodcutterJob job = new WoodcutterJob();
        WoodcutterJob.WorkTarget target = new WoodcutterJob.WorkTarget(
            new BlockPosition(12, 64, 8),
            new WorldPosition(11.5, 64.0, 8.5)
        );

        assertTrue(job.assignTarget(target));
        assertTrue(activity.autonomousWorkAllowed());
        WoodcutterJob.Intent beforeOverride = job.intent();

        activity.orderManualMove(new WorldPosition(30.5, 64.0, 30.5));

        assertFalse(activity.autonomousWorkAllowed());
        assertEquals(beforeOverride, job.intent());

        activity.completeManualMove();

        assertFalse(activity.autonomousWorkAllowed());
        assertEquals(beforeOverride, job.intent());

        activity.advance(InhabitantActivity.MANUAL_MOVE_RESUME_DELAY_SECONDS);

        assertTrue(activity.autonomousWorkAllowed());
        assertEquals(beforeOverride, job.intent());
    }
}
