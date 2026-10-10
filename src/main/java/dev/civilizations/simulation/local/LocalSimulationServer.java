package dev.civilizations.simulation.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.civilizations.simulation.SimulationRuntime;
import dev.civilizations.simulation.SimulationScenario;
import dev.civilizations.simulation.SimulationScenarios;

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
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    private final Object lock = new Object();
    private volatile boolean running;
    private volatile int ticksPerFrame = 1;
    private SimulationScenario scenario = SimulationScenarios.DEMO_SETTLEMENT;
    private SimulationRuntime runtime = scenario.createRuntime();

    public LocalSimulationServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api/scenarios", this::scenarios);
        server.createContext("/api/state", this::state);
        server.createContext("/api/control", this::control);
        server.setExecutor(Executors.newCachedThreadPool());
        ticker.scheduleAtFixedRate(() -> {
            if (!running) return;
            synchronized (lock) {
                if (running) runtime.runTicks(ticksPerFrame);
            }
        }, 50, 50, TimeUnit.MILLISECONDS);
    }

    public void start() { server.start(); }

    public int port() { return server.getAddress().getPort(); }

    private void scenarios(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) { respond(exchange, 405, Map.of("error", "GET required")); return; }
        respond(exchange, 200, SimulationScenarios.all().stream().map(s -> Map.of(
            "id", s.id(), "title", s.displayName(), "description", s.description()
        )).toList());
    }

    private void state(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) { respond(exchange, 405, Map.of("error", "GET required")); return; }
        synchronized (lock) {
            respond(exchange, 200, Map.of(
                "scenario", scenario.id(), "running", running, "speed", ticksPerFrame,
                "world", runtime.worldSnapshot()
            ));
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
                    case "reset" -> { running = false; runtime = scenario.createRuntime(); }
                    case "speed" -> {
                        int value = data.path("value").asInt(0);
                        if (value != 1 && value != 5 && value != 20) throw new IllegalArgumentException("Invalid speed");
                        ticksPerFrame = value;
                    }
                    case "scenario" -> {
                        String id = data.path("id").asText("");
                        scenario = SimulationScenarios.all().stream().filter(s -> s.id().equals(id)).findFirst()
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
    }

    public static void main(String[] args) throws Exception {
        LocalSimulationServer app = new LocalSimulationServer(8765);
        Runtime.getRuntime().addShutdownHook(new Thread(app::close));
        app.start();
        System.out.println("Civ local simulation API: http://localhost:8765/api/state");
        Thread.currentThread().join();
    }
}
