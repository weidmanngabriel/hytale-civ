package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionJobTest {

    @Test
    void completesConstructionThroughTimedSteps() {
        ConstructionJob job = new ConstructionJob();
        ConstructionJob.WorkTarget target = new ConstructionJob.WorkTarget(
            "site-1",
            new WorldPosition(4.5, 65.0, 8.5),
            3
        );

        assertInstanceOf(ConstructionJob.FindConstructionSiteIntent.class, job.intent());
        assertTrue(job.assignTarget(target));
        assertInstanceOf(ConstructionJob.MoveToConstructionSiteIntent.class, job.intent());

        assertTrue(job.movementArrived());
        assertEquals(0, job.advanceWork(ConstructionJob.STEP_SECONDS - 0.1));
        assertEquals(1, job.advanceWork(0.1));
        assertEquals(1, job.completedSteps());

        assertEquals(2, job.advanceWork(ConstructionJob.STEP_SECONDS * 2.0));
        assertEquals(ConstructionJob.WorkState.READY_TO_COMPLETE, job.state());
        assertInstanceOf(ConstructionJob.CompleteConstructionIntent.class, job.intent());

        assertTrue(job.constructionCompleted());
        assertEquals(ConstructionJob.WorkState.SEARCHING, job.state());
        assertNull(job.target());
    }

    @Test
    void resumesProgressOwnedByConstructionSite() {
        ConstructionJob job = new ConstructionJob();
        ConstructionJob.WorkTarget target = new ConstructionJob.WorkTarget(
            "persistent-site",
            new WorldPosition(10.5, 65.0, 12.5),
            8
        );

        assertTrue(job.assignTarget(target, 5));
        assertEquals(5, job.completedSteps());
        assertInstanceOf(ConstructionJob.MoveToConstructionSiteIntent.class, job.intent());
        assertTrue(job.movementArrived());
        assertEquals(1, job.advanceWork(ConstructionJob.STEP_SECONDS));
        assertEquals(6, job.completedSteps());
    }

    @Test
    void fullyMaterializedSiteCanCompleteWithoutRebuildingLayers() {
        ConstructionJob job = new ConstructionJob();
        ConstructionJob.WorkTarget target = new ConstructionJob.WorkTarget(
            "ready-site",
            new WorldPosition(1.5, 2.0, 3.5),
            4
        );

        assertTrue(job.assignTarget(target, 4));
        assertEquals(ConstructionJob.WorkState.READY_TO_COMPLETE, job.state());
        assertInstanceOf(ConstructionJob.CompleteConstructionIntent.class, job.intent());
    }

    @Test
    void abandoningSiteReturnsWorkerToSearch() {
        ConstructionJob job = new ConstructionJob();
        assertTrue(job.assignTarget(new ConstructionJob.WorkTarget(
            "site-2",
            new WorldPosition(1.5, 2.0, 3.5),
            2
        )));

        job.abandonTarget();

        assertEquals(ConstructionJob.WorkState.SEARCHING, job.state());
        assertNull(job.target());
        assertInstanceOf(ConstructionJob.FindConstructionSiteIntent.class, job.intent());
    }
}
