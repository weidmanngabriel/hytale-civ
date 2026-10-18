package dev.civilizations.simulation.local;

import dev.civilizations.simulation.SimulationRuntime;
import dev.civilizations.simulation.SimulationScenario;
import dev.civilizations.simulation.world.WorldArchive;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Regression against the committed *actual* Hytale export, not a fabricated flat terrain. */
class ImportedForestScenarioTest {
    @Test void nativeSnapshotRunsAllThreeForestScenarios() throws Exception {
        var archive = WorldArchive.read(Path.of("src/test/resources/fixtures/region.civworld.gz"));
        var method = LocalSimulationServer.class.getDeclaredMethod("importedForestScenario", boolean.class, boolean.class);
        method.setAccessible(true);
        try (var server = new LocalSimulationServer(0, archive)) {
            for (boolean[] flags : new boolean[][]{{true, false}, {false, true}, {true, true}}) {
                var scenario = (SimulationScenario) method.invoke(server, flags[0], flags[1]);
                SimulationRuntime runtime = scenario.createRuntime();
                runtime.runTicks(3000);
                var snapshot = runtime.worldSnapshot();
                assertEquals(3000, snapshot.tickCount(), scenario.id());
                assertEquals(flags[0] && flags[1] ? 2 : 1, snapshot.residents().size(), scenario.id());
                if (flags[0]) assertFalse(snapshot.trees().isEmpty(), "Forester should establish trees in " + scenario.id());
                if (flags[1]) assertTrue(snapshot.metrics().treesFelled() > 0,
                    "Woodcutter must reach and fell an original tree in " + scenario.id());
            }
        }
    }
}
