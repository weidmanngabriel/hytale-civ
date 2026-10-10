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
        if (!exchange.getRequestMethod().equals("POST")) { respond(exchange, 405, Map.of("error", "POST required")); return; }
        if (!isLocalOrigin(exchange)) { respond(exchange, 403, Map.of("error", "Only local clients")); return; }
        if (exchange.getRequestBody().readNBytes(2049).length > 2048) { respond(exchange, 413, Map.of("error", "Request too large")); return; }
        // Request body must be decoded once; the control commands themselves are intentionally small.
        // The request is buffered by the caller only for a single bounded JSON object.
        respond(exchange, 400, Map.of("error", "Missing command"));
    }

    private static boolean isLocalOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        return origin == null || origin.matches("http://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?");
    }

    private static void respond(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "http://localhost:5173");
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
