package dev.civilizations.core;

import java.util.Comparator;
import java.util.List;

/**
 * Hytale-independent metadata for one Civ building type.
 *
 * <p>Worker capacity is descriptive metadata for now. It does not enforce assignment limits.</p>
 */
public record BuildingTypeDefinition(
    String id,
    List<PhaseDefinition> phases
) {
    public BuildingTypeDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Building type id cannot be blank.");
        }
        phases = List.copyOf(phases == null ? List.of() : phases);
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("Building type requires at least one phase.");
        }
    }

    public PhaseDefinition phase(int phase) {
        return phases.stream()
            .filter(candidate -> candidate.phase() == phase)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown phase " + phase + " for building type " + id
            ));
    }

    public int workerCapacity(int phase) {
        return phase(phase).workerCapacity();
    }

    /** Returns the next authored phase, or 0 when the building is fully upgraded. */
    public int nextPhase(int currentPhase) {
        return phases.stream()
            .map(PhaseDefinition::phase)
            .filter(candidate -> candidate > currentPhase)
            .min(Comparator.naturalOrder())
            .orElse(0);
    }

    public int maxPhase() {
        return phases.stream()
            .mapToInt(PhaseDefinition::phase)
            .max()
            .orElseThrow();
    }

    public record PhaseDefinition(int phase, int workerCapacity) {
        public PhaseDefinition {
            if (phase < 1) {
                throw new IllegalArgumentException("Building phase must be at least 1.");
            }
            if (workerCapacity < 0) {
                throw new IllegalArgumentException("Worker capacity cannot be negative.");
            }
        }
    }
}
