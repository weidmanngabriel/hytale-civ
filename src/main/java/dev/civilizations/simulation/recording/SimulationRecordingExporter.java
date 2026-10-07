package dev.civilizations.simulation.recording;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingOrientation;
import dev.civilizations.core.MineFrontTaskScheduler;
import dev.civilizations.core.MineHeading;
import dev.civilizations.core.MineInfrastructurePlanner;
import dev.civilizations.core.MineInfrastructureTask;
import dev.civilizations.core.MineNetworkGrowthPlanner;
import dev.civilizations.core.MineObstaclePolicy;
import dev.civilizations.core.MineTunnel;
import dev.civilizations.core.MineTunnelGeometry;
import dev.civilizations.core.MineWorkFront;
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
        SimulationRecording straightFull = recordStraightFull();
        write(output, straightFull, scenarios);
        failed |= straightFull.status().equals("failed");
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
            "mine-geometry-" + orientation.name().toLowerCase(Locale.ROOT),
            "Mine · Geometry",
            "Isolierter Layer-2/3-Test: ein einzelner variabler Hauptstollen wird Slice für Slice ausgegraben. Keine Infrastruktur, keine Hindernisse und keine Hytale-Navigation.",
            orientation, 96, 1, 123456789L
        );
    }

    public static SimulationRecording recordBranchingMine(BuildingOrientation orientation) {
        return recordPlannedMine(
            "mine-combinations-" + orientation.name().toLowerCase(Locale.ROOT),
            "Mine · Netzwerk · " + orientation.name(),
            "Aktuelles MineNetwork mit variablem Hauptstollen und geplanten Seitenstollen. Reine Core-Aufzeichnung für automatisierte Netzwerk-/Rotationschecks; nicht Teil des Browser-Referenzkatalogs.",
            orientation, 220, 24, 99887766L
        );
    }

    public static SimulationRecording recordStraightFull() {
        final String id = "mine-straight-full";
        Recorder recorder = null;
        String error = null;
        List<Marker> markers = new ArrayList<>();
        try {
            BuildingOrientation orientation = BuildingOrientation.NORTH;
            long seed = 77112233L;
            UUID mineId = UUID.nameUUIDFromBytes((id + ":" + seed).getBytes(StandardCharsets.UTF_8));
            MineNetworkGrowthPlanner.Plan plan = MineNetworkGrowthPlanner.plan(
                mineId, MINE_ORIGIN, orientation.rotate(MineHeading.NORTH), 140, 1, seed
            );
            MineNetworkGrowthPlanner.PlannedTunnel main = plan.mainTunnel();
            MineTunnelGeometry geometry = main.geometry();
            List<MineInfrastructureTask> infrastructure =
                MineInfrastructurePlanner.plan(main.tunnel().id(), geometry);

            Map<BlockPosition, Integer> world = Map.of();
            recorder = new Recorder(world, rockBounds(plan.tunnels()));
            markers.add(tunnelMarker(main));

            BlockPosition position = geometry.slices().getFirst().floorCenter();
            Set<BlockPosition> excavated = new LinkedHashSet<>();
            int stepNumber = 0;
            int infrastructureCompleted = 0;
            recorder.capture(0, world,
                List.of(resident(position, null, "READY", main.tunnel(), 0, geometry.slices().size())),
                null, Map.of(
                    "phase", "START",
                    "excavatedBlocks", 0,
                    "infrastructureCompleted", 0,
                    "plannedInfrastructure", infrastructure.size()
                ));

            for (int sliceIndex = 0; sliceIndex < geometry.slices().size(); sliceIndex++) {
                MineTunnelGeometry.Slice slice = geometry.slices().get(sliceIndex);
                Map<BlockPosition, Integer> delta = new LinkedHashMap<>();
                for (BlockPosition block : slice.excavationBlocks()) {
                    if (excavated.add(block)) delta.put(block, AIR);
                }
                position = slice.floorCenter();
                recorder.captureDelta(++stepNumber, delta,
                    List.of(resident(position, position,
                        "EXCAVATING slice " + (sliceIndex + 1) + "/" + geometry.slices().size(),
                        main.tunnel(), sliceIndex + 1, geometry.slices().size())),
                    position, Map.of(
                        "phase", "EXCAVATING",
                        "excavatedBlocks", excavated.size(),
                        "infrastructureCompleted", infrastructureCompleted,
                        "plannedInfrastructure", infrastructure.size()
                    ));

                for (MineInfrastructureTask task : tasksStartingAt(infrastructure, sliceIndex)) {
                    infrastructureCompleted++;
                    Map<BlockPosition, Integer> built = headlessInfrastructureVoxels(task, geometry);
                    recorder.captureDelta(++stepNumber, built,
                        List.of(resident(task.anchor(), task.anchor(), task.type().name(),
                            main.tunnel(), sliceIndex + 1, geometry.slices().size())),
                        task.anchor(), Map.of(
                            "phase", task.type().name(),
                            "excavatedBlocks", excavated.size(),
                            "infrastructureCompleted", infrastructureCompleted,
                            "plannedInfrastructure", infrastructure.size()
                        ));
                }
            }

            recorder.captureDelta(++stepNumber, Map.of(),
                List.of(resident(position, null, "COMPLETE", main.tunnel(),
                    geometry.slices().size(), geometry.slices().size())),
                null, Map.of(
                    "phase", "COMPLETE",
                    "excavatedBlocks", excavated.size(),
                    "infrastructureCompleted", infrastructureCompleted,
                    "plannedInfrastructure", infrastructure.size()
                ));
        } catch (RuntimeException exception) {
            error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        if (recorder == null) recorder = emptyRecorder();
        return recorder.finish(id, "Mine · Straight Full",
            "Ein Miner baut einen einzelnen längeren Hauptstollen vollständig aus: echte Core-Ausgrabungsgeometrie plus alle vom MineInfrastructurePlanner geplanten Stützen, Lichter und Stufen. Die sichtbaren Infrastrukturblöcke sind eine richtungsbewusste Headless-Darstellung, nicht Hytales Asset-Auflösung.",
            "semantic-step", markers, error);
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
            Map<UUID, MineNetworkGrowthPlanner.PlannedTunnel> byTunnel = new LinkedHashMap<>();
            ordered.forEach(tunnel -> byTunnel.put(tunnel.tunnel().id(), tunnel));

            MineNetworkGrowthPlanner.PlannedTunnel main = plan.mainTunnel();
            BlockPosition connector = main.geometry().slices().getFirst().floorCenter();
            BlockPosition access = new BlockPosition(connector.x(), connector.y() + 10, connector.z());
            BlockPosition outside = new BlockPosition(access.x(), access.y(), access.z() + 6);

            Map<BlockPosition, Integer> world = prefabPlatform(access);
            recorder = new Recorder(world, rockBounds(plan.tunnels()));
            markers.addAll(plan.tunnels().stream().map(SimulationRecordingExporter::plannedTunnelMarker).toList());
            markers.add(pointMarker("mine-access", "workplace_access", access));
            markers.add(pointMarker("mine-connector", "mine_tunnel_connector", connector));

            int bridgeStart = Math.min(18, main.geometry().slices().size() - 4);
            int bridgeEnd = Math.min(bridgeStart + 3, main.geometry().slices().size() - 2);
            MineInfrastructureTask bridge = MineInfrastructurePlanner.bridgeTask(
                main.tunnel().id(), bridgeStart, bridgeEnd,
                main.geometry().slices().get(bridgeStart).floorCenter()
            );
            markers.add(sliceMarker("water-gap", "water_obstacle", main.geometry(), bridgeStart, bridgeEnd));

            Set<UUID> parentTunnelIds = ordered.stream()
                .map(tunnel -> tunnel.tunnel().parentTunnelId())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            MineNetworkGrowthPlanner.PlannedTunnel hazardous = ordered.stream()
                .filter(tunnel -> tunnel.tunnel().kind() == MineTunnel.Kind.BRANCH)
                .filter(tunnel -> !parentTunnelIds.contains(tunnel.tunnel().id()))
                .max((a, b) -> Integer.compare(a.geometry().slices().size(), b.geometry().slices().size()))
                .orElse(null);
            int hazardSlice = hazardous == null ? -1 : lateHazardSlice(hazardous.geometry());
            if (hazardous != null) {
                markers.add(sliceMarker("lava-front", "lava_obstacle", hazardous.geometry(), hazardSlice, hazardSlice));
            }

            Map<UUID, Integer> progress = new LinkedHashMap<>();
            Map<UUID, List<MineInfrastructureTask>> infrastructureByTunnel = new LinkedHashMap<>();
            Map<UUID, UUID> frontIds = new LinkedHashMap<>();
            Set<UUID> abandoned = new LinkedHashSet<>();
            Set<UUID> completedInfrastructure = new LinkedHashSet<>();
            for (var tunnel : ordered) {
                progress.put(tunnel.tunnel().id(), 0);
                List<MineInfrastructureTask> tasks = new ArrayList<>(
                    MineInfrastructurePlanner.plan(tunnel.tunnel().id(), tunnel.geometry())
                );
                if (tunnel.tunnel().id().equals(main.tunnel().id())) tasks.add(bridge);
                infrastructureByTunnel.put(tunnel.tunnel().id(), List.copyOf(tasks));
                frontIds.put(tunnel.tunnel().id(), UUID.nameUUIDFromBytes(
                    ("sim-front:" + tunnel.tunnel().id()).getBytes(StandardCharsets.UTF_8)
                ));
            }

            BlockPosition[] minerPositions = {
                new BlockPosition(access.x() - 1, access.y(), access.z()),
                access,
                new BlockPosition(access.x() + 1, access.y(), access.z())
            };
            String[] minerStates = {"AT MINE PREFAB", "AT MINE PREFAB", "AT MINE PREFAB"};
            UUID[] minerTunnels = new UUID[minerPositions.length];
            Set<BlockPosition> excavated = new LinkedHashSet<>();
            int stepNumber = 0;
            int infrastructureCompleted = 0;
            int abandonedFronts = 0;
            boolean leaveCycleDone = false;

            recorder.capture(0, world, residents(minerPositions, minerStates, null, minerTunnels),
                access, fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "START_AT_PREFAB"));

            for (int i = 0; i < minerPositions.length; i++) {
                minerPositions[i] = access;
                minerStates[i] = "ENTERING MINE";
            }
            recorder.captureDelta(++stepNumber, Map.of(),
                residents(minerPositions, minerStates, connector, minerTunnels), access,
                fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "ENTERING_MINE"));

            for (int i = 0; i < minerPositions.length; i++) {
                minerPositions[i] = connector;
                minerStates[i] = "AT CONNECTOR";
            }
            recorder.captureDelta(++stepNumber, Map.of(),
                residents(minerPositions, minerStates, null, minerTunnels), connector,
                fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "AT_CONNECTOR"));

            int safetyRounds = 0;
            while (safetyRounds++ < 2000) {
                List<MineWorkFront> executable = executableFronts(
                    plan, byTunnel, progress, abandoned, excavated, frontIds
                );
                if (executable.isEmpty()) break;

                Map<UUID, Integer> workerCounts = new LinkedHashMap<>();
                Map<Integer, MineWorkFront> assignments = new LinkedHashMap<>();
                for (int worker = 0; worker < minerPositions.length; worker++) {
                    MineWorkFront selected = MineFrontTaskScheduler.select(
                        plan.network(), executable, workerCounts, minerPositions[worker]
                    );
                    if (selected == null) {
                        minerTunnels[worker] = null;
                        minerStates[worker] = "WAITING";
                        continue;
                    }
                    assignments.put(worker, selected);
                    workerCounts.merge(selected.id(), 1, Integer::sum);
                    minerTunnels[worker] = selected.tunnelId();
                    minerStates[worker] = "MOVING TO FRONT";
                }

                Set<UUID> handledFronts = new LinkedHashSet<>();
                for (Map.Entry<Integer, MineWorkFront> assignment : assignments.entrySet()) {
                    MineWorkFront front = assignment.getValue();
                    if (!handledFronts.add(front.id())) continue;

                    UUID tunnelId = front.tunnelId();
                    MineNetworkGrowthPlanner.PlannedTunnel tunnel = byTunnel.get(tunnelId);
                    int sliceIndex = progress.get(tunnelId);
                    if (tunnel == null || sliceIndex >= tunnel.geometry().slices().size()) continue;
                    MineTunnelGeometry.Slice slice = tunnel.geometry().slices().get(sliceIndex);

                    List<Integer> workersAtFront = assignments.entrySet().stream()
                        .filter(entry -> entry.getValue().id().equals(front.id()))
                        .map(Map.Entry::getKey)
                        .toList();

                    boolean abandonHere = hazardous != null
                        && tunnelId.equals(hazardous.tunnel().id())
                        && sliceIndex == hazardSlice;
                    if (abandonHere) {
                        for (int worker : workersAtFront) {
                            minerPositions[worker] = slice.floorCenter();
                            minerStates[worker] = "ABANDONED · LAVA";
                        }
                        abandoned.add(tunnelId);
                        abandonedFronts++;
                        recorder.captureDelta(++stepNumber, Map.of(),
                            residents(minerPositions, minerStates, slice.floorCenter(), minerTunnels),
                            slice.floorCenter(),
                            fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts,
                                MineObstaclePolicy.frontStateFor(MineObstaclePolicy.FailureKind.HAZARDOUS_FLUID).name()));
                        continue;
                    }

                    List<MineInfrastructureTask> tasks = infrastructureByTunnel.getOrDefault(tunnelId, List.of());
                    for (MineInfrastructureTask task : tasksStartingAt(tasks, sliceIndex)) {
                        if (task.type() != MineInfrastructureTask.Type.BUILD_BRIDGE
                            || !completedInfrastructure.add(task.id())) continue;
                        infrastructureCompleted++;
                        for (int worker : workersAtFront) {
                            minerPositions[worker] = task.anchor();
                            minerStates[worker] = task.type().name();
                        }
                        recorder.captureDelta(++stepNumber,
                            headlessInfrastructureVoxels(task, tunnel.geometry()),
                            residents(minerPositions, minerStates, task.anchor(), minerTunnels),
                            task.anchor(),
                            fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts,
                                task.type().name()));
                    }

                    Map<BlockPosition, Integer> delta = new LinkedHashMap<>();
                    for (BlockPosition block : slice.excavationBlocks()) {
                        if (excavated.add(block)) delta.put(block, AIR);
                    }
                    for (int worker : workersAtFront) {
                        minerPositions[worker] = slice.floorCenter();
                        minerStates[worker] = "EXCAVATING " + tunnel.tunnel().kind();
                    }
                    recorder.captureDelta(++stepNumber, delta,
                        residents(minerPositions, minerStates, slice.floorCenter(), minerTunnels),
                        slice.floorCenter(),
                        fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "EXCAVATING"));

                    for (MineInfrastructureTask task : tasksStartingAt(tasks, sliceIndex)) {
                        if (task.type() == MineInfrastructureTask.Type.BUILD_BRIDGE
                            || !completedInfrastructure.add(task.id())) continue;
                        infrastructureCompleted++;
                        for (int worker : workersAtFront) {
                            minerPositions[worker] = task.anchor();
                            minerStates[worker] = task.type().name();
                        }
                        recorder.captureDelta(++stepNumber,
                            headlessInfrastructureVoxels(task, tunnel.geometry()),
                            residents(minerPositions, minerStates, task.anchor(), minerTunnels),
                            task.anchor(),
                            fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts,
                                task.type().name()));
                    }

                    progress.put(tunnelId, sliceIndex + 1);
                }

                if (!leaveCycleDone && progress.get(main.tunnel().id()) >= Math.min(30, main.geometry().slices().size())) {
                    int leavingMiner = 1;
                    minerTunnels[leavingMiner] = null;
                    minerPositions[leavingMiner] = connector;
                    minerStates[leavingMiner] = "LEAVING MINE";
                    recorder.captureDelta(++stepNumber, Map.of(),
                        residents(minerPositions, minerStates, access, minerTunnels), connector,
                        fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "LEAVING_MINE"));
                    minerPositions[leavingMiner] = access;
                    minerStates[leavingMiner] = "AT MINE PREFAB";
                    recorder.captureDelta(++stepNumber, Map.of(),
                        residents(minerPositions, minerStates, outside, minerTunnels), access,
                        fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "AT_PREFAB"));
                    minerPositions[leavingMiner] = outside;
                    minerStates[leavingMiner] = "OUTSIDE";
                    recorder.captureDelta(++stepNumber, Map.of(),
                        residents(minerPositions, minerStates, access, minerTunnels), outside,
                        fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "OUTSIDE"));
                    minerPositions[leavingMiner] = access;
                    minerStates[leavingMiner] = "REENTERING MINE";
                    recorder.captureDelta(++stepNumber, Map.of(),
                        residents(minerPositions, minerStates, connector, minerTunnels), access,
                        fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "REENTERING_MINE"));
                    minerPositions[leavingMiner] = connector;
                    minerStates[leavingMiner] = "AT CONNECTOR";
                    recorder.captureDelta(++stepNumber, Map.of(),
                        residents(minerPositions, minerStates, null, minerTunnels), connector,
                        fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "AT_CONNECTOR"));
                    leaveCycleDone = true;
                }

                if (allFrontsTerminal(ordered, progress, abandoned)) break;
            }

            for (var tunnel : ordered) {
                int developedSlices = progress.getOrDefault(tunnel.tunnel().id(), 0);
                if (developedSlices > 0) {
                    markers.add(developedTunnelMarker(tunnel, developedSlices));
                }
            }

            for (int i = 0; i < minerStates.length; i++) {
                minerStates[i] = "COMPLETE";
                minerTunnels[i] = null;
            }
            recorder.captureDelta(++stepNumber, Map.of(),
                residents(minerPositions, minerStates, null, minerTunnels), null,
                fullMetrics(plan, excavated.size(), infrastructureCompleted, abandonedFronts, "COMPLETE"));
        } catch (RuntimeException exception) {
            error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        if (recorder == null) recorder = emptyRecorder();
        return recorder.finish(id, "Mine · Full Scenario",
            "Drei Miner starten am oberirdischen Mine-Prefab-Zugang, gehen über workplace_access und mine_tunnel_connector in die Mine und werden danach mit dem produktiven MineFrontTaskScheduler auf ausführbare Fronten verteilt. Freie Fronten werden vor gemeinsamem Arbeiten bevorzugt. Alle Core-geplanten Stützen, Lichter und Stufen werden sichtbar abgearbeitet; dazu kommen Bridge/Water, Lava-Abbruch und Wiedereinstieg. Hytale-Navigation, Fluidphysik und konkrete Asset-Auflösung bleiben außerhalb dieses Headless-Tests.",
            "semantic-step", markers, error);
    }

    private static List<MineWorkFront> executableFronts(
        MineNetworkGrowthPlanner.Plan plan,
        Map<UUID, MineNetworkGrowthPlanner.PlannedTunnel> byTunnel,
        Map<UUID, Integer> progress,
        Set<UUID> abandoned,
        Set<BlockPosition> excavated,
        Map<UUID, UUID> frontIds
    ) {
        List<MineWorkFront> result = new ArrayList<>();
        for (var tunnel : plan.tunnels()) {
            UUID tunnelId = tunnel.tunnel().id();
            int index = progress.getOrDefault(tunnelId, 0);
            if (abandoned.contains(tunnelId) || index >= tunnel.geometry().slices().size()) continue;
            if (tunnel.tunnel().parentTunnelId() != null
                && !excavated.contains(tunnel.tunnel().origin())) continue;
            BlockPosition position = tunnel.geometry().slices().get(index).floorCenter();
            result.add(new MineWorkFront(
                frontIds.get(tunnelId), tunnelId, position, MineWorkFront.State.OPEN
            ));
        }
        return List.copyOf(result);
    }

    private static boolean allFrontsTerminal(
        List<MineNetworkGrowthPlanner.PlannedTunnel> tunnels,
        Map<UUID, Integer> progress,
        Set<UUID> abandoned
    ) {
        return tunnels.stream().allMatch(tunnel ->
            abandoned.contains(tunnel.tunnel().id())
                || progress.getOrDefault(tunnel.tunnel().id(), 0) >= tunnel.geometry().slices().size()
        );
    }

    private static int lateHazardSlice(MineTunnelGeometry geometry) {
        int size = geometry.slices().size();
        return Math.min(size - 2, Math.max(12, (size * 2) / 3));
    }

    private static Marker plannedTunnelMarker(MineNetworkGrowthPlanner.PlannedTunnel tunnel) {
        return boundedTunnelMarker(
            "planned-" + tunnel.tunnel().id(),
            tunnel.tunnel().kind() == MineTunnel.Kind.MAIN
                ? "planned_main_tunnel"
                : "planned_branch_tunnel",
            tunnel.geometry().slices(),
            tunnel.geometry().slices().size()
        );
    }

    private static Marker developedTunnelMarker(
        MineNetworkGrowthPlanner.PlannedTunnel tunnel,
        int developedSlices
    ) {
        return boundedTunnelMarker(
            "developed-" + tunnel.tunnel().id(),
            tunnel.tunnel().kind() == MineTunnel.Kind.MAIN
                ? "developed_main_tunnel"
                : "developed_branch_tunnel",
            tunnel.geometry().slices(),
            Math.min(developedSlices, tunnel.geometry().slices().size())
        );
    }

    private static Marker boundedTunnelMarker(
        String id,
        String type,
        List<MineTunnelGeometry.Slice> slices,
        int sliceCount
    ) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < sliceCount; i++) {
            for (BlockPosition block : slices.get(i).excavationBlocks()) {
                minX = Math.min(minX, block.x());
                minY = Math.min(minY, block.y());
                minZ = Math.min(minZ, block.z());
                maxX = Math.max(maxX, block.x());
                maxY = Math.max(maxY, block.y());
                maxZ = Math.max(maxZ, block.z());
            }
        }
        return new Marker(id, type, new double[]{
            minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0
        });
    }

    private static Map<BlockPosition, Integer> prefabPlatform(BlockPosition access) {
        Map<BlockPosition, Integer> world = new LinkedHashMap<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                world.put(new BlockPosition(access.x() + dx, access.y() - 1, access.z() + dz), PREFAB);
            }
        }
        for (int y = 0; y <= 3; y++) {
            world.put(new BlockPosition(access.x() - 3, access.y() + y, access.z() - 3), SUPPORT);
            world.put(new BlockPosition(access.x() + 3, access.y() + y, access.z() - 3), SUPPORT);
        }
        return world;
    }

    private static Marker pointMarker(String id, String type, BlockPosition point) {
        return new Marker(id, type, new double[]{
            point.x(), point.y(), point.z(),
            point.x() + 1.0, point.y() + 2.0, point.z() + 1.0
        });
    }

    private static List<MineInfrastructureTask> tasksStartingAt(
        List<MineInfrastructureTask> tasks,
        int sliceIndex
    ) {
        return tasks.stream()
            .filter(task -> task.startSliceIndex() == sliceIndex)
            .sorted((a, b) -> {
                int priority = Integer.compare(b.priority(), a.priority());
                return priority != 0 ? priority : a.type().compareTo(b.type());
            })
            .toList();
    }

    private static Map<BlockPosition, Integer> headlessInfrastructureVoxels(
        MineInfrastructureTask task,
        MineTunnelGeometry geometry
    ) {
        Map<BlockPosition, Integer> delta = new LinkedHashMap<>();
        int index = Math.max(0, Math.min(task.startSliceIndex(), geometry.slices().size() - 1));
        MineTunnelGeometry.Slice slice = geometry.slices().get(index);
        BlockPosition p = slice.floorCenter();
        int[] axes = localAxes(geometry.slices(), index);
        int crossX = -axes[1];
        int crossZ = axes[0];
        int minOffset = -(slice.widthBlocks() / 2);
        int maxOffset = minOffset + slice.widthBlocks() - 1;

        switch (task.type()) {
            case BUILD_SUPPORT -> {
                int beamY = p.y() + Math.max(3, slice.heightBlocks() - 1);
                for (int y = p.y(); y < beamY; y++) {
                    delta.put(offset(p, crossX, crossZ, minOffset, y), SUPPORT);
                    delta.put(offset(p, crossX, crossZ, maxOffset, y), SUPPORT);
                }
                for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
                    delta.put(offset(p, crossX, crossZ, lateral, beamY), SUPPORT);
                }
            }
            case PLACE_LIGHT -> {
                int lateral = Math.abs(minOffset + 1) > 1 ? minOffset + 1 : maxOffset - 1;
                delta.put(offset(p, crossX, crossZ, lateral, p.y()), CONSTRUCTION);
                delta.put(offset(p, crossX, crossZ, lateral, p.y() + 1), CONSTRUCTION);
            }
            case BUILD_STEP -> {
                BlockPosition low = p;
                if (task.endSliceIndex() < geometry.slices().size()) {
                    BlockPosition other = geometry.slices().get(task.endSliceIndex()).floorCenter();
                    if (other.y() < low.y()) low = other;
                }
                for (int lateral = -1; lateral <= 1; lateral++) {
                    delta.put(offset(low, crossX, crossZ, lateral, low.y()), PREFAB);
                }
            }
            case BUILD_BRIDGE -> {
                for (int sliceIndex = task.startSliceIndex();
                     sliceIndex <= task.endSliceIndex() && sliceIndex < geometry.slices().size();
                     sliceIndex++) {
                    MineTunnelGeometry.Slice bridgeSlice = geometry.slices().get(sliceIndex);
                    int[] bridgeAxes = localAxes(geometry.slices(), sliceIndex);
                    int bridgeCrossX = -bridgeAxes[1];
                    int bridgeCrossZ = bridgeAxes[0];
                    BlockPosition floor = bridgeSlice.floorCenter();
                    for (int lateral = -1; lateral <= 1; lateral++) {
                        delta.put(offset(floor, bridgeCrossX, bridgeCrossZ, lateral, floor.y() - 1), WOOD);
                    }
                    delta.put(offset(floor, bridgeCrossX, bridgeCrossZ, -2, floor.y() - 1), SUPPORT);
                    delta.put(offset(floor, bridgeCrossX, bridgeCrossZ, 2, floor.y() - 1), SUPPORT);
                }
            }
        }
        return delta;
    }

    private static int[] localAxes(List<MineTunnelGeometry.Slice> slices, int index) {
        BlockPosition before = slices.get(Math.max(0, index - 1)).floorCenter();
        BlockPosition after = slices.get(Math.min(slices.size() - 1, index + 1)).floorCenter();
        int dx = after.x() - before.x();
        int dz = after.z() - before.z();
        if (Math.abs(dx) >= Math.abs(dz) && dx != 0) return new int[]{Integer.signum(dx), 0};
        if (dz != 0) return new int[]{0, Integer.signum(dz)};
        return new int[]{1, 0};
    }

    private static BlockPosition offset(
        BlockPosition center,
        int directionX,
        int directionZ,
        int distance,
        int y
    ) {
        return new BlockPosition(
            center.x() + directionX * distance,
            y,
            center.z() + directionZ * distance
        );
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
        return residents(positions, states, target, null);
    }

    private static List<Resident> residents(
        BlockPosition[] positions,
        String[] states,
        BlockPosition target,
        UUID[] tunnelIds
    ) {
        List<Resident> residents = new ArrayList<>();
        for (int i = 0; i < positions.length; i++) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("worker", i + 1);
            if (tunnelIds != null && tunnelIds[i] != null) {
                details.put("tunnelId", tunnelIds[i].toString());
            }
            residents.add(new Resident("miner-" + (i + 1), "MINER", worldPosition(positions[i]),
                target == null ? null : worldPosition(target), states[i], Map.copyOf(details)));
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
            int[] rockBounds = rockBounds(plan.tunnels());
            Map<BlockPosition, Integer> world = Map.of();
            recorder = new Recorder(world, rockBounds);
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
