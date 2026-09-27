package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WoodcutterJobTest {

    @Test
    void completesOneTreeOnlyAfterWalkingAndChopping() {
        WoodcutterJob job = new WoodcutterJob();
        BlockPosition tree = new BlockPosition(12, 64, 8);

        assertTrue(job.assignTarget(tree));
        assertEquals(WoodcutterJob.WorkState.WALKING_TO_TREE, job.state());
        assertEquals(tree, job.targetTree());

        assertTrue(job.arriveAtTree());
        assertFalse(job.advanceWork(WoodcutterJob.CHOP_SECONDS - 0.001));
        assertEquals(WoodcutterJob.WorkState.CHOPPING, job.state());

        assertTrue(job.advanceWork(0.001));
        assertEquals(WoodcutterJob.WorkState.READY_TO_FELL, job.state());

        assertTrue(job.completeFelling());
        assertEquals(WoodcutterJob.WorkState.SEARCHING, job.state());
        assertNull(job.targetTree());
    }

    @Test
    void abandoningTreeReturnsToSearch() {
        WoodcutterJob job = new WoodcutterJob();
        assertTrue(job.assignTarget(new BlockPosition(1, 2, 3)));

        job.abandonTarget();

        assertEquals(WoodcutterJob.WorkState.SEARCHING, job.state());
        assertNull(job.targetTree());
        assertFalse(job.arriveAtTree());
    }
}
