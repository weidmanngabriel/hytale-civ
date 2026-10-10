package dev.civilizations.simulation.local;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationEventLogTest {
    @Test void boundedAndReproducible() {
        var log = new SimulationEventLog();
        for (int i = 0; i < 205; i++) log.record(i, "BLOCK", "updated");
        assertEquals(200, log.snapshot().size());
        assertEquals(5, log.snapshot().getFirst().tick());
        assertEquals(204, log.snapshot().getLast().tick());
        var snapshot = log.snapshot();
        log.clear();
        assertTrue(log.snapshot().isEmpty());
        assertEquals(200, snapshot.size());
    }
}
