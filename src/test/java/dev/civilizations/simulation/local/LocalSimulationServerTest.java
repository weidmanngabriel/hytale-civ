package dev.civilizations.simulation.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class LocalSimulationServerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void controlsRunWithoutPrecomputedFramesOrTickLimit() throws Exception {
        try (var server = new LocalSimulationServer(0)) {
            server.start();
            String url = "http://localhost:" + server.port() + "/api/";
            JsonNode scenarios = get(url + "scenarios");
            assertTrue(scenarios.isArray());
            assertTrue(scenarios.size() > 0);
            assertEquals(0, get(url + "state").path("world").path("tickCount").asLong());
            post(url + "control", "{\"command\":\"step\"}");
            assertEquals(1, get(url + "state").path("world").path("tickCount").asLong());
            post(url + "control", "{\"command\":\"scenario\",\"id\":\"woodcutter-basic\"}");
            assertEquals(0, get(url + "state").path("world").path("tickCount").asLong());
            post(url + "control", "{\"command\":\"speed\",\"value\":5}");
            assertEquals(5, get(url + "state").path("speed").asInt());
            post(url + "control", "{\"command\":\"play\"}");
            Thread.sleep(180);
            post(url + "control", "{\"command\":\"pause\"}");
            assertTrue(get(url + "state").path("world").path("tickCount").asLong() > 0);
            post(url + "control", "{\"command\":\"reset\"}");
            assertEquals(0, get(url + "state").path("world").path("tickCount").asLong());
        }
    }

    private JsonNode get(String url) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return JSON.readTree(response.body());
    }

    private void post(String url, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
    }
}
