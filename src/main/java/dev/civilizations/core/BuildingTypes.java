package dev.civilizations.core;

import java.util.List;
import java.util.Map;

/** Central catalog for currently known Civ building types. */
public final class BuildingTypes {
    private static final Map<String, BuildingTypeDefinition> TYPES = Map.of(
        "farm", new BuildingTypeDefinition(
            "farm",
            List.of(new BuildingTypeDefinition.PhaseDefinition(1, 1))
        ),
        "mine", new BuildingTypeDefinition(
            "mine",
            List.of(
                new BuildingTypeDefinition.PhaseDefinition(1, 1),
                new BuildingTypeDefinition.PhaseDefinition(2, 2),
                new BuildingTypeDefinition.PhaseDefinition(3, 3)
            )
        ),
        "dwarf_mine", new BuildingTypeDefinition(
            "dwarf_mine",
            List.of(
                new BuildingTypeDefinition.PhaseDefinition(1, 1),
                new BuildingTypeDefinition.PhaseDefinition(2, 2),
                new BuildingTypeDefinition.PhaseDefinition(3, 3)
            )
        ),
        "wheat_field", new BuildingTypeDefinition(
            "wheat_field",
            List.of(new BuildingTypeDefinition.PhaseDefinition(1, 0))
        )
    );

    private BuildingTypes() {
    }

    public static BuildingTypeDefinition find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return TYPES.get(id);
    }

    public static int workerCapacity(String id, int phase) {
        BuildingTypeDefinition definition = find(id);
        return definition == null ? 0 : definition.workerCapacity(phase);
    }

    /** Returns the next authored phase, or 0 when no further phase exists. */
    public static int nextPhase(String id, int currentPhase) {
        BuildingTypeDefinition definition = find(id);
        return definition == null ? 0 : definition.nextPhase(currentPhase);
    }

    public static int maxPhase(String id) {
        BuildingTypeDefinition definition = find(id);
        return definition == null ? 0 : definition.maxPhase();
    }
}
