package dev.civilizations.simulation.recording;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingOrientation;
import dev.civilizations.simulation.SimulationScenario;
import dev.civilizations.simulation.SimulationScenarios;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SimulationRecordingExporterTest {

    @Test
    void mainTunnelRecordingsUseCurrentVariableGeometryInEveryOrientation() throws Exception {
        for (BuildingOrientation orientation : BuildingOrientation.values()) {
            SimulationRecording recording = SimulationRecordingExporter.recordMine(orientation);
            assertEquals("completed", recording.status(), recording.error());
            assertEquals("semantic-slice", recording.timeUnit());
            assertEquals("Mine · Geometry", recording.title());
            assertFalse(recording.markers().isEmpty());
            assertTrue(recording.markers().stream().allMatch(marker -> marker.type().equals("main_tunnel")));
            assertTrue(recording.frames().getFirst().changes().isEmpty());
            assertEquals("COMPLETE", recording.frames().getLast().residents().getFirst().state());
            assertTrue(((Number) recording.frames().getLast().metrics().get("excavatedBlocks")).intValue() > 0);

            var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(recording));
            assertEquals(1, json.path("schemaVersion").asInt());
            assertTrue(json.path("initialVoxels").isArray());
            assertTrue(json.path("initialVoxels").isEmpty());
            assertEquals(6, json.path("rockBounds").size());
            assertTrue(json.path("frames").get(0).path("residents").get(0).path("position").has("x"));
            assertTrue(recording.frames().stream().mapToInt(frame -> frame.changes().size()).sum() > 0,
                "Record excavation as sparse deltas inside implicit rock");
        }
    }

    @Test
    void networkRecordingContainsBranchTunnelsAndReplaysToCarvedEndState() throws Exception {
        for (BuildingOrientation orientation : BuildingOrientation.values()) {
            SimulationRecording recording = SimulationRecordingExporter.recordBranchingMine(orientation);
            assertEquals("completed", recording.status(), recording.error());
            assertTrue(recording.markers().stream().anyMatch(marker -> marker.type().equals("branch_tunnel")),
                "Layer-4 recording should expose at least one branch tunnel");
            assertTrue(((Number) recording.frames().getLast().metrics().get("totalTunnels")).intValue() > 1);
            assertEquals(
                recording.frames().getLast().metrics().get("totalTunnels"),
                recording.frames().getLast().metrics().get("completedTunnels")
            );
            assertEquals("COMPLETE", recording.frames().getLast().residents().getFirst().state());
            assertEquals(6, recording.rockBounds().length);
            assertTrue(recording.initialVoxels().isEmpty());
            assertTrue(recording.frames().stream().flatMap(frame -> frame.changes().stream())
                .anyMatch(cell -> cell[3] == SimulationRecording.AIR),
                "Final replay should contain carved air inside implicit rock");
            assertTrue(new ObjectMapper().writeValueAsBytes(recording).length < 32 * 1024 * 1024,
                "Fits the publisher's recording size budget");
        }
    }

    @Test
    void straightFullScenarioExecutesAllPlannedCoreInfrastructure() throws Exception {
        SimulationRecording recording = SimulationRecordingExporter.recordStraightFull();
        assertEquals("completed", recording.status(), recording.error());
        assertEquals("mine-straight-full", recording.id());
        assertEquals("Mine · Straight Full", recording.title());
        assertEquals("semantic-step", recording.timeUnit());

        int planned = ((Number) recording.frames().getFirst().metrics().get("plannedInfrastructure")).intValue();
        int completed = ((Number) recording.frames().getLast().metrics().get("infrastructureCompleted")).intValue();
        long supports = recording.frames().stream()
            .filter(frame -> "BUILD_SUPPORT".equals(frame.metrics().get("phase"))).count();
        long lights = recording.frames().stream()
            .filter(frame -> "PLACE_LIGHT".equals(frame.metrics().get("phase"))).count();

        assertTrue(planned > 5, "Straight Full should contain repeated infrastructure work");
        assertEquals(planned, completed, "Every Core-planned infrastructure task should be replayed");
        assertTrue(supports > 1, "Straight Full should visibly build repeated supports");
        assertTrue(lights > 1, "Straight Full should visibly place repeated lights");
        assertTrue(recording.frames().stream().flatMap(frame -> frame.changes().stream())
            .anyMatch(cell -> cell[3] == SimulationRecording.SUPPORT));
        assertEquals("COMPLETE", recording.frames().getLast().metrics().get("phase"));
    }

    @Test
    void fullMineScenarioShowsCurrentNetworkInfrastructureObstaclesAndReentry() throws Exception {
        SimulationRecording recording = SimulationRecordingExporter.recordFullMine();
        assertEquals("completed", recording.status(), recording.error());
        assertEquals("semantic-step", recording.timeUnit());
        assertEquals("mine-full", recording.id());
        assertTrue(recording.initialVoxels().stream().anyMatch(cell -> cell[3] == SimulationRecording.PREFAB),
            "Full scenario should visibly start at a headless Mine prefab platform");
        assertEquals(6, recording.rockBounds().length);
        assertEquals("START_AT_PREFAB", recording.frames().getFirst().metrics().get("phase"));
        assertTrue(recording.markers().stream().anyMatch(marker ->
            marker.type().equals("workplace_access")));
        assertTrue(recording.markers().stream().anyMatch(marker ->
            marker.type().equals("mine_tunnel_connector")));
        assertTrue(recording.frames().stream().anyMatch(frame ->
            "ENTERING_MINE".equals(frame.metrics().get("phase"))));
        assertTrue(recording.frames().stream().anyMatch(frame ->
            "AT_CONNECTOR".equals(frame.metrics().get("phase"))));
        assertTrue(recording.frames().stream().anyMatch(frame -> {
            Set<String> tunnelIds = new HashSet<>();
            frame.residents().forEach(resident -> {
                Object tunnelId = resident.details().get("tunnelId");
                if (tunnelId != null) tunnelIds.add(tunnelId.toString());
            });
            return tunnelIds.size() >= 2;
        }), "Once multiple fronts are executable, miners should visibly split across tunnels");
        assertTrue(recording.frames().stream().anyMatch(frame ->
            "LEAVING_MINE".equals(frame.metrics().get("phase"))));
        assertTrue(recording.frames().stream().anyMatch(frame ->
            "OUTSIDE".equals(frame.metrics().get("phase"))));
        assertTrue(recording.frames().stream().anyMatch(frame ->
            "REENTERING_MINE".equals(frame.metrics().get("phase"))));
        assertTrue(recording.frames().stream().anyMatch(frame ->
            "BUILD_BRIDGE".equals(frame.metrics().get("phase"))));
        long supports = recording.frames().stream()
            .filter(frame -> "BUILD_SUPPORT".equals(frame.metrics().get("phase"))).count();
        long lights = recording.frames().stream()
            .filter(frame -> "PLACE_LIGHT".equals(frame.metrics().get("phase"))).count();
        assertTrue(supports > 1, "Full scenario should execute repeated support work, not one sample");
        assertTrue(lights > 1, "Full scenario should execute repeated lighting work, not one sample");
        assertTrue(recording.markers().stream().anyMatch(marker ->
            marker.type().equals("water_obstacle")));
        assertTrue(recording.markers().stream().anyMatch(marker ->
            marker.type().equals("lava_obstacle")));
        assertTrue(((Number) recording.frames().getLast().metrics().get("abandonedFronts")).intValue() >= 1);
        assertEquals(3, recording.frames().getFirst().residents().size());
        assertEquals("COMPLETE", recording.frames().getLast().metrics().get("phase"));
        assertTrue(new ObjectMapper().writeValueAsBytes(recording).length < 32 * 1024 * 1024,
            "Fits the publisher's recording size budget");
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
        for (int[] cell : recording.initialVoxels()) {
            world.put(new BlockPosition(cell[0], cell[1], cell[2]), cell[3]);
        }
        for (var frame : recording.frames()) {
            for (int[] cell : frame.changes()) {
                BlockPosition position = new BlockPosition(cell[0], cell[1], cell[2]);
                if (cell[3] == 0) world.remove(position); else world.put(position, cell[3]);
            }
        }
        return world;
    }
}
