package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class BuildingTypesTest {

    @Test
    void mineCapacityGrowsWithPhase() {
        BuildingTypeDefinition mine = BuildingTypes.find("mine");

        assertNotNull(mine);
        assertEquals(1, mine.workerCapacity(1));
        assertEquals(2, mine.workerCapacity(2));
        assertEquals(3, mine.workerCapacity(3));
    }

    @Test
    void mineUpgradeProgressionStopsAfterPhaseThree() {
        assertEquals(2, BuildingTypes.nextPhase("mine", 1));
        assertEquals(3, BuildingTypes.nextPhase("mine", 2));
        assertEquals(0, BuildingTypes.nextPhase("mine", 3));
        assertEquals(3, BuildingTypes.maxPhase("mine"));
    }

    @Test
    void singlePhaseBuildingsDoNotOfferAnUpgrade() {
        assertEquals(0, BuildingTypes.nextPhase("farm", 1));
        assertEquals(0, BuildingTypes.nextPhase("wheat_field", 1));
    }

    @Test
    void currentFarmAndFieldMetadataMatchesImplementedRoles() {
        assertEquals(1, BuildingTypes.workerCapacity("farm", 1));
        assertEquals(0, BuildingTypes.workerCapacity("wheat_field", 1));
    }

    @Test
    void unknownBuildingTypeDoesNotInventCapacityOrUpgrade() {
        assertEquals(0, BuildingTypes.workerCapacity("unknown", 1));
        assertEquals(0, BuildingTypes.nextPhase("unknown", 1));
        assertEquals(0, BuildingTypes.maxPhase("unknown"));
    }
}
