package dev.civilizations.core;

import java.util.UUID;

/** Persistent logical point where future mine work may continue. */
public record MineWorkFront(UUID id, UUID tunnelId, BlockPosition position, State state) {
    public MineWorkFront {
        if (id == null || tunnelId == null || position == null || state == null) {
            throw new IllegalArgumentException("Mine work-front fields must not be null.");
        }
    }

    public enum State {
        OPEN,
        ACTIVE,
        BLOCKED,
        ABANDONED,
        COMPLETE
    }
}
