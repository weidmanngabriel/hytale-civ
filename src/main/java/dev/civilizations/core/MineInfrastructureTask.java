package dev.civilizations.core;

import java.util.Objects;
import java.util.UUID;

/** Semantic mine infrastructure/decor work unit owned by Civ core. */
public record MineInfrastructureTask(
    UUID id,
    UUID tunnelId,
    Type type,
    int priority,
    int startSliceIndex,
    int endSliceIndex,
    BlockPosition anchor,
    DecorationKind decorationKind
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
        if (type == Type.PLACE_DECORATION && decorationKind == null) {
            throw new IllegalArgumentException("decoration work requires a decoration kind");
        }
        if (type != Type.PLACE_DECORATION && decorationKind != null) {
            throw new IllegalArgumentException("only decoration work may carry a decoration kind");
        }
    }

    public MineInfrastructureTask(
        UUID id,
        UUID tunnelId,
        Type type,
        int priority,
        int startSliceIndex,
        int endSliceIndex,
        BlockPosition anchor
    ) {
        this(id, tunnelId, type, priority, startSliceIndex, endSliceIndex, anchor, null);
    }

    public boolean mandatory() {
        return priority == 10;
    }

    public boolean decoration() {
        return type == Type.PLACE_DECORATION;
    }

    public enum Type {
        BUILD_SUPPORT,
        BUILD_STEP,
        BUILD_BRIDGE,
        PLACE_LIGHT,
        PLACE_DECORATION
    }

    public enum DecorationKind {
        BARREL,
        CRATE,
        TIMBER_PILE,
        MATERIAL_PILE,
        HANGING_CHAIN,
        HANGING_LANTERN
    }
}
