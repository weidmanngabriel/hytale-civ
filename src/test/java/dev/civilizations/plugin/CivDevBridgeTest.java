package dev.civilizations.plugin;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class CivDevBridgeTest {
    private static final String TOKEN = "test-only-development-token-with-32-characters";

    @Test void refusesMissingDevelopmentCredentials() {
        assertThrows(IllegalStateException.class, () -> new CivDevBridge(null, null, null, "test"));
    }

    @Test void protectsLocalMutationEndpointBeforeTouchingTheEngine() throws Exception {
        try (CivDevBridge bridge = new CivDevBridge(null, null, TOKEN, "test-session")) {
            bridge.start(0);
            assertEquals(403, send(bridge, null, null, null, "{\"action\":\"reset\"}").statusCode());
            assertEquals(403, send(bridge, TOKEN, "other-session", null, "{\"action\":\"reset\"}").statusCode());
            assertEquals(403, send(bridge, TOKEN, "test-session", "https://example.invalid", "{\"action\":\"reset\"}").statusCode());
            assertEquals(400, send(bridge, TOKEN, "test-session", null, "[]").statusCode());
            assertEquals(413, send(bridge, TOKEN, "test-session", null, "x".repeat(65_537)).statusCode());
        }
    }

    private static HttpResponse<String> send(CivDevBridge bridge, String token, String session, String origin, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + bridge.port() + "/dev"))
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (session != null) request.header("X-Civ-Session", session);
        if (origin != null) request.header("Origin", origin);
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
