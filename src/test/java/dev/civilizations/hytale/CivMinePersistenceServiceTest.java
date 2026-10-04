package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineDirection;
import dev.civilizations.core.MineSegment;
import org.junit.jupiter.api.Test;

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
}
