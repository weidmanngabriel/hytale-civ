package dev.civilizations.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivUnitRegistry;
import dev.civilizations.hytale.NpcInfoProvider;
import org.joml.Vector3d;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Opt-in private HTTP bridge. MCP itself runs as a separate local stdio process. */
final class CivDevBridge implements AutoCloseable {
    private static final int MAX_BODY = 65_536;
    private static final String CIV_ROLE = "Civ_Inhabitant";
    private final ObjectMapper mapper = new ObjectMapper();
    private final CivUnitRegistry units;
    private final CivActivityRegistry activities;
    private final NpcInfoProvider npcInfo;
    private final String token;
    private final String sessionId;
    private final String worldName = "civ-mcp-" + UUID.randomUUID().toString().substring(0, 8);
    // Accessed only on the arena's World thread; Refs never escape in responses.
    private final Map<String, Ref<EntityStore>> tracked = new LinkedHashMap<>();
    private CompletableFuture<World> arena;
    private HttpServer server;
    private ExecutorService executor;

    CivDevBridge(CivUnitRegistry units, CivActivityRegistry activities) {
        this(units, activities, System.getenv("CIV_DEV_TOKEN"), System.getenv("CIV_DEV_SESSION"));
    }

    CivDevBridge(CivUnitRegistry units, CivActivityRegistry activities, String token, String sessionId) {
        this.units = units;
        this.activities = activities;
        this.npcInfo = new NpcInfoProvider(units, activities);
        this.token = token;
        this.sessionId = sessionId;
        if (token == null || token.length() < 32 || sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("Development bridge requires CIV_DEV_TOKEN and CIV_DEV_SESSION");
        }
    }

    void start() throws IOException {
        int port = Integer.getInteger("civilizations.devBridgePort", 5522);
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Invalid development bridge port");
        start(port);
    }

    void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 8);
        executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "civ-dev-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/dev", this::handle);
        server.start();
        System.out.println("CIV_DEV_BRIDGE_STARTED address=127.0.0.1 port=" + port);
    }

    int port() { return server.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"/dev".equals(exchange.getRequestURI().getPath()) || !"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, Map.of("error", "Only POST /dev is supported")); return;
            }
            if (exchange.getRequestHeaders().getFirst("Origin") != null
                || !constantEquals("Bearer " + token, exchange.getRequestHeaders().getFirst("Authorization"))
                || !constantEquals(sessionId, exchange.getRequestHeaders().getFirst("X-Civ-Session"))) {
                respond(exchange, 403, Map.of("error", "Forbidden")); return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) { respond(exchange, 413, Map.of("error", "Request too large")); return; }
            JsonNode input = mapper.readTree(body);
            if (input == null || !input.isObject()) throw new IllegalArgumentException("Expected JSON object");
            Map<String, Object> result = dispatch(input).get(12, TimeUnit.SECONDS);
            result.put("sessionId", sessionId);
            respond(exchange, 200, result);
        } catch (java.util.concurrent.TimeoutException exception) {
            respond(exchange, 504, Map.of("error", "Operation timed out; it may still complete. Inspect state before retrying."));
        } catch (Exception exception) {
            Throwable cause = exception;
            while (cause.getCause() != null) cause = cause.getCause();
            String reason = cause instanceof IllegalArgumentException || cause instanceof IllegalStateException
                ? cause.getMessage() : "Bridge operation failed; inspect server logs";
            System.err.println("CIV_DEV_BRIDGE_ERROR type=" + cause.getClass().getSimpleName());
            respond(exchange, 400, Map.of("error", reason == null ? "Invalid operation" : reason));
        } finally { exchange.close(); }
    }

    private CompletableFuture<Map<String, Object>> dispatch(JsonNode input) {
        String action = requiredText(input, "action");
        if ("status".equals(action)) {
            return CompletableFuture.completedFuture(result("ready", Universe.get() != null && Universe.get().getDefaultWorld() != null,
                "world", worldName, "bridgeVersion", 1));
        }
        if ("roles".equals(action)) {
            String query = input.path("query").asText("").toLowerCase(java.util.Locale.ROOT);
            List<String> names = NPCPlugin.get().getRoleTemplateNames(true).stream()
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains(query)).sorted().limit(100).toList();
            return CompletableFuture.completedFuture(result("roles", names, "limit", 100));
        }
        if ("arena".equals(action)) return createArena().thenApply(world -> result("world", worldName, "ready", true));
        CompletableFuture<World> current;
        synchronized (this) { current = arena; }
        if (current == null || !current.isDone() || current.isCompletedExceptionally()) {
            throw new IllegalStateException("Create the development arena first");
        }
        return current.thenCompose(world -> onWorld(world, () -> switch (action) {
            case "entities" -> result("world", worldName, "entities", snapshots());
            case "spawn" -> {
                String role = requiredText(input, "role");
                Profession profession = input.has("profession") ? Profession.valueOf(requiredText(input, "profession")) : null;
                yield result("entity", spawn(world, role, position(input), profession));
            }
            case "move" -> {
                Ref<EntityStore> ref = entity(requiredText(input, "handle"));
                WorldPosition position = position(input);
                if (!activities.orderManualMove(ref, position)) throw new IllegalArgumentException("Manual movement requires a tracked Civ NPC");
                yield result("accepted", true, "entity", snapshot(requiredText(input, "handle"), ref));
            }
            case "profession" -> {
                Ref<EntityStore> ref = entity(requiredText(input, "handle"));
                if (!units.isClaimed(ref)) throw new IllegalArgumentException("Profession requires a tracked Civ NPC");
                units.assignProfession(ref, Profession.valueOf(requiredText(input, "profession")));
                yield result("entity", snapshot(requiredText(input, "handle"), ref));
            }
            case "reset" -> result("removed", reset(), "world", worldName);
            case "soldier_scenario" -> {
                if (!NPCPlugin.get().hasRoleName("Chicken_Undead")) throw new IllegalStateException("Chicken_Undead role is unavailable in this runtime");
                reset();
                try {
                    var soldier = spawn(world, CIV_ROLE, new WorldPosition(0.5, 1, 0.5), Profession.SOLDIER);
                    var opponent = spawn(world, "Chicken_Undead", new WorldPosition(8.5, 1, 0.5), null);
                    yield result("world", worldName, "soldier", soldier, "opponent", opponent);
                } catch (RuntimeException exception) { reset(); throw exception; }
            }
            default -> throw new IllegalArgumentException("Unknown development action");
        }));
    }

    private synchronized CompletableFuture<World> createArena() {
        if (arena != null) return arena;
        if (Universe.get() == null || Universe.get().getDefaultWorld() == null) throw new IllegalStateException("Hytale is still starting");
        arena = Universe.get().addWorld(worldName, "Flat", "default").thenCompose(world ->
            onWorld(world, () -> {
                world.getWorldConfig().setCanUnloadChunks(false);
                List<CompletableFuture<?>> chunks = new ArrayList<>();
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    chunks.add(world.getChunkAsync(ChunkUtil.indexChunkFromBlock(x * 32, z * 32)));
                }
                return CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new));
            }).thenCompose(future -> future).thenApply(ignored -> world));
        return arena;
    }

    private Map<String, Object> spawn(World world, String role, WorldPosition position, Profession profession) {
        if (tracked.size() >= 32) throw new IllegalStateException("Reset before spawning more than 32 test NPCs");
        if (profession != null && !CIV_ROLE.equals(role)) throw new IllegalArgumentException("Only Civ_Inhabitant can receive a Civ profession");
        if (!NPCPlugin.get().hasRoleName(role)) throw new IllegalArgumentException("Unknown NPC role: " + role);
        var pair = NPCPlugin.get().spawnNPC(world.getEntityStore().getStore(), role, null,
            new Vector3d(position.x(), position.y(), position.z()), new Rotation3f());
        if (pair == null || pair.first() == null || !pair.first().isValid()) throw new IllegalStateException("Native NPC spawn failed");
        Ref<EntityStore> ref = pair.first();
        String handle = UUID.randomUUID().toString();
        tracked.put(handle, ref);
        try {
            if (CIV_ROLE.equals(role)) {
                if (!units.toggleClaim(ref)) throw new IllegalStateException("Civ claim failed");
                if (profession != null) units.assignProfession(ref, profession);
            }
            return snapshot(handle, ref);
        } catch (RuntimeException exception) {
            tracked.remove(handle);
            if (ref.isValid()) ref.getStore().removeEntity(ref, RemoveReason.REMOVE);
            throw exception;
        }
    }

    private Ref<EntityStore> entity(String handle) {
        Ref<EntityStore> ref = tracked.get(handle);
        if (ref == null || !ref.isValid()) throw new IllegalArgumentException("Unknown or invalid test NPC handle");
        return ref;
    }

    private List<Map<String, Object>> snapshots() {
        return tracked.entrySet().stream().map(entry -> snapshot(entry.getKey(), entry.getValue())).toList();
    }

    private Map<String, Object> snapshot(String handle, Ref<EntityStore> ref) {
        Map<String, Object> data = result("handle", handle, "valid", ref.isValid(), "entityIndex", ref.getIndex());
        if (!ref.isValid()) { data.put("note", "Invalid Ref is not proof of death"); return data; }
        var store = ref.getStore();
        var uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        data.put("uuid", uuid == null ? null : uuid.getUuid().toString());
        var npc = store.getComponent(ref, NPCEntity.getComponentType());
        data.put("role", npc == null ? null : npc.getRoleName());
        var transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null) {
            var p = transform.getPosition();
            data.put("position", result("x", p.x, "y", p.y, "z", p.z));
        }
        var stats = store.getComponent(ref, EntityStatMap.getComponentType());
        var health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        data.put("health", health == null || !Float.isFinite(health.get()) ? null : health.get());
        data.put("maxHealth", health == null || !Float.isFinite(health.getMax()) ? null : health.getMax());
        data.put("civ", npcInfo.snapshot(ref));
        data.put("autonomousWorkAllowed", units.isClaimed(ref) ? activities.autonomousWorkAllowed(ref) : null);
        var target = units.isClaimed(ref) ? units.getMoveTarget(ref) : null;
        data.put("movementTarget", target == null ? null : result("x", target.x, "y", target.y, "z", target.z));
        return data;
    }

    private int reset() {
        int count = tracked.size();
        for (Ref<EntityStore> ref : tracked.values()) if (ref.isValid()) {
            activities.forget(ref);
            units.forget(ref);
            ref.getStore().removeEntity(ref, RemoveReason.REMOVE);
        }
        tracked.clear();
        return count;
    }

    private static WorldPosition position(JsonNode input) {
        double x = coordinate(input, "x", -24, 24);
        double y = coordinate(input, "y", 1, 16);
        double z = coordinate(input, "z", -24, 24);
        return new WorldPosition(x, y, z);
    }
    private static double coordinate(JsonNode input, String key, double min, double max) {
        JsonNode field = input.get(key);
        if (field == null || !field.isNumber()) throw new IllegalArgumentException("Missing coordinate " + key);
        double value = field.doubleValue();
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Coordinate out of arena bounds: " + key);
        return value;
    }
    private static String requiredText(JsonNode input, String key) {
        JsonNode value = input.get(key);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 128) throw new IllegalArgumentException("Invalid " + key);
        return value.textValue();
    }
    private static boolean constantEquals(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
    private static <T> CompletableFuture<T> onWorld(World world, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        world.execute(() -> {
            try { result.complete(action.get()); } catch (Throwable throwable) { result.completeExceptionally(throwable); }
        });
        return result;
    }
    private static Map<String, Object> result(Object... fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i += 2) result.put((String) fields[i], fields[i + 1]);
        return result;
    }
    private void respond(HttpExchange exchange, int status, Object result) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(result);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
    @Override public void close() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }
}
