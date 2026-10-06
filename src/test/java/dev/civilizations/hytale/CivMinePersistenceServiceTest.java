package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineNavigationAnchor;
import dev.civilizations.core.MineNetwork;
import dev.civilizations.core.MineRoom;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineWorkFront;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CivMinePersistenceServiceTest {

    private final CivMinePersistenceService service = new CivMinePersistenceService(null);

    @Test
    void roundTripKeepsMineNetworkTopologyAndMultipleFronts() {
        UUID mineId = UUID.randomUUID();
        UUID mainId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID anchorAId = UUID.randomUUID();
        UUID anchorBId = UUID.randomUUID();

        MineNetwork network = MineNetwork.create(mineId, mainId, new BlockPosition(1, 2, 3))
            .withTunnel(new MineTunnel(branchId, MineTunnel.Kind.BRANCH, mainId, 1,
                new BlockPosition(10, 2, 3)))
            .withRoom(new MineRoom(UUID.randomUUID(), branchId, MineRoom.Type.SMALL_NICHE,
                new BlockPosition(12, 2, 4)))
            .withWorkFront(new MineWorkFront(UUID.randomUUID(), mainId,
                new BlockPosition(8, 2, 3), MineWorkFront.State.ACTIVE))
            .withWorkFront(new MineWorkFront(UUID.randomUUID(), branchId,
                new BlockPosition(16, 2, 3), MineWorkFront.State.OPEN));

        network = network.withNavigationAnchors(List.of(
            new MineNavigationAnchor(anchorAId, mainId,
                new BlockPosition(5, 2, 3), MineNavigationAnchor.Type.JUNCTION, Set.of(anchorBId)),
            new MineNavigationAnchor(anchorBId, branchId,
                new BlockPosition(10, 2, 3), MineNavigationAnchor.Type.REGULAR, Set.of(anchorAId))
        ));

        assertEquals(network, service.decodeNetwork(service.encodeNetwork(network)));
    }

    @Test
    void rejectsPreCutNetworkFormatInsteadOfMigratingIt() {
        UUID mineId = UUID.randomUUID();
        UUID mainId = UUID.randomUUID();
        String old = "N|" + mineId + "|" + mainId + "\n"
            + "T|" + mainId + "|MAIN||0|1,2,3|";

        assertThrows(IllegalArgumentException.class, () -> service.decodeNetwork(old));
    }
}
