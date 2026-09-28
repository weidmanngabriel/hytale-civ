package dev.civilizations.core;

import java.util.Objects;

/**
 * Hytale-independent state machine for one woodcutter work cycle.
 *
 * <p>The state machine exposes intents instead of performing world access itself. An adapter
 * resolves tree candidates, executes movement and native block harvesting, then reports the
 * corresponding result back to this job.</p>
 */
public final class WoodcutterJob {

    public static final double CHOP_SECONDS = 2.5;

    private WorkTarget target;
    private double workElapsedSeconds;
    private WorkState state = WorkState.SEARCHING;

    public synchronized Intent intent() {
        return switch (state) {
            case SEARCHING -> new FindTreeIntent();
            case WALKING_TO_TREE ->
                new MoveToTreeIntent(new MovementIntent(target.interactionPoint()));
            case CHOPPING -> new ChopTreeIntent(
                target.tree(),
                Math.max(0.0, CHOP_SECONDS - workElapsedSeconds)
            );
            case READY_TO_FELL -> new FellTreeIntent(target.tree());
        };
    }

    public synchronized boolean assignTarget(WorkTarget target) {
        Objects.requireNonNull(target, "target");
        if (state != WorkState.SEARCHING) {
            return false;
        }

        this.target = target;
        workElapsedSeconds = 0.0;
        state = WorkState.WALKING_TO_TREE;
        return true;
    }

    public synchronized boolean movementArrived() {
        if (state != WorkState.WALKING_TO_TREE) {
            return false;
        }

        workElapsedSeconds = 0.0;
        state = WorkState.CHOPPING;
        return true;
    }

    public synchronized boolean advanceWork(double deltaSeconds) {
        if (deltaSeconds < 0.0) {
            throw new IllegalArgumentException("deltaSeconds must be >= 0");
        }
        if (state != WorkState.CHOPPING) {
            return false;
        }

        workElapsedSeconds += deltaSeconds;
        if (workElapsedSeconds + 1.0e-9 < CHOP_SECONDS) {
            return false;
        }

        state = WorkState.READY_TO_FELL;
        return true;
    }

    public synchronized boolean fellingCompleted() {
        if (state != WorkState.READY_TO_FELL) {
            return false;
        }

        reset();
        return true;
    }

    public synchronized void abandonTarget() {
        reset();
    }

    private void reset() {
        target = null;
        workElapsedSeconds = 0.0;
        state = WorkState.SEARCHING;
    }

    public synchronized BlockPosition targetTree() {
        return target == null ? null : target.tree();
    }

    public synchronized WorkTarget target() {
        return target;
    }

    public synchronized double workElapsedSeconds() {
        return workElapsedSeconds;
    }

    public synchronized WorkState state() {
        return state;
    }

    public record WorkTarget(BlockPosition tree, WorldPosition interactionPoint) {

        public WorkTarget {
            Objects.requireNonNull(tree, "tree");
            Objects.requireNonNull(interactionPoint, "interactionPoint");
        }
    }

    public sealed interface Intent
        permits FindTreeIntent, MoveToTreeIntent, ChopTreeIntent, FellTreeIntent {
    }

    public record FindTreeIntent() implements Intent {
    }

    public record MoveToTreeIntent(MovementIntent movement) implements Intent {

        public MoveToTreeIntent {
            Objects.requireNonNull(movement, "movement");
        }
    }

    public record ChopTreeIntent(BlockPosition tree, double remainingSeconds)
        implements Intent {

        public ChopTreeIntent {
            Objects.requireNonNull(tree, "tree");
            if (remainingSeconds < 0.0 || !Double.isFinite(remainingSeconds)) {
                throw new IllegalArgumentException(
                    "remainingSeconds must be finite and >= 0"
                );
            }
        }
    }

    public record FellTreeIntent(BlockPosition tree) implements Intent {

        public FellTreeIntent {
            Objects.requireNonNull(tree, "tree");
        }
    }

    public enum WorkState {
        SEARCHING,
        WALKING_TO_TREE,
        CHOPPING,
        READY_TO_FELL
    }
}
