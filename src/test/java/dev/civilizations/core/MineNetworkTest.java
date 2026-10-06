package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineNetworkTest {

    @Test
    void supportsNestedBranchesRoomsWorkFrontsAndAnchors() {
        UUID mineId = UUID.randomUUID();
        UUID mainId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID nestedId = UUID.randomUUID();
        BlockPosition origin = new BlockPosition(10, 20, 30);

        MineNetwork network = MineNetwork.create(mineId, mainId, origin)
            .withTunnel(new MineTunnel(branchId, MineTunnel.Kind.BRANCH, mainId, 1,
                new BlockPosition(20, 20, 30)))
            .withTunnel(new MineTunnel(nestedId, MineTunnel.Kind.BRANCH, branchId, 2,
                new BlockPosition(20, 20, 40)));

        MineRoom room = new MineRoom(UUID.randomUUID(), branchId, MineRoom.Type.ORE_COLLECTION,
            new BlockPosition(22, 20, 34));
        MineWorkFront front = new MineWorkFront(UUID.randomUUID(), nestedId,
            new BlockPosition(20, 20, 48), MineWorkFront.State.OPEN);
        UUID firstAnchorId = UUID.randomUUID();
        UUID secondAnchorId = UUID.randomUUID();
        MineNavigationAnchor firstAnchor = new MineNavigationAnchor(firstAnchorId, mainId, origin,
            MineNavigationAnchor.Type.JUNCTION, Set.of(secondAnchorId));
        MineNavigationAnchor secondAnchor = new MineNavigationAnchor(secondAnchorId, branchId,
            new BlockPosition(20, 20, 30), MineNavigationAnchor.Type.WORK_FRONT, Set.of(firstAnchorId));

        network = network.withRoom(room).withWorkFront(front)
            .withNavigationAnchors(List.of(firstAnchor, secondAnchor));

        assertEquals(3, network.tunnels().size());
        assertEquals(2, network.tunnel(nestedId).branchDepth());
        assertEquals(room, network.rooms().getFirst());
        assertEquals(front, network.workFronts().getFirst());
        assertEquals(2, network.navigationAnchors().size());

        UUID infrastructureTaskId = UUID.randomUUID();
        MineNetwork completed = network.withInfrastructureTaskCompleted(infrastructureTaskId);
        assertTrue(completed.infrastructureTaskCompleted(infrastructureTaskId));
        assertEquals(Set.of(infrastructureTaskId), completed.completedInfrastructureTaskIds());
    }

    @Test
    void rejectsBranchWithWrongDepth() {
        UUID mineId = UUID.randomUUID();
        UUID mainId = UUID.randomUUID();
        MineNetwork network = MineNetwork.create(mineId, mainId, new BlockPosition(0, 0, 0));

        MineTunnel invalid = new MineTunnel(UUID.randomUUID(), MineTunnel.Kind.BRANCH, mainId, 2,
            new BlockPosition(1, 0, 0));

        assertThrows(IllegalArgumentException.class, () -> network.withTunnel(invalid));
    }

    @Test
    void rejectsTopologyObjectsReferencingUnknownTunnel() {
        UUID mineId = UUID.randomUUID();
        UUID mainId = UUID.randomUUID();
        MineNetwork network = MineNetwork.create(mineId, mainId, new BlockPosition(0, 0, 0));
        MineWorkFront invalid = new MineWorkFront(UUID.randomUUID(), UUID.randomUUID(),
            new BlockPosition(4, 0, 0), MineWorkFront.State.OPEN);

        assertThrows(IllegalArgumentException.class, () -> network.withWorkFront(invalid));
    }
}
