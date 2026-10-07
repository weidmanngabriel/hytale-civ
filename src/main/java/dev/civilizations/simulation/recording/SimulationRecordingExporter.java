package dev.civilizations.simulation.recording;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingOrientation;
import dev.civilizations.core.MineHeading;
import dev.civilizations.core.MineInfrastructurePlanner;
import dev.civilizations.core.MineInfrastructureTask;
import dev.civilizations.core.MineNetworkGrowthPlanner;
import dev.civilizations.core.MineObstaclePolicy;
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
        BuildingOrientation referenceOrientation = BuildingOrientation.NORTH;
        SimulationRecording mineRecording = recordMine(referenceOrientation);
        write(output, mineRecording, scenarios);
        failed |= mineRecording.status().equals("failed");
        SimulationRecording fullMine = recordFullMine();
        write(output, fullMine, scenarios);
        failed |= fullMine.status().equals("failed");
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


    public static SimulationRecording recordFullMine() {
        final String id = "mine-full";
        Recorder recorder = null;
        String error = null;
        List<Marker> markers = new ArrayList<>();
        try {
            BuildingOrientation orientation = BuildingOrientation.NORTH;
            long seed = 42424242L;
            UUID mineId = UUID.nameUUIDFromBytes((id + ":" + seed).getBytes(StandardCharsets.UTF_8));
            MineNetworkGrowthPlanner.Plan plan = MineNetworkGrowthPlanner.plan(
                mineId, MINE_ORIGIN, orientation.rotate(MineHeading.NORTH), 180, 18, seed
            );
            List<MineNetworkGrowthPlanner.PlannedTunnel> ordered = topologicalOrder(plan);
            int[] rockBounds = rockBounds(plan.tunnels());
            Map<BlockPosition, Integer> world = Map.of();
            recorder = new Recorder(world, rockBounds);
            markers.addAll(plan.tunnels().stream().map(SimulationRecordingExporter::tunnelMarker).toList());

            MineNetworkGrowthPlanner.PlannedTunnel main = plan.mainTunnel();
            List<MineInfrastructureTask> mainInfrastructure =
                MineInfrastructurePlanner.plan(main.tunnel().id(), main.geometry());
            MineInfrastructureTask support = mainInfrastructure.stream()
                .filter(task -> task.type() == MineInfrastructureTask.Type.BUILD_SUPPORT)
                .findFirst().orElse(null);
            MineInfrastructureTask light = mainInfrastructure.stream()
                .filter(task -> task.type() == MineInfrastructureTask.Type.PLACE_LIGHT)
                .findFirst().orElse(null);
            MineInfrastructureTask step = mainInfrastructure.stream()
                .filter(task -> task.type() == MineInfrastructureTask.Type.BUILD_STEP)
                .findFirst().orElse(null);

            int bridgeStart = Math.min(18, main.geometry().slices().size() - 4);
            int bridgeEnd = Math.min(bridgeStart + 3, main.geometry().slices().size() - 2);
            MineInfrastructureTask bridge = MineInfrastructurePlanner.bridgeTask(
                main.tunnel().id(), bridgeStart, bridgeEnd,
                main.geometry().slices().get(bridgeStart).floorCenter()
            );
            markers.add(sliceMarker("water-gap", "water_obstacle", main.geometry(), bridgeStart, bridgeEnd));

            MineNetworkGrowthPlanner.PlannedTunnel hazardous = ordered.stream()
                .filter(tunnel -> tunnel.tunnel().kind() == MineTunnel.Kind.BRANCH)
                .findFirst().orElse(null);
            if (hazardous != null) {
                int hazardSlice = Math.min(4, hazardous.geometry().slices().size() - 1);
                markers.add(sliceMarker("lava-front", "lava_obstacle", hazardous.geometry(), hazardSlice, hazardSlice));
            }

            BlockPosition entrance = main.geometry().slices().getFirst().floorCenter();
            BlockPosition outside = new BlockPosition(entrance.x(), entrance.y(), entrance.z() + 6);
            BlockPosition[] minerPositions = {entrance, entrance, entrance};
            String[] minerStates = {"READY", "READY", "READY"};
            Set<BlockPosition> excavated = new LinkedHashSet<>();
            int stepNumber = 0;
            int infrastructureCompleted = 0;
            int abandonedFronts = 0;

            recorder.capture(0, world, residents(minerPositions, minerStates, null),
                null, fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "START"));

            for (int tunnelIndex = 0; tunnelIndex < ordered.size(); tunnelIndex++) {
                var tunnel = ordered.get(tunnelIndex);
                List<MineTunnelGeometry.Slice> slices = tunnel.geometry().slices();
                int worker = tunnelIndex % minerPositions.length;
                boolean abandonThisTunnel = hazardous != null && tunnel.tunnel().id().equals(hazardous.tunnel().id());
                int abandonAt = abandonThisTunnel ? Math.min(4, slices.size() - 1) : -1;

                for (int sliceIndex = 0; sliceIndex < slices.size(); sliceIndex++) {
                    MineTunnelGeometry.Slice slice = slices.get(sliceIndex);
                    if (sliceIndex == abandonAt) {
                        minerPositions[worker] = slice.floorCenter();
                        minerStates[worker] = "ABANDONED · LAVA";
                        abandonedFronts++;
                        recorder.captureDelta(++stepNumber, Map.of(),
                            residents(minerPositions, minerStates, slice.floorCenter()), slice.floorCenter(),
                            fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts,
                                MineObstaclePolicy.frontStateFor(MineObstaclePolicy.FailureKind.HAZARDOUS_FLUID).name()));
                        break;
                    }

                    Map<BlockPosition, Integer> delta = new LinkedHashMap<>();
                    for (BlockPosition block : slice.excavationBlocks()) {
                        if (excavated.add(block)) delta.put(block, AIR);
                    }
                    minerPositions[worker] = slice.floorCenter();
                    minerStates[worker] = "EXCAVATING " + tunnel.tunnel().kind();
                    int partner = (worker + 1) % minerPositions.length;
                    if (sliceIndex % 3 == 0) {
                        minerPositions[partner] = slice.floorCenter();
                        minerStates[partner] = "SHARED FRONT";
                    }
                    recorder.captureDelta(++stepNumber, delta,
                        residents(minerPositions, minerStates, slice.floorCenter()), slice.floorCenter(),
                        fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "EXCAVATING"));

                    if (tunnel.tunnel().id().equals(main.tunnel().id())) {
                        MineInfrastructureTask due = dueInfrastructure(sliceIndex, support, light, step, bridge);
                        if (due != null) {
                            Map<BlockPosition, Integer> built = infrastructureVoxels(due, main.geometry());
                            infrastructureCompleted++;
                            minerStates[worker] = due.type().name();
                            recorder.captureDelta(++stepNumber, built,
                                residents(minerPositions, minerStates, due.anchor()), due.anchor(),
                                fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts,
                                    due.type().name()));
                        }
                        if (sliceIndex == Math.min(30, slices.size() - 1)) {
                            int leavingMiner = 1;
                            minerStates[leavingMiner] = "LEAVING MINE";
                            minerPositions[leavingMiner] = entrance;
                            recorder.captureDelta(++stepNumber, Map.of(),
                                residents(minerPositions, minerStates, outside), entrance,
                                fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "LEAVING_MINE"));
                            minerPositions[leavingMiner] = outside;
                            minerStates[leavingMiner] = "OUTSIDE";
                            recorder.captureDelta(++stepNumber, Map.of(),
                                residents(minerPositions, minerStates, entrance), outside,
                                fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "OUTSIDE"));
                            minerPositions[leavingMiner] = entrance;
                            minerStates[leavingMiner] = "REENTERING MINE";
                            recorder.captureDelta(++stepNumber, Map.of(),
                                residents(minerPositions, minerStates, slice.floorCenter()), entrance,
                                fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "REENTERING_MINE"));
                        }
                    }
                }
            }

            for (int i = 0; i < minerStates.length; i++) minerStates[i] = "COMPLETE";
            recorder.captureDelta(++stepNumber, Map.of(), residents(minerPositions, minerStates, null), null,
                fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "COMPLETE"));
        } catch (RuntimeException exception) {
            error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        if (recorder == null) recorder = emptyRecorder();
        return recorder.finish(id, "Mine · Full Scenario",
            "Ein repräsentativer Layer-2-bis-6-Lauf in fester NORTH-Ausrichtung: drei Miner, geteilte Arbeitsfronten, variable Tunnel und Branches, Support/Licht/Step/Bridge, kurzer Wasser-Gap, Lava-Abbruch sowie Rausgehen und Wiedereinstieg. Engine-nahe Weltreaktionen sind kontrollierte Headless-Fixtures; echte Hytale-Navigation, Fluidphysik und Assetplatzierung werden hier nicht behauptet.",
            "semantic-step", markers, error);
    }

    private static MineInfrastructureTask dueInfrastructure(
        int sliceIndex,
        MineInfrastructureTask support,
        MineInfrastructureTask light,
        MineInfrastructureTask step,
        MineInfrastructureTask bridge
    ) {
        MineInfrastructureTask[] candidates = {support, light, step, bridge};
        for (MineInfrastructureTask task : candidates) {
            if (task != null && task.startSliceIndex() == sliceIndex) return task;
        }
        return null;
    }

    private static Map<BlockPosition, Integer> infrastructureVoxels(
        MineInfrastructureTask task,
        MineTunnelGeometry geometry
    ) {
        Map<BlockPosition, Integer> delta = new LinkedHashMap<>();
        BlockPosition p = task.anchor();
        switch (task.type()) {
            case BUILD_SUPPORT -> {
                for (int y = 0; y < 3; y++) {
                    delta.put(new BlockPosition(p.x() - 2, p.y() + y, p.z()), SUPPORT);
                    delta.put(new BlockPosition(p.x() + 2, p.y() + y, p.z()), SUPPORT);
                }
                for (int x = -2; x <= 2; x++) delta.put(new BlockPosition(p.x() + x, p.y() + 3, p.z()), SUPPORT);
            }
            case PLACE_LIGHT -> delta.put(new BlockPosition(p.x() + 2, p.y() + 2, p.z()), CONSTRUCTION);
            case BUILD_STEP -> {
                delta.put(p, PREFAB);
                delta.put(new BlockPosition(p.x() + 1, p.y(), p.z()), PREFAB);
                delta.put(new BlockPosition(p.x() - 1, p.y(), p.z()), PREFAB);
            }
            case BUILD_BRIDGE -> {
                for (int index = task.startSliceIndex(); index <= task.endSliceIndex(); index++) {
                    BlockPosition floor = geometry.slices().get(index).floorCenter();
                    for (int x = -1; x <= 1; x++) delta.put(new BlockPosition(floor.x() + x, floor.y(), floor.z()), WOOD);
                }
            }
        }
        return delta;
    }

    private static Marker sliceMarker(
        String id,
        String type,
        MineTunnelGeometry geometry,
        int start,
        int end
    ) {
        BlockPosition a = geometry.slices().get(start).floorCenter();
        BlockPosition b = geometry.slices().get(end).floorCenter();
        return new Marker(id, type, new double[]{
            Math.min(a.x(), b.x()) - 2, Math.min(a.y(), b.y()) - 1, Math.min(a.z(), b.z()) - 2,
            Math.max(a.x(), b.x()) + 3, Math.max(a.y(), b.y()) + 3, Math.max(a.z(), b.z()) + 3
        });
    }

    private static List<Resident> residents(BlockPosition[] positions, String[] states, BlockPosition target) {
        List<Resident> residents = new ArrayList<>();
        for (int i = 0; i < positions.length; i++) {
            residents.add(new Resident("miner-" + (i + 1), "MINER", worldPosition(positions[i]),
                target == null ? null : worldPosition(target), states[i], Map.of("worker", i + 1)));
        }
        return List.copyOf(residents);
    }

    private static Map<String, Object> fullMetrics(
        MineNetworkGrowthPlanner.Plan plan,
        int excavatedBlocks,
        int infrastructureCompleted,
        int abandonedFronts,
        String phase
    ) {
        return Map.of(
            "excavatedBlocks", excavatedBlocks,
            "infrastructureCompleted", infrastructureCompleted,
            "abandonedFronts", abandonedFronts,
            "totalTunnels", plan.tunnels().size(),
            "miners", 3,
            "phase", phase
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

    static int[] rockBounds(List<MineNetworkGrowthPlanner.PlannedTunnel> tunnels) {
        Set<BlockPosition> excavation = new LinkedHashSet<>();
        tunnels.forEach(tunnel -> excavation.addAll(tunnel.geometry().excavationBlocks()));
        if (excavation.isEmpty()) return null;
        int minX = excavation.stream().mapToInt(BlockPosition::x).min().orElseThrow() - 2;
        int maxX = excavation.stream().mapToInt(BlockPosition::x).max().orElseThrow() + 3;
        int minY = excavation.stream().mapToInt(BlockPosition::y).min().orElseThrow() - 2;
        int maxY = excavation.stream().mapToInt(BlockPosition::y).max().orElseThrow() + 3;
        int minZ = excavation.stream().mapToInt(BlockPosition::z).min().orElseThrow() - 2;
        int maxZ = excavation.stream().mapToInt(BlockPosition::z).max().orElseThrow() + 3;
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
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
