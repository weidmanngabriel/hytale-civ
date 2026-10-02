package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineSegmentTest {

    @Test
    void phaseOneSegmentContainsExactly128UniqueBlocks() {
        assertEquals(128, MineTuning.blocksPerSegment());
        assertEquals(600.0 / 128.0, MineTuning.secondsPerBlock(), 0.000001);

        for (MineDirection direction : MineDirection.values()) {
            MineSegment segment = segment(direction);
            assertEquals(128, segment.blocks().size());
            assertEquals(128, new HashSet<>(segment.blocks()).size());
        }
    }

    @Test
    void straightContinuationDoesNotOverlapParent() {
        for (MineDirection direction : MineDirection.values()) {
            MineSegment parent = segment(direction);
            MineSegment child = MineSegment.reserved(
                UUID.randomUUID(),
                parent.mineId(),
                parent.id(),
                parent.nextStart(direction),
                direction
            );
            HashSet<BlockPosition> overlap = new HashSet<>(parent.blocks());
            overlap.retainAll(child.blocks());
            assertTrue(overlap.isEmpty(), direction.name());
        }
    }

    @Test
    void leftAndRightContinuationsDoNotReverse() {
        for (MineDirection direction : MineDirection.values()) {
            MineSegment parent = segment(direction);
            assertFalse(parent.nextStart(direction.left()).equals(parent.start()));
            assertFalse(parent.nextStart(direction.right()).equals(parent.start()));
        }
    }

    @Test
    void immediateReverseIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> segment(MineDirection.NORTH).nextStart(MineDirection.SOUTH)
        );
    }

    @Test
    void supportsAreAddressableAtFourAndEightBlocks() {
        MineSegment segment = segment(MineDirection.SOUTH);
        assertEquals(new BlockPosition(10, 20, 33), segment.supportOrigin(4));
        assertEquals(new BlockPosition(10, 20, 37), segment.supportOrigin(8));
    }

    private static MineSegment segment(MineDirection direction) {
        return MineSegment.reserved(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            new BlockPosition(10, 20, 30),
            direction
        );
    }
}
