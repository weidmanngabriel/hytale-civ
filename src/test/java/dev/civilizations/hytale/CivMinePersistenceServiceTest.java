package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineNavigationAnchor;
import dev.civilizations.core.MineNetwork;
import dev.civilizations.core.MineRoom;
import dev.civilizations.core.MineSegment;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineWorkFront;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivMinePersistenceServiceTest {

    private final CivMinePersistenceService service = new CivMinePersistenceService(null);

    @Test
    void roundTripKeepsVariableLength() {
        MineSegment segment = MineSegment.reserved(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            new BlockPosition(1, 2, 3),
            MineDirection.WEST,
            11
        ).withStatus(MineSegment.Status.MINING).withProgress(17).withSupportsPlaced(2);

        assertEquals(segment, service.decode(service.encode(segment)));
    }

    @Test
    void legacyEightFieldSegmentsLoadAsEightBlocksLong() {
        UUID id = UUID.randomUUID();
        UUID mine = UUID.randomUUID();
        String encoded = id + "|" + mine + "||1,2,3|NORTH|MINING|16|0";

        assertEquals(8, service.decode(encoded).lengthBlocks());
    }

    @Test
    void roundTripKeepsMineNetworkTopology() {
        UUID mineId = UUID.randomUUID();
        UUID mainId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID segmentId = UUID.randomUUID();
        UUID anchorAId = UUID.randomUUID();
        UUID anchorBId = UUID.randomUUID();

        MineNetwork network = MineNetwork.create(mineId, mainId, new BlockPosition(1, 2, 3))
            .withTunnel(new MineTunnel(branchId, MineTunnel.Kind.BRANCH, mainId, 1,
                new BlockPosition(10, 2, 3), List.of(segmentId)))
            .withRoom(new MineRoom(UUID.randomUUID(), branchId, MineRoom.Type.SMALL_NICHE,
                new BlockPosition(12, 2, 4)))
            .withWorkFront(new MineWorkFront(UUID.randomUUID(), branchId,
                new BlockPosition(16, 2, 3), MineWorkFront.State.OPEN))
            .withNavigationAnchor(new MineNavigationAnchor(anchorAId, mainId,
                new BlockPosition(5, 2, 3), MineNavigationAnchor.Type.JUNCTION, Set.of(anchorBId)))
            .withNavigationAnchor(new MineNavigationAnchor(anchorBId, branchId,
                new BlockPosition(10, 2, 3), MineNavigationAnchor.Type.REGULAR, Set.of(anchorAId)));

        assertEquals(network, service.decodeNetwork(service.encodeNetwork(network)));
    }
}
