package dev.civilizations.simulation.local;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.simulation.SimulationRuntime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationEventJournalTest {
    @Test void reportsRealCoreStateTransitionsWithoutWritingGameplayState() {
        var runtime = new SimulationRuntime();
        runtime.addWoodcutter("wood",new WorldPosition(0,0,0));
        runtime.addTree(new BlockPosition(3,0,0));
        var trace = new SimulationEventJournal();
        trace.observe(runtime.worldSnapshot());
        for (int i=0;i<150;i++) {
            runtime.tick();
            trace.observe(runtime.worldSnapshot());
        }
        assertTrue(trace.events().stream().anyMatch(event -> event.resident().equals("wood")));
        assertTrue(trace.events().stream().allMatch(event -> event.tick()>0));
        trace.reset();
        assertTrue(trace.events().isEmpty());
    }
}
