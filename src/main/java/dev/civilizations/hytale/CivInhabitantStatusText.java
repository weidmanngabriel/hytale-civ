package dev.civilizations.hytale;

import dev.civilizations.core.Profession;

/**
 * Presentation-only mapping from authoritative Civ runtime state to short player-facing text.
 */
public final class CivInhabitantStatusText {

    private CivInhabitantStatusText() {
    }

    public static String derive(
        Profession profession,
        boolean manualMovementActive,
        boolean autonomousWorkAllowed,
        boolean hasMoveTarget
    ) {
        if (manualMovementActive) {
            return "Geht zum Ziel";
        }
        if (!autonomousWorkAllowed) {
            return "Macht kurze Pause";
        }
        if (profession == null) {
            return "Wartet";
        }

        return switch (profession) {
            case UNEMPLOYED -> "Arbeitslos";
            case WOODCUTTER -> hasMoveTarget ? "Geht zum Baum" : "Holzfäller";
            case FARMER -> hasMoveTarget ? "Geht zur Farmarbeit" : "Bauer";
            case MINER -> hasMoveTarget ? "Geht zur Mine" : "Minenabbauer";
            case CONSTRUCTION_WORKER -> hasMoveTarget ? "Geht zur Baustelle" : "Bauarbeiter";
        };
    }
}
