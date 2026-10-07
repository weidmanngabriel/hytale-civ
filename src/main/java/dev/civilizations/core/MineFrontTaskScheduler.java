package dev.civilizations.core;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chooses one executable tunnel-front task for a miner.
 *
 * <p>This is intentionally narrower than the full miner task model from the design document: the
 * current runtime only exposes tunnel excavation. Already-active fronts with free capacity are
 * filled before a new normal front is opened; within the chosen pool priority, distance and a
 * stable id break ties.</p>
 */
public final class MineFrontTaskScheduler {

    public static final int MAIN_TUNNEL_PRIORITY = 4;
    public static final int BRANCH_TUNNEL_PRIORITY = 6;

    private MineFrontTaskScheduler() {
    }

    public static MineWorkFront select(
        MineNetwork network,
        List<MineWorkFront> executableFronts,
        Map<UUID, Integer> workerCounts,
        BlockPosition workerPosition
    ) {
        if (network == null || executableFronts == null || workerCounts == null || workerPosition == null) {
            throw new IllegalArgumentException("Miner front scheduling inputs must not be null.");
        }

        List<MineWorkFront> available = executableFronts.stream()
            .filter(MineFrontTaskScheduler::availableState)
            .filter(front -> workerCounts.getOrDefault(front.id(), 0)
                < MineFrontCoordinator.NORMAL_TUNNEL_FRONT_CAPACITY)
            .toList();
        if (available.isEmpty()) return null;

        MineNormalTaskSelector.Candidate selected = MineNormalTaskSelector.select(
            available.stream()
                .map(front -> new MineNormalTaskSelector.Candidate(
                    front.id(),
                    MineNormalTaskSelector.Kind.TUNNEL_FRONT,
                    priority(network, front),
                    workerCounts.getOrDefault(front.id(), 0),
                    MineFrontCoordinator.NORMAL_TUNNEL_FRONT_CAPACITY,
                    front.position()
                ))
                .toList(),
            workerPosition
        );
        if (selected == null) return null;
        return available.stream().filter(front -> front.id().equals(selected.id())).findFirst().orElse(null);
    }

    static int priority(MineNetwork network, MineWorkFront front) {
        MineTunnel tunnel = network.tunnel(front.tunnelId());
        return tunnel != null && tunnel.kind() == MineTunnel.Kind.BRANCH
            ? BRANCH_TUNNEL_PRIORITY
            : MAIN_TUNNEL_PRIORITY;
    }

    private static boolean availableState(MineWorkFront front) {
        return front.state() == MineWorkFront.State.OPEN || front.state() == MineWorkFront.State.ACTIVE;
    }

}
