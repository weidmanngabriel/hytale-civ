package dev.civilizations.core;

import java.util.UUID;

/** Optional observer for mine decisions. Implementations must not influence gameplay state. */
public interface MineDecisionSink {

    MineDecisionSink NONE = new MineDecisionSink() {
        @Override
        public boolean enabled(MineDecisionCategory category) {
            return false;
        }

        @Override
        public void record(MineDecisionEvent event) {
        }
    };

    boolean enabled(MineDecisionCategory category);

    void record(MineDecisionEvent event);

    default void record(
        UUID mineId,
        UUID frontId,
        MineDecisionCategory category,
        String type,
        Object... keyValues
    ) {
        if (!enabled(category)) return;
        record(MineDecisionEvent.of(mineId, frontId, category, type, keyValues));
    }
}
