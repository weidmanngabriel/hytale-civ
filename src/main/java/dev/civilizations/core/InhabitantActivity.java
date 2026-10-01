package dev.civilizations.core;

import java.util.Objects;

/**
 * Hytale-independent priority state shared by all inhabitant activities.
 *
 * <p>A direct player movement order temporarily suppresses autonomous work. After the movement
 * completes, a short shared resume delay keeps every profession from immediately snapping back
 * into autonomous work. The underlying profession task is never replaced by the override.</p>
 */
public final class InhabitantActivity {

    public static final double MANUAL_MOVE_RESUME_DELAY_SECONDS = 2.0;

    private MovementIntent manualMovement;
    private double resumeDelayRemainingSeconds;

    public synchronized void orderManualMove(WorldPosition destination) {
        manualMovement = new MovementIntent(
            Objects.requireNonNull(destination, "destination")
        );
        resumeDelayRemainingSeconds = 0.0;
    }

    public synchronized MovementIntent manualMovementIntent() {
        return manualMovement;
    }

    public synchronized void advance(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0.0) {
            throw new IllegalArgumentException("deltaSeconds must be finite and non-negative");
        }
        if (manualMovement != null || resumeDelayRemainingSeconds <= 0.0) {
            return;
        }
        resumeDelayRemainingSeconds = Math.max(
            0.0,
            resumeDelayRemainingSeconds - deltaSeconds
        );
    }

    public synchronized boolean autonomousWorkAllowed() {
        return manualMovement == null && resumeDelayRemainingSeconds <= 0.0;
    }

    public synchronized boolean completeManualMove() {
        if (manualMovement == null) {
            return false;
        }
        manualMovement = null;
        resumeDelayRemainingSeconds = MANUAL_MOVE_RESUME_DELAY_SECONDS;
        return true;
    }

    public synchronized boolean cancelManualMove() {
        if (manualMovement == null && resumeDelayRemainingSeconds <= 0.0) {
            return false;
        }
        manualMovement = null;
        resumeDelayRemainingSeconds = 0.0;
        return true;
    }

    public synchronized ActivitySnapshot snapshot() {
        ActivityMode mode;
        if (manualMovement != null) {
            mode = ActivityMode.MANUAL_MOVE;
        } else if (resumeDelayRemainingSeconds > 0.0) {
            mode = ActivityMode.RESUME_DELAY;
        } else {
            mode = ActivityMode.AUTONOMOUS;
        }
        return new ActivitySnapshot(
            mode,
            manualMovement,
            resumeDelayRemainingSeconds,
            autonomousWorkAllowed()
        );
    }

    public enum ActivityMode {
        MANUAL_MOVE,
        RESUME_DELAY,
        AUTONOMOUS
    }

    public record ActivitySnapshot(
        ActivityMode mode,
        MovementIntent manualMovement,
        double resumeDelayRemainingSeconds,
        boolean autonomousWorkAllowed
    ) {
    }
}
