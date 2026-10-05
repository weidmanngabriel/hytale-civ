package dev.civilizations.core;

import java.util.UUID;

/** Important room or chamber attached to one logical tunnel. */
public record MineRoom(UUID id, UUID tunnelId, Type type, BlockPosition position) {
    public MineRoom {
        if (id == null || tunnelId == null || type == null || position == null) {
            throw new IllegalArgumentException("Mine room fields must not be null.");
        }
    }

    public enum Type {
        REST_ACCOMMODATION,
        MATERIAL_STORAGE,
        TOOL_WORKSHOP,
        ORE_COLLECTION,
        LARGE_NATURAL_CHAMBER,
        SMALL_NICHE,
        SUPPORT_SUPPLY,
        WATER_DRAINAGE,
        LARGE_WORK_HALL
    }
}
