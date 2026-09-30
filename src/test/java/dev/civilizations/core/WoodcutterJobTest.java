package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WoodcutterJobTest {

    @Test
    void completesOneTreeThroughHeadlessIntentResults() {
        WoodcutterJob job = new WoodcutterJob();
        BlockPosition tree = new BlockPosition(12, 64, 8);
        WorldPosition interactionPoint = new WorldPosition(11.5, 64.0, 8.5);
        WoodcutterJob.WorkTarget target =
            new WoodcutterJob.WorkTarget(tree, interactionPoint, 12);

        assertInstanceOf(WoodcutterJob.FindTreeIntent.class, job.intent());

        assertTrue(job.assignTarget(target));
        assertEquals(WoodcutterJob.WorkState.WALKING_TO_TREE, job.state());
        assertEquals(tree, job.targetTree());
        assertEquals(
            new WoodcutterJob.MoveToTreeIntent(new MovementIntent(interactionPoint)),
            job.intent()
        );

        assertTrue(job.movementArrived());
        assertInstanceOf(WoodcutterJob.ChopTreeIntent.class, job.intent());
        assertFalse(job.advanceWork(target.chopSeconds() - 0.001));
        assertEquals(WoodcutterJob.WorkState.CHOPPING, job.state());

        assertTrue(job.advanceWork(0.001));
        assertEquals(
            new WoodcutterJob.FellTreeIntent(tree),
            job.intent()
        );

        assertTrue(job.fellingCompleted());
        assertEquals(WoodcutterJob.WorkState.SEARCHING, job.state());
        assertNull(job.targetTree());
        assertInstanceOf(WoodcutterJob.FindTreeIntent.class, job.intent());
    }

    @Test
    void largerTreesTakeLongerToChop() {
        WoodcutterJob.WorkTarget small = new WoodcutterJob.WorkTarget(
            new BlockPosition(1, 2, 3),
            new WorldPosition(0.5, 2.0, 3.5),
            6
        );
        WoodcutterJob.WorkTarget large = new WoodcutterJob.WorkTarget(
            new BlockPosition(4, 5, 6),
            new WorldPosition(3.5, 5.0, 6.5),
            20
        );

        assertTrue(large.chopSeconds() > small.chopSeconds());
        assertEquals(5.0, small.chopSeconds(), 1.0e-9);
        assertEquals(12.0, large.chopSeconds(), 1.0e-9);
    }

    @Test
    void abandoningTreeReturnsToSearch() {
        WoodcutterJob job = new WoodcutterJob();
        assertTrue(job.assignTarget(new WoodcutterJob.WorkTarget(
            new BlockPosition(1, 2, 3),
            new WorldPosition(0.5, 2.0, 3.5)
        )));

        job.abandonTarget();

        assertEquals(WoodcutterJob.WorkState.SEARCHING, job.state());
        assertNull(job.targetTree());
        assertFalse(job.movementArrived());
        assertInstanceOf(WoodcutterJob.FindTreeIntent.class, job.intent());
    }
}
