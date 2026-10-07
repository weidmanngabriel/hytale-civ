package dev.civilizations.hytale;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineHeading;
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
            .withRoom(new MineRoom(
                UUID.randomUUID(),
                branchId,
                MineRoom.Type.SMALL_NICHE,
                new BlockPosition(12, 2, 4),
                MineHeading.NORTH,
                7,
                MineRoom.State.EXCAVATING,
                2,
                Set.of(0, 2)
            ))
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
        UUID completedInfrastructure = UUID.randomUUID();
        network = network.withInfrastructureTaskCompleted(completedInfrastructure);

        MineNetwork decoded = service.decodeNetwork(service.encodeNetwork(network));
        assertEquals(network, decoded);
        assertEquals(Set.of(completedInfrastructure), decoded.completedInfrastructureTaskIds());
    }


    @Test
    void roundTripKeepsIntegratedNaturalChamberState() {
        UUID mineId = UUID.randomUUID();
        UUID mainId = UUID.randomUUID();
        MineRoom chamber = new MineRoom(
            UUID.randomUUID(),
            mainId,
            MineRoom.Type.LARGE_NATURAL_CHAMBER,
            new BlockPosition(40, 12, 18),
            MineHeading.EAST,
            21,
            MineRoom.State.NATURAL_INTEGRATED,
            0,
            Set.of()
        );
        MineNetwork network = MineNetwork.create(mineId, mainId, new BlockPosition(1, 2, 3))
            .withRoom(chamber);

        MineNetwork decoded = service.decodeNetwork(service.encodeNetwork(network));

        assertEquals(chamber, decoded.rooms().getFirst());
    }

    @Test
    void readsPreviousN3RoomRecordWithDefaultRoomProgress() {
        UUID mineId = UUID.randomUUID();
        UUID mainId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        String previous = "N3|" + mineId + "|" + mainId + "\n"
            + "T|" + mainId + "|MAIN||0|1,2,3\n"
            + "R|" + roomId + "|" + mainId + "|SMALL_NICHE|12,2,4";

        MineNetwork decoded = service.decodeNetwork(previous);

        assertEquals(MineRoom.State.PLANNED, decoded.rooms().getFirst().state());
        assertEquals(0, decoded.rooms().getFirst().excavationWorkUnitIndex());
        assertEquals(Set.of(), decoded.rooms().getFirst().completedBuildSections());
    }

    @Test
    void readsPreviousN2NetworkFormatWithNoCompletedInfrastructure() {
        UUID mineId = UUID.randomUUID();
        UUID mainId = UUID.randomUUID();
        String previous = "N2|" + mineId + "|" + mainId + "\n"
            + "T|" + mainId + "|MAIN||0|1,2,3";

        MineNetwork decoded = service.decodeNetwork(previous);

        assertEquals(mineId, decoded.mineId());
        assertEquals(mainId, decoded.mainTunnelId());
        assertEquals(Set.of(), decoded.completedInfrastructureTaskIds());
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
