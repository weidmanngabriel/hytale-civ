package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductionJobTest {

    @Test
    void producerWithInputsWaitsForGoodsBeforeWorking() {
        ProductionJob job = new ProductionJob(new ProductionRecipe(
            "miller", Map.of("wheat", 2), Map.of("flour", 1), 8.0
        ));

        assertTrue(job.start());
        assertTrue(job.workplaceReached());
        assertEquals(ProductionJob.State.WAITING_FOR_INPUTS, job.state());
        assertTrue(job.inputsReady());
        assertEquals(ProductionJob.State.TRAVELLING_TO_WORK_AREA, job.state());
    }

    @Test
    void speedMultiplierChangesWorkDurationWithoutChangingRecipe() {
        ProductionJob job = new ProductionJob(new ProductionRecipe(
            "stonecutter", Map.of("stone", 1), Map.of("cut_stone", 1), 10.0
        ));
        job.start();
        job.workplaceReached();
        job.inputsReady();
        job.workAreaReached();

        assertFalse(job.advanceWork(4.9, 2.0));
        assertTrue(job.advanceWork(0.1, 2.0));
        assertEquals(ProductionJob.State.CARRYING_OUTPUT, job.state());
    }

    @Test
    void producerWithoutInputsLoopsAfterOutputWasStored() {
        ProductionJob job = new ProductionJob(new ProductionRecipe(
            "farmer", Map.of(), Map.of("wheat", 1), 5.0
        ));
        job.start();
        job.workplaceReached();
        job.workAreaReached();
        assertTrue(job.advanceWork(5.0, 1.0));
        assertTrue(job.workplaceReached());
        assertEquals(ProductionJob.State.STORING_OUTPUT, job.state());
        assertTrue(job.outputStored());
        assertEquals(ProductionJob.State.TRAVELLING_TO_WORK_AREA, job.state());
    }
}
