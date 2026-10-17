package dev.civilizations.simulation;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class ForesterFlatWorldTest {
    @Test void forestersPlantAndTreesGrowOnFlatWorld() {
        var a = SimulationScenarios.FORESTER_FLAT.createRuntime();
        var b = SimulationScenarios.FORESTER_FLAT.createRuntime();
        a.runTicks(1200);
        b.runTicks(1200);
        assertEquals(a.worldSnapshot(), b.worldSnapshot(), "fixed seeds must reproduce the same world");
        assertTrue(a.growingTreeCount() > 0 || !a.worldSnapshot().trees().isEmpty());
        var sites = new HashSet<>(a.worldSnapshot().trees().stream().map(SimulationRuntime.TreeSnapshot::position).toList());
        assertEquals(sites.size(), a.worldSnapshot().trees().size(), "tree sites must be unique");
    }
}
