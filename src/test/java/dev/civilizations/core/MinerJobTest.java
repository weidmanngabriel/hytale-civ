package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinerJobTest {

    @Test
    void oneSegmentProducesEightFaces128BlocksAndTwoSupports() {
        MinerJob job = new MinerJob(segment());
        List<Integer> approachedDepths = new ArrayList<>();
        List<Integer> brokenIndices = new ArrayList<>();
        List<Integer> supportDepths = new ArrayList<>();

        while (job.state() != MinerJob.WorkState.COMPLETE) {
            switch (job.intent()) {
                case MinerJob.MoveToFaceIntent move -> {
                    approachedDepths.add(move.depth());
                    assertTrue(job.movementArrived());
                }
                case MinerJob.BreakBlockIntent mine -> {
                    brokenIndices.add(mine.blockIndex());
                    assertEquals(job.segment().blockAtIndex(mine.blockIndex()), mine.block());
                    assertTrue(job.blockBroken());
                }
                case MinerJob.PlaceSupportIntent support -> {
                    supportDepths.add(support.depth());
                    assertTrue(job.supportPlaced());
                }
                case MinerJob.SegmentCompleteIntent ignored -> throw new AssertionError(
                    "completion intent must only appear after the loop condition"
                );
            }
        }

        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), approachedDepths);
        assertEquals(128, brokenIndices.size());
        assertEquals(0, brokenIndices.getFirst());
        assertEquals(127, brokenIndices.getLast());
        assertEquals(List.of(4, 8), supportDepths);
        assertEquals(2, job.segment().supportsPlaced());
        assertEquals(MineSegment.Status.COMPLETE, job.segment().status());
        assertInstanceOf(MinerJob.SegmentCompleteIntent.class, job.intent());
    }

    @Test
    void aFaceMustBeCompletelyBrokenBeforeTheNextMoveIntent() {
        MinerJob job = new MinerJob(segment());
        assertTrue(job.movementArrived());

        for (int i = 0; i < 15; i++) {
            assertInstanceOf(MinerJob.BreakBlockIntent.class, job.intent());
            assertTrue(job.blockBroken());
        }
        assertInstanceOf(MinerJob.BreakBlockIntent.class, job.intent());

        assertTrue(job.blockBroken());
        MinerJob.MoveToFaceIntent next = assertInstanceOf(MinerJob.MoveToFaceIntent.class, job.intent());
        assertEquals(1, next.depth());
    }

    @Test
    void callbacksAreRejectedWhenTheyDoNotMatchTheCurrentIntent() {
        MinerJob job = new MinerJob(segment());

        assertFalse(job.blockBroken());
        assertFalse(job.supportPlaced());
        assertTrue(job.movementArrived());
        assertFalse(job.movementArrived());
        assertFalse(job.supportPlaced());
    }

    private static MineSegment segment() {
        return MineSegment.reserved(
            UUID.fromString("00000000-0000-0000-0000-000000000101"),
            UUID.fromString("00000000-0000-0000-0000-000000000102"),
            null,
            new BlockPosition(10, 20, 30),
            MineDirection.NORTH
        );
    }
}
