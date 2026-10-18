package dev.civilizations.simulation;

import dev.civilizations.core.Profession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForesterWoodcutterFlatWorldTest {
    @Test
    void oneForesterAndOneWoodcutterShareRenewableTreeSupply() {
        var first = SimulationScenarios.FORESTER_WOODCUTTER_FLAT.createRuntime();
        var repeat = SimulationScenarios.FORESTER_WOODCUTTER_FLAT.createRuntime();

        assertEquals(2, first.worldSnapshot().residents().size());
        assertEquals(1, first.worldSnapshot().residents().stream()
            .filter(r -> r.profession() == Profession.FORESTER).count());
        assertEquals(1, first.worldSnapshot().residents().stream()
            .filter(r -> r.profession() == Profession.WOODCUTTER).count());
        assertTrue(first.worldSnapshot().trees().isEmpty(), "no pre-planted mature trees");

        first.runTicks(4000);
        repeat.runTicks(4000);

        assertEquals(first.worldSnapshot(), repeat.worldSnapshot(), "seeded run must reproduce");
        assertTrue(first.metrics().treesFelled() > 0, "woodcutter must fell forester-grown trees");
        assertTrue(first.growingTreeCount() > 0 || !first.worldSnapshot().trees().isEmpty(),
            "forester must continue planting");
    }
}
