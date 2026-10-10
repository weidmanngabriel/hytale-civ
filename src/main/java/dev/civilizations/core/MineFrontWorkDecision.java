package dev.civilizations.core;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Shared decision boundary for joining a mine front and reserving the next
 * eligible block. Does not read or change Hytale world blocks.
 */
public final class MineFrontWorkDecision {
    private MineFrontWorkDecision() {}

    public enum Result { CLAIMED, FRONT_FULL, NO_BLOCK }

    public record Decision(Result result, BlockPosition block) {
        public Decision {
            Objects.requireNonNull(result);
            if ((result == Result.CLAIMED) != (block != null))
                throw new IllegalArgumentException("Claimed decisions must contain one block");
        }
    }

    public static <W> Decision choose(MineFrontCoordinator<W> reservations, UUID front,
                                      W worker, int capacity, List<BlockPosition> candidates,
                                      Predicate<BlockPosition> available) {
        Objects.requireNonNull(reservations);
        Objects.requireNonNull(front);
        Objects.requireNonNull(worker);
        Objects.requireNonNull(candidates);
        Objects.requireNonNull(available);
        if (!reservations.tryJoin(front, worker, capacity))
            return new Decision(Result.FRONT_FULL, null);
        BlockPosition block = reservations.claimNext(front, worker, capacity, candidates, available);
        return block == null ? new Decision(Result.NO_BLOCK, null)
                             : new Decision(Result.CLAIMED, block);
    }
}
