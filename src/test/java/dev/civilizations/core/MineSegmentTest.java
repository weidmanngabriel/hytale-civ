package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MineSegmentTest {

    @Test
    void variableSegmentsContainExactlyTheirFourByFourVolume() {
        for (int length : List.of(4, 8, 12)) {
            for (MineDirection direction : MineDirection.values()) {
                MineSegment segment = segment(direction, length);
                assertEquals(16 * length, segment.blockCount());
                assertEquals(16 * length, segment.blocks().size());
                assertEquals(16 * length, new HashSet<>(segment.blocks()).size());
            }
        }
    }

    @Test
    void excavationOrderCompletesWholeFaceBeforeAdvancingDepth() {
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        MineSegment segment = segment(MineDirection.SOUTH, 8);
        for (int index = 0; index < faceSize; index++) {
            assertEquals(0, forwardDepth(segment, segment.blockAtIndex(index)));
        }
        assertEquals(1, forwardDepth(segment, segment.blockAtIndex(faceSize)));
    }

    @Test
    void straightContinuationUsesActualParentLength() {
        for (int length : List.of(4, 8, 12)) {
            for (MineDirection direction : MineDirection.values()) {
                MineSegment parent = segment(direction, length);
                assertEquals(
                    new BlockPosition(
                        parent.start().x() + direction.dx() * length,
                        parent.start().y(),
                        parent.start().z() + direction.dz() * length
                    ),
                    parent.nextStart(direction)
                );
            }
        }
    }

    @Test
    void turnsReuseParentsFinalFourByFourJunction() {
        int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
        int expectedOverlap = MineTuning.TUNNEL_WIDTH_BLOCKS * faceSize;
        for (MineDirection direction : MineDirection.values()) {
            MineSegment parent = segment(direction, 8);
            for (MineDirection turn : List.of(direction.left(), direction.right())) {
                MineSegment child = MineSegment.reserved(
                    UUID.randomUUID(),
                    parent.mineId(),
                    parent.id(),
                    parent.nextStart(turn),
                    turn,
                    8
                );
                HashSet<BlockPosition> overlap = new HashSet<>(parent.blocks());
                overlap.retainAll(child.blocks());
                assertEquals(expectedOverlap, overlap.size(), direction + " -> " + turn);
            }
        }
    }

    @Test
    void immediateReverseIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> segment(MineDirection.NORTH, 8).nextStart(MineDirection.SOUTH)
        );
    }

    @Test
    void progressAndSupportBoundsFollowActualLength() {
        MineSegment shortSegment = segment(MineDirection.SOUTH, 4);
        assertEquals(64, shortSegment.blockCount());
        assertEquals(new BlockPosition(10, 20, 33), shortSegment.supportOrigin(4));
        assertThrows(IllegalArgumentException.class, () -> shortSegment.supportOrigin(5));
        assertThrows(IndexOutOfBoundsException.class, () -> shortSegment.blockAtIndex(64));
    }

    private static int forwardDepth(MineSegment segment, BlockPosition block) {
        return (block.x() - segment.start().x()) * segment.direction().dx()
            + (block.z() - segment.start().z()) * segment.direction().dz();
    }

    private static MineSegment segment(MineDirection direction, int length) {
        return MineSegment.reserved(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            new BlockPosition(10, 20, 30),
            direction,
            length
        );
    }
}
