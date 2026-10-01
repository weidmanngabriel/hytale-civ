package dev.civilizations.core;

/**
 * Current Civ inhabitant life stages. Gameplay effects can be added later; for now the stage is
 * persistent identity data used by presentation and appearance generation.
 */
public enum AgeStage {
    BABY("Baby"),
    CHILD("Child"),
    ADULT("Adult"),
    ELDER("Elder");

    private final String configName;

    AgeStage(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }
}
