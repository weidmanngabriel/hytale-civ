package dev.civilizations.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WoodcutterLogisticsSimulationTest {

    @Test
    void woodcutterReturnsWoodAndRecoversLeftoversInReproducibleFlatWorld() {
        var first = SimulationScenarios.WOODCUTTER_STORAGE.createRuntime();
        var repeat = SimulationScenarios.FORESTER_WOODCUTTER_FLAT.createRuntime();
        boolean sawGroundPile = false;
        boolean sawReturnTrip = false;

        for (int tick = 0; tick < 4_000; tick++) {
            first.tick();
            repeat.tick();
            sawGroundPile |= first.woodOnGround() > 0;
            sawReturnTrip |= "RETURNING_WITH_WOOD".equals(
                first.residentSnapshot("woodcutter-1").state());
            assertTrue(first.carriedWood("woodcutter-1") <= 200);
            assertEquals(first.woodInEconomy(), repeat.woodInEconomy());
            assertEquals(first.woodOnGround(), repeat.woodOnGround());
            assertEquals(first.storedWood(), repeat.storedWood());
            assertEquals(first.woodInEconomy(),
                first.storedWood() + first.woodOnGround() + first.carriedWood("woodcutter-1"));
        }
        assertEquals(3, first.metrics().treesFelled(), "all three mature trees harvested");
        assertEquals(315, first.storedWood(), "all wood delivered");
        assertEquals(0, first.woodOnGround());\n        assertEquals(0, first.carriedWood("woodcutter-1"));
        assertTrue(sawGroundPile, "large trees produce persistent overflow piles");
        assertTrue(sawReturnTrip, "worker physically returns to storage");
        assertEquals(first.worldSnapshot(), repeat.worldSnapshot());
    }
}
