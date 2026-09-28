package dev.civilizations.core;

import java.util.Objects;

/**
 * Core request for an inhabitant to reach a world position.
 *
 * <p>The engine adapter owns path finding and physical movement. The Core only owns the target
 * and the gameplay reason that selected it.</p>
 */
public record MovementIntent(WorldPosition destination) {

    public MovementIntent {
        Objects.requireNonNull(destination, "destination");
    }
}
