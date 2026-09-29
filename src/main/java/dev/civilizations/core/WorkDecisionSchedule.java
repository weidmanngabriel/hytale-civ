package dev.civilizations.core;

/**
 * Hytale-independent cadence for autonomous work planning.
 *
 * <p>Expensive planning should happen either because an event requests an immediate decision or
 * because a bounded retry delay elapsed. The schedule itself is cheap to advance every tick and
 * deliberately contains no world-query logic.</p>
 */
public final class WorkDecisionSchedule {

    private static final double EPSILON = 1.0e-9;

    private boolean immediateRequested = true;
    private double retryRemainingSeconds = Double.POSITIVE_INFINITY;

    public synchronized DecisionKind advance(double deltaSeconds) {
        if (deltaSeconds < 0.0 || !Double.isFinite(deltaSeconds)) {
            throw new IllegalArgumentException("deltaSeconds must be finite and >= 0");
        }

        if (immediateRequested) {
            immediateRequested = false;
            retryRemainingSeconds = Double.POSITIVE_INFINITY;
            return DecisionKind.IMMEDIATE;
        }

        if (!Double.isFinite(retryRemainingSeconds)) {
            return DecisionKind.NONE;
        }

        retryRemainingSeconds -= deltaSeconds;
        if (retryRemainingSeconds > EPSILON) {
            return DecisionKind.NONE;
        }

        retryRemainingSeconds = Double.POSITIVE_INFINITY;
        return DecisionKind.RETRY;
    }

    public synchronized void requestImmediate() {
        immediateRequested = true;
        retryRemainingSeconds = Double.POSITIVE_INFINITY;
    }

    public synchronized void scheduleRetry(double delaySeconds) {
        if (delaySeconds < 0.0 || !Double.isFinite(delaySeconds)) {
            throw new IllegalArgumentException("delaySeconds must be finite and >= 0");
        }
        immediateRequested = false;
        retryRemainingSeconds = delaySeconds;
    }

    public synchronized void cancel() {
        immediateRequested = false;
        retryRemainingSeconds = Double.POSITIVE_INFINITY;
    }

    public synchronized boolean immediateRequested() {
        return immediateRequested;
    }

    public synchronized double retryRemainingSeconds() {
        return retryRemainingSeconds;
    }

    public enum DecisionKind {
        NONE,
        IMMEDIATE,
        RETRY
    }
}
