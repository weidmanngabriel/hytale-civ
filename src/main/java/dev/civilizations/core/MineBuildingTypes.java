package dev.civilizations.core;

/** Classification shared by all mine workplaces; presentation and tunnel style remain separate. */
public final class MineBuildingTypes {
    public static final String HUMAN = "mine";
    public static final String DWARVEN = "dwarf_mine";

    private MineBuildingTypes() {
    }

    public static boolean isMine(String buildingType) {
        return HUMAN.equals(buildingType) || DWARVEN.equals(buildingType);
    }

    public static boolean isDwarven(String buildingType) {
        return DWARVEN.equals(buildingType);
    }
}
