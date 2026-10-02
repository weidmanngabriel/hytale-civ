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
    void currentFarmAndFieldMetadataMatchesImplementedRoles() {
        assertEquals(1, BuildingTypes.workerCapacity("farm", 1));
        assertEquals(0, BuildingTypes.workerCapacity("wheat_field", 1));
    }

    @Test
    void unknownBuildingTypeDoesNotInventCapacity() {
        assertEquals(0, BuildingTypes.workerCapacity("unknown", 1));
    }
}
