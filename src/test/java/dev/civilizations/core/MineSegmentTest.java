package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineSegmentTest {

    @Test
    void phaseOneSegmentContainsExactly128UniqueBlocks() {
        assertEquals(128, MineTuning.blocksPerSegment());
        assertEquals(MineTuning.SEGMENT_TARGET_SECONDS / 128.0, MineTuning.secondsPerBlock(), 0.000001);

        for (MineDirection direction : MineDirection.values()) {
            MineSegment segment = segment(direction);
            assertEquals(128, segment.blocks().size());
            assertEquals(128, new HashSet<>(segment.blocks()).size());
        }
    }

    @Test
    void excavationOrderCompletesWholeFourByFourFaceBeforeNextDepth() {
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        assertEquals(16, faceSize);

        for (MineDirection direction : MineDirection.values()) {
            MineSegment segment = segment(direction);

            for (int index = 0; index < faceSize; index++) {
                BlockPosition block = segment.blockAtIndex(index);
                assertEquals(0, forwardDepth(segment, block), direction + " first face index " + index);
            }

            for (int index = faceSize - MineTuning.TUNNEL_WIDTH_BLOCKS; index < faceSize; index++) {
                assertEquals(
                    segment.start().y() + MineTuning.TUNNEL_HEIGHT_BLOCKS - 1,
                    segment.blockAtIndex(index).y(),
                    direction + " top row index " + index
                );
            }

            assertEquals(
                1,
                forwardDepth(segment, segment.blockAtIndex(faceSize)),
                direction + " must only enter depth 1 after all 16 face blocks"
            );
        }
    }

    @Test
    void straightContinuationStaysExactlyInTheSameTunnelLane() {
        for (MineDirection direction : MineDirection.values()) {
            MineSegment parent = segment(direction);
            BlockPosition expectedStart = new BlockPosition(
                parent.start().x() + direction.dx() * MineTuning.SEGMENT_LENGTH_BLOCKS,
                parent.start().y(),
                parent.start().z() + direction.dz() * MineTuning.SEGMENT_LENGTH_BLOCKS
            );
            assertEquals(expectedStart, parent.nextStart(direction), direction.name());

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
    void turningContinuationsReuseOnlyTheParentsFinalFourByFourJunction() {
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int junctionBlocks = MineTuning.TUNNEL_WIDTH_BLOCKS * faceSize;

        for (MineDirection direction : MineDirection.values()) {
            MineSegment parent = segment(direction);
            for (MineDirection turn : List.of(direction.left(), direction.right())) {
                MineSegment child = MineSegment.reserved(
                    UUID.randomUUID(),
                    parent.mineId(),
                    parent.id(),
                    parent.nextStart(turn),
                    turn
                );

                HashSet<BlockPosition> overlap = new HashSet<>(parent.blocks());
                overlap.retainAll(child.blocks());
                assertEquals(junctionBlocks, overlap.size(), direction + " -> " + turn);

                for (int index = 0; index < junctionBlocks; index++) {
                    assertTrue(
                        overlap.contains(child.blockAtIndex(index)),
                        direction + " -> " + turn + " child junction block " + index
                    );
                }
                assertFalse(
                    overlap.contains(child.blockAtIndex(junctionBlocks)),
                    direction + " -> " + turn + " must continue beyond the junction"
                );
            }
        }
    }

    @Test
    void continuationStartsAreSymmetricForAllCardinalDirections() {
        assertContinuationStarts(
            MineDirection.NORTH,
            new BlockPosition(10, 20, 22),
            new BlockPosition(13, 20, 26),
            new BlockPosition(10, 20, 23)
        );
        assertContinuationStarts(
            MineDirection.EAST,
            new BlockPosition(18, 20, 30),
            new BlockPosition(14, 20, 33),
            new BlockPosition(17, 20, 30)
        );
        assertContinuationStarts(
            MineDirection.SOUTH,
            new BlockPosition(10, 20, 38),
            new BlockPosition(7, 20, 34),
            new BlockPosition(10, 20, 37)
        );
        assertContinuationStarts(
            MineDirection.WEST,
            new BlockPosition(2, 20, 30),
            new BlockPosition(6, 20, 27),
            new BlockPosition(3, 20, 30)
        );
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

    private static int forwardDepth(MineSegment segment, BlockPosition block) {
        return (block.x() - segment.start().x()) * segment.direction().dx()
            + (block.z() - segment.start().z()) * segment.direction().dz();
    }

    private static void assertContinuationStarts(
        MineDirection direction,
        BlockPosition straight,
        BlockPosition left,
        BlockPosition right
    ) {
        MineSegment parent = segment(direction);
        assertEquals(straight, parent.nextStart(direction), direction + " straight");
        assertEquals(left, parent.nextStart(direction.left()), direction + " left");
        assertEquals(right, parent.nextStart(direction.right()), direction + " right");
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
