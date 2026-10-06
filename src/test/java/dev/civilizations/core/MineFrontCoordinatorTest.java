package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineFrontCoordinatorTest {

    @Test
    void normalTunnelFrontAcceptsAtMostTwoMiners() {
        MineFrontCoordinator<String> coordinator = new MineFrontCoordinator<>();
        UUID front = UUID.randomUUID();

        assertTrue(coordinator.tryJoin(front, "a"));
        assertTrue(coordinator.tryJoin(front, "b"));
        assertFalse(coordinator.tryJoin(front, "c"));
        assertEquals(2, coordinator.workerCount(front));
    }

    @Test
    void minersOnSameFrontNeverClaimSameBlock() {
        MineFrontCoordinator<String> coordinator = new MineFrontCoordinator<>();
        UUID front = UUID.randomUUID();
        List<BlockPosition> blocks = List.of(
            new BlockPosition(1, 2, 3),
            new BlockPosition(1, 3, 3),
            new BlockPosition(1, 4, 3)
        );

        coordinator.tryJoin(front, "a");
        coordinator.tryJoin(front, "b");
        BlockPosition first = coordinator.claimNext(front, "a", blocks, ignored -> true);
        BlockPosition second = coordinator.claimNext(front, "b", blocks, ignored -> true);

        assertEquals(blocks.get(0), first);
        assertEquals(blocks.get(1), second);
    }

    @Test
    void releasedClaimCanBeTakenByOtherMiner() {
        MineFrontCoordinator<String> coordinator = new MineFrontCoordinator<>();
        UUID front = UUID.randomUUID();
        BlockPosition block = new BlockPosition(5, 6, 7);
        List<BlockPosition> blocks = List.of(block);

        coordinator.tryJoin(front, "a");
        coordinator.tryJoin(front, "b");
        assertEquals(block, coordinator.claimNext(front, "a", blocks, ignored -> true));
        coordinator.releaseWorker("a");

        assertEquals(block, coordinator.claimNext(front, "b", blocks, ignored -> true));
    }

    @Test
    void completingClaimKeepsWorkerOnSharedFrontForNextBlock() {
        MineFrontCoordinator<String> coordinator = new MineFrontCoordinator<>();
        UUID front = UUID.randomUUID();
        BlockPosition first = new BlockPosition(0, 0, 0);
        BlockPosition second = new BlockPosition(0, 1, 0);
        List<BlockPosition> blocks = List.of(first, second);

        coordinator.tryJoin(front, "a");
        assertEquals(first, coordinator.claimNext(front, "a", blocks, ignored -> true));
        coordinator.completeClaim(front, "a", first);
        assertNull(coordinator.claimOf(front, "a"));
        assertEquals(second, coordinator.claimNext(front, "a", blocks, block -> !block.equals(first)));
        assertEquals(1, coordinator.workerCount(front));
    }

    @Test
    void completedWorkUnitReleasesFrontCapacityAndClaims() {
        MineFrontCoordinator<String> coordinator = new MineFrontCoordinator<>();
        UUID front = UUID.randomUUID();
        BlockPosition block = new BlockPosition(2, 3, 4);

        coordinator.tryJoin(front, "a");
        coordinator.tryJoin(front, "b");
        coordinator.claimNext(front, "a", List.of(block), ignored -> true);

        coordinator.releaseFront(front);

        assertEquals(0, coordinator.workerCount(front));
        assertNull(coordinator.claimOf(front, "a"));
        assertTrue(coordinator.tryJoin(front, "c"));
    }
}
