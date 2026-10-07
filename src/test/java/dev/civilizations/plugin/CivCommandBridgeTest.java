package dev.civilizations.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivCommandBridgeTest {
    @Test
    void exposesLoopbackHealthWithoutHytaleRuntime() throws Exception {
        try (CivCommandBridge bridge = new CivCommandBridge(HytaleLogger.getLogger())) {
            bridge.start(0);
            HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + bridge.port() + "/health")
            ).GET().build();
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("\"ready\":true"));
            }
        }
    }

    @Test
    void rejectsInvalidPort() {
        try (CivCommandBridge bridge = new CivCommandBridge(HytaleLogger.getLogger())) {
            assertThrows(IllegalArgumentException.class, () -> bridge.start(80));
        }
    }
}
