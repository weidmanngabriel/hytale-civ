package dev.civilizations.core;

import java.util.Objects;
import java.util.UUID;

/** Semantic mine infrastructure work unit owned by Civ core. */
public record MineInfrastructureTask(
    UUID id,
    UUID tunnelId,
    Type type,
    int priority,
    int startSliceIndex,
    int endSliceIndex,
    BlockPosition anchor
) {
    public MineInfrastructureTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tunnelId, "tunnelId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(anchor, "anchor");
        if (priority < 1 || priority > 10) {
            throw new IllegalArgumentException("priority must be between 1 and 10");
        }
        if (startSliceIndex < 0 || endSliceIndex < startSliceIndex) {
            throw new IllegalArgumentException("slice range must be valid");
        }
    }

    public boolean mandatory() {
        return priority == 10;
    }

    public enum Type {
        BUILD_SUPPORT,
        BUILD_STEP,
        BUILD_BRIDGE,
        PLACE_LIGHT
    }
}
