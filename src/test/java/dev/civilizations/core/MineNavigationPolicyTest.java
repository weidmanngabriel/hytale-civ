package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineNavigationPolicyTest {

    @Test
    void anchorRequiresTenBlocksAirLineFromExistingAnchors() {
        UUID tunnelId = UUID.randomUUID();
        MineNavigationAnchor origin = anchor(UUID.randomUUID(), tunnelId, 0, 20, 0, Set.of());

        assertFalse(MineNavigationPolicy.canCreateRegularAnchor(
            new BlockPosition(9, 20, 0), List.of(origin)
        ));
        assertTrue(MineNavigationPolicy.canCreateRegularAnchor(
            new BlockPosition(10, 20, 0), List.of(origin)
        ));
    }

    @Test
    void teleportThresholdIsStrictlyMoreThanFiftyBlocksAirLine() {
        BlockPosition anchor = new BlockPosition(0, 20, 0);

        assertFalse(MineNavigationPolicy.shouldTeleport(
            anchor, new WorldPosition(50.0, 20.0, 0.0)
        ));
        assertTrue(MineNavigationPolicy.shouldTeleport(
            anchor, new WorldPosition(50.01, 20.0, 0.0)
        ));
    }

    @Test
    void shortestRouteUsesSafeAnchorGraphRatherThanGeometricShortcut() {
        UUID tunnelId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        List<MineNavigationAnchor> anchors = List.of(
            anchor(a, tunnelId, 0, 20, 0, Set.of(b, c)),
            anchor(b, tunnelId, 10, 20, 0, Set.of(a, d)),
            anchor(c, tunnelId, 0, 20, 30, Set.of(a, d)),
            anchor(d, tunnelId, 20, 20, 0, Set.of(b, c))
        );

        assertEquals(List.of(a, b, d), MineNavigationPolicy.shortestRoute(anchors, a, d));
    }

    @Test
    void teleportDestinationIgnoresCloserButDisconnectedAnchor() {
        UUID mainTunnel = UUID.randomUUID();
        UUID targetTunnel = UUID.randomUUID();
        UUID start = UUID.randomUUID();
        UUID junction = UUID.randomUUID();
        UUID reachableTarget = UUID.randomUUID();
        UUID disconnectedCloser = UUID.randomUUID();

        List<MineNavigationAnchor> anchors = List.of(
            anchor(start, mainTunnel, 0, 20, 0, Set.of(junction)),
            anchor(junction, mainTunnel, 10, 20, 0, Set.of(start, reachableTarget)),
            anchor(reachableTarget, targetTunnel, 70, 20, 0, Set.of(junction)),
            anchor(disconnectedCloser, targetTunnel, 99, 20, 0, Set.of())
        );

        MineNavigationAnchor selected = MineNavigationPolicy.selectTeleportAnchor(
            anchors,
            start,
            targetTunnel,
            new WorldPosition(100.0, 20.0, 0.0)
        );

        assertEquals(reachableTarget, selected.id());
    }

    @Test
    void teleportDestinationPrefersReachableAnchorClosestToTarget() {
        UUID tunnelId = UUID.randomUUID();
        UUID start = UUID.randomUUID();
        UUID middle = UUID.randomUUID();
        UUID near = UUID.randomUUID();

        List<MineNavigationAnchor> anchors = List.of(
            anchor(start, tunnelId, 0, 20, 0, Set.of(middle)),
            anchor(middle, tunnelId, 40, 20, 0, Set.of(start, near)),
            anchor(near, tunnelId, 80, 20, 0, Set.of(middle))
        );

        MineNavigationAnchor selected = MineNavigationPolicy.selectTeleportAnchor(
            anchors,
            start,
            tunnelId,
            new WorldPosition(100.0, 20.0, 0.0)
        );

        assertEquals(near, selected.id());
    }

    private static MineNavigationAnchor anchor(
        UUID id,
        UUID tunnelId,
        int x,
        int y,
        int z,
        Set<UUID> connected
    ) {
        return new MineNavigationAnchor(
            id,
            tunnelId,
            new BlockPosition(x, y, z),
            MineNavigationAnchor.Type.REGULAR,
            connected
        );
    }
}
