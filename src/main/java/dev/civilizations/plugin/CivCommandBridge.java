package dev.civilizations.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.util.MessageUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Small localhost-only development command endpoint.
 *
 * <p>This is intentionally independent from the structured CivDevBridge attach lifecycle:
 * it exists only to prove reliable external control and return command output directly.</p>
 */
final class CivCommandBridge implements AutoCloseable {
    private static final int MAX_BODY = 16_384;
    private static final int MAX_COMMAND = 4_096;
    private static final Pattern ANSI = Pattern.compile("\\x1B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])");

    private final ObjectMapper mapper = new ObjectMapper();
    private final HytaleLogger logger;
    private HttpServer server;
    private ExecutorService executor;

    CivCommandBridge(HytaleLogger logger) {
        this.logger = logger;
    }

    void start() throws IOException {
        int port = Integer.getInteger("civilizations.commandBridgePort", 5523);
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Invalid command bridge port");
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 8);
        executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "civ-command-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/health", this::handleHealth);
        server.createContext("/command", this::handle);
        server.start();
        logger.atInfo().log("Civ command bridge listening on 127.0.0.1:%d", port);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        try {
            if (!"/health".equals(exchange.getRequestURI().getPath()) || !"GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, Map.of("error", "Only GET /health is supported"));
                return;
            }
            respond(exchange, 200, Map.of("ready", true, "version", 1));
        } finally {
            exchange.close();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"/command".equals(exchange.getRequestURI().getPath()) || !"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, Map.of("error", "Only POST /command is supported"));
                return;
            }
            if (exchange.getRequestHeaders().getFirst("Origin") != null) {
                respond(exchange, 403, Map.of("error", "Browser-origin requests are not accepted"));
                return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) {
                respond(exchange, 413, Map.of("error", "Request too large"));
                return;
            }
            JsonNode input = mapper.readTree(body);
            String command = input == null ? null : input.path("command").asText(null);
            if (command == null || command.isBlank() || command.length() > MAX_COMMAND) {
                respond(exchange, 400, Map.of("error", "command must be a non-empty bounded string"));
                return;
            }
            respond(exchange, 200, execute(command));
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Civ command bridge request failed");
            respond(exchange, 500, Map.of("error", "Command bridge request failed"));
        } finally {
            exchange.close();
        }
    }

    private Map<String, Object> execute(String rawCommand) {
        String command = rawCommand.strip();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        CollectingSender sender = new CollectingSender();
        try {
            CommandManager.get().handleCommand(sender, command).get(10, TimeUnit.SECONDS);
            return Map.of("success", true, "command", command, "output", sender.snapshot());
        } catch (java.util.concurrent.TimeoutException exception) {
            return Map.of("success", false, "command", command, "output", sender.snapshot(),
                "error", "Command timed out after 10 seconds");
        } catch (Exception exception) {
            Throwable cause = exception;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            logger.atWarning().withCause(cause).log("Remote development command failed: %s", command);
            return Map.of("success", false, "command", command, "output", sender.snapshot(),
                "error", cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
        }
    }

    private void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private static final class CollectingSender implements CommandSender {
        private final List<String> lines = new ArrayList<>();

        @Override
        public synchronized void sendMessage(Message message) {
            if (message != null) {
                String text = MessageUtil.toAnsiString(message).toString();
                for (String line : text.split("\\R")) {
                    String sanitized = ANSI.matcher(line).replaceAll("").strip();
                    if (!sanitized.isEmpty()) {
                        lines.add(sanitized);
                    }
                }
            }
        }

        @Override
        public String getUsername() {
            return ConsoleSender.INSTANCE.getUsername();
        }

        @Override
        public UUID getUuid() {
            return ConsoleSender.INSTANCE.getUuid();
        }

        @Override
        public boolean hasPermission(String permission) {
            return ConsoleSender.INSTANCE.hasPermission(permission);
        }

        @Override
        public boolean hasPermission(String permission, boolean defaultValue) {
            return ConsoleSender.INSTANCE.hasPermission(permission, defaultValue);
        }

        synchronized List<String> snapshot() {
            return List.copyOf(lines);
        }
    }
}
