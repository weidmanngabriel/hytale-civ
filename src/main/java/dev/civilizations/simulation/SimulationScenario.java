package dev.civilizations.simulation;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Named deterministic start state shared by the desktop viewer and headless scenario tests.
 *
 * <p>A scenario defines only tick-zero world state. Assertions and expected outcomes belong in
 * tests, not in the scenario itself, so the same setup can also be explored interactively.</p>
 */
public record SimulationScenario(
    String id,
    String displayName,
    String description,
    Supplier<SimulationRuntime> runtimeFactory
) {

    public SimulationScenario {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }
        description = Objects.requireNonNull(description, "description");
        runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
    }

    public SimulationRuntime createRuntime() {
        return Objects.requireNonNull(
            runtimeFactory.get(),
            "scenario runtimeFactory returned null"
        );
    }

    @Override
    public String toString() {
        return displayName;
    }
}
