package dev.civilizations.core;

import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Layer-4 selector for choosing which open mine front is allowed to advance next.
 *
 * <p>This is mine-generation scheduling, not miner task scheduling. The miner scheduler still owns
 * task priority, reservations and worker assignment. This selector only prevents a large number of
 * side branches from starving the logical main tunnel indefinitely.</p>
 */
public final class MineWorkFrontGrowthSelector {

    public static final double MAIN_SELECTION_SHARE = 0.40;
    public static final int MAX_CONSECUTIVE_BRANCH_SELECTIONS = 3;

    private MineWorkFrontGrowthSelector() {
    }

    public static Selection select(
        MineNetwork network,
        List<MineWorkFront> candidates,
        int consecutiveBranchSelections,
        long seed
    ) {
        if (network == null || candidates == null) {
            throw new IllegalArgumentException("Mine front selection inputs must not be null.");
        }
        if (consecutiveBranchSelections < 0) {
            throw new IllegalArgumentException("Consecutive branch selections must not be negative.");
        }

        List<MineWorkFront> open = candidates.stream()
            .filter(front -> front.state() == MineWorkFront.State.OPEN)
            .toList();
        if (open.isEmpty()) return new Selection(null, 0);

        List<MineWorkFront> main = open.stream()
            .filter(front -> isMain(network, front))
            .sorted(FRONT_ORDER)
            .toList();
        List<MineWorkFront> branches = open.stream()
            .filter(front -> !isMain(network, front))
            .sorted(FRONT_ORDER)
            .toList();

        if (branches.isEmpty()) return new Selection(main.getFirst(), 0);
        if (main.isEmpty()) return new Selection(branches.getFirst(), consecutiveBranchSelections + 1);

        if (consecutiveBranchSelections >= MAX_CONSECUTIVE_BRANCH_SELECTIONS) {
            return new Selection(main.getFirst(), 0);
        }

        SplittableRandom random = new SplittableRandom(seed);
        if (random.nextDouble() < MAIN_SELECTION_SHARE) {
            return new Selection(main.getFirst(), 0);
        }
        return new Selection(branches.getFirst(), consecutiveBranchSelections + 1);
    }

    private static boolean isMain(MineNetwork network, MineWorkFront front) {
        MineTunnel tunnel = network.tunnel(front.tunnelId());
        return tunnel != null && tunnel.kind() == MineTunnel.Kind.MAIN;
    }

    private static final Comparator<MineWorkFront> FRONT_ORDER = Comparator
        .comparing((MineWorkFront front) -> front.position().x())
        .thenComparing(front -> front.position().y())
        .thenComparing(front -> front.position().z())
        .thenComparing(front -> front.id().toString());

    public record Selection(MineWorkFront front, int consecutiveBranchSelections) {
    }
}
