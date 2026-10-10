package dev.civilizations.simulation.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.civilizations.simulation.world.WorldArchive;
import java.util.ArrayList;
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

    @Test
    void archivedTerrainAndConfiguredStartsAreReusable() throws Exception {
        var cells = new ArrayList<WorldArchive.Cell>();
        for (int x=0;x<3;x++) for(int y=0;y<3;y++) for(int z=0;z<3;z++)
            cells.add(new WorldArchive.Cell(x,y,z,y==0?"native:stone":"air",0,0,"NONE"));
        var archive = new WorldArchive(WorldArchive.VERSION,"test-world",new WorldArchive.Bounds(0,0,0,3,3,3),cells);
        try (var server = new LocalSimulationServer(0,archive)) {
            server.start();
            String url="http://localhost:"+server.port()+"/api/";
            var terrain=get(url+"terrain");
            assertTrue(terrain.path("loaded").asBoolean());
            assertEquals("test-world",terrain.path("worldId").asText());
            assertEquals(9,terrain.path("cells").size());
            assertEquals(0,get(url+"state").path("world").path("residents").size());
            post(url+"control","{\"command\":\"configureMiners\",\"miners\":2}");
            assertEquals(2,get(url+"state").path("world").path("residents").size());
            post(url+"control","{\"command\":\"reset\"}");
            assertEquals(2,get(url+"state").path("world").path("residents").size());
            assertEquals(9,get(url+"terrain").path("cells").size());
        }
    }

    @Test
    void importedWorldAllowsRealTimeNavigationAndReversibleObstacles() throws Exception {
        var cells = new ArrayList<WorldArchive.Cell>();
        for (int x=0;x<3;x++) for(int y=0;y<3;y++) for(int z=0;z<3;z++)
            cells.add(new WorldArchive.Cell(x,y,z,y==0?"native:stone":"air",0,0,"NONE"));
        var archive=new WorldArchive(WorldArchive.VERSION,"navigation",new WorldArchive.Bounds(0,0,0,3,3,3),cells);
        try(var server=new LocalSimulationServer(0,archive)) {
            server.start();
            String url="http://localhost:"+server.port()+"/api/";
            post(url+"control","{\"command\":\"configureMiners\",\"miners\":3}");
            post(url+"control","{\"command\":\"move\",\"id\":\"miner-1\",\"x\":2.5,\"y\":1,\"z\":2.5}");
            for(int i=0;i<55;i++)post(url+"control","{\"command\":\"step\"}");
            var moved=get(url+"state").path("world").path("residents").get(0);
            assertEquals(2.5,moved.path("position").path("x").asDouble(),0.01);
            assertEquals(2.5,moved.path("position").path("z").asDouble(),0.01);

            post(url+"control","{\"command\":\"setBlock\",\"x\":1,\"y\":1,\"z\":1,\"category\":\"SOLID\"}");
            assertEquals(1,get(url+"state").path("worldRevision").asLong());
            assertEquals(10,get(url+"terrain").path("cells").size());
            post(url+"control","{\"command\":\"reset\"}");
            assertEquals(0,get(url+"state").path("worldRevision").asLong());
            assertEquals(9,get(url+"terrain").path("cells").size());
        }
    }

    @Test
    void terrainDeltaTransmitsOnlyChangedBlocksAndResetCanRecoverFullSnapshot() throws Exception {
        var cells = new ArrayList<WorldArchive.Cell>();
        for (int x=0;x<3;x++) for(int y=0;y<3;y++) for(int z=0;z<3;z++)
            cells.add(new WorldArchive.Cell(x,y,z,y==0?"native:stone":"air",0,0,"NONE"));
        var archive = new WorldArchive(WorldArchive.VERSION,"terrain-delta",
            new WorldArchive.Bounds(0,0,0,3,3,3),cells);
        try(var server=new LocalSimulationServer(0,archive)) {
            server.start();
            String url="http://localhost:"+server.port()+"/api/";
            assertEquals(9,get(url+"terrain").path("cells").size());
            assertEquals(0,get(url+"terrain?since=0").path("changes").size());
            post(url+"control","{\"command\":\"setBlock\",\"x\":1,\"y\":1,\"z\":1,\"category\":\"SOLID\"}");
            var delta=get(url+"terrain?since=0");
            assertEquals(1,delta.path("revision").asLong());
            assertEquals(1,delta.path("changes").size());
            assertEquals(1,delta.path("changes").get(0).get(3).asInt());
            post(url+"control","{\"command\":\"reset\"}");
            assertTrue(get(url+"terrain?since=1").has("cells"));
            assertEquals(9,get(url+"terrain?since=1").path("cells").size());
        }
    }

    @Test
    void resetPreservesParameterizedWorkerCounts() throws Exception {
        try(var server=new LocalSimulationServer(0)) {
            server.start();
            String url="http://localhost:"+server.port()+"/api/";
            post(url+"control","{\"command\":\"configure\",\"woodcutters\":2,\"builders\":1}");
            var original=get(url+"state").path("world").path("residents").size();
            post(url+"control","{\"command\":\"reset\"}");
            assertEquals(original,get(url+"state").path("world").path("residents").size());
            assertEquals(2,get(url+"state").path("additionalWoodcutters").asInt());
        }
    }

    @Test
    void liveControlHistoryIsBoundedAndResettable() throws Exception {
        try (var server = new LocalSimulationServer(0)) {
            server.start();
            String url = "http://localhost:" + server.port() + "/api/";
            post(url + "control", "{\"command\":\"step\"}");
            var events = get(url + "state").path("events");
            assertEquals(1, events.size());
            assertEquals(1, events.get(0).path("tick").asLong());
            assertEquals("CONTROL", events.get(0).path("kind").asText());
            post(url + "control", "{\"command\":\"reset\"}");
            assertEquals(0, get(url + "state").path("events").size());
        }
    }

    @Test
    void minersStartInSameReachableCavernInsteadOfIsolatedPockets() throws Exception {
        var cells = new ArrayList<WorldArchive.Cell>();
        for (int x=0;x<9;x++) for (int y=0;y<4;y++) for (int z=0;z<4;z++) {
            // No ground under x=3 separates both otherwise walkable plateaus.
            String type = y==0 && x!=3 ? "native:stone" : "air";
            cells.add(new WorldArchive.Cell(x,y,z,type,0,0,"NONE"));
        }
        var archive = new WorldArchive(WorldArchive.VERSION,"split-caverns",
            new WorldArchive.Bounds(0,0,0,9,4,4),cells);
        try (var server = new LocalSimulationServer(0,archive)) {
            server.start();
            String url = "http://localhost:" + server.port() + "/api/";
            post(url+"control","{\"command\":\"configureMiners\",\"miners\":3}");
            var residents = get(url+"state").path("world").path("residents");
            assertEquals(3,residents.size());
            // Largest component is x=4..8, not the other side of the gap.
            for (var miner:residents) assertTrue(miner.path("position").path("x").asDouble() >= 4);
        }
    }

    @Test
    void authoredPrefabPlacementCreatesRealMineMarkersAndLocalWorkerRoutes() throws Exception {
        var cells=new ArrayList<WorldArchive.Cell>();
        for(int x=0;x<50;x++)for(int y=0;y<35;y++)for(int z=0;z<50;z++)
            cells.add(new WorldArchive.Cell(x,y,z,y<=16?"native:stone":"air",0,0,"NONE"));
        var archive=new WorldArchive(WorldArchive.VERSION,"authored-mine-fixture",
            new WorldArchive.Bounds(0,0,0,50,35,50),cells);
        try(var server=new LocalSimulationServer(0,archive)) {
            server.start();
            String url="http://localhost:"+server.port()+"/api/";
            post(url+"control","{\"command\":\"placeMine\",\"x\":25,\"y\":0,\"z\":25}");
            var diagnostic=get(url+"debug");
            assertTrue(diagnostic.path("prefab").path("placed").asBoolean());
            assertTrue(diagnostic.path("prefab").path("markers").size()>=4);
            post(url+"control","{\"command\":\"configureMiners\",\"miners\":3}");
            assertEquals(3,get(url+"state").path("world").path("residents").size());
            assertEquals(8,get(url+"debug").path("mine").path("sliceCount").asInt());
            post(url+"control","{\"command\":\"step\"}");
            assertTrue(get(url+"debug").path("mine").path("workers").isArray());
            post(url+"control","{\"command\":\"reset\"}");
            assertTrue(get(url+"debug").path("prefab").path("placed").asBoolean());
            assertEquals(3,get(url+"state").path("world").path("residents").size());
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
