package dev.civilizations.hytale;

import dev.civilizations.core.Profession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivInhabitantStatusTextTest {

    @Test
    void manualMovementAndResumeDelayOverrideProfessionText() {
        assertEquals(
            "Geht zum Ziel",
            CivInhabitantStatusText.derive(Profession.WOODCUTTER, true, false, true)
        );
        assertEquals(
            "Macht kurze Pause",
            CivInhabitantStatusText.derive(Profession.WOODCUTTER, false, false, false)
        );
    }

    @Test
    void autonomousProfessionTextUsesCurrentMovementState() {
        assertEquals(
            "Arbeitslos",
            CivInhabitantStatusText.derive(Profession.UNEMPLOYED, false, true, false)
        );
        assertEquals(
            "Geht zum Baum",
            CivInhabitantStatusText.derive(Profession.WOODCUTTER, false, true, true)
        );
        assertEquals(
            "Holzfäller",
            CivInhabitantStatusText.derive(Profession.WOODCUTTER, false, true, false)
        );
        assertEquals(
            "Geht zur Farmarbeit",
            CivInhabitantStatusText.derive(Profession.FARMER, false, true, true)
        );
        assertEquals(
            "Bauer",
            CivInhabitantStatusText.derive(Profession.FARMER, false, true, false)
        );
        assertEquals(
            "Geht zur Baustelle",
            CivInhabitantStatusText.derive(Profession.CONSTRUCTION_WORKER, false, true, true)
        );
        assertEquals(
            "Bauarbeiter",
            CivInhabitantStatusText.derive(Profession.CONSTRUCTION_WORKER, false, true, false)
        );
    }
}
