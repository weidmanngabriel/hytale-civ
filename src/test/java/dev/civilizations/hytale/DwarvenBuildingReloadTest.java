package dev.civilizations.hytale;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DwarvenBuildingReloadTest {
    @Test
    void restoresAllDwarvenPhasesAsDwarvenAndPreservesHumanMines() {
        for (int phase = 1; phase <= 3; phase++) {
            var expected = PrefabPlacementService.minePhase("dwarf_mine", phase);
            var restored = CivBuildingPersistenceService.definitionForBuilding("dwarf_mine", phase);
            assertEquals(expected, restored);
            assertEquals("dwarf_mine", restored.id());
            assertEquals(PrefabPlacementService.minePhase(phase),
                CivBuildingPersistenceService.definitionForBuilding("mine", phase));
        }
        assertThrows(IllegalArgumentException.class,
            () -> CivBuildingPersistenceService.definitionForBuilding("other", 1));
    }
}
