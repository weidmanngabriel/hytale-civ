package dev.civilizations.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Data shared by production professions. It describes goods and work time, not
 * where goods come from or how Hytale movement/container access is executed.
 */
public record ProductionRecipe(
    String id,
    Map<String, Integer> inputs,
    Map<String, Integer> outputs,
    double baseWorkSeconds
) {
    public ProductionRecipe {
        Objects.requireNonNull(id, "id");
        inputs = validatedCopy(inputs, "inputs");
        outputs = validatedCopy(outputs, "outputs");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (outputs.isEmpty()) throw new IllegalArgumentException("outputs must not be empty");
        if (!(baseWorkSeconds > 0.0) || !Double.isFinite(baseWorkSeconds)) {
            throw new IllegalArgumentException("baseWorkSeconds must be finite and > 0");
        }
    }

    private static Map<String, Integer> validatedCopy(Map<String, Integer> goods, String name) {
        Objects.requireNonNull(goods, name);
        Map<String, Integer> copy = new LinkedHashMap<>();
        goods.forEach((good, quantity) -> {
            if (good == null || good.isBlank()) throw new IllegalArgumentException(name + " contains blank good");
            if (quantity == null || quantity <= 0) throw new IllegalArgumentException(name + " quantities must be > 0");
            copy.put(good, quantity);
        });
        return Map.copyOf(copy);
    }
}
