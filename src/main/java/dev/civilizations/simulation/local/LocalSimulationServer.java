package dev.civilizations.simulation.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.civilizations.simulation.SimulationRuntime;
import dev.civilizations.simulation.SimulationScenario;
import dev.civilizations.simulation.SimulationScenarios;
import dev.civilizations.simulation.world.WorldArchive;
import dev.civilizations.simulation.world.VoxelWorld;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.core.MineHeading;
import java.util.Comparator;
import java.nio.file.Path;
import java.util.ArrayList;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Localhost-only live simulation. No precomputed recordings and no Hytale runtime dependency. */
public final class LocalSimulationServer implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    private final Object lock = new Object();
    private final SimulationEventLog eventLog = new SimulationEventLog();
    private final SimulationEventJournal journal = new SimulationEventJournal();
    private volatile boolean running;
    private volatile int ticksPerFrame = 1;
    private SimulationScenario scenario = SimulationScenarios.DEMO_SETTLEMENT;
    private SimulationRuntime runtime = newRuntime();
    private int additionalWoodcutters;
    private int additionalBuilders;
    private final WorldArchive sourceArchive;
    private VoxelWorld voxelWorld;
    /** Changes when the entire voxel world is replaced; distinct from block revision. */
    private long terrainEpoch;
    private MineSandboxPrefab.Placement minePlacement;
    private MineSandboxPrefab minePrefab;

    public LocalSimulationServer(int port) throws IOException { this(port, null); }

    public LocalSimulationServer(int port, WorldArchive archive) throws IOException {
        sourceArchive = archive;
        voxelWorld = archive == null ? null : new VoxelWorld(archive);
        if (archive != null) { scenario = customMiners(0); runtime = scenario.createRuntime(); }
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api/scenarios", this::scenarios);
        server.createContext("/api/state", this::state);
        server.createContext("/api/control", this::control);
        server.createContext("/api/terrain", this::terrain);
        server.createContext("/api/debug", this::debug);
        server.setExecutor(requests);
        ticker.scheduleAtFixedRate(() -> {
            if (!running) return;
            synchronized (lock) {
                if (running) for (int i = 0; i < ticksPerFrame; i++) {
                    runtime.tick();
                    journal.observe(runtime.worldSnapshot());
                }
            }
        }, 50, 50, TimeUnit.MILLISECONDS);
    }

    private SimulationRuntime newRuntime() {
        SimulationRuntime result = scenario.createRuntime();
        for (int i = 0; i < additionalWoodcutters; i++)
            result.addWoodcutter("configured-woodcutter-" + i, new dev.civilizations.core.WorldPosition(-12, 0, -12 - i));
        for (int i = 0; i < additionalBuilders; i++)
            result.addConstructionWorker("configured-builder-" + i, new dev.civilizations.core.WorldPosition(-12, 0, 12 + i));
        return result;
    }

    public void start() { server.start(); }

    public int port() { return server.getAddress().getPort(); }

    private void scenarios(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) { respond(exchange, 405, Map.of("error", "GET required")); return; }
        var all = sourceArchive == null ? SimulationScenarios.all() : List.of(customMiners(0));
        respond(exchange, 200, all.stream().map(s -> Map.of(
            "id", s.id(), "title", s.displayName(), "description", s.description()
        )).toList());
    }

    private void state(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) { respond(exchange, 405, Map.of("error", "GET required")); return; }
        synchronized (lock) {
            respond(exchange, 200, Map.of(
                "scenario", scenario.id(), "running", running, "speed", ticksPerFrame,
                "additionalWoodcutters", additionalWoodcutters, "additionalBuilders", additionalBuilders,
                "events", eventLog.snapshot(), "stateTransitions", journal.events(), "world", runtime.worldSnapshot(), "worldRevision", voxelWorld == null ? 0 : voxelWorld.revision(),
                "terrainEpoch", terrainEpoch
            ));
        }
    }

    private void debug(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            respond(exchange,405,Map.of("error","GET required")); return;
        }
        synchronized(lock) {
            respond(exchange,200,Map.of(
                "mine",runtime.mineDebugSnapshot(),
                "prefab",minePlacement == null ? Map.of("placed",false) : Map.of(
                    "placed",true,"origin",minePlacement.origin(),
                    "markers",minePlacement.markers())
            ));
        }
    }

    private MineSandboxPrefab prefab() throws IOException {
        if (minePrefab == null) minePrefab = new MineSandboxPrefab(MineSandboxPrefab.DEFAULT_PREFAB);
        return minePrefab;
    }

    private void placeMine(BlockPosition origin) throws IOException {
        if (sourceArchive == null) throw new IllegalArgumentException("Load a world archive first");
        var fresh = new VoxelWorld(sourceArchive);
        var placed = prefab().place(fresh,sourceArchive.bounds(),origin);
        voxelWorld = fresh;
        terrainEpoch++;
        minePlacement = placed;
        scenario = customMiners(0);
        runtime = scenario.createRuntime();
        running = false;
        journal.reset();
        eventLog.record(runtime.tickCount(),"MINE",
            "Mine_01 placed at "+origin+"; access="+placed.access()+"; connector="+placed.connector());
    }

    private BlockPosition findMineOrigin() throws IOException {
        if (sourceArchive == null) throw new IllegalArgumentException("No imported world");
        var bounds=sourceArchive.bounds();
        var model=prefab().model().blockBounds();
        int centerX=(bounds.minX()+bounds.maxX())/2,centerZ=(bounds.minZ()+bounds.maxZ())/2;
        // Prefer a standable surface near the center, with full prefab below and above it.
        for(int radius=0;radius<=20;radius+=4) {
            for(int x=centerX-radius;x<=centerX+radius;x+=4)
                for(int z=centerZ-radius;z<=centerZ+radius;z+=4) {
                    if(x+model.minX()<bounds.minX()||x+model.maxX()>=bounds.maxX()
                        ||z+model.minZ()<bounds.minZ()||z+model.maxZ()>=bounds.maxZ())continue;
                    for(int feetY=bounds.maxY()-10;feetY>=bounds.minY()+17;feetY--) {
                        if (!voxelWorld.canStand(new BlockPosition(x,feetY,z))) continue;
                        int originY=feetY-17;
                        if(originY+model.maxY()>=bounds.maxY()||originY+model.minY()<bounds.minY())continue;
                        return new BlockPosition(x,originY,z);
                    }
                }
        }
        throw new IllegalArgumentException("No suitable mine site near world center; specify /sim mine place X Y Z");
    }

    private void terrain(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) { respond(exchange,405,Map.of("error","GET required")); return; }
        synchronized(lock) {
            if (sourceArchive == null) { respond(exchange,200,Map.of("loaded",false)); return; }
            long since = -1;
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && query.matches("since=[0-9]{1,16}")) {
                try { since = Long.parseLong(query.substring(6)); } catch (NumberFormatException ignored) {}
            }
            var changes = voxelWorld.changesSince(since);
            if (changes != null) {
                var changed = new ArrayList<int[]>(changes.size());
                for (var change : changes) {
                    var p = change.position();
                    changed.add(new int[]{p.x(), p.y(), p.z(), materialCode(change.material())});
                }
                respond(exchange,200,Map.of("loaded",true,"revision",voxelWorld.revision(),"changes",changed));
                return;
            }
            var bounds=sourceArchive.bounds();
            var cells=new ArrayList<int[]>();
            for (var cell:sourceArchive.cells()) {
                var category=voxelWorld.material(new BlockPosition(cell.x(),cell.y(),cell.z()));
                int code=materialCode(category);
                if(code!=0)cells.add(new int[]{cell.x(),cell.y(),cell.z(),code});
            }
            respond(exchange,200,Map.of("loaded",true,"worldId",sourceArchive.worldId(),
                "bounds",new int[]{bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()},
                "cells",cells));
        }
    }

    private static int materialCode(WorldArchive.Material category) {
        return switch(category) {
            case AIR -> 0;
            case SOLID -> 1;
            case WATER -> 2;
            case LAVA -> 3;
            case OTHER_FLUID -> 4;
        };
    }

    private void control(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("OPTIONS")) { preflight(exchange); return; }
        if (!exchange.getRequestMethod().equals("POST")) { respond(exchange, 405, Map.of("error", "POST required")); return; }
        if (!isLocalOrigin(exchange)) { respond(exchange, 403, Map.of("error", "Only local clients")); return; }
        byte[] body = exchange.getRequestBody().readNBytes(2049);
        if (body.length > 2048) { respond(exchange, 413, Map.of("error", "Request too large")); return; }
        try {
            var data = JSON.readTree(body);
            String command = data.path("command").asText("");
            synchronized (lock) {
                switch (command) {
                    case "play" -> { running = true; eventLog.record(runtime.tickCount(), "CONTROL", "play"); }
                    case "pause" -> { running = false; eventLog.record(runtime.tickCount(), "CONTROL", "pause"); }
                    case "step" -> { running = false; runtime.tick(); journal.observe(runtime.worldSnapshot()); eventLog.record(runtime.tickCount(), "CONTROL", "step"); }
                    case "reset" -> {
                        running = false;
                        eventLog.clear();
                        journal.reset();
                        voxelWorld = sourceArchive == null ? null : new VoxelWorld(sourceArchive);
                        terrainEpoch++;
                        if (minePlacement != null)
                            minePlacement = prefab().place(voxelWorld,sourceArchive.bounds(),minePlacement.origin());
                        runtime = newRuntime();
                    }
                    case "move" -> {
                        String id = data.path("id").asText("");
                        var position = new WorldPosition(
                            finiteCoordinate(data, "x"), finiteCoordinate(data, "y"), finiteCoordinate(data, "z"));
                        runtime.orderManualMove(id,position);
                        eventLog.record(runtime.tickCount(), "MOVE", id);
                    }
                    case "setBlock" -> {
                        if (voxelWorld == null) throw new IllegalArgumentException("No imported terrain");
                        int x = data.path("x").asInt(Integer.MIN_VALUE);
                        int y = data.path("y").asInt(Integer.MIN_VALUE);
                        int z = data.path("z").asInt(Integer.MIN_VALUE);
                        String category = data.path("category").asText("");
                        var material = switch (category) {
                            case "AIR" -> WorldArchive.Material.AIR;
                            case "SOLID" -> WorldArchive.Material.SOLID;
                            case "WATER" -> WorldArchive.Material.WATER;
                            case "LAVA" -> WorldArchive.Material.LAVA;
                            default -> throw new IllegalArgumentException("Unsupported material");
                        };
                        voxelWorld.set(new BlockPosition(x,y,z), material);
                        eventLog.record(runtime.tickCount(), "BLOCK", x + "," + y + "," + z + "=" + category);
                    }
                    case "speed" -> {
                        int value = data.path("value").asInt(0);
                        if (value != 1 && value != 5 && value != 20) throw new IllegalArgumentException("Invalid speed");
                        ticksPerFrame = value;
                    }
                    case "configure" -> {
                        if (sourceArchive != null) throw new IllegalArgumentException("Use configureMiners with an imported world");
                        int woodcutters = data.path("woodcutters").asInt(-1);
                        int builders = data.path("builders").asInt(-1);
                        if (woodcutters < 0 || woodcutters > 12 || builders < 0 || builders > 12)
                            throw new IllegalArgumentException("Worker counts must be between 0 and 12");
                        additionalWoodcutters = woodcutters;
                        additionalBuilders = builders;
                        running = false;
                        runtime = newRuntime();
                        journal.reset();
                    }
                    case "placeMine" -> {
                        placeMine(new BlockPosition(data.path("x").asInt(Integer.MIN_VALUE),
                            data.path("y").asInt(Integer.MIN_VALUE),data.path("z").asInt(Integer.MIN_VALUE)));
                    }
                    case "placeMineAuto" -> { placeMine(findMineOrigin()); }
                    case "configureMiners" -> {
                        int miners = data.path("miners").asInt(0);
                        if (miners < 1 || miners > 20) throw new IllegalArgumentException("miners must be 1..20");
                        running = false;
                        scenario = customMiners(miners);
                        runtime = scenario.createRuntime();
                        journal.reset();
                    }
                    case "scenario" -> {
                        String id = data.path("id").asText("");
                        scenario = sourceArchive != null && id.equals("custom-miners") ? customMiners(0)
                            : sourceArchive == null ? SimulationScenarios.all().stream()
                                .filter(s -> s.id().equals(id)).findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario"))
                            : throwUnknownScenario();
                        running = false;
                        runtime = scenario.createRuntime();
                        journal.reset();
                    }
                    default -> throw new IllegalArgumentException("Unknown command");
                }
                respond(exchange, 200, Map.of("ok", true));
            }
        } catch (IllegalArgumentException ex) {
            respond(exchange, 400, Map.of("error", ex.getMessage()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            respond(exchange, 400, Map.of("error", "Invalid JSON"));
        }
    }

    private static double finiteCoordinate(com.fasterxml.jackson.databind.JsonNode data, String field) {
        var value = data.path(field);
        if (!value.isNumber() || !Double.isFinite(value.asDouble()) || Math.abs(value.asDouble()) > 100000)
            throw new IllegalArgumentException("Invalid coordinate: " + field);
        return value.asDouble();
    }

    private static SimulationScenario throwUnknownScenario() {
        throw new IllegalArgumentException("Only custom miners are available on imported worlds");
    }

    private SimulationScenario customMiners(int count) {
        return new SimulationScenario("custom-miners","Configured Miners",
            "Dynamic miner start state. Large imported regions also run Core-planned excavation.",
            () -> {
                var sim = new SimulationRuntime();
                if (voxelWorld != null) {
                    sim.setVoxelWorld(voxelWorld);
                    if (count == 0) return sim;
                    if (minePlacement != null) {
                        var access=minePlacement.access();
                        var connector=minePlacement.connector();
                        var reachable = new ArrayList<BlockPosition>();
                        for(int dx=-6;dx<=6;dx++) for(int dz=-6;dz<=6;dz++)
                            for(int dy=-2;dy<=2;dy++) {
                                var p=new BlockPosition(access.x()+dx,access.y()+dy,access.z()+dz);
                                if(voxelWorld.canStand(p) && !voxelWorld.path(p,access).isEmpty())
                                    reachable.add(p);
                            }
                        reachable.sort(Comparator
                            .comparingInt((BlockPosition p)->Math.abs(p.x()-access.x())
                                +Math.abs(p.z()-access.z())+Math.abs(p.y()-access.y()))
                            .thenComparingInt(BlockPosition::x).thenComparingInt(BlockPosition::z));
                        if(reachable.isEmpty())throw new IllegalArgumentException("No reachable worker spawn near workplace_access");
                        for(int i=0;i<count;i++) {
                            var p=reachable.get(i%reachable.size());
                            sim.addMiner("miner-"+(i+1),new WorldPosition(p.x()+.5,p.y(),p.z()+.5));
                        }
                        sim.configureMineLab(connector,access,MineHeading.SOUTH,8,99112233L);
                        return sim;
                    }
                    var bounds = sourceArchive.bounds();
                    var candidates = new ArrayList<BlockPosition>();
                    for (int y=bounds.minY()+1;y<bounds.maxY()-1;y++)
                        for(int x=bounds.minX();x<bounds.maxX();x++)
                            for(int z=bounds.minZ();z<bounds.maxZ();z++) {
                                var feet=new BlockPosition(x,y,z);
                                if(voxelWorld.canStand(feet)) candidates.add(feet);
                            }
                    int centerX=(bounds.minX()+bounds.maxX())/2;
                    int centerZ=(bounds.minZ()+bounds.maxZ())/2;
                    candidates.sort(Comparator
                        .comparingInt((BlockPosition p) -> -rockNeighbors(p))
                        .thenComparingInt(p -> Math.abs(p.x()-centerX)+Math.abs(p.z()-centerZ))
                        .thenComparingInt(p -> -p.y())
                        .thenComparingInt(BlockPosition::x)
                        .thenComparingInt(BlockPosition::z));
                    // A standable block in a separate cave is not a reachable worker spawn.
                    // Use a single traversable component; prefer the largest connected region.
                    var remaining = new java.util.HashSet<>(candidates);
                    java.util.Set<BlockPosition> bestComponent = java.util.Set.of();
                    for (var seed : candidates) {
                        if (!remaining.remove(seed)) continue;
                        var component = new java.util.HashSet<BlockPosition>();
                        var queue = new java.util.ArrayDeque<BlockPosition>();
                        component.add(seed);
                        queue.add(seed);
                        while (!queue.isEmpty()) {
                            for (var next : voxelWorld.walkableNeighbors(queue.removeFirst())) {
                                if (remaining.remove(next)) {
                                    component.add(next);
                                    queue.addLast(next);
                                }
                            }
                        }
                        if (component.size() > bestComponent.size()) bestComponent = component;
                    }
                    var connected = bestComponent;
                    candidates.removeIf(p -> !connected.contains(p));
                    if(candidates.size()<count) throw new IllegalArgumentException(
                        "Only " + candidates.size() + " connected standable positions for " + count + " miners");
                    for(int i=0;i<count;i++){
                        var p=candidates.get(i);
                        sim.addMiner("miner-"+(i+1),new WorldPosition(p.x()+.5,p.y(),p.z()+.5));
                    }
                    if(bounds.maxX()-bounds.minX()>=24 && bounds.maxZ()-bounds.minZ()>=24
                        && bounds.maxY()-bounds.minY()>=12){
                        sim.configureMineLab(candidates.getFirst(),MineHeading.NORTH,8,99112233L);
                    }
                } else {
                    for (int i=0;i<count;i++) sim.addMiner("miner-"+(i+1),new WorldPosition(i*2,0,0));
                }
                return sim;
            });
    }

    private int rockNeighbors(BlockPosition p) {
        int solid=0;
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            var material=voxelWorld.material(new BlockPosition(p.x()+d[0],p.y(),p.z()+d[1]));
            if(material==WorldArchive.Material.SOLID)solid++;
        }
        return solid;
    }

    private static String allowedOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        return origin != null && isLocalOrigin(exchange) ? origin : "http://localhost:5173";
    }

    private static void preflight(HttpExchange exchange) throws IOException {
        if (!isLocalOrigin(exchange)) { exchange.sendResponseHeaders(403, -1); return; }
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin(exchange));
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static boolean isLocalOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        return origin == null || origin.matches("http://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?");
    }

    private static void respond(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin(exchange));
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }

    @Override public void close() {
        running = false;
        ticker.shutdownNow();
        server.stop(0);
        requests.shutdownNow();
    }

    private static void startupLog(String stage, long startNanos) {
        System.out.printf("[CIV STARTUP] %s (%.1f s elapsed; heap %.0f MiB used)%n",
            stage, (System.nanoTime() - startNanos) / 1_000_000_000.0,
            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576.0);
        System.out.flush();
    }

    public static void main(String[] args) throws Exception {
        final long started = System.nanoTime();
        startupLog("Initializing", started);
        try {
            WorldArchive archive = null;
            if (args.length > 0) {
                var path = Path.of(args[0]);
                startupLog("Reading archive " + path.toAbsolutePath(), started);
                archive = WorldArchive.read(path);
                startupLog("Archive decoded: " + archive.bounds().volume() + " cells", started);
            }
            startupLog("Creating voxel index and HTTP server", started);
            LocalSimulationServer app = new LocalSimulationServer(8765, archive);
            startupLog("Starting HTTP listener", started);
            Runtime.getRuntime().addShutdownHook(new Thread(app::close));
            app.start();
            startupLog("READY http://localhost:8765/api/state", started);
            Thread.currentThread().join();
        } catch (Throwable error) {
            startupLog("FAILED: " + error.getClass().getSimpleName() + ": " + error.getMessage(), started);
            error.printStackTrace(System.err);
            throw error;
        }
    }
}
