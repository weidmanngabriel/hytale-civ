package dev.civilizations.simulation.recording;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.BuildingOrientation;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.simulation.MineSimulationWorld;
import dev.civilizations.simulation.SimulationRuntime;
import dev.civilizations.simulation.SimulationScenario;
import dev.civilizations.simulation.SimulationScenarios;
import dev.civilizations.simulation.prefab.MinePrefabNavigationScenario;
import dev.civilizations.simulation.prefab.MineBranchingScenario;
import dev.civilizations.simulation.prefab.PrefabSimulationModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.civilizations.simulation.recording.SimulationRecording.*;

/** Executes the existing Java fixtures headlessly and exports data for a browser replay. */
public final class SimulationRecordingExporter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int RUNTIME_TICKS = 600;

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
            SimulationRecording combined = recordBranchingMine(orientation);
            write(output, combined, scenarios);
            failed |= combined.status().equals("failed");
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
        String id = "mine-" + orientation.name().toLowerCase(java.util.Locale.ROOT);
        String title = "Mine · " + orientation.name();
        String description = "Echtes Mine_01-Prefab, geometrischer Testpfad und Core MinerJob. "
            + "Ein Schritt ist eine semantische Aktion, keine Hytale-Laufzeit.";
        Recorder recorder = null;
        List<Marker> markers = List.of();
        String error = null;
        try {
            MinePrefabNavigationScenario scenario = MinePrefabNavigationScenario.create(orientation);
            var snapshot = scenario.snapshot();
            recorder = new Recorder(mineWorld(snapshot));
            markers = snapshot.model().markers().stream().map(m -> new Marker(m.name(), m.type(),
                new double[]{m.bounds().minX(), m.bounds().minY(), m.bounds().minZ(),
                    m.bounds().maxX(), m.bounds().maxY(), m.bounds().maxZ()})).toList();
            captureMine(recorder, snapshot, 0);
            int step = 0;
            while (true) {
                if (++step > 1_000) throw new IllegalStateException("Mine exceeded 1000 semantic steps");
                boolean advanced = scenario.step();
                captureMine(recorder, scenario.snapshot(), step);
                if (!advanced) break;
            }
        } catch (RuntimeException exception) {
            error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        if (recorder == null) recorder = emptyRecorder();
        return recorder.finish(id, title, description, "semantic-step", markers, error);
    }

    public static Map<BlockPosition, Integer> mineWorld(MinePrefabNavigationScenario.Snapshot snapshot) {
        Map<BlockPosition, Integer> world = new LinkedHashMap<>();
        snapshot.tunnelWorld().cells().forEach((p, c) -> {
            if (c != MineSimulationWorld.Cell.AIR) world.put(p,
                c == MineSimulationWorld.Cell.SOLID ? ROCK : SUPPORT);
        });
        snapshot.model().cells().forEach((p, c) -> world.put(p,
            c == PrefabSimulationModel.Cell.DOOR ? DOOR : PREFAB));
        return world;
    }

    private static void captureMine(Recorder recorder, MinePrefabNavigationScenario.Snapshot s, int step) {
        BlockPosition p = s.probe();
        BlockPosition action = s.lastAction();
        Resident resident = new Resident("miner-1", "MINER",
            new WorldPosition(p.x() + 0.5, p.y(), p.z() + 0.5),
            action == null ? null : new WorldPosition(action.x() + 0.5, action.y(), action.z() + 0.5),
            s.phase() + " / " + s.minerState(),
            Map.of("progress", s.segment().nextBlockIndex(), "totalBlocks", s.segment().blockCount(),
                "supports", s.segment().supportsPlaced(), "direction", s.simulationDirection().name()));
        recorder.capture(step, mineWorld(s), List.of(resident), action,
            Map.of("excavatedBlocks", s.segment().nextBlockIndex(), "supports", s.segment().supportsPlaced()));
    }


    public static SimulationRecording recordBranchingMine(BuildingOrientation orientation) {
        Recorder recorder = null;
        List<Marker> markers = new ArrayList<>();
        String error = null;
        try {
            MineBranchingScenario scenario = MineBranchingScenario.create(orientation);
            var start = scenario.snapshot();
            recorder = new Recorder(branchingMineWorld(start));
            for (var m : start.model().markers()) markers.add(new Marker(m.name(), m.type(),
                new double[]{m.bounds().minX(), m.bounds().minY(), m.bounds().minZ(),
                    m.bounds().maxX(), m.bounds().maxY(), m.bounds().maxZ()}));
            for (int i=0; i<start.segments().size(); i++) {
                var segment = start.segments().get(i);
                var b = segment.horizontalBounds();
                markers.add(new Marker("segment-"+i, "planned_tunnel",
                    new double[]{b.minX(),segment.start().y(),b.minZ(),
                        b.maxX()+1,segment.start().y()+4,b.maxZ()+1}));
            }
            captureBranchingMine(recorder, start, 0);
            int step = 0;
            while (true) {
                if (++step > 5_000) throw new IllegalStateException("Branching mine exceeded 5000 steps");
                boolean advanced = scenario.step();
                captureBranchingMine(recorder, scenario.snapshot(), step);
                if (!advanced) break;
            }
        } catch (RuntimeException exception) {
            error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        if (recorder == null) recorder = emptyRecorder();
        return recorder.finish("mine-combinations-"+orientation.name().toLowerCase(java.util.Locale.ROOT),
            "Mine · Kombinationen · "+orientation.name(),
            "Sieben verbundene Abschnitte (4/5/8/9/12 Blöcke), gerade/links/rechts, Stützen. "
                + "Unterbrechung bei Block 73: Rückweg zum Ausgang, Wiedereintritt, andere Äste, "
                + "gespeicherte Arbeit fortsetzen und abschließend nach draußen zurückkehren. "
                + "Geometrische Testwege und geskriptete Arbeitswahl, keine Hytale-Navigation.",
            "semantic-step", markers, error);
    }

    public static Map<BlockPosition, Integer> branchingMineWorld(MineBranchingScenario.Snapshot s) {
        Map<BlockPosition, Integer> world = new LinkedHashMap<>();
        s.tunnelWorld().cells().forEach((p,c)->{
            if(c != MineSimulationWorld.Cell.AIR) world.put(p,
                c == MineSimulationWorld.Cell.SOLID ? ROCK : SUPPORT);
        });
        s.model().cells().forEach((p,c)->world.put(p,
            c == PrefabSimulationModel.Cell.DOOR ? DOOR : PREFAB));
        return world;
    }

    private static void captureBranchingMine(Recorder recorder, MineBranchingScenario.Snapshot s, int step) {
        var segment = s.segments().get(s.activeSegment());
        int excavated = s.segments().stream().mapToInt(x->x.nextBlockIndex()).sum();
        int supports = s.segments().stream().mapToInt(x->x.supportsPlaced()).sum();
        long completed = s.segments().stream().filter(x->x.complete()).count();
        var p = s.worker();
        var t = s.target();
        Resident resident = new Resident("miner-1","MINER",
            new WorldPosition(p.x()+.5,p.y(),p.z()+.5),
            t == null ? null : new WorldPosition(t.x()+.5,t.y(),t.z()+.5),
            s.phase()+" / "+s.minerState(),
            Map.of("segment",s.activeSegment(),"length",segment.lengthBlocks(),
                "direction",segment.direction().name(),"progress",segment.nextBlockIndex(),
                "totalBlocks",segment.blockCount(),"interrupted",s.interrupted(),
                "savedProgress",s.segments().get(1).nextBlockIndex()));
        recorder.capture(step, branchingMineWorld(s), List.of(resident), s.lastAction(),
            Map.of("excavatedBlocks",excavated,"supports",supports,"completedSegments",completed,
                "totalSegments",s.segments().size(),"phase",s.phase()));
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
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            world.put(new BlockPosition(x, -1, z), FIELD);
        }
        s.trees().forEach(t -> {
            for (int y = 0; y < 4; y++) world.put(
                new BlockPosition(t.position().x(), t.position().y() + y, t.position().z()), WOOD);
        });
        s.constructionSites().forEach(site -> {
            WorldPosition p = site.workPoint();
            for (int y = 0; y < (site.completed() ? 3 : 1); y++) world.put(
                new BlockPosition((int) Math.floor(p.x()), (int) Math.floor(p.y()) + y,
                    (int) Math.floor(p.z())), site.completed() ? PREFAB : CONSTRUCTION);
        });
        s.farmFields().forEach(field -> {
            WorldPosition p = field.position();
            world.put(new BlockPosition((int) Math.floor(p.x()), (int) Math.floor(p.y()) - 1,
                (int) Math.floor(p.z())), WOOD);
        });
        return world;
    }

    private static void captureRuntime(Recorder recorder, SimulationRuntime.WorldSnapshot s) {
        List<Resident> residents = s.residents().stream().map(r -> new Resident(r.id(),
            r.profession().name(), r.position(), r.movementTarget(), r.state(),
            Map.<String, Object>of("autonomousState", r.autonomousState(),
                "manualMovementActive", r.manualMovementActive()))).toList();
        Map<String, Object> metrics = JSON.convertValue(s.metrics(),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        recorder.capture(s.elapsedSeconds(), runtimeWorld(s), residents, null, metrics);
    }
}
