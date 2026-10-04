package dev.civilizations.simulation.prefab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.civilizations.core.BlockPosition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads real Civ Hytale prefab JSON and reduces it to the spatial simulation vocabulary. */
public final class PrefabSimulationLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PrefabSimulationModel load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Prefab does not exist: " + path);
        }
        JsonNode root = objectMapper.readTree(Files.readString(path));
        int anchorX = root.path("anchorX").asInt();
        int anchorY = root.path("anchorY").asInt();
        int anchorZ = root.path("anchorZ").asInt();

        Map<BlockPosition, PrefabSimulationModel.Cell> cells = new LinkedHashMap<>();
        for (JsonNode block : root.path("blocks")) {
            String name = block.path("name").asText();
            if (name.isBlank() || "Empty".equalsIgnoreCase(name)) {
                continue;
            }
            BlockPosition position = new BlockPosition(
                block.path("x").asInt(),
                block.path("y").asInt(),
                block.path("z").asInt()
            );
            PrefabSimulationModel.Cell cell = name.toLowerCase(Locale.ROOT).contains("door")
                ? PrefabSimulationModel.Cell.DOOR
                : PrefabSimulationModel.Cell.SOLID;
            cells.put(position, cell);
        }

        List<PrefabSimulationModel.Marker> markers = new ArrayList<>();
        for (JsonNode entity : root.path("entities")) {
            JsonNode components = entity.path("Components");
            JsonNode trigger = components.path("TriggerVolume");
            JsonNode shape = trigger.path("Shape");
            if (!"Box".equals(shape.path("Type").asText())) {
                continue;
            }
            JsonNode tags = trigger.path("Tags");
            String type = tags.path("civ.type").asText();
            String building = tags.path("civ.building").asText();
            if (type.isBlank() || building.isBlank()) {
                continue;
            }
            JsonNode position = components.path("Transform").path("Position");
            JsonNode min = shape.path("Min");
            JsonNode max = shape.path("Max");
            double x = position.path("X").asDouble();
            double y = position.path("Y").asDouble();
            double z = position.path("Z").asDouble();
            markers.add(new PrefabSimulationModel.Marker(
                trigger.path("Name").asText(type),
                type,
                building,
                new PrefabSimulationModel.Box(
                    x + min.path("X").asDouble(),
                    y + min.path("Y").asDouble(),
                    z + min.path("Z").asDouble(),
                    x + max.path("X").asDouble(),
                    y + max.path("Y").asDouble(),
                    z + max.path("Z").asDouble()
                )
            ));
        }

        return new PrefabSimulationModel(anchorX, anchorY, anchorZ, cells, markers);
    }
}
