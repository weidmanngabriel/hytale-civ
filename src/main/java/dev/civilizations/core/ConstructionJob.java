package dev.civilizations.core;

import java.util.Objects;

/**
 * Hytale-independent state machine for one construction worker assignment.
 *
 * <p>The adapter chooses a construction site and maps each completed build step to real
 * prefab materialization. Core owns only the worker lifecycle and pacing.</p>
 */
public final class ConstructionJob {

    public static final double STEP_SECONDS = 1.0;

    private WorkTarget target;
    private int completedSteps;
    private double stepElapsedSeconds;
    private WorkState state = WorkState.SEARCHING;

    public synchronized Intent intent() {
        return switch (state) {
            case SEARCHING -> new FindConstructionSiteIntent();
            case WALKING_TO_SITE ->
                new MoveToConstructionSiteIntent(new MovementIntent(target.workPoint()));
            case BUILDING -> new BuildIntent(
                target.siteId(),
                completedSteps,
                target.totalSteps(),
                Math.max(0.0, STEP_SECONDS - stepElapsedSeconds)
            );
            case READY_TO_COMPLETE -> new CompleteConstructionIntent(target.siteId());
        };
    }

    public synchronized boolean assignTarget(WorkTarget target) {
        Objects.requireNonNull(target, "target");
        if (state != WorkState.SEARCHING) {
            return false;
        }
        this.target = target;
        completedSteps = 0;
        stepElapsedSeconds = 0.0;
        state = WorkState.WALKING_TO_SITE;
        return true;
    }

    public synchronized boolean movementArrived() {
        if (state != WorkState.WALKING_TO_SITE) {
            return false;
        }
        stepElapsedSeconds = 0.0;
        state = WorkState.BUILDING;
        return true;
    }

    /**
     * Advances work and returns the number of newly completed construction steps.
     */
    public synchronized int advanceWork(double deltaSeconds) {
        if (deltaSeconds < 0.0 || !Double.isFinite(deltaSeconds)) {
            throw new IllegalArgumentException("deltaSeconds must be finite and >= 0");
        }
        if (state != WorkState.BUILDING) {
            return 0;
        }

        stepElapsedSeconds += deltaSeconds;
        int newlyCompleted = 0;
        while (stepElapsedSeconds + 1.0e-9 >= STEP_SECONDS
            && completedSteps < target.totalSteps()) {
            stepElapsedSeconds -= STEP_SECONDS;
            completedSteps++;
            newlyCompleted++;
        }

        if (completedSteps >= target.totalSteps()) {
            stepElapsedSeconds = 0.0;
            state = WorkState.READY_TO_COMPLETE;
        }
        return newlyCompleted;
    }

    public synchronized boolean constructionCompleted() {
        if (state != WorkState.READY_TO_COMPLETE) {
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
        completedSteps = 0;
        stepElapsedSeconds = 0.0;
        state = WorkState.SEARCHING;
    }

    public synchronized WorkTarget target() {
        return target;
    }

    public synchronized int completedSteps() {
        return completedSteps;
    }

    public synchronized WorkState state() {
        return state;
    }

    public record WorkTarget(
        String siteId,
        WorldPosition workPoint,
        int totalSteps
    ) {
        public WorkTarget {
            if (siteId == null || siteId.isBlank()) {
                throw new IllegalArgumentException("siteId cannot be blank");
            }
            Objects.requireNonNull(workPoint, "workPoint");
            if (totalSteps <= 0) {
                throw new IllegalArgumentException("totalSteps must be > 0");
            }
        }
    }

    public sealed interface Intent permits
        FindConstructionSiteIntent,
        MoveToConstructionSiteIntent,
        BuildIntent,
        CompleteConstructionIntent {
    }

    public record FindConstructionSiteIntent() implements Intent {
    }

    public record MoveToConstructionSiteIntent(MovementIntent movement) implements Intent {
        public MoveToConstructionSiteIntent {
            Objects.requireNonNull(movement, "movement");
        }
    }

    public record BuildIntent(
        String siteId,
        int completedSteps,
        int totalSteps,
        double remainingStepSeconds
    ) implements Intent {
    }

    public record CompleteConstructionIntent(String siteId) implements Intent {
    }

    public enum WorkState {
        SEARCHING,
        WALKING_TO_SITE,
        BUILDING,
        READY_TO_COMPLETE
    }
}
