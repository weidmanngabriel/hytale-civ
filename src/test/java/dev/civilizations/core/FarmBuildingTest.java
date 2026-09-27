package dev.civilizations.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmBuildingTest {

    @Test
    void farmerProducesTenWheatAndLeavesAfterEveryProductionStep() {
        FarmBuilding farm = new FarmBuilding(
            "farm-test",
            new BlockPosition(10, 64, 10),
            new BlockPosition(10, 64, 8)
        );

        assertTrue(farm.assignFarmer("farmer-1"));
        assertEquals(FarmBuilding.WorkState.WALKING_TO_ENTRANCE, farm.workState());

        for (int expectedWheat = 1; expectedWheat <= FarmBuilding.WHEAT_TARGET; expectedWheat++) {
            assertTrue(farm.enterBuilding());
            assertEquals(FarmBuilding.WorkState.WORKING_INSIDE, farm.workState());

            assertFalse(farm.advanceWork(4.999));
            assertEquals(expectedWheat - 1, farm.wheat());

            assertTrue(farm.advanceWork(0.001));
            assertEquals(expectedWheat, farm.wheat());
            assertEquals(FarmBuilding.WorkState.LEAVING_BUILDING, farm.workState());

            assertTrue(farm.exitBuilding());
            FarmBuilding.WorkState expectedState = expectedWheat == FarmBuilding.WHEAT_TARGET
                ? FarmBuilding.WorkState.COMPLETE
                : FarmBuilding.WorkState.WALKING_TO_ENTRANCE;
            assertEquals(expectedState, farm.workState());
        }

        assertFalse(farm.advanceWork(100));
        assertEquals(FarmBuilding.WHEAT_TARGET, farm.wheat());
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
