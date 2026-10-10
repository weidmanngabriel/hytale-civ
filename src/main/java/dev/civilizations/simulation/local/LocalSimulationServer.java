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
    private volatile boolean running;
    private volatile int ticksPerFrame = 1;
    private SimulationScenario scenario = SimulationScenarios.DEMO_SETTLEMENT;
    private SimulationRuntime runtime = newRuntime();
    private int additionalWoodcutters;
    private int additionalBuilders;
    private final WorldArchive sourceArchive;
    private VoxelWorld voxelWorld;

    public LocalSimulationServer(int port) throws IOException { this(port, null); }

    public LocalSimulationServer(int port, WorldArchive archive) throws IOException {
        sourceArchive = archive;
        voxelWorld = archive == null ? null : new VoxelWorld(archive);
        if (archive != null) { scenario = customMiners(3); runtime = scenario.createRuntime(); }
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api/scenarios", this::scenarios);
        server.createContext("/api/state", this::state);
        server.createContext("/api/control", this::control);
        server.createContext("/api/terrain", this::terrain);
        server.setExecutor(requests);
        ticker.scheduleAtFixedRate(() -> {
            if (!running) return;
            synchronized (lock) {
                if (running) runtime.runTicks(ticksPerFrame);
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
        var all = new ArrayList<SimulationScenario>(SimulationScenarios.all());
        if (sourceArchive != null) all.add(customMiners(3));
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
                "world", runtime.worldSnapshot()
            ));
        }
    }

    private void terrain(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) { respond(exchange,405,Map.of("error","GET required")); return; }
        synchronized(lock) {
            if (sourceArchive == null) { respond(exchange,200,Map.of("loaded",false)); return; }
            var bounds=sourceArchive.bounds();
            var cells=new ArrayList<int[]>();
            for (var cell:sourceArchive.cells()) {
                var category=voxelWorld.material(new BlockPosition(cell.x(),cell.y(),cell.z()));
                int code=switch(category) {
                    case AIR -> 0;
                    case SOLID -> 1;
                    case WATER -> 2;
                    case LAVA -> 3;
                    case OTHER_FLUID -> 4;
                };
                if(code!=0)cells.add(new int[]{cell.x(),cell.y(),cell.z(),code});
            }
            respond(exchange,200,Map.of("loaded",true,"worldId",sourceArchive.worldId(),
                "bounds",new int[]{bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()},
                "cells",cells));
        }
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
                    case "play" -> running = true;
                    case "pause" -> running = false;
                    case "step" -> { running = false; runtime.tick(); }
                    case "reset" -> {
                        running = false;
                        voxelWorld = sourceArchive == null ? null : new VoxelWorld(sourceArchive);
                        runtime = newRuntime();
                    }
                    case "speed" -> {
                        int value = data.path("value").asInt(0);
                        if (value != 1 && value != 5 && value != 20) throw new IllegalArgumentException("Invalid speed");
                        ticksPerFrame = value;
                    }
                    case "configure" -> {
                        int woodcutters = data.path("woodcutters").asInt(-1);
                        int builders = data.path("builders").asInt(-1);
                        if (woodcutters < 0 || woodcutters > 12 || builders < 0 || builders > 12)
                            throw new IllegalArgumentException("Worker counts must be between 0 and 12");
                        additionalWoodcutters = woodcutters;
                        additionalBuilders = builders;
                        running = false;
                        runtime = newRuntime();
                    }
                    case "configureMiners" -> {
                        int miners = data.path("miners").asInt(0);
                        if (miners < 1 || miners > 20) throw new IllegalArgumentException("miners must be 1..20");
                        running = false;
                        scenario = customMiners(miners);
                        runtime = scenario.createRuntime();
                    }
                    case "scenario" -> {
                        String id = data.path("id").asText("");
                        scenario = id.equals("custom-miners") ? customMiners(3) : SimulationScenarios.all().stream()
                            .filter(s -> s.id().equals(id)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Unknown scenario"));
                        running = false;
                        runtime = scenario.createRuntime();
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

    private SimulationScenario customMiners(int count) {
        return new SimulationScenario("custom-miners","Configured Miners",
            "Dynamic miner start state (navigation lab; excavation work is not yet enabled).",
            () -> {
                var sim = new SimulationRuntime();
                if (voxelWorld != null) {
                    sim.setVoxelWorld(voxelWorld);
                    int placed = 0;
                    var bounds = sourceArchive.bounds();
                    for (int y = bounds.maxY() - 2; y >= bounds.minY() + 1 && placed < count; y--)
                        for (int x = bounds.minX(); x < bounds.maxX() && placed < count; x++)
                            for (int z = bounds.minZ(); z < bounds.maxZ() && placed < count; z++) {
                                var feet = new BlockPosition(x,y,z);
                                if (!voxelWorld.canStand(feet)) continue;
                                sim.addMiner("miner-"+(++placed),new WorldPosition(x+0.5,y,z+0.5));
                            }
                    if (placed != count) throw new IllegalArgumentException("Insufficient standable positions");
                } else {
                    for (int i=0;i<count;i++) sim.addMiner("miner-"+(i+1),new WorldPosition(i*2,0,0));
                }
                return sim;
            });
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

    public static void main(String[] args) throws Exception {
        WorldArchive archive = args.length == 0 ? null : WorldArchive.read(Path.of(args[0]));
        LocalSimulationServer app = new LocalSimulationServer(8765, archive);
        Runtime.getRuntime().addShutdownHook(new Thread(app::close));
        app.start();
        System.out.println("Civ local simulation API: http://localhost:8765/api/state");
        Thread.currentThread().join();
    }
}
