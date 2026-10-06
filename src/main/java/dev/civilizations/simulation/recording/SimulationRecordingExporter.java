package dev.civilizations.simulation.recording;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingOrientation;
import dev.civilizations.core.MineHeading;
import dev.civilizations.core.MineNetworkGrowthPlanner;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineTunnelGeometry;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.simulation.SimulationRuntime;
import dev.civilizations.simulation.SimulationScenario;
import dev.civilizations.simulation.SimulationScenarios;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.civilizations.simulation.recording.SimulationRecording.*;

/** Executes Hytale-independent fixtures headlessly and exports data for browser replay. */
public final class SimulationRecordingExporter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int RUNTIME_TICKS = 600;
    private static final BlockPosition MINE_ORIGIN = new BlockPosition(0, 40, 0);

    private SimulationRecordingExporter() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "build/simulation-recordings" : args[0]);
        Files.createDirectories(output);
        List<Map<String, Object>> scenarios = new ArrayList<>();
        boolean failed = false;
        for (BuildingOrientation orientation : BuildingOrientation.values()) {
            SimulationRecording recording = recordMine(orientation);
            write(output, recording, scenarios);
            failed |= recording.status().equals("failed");
            SimulationRecording network = recordBranchingMine(orientation);
            write(output, network, scenarios);
            failed |= network.status().equals("failed");
        }
        for (SimulationScenario scenario : SimulationScenarios.all()) {
            SimulationRecording recording = recordRuntime(scenario, RUNTIME_TICKS);
            write(output, recording, scenarios);
            failed |= recording.status().equals("failed");
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", SCHEMA_VERSION);
        manifest.put("commit", environment("SIM_VIEWER_COMMIT", "local"));
        manifest.put("branch", environment("SIM_VIEWER_BRANCH", "local"));
        manifest.put("runId", environment("GITHUB_RUN_ID", "local"));
        manifest.put("runAttempt", environment("GITHUB_RUN_ATTEMPT", "1"));
        manifest.put("scenarios", scenarios);
        JSON.writeValue(output.resolve("manifest.json").toFile(), manifest);
        System.out.println("Exported " + scenarios.size() + " scenarios to " + output);
        if (failed) throw new IllegalStateException("Scenario failure; partial recordings were exported.");
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void write(Path output, SimulationRecording recording,
                              List<Map<String, Object>> scenarios) throws Exception {
        String filename = recording.id() + ".json";
        JSON.writeValue(output.resolve(filename).toFile(), recording);
        scenarios.add(Map.of("id", recording.id(), "title", recording.title(),
            "file", filename, "status", recording.status(), "frames", recording.frames().size()));
    }

    public static SimulationRecording recordMine(BuildingOrientation orientation) {
        return recordPlannedMine(
            "mine-" + orientation.name().toLowerCase(Locale.ROOT),
            "Mine · " + orientation.name(),
            "Aktuelle Layer-2/3-Minengeometrie: ein variabler Hauptstollen wird Slice für Slice aus dem geplanten Felsvolumen entfernt. Reine Core-Aufzeichnung; keine Hytale-Navigation.",
            orientation, 72, 1, 123456789L
        );
    }

    public static SimulationRecording recordBranchingMine(BuildingOrientation orientation) {
        return recordPlannedMine(
            "mine-combinations-" + orientation.name().toLowerCase(Locale.ROOT),
            "Mine · Netzwerk · " + orientation.name(),
            "Aktuelles MineNetwork mit variablem Hauptstollen und geplanten Seitenstollen. Die Aufzeichnung gräbt die vom Layer-4-Plan erzeugten Layer-3-Slices in Topologie-Reihenfolge aus. Reine Core-Aufzeichnung; keine Hytale-Navigation oder zweite Gameplay-Logik.",
            orientation, 220, 24, 99887766L
        );
    }

    private static SimulationRecording recordPlannedMine(
        String id,
        String title,
        String description,
        BuildingOrientation orientation,
        int mainLength,
        int tunnelBudget,
        long seed
    ) {
        Recorder recorder = null;
        String error = null;
        List<Marker> markers = List.of();
        try {
            UUID mineId = UUID.nameUUIDFromBytes((id + ":" + seed).getBytes(StandardCharsets.UTF_8));
            MineNetworkGrowthPlanner.Plan plan = MineNetworkGrowthPlanner.plan(
                mineId, MINE_ORIGIN, orientation.rotate(MineHeading.NORTH), mainLength, tunnelBudget, seed
            );
            Map<BlockPosition, Integer> world = rockEnvelope(plan.tunnels());
            recorder = new Recorder(world);
            markers = plan.tunnels().stream().map(SimulationRecordingExporter::tunnelMarker).toList();

            var main = plan.mainTunnel();
            BlockPosition start = main.geometry().slices().getFirst().floorCenter();
            recorder.capture(0, world,
                List.of(resident(start, null, "PLANNED", main.tunnel(), 0, 0)),
                null, metrics(plan, 0, 0, 0, main.tunnel()));

            int step = 0;
            int completedTunnels = 0;
            int slicesExcavated = 0;
            Set<BlockPosition> excavated = new LinkedHashSet<>();
            List<MineNetworkGrowthPlanner.PlannedTunnel> ordered = topologicalOrder(plan);
            for (var tunnel : ordered) {
                List<MineTunnelGeometry.Slice> slices = tunnel.geometry().slices();
                for (int sliceIndex = 0; sliceIndex < slices.size(); sliceIndex++) {
                    MineTunnelGeometry.Slice slice = slices.get(sliceIndex);
                    Map<BlockPosition, Integer> delta = new LinkedHashMap<>();
                    for (BlockPosition block : slice.excavationBlocks()) {
                        if (excavated.add(block)) delta.put(block, AIR);
                    }
                    slicesExcavated++;
                    step++;
                    BlockPosition position = slice.floorCenter();
                    recorder.captureDelta(step, delta,
                        List.of(resident(position, position,
                            "EXCAVATING " + tunnel.tunnel().kind() + " slice " + (sliceIndex + 1) + "/" + slices.size(),
                            tunnel.tunnel(), sliceIndex + 1, slices.size())),
                        position, metrics(plan, excavated.size(), slicesExcavated, completedTunnels, tunnel.tunnel()));
                }
                completedTunnels++;
            }

            var last = ordered.getLast();
            BlockPosition finish = last.geometry().slices().getLast().floorCenter();
            recorder.captureDelta(++step, Map.of(),
                List.of(resident(finish, null, "COMPLETE", last.tunnel(),
                    last.geometry().slices().size(), last.geometry().slices().size())),
                null, metrics(plan, excavated.size(), slicesExcavated, completedTunnels, last.tunnel()));
        } catch (RuntimeException exception) {
            error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        if (recorder == null) recorder = emptyRecorder();
        return recorder.finish(id, title, description, "semantic-slice", markers, error);
    }

    private static List<MineNetworkGrowthPlanner.PlannedTunnel> topologicalOrder(MineNetworkGrowthPlanner.Plan plan) {
        List<MineNetworkGrowthPlanner.PlannedTunnel> remaining = new ArrayList<>(plan.tunnels());
        List<MineNetworkGrowthPlanner.PlannedTunnel> ordered = new ArrayList<>();
        Set<UUID> completed = new LinkedHashSet<>();
        while (!remaining.isEmpty()) {
            boolean progressed = false;
            for (var tunnel : List.copyOf(remaining)) {
                UUID parent = tunnel.tunnel().parentTunnelId();
                if (parent == null || completed.contains(parent)) {
                    ordered.add(tunnel);
                    completed.add(tunnel.tunnel().id());
                    remaining.remove(tunnel);
                    progressed = true;
                }
            }
            if (!progressed) throw new IllegalStateException("MineNetwork contains an unresolved tunnel hierarchy");
        }
        return List.copyOf(ordered);
    }

    private static Resident resident(BlockPosition position, BlockPosition target, String state,
                                     MineTunnel tunnel, int sliceIndex, int sliceCount) {
        return new Resident("miner-1", "MINER", worldPosition(position),
            target == null ? null : worldPosition(target), state,
            Map.of("tunnelId", tunnel.id().toString(), "kind", tunnel.kind().name(),
                "branchDepth", tunnel.branchDepth(), "slice", sliceIndex, "sliceCount", sliceCount));
    }

    private static Map<String, Object> metrics(MineNetworkGrowthPlanner.Plan plan, int excavatedBlocks,
                                                int slicesExcavated, int completedTunnels,
                                                MineTunnel activeTunnel) {
        return Map.of("excavatedBlocks", excavatedBlocks, "slicesExcavated", slicesExcavated,
            "completedTunnels", completedTunnels, "totalTunnels", plan.tunnels().size(),
            "activeKind", activeTunnel.kind().name(), "activeDepth", activeTunnel.branchDepth());
    }

    private static WorldPosition worldPosition(BlockPosition position) {
        return new WorldPosition(position.x() + 0.5, position.y(), position.z() + 0.5);
    }

    private static Marker tunnelMarker(MineNetworkGrowthPlanner.PlannedTunnel tunnel) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPosition block : tunnel.geometry().excavationBlocks()) {
            minX = Math.min(minX, block.x()); minY = Math.min(minY, block.y()); minZ = Math.min(minZ, block.z());
            maxX = Math.max(maxX, block.x()); maxY = Math.max(maxY, block.y()); maxZ = Math.max(maxZ, block.z());
        }
        return new Marker(tunnel.tunnel().id().toString(),
            tunnel.tunnel().kind() == MineTunnel.Kind.MAIN ? "main_tunnel" : "branch_tunnel",
            new double[]{minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0});
    }

    static Map<BlockPosition, Integer> rockEnvelope(List<MineNetworkGrowthPlanner.PlannedTunnel> tunnels) {
        Set<BlockPosition> excavation = new LinkedHashSet<>();
        tunnels.forEach(tunnel -> excavation.addAll(tunnel.geometry().excavationBlocks()));
        if (excavation.isEmpty()) return Map.of();
        int minX = excavation.stream().mapToInt(BlockPosition::x).min().orElseThrow() - 2;
        int maxX = excavation.stream().mapToInt(BlockPosition::x).max().orElseThrow() + 2;
        int minY = excavation.stream().mapToInt(BlockPosition::y).min().orElseThrow() - 2;
        int maxY = excavation.stream().mapToInt(BlockPosition::y).max().orElseThrow() + 2;
        int minZ = excavation.stream().mapToInt(BlockPosition::z).min().orElseThrow() - 2;
        int maxZ = excavation.stream().mapToInt(BlockPosition::z).max().orElseThrow() + 2;
        Map<BlockPosition, Integer> world = new LinkedHashMap<>();
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            world.put(new BlockPosition(x, y, z), ROCK);
        }
        return world;
    }

    public static SimulationRecording recordRuntime(SimulationScenario scenario, int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        Recorder recorder = null;
        String error = null;
        try {
            SimulationRuntime runtime = scenario.createRuntime();
            recorder = new Recorder(runtimeWorld(runtime.worldSnapshot()));
            captureRuntime(recorder, runtime.worldSnapshot());
            for (int i = 0; i < ticks; i++) {
                runtime.tick();
                captureRuntime(recorder, runtime.worldSnapshot());
            }
        } catch (RuntimeException exception) {
            error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        if (recorder == null) recorder = emptyRecorder();
        return recorder.finish(scenario.id(), scenario.displayName(), scenario.description()
            + " Geradlinige Fake-Bewegung; keine Hytale-Navigation.", "seconds", List.of(), error);
    }

    private static Recorder emptyRecorder() {
        Recorder recorder = new Recorder(Map.of());
        recorder.capture(0, Map.of(), List.of(), null, Map.of());
        return recorder;
    }

    public static Map<BlockPosition, Integer> runtimeWorld(SimulationRuntime.WorldSnapshot s) {
        Map<BlockPosition, Integer> world = new LinkedHashMap<>();
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) world.put(new BlockPosition(x, -1, z), FIELD);
        s.trees().forEach(t -> { for (int y = 0; y < 4; y++) world.put(new BlockPosition(t.position().x(), t.position().y() + y, t.position().z()), WOOD); });
        s.constructionSites().forEach(site -> {
            WorldPosition p = site.workPoint();
            for (int y = 0; y < (site.completed() ? 3 : 1); y++) world.put(
                new BlockPosition((int) Math.floor(p.x()), (int) Math.floor(p.y()) + y, (int) Math.floor(p.z())),
                site.completed() ? PREFAB : CONSTRUCTION);
        });
        s.farmFields().forEach(field -> {
            WorldPosition p = field.position();
            world.put(new BlockPosition((int) Math.floor(p.x()), (int) Math.floor(p.y()) - 1, (int) Math.floor(p.z())), WOOD);
        });
        return world;
    }

    private static void captureRuntime(Recorder recorder, SimulationRuntime.WorldSnapshot s) {
        List<Resident> residents = s.residents().stream().map(r -> new Resident(r.id(), r.profession().name(),
            r.position(), r.movementTarget(), r.state(), Map.<String, Object>of("autonomousState", r.autonomousState(),
                "manualMovementActive", r.manualMovementActive()))).toList();
        Map<String, Object> metrics = JSON.convertValue(s.metrics(),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        recorder.capture(s.elapsedSeconds(), runtimeWorld(s), residents, null, metrics);
    }
}
