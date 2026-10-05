package dev.civilizations.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable structured diagnostic event emitted where a mine decision actually happens. */
public record MineDecisionEvent(
    UUID mineId,
    UUID frontId,
    MineDecisionCategory category,
    String type,
    Map<String, String> details
) {
    public MineDecisionEvent {
        Objects.requireNonNull(mineId, "mineId");
        Objects.requireNonNull(category, "category");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Mine decision type must not be blank.");
        details = details == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static MineDecisionEvent of(
        UUID mineId,
        UUID frontId,
        MineDecisionCategory category,
        String type,
        Object... keyValues
    ) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Mine decision details require key/value pairs.");
        }
        Map<String, String> details = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            Object key = keyValues[index];
            if (!(key instanceof String stringKey) || stringKey.isBlank()) {
                throw new IllegalArgumentException("Mine decision detail keys must be non-blank strings.");
            }
            details.put(stringKey, String.valueOf(keyValues[index + 1]));
        }
        return new MineDecisionEvent(mineId, frontId, category, type, details);
    }

    public String format() {
        StringBuilder builder = new StringBuilder("[Civ Mine][mine=")
            .append(mineId)
            .append(']');
        if (frontId != null) builder.append("[front=").append(frontId).append(']');
        builder.append('[').append(category).append(']')
            .append('[').append(type).append(']');
        for (Map.Entry<String, String> entry : details.entrySet()) {
            builder.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }
}
