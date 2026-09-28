package dev.civilizations.core;

import java.util.Objects;

/**
 * Hytale-independent priority state shared by all inhabitant activities.
 *
 * <p>A direct player movement order temporarily suppresses autonomous work. Completing or
 * cancelling that order releases the override without modifying the underlying profession task.</p>
 */
public final class InhabitantActivity {

    private MovementIntent manualMovement;

    public synchronized void orderManualMove(WorldPosition destination) {
        manualMovement = new MovementIntent(
            Objects.requireNonNull(destination, "destination")
        );
    }

    public synchronized MovementIntent manualMovementIntent() {
        return manualMovement;
    }

    public synchronized boolean autonomousWorkAllowed() {
        return manualMovement == null;
    }

    public synchronized boolean completeManualMove() {
        if (manualMovement == null) {
            return false;
        }
        manualMovement = null;
        return true;
    }

    public synchronized boolean cancelManualMove() {
        return completeManualMove();
    }
}
