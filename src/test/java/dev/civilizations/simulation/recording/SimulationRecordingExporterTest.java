package dev.civilizations.simulation.recording;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingOrientation;
import dev.civilizations.simulation.SimulationScenario;
import dev.civilizations.simulation.SimulationScenarios;
import dev.civilizations.simulation.prefab.MinePrefabNavigationScenario;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class SimulationRecordingExporterTest {
    @Test
    void recordingReplaysEveryMineOrientationToTheActualScenarioEndState() throws Exception {
        for (BuildingOrientation orientation : BuildingOrientation.values()) {
            SimulationRecording recording = SimulationRecordingExporter.recordMine(orientation);
            assertEquals("completed", recording.status(), recording.error());
            assertEquals("semantic-step", recording.timeUnit());
            assertFalse(recording.markers().isEmpty());
            assertTrue(recording.frames().getFirst().changes().isEmpty());
            MinePrefabNavigationScenario scenario = MinePrefabNavigationScenario.create(orientation);
            scenario.runToCompletion();
            assertEquals(SimulationRecordingExporter.mineWorld(scenario.snapshot()), replay(recording));
            var last = recording.frames().getLast();
            assertTrue(last.residents().getFirst().state().startsWith("COMPLETE"));
            assertEquals(scenario.snapshot().segment().blockCount(), last.metrics().get("excavatedBlocks"));
            // Verify the actual browser wire format, including position objects and compact deltas.
            var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(recording));
            assertEquals(1, json.path("schemaVersion").asInt());
            assertTrue(json.path("initialVoxels").get(0).isArray());
            assertTrue(json.path("frames").get(0).path("residents").get(0).path("position").has("x"));
            assertTrue(recording.frames().stream().mapToInt(f -> f.changes().size()).sum()
                < recording.initialVoxels().size(), "Record deltas rather than repeating the mountain");
        }
    }

    @Test
    void allSharedRuntimeScenariosRecordTheSameWorldAndResidentStateAsDirectExecution() {
        for (SimulationScenario scenario : SimulationScenarios.all()) {
            var recording = SimulationRecordingExporter.recordRuntime(scenario, 100);
            assertEquals("completed", recording.status(), recording.error());
            var runtime = scenario.createRuntime();
            runtime.runTicks(100);
            assertEquals(SimulationRecordingExporter.runtimeWorld(runtime.worldSnapshot()), replay(recording));
            assertEquals(runtime.elapsedSeconds(), recording.frames().getLast().time());
            assertEquals(runtime.worldSnapshot().residents().size(), recording.frames().getLast().residents().size());
            for (var resident : runtime.worldSnapshot().residents()) {
                var recorded = recording.frames().getLast().residents().stream()
                    .filter(r -> r.id().equals(resident.id())).findFirst().orElseThrow();
                assertEquals(resident.position(), recorded.position());
                assertEquals(resident.state(), recorded.state());
            }
        }
    }

    @Test
    void failedScenarioStillProducesAReadableFailureRecording() {
        var broken = new SimulationScenario("broken", "Broken", "Failure diagnostic", () -> {
            throw new IllegalStateException("fixture unavailable");
        });
        var recording = SimulationRecordingExporter.recordRuntime(broken, 100);
        assertEquals("failed", recording.status());
        assertTrue(recording.error().contains("fixture unavailable"));
        assertEquals(1, recording.frames().size());
        assertTrue(recording.initialVoxels().isEmpty());
    }

    private static Map<BlockPosition, Integer> replay(SimulationRecording recording) {
        Map<BlockPosition, Integer> world = new HashMap<>();
        for (int[] c : recording.initialVoxels()) world.put(new BlockPosition(c[0], c[1], c[2]), c[3]);
        for (var frame : recording.frames()) for (int[] c : frame.changes()) {
            BlockPosition p = new BlockPosition(c[0], c[1], c[2]);
            if (c[3] == 0) world.remove(p); else world.put(p, c[3]);
        }
        return world;
    }
}
