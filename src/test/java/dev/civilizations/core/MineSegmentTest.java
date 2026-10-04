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
    void variableSegmentsContainMainTunnelPlusFixedFourBlockJunction() {
        for (int length : List.of(4, 8, 12)) {
            for (MineDirection direction : MineDirection.values()) {
                MineSegment segment = segment(direction, length);
                int expected = 16 * (length + MineTuning.JUNCTION_LENGTH_BLOCKS);
                assertEquals(expected, segment.blockCount());
                assertEquals(expected, segment.blocks().size());
                assertEquals(expected, new HashSet<>(segment.blocks()).size());
                assertEquals(64, segment.junctionBlocks().size());
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
    void junctionStartsImmediatelyAfterMainTunnel() {
        MineSegment segment = segment(MineDirection.SOUTH, 7);
        int mainBlocks = MineTuning.mainTunnelBlocks(7);
        assertFalse(segment.isJunctionIndex(mainBlocks - 1));
        assertTrue(segment.isJunctionIndex(mainBlocks));
        assertEquals(7, forwardDepth(segment, segment.blockAtIndex(mainBlocks)));
    }

    @Test
    void straightContinuationStartsAfterMainTunnelAndJunction() {
        for (int length : List.of(4, 8, 12)) {
            for (MineDirection direction : MineDirection.values()) {
                MineSegment parent = segment(direction, length);
                int total = length + MineTuning.JUNCTION_LENGTH_BLOCKS;
                assertEquals(
                    new BlockPosition(
                        parent.start().x() + direction.dx() * total,
                        parent.start().y(),
                        parent.start().z() + direction.dz() * total
                    ),
                    parent.nextStart(direction)
                );
            }
        }
    }

    @Test
    void turnChildrenStartOutsideTheFullyReservedJunctionWithoutOverlap() {
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
                assertEquals(0, overlap.size(), direction + " -> " + turn);
            }
        }
    }

    @Test
    void northTurnStartsMatchTheTwoOpenSidesOfItsJunction() {
        MineSegment parent = segment(MineDirection.NORTH, 8);
        assertEquals(new BlockPosition(9, 20, 22), parent.nextStart(MineDirection.WEST));
        assertEquals(new BlockPosition(14, 20, 19), parent.nextStart(MineDirection.EAST));
    }

    @Test
    void immediateReverseIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> segment(MineDirection.NORTH, 8).nextStart(MineDirection.SOUTH)
        );
    }

    @Test
    void supportBoundsEndAtMainTunnelNotAtJunction() {
        MineSegment shortSegment = segment(MineDirection.SOUTH, 4);
        assertEquals(128, shortSegment.blockCount());
        assertEquals(new BlockPosition(10, 20, 33), shortSegment.supportOrigin(4));
        assertThrows(IllegalArgumentException.class, () -> shortSegment.supportOrigin(5));
        assertThrows(IndexOutOfBoundsException.class, () -> shortSegment.blockAtIndex(128));
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
