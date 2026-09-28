package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InhabitantActivityTest {

    @Test
    void manualMoveSuppressesAutonomousWorkUntilArrival() {
        InhabitantActivity activity = new InhabitantActivity();
        WorldPosition destination = new WorldPosition(8.5, 65.0, -2.5);

        assertTrue(activity.autonomousWorkAllowed());
        assertNull(activity.manualMovementIntent());

        activity.orderManualMove(destination);

        assertFalse(activity.autonomousWorkAllowed());
        assertEquals(new MovementIntent(destination), activity.manualMovementIntent());

        assertTrue(activity.completeManualMove());
        assertTrue(activity.autonomousWorkAllowed());
        assertNull(activity.manualMovementIntent());
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

        assertTrue(activity.autonomousWorkAllowed());
        assertEquals(beforeOverride, job.intent());
    }
}
