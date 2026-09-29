package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FarmBuildingTest {

    @Test
    void farmerHarvestsThenWaitsUntilPhysicalOutputIsStored() {
        FarmBuilding farm = new FarmBuilding(
            "farm-test",
            new BlockPosition(10, 64, 10),
            new BlockPosition(10, 64, 8)
        );

        assertTrue(farm.assignFarmer("farmer-1"));
        assertEquals(FarmBuilding.WorkState.WALKING_TO_FARM, farm.workState());

        assertTrue(farm.arriveAtFarm());
        assertEquals(FarmBuilding.WorkState.WALKING_TO_FIELD, farm.workState());
        assertTrue(farm.arriveAtField());

        assertFalse(farm.advanceWork(4.999));
        assertTrue(farm.advanceWork(0.001));
        assertEquals(FarmBuilding.WorkState.RETURNING_TO_STORAGE, farm.workState());

        assertTrue(farm.arriveAtFarm());
        assertEquals(FarmBuilding.WorkState.STORING_OUTPUT, farm.workState());

        assertTrue(farm.outputStored());
        assertEquals(FarmBuilding.WorkState.WALKING_TO_FIELD, farm.workState());
    }

    @Test
    void farmHasOnlyOneFarmerSlot() {
        FarmBuilding farm = new FarmBuilding(
            "farm-test",
            new BlockPosition(0, 0, 0),
            new BlockPosition(0, 0, -2)
        );

        assertTrue(farm.assignFarmer("farmer-1"));
        assertFalse(farm.assignFarmer("farmer-2"));
        assertEquals("farmer-1", farm.farmerId());
    }
}
